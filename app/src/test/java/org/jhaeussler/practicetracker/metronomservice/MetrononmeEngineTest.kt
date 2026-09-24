package org.jhaeussler.practicetracker.metronomservice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeEngineTest {
    private lateinit var engine: MetronomeEngine

    @Before
    fun setUp() {
        engine = MetronomeEngine(sampleRate = TEST_SAMPLE_RATE)
        engine.enableWallClockSync = false
    }

// --- Time-Sensitive Audio Generation Tests ---

    @Test
    fun `clickSamples generates correct length and non-zero sine audio`() {
        // 10ms at 44100  = 441 samples
        assertEquals(441, engine.regularClickSamples.size)
        assertEquals(441, engine.accentClickSamples.size)

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
        val exactSamplesPerBeat = TEST_SAMPLE_RATE.toFloat() * 60.0 / bpm
        val totalBeats = 100

        // Beat 0 is at sample 0.
        // Beat 100 should start at floor(100 * 19894.73684...) = 1,989,473
        val expected100thBeatSampleIndex = (exactSamplesPerBeat * totalBeats).roundToInt()

        val chunkSize = 512
        val tempBuffer = ShortArray(chunkSize)

        var globalSampleIndex = 0
        val actualBeatIndices = mutableListOf<Int>()

        // Record the global sample index of every beat trigger
        // Beat 0 triggers at globalSampleIndex = 0
        // first beat is skipped by condition below so add it manually
        actualBeatIndices.add(0)

        // Run until we have recorded 100 beat intervals (101 total beat events)
        while (actualBeatIndices.size <= totalBeats) {
            // Compute where phase wraps during the next chunk calculation
            engine.fillNextChunk(tempBuffer, bpm)

            for (i in tempBuffer.indices) {
                val currentGlobalIndex = globalSampleIndex + i

                // Detect exact tick start: non-zero PCM immediately after a silence sample (0)
                // outside the 10ms click duration window from the previous beat
                val accentedBeat = engine.currentBeatInMeasure == 0

                if (accentedBeat && tempBuffer[i] == engine.accentClickSamples[1] ||
                    tempBuffer[i] == engine.regularClickSamples[1]
                ) {
                    val nextPotentialSineStartIndex = currentGlobalIndex - 1
                    val lastBeatIndex = actualBeatIndices.last()
                    val samplesSinceLastBeat = nextPotentialSineStartIndex - lastBeatIndex

                    // Ensure we aren't inside the 10ms click wave (441 samples) of the previous beat
                    // Note that this will miss the first click, but we added it manually above
                    if (samplesSinceLastBeat >= engine.regularClickSamples.size) {
                        actualBeatIndices.add(nextPotentialSineStartIndex)
                    }
                }
            }
            globalSampleIndex += chunkSize
        }

        val actual100thBeatSampleIndex = actualBeatIndices[totalBeats]

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
        val midpoint = 22051
        assertEquals(0.toShort(), buffer60Bpm[midpoint])
        assertNotEquals(0.toShort(), buffer120Bpm[midpoint])
    }

    @Test
    fun `resetPhase sets sample counters to zero`() {
        val buffer = ShortArray(1024)
        engine.fillNextChunk(buffer, bpm = 120)

        assertTrue(engine.totalSamplesGenerated > 0)

        engine.resetPhase()

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
    fun `wall clock sync does not alter phase when disabled`() {
        engine.resetPhase()

        val bpm = 120
        val samplesPerBeat = TEST_SAMPLE_RATE * 60 / bpm
        val buffer = ShortArray(samplesPerBeat)

        engine.fillNextChunk(buffer, bpm = bpm)

        assertEquals(0.0, engine.sampleIndexInBeat, 0.0001)
    }

    @Test
    fun `sync shifts phase forward when wall clock runs faster than audio engine`() {
        engine.enableWallClockSync = true
        engine.resetPhase()

        val bpm = 120
        val samplesPerBeat = TEST_SAMPLE_RATE * 60 / bpm
        val buffer = ShortArray(samplesPerBeat)

        // Simulate real-time delay (e.g. CPU or thread lag) by letting system time advance
        Thread.sleep(520) // Sleep slightly longer than 500ms beat duration

        engine.fillNextChunk(buffer, bpm = bpm)

        // Because wall clock elapsed time > expected 500ms, driftInBeats < 0.
        // A negative phaseCorrection reduces sampleIndexInBeat, shortening the upcoming silence.
        assertTrue(
            "Phase should be nudged backwards (less than 0.0) to trigger next beat sooner",
            engine.sampleIndexInBeat < 0.0
        )
    }

    @Test
    fun `sync delays phase when wall clock runs slower than audio engine`() {
        engine.enableWallClockSync = true
        engine.resetPhase()

        val bpm = 120
        val samplesPerBeat = TEST_SAMPLE_RATE  * 60 / bpm
        val buffer = ShortArray(samplesPerBeat)

        // Process a beat almost instantaneously without sleeping (~0ms elapsed)
        engine.fillNextChunk(buffer, bpm = 120)

        // Because wall clock elapsed time < expected 500ms, targetBeats < 1.0.
        // driftInBeats > 0, so phaseCorrection > 0, pushing sampleIndexInBeat higher.
        assertTrue(
            "Phase should be nudged forward (greater than 0.0) to delay next beat",
            engine.sampleIndexInBeat > 0.0
        )
    }

    @Test
    fun `setSubdivision updates beatsPerMeasure within valid range`() {
        assertTrue(engine.setSubdivision(3))
        assertEquals(3, engine.beatsPerMeasure)

        assertTrue(engine.setSubdivision(8))
        assertEquals(8, engine.beatsPerMeasure)

        // Invalid values should be rejected and retain previous state
        assertFalse(engine.setSubdivision(0))
        assertFalse(engine.setSubdivision(9))
        assertEquals(8, engine.beatsPerMeasure)
    }

    @Test
    fun `fillNextChunk plays accented tone on Beat 0 and regular tone on subsequent beats`() {
        engine.resetPhase()
        engine.setSubdivision(4)

        val bpm = 60 // 44,100 samples per beat
        val beatBuffer = ShortArray(44100)

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
        val beat1Buffer = ShortArray(44100)

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
}