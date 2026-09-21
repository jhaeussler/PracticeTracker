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
import kotlin.math.sin

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

    // Pre-generate a short 1000 Hz sine wave click (10ms duration)
    private val clickSamples: ShortArray by lazy {
        val durationMs = 10
        val numSamples = ((SAMPLE_RATE / 1000.0) * durationMs).toInt()
        val samples = ShortArray(numSamples)
        val frequency = 1000.0 // 1 kHz tone
        for (i in 0 until numSamples) {
            // sin (2 * pi * x) -> sinus with amplitude 1 und period 1
            // sin (2 * pi * x / sr) -> will give us period of 44100 (samples)
            // -> multiply with Hz we actually want to compress the sin curve
            val angle = 2.0 * Math.PI * i * frequency / SAMPLE_RATE
            // Scale to 16-bit short max value (~32767) with a slight fade out
            val envelope = 1.0 - (i.toDouble() / numSamples)
            samples[i] = (sin(angle) * Short.MAX_VALUE * envelope).toInt().toShort()
        }
        samples
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

        audioTrack.play()

        // Fixed chunk size for writing to the hardware buffer
        val chunkSize = 512
        var sampleIndexInBeat = 0.0

        val buffer = ShortArray(chunkSize)

        while (isRunning.value) {
            // 1. Calculate how many silent samples belong between ticks for current BPM
            val samplesPerBeat = (SAMPLE_RATE * 60.0 / bpm.value).toInt()

            for (i in 0 until chunkSize)
            {
                val currentSampleIndexAsInt = sampleIndexInBeat.toInt()

                if (sampleIndexInBeat < clickSamples.size) {
                    buffer[i] = clickSamples[currentSampleIndexAsInt]
                } else {
                    buffer[i] = 0
                }

                sampleIndexInBeat += 1.0

                // samplesPerBeat = sr * 60 / BPM
                // BPM = 133: samplesPerBeat = 44100 * 60 / 133 = 19894.7368
                // rounded toInt() to 19894 -> dropping the .7368 -> drift after a while
                if (sampleIndexInBeat >= samplesPerBeat)
                {
                    // Preserves fractional remainder so no drift occurs due to rounding
                    sampleIndexInBeat -= samplesPerBeat
                }
            }

            audioTrack.write(buffer, 0, chunkSize)
        }

        audioTrack.stop()
        audioTrack.release()
    }
}