/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.utils.secondsToNiceString
import java.time.LocalDate

class SessionTimerService : Service() {

    enum class TimerState { STOPPED, RUNNING, PAUSED }

    private var sessionStartTimeMillis = 0L
    private var nextTickTimeMillis = 0L
    private var timePausedAtMillis = 0L             // Absolute time when the timer was PAUSED
    private var totalPausedDurationMillis = 0L      // Total time spent paused (Cumulative offset)

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"

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
            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resetTimer(context: Context) {
            val intent = Intent(context,
                SessionTimerService::class.java).apply { action = ACTION_RESET }
            context.startService(intent)
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (_timerState.value != TimerState.RUNNING) {
                return
            }

            val currentTime = SystemClock.elapsedRealtime()
            _elapsedTimeSec.value =
                (currentTime - sessionStartTimeMillis - totalPausedDurationMillis) / 1000

            nextTickTimeMillis += TIMER_TICK

            // difference between last scheduled time (last call here + 1 s)
            // and now (slightly more than 1 s later) -> adapt to delay
            val delayMillis = nextTickTimeMillis - SystemClock.elapsedRealtime()

            updateNotification()

            if (delayMillis <= 0) {
                // Last tick already over 1 s ago...
                handler.post(this)
            } else {
                handler.postDelayed(this, delayMillis)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        stopTimer()

        if (_timerState.value != TimerState.STOPPED) {
            _timerState.value = TimerState.STOPPED
            _elapsedTimeSec.value = 0L
            _sessionStartDate.value = null
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: intent?.getStringExtra("action")) {
            ACTION_START, "startTimer" -> handleStartTimer()
            ACTION_PAUSE, "pauseTimer" -> handlePauseTimer()
            ACTION_RESET -> handleResetTimer()
        }

        return START_STICKY
    }

    private fun getTimerNotificationText() : String {
        return "Duration: ${secondsToNiceString(_elapsedTimeSec.value)}"
    }

    // Notification channel for timer notification
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Practice Session Notifications", // Channel Name
            NotificationManager.IMPORTANCE_LOW // Importance level
        ).apply {
            description = "Practice Session Notification channel"
        }

        // Register the channel with the system
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun getIntentForAction( actionToSet: String) : Intent {
        val intent = Intent(this,
            SessionTimerService::class.java).apply { action = actionToSet }
        return intent
    }

    private fun getPendingIntent(requestCode: Int, intent: Intent) : PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ongoing Practice Session")
            .setContentText(
                getTimerNotificationText() +
                        if (currentState == TimerState.PAUSED) " - Session Paused" else ""
            )
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.timelapse)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(currentState == TimerState.RUNNING)
            .setOnlyAlertOnce(true)

        // Lock screen & tray action buttons
        if (currentState == TimerState.RUNNING)
        {
            val pausePendingIntent =
                getPendingIntent(1, getIntentForAction(ACTION_PAUSE))
            builder.addAction(R.drawable.timelapse, "Pause", pausePendingIntent)
        }
        else if (currentState == TimerState.PAUSED)
        {
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
            nextTickTimeMillis = sessionStartTimeMillis + TIMER_TICK
        }

        if (_timerState.value == TimerState.PAUSED)
        {
            val pauseDuration = SystemClock.elapsedRealtime() - timePausedAtMillis
            totalPausedDurationMillis += pauseDuration
            nextTickTimeMillis += pauseDuration
        }

        if (_timerState.value == TimerState.STOPPED) {
            startForegroundService()
        }

        // Ensure no stale callbacks exist before scheduling a new tick loop
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
        _timerState.value = TimerState.RUNNING

        updateNotification()
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

        _elapsedTimeSec.value = 0L
        sessionStartTimeMillis = 0L
        timePausedAtMillis = 0L
        totalPausedDurationMillis = 0L
        nextTickTimeMillis = 0L
        _sessionStartDate.value = null

        stopSelf()
    }

    private fun stopTimer() {
        if (_timerState.value in setOf(TimerState.RUNNING, TimerState.PAUSED))
        {
            handler.removeCallbacks(timerRunnable)
            _timerState.value = TimerState.STOPPED

            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startForegroundService() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification().build(),
            FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }
}
