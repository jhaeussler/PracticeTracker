/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import org.jhaeussler.practicetracker.metronomservice.Beat
import org.jhaeussler.practicetracker.metronomservice.MetronomeRepository
import org.jhaeussler.practicetracker.metronomservice.MetronomeService.Companion.ACTION_TOGGLE_METRONOME
import org.jhaeussler.practicetracker.sessiontimerservice.SessionTimerService

class MetronomeViewModel(
) : ViewModel() {
    private val metronomeRepository = MetronomeRepository
    val isMetronomeRunning: StateFlow<Boolean> = metronomeRepository.isRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val bpm: StateFlow<Int> = metronomeRepository.bpm
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 120
        )
    val beats : StateFlow<List<Beat>> = metronomeRepository.beats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf()
        )
    val currentBeatInMeasure : StateFlow<Int> = metronomeRepository.currentBeatInMeasure
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun toggleMetronome(context: Context) {
        metronomeRepository.sendCommandToMetronome(context, ACTION_TOGGLE_METRONOME)
    }

    fun sessionNotificationGranted(context: Context) {
        metronomeRepository.notificationGranted(context)
    }

    fun setBpm(value: Int) {
        metronomeRepository.setBpm(value)
    }

    fun setSubdivisions(context: Context, value: Int) {
        metronomeRepository.setSubdivisions(context, value)
    }

    fun toggleBeat(context: Context, index: Int) {
        metronomeRepository.toggleBeat(context, index)
    }
}