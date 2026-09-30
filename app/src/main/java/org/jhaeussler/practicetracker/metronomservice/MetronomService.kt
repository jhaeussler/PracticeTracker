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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

class MetronomeService : Service() {
    companion object {
        const val ACTION_START_METRONOME = "org.jhaeussler.practicetracker.metronome.ACTION_START"
        const val ACTION_STOP_METRONOME = "org.jhaeussler.practicetracker.metronome.ACTION_STOP"
        const val ACTION_TOGGLE_METRONOME = "org.jhaeussler.practicetracker.metronome.ACTION_TOGGLE"
        const val ACTION_SET_SUBDIVISION = "org.jhaeussler.practicetracker.metronome.ACTION_SET_SUBDIVISION"
        const val ACTION_TOGGLE_BEAT = "org.jhaeussler.practicetracker.metronome.ACTION_TOGGLE_BEAT"
        const val EXTRA_VALUE = "org.jhaeussler.practicetracker.metronome.EXTRA_VALUE"

        // private
        private const val CHANNEL_ID = "metronome_channel"
        private const val NOTIFICATION_ID = 420
        private const val SAMPLE_RATE = 44100
    }

    private val repository = MetronomeRepository
    private val engine = MetronomeEngine(SAMPLE_RATE)
    private var audioThread: Thread? = null
    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isNoisyReceiverRegistered = false

    // Pauses metronome if headphones are unplugged
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                handleStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate()
    {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()

        if (repository.beats.value.isNotEmpty()) {
            engine.setBeatsList(repository.beats.value)
        } else {
            repository.updateBeatMeasure(engine.beats)
        }
    }

    // Handles user swiping the app out of recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        handleStop()
    }

    // Handles explicit stopService(), stopSelf(), or OS system destruction
    override fun onDestroy() {
        stopAudioAndCleanup()
        super.onDestroy()
    }

    private fun stopAudioAndCleanup() {
        unregisterNoisyReceiver()
        stopAudioPlayback()
        abandonAudioFocus()
        resetMetronome()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        when (intent?.action) {
            ACTION_START_METRONOME -> handleStart()
            ACTION_STOP_METRONOME -> handleStop()
            ACTION_TOGGLE_METRONOME -> if (repository.isRunning.value) handleStop() else handleStart()
            ACTION_SET_SUBDIVISION -> {
                val value = intent.getIntExtra(EXTRA_VALUE, 1)
                if (engine.setSubdivision(value)) {
                    repository.updateBeatMeasure(engine.beats)
                    resetMetronome()
                }
            }
            ACTION_TOGGLE_BEAT -> {
                val index = intent.getIntExtra(EXTRA_VALUE, -1)
                if (index != -1) {
                    if (engine.toggleBeatEnabled(index)) {
                        repository.updateBeatMeasure(engine.beats)
                    }
                }
            }
            else -> if (!repository.isRunning.value) stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun handleStart()
    {
        if (!requestAudioFocus()) return

        registerNoisyReceiver()

        val notification = createNotification()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        startAudioPlayback()
    }

    private fun handleStop()
    {
        stopAudioAndCleanup()
        stopSelf()
    }

    private fun resetMetronome() {
        engine.resetPhase()
        repository.updateCurrentBeat(engine.currentBeatInMeasure)
    }

    // Audio playback

    private fun syncEngineWithRepository() {
        val currentBeats = repository.beats.value

        if (engine.beats != currentBeats) {
            engine.setBeatsList(currentBeats)
        }
    }

    @Synchronized
    private fun stopAudioPlayback()
    {
        if (!repository.isRunning.value) return

        repository.setRunning(false)

        activeAudioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {}
        }

        audioThread?.interrupt()
        audioThread?.join(300)
        audioThread = null
    }

    @Synchronized
    private fun startAudioPlayback()
    {
        if (repository.isRunning.value) return

        syncEngineWithRepository()
        repository.setRunning(true)

        audioThread = thread(
            start = true,
            priority = Thread.MAX_PRIORITY,
            name = "MetronomeAudioThread"
        ) {
            runAudioLoop()
        }
    }
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

        activeAudioTrack = audioTrack

        val chunkSize = 512
        val buffer = ShortArray(chunkSize)

        resetMetronome()
        audioTrack.play()

        try {
            while (repository.isRunning.value && !Thread.currentThread().isInterrupted)
            {
                engine.fillNextChunk(buffer, repository.bpm.value)

                val result = audioTrack.write(
                    buffer, 0, chunkSize
                )

                if (result < 0) {
                    Handler(Looper.getMainLooper()).post { handleStop() }
                    break // Audio route died (e.g. bluetooth disconnected)
                }

                if (repository.currentBeatInMeasure.value != engine.currentBeatInMeasure) {
                    repository.updateCurrentBeat(engine.currentBeatInMeasure)
                }
            }
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}

            activeAudioTrack = null
        }
    }

    // Audio Focus Handling

    private fun requestAudioFocus(): Boolean
    {
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    handleStop()
                }
            }
            .build()

        return audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // Noisy Receiver Registration (API 34+ requirement)

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                RECEIVER_NOT_EXPORTED
            )
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (_: IllegalArgumentException) {}
            isNoisyReceiverRegistered = false
        }
    }

    // Notification

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
            action = ACTION_STOP_METRONOME
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
}
