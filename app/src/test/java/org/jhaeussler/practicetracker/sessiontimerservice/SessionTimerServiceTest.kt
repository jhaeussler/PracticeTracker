/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.os.SystemClock
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
}
