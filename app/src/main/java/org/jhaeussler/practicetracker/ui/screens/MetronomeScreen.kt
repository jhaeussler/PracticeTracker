/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.metronomservice.Beat
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.PracticeAppButtonRawString
import org.jhaeussler.practicetracker.ui.components.RoundButtonWithIcon
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
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
        BpmDisplayRow(
            bpm,
            { metronomeViewModel.decrementBpm() },
            { metronomeViewModel.incrementBpm() },
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

        val setBpmWrapper : (Int) -> Unit = { metronomeViewModel.setBpm(it) }

        SetFixedBpmValueButtonRow(
            listOf(
                Pair(30, setBpmWrapper),
                Pair(60, setBpmWrapper),
                Pair(90, setBpmWrapper)
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        SetFixedBpmValueButtonRow(
            listOf(
                Pair(120, setBpmWrapper),
                Pair(160, setBpmWrapper),
            )
        )
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun BpmDisplayRow(
    bpm: Int,
    decrementBpm: () -> Unit,
    incrementBpm: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButtonWithIcon(
            decrementBpm,
            "Decrease BPM",
            iconImage = Icons.Default.Remove
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(13.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(140.dp).padding(10.dp)
            ) {
                Text(
                    text = "$bpm",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = stringResource(R.string.bpm),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        RoundButtonWithIcon(
            incrementBpm,
            "Increase BPM",
            iconImage = Icons.Default.Add
        )
    }
}

@Composable
fun SubdivisionSection(
    beats: List<Beat>,
    onToggleBeat: (Int) -> Unit,
    currentBeatInMeasure: Int,
    decrementSubdivision: () -> Unit,
    addSubdivision: () -> Unit
) {
    val firstRow = beats.take(4)
    val secondRow = beats.drop(4)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SubdivisionButtonRow(
            firstRow,
            0,
            onToggleBeat,
            currentBeatInMeasure = currentBeatInMeasure
        )
        if (beats.size > 4) {
            Spacer(modifier = Modifier.height(20.dp))
            SubdivisionButtonRow(
                secondRow,
                4,
                onToggleBeat,
                currentBeatInMeasure = currentBeatInMeasure
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ChangeSubdivisionRow(
            decrementSubdivision,
            addSubdivision
        )
    }
}

@Composable
fun SubdivisionButtonRow(
    beats: List<Beat>,
    firstIndex: Int = 0,
    onToggleBeat: (Int) -> Unit,
    currentBeatInMeasure: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        beats.forEachIndexed { i, beat ->
            val realIndex = firstIndex + i

            RoundButtonWithIcon(
                { onToggleBeat(realIndex) },
                "${realIndex + 1}",
                text = "${realIndex + 1}",
                contentColor = if (beat.isEnabled) Color.White else Color.DarkGray,
                containerColor = if (beat.index == currentBeatInMeasure) {
                    if (beat.isEnabled) IconButtonDefaults.filledIconButtonColors().containerColor else Color.White
                }
                else {
                    if (beat.isEnabled) Color.DarkGray else Color.LightGray
                }
            )
        }
    }
}

@Composable
fun ChangeSubdivisionRow(
    decrementSubdivision: () -> Unit,
    addSubdivision: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButtonWithIcon(
            decrementSubdivision,
            "Decrease Subdivisions",
            iconImage = Icons.Default.Remove
        )

        RoundButtonWithIcon(
            addSubdivision,
            "Add Subdivisions",
            iconImage = Icons.Default.Add
        )
    }
}

@Composable
fun SetFixedBpmValueButtonRow(
    valuesToDisplay: List<Pair<Int, (Int) -> Unit>>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            10.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        valuesToDisplay.forEach { pair ->
            PracticeAppButtonRawString(
                onClick  = { pair.second(pair.first) },
                text = "${pair.first}",
                fontSize = 27,
                modifier = Modifier.width(100.dp).height(70.dp)
            )
        }
    }
}