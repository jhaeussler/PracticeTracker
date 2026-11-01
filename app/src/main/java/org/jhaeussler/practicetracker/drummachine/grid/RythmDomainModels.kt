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
}

enum class BeatState {
    NORMAL,
    ACCENTED,
    MUTED
}

/**
 * An individual meter track with independent beat counts, accent/mute sets, and voices.
 */
data class MeterTrack(
    val beatCount: Int = 4,
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
         * Generates sensible musical default groupings (e.g. 6 -> [3, 3], 7 -> [4, 3], 21 -> [5, 5, 5, 6]).
         * The user can freely mutate this in the Rhythm page.
         */
        fun defaultGroupingFor(count: Int, maxChunk: Int = 5): List<Int> {
            if (count <= maxChunk) return listOf(count)

            // Common musical defaults
            if (count == 6) return listOf(3, 3)
            if (count == 7) return listOf(4, 3)
            if (count == 9) return listOf(3, 3, 3)
            if (count == 12) return listOf(3, 3, 3, 3)

            // Generic auto-partition into chunks of at most maxChunk (e.g. 21 -> [5, 5, 5, 6])
            val fullChunks = count / maxChunk
            val remainder = count % maxChunk
            val list = MutableList(fullChunks) { maxChunk }
            if (remainder > 0) {
                // Merge small remainder into last chunk if <= 2, otherwise add as new chunk
                if (remainder <= 2 && list.isNotEmpty()) {
                    list[list.lastIndex] += remainder
                } else {
                    list.add(remainder)
                }
            }
            return list
        }
    }

    /**
     * Uses the user's custom grouping if valid (sum == beatCount),
     * otherwise falls back to the default grouping.
     */
    fun getBeatWindows(): List<IntRange> {
        val activeGrouping = if (grouping.sum() == beatCount) {
            grouping
        } else {
            defaultGroupingFor(beatCount)
        }

        var start = 0
        return activeGrouping.map { size ->
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

