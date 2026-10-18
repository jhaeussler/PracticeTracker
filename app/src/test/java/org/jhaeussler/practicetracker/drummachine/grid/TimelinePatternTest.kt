package org.jhaeussler.practicetracker.drummachine.grid

import org.jhaeussler.practicetracker.drummachine.engine.EventMetadata
import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import org.jhaeussler.practicetracker.drummachine.engine.TimelineEvent
import org.jhaeussler.practicetracker.drummachine.engine.TimelinePattern
import org.junit.Assert.*
import org.junit.Test

class TimelinePatternTest {

    @Test
    fun `empty events pattern generates uniform anchors based on nominalBeats`() {
        val pattern = TimelinePattern(nominalBeats = 4, events = emptyList())

        assertEquals(4, pattern.beatAnchors.size)
        assertEquals(0.00, pattern.beatAnchors[0].startRatio, 1e-6)
        assertEquals(0.25, pattern.beatAnchors[0].endRatio, 1e-6)
        assertEquals(0.75, pattern.beatAnchors[3].startRatio, 1e-6)
        assertEquals(1.00, pattern.beatAnchors[3].endRatio, 1e-6)
    }

    @Test
    fun `standard 4_4 meter generates four uniform beat anchors`() {
        val events = List(4) { index ->
            TimelineEvent(
                positionRatio = index * 0.25,
                soundId = SoundId.CLICK_BEAT,
                gain = 1.0f,
                metadata = EventMetadata(beatIndex = index, isPrimaryBeat = true)
            )
        }

        val pattern = TimelinePattern(nominalBeats = 4, events = events)

        assertEquals(4, pattern.beatAnchors.size)

        assertEquals(0.00, pattern.beatAnchors[0].startRatio, 1e-6)
        assertEquals(0.25, pattern.beatAnchors[0].endRatio, 1e-6)

        assertEquals(0.25, pattern.beatAnchors[1].startRatio, 1e-6)
        assertEquals(0.50, pattern.beatAnchors[1].endRatio, 1e-6)

        assertEquals(0.50, pattern.beatAnchors[2].startRatio, 1e-6)
        assertEquals(0.75, pattern.beatAnchors[2].endRatio, 1e-6)

        assertEquals(0.75, pattern.beatAnchors[3].startRatio, 1e-6)
        assertEquals(1.00, pattern.beatAnchors[3].endRatio, 1e-6)
    }

    @Test
    fun `subdivision pulses do not generate spurious beat anchors`() {
        val events = listOf(
            TimelineEvent(0.0, SoundId.CLICK_BEAT, 1.0f, EventMetadata(beatIndex = 0, isPrimaryBeat = true)),
            TimelineEvent(0.125, SoundId.CLICK_SUBDIVISION, 1.0f, EventMetadata(beatIndex = 0, subIndex = 1, isPrimaryBeat = false)),
            TimelineEvent(0.25, SoundId.CLICK_BEAT, 1.0f, EventMetadata(beatIndex = 1, isPrimaryBeat = true)),
            TimelineEvent(0.375, SoundId.CLICK_SUBDIVISION, 1.0f, EventMetadata(beatIndex = 1, subIndex = 1, isPrimaryBeat = false))
        )

        val pattern = TimelinePattern(nominalBeats = 2, events = events)

        assertEquals(2, pattern.beatAnchors.size)
        assertEquals(0.0, pattern.beatAnchors[0].startRatio, 1e-6)
        assertEquals(0.25, pattern.beatAnchors[0].endRatio, 1e-6)
        assertEquals(0.25, pattern.beatAnchors[1].startRatio, 1e-6)
        assertEquals(1.0, pattern.beatAnchors[1].endRatio, 1e-6)
    }

    @Test
    fun `polymeter grid pattern with 20 nominal beats generates 20 continuous anchors`() {
        val config = MetronomeConfig(
            primaryMeter = MeterTrack(4),
            counterMeter = MeterTrack(5)
        )
        val pattern = GridPatternGenerator.createPattern(config)

        assertEquals(20, pattern.nominalBeats)
        assertEquals(20, pattern.beatAnchors.size)
        assertEquals(0.0, pattern.beatAnchors[0].startRatio, 1e-6)
        assertEquals(1.0, pattern.beatAnchors[19].endRatio, 1e-6)

        for (i in 0 until 19) {
            assertEquals(
                "Anchors must be continuous",
                pattern.beatAnchors[i].endRatio,
                pattern.beatAnchors[i + 1].startRatio,
                1e-6
            )
        }
    }

    @Test
    fun `structural equality succeeds across distinct array instances with same content`() {
        val patternA = TimelinePattern(nominalBeats = 4, events = emptyList())
        val patternB = TimelinePattern(nominalBeats = 4, events = emptyList())

        assertEquals(patternA, patternB)
        assertEquals(patternA.hashCode(), patternB.hashCode())
    }
}