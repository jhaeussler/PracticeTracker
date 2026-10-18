package org.jhaeussler.practicetracker.drummachine.engine

import org.jhaeussler.practicetracker.drummachine.sound.SoundBank
import java.util.Arrays
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

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

            var i = 0
            while (i < toRender) {
                accum[offset + i] += (buf[readPosition + i] * gain).toInt()
                i++
            }

            readPosition += toRender
            if (readPosition >= buf.size) {
                isActive = false
            }
        }
    }

    // Diagnostic contract accessors for verification
    internal fun getActivePattern(): TimelinePattern = activePattern
    internal fun getCurrentBeatIndex(): Int = currentBeatIndex

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
     * Only mutates immediately if playback has NOT yet started.
     * Once running, it ALWAYS stages via pendingPattern to guarantee lock-free latching.
     */
    fun setPattern(newPattern: TimelinePattern)
    {
        if (totalSamplesGenerated == 0L ||
            currentBeatIndex == -1
        ) {
            activePattern = newPattern
            pendingPattern.set(null)
        }
        else {
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

        val poolSize = voicePool.size
        var i = 0

        while (i < poolSize) {
            voicePool[i].reset()
            i++
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
        val chunkSize = buffer.size
        if (chunkSize == 0) return

        val safeBpm = if (bpm < 1) 1 else bpm

        if (mixBuffer.size < chunkSize) {
            mixBuffer = IntArray(chunkSize)
        }
        Arrays.fill(mixBuffer, 0, chunkSize, 0)

        var bufferOffset = 0

        while (bufferOffset < chunkSize) {
            if (currentBeatDurationSamples == 0 || sampleIndexInCurrentBeat >= currentBeatDurationSamples) {
                advanceToNextBeat(safeBpm)
            }

            if (currentBeatDurationSamples <= 0) currentBeatDurationSamples = chunkSize

            val samplesRemainingInBeat = currentBeatDurationSamples - sampleIndexInCurrentBeat
            val needed = chunkSize - bufferOffset
            val sliceLength = if (needed < samplesRemainingInBeat) needed else samplesRemainingInBeat

            // 1. Render in-flight voices from previous slices FIRST
            val poolSize = voicePool.size
            var vIdx = 0

            while (vIdx < poolSize)
            {
                val voice = voicePool[vIdx]

                if (voice.isActive) {
                    voice.mixInto(mixBuffer, bufferOffset, sliceLength)
                }
                vIdx++
            }

            // 2. Trigger new events scheduled in this slice SECOND
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
        var cIdx = 0
        while (cIdx < chunkSize)
        {
            val sample = mixBuffer[cIdx]

            buffer[cIdx] = if (sample > 32767) {
                32767
            }
            else if (sample < -32768) {
                -32768
            }
            else {
                sample.toShort()
            }

            cIdx++
        }
    }

    private fun advanceToNextBeat(safeBpm: Int)
    {
        var anchors = activePattern.beatAnchors
        val anchorCount = anchors.size
        if (anchorCount == 0) return

        if (currentBeatIndex == -1)
        {
            // Start of playback: Begin at Beat 0
            currentBeatIndex = 0
            beatStartSampleDouble = totalSamplesGenerated.toDouble()
        }
        else {
            val isLastBeatOfMeasure = currentBeatIndex + 1 >= anchorCount

            if (isLastBeatOfMeasure)
            {
                // Tier 2: Downbeat Wrap Boundary (Any meter change accepted)
                val pending = pendingPattern.getAndSet(null)
                if (pending != null) {
                    activePattern = pending
                    anchors = pending.beatAnchors
                }
                currentBeatIndex = 0
            }
            else {
                // Tier 1: Beat Boundary (Same nominalBeats only, race-safe CAS)
                val pending = pendingPattern.get()
                if (pending != null && pending.nominalBeats == activePattern.nominalBeats)
                {
                    if (pendingPattern.compareAndSet(pending, null)) {
                        activePattern = pending
                        anchors = pending.beatAnchors
                    }
                }
                currentBeatIndex++
            }
        }

        val maxAnchorIndex = anchors.size - 1
        val safeBeatIndex = if (currentBeatIndex < 0) 0 else if (currentBeatIndex > maxAnchorIndex) maxAnchorIndex else currentBeatIndex
        val anchor = anchors[safeBeatIndex]

        val rawNominal = activePattern.nominalBeats
        val safeNominal = if (rawNominal < 1) 1 else rawNominal

        val deltaBeats = (anchor.endRatio - anchor.startRatio) * safeNominal
        val exactBeatDuration = deltaBeats * (sampleRate.toDouble() * 60.0) / safeBpm

        val nextBeatStartDouble = beatStartSampleDouble + exactBeatDuration
        val currentStartInt = Math.round(beatStartSampleDouble)
        val nextStartInt = Math.round(nextBeatStartDouble)

        val durationDiff = (nextStartInt - currentStartInt).toInt()
        currentBeatDurationSamples = if (durationDiff < 1) 1 else durationDiff

        beatStartSampleDouble = nextBeatStartDouble
        sampleIndexInCurrentBeat = 0
    }

    private fun triggerEventsInSlice(
        sliceStartInBeat: Int,
        sliceLength: Int,
        chunkBufferOffset: Int
    ) {
        val pattern = activePattern
        val anchors = pattern.beatAnchors
        val anchorCount = anchors.size

        if (anchorCount == 0 || currentBeatIndex < 0 || currentBeatIndex >= anchorCount) return

        val anchor = anchors[currentBeatIndex]
        val segmentSpan = anchor.endRatio - anchor.startRatio
        if (segmentSpan <= 0.0) return

        val events = pattern.events
        val eventCount = events.size
        if (eventCount == 0) return

        val sliceEndInBeat = sliceStartInBeat + sliceLength

        var eIdx = 0
        while (eIdx < eventCount)
        {
            val event = events[eIdx]
            val eventRatio = event.positionRatio

            // Check if event belongs to this beat segment
            if (eventRatio >= anchor.startRatio && eventRatio < anchor.endRatio)
            {
                val alpha = (eventRatio - anchor.startRatio) / segmentSpan
                val calculatedOffset = Math.round(alpha * currentBeatDurationSamples).toInt()
                val maxOffset = currentBeatDurationSamples - 1
                val eventOffsetInBeat = if (calculatedOffset > maxOffset) maxOffset else if (calculatedOffset < 0) 0 else calculatedOffset

                if (eventOffsetInBeat >= sliceStartInBeat && eventOffsetInBeat < sliceEndInBeat)
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
                    playbackQueue.add(PlaybackEvent(globalSamplePos, event.metadata))
                }
            }
            eIdx++
        }
    }

    private fun allocateVoice(): Voice
    {
        val poolSize = voicePool.size

        var i = 0
        while (i < poolSize)
        {
            val v = voicePool[i]

            if (!v.isActive) return v

            i++
        }

        var maxPos = -1
        var chosen = voicePool[0]

        i = 0

        while (i < poolSize)
        {
            val v = voicePool[i]

            if (v.readPosition > maxPos) {
                maxPos = v.readPosition
                chosen = v
            }

            i++
        }

        chosen.reset()
        return chosen
    }
}