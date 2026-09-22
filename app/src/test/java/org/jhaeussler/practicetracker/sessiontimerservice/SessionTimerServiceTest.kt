/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionTimerServiceTest {

    private lateinit var controller: ServiceController<SessionTimerService>
    private lateinit var service: SessionTimerService

    @Before
    fun setUp() {
        // Create and setup service using Robolectric
        controller = Robolectric.buildService(SessionTimerService::class.java)
        service = controller.create().get()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    /**
     * Advances both SystemClock.elapsedRealtime() and the Handler's Looper
     * synchronously to simulate real time passing in unit tests.
     */
    private fun advanceTimeBySeconds(seconds: Long) {
        val millis = TimeUnit.SECONDS.toMillis(seconds)
        SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + millis)
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    @Test
    fun service_initialState_isStopped() {
        assertFalse(service.isRunning())
        assertFalse(service.isPaused())
        assertNull(service.getSessionStartDate())
    }

    @Test
    fun startOrResumeTimer_startsTimer_andUpdatesState() {
        service.startOrResumeTimer()

        assertTrue(service.isRunning())
        assertFalse(service.isPaused())
        assertEquals(LocalDate.now(), service.getSessionStartDate())
    }

    @Test
    fun pauseTimer_whenRunning_pausesState() {
        service.startOrResumeTimer()
        service.pauseTimer()

        assertFalse(service.isRunning())
        assertTrue(service.isPaused())
    }

    @Test
    fun pauseTimer_whenStopped_doesNothing() {
        service.pauseTimer()

        assertFalse(service.isRunning())
        assertFalse(service.isPaused())
    }

    @Test
    fun resetTimer_resetsStateAndDate() {
        service.startOrResumeTimer()
        advanceTimeBySeconds(5)

        service.resetTimer()

        assertFalse(service.isRunning())
        assertFalse(service.isPaused())
    }

    @Test
    fun timerCallback_receivesTickUpdates_asTimeAdvances() {
        var lastEmittedTime = -1L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        service.startOrResumeTimer()

        // Advance by 1 second
        advanceTimeBySeconds(1)
        assertEquals(1L, lastEmittedTime)

        // Advance by 3 more seconds
        advanceTimeBySeconds(3)
        assertEquals(4L, lastEmittedTime)
    }

    @Test
    fun timerCallback_doesNotTick_whenPaused() {
        var tickCount = 0
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                tickCount++
            }
        })

        service.startOrResumeTimer()
        advanceTimeBySeconds(2) // tickCount = 2

        service.pauseTimer()
        val tickCountAtPause = tickCount

        advanceTimeBySeconds(5) // Time moves forward, but timer is paused

        assertEquals(tickCountAtPause, tickCount)
    }

    @Test
    fun resumeTimer_afterPause_calculatesCorrectElapsedTime() {
        var lastEmittedTime = 0L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        // 1. Run for 3 seconds
        service.startOrResumeTimer()
        advanceTimeBySeconds(3)
        assertEquals(3L, lastEmittedTime)

        // 2. Pause for 10 seconds (paused duration should be ignored in total)
        service.pauseTimer()
        advanceTimeBySeconds(10)

        // 3. Resume and run for 2 more seconds
        service.startOrResumeTimer()
        advanceTimeBySeconds(2)

        // Total running time should be 3s + 2s = 5s (ignoring the 10s pause)
        assertEquals(5L, lastEmittedTime)
    }

    @Test
    fun onStartCommand_withActionStart_startsTimer() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            SessionTimerService::class.java
        ).apply {
            action = "ACTION_START"
        }

        service.onStartCommand(intent, 0, 1)
        assertTrue(service.isRunning())
    }

    @Test
    fun onStartCommand_withActionPause_pausesTimer() {
        service.startOrResumeTimer()

        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionTimerService::class.java).apply {
            action = "ACTION_PAUSE"
        }

        service.onStartCommand(intent, 0, 2)
        assertTrue(service.isPaused())
    }
}
