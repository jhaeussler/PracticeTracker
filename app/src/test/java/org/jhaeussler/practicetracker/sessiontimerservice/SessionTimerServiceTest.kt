/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowService
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
     * Advances the Robolectric main looper time synchronously,
     * which automatically updates SystemClock.elapsedRealtime() and triggers delayed Runnables.
     */
    private fun advanceTimeBySeconds(seconds: Long) {
        ShadowLooper.idleMainLooper(TimeUnit.SECONDS.toMillis(seconds), TimeUnit.MILLISECONDS)
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

    @Test
    fun onStartCommand_withStringExtraAction_handlesStartAndPause() {
        val startIntent = Intent(ApplicationProvider.getApplicationContext(), SessionTimerService::class.java).apply {
            putExtra("action", "startTimer")
        }
        service.onStartCommand(startIntent, 0, 1)
        assertTrue(service.isRunning())

        val pauseIntent = Intent(ApplicationProvider.getApplicationContext(), SessionTimerService::class.java).apply {
            putExtra("action", "pauseTimer")
        }
        service.onStartCommand(pauseIntent, 0, 2)
        assertTrue(service.isPaused())
    }

    @Test
    fun notification_reflectsRunningAndPausedStates() {
        val shadowService: ShadowService = shadowOf(service)
        service.startOrResumeTimer()

        val foregroundNotification = shadowService.lastForegroundNotification
        val foregroundId = shadowService.lastForegroundNotificationId

        assertEquals(42, foregroundId)
        assertTrue(foregroundNotification.extras.getCharSequence(
            Notification.EXTRA_TEXT).toString().contains("Duration: 00 sec."))

        val notificationManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM: ShadowNotificationManager = shadowOf(notificationManager)

        advanceTimeBySeconds(65)
        val updatedNotification = shadowNM.getNotification(42)
        assertTrue(updatedNotification.extras.getCharSequence(
            Notification.EXTRA_TEXT).toString().contains("1:05 min."))

        service.pauseTimer()
        val pausedNotification = shadowNM.getNotification(42)
        val pausedText = pausedNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(pausedText.contains("Session Paused"))
    }

    @Test
    fun startOrResumeTimer_calledMultipleTimes_doesNotAccelerateTicks() {
        var tickCount = 0
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                tickCount++
            }
        })

        service.startOrResumeTimer()
        service.startOrResumeTimer()

        advanceTimeBySeconds(1)

        // Should only have ticked once, not twice
        assertEquals(1, tickCount)
    }

    @Test
    fun pauseTimer_calledMultipleTimes_doesNotCorruptState() {
        var lastEmittedTime = 0L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        service.startOrResumeTimer()
        advanceTimeBySeconds(2)
        assertEquals(2L, lastEmittedTime)

        service.pauseTimer()
        advanceTimeBySeconds(5)

        // 3. Call pause again (should be ignored and NOT overwrite timePausedAtMillis)
        service.pauseTimer()
        advanceTimeBySeconds(5)

        service.startOrResumeTimer()
        advanceTimeBySeconds(1)

        // 5. Verify the 10 total seconds spent paused were ignored
        assertEquals(3L, lastEmittedTime)
    }

    @Test
    fun resetTimer_emitsZeroAndStopsCallback() {
        var lastEmittedTime = -1L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        service.startOrResumeTimer()
        advanceTimeBySeconds(10)
        assertEquals(10L, lastEmittedTime)

        service.resetTimer()
        assertEquals(0L, lastEmittedTime)

        // Advance time again after reset to make sure handler loop completely stopped
        advanceTimeBySeconds(5)
        assertEquals(0L, lastEmittedTime)
    }

    @Test
    fun multiplePauseResumeCycles_accumulatePauseOffsetCorrectly() {
        var lastEmittedTime = 0L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        // Start -> run 2s (Total run: 2s)
        service.startOrResumeTimer()
        advanceTimeBySeconds(2)

        // Pause 5s
        service.pauseTimer()
        advanceTimeBySeconds(5)

        // Resume -> run 3s (Total run: 5s)
        service.startOrResumeTimer()
        advanceTimeBySeconds(3)
        assertEquals(5L, lastEmittedTime)

        // Pause 10s
        service.pauseTimer()
        advanceTimeBySeconds(10)

        // Resume -> run 4s (Total run: 9s)
        service.startOrResumeTimer()
        advanceTimeBySeconds(4)
        assertEquals(9L, lastEmittedTime)
    }

    @Test
    fun startAfterReset_startsFreshFromZero() {
        var lastEmittedTime = -1L
        service.setTimerCallback(object : SessionTimerService.TimerCallback {
            override fun onTimerTick(elapsedTime: Long) {
                lastEmittedTime = elapsedTime
            }
        })

        service.startOrResumeTimer()
        advanceTimeBySeconds(15)

        service.resetTimer()

        service.startOrResumeTimer()
        advanceTimeBySeconds(2)

        assertEquals(2L, lastEmittedTime)
    }
}
