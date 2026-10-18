package org.jhaeussler.practicetracker.drummachine.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jhaeussler.practicetracker.drummachine.session.AppDrumMachineSession
import org.jhaeussler.practicetracker.drummachine.session.RhythmMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DrumMachineServiceTest {

    private val session = AppDrumMachineSession
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun resetSessionState() {
        session.setRunning(false)
        session.setBpm(120)
        session.setMode(RhythmMode.METRONOME)
        session.updatePlaybackEvent(null)
    }

    // --- Transport & Lifecycle Tests ---

    @Test
    fun `handleStart sets isRunning state to true and starts foreground notification`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        assertTrue(session.isRunning.value)

        val shadowService = Shadows.shadowOf(service)
        assertNotNull("Foreground notification should be posted", shadowService.lastForegroundNotification)
        assertEquals(420, shadowService.lastForegroundNotificationId)

        controller.destroy()
    }

    @Test
    fun `handleStop sets isRunning state to false and stops service`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_STOP
        }, 0, 2)

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `toggle command switches isRunning state back and forth`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        assertFalse(session.isRunning.value)

        val toggleIntent = Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_TOGGLE
        }

        // Toggle 1: Start
        service.onStartCommand(toggleIntent, 0, 1)
        assertTrue(session.isRunning.value)

        // Toggle 2: Stop
        service.onStartCommand(toggleIntent, 0, 2)
        assertFalse(session.isRunning.value)

        controller.destroy()
    }

    @Test
    fun `onDestroy stops playback and cleans up`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        controller.destroy()

        assertFalse(session.isRunning.value)
    }

    @Test
    fun `onTaskRemoved stops playback and calls stopSelf`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        service.onTaskRemoved(Intent())

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    // --- Audio Focus & Hardware Route Tests ---

    @Test
    fun `ACTION_AUDIO_BECOMING_NOISY stops playback`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `audio focus loss stops playback`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        shadowAudioManager.lastAudioFocusRequest?.listener?.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `audio focus loss transient stops playback`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        shadowAudioManager.lastAudioFocusRequest?.listener?.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `handleStart aborts and calls stopSelf if audio focus request is denied`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)

        assertFalse(session.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertNull(Shadows.shadowOf(service).lastForegroundNotification)

        controller.destroy()
    }

    // --- Notification Tests ---

    @Test
    fun `notification contains correct title, current BPM, and stop action button`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        session.setBpm(135)

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)

        val shadowService = Shadows.shadowOf(service)
        val notification = shadowService.lastForegroundNotification

        assertNotNull(notification)
        assertEquals("Metronome", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("135 BPM", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue("Notification must be ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

        assertNotNull(notification.actions)
        val stopAction = notification.actions.firstOrNull { it.title.toString() == "Stop" }
        assertNotNull("Notification must contain a 'Stop' action", stopAction)

        val shadowPendingIntent = Shadows.shadowOf(stopAction?.actionIntent)
        val deliveredIntent = shadowPendingIntent.savedIntent
        assertEquals(DrumMachineService.ACTION_STOP, deliveredIntent.action)

        controller.destroy()
    }

    @Test
    fun `changing BPM updates notification after debounce when running`() = runTest {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        session.setBpm(180)

        var updatedNotification = shadowNotificationManager.getNotification(420)
        assertNotNull(updatedNotification)
        assertNotEquals("180 BPM", updatedNotification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))

        updatedNotification = shadowNotificationManager.getNotification(420)
        assertEquals("180 BPM", updatedNotification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        controller.destroy()
    }

    @Test
    fun `changing BPM does not post or update notification when stopped`() = runTest {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        controller.create().get()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        session.setBpm(150)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))

        val notification = shadowNotificationManager.getNotification(420)
        assertNull(notification)

        controller.destroy()
    }

    // --- Intent Routing Edge Cases ---

    @Test
    fun `unrecognized action stops service if not running`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        assertFalse(session.isRunning.value)

        val unknownIntent = Intent(context, DrumMachineService::class.java).apply {
            action = "UNRECOGNIZED_ACTION"
        }
        val result = service.onStartCommand(unknownIntent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `unrecognized action does not stop service if actively running`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)
        assertTrue(session.isRunning.value)

        val unknownIntent = Intent(context, DrumMachineService::class.java).apply {
            action = "UNRECOGNIZED_ACTION"
        }
        service.onStartCommand(unknownIntent, 0, 2)

        assertTrue(session.isRunning.value)
        assertFalse(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `static helper functions dispatch correct service intents`() {
        val shadowApp = Shadows.shadowOf(context as Application)

        DrumMachineService.start(context)
        var nextIntent = shadowApp.nextStartedService
        assertNotNull(nextIntent)
        assertEquals(DrumMachineService.ACTION_START, nextIntent.action)

        DrumMachineService.stop(context)
        nextIntent = shadowApp.nextStartedService
        assertNotNull(nextIntent)
        assertEquals(DrumMachineService.ACTION_STOP, nextIntent.action)

        DrumMachineService.toggle(context)
        nextIntent = shadowApp.nextStartedService
        assertNotNull(nextIntent)
        assertEquals(DrumMachineService.ACTION_TOGGLE, nextIntent.action)
    }

    @Test
    fun `ACTION_NOTIFICATION_GRANTED refreshes notification if playback is active`() {
        val controller = Robolectric.buildService(DrumMachineService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_START
        }, 0, 1)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        // Deliver notification permission callback
        service.onStartCommand(Intent(context, DrumMachineService::class.java).apply {
            action = DrumMachineService.ACTION_NOTIFICATION_GRANTED
        }, 0, 2)

        assertNotNull(shadowNotificationManager.getNotification(420))

        controller.destroy()
    }
}