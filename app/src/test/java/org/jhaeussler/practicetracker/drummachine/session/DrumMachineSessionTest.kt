package org.jhaeussler.practicetracker.drummachine.session

import org.jhaeussler.practicetracker.drummachine.engine.EventMetadata
import org.jhaeussler.practicetracker.drummachine.engine.PlaybackEvent
import org.jhaeussler.practicetracker.drummachine.grid.Subdivision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DrumMachineSessionTest {

    private lateinit var session: DrumMachineSession

    @Before
    fun setUp() {
        session = DrumMachineSession()
    }

    @Test
    fun initialState_defaultsAreCorrect() {
        assertEquals(RhythmMode.METRONOME, session.mode.value)
        assertEquals(120, session.bpm.value)
        assertFalse(session.isRunning.value)
        assertNull(session.currentPlaybackEvent.value)

        // Default metronome: 4/4 straight
        assertEquals(4, session.metronomeConfig.value.primaryMeter.beatCount)
        assertEquals(Subdivision.NONE, session.metronomeConfig.value.subdivision)

        // Active pattern should be pre-compiled to 4 nominal beats
        assertEquals(4, session.activePattern.value.nominalBeats)
        assertEquals(4, session.activePattern.value.events.size)
    }

    @Test
    fun setBpm_clampsWithinValidMusicalRange() {
        session.setBpm(140)
        assertEquals(140, session.bpm.value)

        // Clamp below MIN_BPM (30)
        session.setBpm(15)
        assertEquals(DrumMachineSession.MIN_BPM, session.bpm.value)

        // Clamp above MAX_BPM (300)
        session.setBpm(500)
        assertEquals(DrumMachineSession.MAX_BPM, session.bpm.value)
    }

    @Test
    fun setRunning_updatesState() {
        session.setRunning(true)
        assertTrue(session.isRunning.value)

        session.setRunning(false)
        assertFalse(session.isRunning.value)
    }

    @Test
    fun setBeatCount_updatesConfigAndRecompilesActivePattern() {
        // Change from 4/4 to 3/4
        session.setBeatCount(3)

        assertEquals(3, session.metronomeConfig.value.primaryMeter.beatCount)
        assertEquals(3, session.activePattern.value.nominalBeats)
        assertEquals(3, session.activePattern.value.events.size)
    }

    @Test
    fun toggleBeatMute_updatesConfigAndRecompilesPatternGains() {
        session.toggleBeatMute(1)

        assertTrue(session.metronomeConfig.value.primaryMeter.isMuted(1))
        // Event 1 in activePattern must now have gain 0.0f (visual sync preserved)
        assertEquals(0.0f, session.activePattern.value.events[1].gain)
        assertEquals(1.0f, session.activePattern.value.events[0].gain)
    }

    @Test
    fun setSubdivision_updatesConfigAndRecompilesSubticks() {
        // 4/4 with 8th notes -> 4 primary + 4 sub-ticks = 8 events
        session.setSubdivision(Subdivision.EIGHTHS)

        assertEquals(Subdivision.EIGHTHS, session.metronomeConfig.value.subdivision)
        assertEquals(8, session.activePattern.value.events.size)
    }

    @Test
    fun updatePlaybackEvent_relaysEventForUi() {
        val event = PlaybackEvent(
            samplePosition = 48000L,
            metadata = EventMetadata(beatIndex = 2, isPrimaryBeat = true)
        )

        session.updatePlaybackEvent(event)
        assertEquals(event, session.currentPlaybackEvent.value)
    }
}