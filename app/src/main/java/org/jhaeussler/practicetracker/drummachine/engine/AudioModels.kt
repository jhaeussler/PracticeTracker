package org.jhaeussler.practicetracker.drummachine.engine

enum class SoundId {
    CLICK_ACCENT,
    CLICK_BEAT,
    CLICK_SUBDIVISION,
    DRUM_KICK,
    DRUM_SNARE,
    DRUM_HIHAT_CLOSED,
    DRUM_HIHAT_OPEN,
    DRUM_CLAP
}

data class EventMetadata(
    val beatIndex: Int,
    val subIndex: Int = 0,
    val isPrimaryBeat: Boolean = true,
    val isDownbeat: Boolean = false
)

data class PlaybackEvent(
    val samplePosition: Long,    // Continuous 64-bit hardware playback sample
    val metadata: EventMetadata
)

data class TimelineEvent(
    val positionRatio: Double,   // 0.0 <= positionRatio < 1.0
    val soundId: SoundId,
    val gain: Float = 1.0f,
    val metadata: EventMetadata
)

/**
 * Normalized timeline anchor representing the boundaries [startRatio, endRatio) of a beat segment.
 * Pre-computed during pattern compilation to ensure zero allocations on the audio thread.
 */
data class BeatAnchor(
    val startRatio: Double,
    val endRatio: Double
)

data class TimelinePattern(
    val nominalBeats: Int = 4,
    val events: List<TimelineEvent> = emptyList(),
    val beatAnchors: Array<BeatAnchor> = computeBeatAnchors(nominalBeats, events)
) {
    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimelinePattern
        if (nominalBeats != other.nominalBeats) return false
        if (events != other.events) return false
        if (!beatAnchors.contentEquals(other.beatAnchors)) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = nominalBeats
        result = 31 * result + events.hashCode()
        result = 31 * result + beatAnchors.contentHashCode()

        return result
    }

    companion object {
        /**
         * Pure function executed once during pattern creation on worker/UI threads.
         * Extracts beat segment boundaries from primary beat positions, falling back
         * to uniform nominal slices when no primary events exist.
         */
        fun computeBeatAnchors(nominalBeats: Int, events: List<TimelineEvent>): Array<BeatAnchor> {
            val safeBeats = nominalBeats.coerceAtLeast(1)

            if (events.isEmpty())
            {
                return Array(safeBeats) { b ->
                    BeatAnchor(
                        startRatio = b.toDouble() / safeBeats,
                        endRatio = (b + 1).toDouble() / safeBeats
                    )
                }
            }

            val primaryRatios = ArrayList<Double>()

            for (i in events.indices)
            {
                val event = events[i]

                if (event.metadata.isPrimaryBeat)
                {
                    if (primaryRatios.isEmpty() ||
                        event.positionRatio > primaryRatios[primaryRatios.size - 1] + 0.00001
                    ) {
                        primaryRatios.add(event.positionRatio)
                    }
                }
            }

            if (primaryRatios.isEmpty())
            {
                return Array(safeBeats) { b ->
                    BeatAnchor(
                        startRatio = b.toDouble() / safeBeats,
                        endRatio = (b + 1).toDouble() / safeBeats
                    )
                }
            }

            if (primaryRatios[0] > 0.00001) {
                primaryRatios.add(0, 0.0)
            }

            return Array(primaryRatios.size) { i ->

                val startRatio = primaryRatios[i]

                val endRatio = if (i + 1 < primaryRatios.size) {
                    primaryRatios[i + 1]
                } else {
                    1.0
                }

                BeatAnchor(startRatio, endRatio)
            }
        }
    }
}