/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.sessiontimerservice.SessionTimerService
import org.jhaeussler.practicetracker.ui.components.LocalNotificationRequester
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.components.metronome.BpmControlSection
import org.jhaeussler.practicetracker.ui.components.metronome.SubdivisionSection
import org.jhaeussler.practicetracker.ui.viewModels.MetronomeViewModel

@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current

    val isRunning by viewModel.isMetronomeRunning.collectAsStateWithLifecycle()
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val beats = viewModel.beats.collectAsStateWithLifecycle()
    val currentBeatInMeasure by viewModel.currentBeatInMeasure.collectAsStateWithLifecycle()
    val notificationRequester = LocalNotificationRequester.current

    ScreenContainer(
        title = R.string.metronome_screen_title
    ) {
        BpmControlSection(
            bpm = bpm,
            onBpmChange = { viewModel.setBpm(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        PracticeAppButton(
            onClick  = {
                if (!isRunning) {
                    notificationRequester.checkAndRequest {
                        viewModel.sessionNotificationGranted(context)
                    }
                }

                viewModel.toggleMetronome(context)
            },
            text = if (!isRunning) R.string.start else R.string.stop,
            fontSize = 27,
            modifier = Modifier.fillMaxWidth(0.7f).height(70.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        SubdivisionSection(
            beats = beats.value,
            onToggleBeat = { index ->  viewModel.toggleBeat(context, index) },
            currentBeatInMeasure = currentBeatInMeasure,
            decrementSubdivision = { viewModel.setSubdivisions(context, beats.value.size -1) },
            addSubdivision = { viewModel.setSubdivisions(context, beats.value.size +1) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
