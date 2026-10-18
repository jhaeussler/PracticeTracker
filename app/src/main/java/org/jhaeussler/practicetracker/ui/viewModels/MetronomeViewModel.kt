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
import org.jhaeussler.practicetracker.drummachine.session.AppDrumMachineSession

class MetronomeViewModel(
) : ViewModel() {

    private val session = AppDrumMachineSession
    val isMetronomeRunning: StateFlow<Boolean> = session.isRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val bpm: StateFlow<Int> = session.bpm
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 120
        )
    fun toggleMetronome(context: Context) {

    }

    //fun sessionNotificationGranted(context: Context) {
    //    session.notificationGranted(context)
    //}

    fun setBpm(value: Int) {
        session.setBpm(value)
    }
}