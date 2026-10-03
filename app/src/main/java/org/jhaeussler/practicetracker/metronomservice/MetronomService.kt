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
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

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
        private const val WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private var SAMPLE_RATE = 48000
    }

    private val repository = MetronomeRepository
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isNoisyReceiverRegistered = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioThread: Thread? = null
    private lateinit var engine: MetronomeEngine

    // 64-bit unwrapper for playbackHeadPosition
    private var lastRawHeadPos = 0L
    private var headWrapCount = 0L

    // Pauses metronome if headphones are unplugged
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                handleStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(FlowPreview::class)
    override fun onCreate()
    {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)

        SAMPLE_RATE = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48000

        engine = MetronomeEngine(SAMPLE_RATE)

        createNotificationChannel()

        if (repository.beats.value.isNotEmpty()) {
            engine.setBeatsList(repository.beats.value)
        } else {
            repository.updateBeatMeasure(engine.beats)
        }

        // Automatically update the notification when BPM is changed in the app
        serviceScope.launch {
            repository.bpm.debounce(200.milliseconds).collect {
                if (repository.isRunning.value) {
                    updateNotification()
                }
            }
        }
    }

    // Handles user swiping the app out of recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        handleStop()
    }

    // Handles explicit stopService(), stopSelf(), or OS system destruction
    override fun onDestroy()
    {
        serviceScope.cancel()
        stopAudioAndCleanup()
        super.onDestroy()
    }

    private fun resetPlaybackHeadTracking() {
        lastRawHeadPos = 0L
        headWrapCount = 0L
    }
    private fun resetMetronome(fullReset: Boolean = false)
    {
        engine.resetPhase(fullReset)
        if (fullReset) {
            resetPlaybackHeadTracking()
        }
        repository.updateCurrentBeat(engine.currentBeatInMeasure)
    }

    // Audio playback

    private fun syncEngineWithRepository() {
        val currentBeats = repository.beats.value

        if (engine.beats != currentBeats) {
            engine.setBeatsList(currentBeats)
        }
    }

    private fun stopAudioAndCleanup() {
        unregisterNoisyReceiver()
        stopAudioPlayback()
        abandonAudioFocus()
        resetMetronome(fullReset = true)
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
                if (engine.setSubdivision(value))
                {
                    repository.updateBeatMeasure(engine.beats)
                    resetMetronome(fullReset = !repository.isRunning.value)
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
        if (!requestAudioFocus()) {
            stopSelf()
            return
        }

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

    @Synchronized
    private fun startAudioPlayback()
    {
        if (repository.isRunning.value || audioThread?.isAlive == true) return

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PracticeTracker:MetronomeWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS) // Protect starting immediately
        }

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

    @Synchronized
    private fun stopAudioPlayback()
    {
        if (!repository.isRunning.value &&
            wakeLock == null &&
            audioThread == null
        ) {
            return
        }

        repository.setRunning(false)

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

        audioThread?.interrupt()
        audioThread?.join(300)
        audioThread = null
    }


    private fun runAudioLoop()
    {
        var lastRefresh = System.currentTimeMillis()

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
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
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

        val chunkSize = 512
        val buffer = ShortArray(chunkSize)

        resetMetronome(fullReset = true)

        try {
            // Pre-fill the initial chunk so the hardware has data ready
            engine.fillNextChunk(buffer, repository.bpm.value)
            audioTrack.write(buffer, 0, chunkSize)

            audioTrack.play()

            while (repository.isRunning.value && !Thread.currentThread().isInterrupted)
            {
                engine.fillNextChunk(buffer, repository.bpm.value)

                val result = audioTrack.write(buffer, 0, chunkSize)
                if (result < 0) {
                    Handler(Looper.getMainLooper()).post { handleStop() }
                    break // Audio route died (e.g. bluetooth disconnected)
                }

                val currentPlayedSample = getContinuousPlaybackSamples(audioTrack)
                var latestPlayedBeat: Int? = null

                // Drain all beats whose audio has already exited the DAC
                while (engine.beatQueue.peek()?.let {
                    it.samplePosition <= currentPlayedSample } == true)
                {
                    latestPlayedBeat = engine.beatQueue.poll()?.beatIndex
                }

                // Update UI only when the beat has physically arrived at the speaker
                latestPlayedBeat?.let { beatIndex ->
                    if (repository.currentBeatInMeasure.value != beatIndex) {
                        repository.updateCurrentBeat(beatIndex)
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastRefresh > 60_000L) {
                    wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                    lastRefresh = now
                }
            }
        }
        catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { handleStop() }
        }
        finally {
            try {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop()
                }
            }
            catch (e: IllegalStateException) {
                //
            }
            finally {
                try {
                    audioTrack.release()
                } catch (e: Exception) {
                    //
                }
            }

            repository.setRunning(false)
        }
    }

    // Handles 32-bit unsigned overflow (occurs after ~24.8 hours of continuous playback at 48kHz)
    private fun getContinuousPlaybackSamples(track: AudioTrack): Long {
        val rawPos = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        if (rawPos < lastRawHeadPos) {
            headWrapCount++
        }
        lastRawHeadPos = rawPos
        return (headWrapCount shl 32) or rawPos
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
        ).apply {
            description = "Metronome service controls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        // PendingIntent to launch main activity when tapping the notification body
        val contentIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
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
            .setContentTitle("Metronome")
            .setContentText("${repository.bpm.value} BPM")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setRequestPromotedOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
}
