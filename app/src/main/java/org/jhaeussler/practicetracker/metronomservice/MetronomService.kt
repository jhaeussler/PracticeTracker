package org.jhaeussler.practicetracker.metronomservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MetronomeService : Service() {

    companion object {
        private const val CHANNEL_ID = "metronome_channel"
        private const val NOTIFICATION_ID = 420
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        const val ACTION_STOP = "com.example.app.ACTION_STOP"

        private val _bpm = MutableStateFlow(120) // Default 120 BPM
        val bpm: StateFlow<Int> = _bpm.asStateFlow()

        fun setBpm(newBpm: Int) {
            // Keep BPM within reasonable physical musical bounds
            _bpm.value = newBpm.coerceIn(30, 300)
        }
    }
    private val binder = MetronomeBinder()

    inner class MetronomeBinder : Binder() {
        fun getService(): MetronomeService = this@MetronomeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Handles user swiping the app out of recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        cleanupAndStop()
    }

    // Handles explicit stopService(), stopSelf(), or OS system destruction
    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }

    private fun cleanupAndStop() {
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = createNotification()

        // Promote service to foreground to prevent OS from killing it in background
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        _isRunning.value = true

        return START_STICKY
    }
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome Playback",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
    private fun createNotification(): Notification
    {
        // PendingIntent to launch main activity when tapping the notification body
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopIntent = Intent(this, MetronomeService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metronome Running")
            .setContentText("Tap to open app")
            .setSmallIcon(android.R.drawable.ic_media_play) // Replace with your drawable icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
}