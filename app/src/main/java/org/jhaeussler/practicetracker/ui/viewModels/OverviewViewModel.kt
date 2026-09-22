/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import org.jhaeussler.practicetracker.sessiontimerservice.SessionTimerService
import org.jhaeussler.practicetracker.utils.HoursAndMins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class OverviewViewModel (
    private val practiceTimeRepository: PracticeTimeRepository,
    application: Application
) : AndroidViewModel(application)
{
    val overviewUiState: StateFlow<List<PracticeTime>> =
        practiceTimeRepository.getAllPracticeTimesStream()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = listOf()
            )

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }

    val timerState = SessionTimerService.timerState
    val elapsedTimeSec = SessionTimerService.elapsedTimeSec
    val sessionStartDate = SessionTimerService.sessionStartDate

    // DialogFlags

    private val _showConfirmSessionEndDiag = MutableStateFlow(false)
    val showConfirmSessionEndDiag: StateFlow<Boolean> = _showConfirmSessionEndDiag

    private val _showSessionEndErrorDiag = MutableStateFlow(false)
    val showSessionEndErrorDiag: StateFlow<Boolean> = _showSessionEndErrorDiag

    private val _showCancelSessionDiag = MutableStateFlow(false)
    val showCancelSessionDiag: StateFlow<Boolean> = _showCancelSessionDiag

    // public methods to be called by composable

    fun resetDialogFlags() {
        _showConfirmSessionEndDiag.value = false
        _showSessionEndErrorDiag.value = false
        _showCancelSessionDiag.value = false
    }

    fun requestTimerService(requestPermission: (String) -> Unit)
    {
        if (ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS)
        }

        toggleTimer()
    }

    fun toggleTimer() {
        when (timerState.value) {
            SessionTimerService.TimerState.RUNNING -> SessionTimerService.pauseTimer(getApplication())
            else -> SessionTimerService.startTimer(getApplication())
        }
    }

    fun requestCancelSession() {
        _showCancelSessionDiag.value = true
    }

    fun requestEndSession() {
        val sessionTime = HoursAndMins.fromSeconds(elapsedTimeSec.value)

        if (sessionTime.roundedToNextHalf() < 1.0) {
            _showSessionEndErrorDiag.value = true
            return
        }

        _showConfirmSessionEndDiag.value = true
    }

    suspend fun endSession()
    {
        val sessionTime = HoursAndMins.fromSeconds(elapsedTimeSec.value)
        val practiceDate: LocalDate = sessionStartDate.value ?: LocalDate.now()

        practiceTimeRepository.accumulatePracticeTime(
            PracticeTime(
                0,
                sessionTime.roundedToNextHalf(),
                practiceDate
            )
        )

        SessionTimerService.resetTimer(getApplication())
    }

    fun resetTimer() {
        SessionTimerService.resetTimer(getApplication())
    }

    fun getSessionTime() : Double {
        return HoursAndMins.fromSeconds(elapsedTimeSec.value).roundedToNextHalf()
    }
}