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
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.components.metronome.BpmControlSection
import org.jhaeussler.practicetracker.ui.components.metronome.SubdivisionSection
import org.jhaeussler.practicetracker.ui.viewModels.MetronomeViewModel

@Composable
fun MetronomeScreen(
    metronomeViewModel: MetronomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current

    val isRunning by metronomeViewModel.isMetronomeRunning.collectAsStateWithLifecycle()
    val bpm by metronomeViewModel.bpm.collectAsStateWithLifecycle()
    val beats = metronomeViewModel.beats.collectAsStateWithLifecycle()
    val currentBeatInMeasure by metronomeViewModel.currentBeatInMeasure.collectAsStateWithLifecycle()


    ScreenContainer(
        title = R.string.metronome_screen_title
    ) {
        BpmControlSection(
            bpm = bpm,
            onBpmChange = { metronomeViewModel.setBpm(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        PracticeAppButton(
            onClick  = { metronomeViewModel.toggleMetronome(context) },
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
            onToggleBeat = { index ->  metronomeViewModel.toggleBeat(context, index) },
            currentBeatInMeasure = currentBeatInMeasure,
            decrementSubdivision = { metronomeViewModel.setSubdivisions(context, beats.value.size -1) },
            addSubdivision = { metronomeViewModel.setSubdivisions(context, beats.value.size +1) }
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
