package org.jhaeussler.practicetracker.audioengine

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

data class TimelineEvent(
    val positionRatio: Double,   // 0.0 <= positionRatio < 1.0
    val soundId: SoundId,
    val gain: Float = 1.0f,
    val metadata: EventMetadata
)

data class TimelinePattern(
    val nominalBeats: Int = 4,   // Reference beat count for BPM calculation
    val events: List<TimelineEvent> // Strictly ordered by positionRatio ascending
)

data class PlaybackEvent(
    val samplePosition: Long,    // Continuous 64-bit hardware playback sample
    val metadata: EventMetadata
)

class SoundBank(
    private val sampleMap: Map<SoundId, ShortArray> = emptyMap()
) {
    private val soundTable = Array<ShortArray?>(SoundId.entries.size) { index ->
        sampleMap[SoundId.entries[index]]
    }

    fun get(soundId: SoundId): ShortArray? = soundTable[soundId.ordinal]

    fun copyWith(soundId: SoundId, samples: ShortArray): SoundBank {
        val updated = sampleMap.toMutableMap()
        updated[soundId] = samples
        return SoundBank(updated)
    }
}