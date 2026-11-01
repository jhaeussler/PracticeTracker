package org.jhaeussler.practicetracker.drummachine.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import org.jhaeussler.practicetracker.drummachine.grid.*
import org.jhaeussler.practicetracker.drummachine.engine.*
import kotlin.math.roundToInt

enum class RhythmMode {
    METRONOME,
    DRUM_MACHINE
}

open class DrumMachineSession(
    private val sessionScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 450
        const val MIN_BEAT_COUNT = 1
        const val MAX_BEAT_COUNT = 64
        private const val TAP_TEMPO_TIMEOUT_MS = 2000L
    }

    private val _mode = MutableStateFlow(RhythmMode.METRONOME)
    val mode: StateFlow<RhythmMode> = _mode.asStateFlow()

    private val _bpm = MutableStateFlow(120)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _metronomeConfig = MutableStateFlow(MetronomeConfig(primaryMeter = MeterTrack(4)))
    val metronomeConfig: StateFlow<MetronomeConfig> = _metronomeConfig.asStateFlow()

    private val _activePattern = MutableStateFlow(
        GridPatternGenerator.createPattern(_metronomeConfig.value)
    )
    val activePattern: StateFlow<TimelinePattern> = _activePattern.asStateFlow()

    private val _currentPlaybackEvent = MutableStateFlow<PlaybackEvent?>(null)
    val currentPlaybackEvent: StateFlow<PlaybackEvent?> = _currentPlaybackEvent.asStateFlow()

    // --- Derived UI Visual StateFlows ---

    /**
     * The actively playing primary beat index (0 .. beatCount-1), or null if stopped.
     * Filtered with distinctUntilChanged() to prevent subdivisions from triggering recompositions.
     */
    val activePrimaryBeat: StateFlow<Int?> = _currentPlaybackEvent
        .filter { it == null || (it.metadata.trackIndex == 0 && it.metadata.isPrimaryBeat) }
        .map { it?.metadata?.beatIndex }
        .distinctUntilChanged()
        .stateIn(sessionScope, SharingStarted.Eagerly, null)

    /**
     * The actively playing counter/polymeter beat index, or null if stopped or no counter meter.
     */
    val activeCounterBeat: StateFlow<Int?> = _currentPlaybackEvent
        .filter { it == null || it.metadata.trackIndex == 1 }
        .map { it?.metadata?.beatIndex }
        .distinctUntilChanged()
        .stateIn(sessionScope, SharingStarted.Eagerly, null)

    /**
     * Pulses true on downbeats (measure starts) to drive visual flash animations.
     */
    val isDownbeatFlash: StateFlow<Boolean> = _currentPlaybackEvent
        .map { it?.metadata?.isDownbeat == true }
        .stateIn(sessionScope, SharingStarted.Eagerly, false)

    // --- Tap Tempo State ---
    private val tapTimestamps = mutableListOf<Long>()
    private var lastTapTime = 0L

    // --- Global Transport & Tempo Mutators ---

    fun setMode(newMode: RhythmMode) {
        _mode.value = newMode
        recompilePattern()
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    fun adjustBpm(delta: Int) {
        setBpm(_bpm.value + delta)
    }

    fun tapTempo()
    {
        val now = System.currentTimeMillis()

        if (now - lastTapTime > TAP_TEMPO_TIMEOUT_MS) {
            tapTimestamps.clear()
        }

        lastTapTime = now
        tapTimestamps.add(now)

        if (tapTimestamps.size > 3) {
            tapTimestamps.removeAt(0)
        }

        if (tapTimestamps.size >= 2)
        {
            val intervals =
                (1 until tapTimestamps.size).map { i ->
                tapTimestamps[i] - tapTimestamps[i - 1]
            }

            val avgIntervalMs = intervals.average()

            if (avgIntervalMs > 0) {
                setBpm((60_000.0 / avgIntervalMs).roundToInt())
            }
        }
    }

    fun setRunning(running: Boolean)
    {
        _isRunning.value = running

        if (!running) {
            _currentPlaybackEvent.value = null
        }
    }

    fun updatePlaybackEvent(event: PlaybackEvent?) {
        _currentPlaybackEvent.value = event
    }

    // --- Metronome Domain Mutators ---

    fun setMetronomeConfig(config: MetronomeConfig) {
        _metronomeConfig.value = config
        recompilePattern()
    }

    fun setBeatCount(count: Int)
    {
        val safeCount = count.coerceIn(MIN_BEAT_COUNT, MAX_BEAT_COUNT)
        val updatedMeter = _metronomeConfig.value.primaryMeter.copy(
            beatCount = safeCount,
            grouping = MeterTrack.defaultGroupingFor(safeCount)
        )

        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun incrementBeatCount() = setBeatCount(_metronomeConfig.value.primaryMeter.beatCount + 1)
    fun decrementBeatCount() = setBeatCount(_metronomeConfig.value.primaryMeter.beatCount - 1)

    fun cyclePrimaryBeatState(beatIndex: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.cycleBeatState(beatIndex)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun cycleCounterBeatState(beatIndex: Int) {
        val counter = _metronomeConfig.value.counterMeter ?: return
        val updatedMeter = counter.cycleBeatState(beatIndex)
        setMetronomeConfig(_metronomeConfig.value.copy(counterMeter = updatedMeter))
    }

    fun toggleBeatAccent(index: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.toggleAccent(index)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun toggleBeatMute(index: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.toggleMute(index)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun setCounterMeter(counterMeter: MeterTrack?) {
        setMetronomeConfig(_metronomeConfig.value.copy(counterMeter = counterMeter))
    }

    fun setSubdivision(subdivision: Subdivision) {
        setMetronomeConfig(_metronomeConfig.value.copy(subdivision = subdivision))
    }

    fun enableCounterMeter(beatCount: Int = 3) {
        val counter = MeterTrack(
            beatCount = beatCount.coerceIn(MIN_BEAT_COUNT, MAX_BEAT_COUNT),
            accentedBeats = setOf(0),
            soundIdPrimary = SoundId.CLICK_SUBDIVISION, // Distinct sound for counter meter
            soundIdAccent = SoundId.CLICK_ACCENT
        )
        setMetronomeConfig(_metronomeConfig.value.copy(counterMeter = counter))
    }

    fun disableCounterMeter() {
        setMetronomeConfig(_metronomeConfig.value.copy(counterMeter = null))
    }

    private fun recompilePattern() {
        when (_mode.value) {
            RhythmMode.METRONOME -> {
                _activePattern.value = GridPatternGenerator.createPattern(_metronomeConfig.value)
            }
            RhythmMode.DRUM_MACHINE -> {
                // Reserved for drum machine patterns
            }
        }
    }

    fun setPrimaryMeterGrouping(newGrouping: List<Int>) {
        // Only apply if the custom grouping matches the total beats
        if (newGrouping.sum() == _metronomeConfig.value.primaryMeter.beatCount) {
            val updatedMeter = _metronomeConfig.value.primaryMeter.copy(grouping = newGrouping)
            setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
        }
    }
}

/**
 * Default singleton instance shared across the Android application.
 */
object AppDrumMachineSession : DrumMachineSession()