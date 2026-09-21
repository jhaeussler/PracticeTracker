/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

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
    var totalSamplesGenerated: Long = 0
        private set

    fun resetPhase() {
        sampleIndexInBeat = 0.0
        totalSamplesGenerated = 0
    }

    val samplesPerMs = sampleRate / 1000.0
    // hard-resetting to 0 ms drift would create audible artifacts
    // so only correct the drift slightly
    val driftCorrectionFactor = 0.05
    fun syncToHardwareClock(elapsedNanos: Long, hardwareSamplesPlayed: Long)
    {
        val expectedTotalSamples = (elapsedNanos / 1_000_000_000.0) * sampleRate
        val driftInSamples = hardwareSamplesPlayed - expectedTotalSamples

        // Apply a gentle low-pass nudge if drift exceeds 1ms
        if (kotlin.math.abs(driftInSamples) > samplesPerMs) {
            sampleIndexInBeat -= driftInSamples * driftCorrectionFactor
        }
    }

    fun fillNextChunk(buffer: ShortArray, bpm: Int) {
        // 1. Calculate how many silent samples belong between ticks for current BPM
        val samplesPerBeat = sampleRate * 60.0 / bpm

        for (i in buffer.indices)
        {
            val currentSampleIndexAsInt = sampleIndexInBeat.toInt()

            if (currentSampleIndexAsInt in clickSamples.indices) {
                buffer[i] = clickSamples[currentSampleIndexAsInt]
            } else {
                buffer[i] = 0
            }

            sampleIndexInBeat += 1.0
            totalSamplesGenerated++

            // samplesPerBeat = sr * 60 / BPM
            // BPM = 133: samplesPerBeat = 44100 * 60 / 133 = 19894.7368
            if (sampleIndexInBeat >= samplesPerBeat) {
                // float substraction to preserves fractional remainder -> no drift due to rounding
                sampleIndexInBeat -= samplesPerBeat
            }
        }
    }
}
