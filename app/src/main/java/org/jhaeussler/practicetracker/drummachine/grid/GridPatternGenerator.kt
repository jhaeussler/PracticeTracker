package org.jhaeussler.practicetracker.drummachine.grid

import org.jhaeussler.practicetracker.drummachine.engine.EventMetadata
import org.jhaeussler.practicetracker.drummachine.engine.SoundId
import org.jhaeussler.practicetracker.drummachine.engine.TimelineEvent
import org.jhaeussler.practicetracker.drummachine.engine.TimelinePattern

import kotlin.math.abs
import kotlin.math.roundToLong

object GridPatternGenerator {

    private const val COLLISION_EPSILON = 1e-5

    /**
     * Compiles [config] into a deterministic, chronological [TimelinePattern]
     * scaled along the normalized timeline cycle [0.0, 1.0).
     */
    fun createPattern(config: MetronomeConfig): TimelinePattern
    {
        val macroBeats = config.cycleNominalBeats
        val events = ArrayList<TimelineEvent>()

        // 1. Compile Primary Meter Track
        val primary = config.primaryMeter

        for (b in 0 until macroBeats)
        {
            val beatIndex = b % primary.beatCount
            val ratio = b.toDouble() / macroBeats
            val isDownbeat = (beatIndex == 0)
            val isAccented = primary.isAccented(beatIndex)
            val isMuted = primary.isMuted(beatIndex)

            val soundId = if (isAccented) primary.soundIdAccent else primary.soundIdPrimary
            val gain = if (isMuted) 0.0f else 1.0f

            events.add(
                TimelineEvent(
                    positionRatio = ratio,
                    soundId = soundId,
                    gain = gain,
                    metadata = EventMetadata(
                        beatIndex = beatIndex,
                        subIndex = 0,
                        isPrimaryBeat = true,
                        isDownbeat = isDownbeat
                    )
                )
            )
        }

        // 2. Compile Subdivision Layer (if enabled)
        val sub = config.subdivision

        if (sub != Subdivision.NONE)
        {
            val totalSubPulses = ((macroBeats * sub.pulses - 1) / sub.overBeats) + 1
            val deltaPhi = sub.overBeats.toDouble() / (macroBeats * sub.pulses)

            for (p in 0 until totalSubPulses)
            {
                val ratio = p * deltaPhi

                // Collision Detection: Suppress subdivision ticks falling on primary beats
                val nearestPrimaryIndex = (ratio * macroBeats).roundToLong()
                val nearestPrimaryRatio = nearestPrimaryIndex.toDouble() / macroBeats
                val isColliding = abs(ratio - nearestPrimaryRatio) < COLLISION_EPSILON

                if (!isColliding)
                {
                    val beatInMacro = (ratio * macroBeats).toInt().coerceIn(0, macroBeats - 1)
                    val primaryBeatIndex = beatInMacro % primary.beatCount
                    val isMuted = primary.isMuted(primaryBeatIndex)
                    val gain = if (isMuted) 0.0f else 1.0f
                    val subIndex = p % sub.pulses

                    events.add(
                        TimelineEvent(
                            positionRatio = ratio,
                            soundId = SoundId.CLICK_SUBDIVISION,
                            gain = gain,
                            metadata = EventMetadata(
                                beatIndex = primaryBeatIndex,
                                subIndex = subIndex,
                                isPrimaryBeat = false,
                                isDownbeat = false
                            )
                        )
                    )
                }
            }
        }

        // 3. Compile Counter-Meter (Polymeter, if present)
        val counter = config.counterMeter

        if (counter != null)
        {
            for (b in 0 until macroBeats)
            {
                val beatIndex = b % counter.beatCount
                val ratio = b.toDouble() / macroBeats
                val isDownbeat = (beatIndex == 0)
                val isAccented = counter.isAccented(beatIndex)
                val isMuted = counter.isMuted(beatIndex)

                val soundId = if (isAccented) counter.soundIdAccent else counter.soundIdPrimary
                val gain = if (isMuted) 0.0f else 1.0f

                events.add(
                    TimelineEvent(
                        positionRatio = ratio,
                        soundId = soundId,
                        gain = gain,
                        metadata = EventMetadata(
                            beatIndex = beatIndex,
                            subIndex = 0,
                            isPrimaryBeat = (beatIndex == 0),
                            isDownbeat = isDownbeat
                        )
                    )
                )
            }
        }

        // 4. Stable Sort Chronologically by positionRatio
        events.sortWith(compareBy<TimelineEvent> { it.positionRatio }
            .thenBy { !it.metadata.isPrimaryBeat })

        return TimelinePattern(
            nominalBeats = macroBeats,
            events = events
        )
    }
}
