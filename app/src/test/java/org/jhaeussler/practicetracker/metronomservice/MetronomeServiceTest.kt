package org.jhaeussler.practicetracker.metronomservice

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeServiceTest {

    private lateinit var engine: MetronomeEngine

    @Before
    fun setUp() {
        engine = MetronomeEngine(sampleRate = 44100)
    }

    // --- StateFlow & BPM Validation Tests ---

    @Test
    fun `setBpm should coerce values within range 30 to 300`() = runTest {
        MetronomeService.setBpm(20)
        assertEquals(30, MetronomeService.bpm.value)

        MetronomeService.setBpm(350)
        assertEquals(300, MetronomeService.bpm.value)

        MetronomeService.setBpm(120)
        assertEquals(120, MetronomeService.bpm.value)
    }

    @Test
    fun `bpm flow should emit new values when setBpm is called`() = runTest {
        // Reset to a known value first
        MetronomeService.setBpm(120)
        
        MetronomeService.bpm.test {
            assertEquals(120, awaitItem())
            
            MetronomeService.setBpm(140)
            assertEquals(140, awaitItem())

            MetronomeService.setBpm(60)
            assertEquals(60, awaitItem())
        }
    }

    @Test
    fun `isRunning has correct initial value`() = runTest {
        assertFalse(MetronomeService.isRunning.value)
    }

    // --- Time-Sensitive Audio Generation Tests ---

    @Test
    fun `clickSamples generates correct length and non-zero sine audio`() {
        // 10ms at 44100Hz = 441 samples
        assertEquals(441, engine.clickSamples.size)

        // First sample of sine(0) is 0, second sample must be positive audio signal
        assertEquals(0, engine.clickSamples[0].toInt())
        assertTrue("Second sample should be non-zero sine amplitude",
            engine.clickSamples[1] > 0)
    }

    @Test
    fun `fillNextChunk produces click impulse followed by silence at 60 BPM`() {
        val bpm = 60 // 1 beat per second = 44,100 samples per beat
        val buffer = ShortArray(1000)

        engine.fillNextChunk(buffer, bpm)

        // Verify click samples exist at the start of the beat
        for ((i, element) in engine.clickSamples.withIndex()) {
            assertEquals(element, buffer[i])
        }

        // Verify silence immediately follows the 10ms click wave
        for (i in engine.clickSamples.size until buffer.size) {
            assertEquals(0.toShort(), buffer[i])
        }
    }

    @Test
    fun `beat interval spacing matches expected sample distance for 120 BPM`() {
        val bpm = 120 // 0.5 sec per beat = 22,050 samples between ticks
        val expectedSamplesPerBeat = 22050

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
        val firstBeatIndex = 0 // Started at phase 0
        var secondBeatIndex = -1

        // Look for the start of the second beat (after the first click finishes)
        for (i in engine.clickSamples.size until totalBufferLength) {
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
        // 133 BPM at 44100 Hz = 19,894.73684... samples per beat
        val bpm = 133
        val exactSamplesPerBeat = 44100.0 * 60.0 / bpm
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

        engine.resetPhase()

        // Run until we have recorded 100 beat intervals (101 total beat events)
        while (actualBeatIndices.size <= totalBeats) {
            // Compute where phase wraps during the next chunk calculation
            engine.fillNextChunk(tempBuffer, bpm)

            for (i in tempBuffer.indices) {
                val currentGlobalIndex = globalSampleIndex + i

                // Detect exact tick start: non-zero PCM immediately after a silence sample (0)
                // outside the 10ms click duration window from the previous beat
                if (tempBuffer[i] == engine.clickSamples[1]) {
                    val nextPotentialSineStartIndex = currentGlobalIndex - 1
                    val lastBeatIndex = actualBeatIndices.last()
                    val samplesSinceLastBeat = nextPotentialSineStartIndex - lastBeatIndex

                    // Ensure we aren't inside the 10ms click wave (441 samples) of the previous beat
                    // Note that this will miss the first click, but we added it manually above
                    if (samplesSinceLastBeat >= engine.clickSamples.size) {
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
        val buffer60Bpm = ShortArray(44100)
        val buffer120Bpm = ShortArray(44100)

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
}
