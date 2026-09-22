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
import org.jhaeussler.practicetracker.ui.viewmodels.statisticsData.StatisticsViewModel

@Composable
fun ExtraInfoScreen(
    viewModel: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.statsUiState.collectAsState()

    ScreenContainer(
        title = R.string.extra_info_screen_title
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
                        listOf("First Date: ", uiState.getFirstDateFormatted()),
                        null
                    ),
                    Pair(
                        listOf("Days since first Entry", uiState.getDaysPassedSinceFirstEntry().toString()),
                        null
                    ),
                    Pair(
                        listOf("Days Played On", "${uiState.getDaysPlayedTotal()} (${uiState.getDaysPlayedInPercent()} %)"),
                        null
                    ),
                    Pair(
                        listOf("Most Days in a Row", uiState.getLongestStreak().toString()),
                        null
                    ),
                    Pair(
                        listOf("Longest Break", uiState.getLongestAbsence().toString()),
                        null
                    )
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}