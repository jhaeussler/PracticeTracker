/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens.datascreens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.components.ScrollableTable
import org.jhaeussler.practicetracker.ui.viewModels.statisticsData.StatisticsViewModel

@Composable
fun AllTimeDataScreen(
    viewModel: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.statsUiState.collectAsState()

    ScreenContainer(
        title = R.string.all_time_stats_screen_title
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScrollableTable(
                modifier = Modifier.fillMaxWidth(),
                headers = listOf("Description", "Value(s)"),
                cellWith = 150,
                rowHeight = 80,
                visibleRowCount = 6,
                data = listOf(
                    Pair(
                        listOf("Total h", uiState.getAllHoursPlayed().toString() + " h"),
                        null
                    ),
                    Pair(
                        listOf("Avrg. h/d",
                            uiState.getAverageHoursPerDay().toString() + " h"
                        ),
                        null
                    ),
                    Pair(
                        listOf("Avrg. h / Practice Day",
                            uiState.getAverageHoursOnPlayDates().toString() + " h"
                        ),
                        null
                    ),
                    Pair(
                        listOf("Best Day(s) Ever", uiState.getBestDays()),
                        null
                    ),
                    Pair(
                        listOf("Today", uiState.getPracticeHoursToday().toString() + " h"),
                        null
                    ),
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}