/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import org.jhaeussler.practicetracker.metronomservice.MetronomeRepository.MAX_SUBDIVISIONS
import org.jhaeussler.practicetracker.metronomservice.MetronomeRepository.createInitialBeats
import kotlin.math.ceil
import kotlin.math.sin

data class Beat(
    val index: Int,
    val isEnabled: Boolean = true,
    val isAccent: Boolean = false
)

class MetronomeEngine(
    val sampleRate: Int = 44100,
    val clickDurationMs: Int = 10,
    regularToneHz: Double = 1000.0,
    accentToneHz: Double = 1500.0
) {
    val regularClickSamples: ShortArray = generateSineClickBuffer(regularToneHz)
    val accentClickSamples: ShortArray = generateSineClickBuffer(accentToneHz)

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
        private set
    var totalSamplesGenerated: Long = 0
        private set
    var totalBeatsDelivered: Long = 0
        private set
    @Volatile
    var beats: List<Beat> = createInitialBeats(4)
        private set
    @Volatile
    var currentBeatInMeasure = 0
        private set

    fun beatsPerMeasure() : Int = beats.size

    fun setBeatsList(newBeats: List<Beat>) {
        if (newBeats.isEmpty() || newBeats.size > MAX_SUBDIVISIONS) return

        beats = newBeats
        resetPhase()
    }
    fun setSubdivision(value : Int) : Boolean
    {
        if (value !in 1..MAX_SUBDIVISIONS) return false

        beats = createInitialBeats(value)
        resetPhase()
        return true
    }

    fun toggleBeatEnabled(index: Int) : Boolean
    {
        val current = beats
        if (index !in current.indices) return false

        beats = current.mapIndexed { i, beat ->
            if (i == index) beat.copy(isEnabled = !beat.isEnabled) else beat
        }

        return true
    }

    fun toggleBeatAccent(index: Int) : Boolean
    {
        val current = beats
        if (index !in current.indices) return false

        beats = current.mapIndexed { i, beat ->
            if (i == index) beat.copy(isAccent = !beat.isAccent) else beat
        }

        return true
    }

    fun resetPhase()
    {
        sampleIndexInBeat = 0.0
        totalSamplesGenerated = 0
        totalBeatsDelivered = 0
        currentBeatInMeasure = 0
    }

    private fun getClickSamplesForCurrentBeat(): ShortArray?
    {
        val activeBeats = beats
        if (activeBeats.isEmpty()) return null

        val safeIndex = currentBeatInMeasure.coerceIn(0, activeBeats.lastIndex)
        val activeBeat = activeBeats[safeIndex]

        return when {
            !activeBeat.isEnabled -> null
            activeBeat.isAccent -> accentClickSamples
            else -> regularClickSamples
        }
    }

    fun fillNextChunk(buffer: ShortArray, bpm: Int)
    {
        var bufferOffset = 0
        val bufferLength = buffer.size

        val samplesPerBeat = sampleRate * 60.0 / bpm.coerceAtLeast(1)

        while (bufferOffset < bufferLength)
        {
            if (sampleIndexInBeat >= samplesPerBeat) {
                advanceBeat(samplesPerBeat)
            }

            val samplesRemainingInBeat = ceil(samplesPerBeat - sampleIndexInBeat).toInt().coerceAtLeast(1)

            val samplesToProcess = minOf(bufferLength - bufferOffset, samplesRemainingInBeat)

            renderUntilBeatOrBufferEnds(buffer, bufferOffset, samplesToProcess)

            bufferOffset += samplesToProcess
            sampleIndexInBeat += samplesToProcess
            totalSamplesGenerated += samplesToProcess

            if (sampleIndexInBeat >= samplesPerBeat) {
                advanceBeat(samplesPerBeat)
            }
        }
    }

    private fun renderUntilBeatOrBufferEnds(
        buffer: ShortArray,
        bufferOffset: Int,
        samplesToProcess: Int
    ) {
        // Fast SIMD-accelerated zero-fill of the slice
        buffer.fill(0.toShort(), bufferOffset, bufferOffset + samplesToProcess)

        val clickSamples = getClickSamplesForCurrentBeat() ?: return

        // Calculate overlap between this segment and the click duration
        val startOfChunk = sampleIndexInBeat.toInt()
        val endOfChunk = startOfChunk + samplesToProcess

        val overlapStart = maxOf(startOfChunk, 0)
        val overlapEnd = minOf(endOfChunk, clickSamples.size)

        if (overlapStart < overlapEnd) {
            val copyLength = overlapEnd - overlapStart
            val destPos = bufferOffset + (overlapStart - startOfChunk)

            // Fast hardware block copy of the click audio
            System.arraycopy(clickSamples, overlapStart, buffer, destPos, copyLength)
        }
    }

    private fun advanceBeat(samplesPerBeat: Double)
    {
        totalBeatsDelivered++

        val measures = beats.size
        if (measures > 0) {
            currentBeatInMeasure = (currentBeatInMeasure + 1) % measures
        }

        // Sub-sample remainder preserved to guarantee zero drift over time
        sampleIndexInBeat -= samplesPerBeat
    }
}
