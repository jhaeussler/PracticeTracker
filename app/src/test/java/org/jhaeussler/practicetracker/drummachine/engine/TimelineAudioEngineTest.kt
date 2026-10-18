/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.drummachine.engine

import org.jhaeussler.practicetracker.drummachine.sound.SoundBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

class TimelineAudioEngineTest {

    companion object {
        private const val TEST_SAMPLE_RATE = 48000
        private const val TEST_BPM = 120
    }

    private lateinit var engine: TimelineAudioEngine
    private lateinit var defaultSoundBank: SoundBank

    // Distinct sample values to prevent cross-beat value collision
    private val accentSample = ShortArray(100) { ((it + 1) * 100 + 12000).toShort() } // Starts at 12100
    private val beatSample = ShortArray(100) { ((it + 1) * 50 + 5000).toShort() }     // Starts at 5050
    private val subSample = ShortArray(100) { ((it + 1) * 20 + 1000).toShort() }      // Starts at 1020
    private val loudSampleA = ShortArray(100) { 25000.toShort() }
    private val loudSampleB = ShortArray(100) { 20000.toShort() }

    private fun create4_4Pattern(): TimelinePattern {
        return TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(
                    0.00,
                    SoundId.CLICK_ACCENT,
                    1.0f,
                    EventMetadata(0, 0, isPrimaryBeat = true, isDownbeat = true)
                ),
                TimelineEvent(
                    0.25,
                    SoundId.CLICK_BEAT,
                    1.0f,
                    EventMetadata(1, 0, isPrimaryBeat = true, isDownbeat = false)
                ),
                TimelineEvent(
                    0.50,
                    SoundId.CLICK_BEAT,
                    1.0f,
                    EventMetadata(2, 0, isPrimaryBeat = true, isDownbeat = false)
                ),
                TimelineEvent(
                    0.75,
                    SoundId.CLICK_BEAT,
                    1.0f,
                    EventMetadata(3, 0, isPrimaryBeat = true, isDownbeat = false)
                )
            )
        )
    }

    @Before
    fun setUp() {
        defaultSoundBank = SoundBank(
            mapOf(
                SoundId.CLICK_ACCENT to accentSample,
                SoundId.CLICK_BEAT to beatSample,
                SoundId.CLICK_SUBDIVISION to subSample,
                SoundId.DRUM_KICK to loudSampleA,
                SoundId.DRUM_SNARE to loudSampleB
            )
        )
        engine = TimelineAudioEngine(sampleRate = TEST_SAMPLE_RATE, maxVoices = 16)
        engine.setSoundBank(defaultSoundBank)
        engine.setPattern(create4_4Pattern())
    }

    // =========================================================================
    // 1. Timing Physics & Drift Prevention
    // =========================================================================

    @Test(timeout = 10000)
    fun `sub-sample fractional remainder prevents timing drift across 100 cycles`() {
        engine.resetPhase(resetTotalSamples = true)

        val bpm = 133
        val exactSamplesPerCycle = TEST_SAMPLE_RATE.toDouble() * 4.0 * 60.0 / bpm
        val totalCycles = 100
        val expected100thDownbeat = (exactSamplesPerCycle * totalCycles).roundToLong()

        val chunkSize = 512
        val tempBuffer = ShortArray(chunkSize)
        var globalSampleIndex = 0L
        var actual100thDownbeat = -1L

        var downbeatsDetected = 0
        var silentSpan = 0

        val maxSamplesToSearch = (exactSamplesPerCycle * (totalCycles + 5)).roundToLong()

        while (downbeatsDetected < totalCycles && globalSampleIndex < maxSamplesToSearch) {
            engine.fillNextChunk(tempBuffer, bpm)

            for (i in tempBuffer.indices) {
                val sample = tempBuffer[i]
                val currentGlobalSample = globalSampleIndex + i

                if (sample == accentSample[0]) {
                    if (silentSpan >= 500) {
                        if (currentGlobalSample > 0) {
                            downbeatsDetected++
                        }
                        if (downbeatsDetected == totalCycles) {
                            actual100thDownbeat = currentGlobalSample
                            break
                        }
                    }
                    silentSpan = 0
                } else {
                    silentSpan++
                }
            }

            if (actual100thDownbeat != -1L) break
            globalSampleIndex += chunkSize
        }

        assertTrue("Timed out: only detected $downbeatsDetected / $totalCycles downbeats", downbeatsDetected == totalCycles)

        assertEquals(
            "Drift detected across 100 cycles!",
            expected100thDownbeat,
            actual100thDownbeat
        )
    }

    @Test
    fun `events spaced evenly across 4 beats match exact sample distance for 120 BPM`() {
        engine.resetPhase(resetTotalSamples = true)

        val expectedSamplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / TEST_BPM).toInt()

        val totalBuffer = ShortArray(96000)
        val chunkSize = 1000
        var offset = 0
        while (offset < totalBuffer.size) {
            val chunk = ShortArray(chunkSize)
            engine.fillNextChunk(chunk, TEST_BPM)
            System.arraycopy(chunk, 0, totalBuffer, offset, chunkSize)
            offset += chunkSize
        }

        assertEquals(accentSample[0], totalBuffer[0])
        assertEquals(beatSample[0], totalBuffer[expectedSamplesPerBeat])
        assertEquals(beatSample[0], totalBuffer[expectedSamplesPerBeat * 2])
        assertEquals(beatSample[0], totalBuffer[expectedSamplesPerBeat * 3])
    }

    @Test
    fun `events placed at arbitrary ratios (0_1, 0_674, 0_875) trigger at exact sample offsets`() {
        engine.resetPhase(resetTotalSamples = true)
        val cycleSamples = 96000

        val arbitraryPattern = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.0, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0)),
                TimelineEvent(0.1, SoundId.CLICK_BEAT, 1.0f, EventMetadata(1, 0)),
                TimelineEvent(0.674, SoundId.CLICK_BEAT, 1.0f, EventMetadata(2, 0)),
                TimelineEvent(0.875, SoundId.CLICK_BEAT, 1.0f, EventMetadata(3, 0))
            )
        )
        engine.setPattern(arbitraryPattern)

        val fullCycleBuffer = ShortArray(cycleSamples)
        val chunkSize = 500
        var written = 0
        while (written < cycleSamples) {
            val chunk = ShortArray(chunkSize)
            engine.fillNextChunk(chunk, TEST_BPM)
            System.arraycopy(chunk, 0, fullCycleBuffer, written, chunkSize)
            written += chunkSize
        }

        val expectedOffsetBeat1 = (0.1 * cycleSamples).roundToLong().toInt()
        val expectedOffsetBeat2 = (0.674 * cycleSamples).roundToLong().toInt()
        val expectedOffsetBeat3 = (0.875 * cycleSamples).roundToLong().toInt()

        assertEquals(accentSample[0], fullCycleBuffer[0])
        assertEquals(beatSample[0], fullCycleBuffer[expectedOffsetBeat1])
        assertEquals(beatSample[0], fullCycleBuffer[expectedOffsetBeat2])
        assertEquals(beatSample[0], fullCycleBuffer[expectedOffsetBeat3])
    }

    // =========================================================================
    // 2. Responsive BPM Transitions (Beat Boundary Latching)
    // =========================================================================

    @Test
    fun `changing BPM mid-beat finishes active beat at old tempo and applies new tempo at next beat boundary`() {
        engine.resetPhase(resetTotalSamples = true)
        // 4/4 at 60 BPM: 48,000 samples per beat
        // 4/4 at 120 BPM: 24,000 samples per beat

        // 1. Render first 10,000 samples of Beat 0 at 60 BPM
        val initialChunk = ShortArray(10000)
        engine.fillNextChunk(initialChunk, bpm = 60)
        assertEquals(accentSample[0], initialChunk[0])

        // 2. User sets BPM to 120 while Beat 0 is still playing.
        // Render remaining 38,000 samples of Beat 0 at the new BPM argument.
        val finishBeat0Chunk = ShortArray(38000)
        engine.fillNextChunk(finishBeat0Chunk, bpm = 120)

        // 3. Beat 1 must now begin at global sample 48,000, and last 24,000 samples (120 BPM).
        // Beat 2 must follow 24,000 samples later at global sample 72,000.
        val beats1And2Chunk = ShortArray(48000) // Spans samples 48,000 to 96,000
        engine.fillNextChunk(beats1And2Chunk, bpm = 120)

        // Beat 1 triggers at global sample 48,000 (offset 0 in this chunk)
        assertEquals("Beat 1 must trigger at global sample 48,000", beatSample[0], beats1And2Chunk[0])

        // Beat 2 triggers at global sample 72,000 (offset 24,000 in this chunk)
        assertEquals("Beat 2 must trigger at global sample 72,000 (24,000 samples after Beat 1)", beatSample[0], beats1And2Chunk[24000])
    }

    // =========================================================================
    // 3. Additive Mixing & Polyphony
    // =========================================================================

    @Test
    fun `simultaneous events at identical position sum additively without overwriting`() {
        engine.resetPhase(resetTotalSamples = true)
        val polyPattern = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.0, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0)),
                TimelineEvent(0.0, SoundId.CLICK_BEAT, 1.0f, EventMetadata(0, 0))
            )
        )
        engine.setPattern(polyPattern)

        val buffer = ShortArray(100)
        engine.fillNextChunk(buffer, bpm = 120)

        val expectedSum = (accentSample[0] + beatSample[0]).toShort()
        assertEquals(expectedSum, buffer[0])
    }

    @Test
    fun `additive mixing clamps to 16-bit short bounds without integer overflow wrapping`() {
        engine.resetPhase(resetTotalSamples = true)
        val loudPattern = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.0, SoundId.DRUM_KICK, 1.0f, EventMetadata(0, 0)),
                TimelineEvent(0.0, SoundId.DRUM_SNARE, 1.0f, EventMetadata(0, 0))
            )
        )
        engine.setPattern(loudPattern)

        val buffer = ShortArray(100)
        engine.fillNextChunk(buffer, bpm = 120)

        assertEquals(Short.MAX_VALUE, buffer[0])
    }

    // =========================================================================
    // 4. Dynamic SoundBank Hot-Swapping
    // =========================================================================

    @Test
    fun `updating soundBank mid-playback updates audio on next trigger without clicks`() {
        engine.resetPhase(resetTotalSamples = true)
        val beatSamples = 24000
        val chunk = ShortArray(beatSamples)

        engine.fillNextChunk(chunk, TEST_BPM)
        assertEquals(accentSample[0], chunk[0])

        val newBeatSound = ShortArray(100) { 777.toShort() }
        val updatedBank = defaultSoundBank.copyWith(SoundId.CLICK_BEAT, newBeatSound)
        engine.setSoundBank(updatedBank)

        engine.fillNextChunk(chunk, TEST_BPM)
        assertEquals(newBeatSound[0], chunk[0])
    }

    // =========================================================================
    // 5. Two-Tier Latching (Beat Boundary vs Downbeat)
    // =========================================================================

    @Test
    fun `subdivision pattern change with same nominalBeats latches at beat boundary`() {
        engine.resetPhase(resetTotalSamples = true)

        val customSound = ShortArray(100) { 9999.toShort() }
        engine.setSoundBank(defaultSoundBank.copyWith(SoundId.DRUM_CLAP, customSound))

        val patternB = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.00, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0)),
                TimelineEvent(0.25, SoundId.DRUM_CLAP, 1.0f, EventMetadata(1, 0)),
                TimelineEvent(0.50, SoundId.CLICK_BEAT, 1.0f, EventMetadata(2, 0)),
                TimelineEvent(0.75, SoundId.CLICK_BEAT, 1.0f, EventMetadata(3, 0))
            )
        )

        // 1. Render first 10,000 samples of Beat 0
        val partialChunk = ShortArray(10000)
        engine.fillNextChunk(partialChunk, TEST_BPM)
        assertEquals(accentSample[0], partialChunk[0])

        // 2. User stages Pattern B (same 4 beats, different subdivision/sound)
        engine.setPattern(patternB)

        // 3. Render remaining 14,000 samples of Beat 0
        val finishBeat0Chunk = ShortArray(14000)
        engine.fillNextChunk(finishBeat0Chunk, TEST_BPM)
        assertEquals(0.toShort(), finishBeat0Chunk[0])

        // 4. Beat 1 begins -> Pattern B must now be active
        val beat1Chunk = ShortArray(10000)
        engine.fillNextChunk(beat1Chunk, TEST_BPM)
        assertEquals(customSound[0], beat1Chunk[0])
    }

    @Test
    fun `meter change with different nominalBeats waits for downbeat to latch`() {
        engine.resetPhase(resetTotalSamples = true)

        val clapSound = ShortArray(100) { 15000.toShort() }
        engine.setSoundBank(defaultSoundBank.copyWith(SoundId.DRUM_CLAP, clapSound))

        val pattern3_4 = TimelinePattern(
            nominalBeats = 3,
            events = listOf(
                TimelineEvent(
                    0.000,
                    SoundId.DRUM_CLAP,
                    1.0f,
                    EventMetadata(0, 0, isPrimaryBeat = true, isDownbeat = true)
                ),
                TimelineEvent(
                    0.333,
                    SoundId.CLICK_BEAT,
                    1.0f,
                    EventMetadata(1, 0, isPrimaryBeat = true, isDownbeat = false)
                ),
                TimelineEvent(
                    0.666,
                    SoundId.CLICK_BEAT,
                    1.0f,
                    EventMetadata(2, 0, isPrimaryBeat = true, isDownbeat = false)
                )
            )
        )

        // 1. Render Beat 0 of 4/4 (24,000 samples)
        val beat0Chunk = ShortArray(24000)
        engine.fillNextChunk(beat0Chunk, TEST_BPM)
        assertEquals(accentSample[0], beat0Chunk[0])

        // 2. User switches to 3/4 time signature during playback
        engine.setPattern(pattern3_4)

        // 3. Render Beats 1, 2, and 3 of the remaining 4/4 bar (72,000 samples).
        // It MUST NOT latch Pattern 3/4 at Beat 1; it must complete all 4 beats!
        val remaining4_4Chunk = ShortArray(72000)
        engine.fillNextChunk(remaining4_4Chunk, TEST_BPM)

        assertEquals("Beat 1 must still play from 4/4 pattern", beatSample[0], remaining4_4Chunk[0])
        assertEquals("Beat 2 must still play from 4/4 pattern", beatSample[0], remaining4_4Chunk[24000])
        assertEquals("Beat 3 must still play from 4/4 pattern", beatSample[0], remaining4_4Chunk[48000])

        // 4. Now the 4/4 measure has completed (total 96,000 samples).
        // Downbeat of new 3/4 pattern triggers with DRUM_CLAP!
        val newMeasureChunk = ShortArray(24000)
        engine.fillNextChunk(newMeasureChunk, TEST_BPM)
        assertEquals("New 3/4 pattern downbeat must trigger after old bar finishes", clapSound[0], newMeasureChunk[0])
    }

    // =========================================================================
    // 6. Buffer Straddling & Queue Sync
    // =========================================================================

    @Test
    fun `buffer straddling the cycle wrap-around point triggers downbeat at exact split`() {
        engine.resetPhase(resetTotalSamples = true)

        val preChunk = ShortArray(94000)
        engine.fillNextChunk(preChunk, TEST_BPM)

        val straddlingBuffer = ShortArray(5000)
        engine.fillNextChunk(straddlingBuffer, TEST_BPM)

        assertEquals(0.toShort(), straddlingBuffer[1999])
        assertEquals(accentSample[0], straddlingBuffer[2000])
    }

    @Test
    fun `playbackQueue delivers sample-accurate events matching physical DAC consumption`() {
        engine.resetPhase(resetTotalSamples = true)
        val samplesPerBeat = 24000L

        val buffer = ShortArray(samplesPerBeat.toInt() * 3)
        engine.fillNextChunk(buffer, TEST_BPM)

        val events = mutableListOf<PlaybackEvent>()
        while (!engine.playbackQueue.isEmpty()) {
            events.add(engine.playbackQueue.poll()!!)
        }

        assertEquals(3, events.size)
        assertEquals(0L, events[0].samplePosition)
        assertEquals(0, events[0].metadata.beatIndex)

        assertEquals(samplesPerBeat, events[1].samplePosition)
        assertEquals(1, events[1].metadata.beatIndex)

        assertEquals(samplesPerBeat * 2, events[2].samplePosition)
        assertEquals(2, events[2].metadata.beatIndex)
    }

    @Test
    fun `fillNextChunk handles edge cases (0 BPM, negative BPM, empty buffer) gracefully`() {
        engine.resetPhase(resetTotalSamples = true)

        val buffer = ShortArray(512)

        val samplesBeforeZeroBpm = engine.totalSamplesGenerated
        engine.fillNextChunk(buffer, bpm = 0)

        assertEquals("Engine must advance sample counter even when BPM is 0",
            samplesBeforeZeroBpm + buffer.size, engine.totalSamplesGenerated)
        assertEquals("Engine must render audio (downbeat) even when BPM was coerced from 0",
            accentSample[0], buffer[0])

        val samplesBeforeNegBpm = engine.totalSamplesGenerated
        engine.fillNextChunk(buffer, bpm = -50)

        assertEquals("Engine must advance sample counter when BPM is negative",
            samplesBeforeNegBpm + buffer.size, engine.totalSamplesGenerated)

        val emptyBuffer = ShortArray(0)
        val samplesBeforeEmpty = engine.totalSamplesGenerated
        engine.fillNextChunk(emptyBuffer, bpm = 120)

        assertEquals("Empty buffer must not alter sample counter",
            samplesBeforeEmpty, engine.totalSamplesGenerated)
    }

    @Test
    fun `tier 1 latching updates activePattern and preserves beatIndex continuity at beat boundary`() {
        engine.resetPhase(resetTotalSamples = true)

        val initialPattern = create4_4Pattern()
        engine.setPattern(initialPattern)

        val buffer = ShortArray(512)
        // Enter Beat 0
        engine.fillNextChunk(buffer, TEST_BPM)
        assertEquals(initialPattern, engine.getActivePattern())
        assertEquals(0, engine.getCurrentBeatIndex())

        val patternB = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.00, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0))
            )
        )
        engine.setPattern(patternB)

        // Render remaining samples of Beat 0 (24,000 total per beat at 120 BPM)
        var rendered = 512
        while (rendered < 24000) {
            engine.fillNextChunk(buffer, TEST_BPM)
            rendered += buffer.size
        }

        // At Beat 1 boundary: Pattern B latches without resetting currentBeatIndex to 0
        assertEquals(1, engine.getCurrentBeatIndex())
        assertEquals("Pattern B must be active at beat 1 boundary", patternB, engine.getActivePattern())
    }

    @Test
    fun `tier 2 latching defers activePattern update until downbeat 0_0 cycle wrap`() {
        engine.resetPhase(resetTotalSamples = true)

        val initialPattern = create4_4Pattern()
        engine.setPattern(initialPattern)

        val buffer = ShortArray(512)
        engine.fillNextChunk(buffer, TEST_BPM)
        assertEquals(0, engine.getCurrentBeatIndex())

        val pattern3 = TimelinePattern(
            nominalBeats = 3,
            events = listOf(
                TimelineEvent(0.0, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0))
            )
        )
        engine.setPattern(pattern3)

        // Render past beat 0 into beat 1 (samples 512 to 24512)
        var rendered = 512
        while (rendered < 24000 + 512) {
            engine.fillNextChunk(buffer, TEST_BPM)
            rendered += buffer.size
        }

        // Must NOT latch yet because nominalBeats differs (4 != 3)
        assertEquals(1, engine.getCurrentBeatIndex())
        assertEquals("Initial 4/4 pattern must remain active mid-measure", initialPattern, engine.getActivePattern())

        // Render remaining beats (1, 2, 3) until cycle wrap (4 * 24000 = 96000 samples)
        while (rendered < 96000 + 512) {
            engine.fillNextChunk(buffer, TEST_BPM)
            rendered += buffer.size
        }

        // Measure completed: wraps to downbeat Beat 0 and latches Pattern 3
        assertEquals(0, engine.getCurrentBeatIndex())
        assertEquals("Pattern 3 must latch on downbeat 0.0 wrap", pattern3, engine.getActivePattern())
    }

    @Test
    fun `in-flight voices continue natural decay across pattern latch boundary without truncation`() {
        engine.resetPhase(resetTotalSamples = true)

        // 1. Load a long voice (30,000 samples = spans across Beat 0's 24,000 samples into Beat 1)
        val longVoice = ShortArray(30000) { 4000 }
        engine.updateSound(SoundId.CLICK_ACCENT, longVoice)

        // 2. Pattern B has NO event on Beat 1, so any sound in Beat 1 MUST come from the in-flight voice
        val patternB = TimelinePattern(
            nominalBeats = 4,
            events = listOf(
                TimelineEvent(0.00, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0))
            )
        )

        // 3. Start Beat 0 (triggers longVoice)
        val initialChunk = ShortArray(10000)
        engine.fillNextChunk(initialChunk, TEST_BPM)
        assertEquals(4000.toShort(), initialChunk[0])

        // 4. Stage Pattern B mid-playback
        engine.setPattern(patternB)

        // 5. Render remaining 14,000 samples of Beat 0 (total 24,000 samples rendered)
        val finishBeat0Chunk = ShortArray(14000)
        engine.fillNextChunk(finishBeat0Chunk, TEST_BPM)

        // 6. Render first 5,000 samples of Beat 1: advances engine into Beat 1 and latches Pattern B
        val beat1Chunk = ShortArray(5000)
        engine.fillNextChunk(beat1Chunk, TEST_BPM)

        // Beat 1 has now begun: Pattern B is active and beat index is 1
        assertEquals(1, engine.getCurrentBeatIndex())
        assertEquals(patternB, engine.getActivePattern())

        // The 6,000 remaining samples of longVoice must still be playing
        assertTrue(
            "In-flight voice must remain active across latch into next pattern",
            beat1Chunk.all { it == 4000.toShort() }
        )
    }

    // =========================================================================
    // Real-Time Zero-Allocation & Concurrency Stress Verification
    // =========================================================================

    @Suppress("DEPRECATION")
    private fun getCurrentThreadId(): Long {
        return try {
            val method = Thread::class.java.getMethod("threadId")
            method.invoke(Thread.currentThread()) as Long
        } catch (_: Throwable) {
            Thread.currentThread().id
        }
    }

    private fun getThreadAllocatedBytes(targetThreadId: Long): Long? {
        return try {
            val factoryClass = Class.forName("java.lang.management.ManagementFactory")
            val mxBean = factoryClass.getMethod("getThreadMXBean").invoke(null)
            val sunThreadBeanClass = Class.forName("com.sun.management.ThreadMXBean")
            if (sunThreadBeanClass.isInstance(mxBean)) {
                val method = sunThreadBeanClass.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
                method.invoke(mxBean, targetThreadId) as Long
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Test
    fun `audio thread renders and latches patterns with strictly zero heap allocations`() {
        val testSupported = getThreadAllocatedBytes(getCurrentThreadId())
        if (testSupported == null) {
            println("Thread allocated memory measurement not supported on this JVM runtime. Skipping.")
            return
        }

        engine.resetPhase(resetTotalSamples = true)

        val patternA = TimelinePattern(nominalBeats = 4, events = emptyList())
        val patternB = TimelinePattern(nominalBeats = 4, events = emptyList())

        engine.setPattern(patternA)

        val isWarmedUp = AtomicBoolean(false)
        val startRound = AtomicBoolean(false)
        val roundDone = AtomicBoolean(false)
        val canTerminate = AtomicBoolean(false)
        var audioThreadId = 0L

        val audioThread = Thread {
            audioThreadId = getCurrentThreadId()
            val buffer = ShortArray(512)

            // Warmup JIT
            var w = 0
            while (w < 200) {
                if (w == 50) engine.setPattern(patternB)
                if (w == 100) engine.setPattern(patternA)
                engine.fillNextChunk(buffer, TEST_BPM)
                w++
            }

            isWarmedUp.set(true)

            // Measurement rounds
            while (!canTerminate.get()) {
                while (!startRound.get() && !canTerminate.get()) {
                }
                if (canTerminate.get()) break

                var i = 0
                while (i < 100) {
                    engine.fillNextChunk(buffer, TEST_BPM)
                    i++
                }

                roundDone.set(true)
                while (startRound.get() && !canTerminate.get()) {
                }
            }
        }

        audioThread.start()

        while (!isWarmedUp.get()) {
            Thread.sleep(1)
        }

        // Test up to 3 rounds: filters out one-time JVM TLAB flushes (~1.9 KB)
        var minAllocatedBytes = Long.MAX_VALUE

        var round = 1
        while (round <= 3) {
            engine.setPattern(if (round % 2 == 1) patternB else patternA)

            val startBytes = getThreadAllocatedBytes(audioThreadId)!!
            startRound.set(true)

            while (!roundDone.get()) {
                Thread.sleep(1)
            }

            val endBytes = getThreadAllocatedBytes(audioThreadId)!!
            startRound.set(false)
            roundDone.set(false)

            val delta = endBytes - startBytes
            if (delta < minAllocatedBytes) {
                minAllocatedBytes = delta
            }

            if (minAllocatedBytes == 0L) {
                break // Strictly zero allocations confirmed!
            }
            round++
        }

        canTerminate.set(true)
        audioThread.join()

        assertEquals(
            "Audio thread must allocate strictly 0 bytes during steady rendering and pattern latching",
            0L,
            minAllocatedBytes
        )
    }

    @Test
    fun `multithreaded stress test verifies zero drift and no deadlocks under rapid pattern swapping`() {
        engine.resetPhase(resetTotalSamples = true)

        val pattern4 = create4_4Pattern()
        val pattern3 = TimelinePattern(
            nominalBeats = 3,
            events = listOf(
                TimelineEvent(0.0, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0))
            )
        )
        val pattern5 = TimelinePattern(
            nominalBeats = 5,
            events = listOf(
                TimelineEvent(0.0, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0))
            )
        )

        engine.setPattern(pattern4)

        val chunkSize = 512
        val totalChunks = 2000 // 1,024,000 samples (~21.3s of 48kHz audio)
        val buffer = ShortArray(chunkSize)

        val running = AtomicBoolean(true)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        // 1. Mutator thread: Hammers setPattern with 4/4, 3/4, 5/4 at ~200 swaps/second
        val uiThread = Thread {
            startLatch.await()
            val patterns = arrayOf(pattern4, pattern3, pattern5)
            var counter = 0
            while (running.get()) {
                engine.setPattern(patterns[counter % patterns.size])
                counter++
                Thread.sleep(5)
            }
            finishLatch.countDown()
        }

        // 2. Audio thread: Continuous real-time streaming loop
        val audioThread = Thread {
            startLatch.await()
            for (i in 0 until totalChunks) {
                engine.fillNextChunk(buffer, TEST_BPM)
            }
            running.set(false)
            finishLatch.countDown()
        }

        audioThread.start()
        uiThread.start()
        startLatch.countDown()

        assertTrue(
            "Engine deadlocked under concurrent pattern swaps",
            finishLatch.await(10, TimeUnit.SECONDS)
        )

        val expectedSamples = totalChunks.toLong() * chunkSize.toLong()
        assertEquals(
            "Rendered sample count must match exact physical sample expectations (zero drift)",
            expectedSamples,
            engine.totalSamplesGenerated
        )
    }
}