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
import org.jhaeussler.practicetracker.drummachine.grid.BeatUnit
import org.jhaeussler.practicetracker.drummachine.grid.MeterTrack
import org.jhaeussler.practicetracker.drummachine.grid.MetronomeConfig
import org.jhaeussler.practicetracker.drummachine.grid.Subdivision
import org.jhaeussler.practicetracker.drummachine.service.DrumMachineService
import org.jhaeussler.practicetracker.drummachine.session.AppDrumMachineSession
import org.jhaeussler.practicetracker.drummachine.session.DrumMachineSession
import kotlin.math.roundToInt

class MetronomeViewModel(
    private val session: DrumMachineSession = AppDrumMachineSession
) : ViewModel() {

    // --- Observable UI States ---

    val isMetronomeRunning: StateFlow<Boolean> = session.isRunning

    val bpm: StateFlow<Int> = session.bpm
    val metronomeConfig: StateFlow<MetronomeConfig> = session.metronomeConfig

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

    // --- Beat Window Navigation (Auto-tracking + Manual) ---

    private val _manualWindowIndex = MutableStateFlow<Int?>(null)

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
        if (windows.isEmpty()) return

        val current = currentWindowIndex.value
        // Wrap to 0 when reaching the end
        _manualWindowIndex.value = (current + 1) % windows.size
    }

    fun previousBeatWindow() {
        val windows = session.metronomeConfig.value.primaryMeter.getBeatWindows()
        if (windows.isEmpty()) return

        val current = currentWindowIndex.value
        // Wrap to last window when clicking previous from index 0
        _manualWindowIndex.value = if (current <= 0) windows.size - 1 else current - 1
    }

    // --- Live Timing Summary Helper ---

    /**
     * E.g.: "7/8 @ 120 BPM • ♪ = 250ms • 1 bar = 1.75s"
     */
    val timingSummary: StateFlow<String> = combine(
        session.bpm,
        session.metronomeConfig
    ) { bpm, config ->
        val meter = config.primaryMeter
        val unit = meter.beatUnit
        val tickMs = ((60_000.0 / bpm) * unit.quarterMultiplier).roundToInt()
        val barMs = tickMs * meter.beatCount

        val barSec = String.format(java.util.Locale.US,
            "%.2f", barMs / 1000.0)
        "${meter.beatCount}/${unit.denominator} @ $bpm BPM • ${unit.symbol} = ${tickMs}ms • 1 bar = ${barSec}s"
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000), "")


    // --- Transport & Service Actions ---

    /**
     * Starts or stops the foreground audio service.
     */
    fun toggleMetronome(context: Context) = DrumMachineService.toggle(context)
    fun stopMetronome(context: Context) = DrumMachineService.stop(context)


    // --- Tempo / BPM Actions ---

    fun setBpm(value: Int) = session.setBpm(value)
    fun adjustBpm(delta: Int) = session.adjustBpm(delta)
    fun tapTempo() = session.tapTempo()

    // --- Time Signature & Primary Meter Actions ---

    fun setBeatCount(count: Int) = session.setBeatCount(count)
    fun incrementBeatCount() = session.incrementBeatCount()
    fun decrementBeatCount() = session.decrementBeatCount()

    fun setBeatUnit(unit: BeatUnit) = session.setBeatUnit(unit)

    fun cyclePrimaryBeatState(beatIndex: Int) = session.cyclePrimaryBeatState(beatIndex)


    // --- Metric Grouping Helpers for Rhythm Tab ---

    fun setBeatGrouping(grouping: List<Int>) = session.setPrimaryMeterGrouping(grouping)

    fun resetGroupingToDefault() {
        val meter = session.metronomeConfig.value.primaryMeter
        setBeatGrouping(MeterTrack.defaultGroupingFor(meter.beatCount))
        session.resetToDefaultAccents()
    }

    fun resetToDefaultAccents() = session.resetToDefaultAccents()

    /**
     * Adjusts group at [groupIndex] by [delta] (+1 or -1) and balances the difference
     * on the last block (or block 0 if the last block is being adjusted).
     */
    fun adjustGroupSize(groupIndex: Int, delta: Int)
    {
        val meter = session.metronomeConfig.value.primaryMeter
        val groups = (if (meter.grouping.sum() == meter.beatCount) meter.grouping else MeterTrack.defaultGroupingFor(meter.beatCount)).toMutableList()

        // Must have at least 2 blocks to shift beats between them
        if (groups.size <= 1 || groupIndex !in groups.indices) return

        if (delta < 0) {
            // --- DECREMENT LOGIC ---
            if (groups[groupIndex] <= 0) return

            val lastIdx = groups.size - 1
            // Receiver: prefer last block (if target != last and last < 8), otherwise find next free block (< 8)
            val receiverIdx = if (groupIndex != lastIdx && groups[lastIdx] < 8) {
                lastIdx
            } else {
                groups.indices.firstOrNull { it != groupIndex && groups[it] < 8 }
            } ?: return // All other blocks are maxed out at 8

            groups[groupIndex]--
            groups[receiverIdx]++

            // If target block drops to 0, delete it
            if (groups[groupIndex] == 0) {
                groups.removeAt(groupIndex)
            }
            setBeatGrouping(groups)

        } else if (delta > 0) {
            // --- INCREMENT LOGIC ---
            // Target cannot exceed 8 beats
            if (groups[groupIndex] >= 8) return

            val lastIdx = groups.size - 1
            // Donor: prefer last block (even if size == 1, which will delete it)
            val donorIdx = if (groupIndex != lastIdx && groups[lastIdx] >= 1) {
                lastIdx
            } else {
                // If target is the last block, prefer a block with > 1, or any block with >= 1
                groups.indices.firstOrNull { it != groupIndex && groups[it] > 1 }
                    ?: groups.indices.firstOrNull { it != groupIndex && groups[it] >= 1 }
            } ?: return // No block has beats to donate

            groups[groupIndex]++
            groups[donorIdx]--

            // If donor drops to 0, delete it
            if (groups[donorIdx] == 0) {
                groups.removeAt(donorIdx)
            }
            setBeatGrouping(groups)
        }
    }

    /**
     * Splits group at [groupIndex] in half (e.g., 6 -> 3 + 3).
     */
    fun splitGroup(groupIndex: Int)
    {
        val meter = session.metronomeConfig.value.primaryMeter
        val groups =
            (if (meter.grouping.sum() == meter.beatCount) meter.grouping
            else MeterTrack.defaultGroupingFor(meter.beatCount)).toMutableList()

        if (groupIndex !in groups.indices) return

        val size = groups[groupIndex]
        if (size >= 2) {
            val left = (size + 1) / 2
            val right = size / 2
            groups.removeAt(groupIndex)
            groups.add(groupIndex, right)
            groups.add(groupIndex, left)
            setBeatGrouping(groups)
        }
    }

    /**
     * Merges group at [groupIndex] with the previous group (groupIndex - 1).
     */
    fun mergeGroupWithPrevious(groupIndex: Int)
    {
        val meter = session.metronomeConfig.value.primaryMeter
        val groups =
            (if (meter.grouping.sum() == meter.beatCount) meter.grouping
            else MeterTrack.defaultGroupingFor(meter.beatCount)).toMutableList()

        if (groupIndex in 1 until groups.size)
        {
            val mergedSize = groups[groupIndex - 1] + groups[groupIndex]

            if (mergedSize <= 8) {
                groups.removeAt(groupIndex)
                groups[groupIndex - 1] = mergedSize
            }
            else {
                groups[groupIndex] -= (8 - groups[groupIndex - 1])
                groups[groupIndex - 1] = 8
            }

            setBeatGrouping(groups)
        }
    }

    fun applyGroupingAccents() = session.applyGroupingAccents()


    // --- Subdivision & Polymeter Actions ---

    fun setSubdivision(subdivision: Subdivision) = session.setSubdivision(subdivision)
    fun enableCounterMeter(beatCount: Int = 3) = session.enableCounterMeter(beatCount)
    fun disableCounterMeter() = session.disableCounterMeter()
    fun cycleCounterBeatState(beatIndex: Int) = session.cycleCounterBeatState(beatIndex)
}
