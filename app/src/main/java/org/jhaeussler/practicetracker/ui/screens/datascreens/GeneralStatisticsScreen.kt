/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens.datascreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.viewmodels.statisticsData.StatisticsViewModel
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScrollableTable


@Composable
fun GeneralStatisticsScreen(
    navigateToAllTimeStatScreen: () -> Unit,
    navigateToCurrentWeekStatScreen: () -> Unit,
    navigateToWeeklyStatScreen: () -> Unit,
    navigateToExtraInfoScreen: () -> Unit,
    navigateToEntryListScreen: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.statsUiState.collectAsState()

    ScreenContainer(
        title = R.string.general_stats_screen_title
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
                        navigateToAllTimeStatScreen
                    ),
                    Pair(
                        listOf("Average h per Week", uiState.getAverageHoursPerWeek().toString() + " h"),
                        navigateToWeeklyStatScreen
                    ),
                    Pair(
                        listOf(
                            "h this Week (so far)",
                            uiState.getHoursPlayedThisWeek().toString() + " h"
                        ),
                        navigateToCurrentWeekStatScreen
                    ),

                    Pair(
                        listOf("Extra Info", "days played: ${uiState.getDaysPlayedTotal()} (${uiState.getDaysPlayedInPercent()} %)"),
                        navigateToExtraInfoScreen
                    ),
                )
            )
            Spacer(modifier = Modifier.height(35.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                PracticeAppButton(
                    onClick  = {
                        navigateToEntryListScreen()
                    },
                    text = R.string.show_entry_list_button,
                    fontSize = 21,
                    modifier = Modifier.fillMaxWidth(0.5f).height(60.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}