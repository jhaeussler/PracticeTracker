/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.viewModels.OverviewViewModel
import org.jhaeussler.practicetracker.utils.secondsToNiceString
import kotlinx.coroutines.launch
import org.jhaeussler.practicetracker.sessiontimerservice.SessionTimerService
import org.jhaeussler.practicetracker.ui.components.LocalNotificationRequester
import kotlin.math.floor

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onStatisticsBtnClicked: () -> Unit,
    onAddTimeClicked: () -> Unit,
    onToMetronomeScreenClicked: () -> Unit,
) {
    val timeUiState by viewModel.overviewUiState.collectAsState()
    val timeEntries: List<PracticeTime> = timeUiState
    val totalHours: Double = timeEntries.sumOf { it.value }

    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val currentPracticeTime by viewModel.elapsedTimeSec.collectAsState()

    val showCloseSessionDiag by viewModel.showConfirmSessionEndDiag.collectAsState()
    val showSessionEndErrorDiag by viewModel.showSessionEndErrorDiag.collectAsState()
    val showCancelSessionDiag by viewModel.showCancelSessionDiag.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val notificationRequester = LocalNotificationRequester.current

    val okBtnColor = Color(0.2f, 0.8f, 0.5f)

    ScreenContainer(
        title = R.string.greeting
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            PracticeAppButton(
                onClick  = {
                    if (timerState == SessionTimerService.TimerState.STOPPED) {
                        notificationRequester.checkAndRequest {
                            viewModel.sessionNotificationGranted()
                        }
                    }
                    viewModel.toggleTimer()
                },
                text = when (timerState) {
                    SessionTimerService.TimerState.RUNNING -> R.string.practice_session_running
                    SessionTimerService.TimerState.PAUSED -> R.string.resume_practice_session
                    else -> R.string.start_practice_session
                },
                fontSize = 27,
                modifier = Modifier.fillMaxWidth(0.7f).height(70.dp)
            )
        }
        if (timerState != SessionTimerService.TimerState.STOPPED)
        {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                thickness = 2.dp,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth(0.7f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .background(
                        Color(0.8f, 0.4f, 0.7f, 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = "Current Session:\n${secondsToNiceString(currentPracticeTime)}",
                    fontSize = 37.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp).padding(horizontal = 20.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row (
                horizontalArrangement = Arrangement.spacedBy(
                    30.dp, alignment = Alignment.CenterHorizontally
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)
            ) {
                PracticeAppButton(
                    onClick  = { viewModel.requestCancelSession() },
                    text = R.string.reset_session_btn_txt,
                    modifier = Modifier.weight(0.5f)
                )
                PracticeAppButton(
                    onClick  = { viewModel.requestEndSession() },
                    text = R.string.close_session_btn_text,
                    modifier = Modifier.weight(0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .background(
                    Color(0.6f, 0.8f, 0.5f, 0.8f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Text(
                text = "Total: " +
                        "${
                            if((totalHours % floor(totalHours)) == 0.0 ||
                                totalHours == 0.0) totalHours.toInt()
                            else totalHours
                        } h",
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp).padding(horizontal = 20.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        PracticeAppButton(
            onClick  = onToMetronomeScreenClicked,
            text = R.string.to_metronome_screen,
            fontSize = 21,
            modifier = Modifier.height(75.dp).fillMaxWidth(0.5f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row (
            horizontalArrangement = Arrangement.spacedBy(
                30.dp, alignment = Alignment.CenterHorizontally
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)
        ) {
            PracticeAppButton(
                onClick  = onStatisticsBtnClicked,
                text = R.string.show_stats_btn_txt,
                modifier = Modifier.weight(0.5f)
            )
            PracticeAppButton(
                onClick  = onAddTimeClicked,
                text = R.string.add_time_btn_txt,
                modifier = Modifier.weight(0.5f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
    when {
        showCloseSessionDiag -> {
            val sessionTime: Double = viewModel.getSessionTime()

            AlertDialog(
                title = {
                    Text(text = stringResource(R.string.close_session_btn_text))
                },
                text = {
                    Text(text = stringResource(
                        R.string.close_session_diag_text,
                        sessionTime)
                    )
                },
                onDismissRequest = {
                    viewModel.resetDialogFlags()
                },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors()
                            .copy(contentColor = okBtnColor),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.endSession()
                            }
                            viewModel.resetDialogFlags()
                        }
                    ) {
                        Text(stringResource(R.string.confirm_btn_txt))
                    }
                },
                dismissButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors()
                            .copy(contentColor = Color.Red),
                        onClick = {
                            viewModel.resetDialogFlags()
                        }
                    ) {
                        Text(stringResource(R.string.cancel_btn_txt))
                    }
                }
            )
        }
    }
    when {
        showSessionEndErrorDiag -> {
            AlertDialog(
                //icon = { Icon(icon, contentDescription = "Example Icon") },
                title = {
                    Text(text = stringResource(R.string.session_end_error_title))
                },
                text = {
                    Text(text = stringResource(R.string.session_end_error_text))
                },
                onDismissRequest = {
                    viewModel.resetDialogFlags()
                },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors()
                            .copy(contentColor = okBtnColor),
                        onClick = { viewModel.resetDialogFlags() }
                    ) {
                        Text(stringResource(R.string.confirm_btn_txt))
                    }
                }
            )
        }
    }
    when {
        showCancelSessionDiag -> {
            AlertDialog(
                //icon = { Icon(icon, contentDescription = "Example Icon") },
                title = {
                    Text(text = stringResource(R.string.cancel_session_diag_title))
                },
                text = {
                    Text(text = stringResource(R.string.cancel_session_diag_text))
                },
                onDismissRequest = {
                    viewModel.resetDialogFlags()
                },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors()
                            .copy(contentColor = okBtnColor),
                        onClick = {
                            viewModel.resetTimer()
                            viewModel.resetDialogFlags()
                        }
                    ) {
                        Text(stringResource(R.string.confirm_btn_txt))
                    }
                },
                dismissButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors()
                            .copy(contentColor = Color.Red),
                        onClick = {
                            viewModel.resetDialogFlags()
                        }
                    ) {
                        Text(stringResource(R.string.cancel_btn_txt))
                    }
                }
            )
        }
    }
}