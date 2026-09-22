/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import org.jhaeussler.practicetracker.timerservice.PracticeTimerService
import org.jhaeussler.practicetracker.utils.HoursAndMins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.lang.ref.WeakReference
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

    // Timer service binding

    private var timerServiceWeakRef: WeakReference<PracticeTimerService>? = null
    val timerService: PracticeTimerService? get() = timerServiceWeakRef?.get()

    private var isServiceBound = false
    private var serviceConnection: ServiceConnection = object : ServiceConnection
    {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?)
        {
            val binder = service as PracticeTimerService.TimerBinder
            timerServiceWeakRef = WeakReference(binder.getService())
            isServiceBound = true

            timerService?.setTimerCallback(object : PracticeTimerService.TimerCallback {
                override fun onTimerTick(elapsedTime: Long) {
                    _elapsedTime.value = elapsedTime // Update StateFlow
                    updateTimerState()
                }
            })
            updateTimerState()
        }

        override fun onServiceDisconnected(name: ComponentName?)
        {
            timerService?.setTimerCallback(null) // Remove callback
            timerServiceWeakRef = null
            isServiceBound = false
        }
    }

    init {
        bindToTimerService()
    }

    private fun bindToTimerService()
    {
        if (isServiceBound) return

        val serviceStartIntent = Intent(getApplication(), PracticeTimerService::class.java)
        getApplication<Application>().startService(serviceStartIntent)

        val bindToServiceIntent = Intent(getApplication(), PracticeTimerService::class.java)
        getApplication<Application>().bindService(
            bindToServiceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        if (isServiceBound) {
            timerService?.setTimerCallback(null)
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
        timerServiceWeakRef?.clear()
        timerServiceWeakRef = null
    }

    // State variables for tracking timer state

    private val _isRunning = MutableStateFlow(false) // Tracks if the timer is running
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isPaused = MutableStateFlow(false) // Tracks if the timer is paused
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _elapsedTime = MutableStateFlow(0L) // Tracks elapsed time
    val elapsedTime: StateFlow<Long> = _elapsedTime

    private var sessionTime: HoursAndMins? = null

    private fun updateTimerState() {
        timerService?.let {
            _isRunning.value = it.isRunning()
            _isPaused.value = it.isPaused()
        }
    }

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

    fun requestTimerService(requestPermission: (String) -> Unit) {
        if(timerService == null)
            Log.e("OverviewViewModel", "Timer Service not bound. Cannot start timer.")

        if(ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS)
        }

        toggleTimer()
    }

    fun toggleTimer() {
        timerService?.let {
            if (it.isRunning()) {
                it.pauseTimer()
            }
            else {
                it.startOrResumeTimer()
            }

            updateTimerState()
        }
    }

    fun requestCancelSession() {
        _showCancelSessionDiag.value = true
    }

    fun resetTimer() {
        timerService?.resetTimer()
    }

    fun requestEndSession() {
        sessionTime = HoursAndMins.fromSeconds(elapsedTime.value)

        if((sessionTime?.roundedToNextHalf() ?: 0.0) < 1.0) {
            _showSessionEndErrorDiag.value = true
            return
        }

        _showConfirmSessionEndDiag.value = true
    }

    fun getSessionTime() : Double? {
        return sessionTime?.roundedToNextHalf()
    }

    suspend fun endSession() {
        if(timerService == null || sessionTime == null)
            return

        val practiceDate: LocalDate = timerService!!.getSessionStartDate() ?: return

        practiceTimeRepository.accumulatePracticeTime(
            PracticeTime(
                0,
                sessionTime!!.roundedToNextHalf(),
                practiceDate
            )
        )

        timerService?.resetTimer()
    }
}