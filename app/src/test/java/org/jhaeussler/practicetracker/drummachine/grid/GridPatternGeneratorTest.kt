package org.jhaeussler.practicetracker.drummachine.grid

import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPatternGeneratorTest {

    private val epsilon = 0.0001

    @Test
    fun createPattern_straightFourFour_generatesFourChronologicalPrimaryEvents() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4, accentedBeats = setOf(0)),
            subdivision = Subdivision.NONE
        )

        val pattern = GridPatternGenerator.createPattern(config)

        assertEquals(4, pattern.nominalBeats)
        assertEquals(4, pattern.events.size)

        // Beat 0: Downbeat & Accented
        val b0 = pattern.events[0]
        assertEquals(0.0, b0.positionRatio, epsilon)
        assertEquals(SoundId.CLICK_ACCENT, b0.soundId)
        assertEquals(1.0f, b0.gain)
        assertTrue(b0.metadata.isDownbeat)
        assertTrue(b0.metadata.isPrimaryBeat)
        assertEquals(0, b0.metadata.beatIndex)

        // Beats 1..3: Regular primary beats
        val expectedRatios = listOf(0.25, 0.50, 0.75)
        for (i in 1..3) {
            val event = pattern.events[i]
            assertEquals(expectedRatios[i - 1], event.positionRatio, epsilon)
            assertEquals(SoundId.CLICK_BEAT, event.soundId)
            assertEquals(1.0f, event.gain)
            assertFalse(event.metadata.isDownbeat)
            assertTrue(event.metadata.isPrimaryBeat)
            assertEquals(i, event.metadata.beatIndex)
        }
    }

    @Test
    fun createPattern_mutedBeats_emitsGainZeroForVisualSync() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4, mutedBeats = setOf(1))
        )

        val pattern = GridPatternGenerator.createPattern(config)

        assertEquals(4, pattern.events.size)
        // Beat 1 must be present with gain 0.0f
        val mutedBeat = pattern.events[1]
        assertEquals(0.25, mutedBeat.positionRatio, epsilon)
        assertEquals(0.0f, mutedBeat.gain)

        // Beat 0, 2, 3 must have normal gain
        assertEquals(1.0f, pattern.events[0].gain)
        assertEquals(1.0f, pattern.events[2].gain)
        assertEquals(1.0f, pattern.events[3].gain)
    }

    @Test
    fun createPattern_eighthNotes_generatesSubticksWithoutCollisions() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4),
            subdivision = Subdivision.EIGHTHS
        )

        val pattern = GridPatternGenerator.createPattern(config)

        // 4 primary beats + 4 subdivision ticks = 8 total events
        assertEquals(8, pattern.events.size)

        // Verify strictly chronological ordering
        for (i in 0 until pattern.events.lastIndex) {
            assertTrue(
                "Events must be strictly ascending by positionRatio",
                pattern.events[i].positionRatio <= pattern.events[i + 1].positionRatio
            )
        }

        // Subdivision pulses should be at 0.125, 0.375, 0.625, 0.875
        val subTicks = pattern.events.filter { !it.metadata.isPrimaryBeat }
        assertEquals(4, subTicks.size)

        val expectedSubRatios = listOf(0.125, 0.375, 0.625, 0.875)
        for (i in subTicks.indices) {
            assertEquals(expectedSubRatios[i], subTicks[i].positionRatio, epsilon)
            assertEquals(SoundId.CLICK_SUBDIVISION, subTicks[i].soundId)
        }
    }

    @Test
    fun createPattern_sixteenthTriplets_generatesSixPulsesPerBeatDeduplicatingPrimary() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4),
            subdivision = Subdivision.SIXTEENTH_TRIPLETS // 6 pulses per beat
        )

        val pattern = GridPatternGenerator.createPattern(config)

        // Total pulses in 4 beats = 24.
        // 4 coincide with primary beats (0.0, 0.25, 0.50, 0.75), so 20 subdivision events are added.
        assertEquals(24, pattern.events.size)

        val primaryBeats = pattern.events.filter { it.metadata.isPrimaryBeat }
        val subTicks = pattern.events.filter { !it.metadata.isPrimaryBeat }

        assertEquals(4, primaryBeats.size)
        assertEquals(20, subTicks.size)
    }

    @Test
    fun createPattern_quarterTriplets_generatesCorrectCrossBeatTimestamps() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4),
            subdivision = Subdivision.QUARTER_TRIPLETS // 3:2 across 2 beats
        )

        val pattern = GridPatternGenerator.createPattern(config)

        // In 4/4, quarter triplets occur at:
        // 0.0 (Primary Downbeat), 1/6 (Sub), 2/6 (Sub), 3/6 = 0.5 (Primary Beat 2), 4/6 (Sub), 5/6 (Sub)
        // Primary beats at 0.25 and 0.75 remain intact.
        // Total events: 4 primary beats + 4 non-colliding quarter-triplets = 8 events.
        assertEquals(8, pattern.events.size)

        val quarterTripletSubs = pattern.events.filter { !it.metadata.isPrimaryBeat }
        assertEquals(4, quarterTripletSubs.size)

        val expectedTripletRatios = listOf(1.0 / 6.0, 2.0 / 6.0, 4.0 / 6.0, 5.0 / 6.0)
        for (i in quarterTripletSubs.indices) {
            assertEquals(expectedTripletRatios[i], quarterTripletSubs[i].positionRatio, epsilon)
            assertEquals(SoundId.CLICK_SUBDIVISION, quarterTripletSubs[i].soundId)
        }
    }

    @Test
    fun createPattern_polymeter_scalesToLcmAndLayersBothMeters() {
        // 4/4 primary vs 3/4 counter meter -> LCM = 12 beats
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(beatCount = 4, soundIdPrimary = SoundId.CLICK_BEAT),
            subdivision = Subdivision.NONE,
            counterMeter = MeterTrack(beatCount = 3, soundIdPrimary = SoundId.DRUM_SNARE, soundIdAccent = SoundId.DRUM_SNARE)
        )

        val pattern = GridPatternGenerator.createPattern(config)

        assertEquals(12, pattern.nominalBeats)

        // 12 primary beats + 12 counter meter beats = 24 events
        val primaryEvents = pattern.events.filter { it.soundId == SoundId.CLICK_BEAT || it.soundId == SoundId.CLICK_ACCENT }
        val counterEvents = pattern.events.filter { it.soundId == SoundId.DRUM_SNARE }

        assertEquals(12, primaryEvents.size)
        assertEquals(12, counterEvents.size)

        // Ensure chronological ordering across merged tracks
        for (i in 0 until pattern.events.lastIndex) {
            assertTrue(pattern.events[i].positionRatio <= pattern.events[i + 1].positionRatio)
        }
    }
}