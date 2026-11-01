/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.ui.components.LocalNotificationRequester
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.components.metronome.BeatRibbon
import org.jhaeussler.practicetracker.ui.components.metronome.BpmControlSection
import org.jhaeussler.practicetracker.ui.viewModels.MetronomeViewModel
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.metronome.TabTempoSection

enum class MetronomeTab(val title: Int) {
    BPM(R.string.bpm_tab_name),
    TAB_TEMPO(R.string.tap_tempo_tab_name),
    RHYTHM(R.string.rythm_tab_name),
    POLYMETER(R.string.polymeter_tab_name)
}


@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val notificationRequester = LocalNotificationRequester.current

    val isRunning by viewModel.isMetronomeRunning.collectAsStateWithLifecycle()
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val config by viewModel.metronomeConfig.collectAsStateWithLifecycle()
    val activeBeat by viewModel.activePrimaryBeat.collectAsStateWithLifecycle()

    val currentWindowIndex by viewModel.currentWindowIndex.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MetronomeTab.BPM) }

    ScreenContainer(
        title = R.string.metronome_screen_title
    ) {
        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        PracticeAppButton(
            onClick = {
                if (!isRunning) {
                    notificationRequester.checkAndRequest {
                        // Notification granted callback
                    }
                }
                viewModel.toggleMetronome(context)
            },
            text = if (!isRunning) R.string.start else R.string.stop,
            fontSize = 24,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(64.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        BeatRibbon(
            primaryMeter = config.primaryMeter,
            activeBeatIndex = activeBeat,
            currentWindowIndex = currentWindowIndex,
            onToggleBeat = { beatIndex -> viewModel.cyclePrimaryBeatState(beatIndex) },
            onPreviousWindow = { viewModel.previousBeatWindow() },
            onNextWindow = { viewModel.nextBeatWindow() },
            modifier = Modifier.padding(vertical = 4.dp)
        )

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            MetronomeTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = MetronomeTab.entries.size
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        inactiveContentColor = Color.Black
                    ),
                    label = {
                        Text(
                            text = stringResource(tab.title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "MetronomeTabTransition"
        ) { tab ->
            when (tab) {
                MetronomeTab.BPM -> {
                    BpmControlSection(
                        bpm = bpm,
                        onBpmChange = { viewModel.setBpm(it) },
                        onAdjustBpm = { viewModel.adjustBpm(it) },
                    )
                }

                MetronomeTab.TAB_TEMPO -> {
                    TabTempoSection(
                        bpm = bpm,
                        onTapTempo = { viewModel.tapTempo() }
                    )
                }

                MetronomeTab.RHYTHM -> {
                    // Placeholder for Step 3: Beat Sequencer + Subdivision Chips
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Rhythm & Subdivisions (Next Step)")
                    }
                }

                MetronomeTab.POLYMETER -> {
                    // Placeholder for Step 4: Polymeter Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Polymeter Track (Following Step)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}