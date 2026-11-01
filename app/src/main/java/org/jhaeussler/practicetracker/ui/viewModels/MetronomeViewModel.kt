/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jhaeussler.practicetracker.drummachine.grid.MetronomeConfig
import org.jhaeussler.practicetracker.drummachine.grid.Subdivision
import org.jhaeussler.practicetracker.drummachine.service.DrumMachineService
import org.jhaeussler.practicetracker.drummachine.session.AppDrumMachineSession
import org.jhaeussler.practicetracker.drummachine.session.DrumMachineSession

class MetronomeViewModel(
    private val session: DrumMachineSession = AppDrumMachineSession
) : ViewModel() {

    val isMetronomeRunning: StateFlow<Boolean> = session.isRunning
    val bpm: StateFlow<Int> = session.bpm
    val metronomeConfig: StateFlow<MetronomeConfig> = session.metronomeConfig
    private val _manualWindowIndex = MutableStateFlow<Int?>(null)

    /**
     * Active primary beat index (0 until beatCount), or null when stopped.
     * Only updates on primary beats, preventing subdivisions from causing recompositions.
     */
    val activePrimaryBeat: StateFlow<Int?> = session.activePrimaryBeat

    /**
     * Active counter/polymeter beat index, or null if stopped or counter-meter is disabled.
     */
    val activeCounterBeat: StateFlow<Int?> = session.activeCounterBeat

    /**
     * True on downbeats (measure 0) for visual flash indicators.
     */
    val isDownbeatFlash: StateFlow<Boolean> = session.isDownbeatFlash

    // --- Transport & Service Actions ---

    /**
     * Starts or stops the foreground audio service.
     */
    fun toggleMetronome(context: Context) {
        DrumMachineService.toggle(context)
    }

    fun stopMetronome(context: Context) {
        DrumMachineService.stop(context)
    }


    // --- Tempo / BPM Actions ---

    fun setBpm(value: Int) {
        session.setBpm(value)
    }

    fun adjustBpm(delta: Int) {
        session.adjustBpm(delta)
    }

    fun tapTempo() {
        session.tapTempo()
    }


    // --- Beat Sequencer & Primary Meter Actions ---

    fun cyclePrimaryBeatState(beatIndex: Int) {
        session.cyclePrimaryBeatState(beatIndex)
    }

    fun setBeatCount(count: Int) {
        session.setBeatCount(count)
    }

    fun incrementBeatCount() {
        session.incrementBeatCount()
    }

    fun decrementBeatCount() {
        session.decrementBeatCount()
    }


    // --- Subdivision Actions ---

    fun setSubdivision(subdivision: Subdivision) {
        session.setSubdivision(subdivision)
    }


    // --- Counter Meter (Polymeter) Actions ---

    fun enableCounterMeter(beatCount: Int = 3) {
        session.enableCounterMeter(beatCount)
    }

    fun disableCounterMeter() {
        session.disableCounterMeter()
    }

    fun cycleCounterBeatState(beatIndex: Int) {
        session.cycleCounterBeatState(beatIndex)
    }

    /**
     * The actively displayed beat window index.
     * Automatically follows activePrimaryBeat during playback, or manual selection when stopped.
     */
    val currentWindowIndex: StateFlow<Int> = combine(
        session.activePrimaryBeat,
        session.metronomeConfig,
        _manualWindowIndex
    ) { activeBeat, config, manualIndex ->

        val windows = config.primaryMeter.getBeatWindows()
        if (windows.isEmpty()) return@combine 0

        // Auto-track playhead while running
        if (activeBeat != null)
        {
            val autoIndex = windows.indexOfFirst { activeBeat in it }
            if (autoIndex != -1) autoIndex else 0
        }
        else {
            // Manual browsing when stopped
            (manualIndex ?: 0).coerceIn(0, windows.size - 1)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun nextBeatWindow() {
        val windows = session.metronomeConfig.value.primaryMeter.getBeatWindows()
        val current = currentWindowIndex.value
        if (current < windows.size - 1) {
            _manualWindowIndex.value = current + 1
        }
    }

    fun previousBeatWindow() {
        val current = currentWindowIndex.value
        if (current > 0) {
            _manualWindowIndex.value = current - 1
        }
    }

    fun setBeatGrouping(grouping: List<Int>) {
        session.setPrimaryMeterGrouping(grouping)
    }
}
