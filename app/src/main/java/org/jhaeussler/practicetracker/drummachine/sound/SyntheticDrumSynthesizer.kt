package org.jhaeussler.practicetracker.drummachine.sound

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

object SyntheticDrumSynthesizer {

    fun generateKick(sampleRate: Int): ShortArray
    {
        val durationMs = 160.0
        val numSamples = ((sampleRate / 1000.0) * durationMs).toInt()
        val samples = ShortArray(numSamples)

        val startFreq = 160.0
        val endFreq = 45.0
        val pitchDropRate = 22.0
        val decay = 4.0
        val minExp = exp(-decay)
        val scale = 1.0 / (1.0 - minExp)
        val peakAmp = Short.MAX_VALUE * 0.95

        var phase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / (numSamples - 1)
            val tSec = (i.toDouble() / sampleRate)

            // Dynamic exponential pitch drop: 160Hz -> 45Hz
            val currentFreq = endFreq + (startFreq - endFreq) * exp(-pitchDropRate * tSec)
            phase += 2.0 * Math.PI * currentFreq / sampleRate

            val envelope = (exp(-decay * progress) - minExp) * scale
            val value = (sin(phase) * peakAmp * envelope).roundToInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }

    fun generateSnare(sampleRate: Int): ShortArray
    {
        val durationMs = 140.0
        val numSamples = ((sampleRate / 1000.0) * durationMs).toInt()
        val samples = ShortArray(numSamples)

        val bodyFreq = 185.0
        val bodyDecay = 12.0
        val noiseDecay = 5.0
        val minExp = exp(-noiseDecay)
        val scale = 1.0 / (1.0 - minExp)

        val random = Random(42) // Fixed seed for reproducible rendering
        var prevNoise = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / (numSamples - 1)

            // Body tone: fast-decaying sine
            val toneEnv = exp(-bodyDecay * progress)
            val tone = sin(2.0 * Math.PI * i * bodyFreq / sampleRate) * toneEnv

            // Snare rattle: high-pass filtered white noise
            val rawNoise = random.nextDouble(-1.0, 1.0)
            val hpNoise = rawNoise - 0.6 * prevNoise
            prevNoise = rawNoise

            // Combined envelope tapering to exact zero at the end
            val overallEnvelope = (exp(-noiseDecay * progress) - minExp) * scale
            val mixed = (0.4 * tone + 0.6 * hpNoise) * overallEnvelope

            val value = (mixed * Short.MAX_VALUE * 0.9).roundToInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }

    fun generateHiHatClosed(sampleRate: Int): ShortArray {
        return generateHiHat(sampleRate, durationMs = 35.0, decayFactor = 8.0, seed = 101)
    }

    fun generateHiHatOpen(sampleRate: Int): ShortArray {
        return generateHiHat(sampleRate, durationMs = 180.0, decayFactor = 3.2, seed = 202)
    }

    fun generateClap(sampleRate: Int): ShortArray
    {
        val durationMs = 150.0
        val numSamples = ((sampleRate / 1000.0) * durationMs).toInt()
        val samples = ShortArray(numSamples)

        val random = Random(303)
        var prevNoise = 0.0
        val tailDecay = 4.5
        val minExp = exp(-tailDecay)
        val scale = 1.0 / (1.0 - minExp)

        // Pre-compute 3 staggered hand-clap burst time points (in samples)
        val burst1 = 0
        val burst2 = ((sampleRate / 1000.0) * 11.0).toInt()
        val burst3 = ((sampleRate / 1000.0) * 23.0).toInt()
        val burstDuration = ((sampleRate / 1000.0) * 8.0).toInt()

        for (i in 0 until numSamples) {
            val rawNoise = random.nextDouble(-1.0, 1.0)
            val bandNoise = rawNoise - 0.7 * prevNoise
            prevNoise = rawNoise

            // Amplitude envelope combining initial staggered taps + decaying diffuse tail
            var burstGain = 0.0
            if (i in burst1 until burst1 + burstDuration) burstGain += 0.8
            if (i in burst2 until burst2 + burstDuration) burstGain += 0.9
            if (i in burst3 until burst3 + burstDuration) burstGain += 1.0

            val progress = i.toDouble() / (numSamples - 1)
            val tailEnvelope = (exp(-tailDecay * progress) - minExp) * scale

            val amp = maxOf(burstGain, tailEnvelope * 0.7)
            val value = (bandNoise * amp * Short.MAX_VALUE * 0.85).roundToInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }

    /**
     * Convenience dispatcher. Throws [IllegalArgumentException] if [soundId] is not a drum sound.
     */
    fun generate(soundId: SoundId, sampleRate: Int): ShortArray
    {
        return when (soundId) {
            SoundId.DRUM_KICK -> generateKick(sampleRate)
            SoundId.DRUM_SNARE -> generateSnare(sampleRate)
            SoundId.DRUM_HIHAT_CLOSED -> generateHiHatClosed(sampleRate)
            SoundId.DRUM_HIHAT_OPEN -> generateHiHatOpen(sampleRate)
            SoundId.DRUM_CLAP -> generateClap(sampleRate)
            else -> throw IllegalArgumentException("SoundId $soundId is not a synthetic drum sound")
        }
    }

    private fun generateHiHat(
        sampleRate: Int,
        durationMs: Double,
        decayFactor: Double,
        seed: Int
    ): ShortArray
    {
        val numSamples = ((sampleRate / 1000.0) * durationMs).toInt()
        val samples = ShortArray(numSamples)
        val random = Random(seed)
        var prevNoise = 0.0

        val minExp = exp(-decayFactor)
        val scale = 1.0 / (1.0 - minExp)

        for (i in 0 until numSamples)
        {
            val rawNoise = random.nextDouble(-1.0, 1.0)
            // First-order high-pass difference: y[n] = x[n] - 0.8 * x[n-1]
            val hpNoise = rawNoise - 0.8 * prevNoise
            prevNoise = rawNoise

            val progress = i.toDouble() / (numSamples - 1)
            val envelope = (exp(-decayFactor * progress) - minExp) * scale

            val value = (hpNoise * envelope * Short.MAX_VALUE * 0.75).roundToInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }
}