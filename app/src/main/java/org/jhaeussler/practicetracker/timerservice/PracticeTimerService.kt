package org.jhaeussler.practicetracker.timerservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.utils.secondsToNiceString
import java.time.LocalDate

class PracticeTimerService : Service() {

    enum class TimerState { STOPPED, RUNNING, PAUSED }

    private companion object {
        const val TIMER_TICK = 1000L
        const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "practice_timer_channel"
    }
    private var elapsedTimeSec = 0L
    private var sessionStartTimeMillis = 0L
    private var nextTickTimeMillis = 0L
    private var timePausedAtMillis = 0L             // Absolute time when the timer was PAUSED
    private var totalPausedDurationMillis = 0L      // Total time spent paused (Cumulative offset)

    private var sessionStartDate: LocalDate? = null
    private var timerState = TimerState.STOPPED

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaSessionCompat

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
        setupMediaSession()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        stopTimer()

        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = TimerBinder()

    inner class TimerBinder : Binder() {
        fun getService(): PracticeTimerService = this@PracticeTimerService
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "PracticeTimerTag").apply {
            // some default state is needed
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                    // Minimal actions to define it as 'media'
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .build()
            )
        }
    }

    private fun startForegroundService() {
        // Create the notification builder
        notificationBuilder =
            NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ongoing Practice Session...")
            .setContentText(getTimerNotificationText())
            .setSmallIcon(R.drawable.timelapse)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use low priority for background tasks
            .setOngoing(true) // Make the notification persistent

        // Start the service in the foreground with the initial notification
        startForeground(
            NOTIFICATION_ID,
            notificationBuilder.build(),
            FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        when (action) {
            "startTimer" -> startTimer()
            "pauseTimer" -> pauseTimer()
        }

        return START_STICKY
    }

    private fun startTimer() {
        if(timerState == TimerState.RUNNING)
            return

        // start the media session - otherwise the timer will be interrupted when the phone sleeps
        if(!mediaSession.isActive) {
            mediaSession.isActive = true
        }

        // must be set before Foreground Service is started.
        // Only playing state at service start will prevent OS of pausing timer to save resources
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING,elapsedTimeSec, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build()
        )

        if (sessionStartTimeMillis == 0L) {
            sessionStartTimeMillis = SystemClock.elapsedRealtime()
            sessionStartDate = LocalDate.now()
            nextTickTimeMillis = sessionStartTimeMillis + 1000L
        }

        if(timerState == TimerState.PAUSED) {
            val pauseDuration = SystemClock.elapsedRealtime() - timePausedAtMillis
            totalPausedDurationMillis += pauseDuration
            nextTickTimeMillis += pauseDuration
        }

        if (timerState == TimerState.STOPPED) {
            startForegroundService()
        }

        handler.postDelayed(timerRunnable, TIMER_TICK)
        timerState = TimerState.RUNNING
    }

    private fun stopTimer() {
        if(timerState in setOf(TimerState.RUNNING, TimerState.PAUSED)) {
            timerState = TimerState.STOPPED
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun getTimerNotificationText() : String {
        return "Duration: ${secondsToNiceString(elapsedTimeSec)}"
    }

    // Notification channel for timer notification
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Practice Timer Notifications", // Channel Name
            NotificationManager.IMPORTANCE_LOW // Importance level
        ).apply {
            description = "Practice App Timer Notification channel"
        }

        // Register the channel with the system
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        if (!::notificationBuilder.isInitialized) {
            return
        }

        // Update the notification's content
        notificationBuilder.setContentText(
            getTimerNotificationText() +
                    if(timerState == TimerState.PAUSED) " - Session Paused" else ""
        )

        // Notify the system to update the existing notification
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // For interaction with timer service from viewModels

    fun pauseTimer() {
        if(timerState == TimerState.RUNNING)
        {
            timerState = TimerState.PAUSED
            timePausedAtMillis = SystemClock.elapsedRealtime()

            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED,0, 0.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .build()
            )

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

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED,0, 0.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build()
        )

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