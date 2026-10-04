/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.utils.secondsToNiceString
import java.time.LocalDate

class SessionTimerService : Service() {

    enum class TimerState { STOPPED, RUNNING, PAUSED }

    private var sessionStartTimeMillis = 0L
    private var timePausedAtMillis = 0L             // Absolute time when the timer was PAUSED
    private var totalPausedDurationMillis = 0L      // Total time spent paused (Cumulative offset)

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"

        const val ACTION_NOTIFICATION_GRANTED = "ACTION_NOTIFICATION_GRANTED"

        private val _timerState = MutableStateFlow(TimerState.STOPPED)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        private val _elapsedTimeSec = MutableStateFlow(0L)
        val elapsedTimeSec: StateFlow<Long> = _elapsedTimeSec.asStateFlow()

        private val _sessionStartDate = MutableStateFlow<LocalDate?>(null)
        val sessionStartDate: StateFlow<LocalDate?> = _sessionStartDate.asStateFlow()

        private const val TIMER_TICK = 1000L
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "practice_timer_channel"

        // Helper methods for ViewModels to trigger service intents easily
        fun startTimer(context: Context) {
            if (_timerState.value == TimerState.RUNNING) return

            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_START }
            context.startService(intent)
        }

        fun pauseTimer(context: Context) {
            if (_timerState.value != TimerState.RUNNING) return

            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resetTimer(context: Context) {
            if (_timerState.value == TimerState.STOPPED) return

            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_RESET }
            context.startService(intent)
        }

        fun notificationGranted(context: Context) {
            if (_timerState.value == TimerState.STOPPED) return

            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_NOTIFICATION_GRANTED }

            context.startService(intent)
        }
    }

    private val timerRunnable = object : Runnable
    {
        override fun run() {
            if (_timerState.value != TimerState.RUNNING) return

            val totalElapsedMillis = getCurrentElapsedMillis()

            _elapsedTimeSec.value = totalElapsedMillis / TIMER_TICK

            val timeToNextSecond = TIMER_TICK - (totalElapsedMillis % TIMER_TICK)

            // Schedule next tick precisely when the next second flips
            handler.postDelayed(this, timeToNextSecond)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON && _timerState.value == TimerState.RUNNING) {
                updateNotification()
            }
        }
    }

    override fun onCreate()
    {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy()
    {
        unregisterReceiver(screenStateReceiver)
        stopTimer()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: intent?.getStringExtra("action")) {
            ACTION_START -> handleStartTimer()
            ACTION_PAUSE -> handlePauseTimer()
            ACTION_RESET -> handleResetTimer()
            ACTION_NOTIFICATION_GRANTED -> handleNotificationPermissionGranted()
            else -> {
                if (_timerState.value == TimerState.STOPPED) {
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun getCurrentElapsedMillis(): Long {
        if (sessionStartTimeMillis == 0L) return 0L
        val referenceTime = if (_timerState.value == TimerState.PAUSED) {
            timePausedAtMillis
        } else {
            SystemClock.elapsedRealtime()
        }
        return referenceTime - sessionStartTimeMillis - totalPausedDurationMillis
    }

    private fun getIntentForAction( actionToSet: String) : Intent {
        return Intent(this,
            SessionTimerService::class.java).apply { action = actionToSet }
    }

    private fun getPendingIntent(requestCode: Int, intent: Intent) : PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Notification channel for timer notification
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Practice Session Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Practice Session Notification channel"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }

        // Register the channel with the system
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val contentIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        }

        val currentState = _timerState.value
        val isRunning = currentState == TimerState.RUNNING

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Practice Session")
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.timelapse)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(currentState != TimerState.STOPPED)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)

        builder.setRequestPromotedOngoing(true)

        // Lock screen & tray action buttons
        if (isRunning)
        {
            val elapsedRealtimeOffset = SystemClock.elapsedRealtime() - getCurrentElapsedMillis()
            val baseSystemTime =
                System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedRealtimeOffset)

            // Native Chronometer: Ticks smoothly on lock screen without waking CPU
            builder.setUsesChronometer(true)
                .setWhen(baseSystemTime)
                .setShowWhen(true)
                .setContentText("in Progress...")

            val pausePendingIntent =
                getPendingIntent(1, getIntentForAction(ACTION_PAUSE))
            builder.addAction(R.drawable.timelapse, "Pause", pausePendingIntent)
        }
        else {
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setSubText("Paused")
                .setContentText("So far: ${secondsToNiceString(_elapsedTimeSec.value)}")

            val resumePendingIntent =
                getPendingIntent(2, getIntentForAction(ACTION_START))
            builder.addAction(R.drawable.timelapse, "Resume", resumePendingIntent)
        }

        return builder
    }

    private fun updateNotification() {
        if (_timerState.value == TimerState.STOPPED) return

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification().build())
    }

    // Timer handling

    private fun handleStartTimer() {
        if (_timerState.value == TimerState.RUNNING) return

        if (sessionStartTimeMillis == 0L)
        {
            sessionStartTimeMillis = SystemClock.elapsedRealtime()
            _sessionStartDate.value = LocalDate.now()
        }

        if (_timerState.value == TimerState.PAUSED)
        {
            val pauseDuration = SystemClock.elapsedRealtime() - timePausedAtMillis
            totalPausedDurationMillis += pauseDuration
        }

        _timerState.value = TimerState.RUNNING
        startForegroundService()

        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    private fun handlePauseTimer() {
        if (_timerState.value == TimerState.RUNNING)
        {
            _timerState.value = TimerState.PAUSED
            handler.removeCallbacks(timerRunnable)
            timePausedAtMillis = SystemClock.elapsedRealtime()
            updateNotification()
        }
    }

    private fun handleResetTimer() {
        if(_timerState.value != TimerState.STOPPED) {
            stopTimer()
        }

        stopSelf()
    }

    private fun handleNotificationPermissionGranted() {
        if (_timerState.value != TimerState.STOPPED) {
            updateNotification()
        }
    }

    private fun stopTimer() {
        if (_timerState.value != TimerState.STOPPED)
        {
            handler.removeCallbacks(timerRunnable)
            _timerState.value = TimerState.STOPPED

            stopForeground(STOP_FOREGROUND_REMOVE)

            _elapsedTimeSec.value = 0L
            sessionStartTimeMillis = 0L
            timePausedAtMillis = 0L
            totalPausedDurationMillis = 0L
            _sessionStartDate.value = null
        }
    }

    private fun startForegroundService() {
        val notification = buildNotification().build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0 // Legacy fallback (no type required)
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )
    }
}
