package org.jhaeussler.practicetracker.drummachine.sound

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Acoustic configuration parameters for synthesized clicks.
 */
data class ClickConfig(
    val frequencyHz: Double,
    val durationMs: Double,
    val peakGain: Float = 1.0f,
    val decayFactor: Double = 3.5
)

/**
 * Generates sample-rate-accurate PCM buffers for metronome clicks.
 * Guarantees zero DC-offset and smooth boundary taper (no pops/clicks).
 */
object ClickSynthesizer {

    val DEFAULT_ACCENT_CONFIG = ClickConfig(
        frequencyHz = 1500.0, durationMs = 12.0, peakGain = 1.0f
    )
    val DEFAULT_BEAT_CONFIG = ClickConfig(
        frequencyHz = 1000.0, durationMs = 10.0, peakGain = 0.85f
    )
    val DEFAULT_SUBDIVISION_CONFIG = ClickConfig(
        frequencyHz = 750.0, durationMs = 8.0, peakGain = 0.6f
    )

    /**
     * Synthesizes a mono 16-bit PCM ShortArray for a given click configuration.
     */
    fun generateClick(sampleRate: Int, config: ClickConfig): ShortArray
    {
        val numSamples = ((sampleRate / 1000.0) * config.durationMs).toInt()

        if (numSamples <= 0) return ShortArray(0)
        if (numSamples == 1) return ShortArray(1)

        val samples = ShortArray(numSamples)
        val decay = config.decayFactor
        val minExp = exp(-decay)
        val scale = 1.0 / (1.0 - minExp)
        val maxAmp = Short.MAX_VALUE * config.peakGain.coerceIn(0.0f, 1.0f)

        for (i in 0 until numSamples)
        {
            val angle = 2.0 * Math.PI * i * config.frequencyHz / sampleRate

            // Normalize progress to (numSamples - 1) so the final sample evaluates to 0.0
            val progress = i.toDouble() / (numSamples - 1)
            val envelope = (exp(-decay * progress) - minExp) * scale

            val sampleVal = (sin(angle) * maxAmp * envelope).roundToInt()

            samples[i] = sampleVal.coerceIn(
                Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()
            ).toShort()
        }

        return samples
    }

    /**
     * Synthesizes standard click variants based on [SoundId].
     * Throws [IllegalArgumentException] if [soundId] is not a click type.
     */
    fun generate(soundId: SoundId, sampleRate: Int): ShortArray
    {
        val config = when (soundId) {
            SoundId.CLICK_ACCENT -> DEFAULT_ACCENT_CONFIG
            SoundId.CLICK_BEAT -> DEFAULT_BEAT_CONFIG
            SoundId.CLICK_SUBDIVISION -> DEFAULT_SUBDIVISION_CONFIG
            else -> throw IllegalArgumentException(
                "SoundId $soundId is not a synthesized click sound")
        }

        return generateClick(sampleRate, config)
    }
}

/**
 * Extended SoundBank contract with Builder and Factory.
 */
class SoundBank(
    private val sampleMap: Map<SoundId, ShortArray> = emptyMap()
) {
    private val soundTable = Array(SoundId.entries.size) { index ->
        sampleMap[SoundId.entries[index]]
    }

    fun get(soundId: SoundId): ShortArray? = soundTable[soundId.ordinal]

    fun copyWith(soundId: SoundId, samples: ShortArray): SoundBank
    {
        val updated = sampleMap.toMutableMap()
        updated[soundId] = samples

        return SoundBank(updated)
    }

    class Builder {
        private val samples = mutableMapOf<SoundId, ShortArray>()

        fun put(soundId: SoundId, pcmData: ShortArray): Builder
        {
            samples[soundId] = pcmData
            return this
        }

        /**
         * Decodes a 16-bit PCM WAV stream and registers the resulting mono samples for [soundId].
         */
        fun putWav(
            soundId: SoundId,
            inputStream: java.io.InputStream,
            expectedSampleRate: Int? = null
        ): Builder {
            val monoPcm = inputStream.use {
                WavAudioDecoder.decodeToMonoPcm(it, expectedSampleRate)
            }
            return put(soundId, monoPcm)
        }

        fun build(): SoundBank = SoundBank(samples.toMap())
    }

    companion object {
        /**
         * Convenience factory that pre-populates ACCENT, BEAT, and SUBDIVISION clicks.
         */
        fun createMetronomeBank(sampleRate: Int): SoundBank
        {
            return Builder()
                .put(SoundId.CLICK_ACCENT, ClickSynthesizer.generate(SoundId.CLICK_ACCENT, sampleRate))
                .put(SoundId.CLICK_BEAT, ClickSynthesizer.generate(SoundId.CLICK_BEAT, sampleRate))
                .put(SoundId.CLICK_SUBDIVISION, ClickSynthesizer.generate(SoundId.CLICK_SUBDIVISION, sampleRate))
                .build()
        }

        /**
         * Assembles a complete, ready-to-use SoundBank populating all 8 [SoundId] entries
         * using [ClickSynthesizer] for clicks and [SyntheticDrumSynthesizer] for drum voices.
         */
        fun createDefault(sampleRate: Int): SoundBank
        {
            return Builder()
                // Metronome Clicks
                .put(SoundId.CLICK_ACCENT, ClickSynthesizer.generate(SoundId.CLICK_ACCENT, sampleRate))
                .put(SoundId.CLICK_BEAT, ClickSynthesizer.generate(SoundId.CLICK_BEAT, sampleRate))
                .put(SoundId.CLICK_SUBDIVISION, ClickSynthesizer.generate(SoundId.CLICK_SUBDIVISION, sampleRate))
                // Synthetic Drum Voices
                .put(SoundId.DRUM_KICK, SyntheticDrumSynthesizer.generate(SoundId.DRUM_KICK, sampleRate))
                .put(SoundId.DRUM_SNARE, SyntheticDrumSynthesizer.generate(SoundId.DRUM_SNARE, sampleRate))
                .put(SoundId.DRUM_HIHAT_CLOSED, SyntheticDrumSynthesizer.generate(SoundId.DRUM_HIHAT_CLOSED, sampleRate))
                .put(SoundId.DRUM_HIHAT_OPEN, SyntheticDrumSynthesizer.generate(SoundId.DRUM_HIHAT_OPEN, sampleRate))
                .put(SoundId.DRUM_CLAP, SyntheticDrumSynthesizer.generate(SoundId.DRUM_CLAP, sampleRate))
                .build()
        }
    }
}