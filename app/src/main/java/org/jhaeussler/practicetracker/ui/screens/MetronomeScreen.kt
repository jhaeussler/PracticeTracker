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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
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
    val currentBeatInMeasure by metronomeViewModel.currentBeatInMeasure.collectAsStateWithLifecycle()
    val subdivisions by metronomeViewModel.subdivisions.collectAsStateWithLifecycle()

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
            currentBeatInMeasure = currentBeatInMeasure,
            subdivisions = subdivisions
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
                    metronomeViewModel.startMetronomeService(context)
                }
                else {
                    metronomeViewModel.stopMetronomeService(context)
                }
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
    }
}

@Composable
fun SubdivisionButtonRow(
    buttonsInRow: Int,
    firstIndex: Int = 0,
    currentBeatInMeasure: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until buttonsInRow)
        {
            val realIndex = firstIndex + i

            RoundButtonWithIcon(
                {},
                "${realIndex + 1}",
                text = "${realIndex + 1}",
                colors = if (currentBeatInMeasure == realIndex) {
                    IconButtonDefaults.filledIconButtonColors()
                }
                else {
                    IconButtonColors(
                        contentColor = Color.White,
                        containerColor = Color.DarkGray,
                        disabledContentColor = Color.DarkGray,
                        disabledContainerColor = Color.LightGray
                    )
                }
            )
        }
    }
}

@Composable
fun SubdivisionSection(
    currentBeatInMeasure: Int,
    subdivisions: Int
) {
    SubdivisionButtonRow(
        4,
        currentBeatInMeasure = currentBeatInMeasure
    )
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