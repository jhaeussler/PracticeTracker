package org.jhaeussler.practicetracker.metronomservice

import kotlin.math.sin


class MetronomeEngine(
    val sampleRate: Int = 44100,
    val clickDurationMs: Int = 10,
    val toneFrequencyHz: Double = 1000.0
) {
    // Pre-generate a short 1000 Hz sine wave click (10ms duration)
    val clickSamples: ShortArray by lazy {
        val numSamples = ((sampleRate / 1000.0) * clickDurationMs).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            // sin (2 * pi * x) -> sinus with amplitude 1 und period 1
            // sin (2 * pi * x / sr) -> will give us period of 44100 (samples)
            // -> multiply with Hz we actually want to compress the sin curve
            val angle = 2.0 * Math.PI * i * toneFrequencyHz / sampleRate
            // Scale to 16-bit short max value (~32767) with a slight fade out
            val envelope = 1.0 - (i.toDouble() / numSamples)
            samples[i] = (sin(angle) * Short.MAX_VALUE * envelope).toInt().toShort()
        }

        samples
    }

    var sampleIndexInBeat = 0.0

    fun resetPhase() {
        sampleIndexInBeat = 0.0
    }

    fun fillNextChunk(buffer: ShortArray, bpm: Int) {
        // 1. Calculate how many silent samples belong between ticks for current BPM
        val samplesPerBeat = sampleRate * 60.0 / bpm

        for (i in buffer.indices)
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
            if (sampleIndexInBeat >= samplesPerBeat) {
                // float substraction to preserves fractional remainder -> no drift due to rounding
                sampleIndexInBeat -= samplesPerBeat
            }
        }
    }
}
