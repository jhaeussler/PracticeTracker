/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import org.jhaeussler.practicetracker.metronomservice.MetronomeService

class MetronomeViewModel(
) : ViewModel() {

    val isMetronomeRunning: StateFlow<Boolean> = MetronomeService.isRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val bpm: StateFlow<Int> = MetronomeService.bpm
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 120
        )
    val subdivisions : StateFlow<Int> = MetronomeService.subdivisions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4
        )
    val currentBeatInMeasure : StateFlow<Int> = MetronomeService.currentBeatInMeasure
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun startMetronomeService(context: Context) {
        val intent = Intent(context, MetronomeService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
    fun stopMetronomeService(context: Context) {
        val intent = Intent(context, MetronomeService::class.java)
        context.stopService(intent)
    }

    fun incrementBpm() {
        MetronomeService.setBpm(bpm.value + 1)
    }

    fun decrementBpm() {
        MetronomeService.setBpm(bpm.value - 1)
    }

    fun setBpm(value: Int) {
        MetronomeService.setBpm(value)
    }

    fun setSubdivisions(value: Int) {
        MetronomeService.setSubdivisions(value)
    }
}