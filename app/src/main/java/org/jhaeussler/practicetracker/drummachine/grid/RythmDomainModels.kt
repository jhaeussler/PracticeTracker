package org.jhaeussler.practicetracker.drummachine.grid

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import kotlin.math.abs

/**
 * Mathematical utilities for cyclic rhythm calculations and polymetric phase alignment.
 */
object RhythmMath {

    // greatest common divisor
    tailrec fun gcd(a: Int, b: Int): Int
    {
        val x = abs(a)
        val y = abs(b)

        return if (y == 0) x else gcd(y, x % y)
    }

    // lowest common multiple
    fun lcm(a: Int, b: Int): Int
    {
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

    fun getLabelFor(beatUnit: BeatUnit): String = when (this)
    {
        NONE -> "1:1 (Quarter notes)"

        EIGHTHS -> when (beatUnit) {
            BeatUnit.HALF -> "2:1 (Quarter notes)"
            BeatUnit.QUARTER -> "2:1 (8th notes)"
            BeatUnit.EIGHTH -> "2:1 (16th notes)"
            BeatUnit.SIXTEENTH -> "2:1 (32nd notes)"
        }

        EIGHTH_TRIPLETS -> when (beatUnit) {
            BeatUnit.HALF -> "3:1 (Quarter Triplets)"
            BeatUnit.QUARTER -> "3:1 (Triplets)"
            BeatUnit.EIGHTH -> "3:1 (16th Triplets)"
            BeatUnit.SIXTEENTH -> "3:1 (32nd Triplets)"
        }

        SIXTEENTHS -> when (beatUnit) {
            BeatUnit.HALF -> "4:1 (8th notes)"
            BeatUnit.QUARTER -> "4:1 (16th notes)"
            BeatUnit.EIGHTH -> "4:1 (32nd notes)"
            BeatUnit.SIXTEENTH -> "4:1 (64th notes)"
        }

        SIXTEENTH_TRIPLETS -> "6:1 (Sextuplets)"

        QUARTER_TRIPLETS -> "3:2 (Cross-Beat)"
    }
}

enum class BeatState {
    NORMAL,
    ACCENTED,
    MUTED
}

/**
 * The reference note value (denominator) of a time signature.
 * [quarterMultiplier] defines how many quarter-note beats (the standard BPM unit)
 * each pulse of this unit represents.
 */
enum class BeatUnit(
    val denominator: Int,
    val quarterMultiplier: Double,
    val symbol: String
) {
    HALF(denominator = 2, quarterMultiplier = 2.0, symbol = "⬤"),
    QUARTER(denominator = 4, quarterMultiplier = 1.0, symbol = "♩"),
    EIGHTH(denominator = 8, quarterMultiplier = 0.5, symbol = "♪"),
    SIXTEENTH(denominator = 16, quarterMultiplier = 0.25, symbol = "♬")
}

/**
 * An individual meter track with independent beat counts, accent/mute sets, and voices.
 */
data class MeterTrack(
    val beatCount: Int = 4,
    val beatUnit: BeatUnit = BeatUnit.QUARTER,
    val grouping: List<Int> = listOf(beatCount), // e.g. [5, 5, 5, 6] for 21/8, or [4] for 4/4
    val accentedBeats: Set<Int> = setOf(0),
    val mutedBeats: Set<Int> = emptySet(),
    val soundIdPrimary: SoundId = SoundId.CLICK_BEAT,
    val soundIdAccent: SoundId = SoundId.CLICK_ACCENT
) {
    init {
        require(beatCount >= 1) { "beatCount must be at least 1" }
    }

    companion object {
        /**
         * Splits [size] into balanced chunks of at most [maxChunkSize],
         * avoiding awkward 1-beat orphans (e.g. 6 -> [3, 3], 7 -> [4, 3], 11 -> [4, 4, 3]).
         */
        fun splitEvenly(size: Int, maxChunkSize: Int = 5): List<Int>
        {
            if (size <= maxChunkSize) return listOf(size)

            val pageCount = (size + maxChunkSize - 1) / maxChunkSize // ceil(size / maxChunkSize)
            val base = size / pageCount
            val remainder = size % pageCount

            return List(pageCount) { i ->
                base + if (i < remainder) 1 else 0
            }
        }


        /**
         * Generates sensible musical default groupings (e.g. 6 -> [3, 3], 7 -> [4, 3], 21 -> [5, 5, 5, 6]).
         * The user can freely mutate this in the Rhythm page.
         */
        fun defaultGroupingFor(count: Int, maxChunk: Int = 5): List<Int>
        {
            if (count <= 6) return listOf(count) // 6 is allowed as a single musical group!

            // Even numbers: groups of 4 with a trailing 2 if necessary
            if (count % 2 == 0) {
                val fours = count / 4
                val remainder = count % 4 // Always 0 or 2 for even numbers
                val list = MutableList(fours) { 4 }
                if (remainder > 0) {
                    list.add(remainder) // Adds the trailing 2
                }
                return list
            }

            // Common odd musical standards
            if (count == 7) return listOf(4, 3)
            if (count == 9) return listOf(3, 3, 3)

            // Other odd counts (e.g. 11, 13, 21...) split evenly into chunks <= 5
            return splitEvenly(count, maxChunkSize = maxChunk)
        }

    }

    /**
    * PURE UI PAGINATION:
    * Splits any musical group > 5 into balanced sub-windows for BeatRibbon display.
    * Does NOT touch musical grouping or accents.
    */
    fun getBeatWindows(maxWindowSize: Int = 5): List<IntRange> {
        val activeGrouping = if (grouping.sum() == beatCount) {
            grouping
        } else {
            defaultGroupingFor(beatCount)
        }

        // UI-only viewport division
        val windowSizes = activeGrouping.flatMap { groupSize ->
            splitEvenly(groupSize, maxWindowSize)
        }

        var start = 0
        return windowSizes.map { size ->
            val range = start until (start + size)
            start += size
            range
        }
    }

    fun isAccented(beatIndex: Int): Boolean = beatIndex in accentedBeats
    fun isMuted(beatIndex: Int): Boolean = beatIndex in mutedBeats

    fun getBeatState(beatIndex: Int): BeatState = when (beatIndex) {
        in mutedBeats -> BeatState.MUTED
        in accentedBeats -> BeatState.ACCENTED
        else -> BeatState.NORMAL
    }

    /**
     * Cycles beat state: NORMAL -> ACCENTED -> MUTED -> NORMAL
     */
    fun cycleBeatState(beatIndex: Int): MeterTrack {
        val nextState = when (getBeatState(beatIndex)) {
            BeatState.NORMAL -> BeatState.ACCENTED
            BeatState.ACCENTED -> BeatState.MUTED
            BeatState.MUTED -> BeatState.NORMAL
        }
        return setBeatState(beatIndex, nextState)
    }

    fun setBeatState(beatIndex: Int, state: BeatState): MeterTrack {
        val newAccents = when (state) {
            BeatState.ACCENTED -> accentedBeats + beatIndex
            else -> accentedBeats - beatIndex
        }
        val newMutes = when (state) {
            BeatState.MUTED -> mutedBeats + beatIndex
            else -> mutedBeats - beatIndex
        }
        return copy(accentedBeats = newAccents, mutedBeats = newMutes)
    }

    /**
     * Toggles accent state on/off. If the beat was muted, it un-mutes and becomes accented.
     */
    fun toggleAccent(beatIndex: Int): MeterTrack {
        val nextState = if (isAccented(beatIndex)) BeatState.NORMAL else BeatState.ACCENTED
        return setBeatState(beatIndex, nextState)
    }

    /**
     * Toggles mute state on/off. If the beat was accented, it un-accents and becomes muted.
     */
    fun toggleMute(beatIndex: Int): MeterTrack {
        val nextState = if (isMuted(beatIndex)) BeatState.NORMAL else BeatState.MUTED
        return setBeatState(beatIndex, nextState)
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

