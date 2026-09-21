/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels.statisticsData

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatisticsViewModel(
    private val practiceTimeRepository: PracticeTimeRepository
) : ViewModel()
{
    val statsUiState: StateFlow<Analyzer> =
        practiceTimeRepository.getAllPracticeTimesStream().map {
            Analyzer(
                dbEntries = it,
                currentDate = LocalDate.now()
            )
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Companion.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = Analyzer(
                    dbEntries = listOf(),
                    currentDate = LocalDate.now()
                )
            )

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}