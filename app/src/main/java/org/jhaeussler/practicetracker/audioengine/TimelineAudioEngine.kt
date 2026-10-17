package org.jhaeussler.practicetracker.audioengine

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

/**
 * High-performance, cyclic timeline audio engine.
 *
 * Renders arbitrary rhythmic patterns along a normalized timeline [0.0, 1.0)
 * into real-time 16-bit PCM mono audio chunks using an additive multi-voice mixer.
 */
class TimelineAudioEngine(
    val sampleRate: Int = 48000,
    val maxVoices: Int = 16
) {
    /**
     * Monotonic 64-bit continuous sample count since engine startup or hard reset.
     * Used by the service and UI to synchronize visual state with DAC output.
     */
    var totalSamplesGenerated: Long = 0L
        private set

    /**
     * Lock-free queue delivering sample-accurate trigger events to the UI consumer.
     */
    val playbackQueue = ConcurrentLinkedQueue<PlaybackEvent>()

    @Volatile
    private var soundBank: SoundBank = SoundBank()

    @Volatile
    private var activePattern: TimelinePattern = TimelinePattern(4, emptyList())

    private val pendingPattern = AtomicReference<TimelinePattern?>(null)

    // Beat Segment Tracking
    private class BeatAnchor(
        val startRatio: Double,
        val endRatio: Double
    )

    private var beatAnchors: Array<BeatAnchor> = emptyArray()

    private var currentBeatIndex: Int = -1
    private var sampleIndexInCurrentBeat: Int = 0
    private var currentBeatDurationSamples: Int = 0
    private var beatStartSampleDouble: Double = 0.0

    // Pre-allocated scratch buffer for 32-bit mixing
    private var mixBuffer = IntArray(4096)

    // Polyphonic voice pool
    private val voicePool = Array(maxVoices) { Voice() }


    private class Voice {
        var samples: ShortArray? = null
        var readPosition: Int = 0
        var gain: Float = 1.0f
        var isActive: Boolean = false

        fun reset() {
            samples = null
            readPosition = 0
            gain = 1.0f
            isActive = false
        }

        fun mixInto(accum: IntArray, offset: Int, count: Int)
        {
            val buf = samples ?: return
            val available = buf.size - readPosition
            val toRender = minOf(count, available)

            for (i in 0 until toRender) {
                accum[offset + i] += (buf[readPosition + i] * gain).toInt()
            }

            readPosition += toRender
            if (readPosition >= buf.size) {
                isActive = false
            }
        }
    }

    init {
        updateBeatAnchors()
    }

    /**
     * Updates the active soundbank.
     * Safe to call while playback is active; in-flight voices will finish naturally.
     */
    fun setSoundBank(bank: SoundBank) {
        this.soundBank = bank
    }

    /**
     * Convenience method to update a single sound in the soundbank while running.
     */
    fun updateSound(soundId: SoundId, samples: ShortArray) {
        this.soundBank = this.soundBank.copyWith(soundId, samples)
    }

    /**
     * Stages a new timeline pattern.
     * The pattern will latch seamlessly once the currently playing beat completes.
     */
    fun setPattern(newPattern: TimelinePattern)
    {
        if (activePattern.events.isEmpty() ||
            totalSamplesGenerated == 0L ||
            currentBeatIndex == -1
        ) {
            activePattern = newPattern
            updateBeatAnchors()
            pendingPattern.set(null)
        } else {
            pendingPattern.set(newPattern)
        }
    }

    /**
     * Resets cycle phase to 0.0 (downbeat).
     *
     * @param resetTotalSamples If true, also resets [totalSamplesGenerated] and clears [playbackQueue].
     */
    fun resetPhase(resetTotalSamples: Boolean = false)
    {
        if (resetTotalSamples) {
            totalSamplesGenerated = 0L
            playbackQueue.clear()
        }

        currentBeatIndex = -1
        sampleIndexInCurrentBeat = 0
        currentBeatDurationSamples = 0
        beatStartSampleDouble = 0.0

        val pending = pendingPattern.getAndSet(null)

        if (pending != null) {
            activePattern = pending
        }

        updateBeatAnchors()

        for (i in voicePool.indices) {
            voicePool[i].reset()
        }
    }

    /**
     * Renders the next audio chunk into [buffer].
     *
     * MUST be called exclusively from the real-time audio thread.
     * Guarantees zero memory allocation and lock-free execution.
     *
     * @param buffer PCM 16-bit mono destination buffer to fill.
     * @param bpm Beats per minute reference for cycle duration.
     */
    fun fillNextChunk(buffer: ShortArray, bpm: Int)
    {
        if (buffer.isEmpty()) return

        val safeBpm = bpm.coerceAtLeast(1)
        val chunkSize = buffer.size

        if (mixBuffer.size < chunkSize) {
            mixBuffer = IntArray(chunkSize)
        }
        mixBuffer.fill(0, 0, chunkSize)

        var bufferOffset = 0

        while (bufferOffset < chunkSize) {
            // Check if we need to advance to the next beat segment
            if (currentBeatDurationSamples == 0 || sampleIndexInCurrentBeat >= currentBeatDurationSamples) {
                advanceToNextBeat(safeBpm)
            }

            val samplesRemainingInBeat = currentBeatDurationSamples - sampleIndexInCurrentBeat
            val sliceLength = minOf(chunkSize - bufferOffset, samplesRemainingInBeat)

            // 1. Render in-flight voices from previous slices FIRST
            for (i in 0 until voicePool.size) {
                val voice = voicePool[i]
                if (voice.isActive) {
                    voice.mixInto(mixBuffer, bufferOffset, sliceLength)
                }
            }

            // 2. Trigger new events scheduled in this slice SECOND (Zero-allocation index loop)
            triggerEventsInSlice(
                sliceStartInBeat = sampleIndexInCurrentBeat,
                sliceLength = sliceLength,
                chunkBufferOffset = bufferOffset
            )

            bufferOffset += sliceLength
            sampleIndexInCurrentBeat += sliceLength
            totalSamplesGenerated += sliceLength
        }

        // Clamp 32-bit mix accumulation into 16-bit PCM output buffer
        for (i in 0 until chunkSize)
        {
            val sample = mixBuffer[i]

            buffer[i] = when {
                sample > Short.MAX_VALUE -> Short.MAX_VALUE
                sample < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> sample.toShort()
            }
        }
    }

    private fun advanceToNextBeat(safeBpm: Int)
    {
        if (beatAnchors.isEmpty()) {
            updateBeatAnchors()
            if (beatAnchors.isEmpty()) return
        }

        if (currentBeatIndex == -1) {
            // Start of playback: Begin at Beat 0
            currentBeatIndex = 0
            beatStartSampleDouble = totalSamplesGenerated.toDouble()
        }
        else {
            val isLastBeatOfMeasure = currentBeatIndex + 1 >= beatAnchors.size

            if (isLastBeatOfMeasure)
            {
                // Tier 2 Latching Checkpoint: Downbeat / Cycle Wrap Boundary
                // Latch ANY pending pattern (including time signature / meter changes)
                val pending = pendingPattern.getAndSet(null)

                if (pending != null) {
                    activePattern = pending
                    updateBeatAnchors()
                }
                currentBeatIndex = 0
            }
            else {
                // Tier 1 Latching Checkpoint: Beat Boundary
                // Latch pending pattern ONLY if nominalBeats matches (same meter)
                val pending = pendingPattern.get()

                if (pending != null && pending.nominalBeats == activePattern.nominalBeats) {
                    activePattern = pendingPattern.getAndSet(null)!!
                    updateBeatAnchors()
                }
                currentBeatIndex++
            }
        }

        val anchor = beatAnchors[currentBeatIndex]
        val deltaBeats = (anchor.endRatio - anchor.startRatio) * activePattern.nominalBeats.coerceAtLeast(1)
        val exactBeatDuration = deltaBeats * (sampleRate.toDouble() * 60.0) / safeBpm

        val nextBeatStartDouble = beatStartSampleDouble + exactBeatDuration
        val currentStartInt = beatStartSampleDouble.roundToLong()
        val nextStartInt = nextBeatStartDouble.roundToLong()

        currentBeatDurationSamples = (nextStartInt - currentStartInt).toInt().coerceAtLeast(1)
        beatStartSampleDouble = nextBeatStartDouble
        sampleIndexInCurrentBeat = 0
    }

    private fun updateBeatAnchors()
    {
        val events = activePattern.events

        if (events.isEmpty()) {
            beatAnchors = emptyArray()
            return
        }

        val primaryRatios = ArrayList<Double>()

        for (i in events.indices)
        {
            val event = events[i]

            if (event.metadata.isPrimaryBeat)
            {
                if (primaryRatios.isEmpty() ||
                    event.positionRatio > primaryRatios[primaryRatios.size - 1] + 0.00001
                ) {
                    primaryRatios.add(event.positionRatio)
                }
            }
        }

        if (primaryRatios.isEmpty()) {
            beatAnchors = arrayOf(BeatAnchor(0.0, 1.0))
            return
        }

        if (primaryRatios[0] > 0.00001) {
            primaryRatios.add(0, 0.0)
        }

        val anchors = Array(primaryRatios.size) { i ->

            val startRatio = primaryRatios[i]

            val endRatio = if (i + 1 < primaryRatios.size) {
                primaryRatios[i + 1]
            } else {
                1.0
            }

            BeatAnchor(startRatio, endRatio)
        }
        beatAnchors = anchors
    }

    private fun triggerEventsInSlice(
        sliceStartInBeat: Int,
        sliceLength: Int,
        chunkBufferOffset: Int
    ) {
        if (beatAnchors.isEmpty()) return

        val anchor = beatAnchors[currentBeatIndex]
        val segmentSpan = anchor.endRatio - anchor.startRatio

        if (segmentSpan <= 0.0) return

        val sliceEndInBeat = sliceStartInBeat + sliceLength
        val events = activePattern.events

        for (i in events.indices)
        {
            val event = events[i]
            val eventRatio = event.positionRatio

            // Check if event belongs to this beat segment
            if (eventRatio >= anchor.startRatio && eventRatio < anchor.endRatio)
            {
                val alpha = (eventRatio - anchor.startRatio) / segmentSpan
                val eventOffsetInBeat = (alpha * currentBeatDurationSamples).roundToLong().toInt()

                if (eventOffsetInBeat in sliceStartInBeat until sliceEndInBeat)
                {
                    val delayInSlice = eventOffsetInBeat - sliceStartInBeat
                    val triggerOffsetInChunk = chunkBufferOffset + delayInSlice

                    val sampleData = soundBank.get(event.soundId)

                    if (sampleData != null && event.gain > 0f)
                    {
                        val voice = allocateVoice()

                        voice.samples = sampleData
                        voice.readPosition = 0
                        voice.gain = event.gain
                        voice.isActive = true

                        voice.mixInto(
                            mixBuffer,
                            triggerOffsetInChunk,
                            sliceLength - delayInSlice
                        )
                    }

                    val globalSamplePos = totalSamplesGenerated + delayInSlice

                    playbackQueue.add(
                        PlaybackEvent(globalSamplePos, event.metadata)
                    )
                }
            }
        }
    }

    private fun allocateVoice(): Voice
    {
        for (i in voicePool.indices) {
            val v = voicePool[i]
            if (!v.isActive) return v
        }

        var maxPos = -1
        var chosen = voicePool[0]

        for (i in voicePool.indices)
        {
            val v = voicePool[i]
            if (v.readPosition > maxPos) {
                maxPos = v.readPosition
                chosen = v
            }
        }

        chosen.reset()
        return chosen
    }
}