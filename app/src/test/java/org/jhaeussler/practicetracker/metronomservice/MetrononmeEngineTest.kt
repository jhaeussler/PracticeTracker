/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeEngineTest {
    private lateinit var engine: MetronomeEngine

    @Before
    fun setUp() {
        engine = MetronomeEngine(sampleRate = TEST_SAMPLE_RATE)
    }

// --- Time-Sensitive Audio Generation Tests ---

    @Test
    fun `clickSamples generates correct length and non-zero sine audio`() {
        // 10ms at 44800  = 480 samples
        assertEquals(TEST_SAMPLE_RATE / 100, engine.regularClickSamples.size)
        assertEquals(TEST_SAMPLE_RATE / 100, engine.accentClickSamples.size)

        // First sample of sine(0) is 0, second sample must be positive audio signal
        assertEquals(0, engine.regularClickSamples[0].toInt())
        assertEquals(0, engine.accentClickSamples[0].toInt())

        assertTrue("Second sample should be non-zero sine amplitude",
            engine.regularClickSamples[1] > 0)
        assertTrue("Second sample should be non-zero sine amplitude",
            engine.accentClickSamples[1] > 0)
    }

    @Test
    fun `fillNextChunk produces click impulse followed by silence at 60 BPM`() {
        engine.resetPhase()

        val bpm = 60 // 1 beat per second = 44,100 samples per beat
        val buffer = ShortArray(1000)

        engine.fillNextChunk(buffer, bpm)

        // Verify click samples exist at the start of the beat
        for ((i, element) in engine.accentClickSamples.withIndex()) {
            assertEquals(element, buffer[i])
        }

        // Verify silence immediately follows the 10ms click wave
        for (i in engine.regularClickSamples.size until buffer.size) {
            assertEquals(0.toShort(), buffer[i])
        }
    }

    @Test
    fun `beat interval spacing matches expected sample distance for 120 BPM`() {
        engine.resetPhase()

        val bpm = 120 // 0.5 sec per beat = 22,050 samples between ticks
        val expectedSamplesPerBeat = TEST_SAMPLE_RATE  * 60 / bpm

        val totalBufferLength = 50000
        val pcmOutput = ShortArray(totalBufferLength)

        // Render enough chunks to span past 2 beats
        val chunkSize = 500
        var written = 0
        while (written < totalBufferLength) {
            val chunk = ShortArray(chunkSize)
            engine.fillNextChunk(chunk, bpm)
            System.arraycopy(chunk, 0, pcmOutput, written, chunkSize)
            written += chunkSize
        }

        // Find indices of first non-zero sample of beat 1 and beat 2
        var secondBeatIndex = -1

        // Look for the start of the second beat (after the first click finishes)
        for (i in engine.regularClickSamples.size until totalBufferLength) {
            if (pcmOutput[i] != 0.toShort()) {
                secondBeatIndex = i - 1 // sine wave actually starts 1 sample earlier but sin(0) = 0
                break
            }
        }

        assertEquals("Second beat tick did not start at exact sample boundary",
            expectedSamplesPerBeat, secondBeatIndex)
    }

    @Test
    fun `sub-sample fractional remainder prevents timing drift across 100 beats`() {
        engine.resetPhase()
        // 133 BPM at 44100 Hz = 19,894.73684... samples per beat
        val bpm = 133
        val exactSamplesPerBeat = TEST_SAMPLE_RATE.toDouble() * 60.0 / bpm
        val totalBeats = 100

        // Beat 0 is at sample 0, beat 1 at sample 19894, ...
        // Beat 100 should start at floor(100 * 19894.73684...) = 1,989,473
        val expected100thBeatSampleIndex = (exactSamplesPerBeat * totalBeats).roundToLong()

        val chunkSize = 512
        val tempBuffer = ShortArray(chunkSize)

        var globalSampleIndex = 0L
        var actual100thBeatSampleIndex = -1L

        var totalBeatsDetected = 0

        var zeroCount = 0

        while (totalBeatsDetected < totalBeats) {
            engine.fillNextChunk(tempBuffer, bpm)

            for (i in tempBuffer.indices) {
                val currentSample = tempBuffer[i]
                val currentGlobalSample = globalSampleIndex + i

                if (currentSample != 0.toShort())
                {
                    if (zeroCount >= 10)
                    {
                        if (currentGlobalSample > 0) {
                            totalBeatsDetected++
                        }

                        if (totalBeatsDetected == totalBeats) {
                            // Sin wave starts at 0 -> only second sine sample will be != 0
                            actual100thBeatSampleIndex = currentGlobalSample - 1L
                            break
                        }
                    }
                    zeroCount = 0
                } else {
                    zeroCount++
                }
            }

            if (actual100thBeatSampleIndex != -1L) break
            globalSampleIndex += chunkSize
        }

        assertEquals(
            "Drift detected across 100 beats!",
            expected100thBeatSampleIndex,
            actual100thBeatSampleIndex
        )
    }

    @Test
    fun `changing BPM dynamically during playback changes sample density immediately`() {
        val buffer60Bpm = ShortArray(TEST_SAMPLE_RATE)
        val buffer120Bpm = ShortArray(TEST_SAMPLE_RATE)

        // Generate 1 second at 60 BPM (1 beat)
        engine.resetPhase()
        engine.fillNextChunk(buffer60Bpm, 60)

        // Generate 1 second at 120 BPM (2 beats)
        engine.resetPhase()
        engine.fillNextChunk(buffer120Bpm, 120)

        // 120 BPM stream should contain a second tick pulse midway, while 60 BPM stream is silent
        val midpoint = TEST_SAMPLE_RATE / 2 + 1
        assertEquals(0.toShort(), buffer60Bpm[midpoint])
        assertNotEquals(0.toShort(), buffer120Bpm[midpoint])
    }

    @Test
    fun `resetPhase sets sample counters to zero and resets click sound`() {
        val buffer = ShortArray(TEST_SAMPLE_RATE / 2 + 1)
        engine.fillNextChunk(buffer, bpm = 120)

        assertTrue(engine.totalSamplesGenerated > 0)
        assertTrue(engine.sampleIndexInBeat > 0)
        assertTrue(engine.currentBeatInMeasure > 0)

        engine.resetPhase(false)

        assertEquals(buffer.size.toLong(), engine.totalSamplesGenerated)
        assertEquals(0.0, engine.sampleIndexInBeat, 0.0)

        engine.fillNextChunk(buffer, bpm = 120)
        assertEquals(buffer[1], engine.accentClickSamples[1])

        engine.resetPhase(true)

        assertEquals(0L, engine.totalSamplesGenerated)
        assertEquals(0.0, engine.sampleIndexInBeat, 0.0)
    }

    @Test
    fun `beat boundary rollover preserves sub-sample accuracy`() {
        engine.resetPhase()

        val bpm = 120
        val samplesPerBeat = TEST_SAMPLE_RATE * 60 / bpm
        val buffer = ShortArray(samplesPerBeat)

        engine.fillNextChunk(buffer, bpm = bpm)

        assertEquals(samplesPerBeat.toLong(), engine.totalSamplesGenerated)
        // Upon crossing the boundary, phase should wrap cleanly back to 0.0
        assertEquals(0.0, engine.sampleIndexInBeat, 0.0001)
    }

    @Test
    fun `fillNextChunk plays accented tone on Beat 0 and regular tone on subsequent beats`() {
        engine.resetPhase()
        engine.setSubdivision(4)

        val bpm = 60 // 44,100 samples per beat
        val beatBuffer = ShortArray(TEST_SAMPLE_RATE)

        // Beat 0: Fill buffer for first beat
        engine.fillNextChunk(beatBuffer, bpm)
        assertEquals(
            "Beat 0 must use accented click audio",
            engine.accentClickSamples[1],
            beatBuffer[1]
        )
        // Verify engine transitioned to beat 1 for the next upcoming beat
        assertEquals(1, engine.currentBeatInMeasure)

        // Beat 1: buffer for second beat
        val beat1Buffer = ShortArray(TEST_SAMPLE_RATE)

        engine.fillNextChunk(beat1Buffer, bpm)
        assertEquals(
            "Beat 1 must use regular click audio",
            engine.regularClickSamples[1],
            beat1Buffer[1]
        )
        assertEquals(2, engine.currentBeatInMeasure)
    }

    @Test
    fun `currentBeatInMeasure rolls over correctly according to set subdivision`() {
        engine.resetPhase()
        engine.setSubdivision(3) // 3/4 time signature

        val bpm = 120
        val beatSamples = TEST_SAMPLE_RATE * 60 / bpm
        val tempBuffer = ShortArray(beatSamples)

        // Initial state
        assertEquals(0, engine.currentBeatInMeasure)

        // Advance Beat 0 -> Beat 1
        engine.fillNextChunk(tempBuffer, bpm)
        assertEquals(1, engine.currentBeatInMeasure)

        // Advance Beat 1 -> Beat 2
        engine.fillNextChunk(tempBuffer, bpm)
        assertEquals(2, engine.currentBeatInMeasure)

        // Advance Beat 2 -> Rollover back to Beat 0
        engine.fillNextChunk(tempBuffer, bpm)
        assertEquals(0, engine.currentBeatInMeasure)
    }

    @Test
    fun `initial beats list should defaulted to 4 subdivisions with first beat accented`() {
        val beats = engine.beats

        assertEquals(4, beats.size)
        assertEquals(4, engine.beatsPerMeasure())
        assertTrue(beats[0].isAccent)
        assertTrue(beats[0].isEnabled)

        for (i in 1 until beats.size) {
            assertFalse(beats[i].isAccent)
            assertTrue(beats[i].isEnabled)
        }
    }

    @Test
    fun `setSubdivision updates beat count and resets beat list`() {
        val result = engine.setSubdivision(6)

        assertTrue(result)
        assertEquals(6, engine.beats.size)
        assertEquals(6, engine.beatsPerMeasure())

        assertTrue(engine.beats[0].isAccent)
        assertFalse(engine.beats[1].isAccent)
    }

    @Test
    fun `setSubdivision rejects values outside valid range`() {
        val tooLow = engine.setSubdivision(0)
        assertFalse(tooLow)
        assertEquals(4, engine.beats.size)
        assertEquals(4, engine.beatsPerMeasure())


        val tooHigh = engine.setSubdivision(9)
        assertFalse(tooHigh)
        assertEquals(4, engine.beats.size)
        assertEquals(4, engine.beatsPerMeasure())

    }

    @Test
    fun `toggleBeatEnabled toggles enabled state of correct beat index`() {
        // Mute second beat (index 1)
        engine.toggleBeatEnabled(1)
        assertFalse(engine.beats[1].isEnabled)
        assertTrue(engine.beats[0].isEnabled)
        assertTrue(engine.beats[2].isEnabled)

        // Unmute second beat
        engine.toggleBeatEnabled(1)
        assertTrue(engine.beats[1].isEnabled)
    }

    @Test
    fun `toggleBeatAccent toggles accent state of correct beat index`() {
        // Make second beat an accent
        engine.toggleBeatAccent(1)
        assertTrue(engine.beats[1].isAccent)

        // Toggle back off
        engine.toggleBeatAccent(1)
        assertFalse(engine.beats[1].isAccent)
    }

    @Test
    fun `currentBeatInMeasure stays within valid bounds across measure boundaries and subdivision changes`() {
        engine.setSubdivision(3)
        engine.resetPhase()


        val bpm = 240 // High BPM to advance beats rapidly
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toInt()
        val buffer = ShortArray(samplesPerBeat)

        assertEquals(0, engine.currentBeatInMeasure)

        engine.fillNextChunk(buffer, bpm)
        assertEquals(1, engine.currentBeatInMeasure)

        engine.fillNextChunk(buffer, bpm)
        assertEquals(2, engine.currentBeatInMeasure)

        engine.setSubdivision(2)
        assertEquals(0, engine.currentBeatInMeasure)

        engine.fillNextChunk(buffer, bpm)
        assertEquals(1, engine.currentBeatInMeasure)
    }

    @Test
    fun `currentBeatInMeasure wraps back to zero after reaching measure end`() {
        engine.setSubdivision(3)
        engine.resetPhase()

        val bpm = 240
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toInt()
        val buffer = ShortArray(samplesPerBeat)

        // Beat 0 -> Beat 1 -> Beat 2
        repeat(3) { engine.fillNextChunk(buffer, bpm) }

        // After 3 full beats in a 3-subdivision measure, it should wrap around back to 0
        assertEquals(0, engine.currentBeatInMeasure)
    }

    @Test
    fun `fillNextChunk outputs silence when current beat is disabled`() {
        engine.toggleBeatEnabled(0)
        assertFalse(engine.beats[0].isEnabled)
        engine.resetPhase()

        val buffer = ShortArray(engine.accentClickSamples.size)
        engine.fillNextChunk(buffer, bpm = 120)

        // Verify buffer is entirely silent (all zeros)
        assertTrue(buffer.all { sample -> sample == 0.toShort() })
    }

    @Test
    fun `fillNextChunk outputs accent samples on accented beats`() {
        engine.resetPhase()

        val bpm = 120
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toInt()
        val buffer = ShortArray(samplesPerBeat)

        // accent 3. beat
        engine.toggleBeatAccent(2)
        assertTrue(engine.beats[2].isAccent)
        repeat(3) { engine.fillNextChunk(buffer, bpm) }

        for (i in engine.accentClickSamples.indices) {
            assertEquals(engine.accentClickSamples[i], buffer[i])
        }
    }

    @Test
    fun `fillNextChunk outputs regular samples on unaccented enabled beats`() {
        engine.toggleBeatAccent(0)
        assertFalse(engine.beats[0].isAccent)
        engine.resetPhase()

        val buffer = ShortArray(engine.regularClickSamples.size)
        engine.fillNextChunk(buffer, bpm = 120)

        // Verify output matches regular click samples
        for (i in buffer.indices) {
            assertEquals(engine.regularClickSamples[i], buffer[i])
        }
    }

    @Test
    fun `single buffer spanning across a beat boundary contains silence then new beat click`() {
        engine.resetPhase()
        val bpm = 120 // 24 000 samples per beat

        val initialBuffer = ShortArray(22000)
        engine.fillNextChunk(initialBuffer, bpm)
        assertEquals(0, engine.currentBeatInMeasure)

        // It should contain 2,000 samples of Beat 0 silence,
        // followed by the Beat 1 regular click starting at index 2000.
        val straddlingBuffer = ShortArray(5000)
        engine.fillNextChunk(straddlingBuffer, bpm)

        // First 2000 samples should be silence (end of Beat 0)
        for (i in 0 until 2000) {
            assertEquals(0.toShort(), straddlingBuffer[i])
        }

        // Beat 1 should start at index 2050 (regular click)
        assertEquals(engine.regularClickSamples[0], straddlingBuffer[2000])
        assertEquals(engine.regularClickSamples[1], straddlingBuffer[2001])

        assertEquals(1, engine.currentBeatInMeasure)
    }

    @Test
    fun `buffer larger than a beat renders multiple beats in a single call`() {
        engine.resetPhase()
        val bpm = 240 // 11,025 samples per beat
        val samplesPerBeat = TEST_SAMPLE_RATE * 60 / bpm

        // Sized to fit exactly 2 full beats plus a 500-sample tail
        val buffer = ShortArray(samplesPerBeat * 2 + 500)
        engine.fillNextChunk(buffer, bpm)

        assertEquals(engine.accentClickSamples[1], buffer[1])

        assertEquals(engine.regularClickSamples[1], buffer[samplesPerBeat + 1])

        assertEquals(engine.regularClickSamples[1], buffer[samplesPerBeat * 2 + 1])

        assertEquals(2, engine.currentBeatInMeasure)
    }

    @Test
    fun `setBeatsList updates pattern correctly and rejects invalid lists`() {
        val customBeats = listOf(
            Beat(index = 0, isEnabled = true, isAccent = true),
            Beat(index = 1, isEnabled = false, isAccent = false),
            Beat(index = 2, isEnabled = true, isAccent = false)
        )

        engine.setBeatsList(customBeats)
        assertEquals(3, engine.beats.size)
        assertEquals(3, engine.beatsPerMeasure())
        assertFalse(engine.beats[1].isEnabled)

        // Empty list must be rejected
        engine.setBeatsList(emptyList())
        assertEquals(3, engine.beats.size)

        // Oversized list must be rejected
        val oversizedList = List(MetronomeRepository.MAX_SUBDIVISIONS + 1) { Beat(it) }
        engine.setBeatsList(oversizedList)
        assertEquals(3, engine.beats.size)
    }

    @Test
    fun `fillNextChunk handles edge case inputs gracefully without hanging`() {
        val buffer = ShortArray(512)

        // Non-positive BPM should be safely coerced without divide-by-zero or hanging
        engine.fillNextChunk(buffer, bpm = 0)
        engine.fillNextChunk(buffer, bpm = -50)

        // Empty buffer should return immediately and not alter sample counts
        val emptyBuffer = ShortArray(0)
        val samplesBefore = engine.totalSamplesGenerated
        engine.fillNextChunk(emptyBuffer, bpm = 120)
        assertEquals(samplesBefore, engine.totalSamplesGenerated)
    }

    // --- BeatEvent Queue & Hardware Sync Tests ---

    @Test
    fun `resetPhase initializes beatQueue with Beat 0 at sample position 0`() {
        engine.resetPhase()

        assertEquals(1, engine.beatQueue.size)

        val initialEvent = engine.beatQueue.peek()
        assertNotNull(initialEvent)
        assertEquals(0L, initialEvent?.samplePosition)
        assertEquals(0, initialEvent?.beatIndex)
    }

    @Test
    fun `beatQueue enqueues accurate samplePositions across multiple beats`() {
        engine.resetPhase()

        val bpm = 120 // At 48,000 Hz -> 24,000 samples per beat
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).roundToLong()
        val buffer = ShortArray(samplesPerBeat.toInt())

        engine.fillNextChunk(buffer, bpm)
        engine.fillNextChunk(buffer, bpm)
        engine.fillNextChunk(buffer, bpm)

        assertEquals(4, engine.beatQueue.size)

        val events = engine.beatQueue.toList()

        // Beat 0: Initial beat at start
        assertEquals(0L, events[0].samplePosition)
        assertEquals(0, events[0].beatIndex)

        // Beat 1: Occurred after exactly 1 beat's worth of samples
        assertEquals(samplesPerBeat, events[1].samplePosition)
        assertEquals(1, events[1].beatIndex)

        assertEquals(samplesPerBeat * 2, events[2].samplePosition)
        assertEquals(2, events[2].beatIndex)

        assertEquals(samplesPerBeat * 3, events[3].samplePosition)
        assertEquals(3, events[3].beatIndex)
    }

    @Test
    fun `beatQueue handles beat rollover indices correctly across measure boundaries`() {
        engine.setSubdivision(3) // 3/4 time signature (indices: 0, 1, 2 -> 0, 1...)
        engine.resetPhase()

        val bpm = 240
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toInt()
        val buffer = ShortArray(samplesPerBeat)

        // Render 4 beats (indices: 0 -> 1 -> 2 -> 0)
        repeat(4) {
            engine.fillNextChunk(buffer, bpm)
        }

        val events = engine.beatQueue.toList()
        assertEquals(5, events.size)

        val beatIndices = events.map { it.beatIndex }
        assertEquals(listOf(0, 1, 2, 0, 1), beatIndices)
    }

    @Test
    fun `buffer spanning across a beat boundary enqueues event at exact split position`() {
        engine.resetPhase()
        val bpm = 120 // 24,000 samples per beat

        // 1. Fill 20,000 samples (4,000 samples remaining before Beat 1)
        val firstChunk = ShortArray(20000)
        engine.fillNextChunk(firstChunk, bpm)

        assertEquals(1, engine.beatQueue.size) // Only initial Beat 0

        // 2. Fill 10,000 samples (straddles the 24,000 boundary at local index 4,000)
        val straddlingChunk = ShortArray(10000)
        engine.fillNextChunk(straddlingChunk, bpm)

        assertEquals(2, engine.beatQueue.size)

        val events = engine.beatQueue.toList()
        assertEquals(0L, events[0].samplePosition)
        assertEquals(0, events[0].beatIndex)

        // Beat 1 should be stamped at global sample 24,000
        assertEquals((TEST_SAMPLE_RATE * 60.0 / bpm).toLong(), events[1].samplePosition)
        assertEquals(1, events[1].beatIndex)
    }

    @Test
    fun `single large buffer enqueues all spanned beat events`() {
        engine.resetPhase()
        val bpm = 240
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toInt()

        // Sized to fit exactly 3 full beats
        val largeBuffer = ShortArray(samplesPerBeat * 3)
        engine.fillNextChunk(largeBuffer, bpm)

        // Expecting 4 events: Initial Beat 0, plus Beat 1, Beat 2, Beat 3
        assertEquals(4, engine.beatQueue.size)

        val events = engine.beatQueue.toList()
        for (i in events.indices) {
            assertEquals((i * samplesPerBeat).toLong(), events[i].samplePosition)
            assertEquals(i % 4, events[i].beatIndex)
        }
    }

    @Test
    fun `setting subdivisions or custom beats list clears and resets beatQueue`() {
        val bpm = 120
        val buffer = ShortArray(TEST_SAMPLE_RATE)

        // Accumulate some beats in the queue
        engine.fillNextChunk(buffer, bpm)
        assertTrue(engine.beatQueue.size > 1)

        // Changing subdivision internally invokes resetPhase()
        engine.setSubdivision(5)

        assertEquals(1, engine.beatQueue.size)
        val event = engine.beatQueue.peek()
        assertEquals(buffer.size.toLong(), event?.samplePosition)
        assertEquals(0, event?.beatIndex)

        // Setting beat list also resets queue
        engine.fillNextChunk(buffer, bpm)
        assertTrue(engine.beatQueue.size > 1)

        engine.setBeatsList(listOf(Beat(0), Beat(1)))
        assertEquals(1, engine.beatQueue.size)
        assertEquals(buffer.size.toLong() * 2, engine.beatQueue.peek()?.samplePosition)
    }

    @Test
    fun `simulating AudioTrack playbackHeadPosition consumer drains queue accurately`() {
        engine.resetPhase()
        val bpm = 120 // 24,000 samples per beat
        val samplesPerBeat = (TEST_SAMPLE_RATE * 60.0 / bpm).toLong()

        // Pre-fill audio buffer (generating 3 beats ahead)
        val buffer = ShortArray((samplesPerBeat * 3).toInt())
        engine.fillNextChunk(buffer, bpm)

        assertEquals(4, engine.beatQueue.size)

        // Simulate AudioTrack draining frames:

        // 1. Playback starts at sample 0 -> Beat 0 should be consumed
        var currentPlaybackHead = 0L
        var activeBeat = -1
        while (engine.beatQueue.peek()?.let { it.samplePosition <= currentPlaybackHead } == true) {
            activeBeat = engine.beatQueue.poll()!!.beatIndex
        }
        assertEquals(0, activeBeat)
        assertEquals(3, engine.beatQueue.size)

        // 2. Playback advances midway through Beat 0 (sample 12,000) -> Still Beat 0
        currentPlaybackHead = 12000L
        while (engine.beatQueue.peek()?.let { it.samplePosition <= currentPlaybackHead } == true) {
            activeBeat = engine.beatQueue.poll()!!.beatIndex
        }
        assertEquals(0, activeBeat) // No new beat polled
        assertEquals(3, engine.beatQueue.size)

        // 3. Playback reaches sample 24,000 -> Beat 1 consumed
        currentPlaybackHead = samplesPerBeat
        while (engine.beatQueue.peek()?.let { it.samplePosition <= currentPlaybackHead } == true) {
            activeBeat = engine.beatQueue.poll()!!.beatIndex
        }
        assertEquals(1, activeBeat)
        assertEquals(2, engine.beatQueue.size)

        // 4. Playback jumps past Beat 2 and Beat 3 (e.g. sample 80,000) -> Drains both, ends on Beat 3
        currentPlaybackHead = samplesPerBeat * 3 + 1000
        while (engine.beatQueue.peek()?.let { it.samplePosition <= currentPlaybackHead } == true) {
            activeBeat = engine.beatQueue.poll()!!.beatIndex
        }
        assertEquals(3, activeBeat)
        assertTrue(engine.beatQueue.isEmpty())
    }
}