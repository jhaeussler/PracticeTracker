/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.timerservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.utils.secondsToNiceString
import java.time.LocalDate

class PracticeTimerService : Service() {

    enum class TimerState { STOPPED, RUNNING, PAUSED }

    private companion object {
        const val TIMER_TICK = 1000L
        const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "practice_timer_channel"

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"
    }

    private var elapsedTimeSec = 0L
    private var sessionStartTimeMillis = 0L
    private var nextTickTimeMillis = 0L
    private var timePausedAtMillis = 0L             // Absolute time when the timer was PAUSED
    private var totalPausedDurationMillis = 0L      // Total time spent paused (Cumulative offset)

    private var sessionStartDate: LocalDate? = null
    private var timerState = TimerState.STOPPED

    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerState != TimerState.RUNNING) {
                return
            }

            val currentTime = SystemClock.elapsedRealtime()
            elapsedTimeSec = (currentTime - sessionStartTimeMillis - totalPausedDurationMillis) / 1000
            nextTickTimeMillis += TIMER_TICK

            // difference between last scheduled time (last call here + 1 s)
            // and now (slightly more than 1 s later) -> adapt to delay
            val delayMillis = nextTickTimeMillis - SystemClock.elapsedRealtime()

            timerCallback?.onTimerTick(elapsedTimeSec) // Notify the callback
            updateNotification()

            if (delayMillis <= 0) {
                // Last tick already over 1 s ago...
                handler.post(this)
            } else {
                handler.postDelayed(this, delayMillis)
            }
        }
    }

    private var timerCallback: TimerCallback? = null // Callback interface

    interface TimerCallback {
        fun onTimerTick(elapsedTime: Long)
    }

    fun setTimerCallback(callback: TimerCallback?) {
        timerCallback = callback
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        stopTimer()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = TimerBinder()

    inner class TimerBinder : Binder() {
        fun getService(): PracticeTimerService = this@PracticeTimerService
    }

    private fun startForegroundService() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification().build(),
            FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: intent?.getStringExtra("action")) {
            ACTION_START, "startTimer" -> startTimer()
            ACTION_PAUSE, "pauseTimer" -> pauseTimer()
            ACTION_RESET -> resetTimer()
        }

        return START_STICKY
    }

    private fun getTimerNotificationText() : String {
        return "Duration: ${secondsToNiceString(elapsedTimeSec)}"
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
            PracticeTimerService::class.java).apply { action = actionToSet }
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ongoing Practice Session")
            .setContentText(
                getTimerNotificationText() +
                        if (timerState == TimerState.PAUSED) " - Session Paused" else ""
            )
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.timelapse)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(timerState == TimerState.RUNNING)
            .setOnlyAlertOnce(true)

        // Lock screen & tray action buttons
        if (timerState == TimerState.RUNNING)
        {
            val pauseIntent = getIntentForAction(ACTION_PAUSE)
            val pausePendingIntent = getPendingIntent(1, pauseIntent)
            builder.addAction(R.drawable.timelapse, "Pause", pausePendingIntent)
        }
        else if (timerState == TimerState.PAUSED)
        {
            val resumeIntent = getIntentForAction(ACTION_START)
            val resumePendingIntent = getPendingIntent(2, resumeIntent)
            builder.addAction(R.drawable.timelapse, "Resume", resumePendingIntent)
        }

        return builder
    }

    private fun updateNotification() {
        if (timerState == TimerState.STOPPED) return

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification().build())
    }

    private fun startTimer() {
        if (timerState == TimerState.RUNNING) return

        if (sessionStartTimeMillis == 0L) {
            sessionStartTimeMillis = SystemClock.elapsedRealtime()
            sessionStartDate = LocalDate.now()
            nextTickTimeMillis = sessionStartTimeMillis + 1000L
        }

        if (timerState == TimerState.PAUSED) {
            val pauseDuration = SystemClock.elapsedRealtime() - timePausedAtMillis
            totalPausedDurationMillis += pauseDuration
            nextTickTimeMillis += pauseDuration
        }

        if (timerState == TimerState.STOPPED) {
            startForegroundService()
        }

        handler.postDelayed(timerRunnable, TIMER_TICK)
        timerState = TimerState.RUNNING
        updateNotification()
    }

    private fun stopTimer() {
        if (timerState in setOf(TimerState.RUNNING, TimerState.PAUSED)) {
            handler.removeCallbacks(timerRunnable)
            timerState = TimerState.STOPPED

            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    // For interaction with timer service from viewModels

    fun pauseTimer() {
        if (timerState == TimerState.RUNNING)
        {
            timerState = TimerState.PAUSED
            handler.removeCallbacks(timerRunnable)
            timePausedAtMillis = SystemClock.elapsedRealtime()
            updateNotification()
        }
    }

    fun startOrResumeTimer() {
        startTimer()
    }

    fun resetTimer() {
        if(timerState == TimerState.STOPPED) {
            return
        }

        stopTimer()

        elapsedTimeSec = 0L
        sessionStartTimeMillis = 0L
        timePausedAtMillis = 0L
        totalPausedDurationMillis = 0L
        nextTickTimeMillis = 0L

        timerCallback?.onTimerTick(elapsedTimeSec)
        stopSelf()
    }

    fun getSessionStartDate() : LocalDate? { return  sessionStartDate }
    fun isRunning() : Boolean { return timerState == TimerState.RUNNING }
    fun isPaused() : Boolean { return timerState == TimerState.PAUSED }
}