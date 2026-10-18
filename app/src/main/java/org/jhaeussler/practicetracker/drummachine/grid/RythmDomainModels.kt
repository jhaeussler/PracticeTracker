package org.jhaeussler.practicetracker.drummachine.grid

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import kotlin.math.abs

/**
 * Mathematical utilities for cyclic rhythm calculations and polymetric phase alignment.
 */
object RhythmMath {

    tailrec fun gcd(a: Int, b: Int): Int {
        val x = abs(a)
        val y = abs(b)
        return if (y == 0) x else gcd(y, x % y)
    }

    fun lcm(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        val gcdVal = gcd(a, b)
        // Divide before multiply to prevent intermediate integer overflow
        return (abs(a) / gcdVal) * abs(b)
    }
}

/**
 * Rhythmic division modeled as [pulses] over [overBeats].
 */
enum class Subdivision(val pulses: Int, val overBeats: Int) {
    NONE(pulses = 1, overBeats = 1),                  // Straight quarter notes (1:1)
    EIGHTHS(pulses = 2, overBeats = 1),               // 8th notes (2:1)
    EIGHTH_TRIPLETS(pulses = 3, overBeats = 1),       // 8th note triplets (3:1)
    SIXTEENTHS(pulses = 4, overBeats = 1),            // 16th notes (4:1)
    SIXTEENTH_TRIPLETS(pulses = 6, overBeats = 1),    // 16th note triplets / Sextuplets (6:1)
    QUARTER_TRIPLETS(pulses = 3, overBeats = 2);      // Quarter note triplets (3:2)

    val isCrossBeat: Boolean
        get() = overBeats > 1
}

/**
 * An individual meter track with independent beat counts, accent/mute sets, and voices.
 */
data class MeterTrack(
    val beatCount: Int = 4,
    val accentedBeats: Set<Int> = setOf(0),
    val mutedBeats: Set<Int> = emptySet(),
    val soundIdPrimary: SoundId = SoundId.CLICK_BEAT,
    val soundIdAccent: SoundId = SoundId.CLICK_ACCENT
) {
    init {
        require(beatCount >= 1) { "beatCount must be at least 1" }
    }

    fun isAccented(beatIndex: Int): Boolean = beatIndex in accentedBeats
    fun isMuted(beatIndex: Int): Boolean = beatIndex in mutedBeats

    /**
     * Returns a new [MeterTrack] with the accent state toggled for [beatIndex].
     */
    fun toggleAccent(beatIndex: Int): MeterTrack
    {
        val updated = if (beatIndex in accentedBeats) {
            accentedBeats - beatIndex
        } else {
            accentedBeats + beatIndex
        }
        return copy(accentedBeats = updated)
    }

    /**
     * Returns a new [MeterTrack] with the mute state toggled for [beatIndex].
     */
    fun toggleMute(beatIndex: Int): MeterTrack
    {
        val updated = if (beatIndex in mutedBeats) {
            mutedBeats - beatIndex
        } else {
            mutedBeats + beatIndex
        }
        return copy(mutedBeats = updated)
    }
}

/**
 * Root configuration container for Metronome Mode.
 */
data class MetronomeConfig(
    val primaryMeter: MeterTrack = MeterTrack(4),
    val subdivision: Subdivision = Subdivision.NONE,
    val counterMeter: MeterTrack? = null
) {
    /**
     * The total nominal beat count of the combined macro-cycle.
     * - Single meter: equals primaryMeter.beatCount.
     * - Polymeter: equals LCM(primaryMeter.beatCount, counterMeter.beatCount).
     */
    val cycleNominalBeats: Int
        get() = if (counterMeter == null) {
            primaryMeter.beatCount
        } else {
            RhythmMath.lcm(primaryMeter.beatCount, counterMeter.beatCount)
        }
}

