package org.jhaeussler.practicetracker.drummachine.sound

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import org.junit.Assert.assertArrayEquals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoundBankTest {

    private val sampleRate = 48000

    // --- ClickSynthesizer Tests ---

    @Test
    fun generateClick_producesCorrectSampleCount() {
        val durationMs = 10.0
        val config = ClickConfig(frequencyHz = 1000.0, durationMs = durationMs)
        val samples = ClickSynthesizer.generateClick(sampleRate, config)

        val expectedSamples = ((sampleRate / 1000.0) * durationMs).toInt()
        assertEquals(expectedSamples, samples.size)
    }

    @Test
    fun generateClick_startsAndEndsAtNearZero() {
        val config = ClickSynthesizer.DEFAULT_BEAT_CONFIG
        val samples = ClickSynthesizer.generateClick(sampleRate, config)

        assertTrue("Buffer must not be empty", samples.isNotEmpty())

        // Start must begin at 0 (sin(0) = 0)
        assertEquals("Initial sample must be 0 to prevent start click", 0, samples.first().toInt())

        // End must decay to virtually 0 to prevent truncation thump
        val lastSample = abs(samples.last().toInt())
        assertTrue("Final sample must taper to 0 (was $lastSample)", lastSample <= 10)
    }

    @Test
    fun generateClick_respectsPeakGainHierarchy() {
        val accent = ClickSynthesizer.generate(SoundId.CLICK_ACCENT, sampleRate)
        val beat = ClickSynthesizer.generate(SoundId.CLICK_BEAT, sampleRate)
        val sub = ClickSynthesizer.generate(SoundId.CLICK_SUBDIVISION, sampleRate)

        val maxAccent = accent.maxOf { abs(it.toInt()) }
        val maxBeat = beat.maxOf { abs(it.toInt()) }
        val maxSub = sub.maxOf { abs(it.toInt()) }

        assertTrue("Accent peak ($maxAccent) must exceed Beat peak ($maxBeat)", maxAccent > maxBeat)
        assertTrue("Beat peak ($maxBeat) must exceed Subdivision peak ($maxSub)", maxBeat > maxSub)
    }

    @Test
    fun generate_throwsOnNonClickSoundId() {
        assertThrows(IllegalArgumentException::class.java) {
            ClickSynthesizer.generate(SoundId.DRUM_KICK, sampleRate)
        }
    }

    // --- SoundBank & Builder Tests ---

    @Test
    fun builder_constructsValidSoundBank() {
        val dummyData = shortArrayOf(10, 20, 30)
        val bank = SoundBank.Builder()
            .put(SoundId.CLICK_ACCENT, dummyData)
            .build()

        assertEquals(dummyData, bank.get(SoundId.CLICK_ACCENT))
        assertNull(bank.get(SoundId.CLICK_BEAT))
    }

    @Test
    fun createMetronomeBank_populatesAllClicksAndLeavesDrumsNull() {
        val bank = SoundBank.createMetronomeBank(sampleRate)

        // All 3 click types must be pre-rendered
        assertNotNull(bank.get(SoundId.CLICK_ACCENT))
        assertNotNull(bank.get(SoundId.CLICK_BEAT))
        assertNotNull(bank.get(SoundId.CLICK_SUBDIVISION))

        // Drum slots must remain unpopulated
        assertNull(bank.get(SoundId.DRUM_KICK))
        assertNull(bank.get(SoundId.DRUM_SNARE))
        assertNull(bank.get(SoundId.DRUM_HIHAT_CLOSED))
        assertNull(bank.get(SoundId.DRUM_HIHAT_OPEN))
        assertNull(bank.get(SoundId.DRUM_CLAP))
    }

    // Wav decoder

    @Test
    fun decode_mono16Bit_decodesExactSamples() {
        val originalSamples = shortArrayOf(0, 1000, -2000, 32767, -32768)
        val wavBytes = createMockWav(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            pcmData = originalSamples
        )

        val decoded = WavAudioDecoder.decodeToMonoPcm(
            ByteArrayInputStream(wavBytes),
            expectedSampleRate = sampleRate
        )

        assertArrayEquals(originalSamples, decoded)
    }

    @Test
    fun decode_stereo16Bit_downmixesToMonoCorrectly() {
        // Interleaved L, R, L, R
        // Frame 0: L=1000, R=3000 -> Expected mono: 2000
        // Frame 1: L=-2000, R=4000 -> Expected mono: 1000
        val stereoInterleaved = shortArrayOf(1000, 3000, -2000, 4000)
        val expectedMono = shortArrayOf(2000, 1000)

        val wavBytes = createMockWav(
            sampleRate = sampleRate,
            channels = 2,
            bitsPerSample = 16,
            pcmData = stereoInterleaved
        )

        val decoded = WavAudioDecoder.decodeToMonoPcm(
            ByteArrayInputStream(wavBytes),
            expectedSampleRate = sampleRate
        )

        assertArrayEquals(expectedMono, decoded)
    }

    @Test
    fun decode_skipsMetadataChunksBeforeData() {
        val originalSamples = shortArrayOf(500, -500, 1500)
        val wavBytes = createMockWav(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            pcmData = originalSamples,
            extraChunkBeforeData = "JUNK" to byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )

        val decoded = WavAudioDecoder.decodeToMonoPcm(
            ByteArrayInputStream(wavBytes),
            expectedSampleRate = sampleRate
        )

        assertArrayEquals(originalSamples, decoded)
    }

    @Test
    fun decode_throwsOnSampleRateMismatch() {
        val wavBytes = createMockWav(
            sampleRate = 44100, // file rate
            channels = 1,
            bitsPerSample = 16,
            pcmData = shortArrayOf(10, 20)
        )

        assertThrows(IllegalArgumentException::class.java) {
            WavAudioDecoder.decodeToMonoPcm(
                ByteArrayInputStream(wavBytes),
                expectedSampleRate = 48000 // expected hardware rate
            )
        }
    }

    @Test
    fun decode_throwsOnUnsupportedBitDepth() {
        val wavBytes = createMockWav(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 24, // 24-bit unsupported
            pcmData = shortArrayOf()
        )

        assertThrows(IllegalArgumentException::class.java) {
            WavAudioDecoder.decodeToMonoPcm(ByteArrayInputStream(wavBytes))
        }
    }

    @Test
    fun decode_throwsOnInvalidHeader() {
        val corruptBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        assertThrows(IllegalArgumentException::class.java) {
            WavAudioDecoder.decodeToMonoPcm(ByteArrayInputStream(corruptBytes))
        }
    }

    @Test
    fun soundBankBuilder_putWav_loadsAudioSuccessfully() {
        val samples = shortArrayOf(100, -200, 300)
        val wavBytes = createMockWav(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            pcmData = samples
        )

        val bank = SoundBank.Builder()
            .putWav(SoundId.DRUM_KICK, ByteArrayInputStream(wavBytes), sampleRate)
            .build()

        assertNotNull(bank.get(SoundId.DRUM_KICK))
        assertEquals(3, bank.get(SoundId.DRUM_KICK)?.size)
        assertArrayEquals(samples, bank.get(SoundId.DRUM_KICK))
    }

    // --- In-memory RIFF/WAV binary generator helper ---

    private fun createMockWav(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmData: ShortArray,
        extraChunkBeforeData: Pair<String, ByteArray>? = null
    ): ByteArray {
        val byteBuffer = ByteArrayOutputStream()
        val pcmByteSize = pcmData.size * 2

        // fmt chunk size is 16 for PCM
        val fmtChunkSize = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val extraSize = extraChunkBeforeData?.let { 8 + it.second.size } ?: 0
        val riffPayloadSize = 4 + (8 + fmtChunkSize) + extraSize + (8 + pcmByteSize)

        // 1. RIFF Header
        byteBuffer.write("RIFF".toByteArray())
        byteBuffer.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(riffPayloadSize).array())
        byteBuffer.write("WAVE".toByteArray())

        // 2. fmt chunk
        byteBuffer.write("fmt ".toByteArray())
        byteBuffer.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fmtChunkSize).array())
        val fmtData = ByteBuffer.allocate(fmtChunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1.toShort()) // AudioFormat: 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
        }.array()
        byteBuffer.write(fmtData)

        // 3. Optional Extra Chunk (e.g. JUNK)
        extraChunkBeforeData?.let { (tag, payload) ->
            byteBuffer.write(tag.toByteArray())
            byteBuffer.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array())
            byteBuffer.write(payload)
        }

        // 4. data chunk
        byteBuffer.write("data".toByteArray())
        byteBuffer.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcmByteSize).array())

        val pcmByteBuffer = ByteBuffer.allocate(pcmByteSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcmData) {
            pcmByteBuffer.putShort(sample)
        }
        byteBuffer.write(pcmByteBuffer.array())

        return byteBuffer.toByteArray()
    }

    // Drum Synth

    @Test
    fun generate_producesValidBuffersForEveryDrumSoundId() {
        val drumSounds = listOf(
            SoundId.DRUM_KICK,
            SoundId.DRUM_SNARE,
            SoundId.DRUM_HIHAT_CLOSED,
            SoundId.DRUM_HIHAT_OPEN,
            SoundId.DRUM_CLAP
        )

        for (soundId in drumSounds) {
            val samples = SyntheticDrumSynthesizer.generate(soundId, sampleRate)
            assertTrue("$soundId buffer must not be empty", samples.isNotEmpty())

            // Tail must cleanly taper to near-zero (prevent DC thumps)
            val tailAmp = abs(samples.last().toInt())
            assertTrue("$soundId tail must end near zero (was $tailAmp)", tailAmp <= 10)
        }
    }

    @Test
    fun generateKick_hasSubBassDurationAndPeakAmplitude() {
        val kick = SyntheticDrumSynthesizer.generateKick(sampleRate)
        val durationMs = (kick.size.toDouble() / sampleRate) * 1000.0

        // Kick should be punchy and deep (between 100ms and 250ms)
        assertTrue("Kick duration ($durationMs ms) should be between 100ms and 250ms", durationMs in 100.0..250.0)

        val peak = kick.maxOf { abs(it.toInt()) }
        assertTrue("Kick should have high peak amplitude ($peak)", peak > 20000)
    }

    @Test
    fun generateHiHats_closedIsShorterThanOpen() {
        val closedHat = SyntheticDrumSynthesizer.generateHiHatClosed(sampleRate)
        val openHat = SyntheticDrumSynthesizer.generateHiHatOpen(sampleRate)

        assertTrue(
            "Closed hi-hat (${closedHat.size}) must be shorter than Open hi-hat (${openHat.size})",
            closedHat.size < openHat.size
        )
    }

    @Test
    fun generate_throwsOnClickSoundIds() {
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticDrumSynthesizer.generate(SoundId.CLICK_ACCENT, sampleRate)
        }
    }

    @Test
    fun createDefault_populatesAllEightSoundIds() {
        val bank = SoundBank.createDefault(sampleRate)

        // Every enum entry in SoundId must now be present in the default soundbank
        for (soundId in SoundId.entries) {
            val samples = bank.get(soundId)
            assertNotNull("SoundId $soundId should be non-null in default bank", samples)
            assertTrue("SoundId $soundId should have samples", samples!!.isNotEmpty())
        }
    }
}
