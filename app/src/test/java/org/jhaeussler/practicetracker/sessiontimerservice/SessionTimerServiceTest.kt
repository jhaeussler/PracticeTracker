/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.sessiontimerservice

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionTimerServiceTest {

    private lateinit var controller: ServiceController<SessionTimerService>
    private lateinit var service: SessionTimerService
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SystemClock.setCurrentTimeMillis(0L)
        // Create and setup service using Robolectric
        controller = Robolectric.buildService(SessionTimerService::class.java)
        service = controller.create().get()

        sendAction(SessionTimerService.ACTION_RESET)
    }

    @After
    fun tearDown() {
        sendAction(SessionTimerService.ACTION_RESET)
        controller.destroy()
    }

    private fun sendAction(action: String) {
        val intent = Intent(context, SessionTimerService::class.java).apply {
            this.action = action
        }
        service.onStartCommand(intent, 0, 1)
    }

    /**
     * Advances the Robolectric main looper time synchronously,
     * which automatically updates SystemClock.elapsedRealtime() and triggers delayed Runnables.
     */
    private fun advanceTimeBySeconds(seconds: Long) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds))
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun service_initialState_isStoppedAndZeroedOut() {
        // Verify state flows are initialized to STOPPED, 0 seconds, and null date
        assertEquals(SessionTimerService.TimerState.STOPPED, SessionTimerService.timerState.value)
        assertEquals(0L, SessionTimerService.elapsedTimeSec.value)
        assertNull(SessionTimerService.sessionStartDate.value)
    }

    @Test
    fun startTimer_fromStopped_setsRunningStateAndTodayDate() {
        sendAction(SessionTimerService.ACTION_START)

        // Verify state shifts to RUNNING and sessionStartDate captures today's date
        assertEquals(SessionTimerService.TimerState.RUNNING, SessionTimerService.timerState.value)
        assertEquals(LocalDate.now(), SessionTimerService.sessionStartDate.value)
    }

    @Test
    fun startTimer_advancesElapsedTime_onHandlerTicks() {
        sendAction(SessionTimerService.ACTION_START)

        // 1. First tick fires immediately on handleStartTimer() post
        assertEquals(0L, SessionTimerService.elapsedTimeSec.value)

        advanceTimeBySeconds(1)
        assertEquals(1L, SessionTimerService.elapsedTimeSec.value)

        advanceTimeBySeconds(4)
        assertEquals(5L, SessionTimerService.elapsedTimeSec.value)
    }

    @Test
    fun pauseTimer_freezesElapsedTime_andContinueTriggersAccumulationAgain() {
        sendAction(SessionTimerService.ACTION_START)
        advanceTimeBySeconds(3)
        assertEquals(3L, SessionTimerService.elapsedTimeSec.value)

        // Pause the timer
        sendAction(SessionTimerService.ACTION_PAUSE)
        assertEquals(SessionTimerService.TimerState.PAUSED, SessionTimerService.timerState.value)

        // Advance time while paused — elapsed time must remain frozen at 3s
        advanceTimeBySeconds(10)
        assertEquals(3L, SessionTimerService.elapsedTimeSec.value)

        // Resume execution
        sendAction(SessionTimerService.ACTION_START)
        assertEquals(SessionTimerService.TimerState.RUNNING, SessionTimerService.timerState.value)

        // Verify time picks up at 3 seconds immediately
        assertEquals(3L, SessionTimerService.elapsedTimeSec.value)

        // Advance another 3 active seconds
        advanceTimeBySeconds(4)
        assertEquals(7L, SessionTimerService.elapsedTimeSec.value)
    }

    @Test
    fun resetTimer_resetsAllStateFlowsToDefaultsAndStopsTimer() {
        sendAction(SessionTimerService.ACTION_START)

        advanceTimeBySeconds(5)
        assertEquals(5L, SessionTimerService.elapsedTimeSec.value)

        // Trigger reset
        sendAction(SessionTimerService.ACTION_RESET)

        // Verify state flows are completely wiped back to clean defaults
        assertEquals(SessionTimerService.TimerState.STOPPED, SessionTimerService.timerState.value)
        assertEquals(0L, SessionTimerService.elapsedTimeSec.value)
        assertNull(SessionTimerService.sessionStartDate.value)
    }

    @Test
    fun startTimer_createsNotificationChannel_andPostsForegroundNotification() {
        sendAction(SessionTimerService.ACTION_START)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)

        val channel = shadowManager.notificationChannels.firstOrNull { it.id == "practice_timer_channel" }
        assertNotNull("Expected notification channel 'practice_timer_channel' to exist", channel)
        assertEquals("Practice Session Notifications", channel?.name)

        // Retrieve posted notification by ID 42
        val notification = shadowManager.getNotification(42)
        assertNotNull(notification)

        val title = notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        assertEquals("Ongoing Practice Session", title)
        assertTrue(text?.contains("Duration: 00 sec.") == true)
    }

    @Test
    fun runningState_notificationContainsPauseAction() {
        sendAction(SessionTimerService.ACTION_START)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)

        val notification = shadowManager.getNotification(42)
        assertNotNull(notification)

        assertEquals(1, notification.actions.size)
        assertEquals("Pause", notification.actions[0].title)
    }

    @Test
    fun pausedState_notificationContainsResumeAction_andUpdatedText() {
        sendAction(SessionTimerService.ACTION_START)
        advanceTimeBySeconds(2)
        sendAction(SessionTimerService.ACTION_PAUSE)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)

        val notification = shadowManager.getNotification(42)
        assertNotNull(notification)

        assertEquals(1, notification.actions.size)
        assertEquals("Resume", notification.actions[0].title)

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertTrue(text?.contains("- Session Paused") == true)
    }

    @Test
    fun startTimer_whenAlreadyRunning_doesNotResetElapsedTimeOrStartDate() {
        sendAction(SessionTimerService.ACTION_START)
        advanceTimeBySeconds(5)

        assertEquals(5L, SessionTimerService.elapsedTimeSec.value)

        // Trigger START again while already running does not change already elapsed time
        sendAction(SessionTimerService.ACTION_START)

        assertEquals(5L, SessionTimerService.elapsedTimeSec.value)
    }

    @Test
    fun resetTimer_whenAlreadyStopped_remainsStoppedAndZeroedOut() {
        assertEquals(SessionTimerService.TimerState.STOPPED, SessionTimerService.timerState.value)

        // Sending RESET should safely do nothing / clear state without throwing
        sendAction(SessionTimerService.ACTION_RESET)

        assertEquals(SessionTimerService.TimerState.STOPPED, SessionTimerService.timerState.value)
        assertEquals(0L, SessionTimerService.elapsedTimeSec.value)
        assertNull(SessionTimerService.sessionStartDate.value)
    }

    @Test
    fun serviceDestroy_cancelsTimerTicks_andStopsAdvancingTime() {
        sendAction(SessionTimerService.ACTION_START)
        advanceTimeBySeconds(3)
        assertEquals(3L, SessionTimerService.elapsedTimeSec.value)

        // Destroy the service (simulating OS service teardown)
        controller.destroy()

        // Advancing time after destruction should not increment elapsedTimeSec
        advanceTimeBySeconds(5)
        assertEquals(3L, SessionTimerService.elapsedTimeSec.value)
    }

    @Test
    fun companionHelperMethods_launchCorrectServiceIntents() {
        SessionTimerService.startTimer(context)

        val startedIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(startedIntent)
        assertEquals(SessionTimerService.ACTION_START, startedIntent.action)
        assertEquals(SessionTimerService::class.java.name, startedIntent.component?.className)
    }

    @Test
    fun onStartCommand_handlesActionPassedAsStringExtra() {
        val intent = Intent(context, SessionTimerService::class.java).apply {
            putExtra("action", "startTimer")
        }
        service.onStartCommand(intent, 0, 1)

        assertEquals(SessionTimerService.TimerState.RUNNING, SessionTimerService.timerState.value)
    }

    @Test
    fun notification_hasContentIntent_targetingPackageLaunchIntent() {
        sendAction(SessionTimerService.ACTION_START)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)
        val notification = shadowManager.getNotification(42)

        assertNotNull(notification?.contentIntent)
    }

    @Test
    fun notification_pauseAction_containsCorrectPendingIntentAction() {
        sendAction(SessionTimerService.ACTION_START)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)
        val notification = shadowManager.getNotification(42)

        val pauseAction = notification?.actions?.get(0)
        assertNotNull(pauseAction?.actionIntent)
    }
}
