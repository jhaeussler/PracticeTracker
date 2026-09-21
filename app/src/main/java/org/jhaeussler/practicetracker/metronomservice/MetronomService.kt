/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

class MetronomeService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private val _bpm = MutableStateFlow(120)
        val bpm: StateFlow<Int> = _bpm.asStateFlow()
        fun setBpm(newBpm: Int) {
            // Keep BPM within reasonable physical musical bounds
            _bpm.value = newBpm.coerceIn(30, 300)
        }

        // purely private members
        private const val ACTION_STOP = "org.jhaeussler.practicetracker.ACTION_STOP"
        private const val CHANNEL_ID = "metronome_channel"
        private const val NOTIFICATION_ID = 420
        private const val SAMPLE_RATE = 44100
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
    override fun onTaskRemoved(rootIntent: Intent?)
    {
        super.onTaskRemoved(rootIntent)
        cleanupAndStop()
    }

    // Handles explicit stopService(), stopSelf(), or OS system destruction
    override fun onDestroy()
    {
        cleanupAndStop()
        super.onDestroy()
    }

    private fun cleanupAndStop()
    {
        stopAudioPlayback()
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

        startAudioPlayback()

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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    // Audio playback

    fun startAudioPlayback()
    {
        if (isRunning.value) return

        _isRunning.value = true

        audioThread = thread(start = true, priority = Thread.MAX_PRIORITY) {
            runAudioLoop()
        }
    }

    fun stopAudioPlayback()
    {
        _isRunning.value = false
        audioThread?.join()
        audioThread = null
    }

    private var audioThread: Thread? = null
    private val engine = MetronomeEngine(SAMPLE_RATE)

    private fun runAudioLoop()
    {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Using a buffer size twice the minimum prevents emulator crackling
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()
        engine.resetPhase()

        // Fixed chunk size for writing to the hardware buffer
        val chunkSize = 512
        val buffer = ShortArray(chunkSize)
        val startNanos = System.nanoTime()

        while (isRunning.value)
        {
            engine.fillNextChunk(buffer, bpm.value)
            audioTrack.write(buffer, 0, chunkSize)

            val elapsedNanos = System.nanoTime() - startNanos
            val physicalSamplesPlayed = audioTrack.playbackHeadPosition.toLong()
            engine.syncToHardwareClock(elapsedNanos, physicalSamplesPlayed)
        }

        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            // Handle potential teardown exceptions safely
        }
    }
}