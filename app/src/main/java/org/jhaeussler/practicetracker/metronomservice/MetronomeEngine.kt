/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import kotlin.math.sin


class MetronomeEngine(
    val sampleRate: Int = 44100,
    val clickDurationMs: Int = 10,
    val regularToneHz: Double = 1000.0,
    val accentToneHz: Double = 1500.0,
    var enableWallClockSync: Boolean = true
) {
    // lazy means load only once and cache in memory
    val regularClickSamples: ShortArray by lazy {
        generateSineClickBuffer(regularToneHz)
    }
    val accentClickSamples: ShortArray by lazy {
        generateSineClickBuffer(accentToneHz)
    }

    private fun generateSineClickBuffer(frequency: Double): ShortArray {
        val numSamples = ((sampleRate / 1000.0) * clickDurationMs).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            // sin (2 * pi * x) -> sinus with amplitude 1 und period 1
            // sin (2 * pi * x / sr) -> will give us period of 44100 (samples)
            // -> multiply with Hz we actually want to compress the sin curve
            val angle = 2.0 * Math.PI * i * frequency / sampleRate
            // Scale to 16-bit short max value (~32767) with a slight fade out
            val envelope = 1.0 - (i.toDouble() / numSamples)
            samples[i] = (sin(angle) * Short.MAX_VALUE * envelope).toInt().toShort()
        }

        return samples
    }

    var sampleIndexInBeat = 0.0
    var totalSamplesGenerated: Long = 0
        private set

    // Tracks target timing relative to system uptime
    private var startTimeNanos: Long = 0
    private var beatsDelivered: Long = 0

    // sub-division tracking
    var beatsPerMeasure = 4
        private set
    @Volatile
    var currentBeatInMeasure = 0

    fun setSubdivision(value : Int) : Boolean {
        if (value in 1..16)
        {
            beatsPerMeasure = value
            return true
        }

        return false
    }

    fun resetPhase() {
        sampleIndexInBeat = 0.0
        totalSamplesGenerated = 0
        startTimeNanos = System.nanoTime()
        beatsDelivered = 0
        currentBeatInMeasure = 0
    }

    private var currentClickSamples: ShortArray = accentClickSamples

    fun fillNextChunk(buffer: ShortArray, bpm: Int) {
        // 1. Calculate how many silent samples belong between ticks for current BPM
        val samplesPerBeat = sampleRate * 60.0 / bpm

        for (i in buffer.indices)
        {
            val currentSampleIndexAsInt = sampleIndexInBeat.toInt()

            if (currentSampleIndexAsInt in currentClickSamples.indices) {
                buffer[i] = currentClickSamples[currentSampleIndexAsInt]
            } else {
                buffer[i] = 0
            }

            sampleIndexInBeat += 1.0
            totalSamplesGenerated++

            // samplesPerBeat = sr * 60 / BPM
            // BPM = 133: samplesPerBeat = 44100 * 60 / 133 = 19894.7368
            if (sampleIndexInBeat >= samplesPerBeat)
            {
                beatsDelivered++

                currentBeatInMeasure = (currentBeatInMeasure + 1) % beatsPerMeasure

                currentClickSamples = if (currentBeatInMeasure == 0) {
                    accentClickSamples
                } else {
                    regularClickSamples
                }

                // only apply phase correction mechanism if the corresponding flag is set
                val phaseCorrection = if (enableWallClockSync) {
                    // Calculate where sampleIndexInBeat SHOULD be based on nanosecond wall clock
                    val elapsedNanos = System.nanoTime() - startTimeNanos
                    val targetBeats = (elapsedNanos / 1_000_000_000.0) * (bpm / 60.0)
                    val driftInBeats = beatsDelivered - targetBeats

                    driftInBeats * 0.01 // Gentle 1% nudge
                }
                else {
                    0.0
                }

                // float substraction to preserves fractional remainder -> no drift due to rounding
                // phase correction will adjust the silence length
                // to compensate to hardware clock inaccuracy
                sampleIndexInBeat = (sampleIndexInBeat - samplesPerBeat) + phaseCorrection
            }
        }
    }
}
