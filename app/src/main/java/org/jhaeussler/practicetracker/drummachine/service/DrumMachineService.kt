/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.drummachine.service

import android.R
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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import org.jhaeussler.practicetracker.drummachine.engine.PlaybackEvent
import org.jhaeussler.practicetracker.drummachine.engine.TimelineAudioEngine
import org.jhaeussler.practicetracker.drummachine.session.AppDrumMachineSession
import org.jhaeussler.practicetracker.drummachine.sound.SoundBank
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

class DrumMachineService : Service() {
    companion object {
        const val ACTION_START = "org.jhaeussler.practicetracker.drummachine.ACTION_START"
        const val ACTION_STOP = "org.jhaeussler.practicetracker.drummachine.ACTION_STOP"
        const val ACTION_TOGGLE = "org.jhaeussler.practicetracker.drummachine.ACTION_TOGGLE"
        const val ACTION_NOTIFICATION_GRANTED = "org.jhaeussler.practicetracker.drummachine.ACTION_NOTIFICATION_GRANTED"

        private const val CHANNEL_ID = "drum_machine_playback_channel"
        private const val NOTIFICATION_ID = 420
        private const val WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private var SAMPLE_RATE = 48000

        fun start(context: Context) {
            val intent = Intent(context, DrumMachineService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DrumMachineService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, DrumMachineService::class.java).apply { action = ACTION_TOGGLE }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val session = AppDrumMachineSession
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isNoisyReceiverRegistered = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioThread: Thread? = null

    private lateinit var engine: TimelineAudioEngine

    // 64-bit unwrapper for playbackHeadPosition to prevent 24.8-hour overflow
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

        engine = TimelineAudioEngine(sampleRate = SAMPLE_RATE)
        engine.setSoundBank(SoundBank.createDefault(SAMPLE_RATE))
        engine.setPattern(session.activePattern.value)

        createNotificationChannel()

        // 1. Reactive Pattern Latching: Automatically stage pattern changes at beat boundaries
        serviceScope.launch {
            session.activePattern.collect { pattern ->
                engine.setPattern(pattern)
            }
        }

        // 2. Dynamic Notification Updates when BPM changes
        serviceScope.launch {
            session.bpm.debounce(200.milliseconds).collect {
                if (session.isRunning.value) {
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_TOGGLE -> if (session.isRunning.value) handleStop() else handleStart()
            ACTION_NOTIFICATION_GRANTED -> handleNotificationPermissionGranted()
            else -> if (!session.isRunning.value) stopSelf()
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

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )

        startAudioPlayback()
    }

    private fun handleStop()
    {
        stopAudioAndCleanup()
        stopSelf()
    }

    private fun handleNotificationPermissionGranted() {
        if (session.isRunning.value) {
            updateNotification()
        }
    }

    @Synchronized
    private fun startAudioPlayback()
    {
        if (session.isRunning.value || audioThread?.isAlive == true) return

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PracticeTracker:DrumMachineWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }

        session.setRunning(true)

        audioThread = thread(
            start = true,
            priority = Thread.MAX_PRIORITY,
            name = "RhythmAudioThread"
        ) {
            runAudioLoop()
        }
    }

    @Synchronized
    private fun stopAudioPlayback()
    {
        if (!session.isRunning.value &&
            wakeLock == null &&
            audioThread == null
        ) return

        session.setRunning(false)

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        wakeLock = null

        audioThread?.interrupt()
        audioThread?.join(300)
        audioThread = null
    }

    private fun resetPlaybackHeadTracking() {
        lastRawHeadPos = 0L
        headWrapCount = 0L
    }

    private fun stopAudioAndCleanup() {
        unregisterNoisyReceiver()
        stopAudioPlayback()
        abandonAudioFocus()
        engine.resetPhase(resetTotalSamples = true)
        resetPlaybackHeadTracking()
        session.updatePlaybackEvent(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun runAudioLoop()
    {
        // Sets Linux thread priority to nice -19 (real-time audio priority)
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

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

        engine.resetPhase(resetTotalSamples = true)
        resetPlaybackHeadTracking()

        try {
            // Pre-fill the initial chunk so the hardware has data ready
            engine.fillNextChunk(buffer, session.bpm.value)
            audioTrack.write(buffer, 0, chunkSize)

            audioTrack.play()

            while (session.isRunning.value && !Thread.currentThread().isInterrupted)
            {
                engine.fillNextChunk(buffer, session.bpm.value)

                val result = audioTrack.write(buffer, 0, chunkSize)
                if (result < 0) {
                    Handler(Looper.getMainLooper()).post { handleStop() }
                    break // Audio route died (e.g. bluetooth disconnected)
                }

                // Synchronize visual state with physical DAC output
                val currentPlayedSample = getContinuousPlaybackSamples(audioTrack)
                var latestPlayedEvent: PlaybackEvent? = null

                // Drain all beats whose audio has already exited the DAC
                while (engine.playbackQueue.peek()?.let {
                    it.samplePosition <= currentPlayedSample } == true)
                {
                    latestPlayedEvent = engine.playbackQueue.poll()
                }

                // Update UI only when the beat has physically arrived at the speaker
                latestPlayedEvent?.let { event ->
                    session.updatePlaybackEvent(event)
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

            session.setRunning(false)
        }
    }

    // Handles 32-bit unsigned overflow (occurs after ~24.8 hours of continuous playback at 48kHz)
    private fun getContinuousPlaybackSamples(track: AudioTrack): Long
    {
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
            ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
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
            NotificationManager.IMPORTANCE_DEFAULT
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

    private fun createNotification(): Notification
    {
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

        val stopIntent = Intent(this, DrumMachineService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metronome")
            .setContentText("${session.bpm.value} BPM")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
}
