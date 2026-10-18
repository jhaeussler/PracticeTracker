package org.jhaeussler.practicetracker.drummachine.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmicDomainModelsTest {

    // --- RhythmMath Tests ---

    @Test
    fun rhythmMath_gcdAndLcm_computesAccurately() {
        assertEquals(1, RhythmMath.gcd(4, 5))
        assertEquals(20, RhythmMath.lcm(4, 5))

        assertEquals(2, RhythmMath.gcd(4, 6))
        assertEquals(12, RhythmMath.lcm(4, 6))

        assertEquals(4, RhythmMath.lcm(4, 4))
        assertEquals(21, RhythmMath.lcm(3, 7))
    }

    // --- Subdivision Ratio Invariants ---

    @Test
    fun subdivision_ratiosAndCrossBeatFlagsAreCorrect() {
        assertFalse(Subdivision.NONE.isCrossBeat)
        assertEquals(1, Subdivision.NONE.pulses)
        assertEquals(1, Subdivision.NONE.overBeats)

        assertFalse(Subdivision.EIGHTH_TRIPLETS.isCrossBeat)
        assertEquals(3, Subdivision.EIGHTH_TRIPLETS.pulses)
        assertEquals(1, Subdivision.EIGHTH_TRIPLETS.overBeats)

        assertFalse(Subdivision.SIXTEENTH_TRIPLETS.isCrossBeat)
        assertEquals(6, Subdivision.SIXTEENTH_TRIPLETS.pulses)
        assertEquals(1, Subdivision.SIXTEENTH_TRIPLETS.overBeats)

        assertTrue(Subdivision.QUARTER_TRIPLETS.isCrossBeat)
        assertEquals(3, Subdivision.QUARTER_TRIPLETS.pulses)
        assertEquals(2, Subdivision.QUARTER_TRIPLETS.overBeats)
    }

    // --- MeterTrack Tests ---

    @Test
    fun meterTrack_throwsOnInvalidBeatCount() {
        assertThrows(IllegalArgumentException::class.java) {
            MeterTrack(beatCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeterTrack(beatCount = -3)
        }
    }

    @Test
    fun meterTrack_toggleAccent_togglesStateCorrectly() {
        val track = MeterTrack(beatCount = 4, accentedBeats = setOf(0))

        val toggledOn = track.toggleAccent(2)
        assertTrue(toggledOn.isAccented(2))
        assertTrue(toggledOn.isAccented(0))

        val toggledOff = toggledOn.toggleAccent(0)
        assertFalse(toggledOff.isAccented(0))
        assertTrue(toggledOff.isAccented(2))
    }

    @Test
    fun meterTrack_toggleMute_togglesStateCorrectly() {
        val track = MeterTrack(beatCount = 4, mutedBeats = emptySet())

        val muted = track.toggleMute(1)
        assertTrue(muted.isMuted(1))
        assertFalse(muted.isMuted(0))

        val unmuted = muted.toggleMute(1)
        assertFalse(unmuted.isMuted(1))
    }

    // --- MetronomeConfig Polymetric Cycle Scaling ---

    @Test
    fun metronomeConfig_cycleNominalBeats_singleMeter() {
        val config4 = MetronomeConfig(primaryMeter = MeterTrack(4))
        assertEquals(4, config4.cycleNominalBeats)

        val config7 = MetronomeConfig(primaryMeter = MeterTrack(7))
        assertEquals(7, config7.cycleNominalBeats)
    }

    @Test
    fun metronomeConfig_cycleNominalBeats_polymeterCalculatesLcm() {
        // 4/4 vs 5/4 polymeter aligns after 20 beats
        val polymeter4_5 = MetronomeConfig(
            primaryMeter = MeterTrack(4),
            counterMeter = MeterTrack(5)
        )
        assertEquals(20, polymeter4_5.cycleNominalBeats)

        // 4/4 vs 6/4 polymeter aligns after 12 beats
        val polymeter4_6 = MetronomeConfig(
            primaryMeter = MeterTrack(4),
            counterMeter = MeterTrack(6)
        )
        assertEquals(12, polymeter4_6.cycleNominalBeats)
    }
}