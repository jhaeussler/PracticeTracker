package org.jhaeussler.practicetracker.drummachine.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jhaeussler.practicetracker.drummachine.engine.PlaybackEvent
import org.jhaeussler.practicetracker.drummachine.engine.TimelinePattern
import org.jhaeussler.practicetracker.drummachine.grid.GridPatternGenerator
import org.jhaeussler.practicetracker.drummachine.grid.MeterTrack
import org.jhaeussler.practicetracker.drummachine.grid.MetronomeConfig
import org.jhaeussler.practicetracker.drummachine.grid.Subdivision

enum class RhythmMode {
    METRONOME,
    DRUM_MACHINE
}

open class DrumMachineSession {

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

    // --- Global Transport & Tempo Mutators ---

    fun setMode(newMode: RhythmMode) {
        _mode.value = newMode
        recompilePattern()
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun updatePlaybackEvent(event: PlaybackEvent?) {
        _currentPlaybackEvent.value = event
    }

    // --- Metronome Domain Mutators ---

    fun setMetronomeConfig(config: MetronomeConfig) {
        _metronomeConfig.value = config
        recompilePattern()
    }

    fun setBeatCount(count: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.copy(beatCount = count)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun toggleBeatAccent(index: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.toggleAccent(index)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun toggleBeatMute(index: Int) {
        val updatedMeter = _metronomeConfig.value.primaryMeter.toggleMute(index)
        setMetronomeConfig(_metronomeConfig.value.copy(primaryMeter = updatedMeter))
    }

    fun setSubdivision(subdivision: Subdivision) {
        setMetronomeConfig(_metronomeConfig.value.copy(subdivision = subdivision))
    }

    fun setCounterMeter(counterMeter: MeterTrack?) {
        setMetronomeConfig(_metronomeConfig.value.copy(counterMeter = counterMeter))
    }

    private fun recompilePattern() {
        when (_mode.value) {
            RhythmMode.METRONOME -> {
                _activePattern.value = GridPatternGenerator.createPattern(_metronomeConfig.value)
            }
            RhythmMode.DRUM_MACHINE -> {
                // Reserved for Phase 5: FreeformPatternGenerator
            }
        }
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
    }
}

/**
 * Default singleton instance shared across the Android application.
 */
object AppDrumMachineSession : DrumMachineSession()