package org.jhaeussler.practicetracker.metronomservice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MetronomeRepository
{
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _bpm = MutableStateFlow(120)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    private val _beats = MutableStateFlow(createInitialBeats(4))
    val beats: StateFlow<List<Beat>> = _beats.asStateFlow()
    private val _currentBeatInMeasure = MutableStateFlow(0)
    val currentBeatInMeasure: StateFlow<Int> = _currentBeatInMeasure.asStateFlow()

    const val MAX_SUBDIVISIONS = 8
    fun createInitialBeats(count: Int) : List<Beat> {
        return List(count.coerceIn(1, MAX_SUBDIVISIONS)) { index ->
            Beat(index, true, index == 0)
        }
    }
    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
    fun setBpm(newBpm: Int) {
        // Keep BPM within reasonable physical musical bounds
        _bpm.value = newBpm.coerceIn(30, 300)
    }

    fun updateCurrentBeat(beat: Int) {
        _currentBeatInMeasure.value = beat
    }

    fun updateBeatMeasure(newBeats: List<Beat>) {
        _beats.value = newBeats
    }

    fun setSubdivisions(context: Context, value: Int) {
        sendCommandToMetronome(context,
            MetronomeService.ACTION_SET_SUBDIVISION,
            value.coerceIn(1, MAX_SUBDIVISIONS)
        )
    }

    fun toggleBeat(context: Context, index: Int) {
        sendCommandToMetronome(
            context, MetronomeService.ACTION_TOGGLE_BEAT, index
        )
    }

    fun sendCommandToMetronome(context: Context, actionToSend: String, extraValue: Int? = null) {
        val intent = Intent(context, MetronomeService::class.java).apply {
            action = actionToSend
            extraValue?.let { putExtra(MetronomeService.EXTRA_VALUE, it) }
        }

        if (actionToSend == MetronomeService.ACTION_START_METRONOME ||
            actionToSend == MetronomeService.ACTION_TOGGLE_METRONOME) {
            // Only trigger foreground service contract when starting audio playback
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}