/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

const val TEST_SAMPLE_RATE = 44100

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeServiceTest {

    private val repository = MetronomeRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun resetRepositoryState() {
        // Guarantee clean, isolated state between tests
        repository.setRunning(false)
        repository.setBpm(120)
        repository.updateCurrentBeat(0)
        repository.updateBeatMeasure(MetronomeRepository.createInitialBeats(4))
    }

    // --- StateFlow & BPM Validation Tests ---

    @Test
    fun `setBpm should coerce values within range 30 to 300`() = runTest {
        repository.setBpm(20)
        assertEquals(30, repository.bpm.value)

        repository.setBpm(350)
        assertEquals(300, repository.bpm.value)

        repository.setBpm(120)
        assertEquals(120, repository.bpm.value)
    }

    @Test
    fun `bpm flow should emit new values when setBpm is called`() = runTest {
        // Reset to a known value first
        repository.setBpm(120)

        repository.bpm.test {
            assertEquals(120, awaitItem())

            repository.setBpm(140)
            assertEquals(140, awaitItem())

            repository.setBpm(60)
            assertEquals(60, awaitItem())
        }
    }

    @Test
    fun `isRunning has correct initial value`() = runTest {
        assertFalse(repository.isRunning.value)
    }

    @Test
    fun `setSubdivisions dispatches correct ACTION_SET_SUBDIVISION intent`() {
        repository.setSubdivisions(context, 3)

        val shadowApp = Shadows.shadowOf(context as Application)
        val nextIntent = shadowApp.nextStartedService

        assertNotNull(nextIntent)
        assertEquals(MetronomeService.ACTION_SET_SUBDIVISION, nextIntent.action)
        assertEquals(3, nextIntent.getIntExtra(MetronomeService.EXTRA_VALUE, -1))
    }

    @Test
    fun `service updates repository beat state when receiving ACTION_SET_SUBDIVISION`() {
        // Create and launch MetronomeService instance
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        // Build intent sent by setSubdivisions
        val intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 3)
        }

        // Deliver intent to onStartCommand
        service.onStartCommand(intent, 0, 1)

        // Assert that repository beats stateflow updated as a result
        assertEquals(3, repository.beats.value.size)

        controller.destroy()
    }

    @Test
    fun `setSubdivisions rejects values outside 1 to 8`() = runTest {
        // Create and launch MetronomeService instance
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        var intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 4)
        }

        service.onStartCommand(intent, 0, 1)

        // Assert that repository beats stateflow updated as a result
        assertEquals(4, repository.beats.value.size)

        intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 0)
        }

        service.onStartCommand(intent, 0, 2)
        assertEquals(4, repository.beats.value.size)

        intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 1)
        }

        service.onStartCommand(intent, 0, 3)
        assertEquals(1, repository.beats.value.size)

        intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 9)
        }

        service.onStartCommand(intent, 0, 4)
        assertEquals(1, repository.beats.value.size)

        intent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 8)
        }

        service.onStartCommand(intent, 0, 5)
        assertEquals(8, repository.beats.value.size)

        controller.destroy()
    }

    @Test
    fun `toggleBeat dispatches correct ACTION_TOGGLE_BEAT intent with index extra`() {
        // Dispatch toggle command from repository
        repository.toggleBeat(context, 2)

        // Capture sent service intent via Robolectric Shadow
        val shadowApp = Shadows.shadowOf(context as Application)
        val nextIntent = shadowApp.nextStartedService

        assertNotNull(nextIntent)
        assertEquals(MetronomeService.ACTION_TOGGLE_BEAT, nextIntent.action)
        assertEquals(2, nextIntent.getIntExtra(MetronomeService.EXTRA_VALUE, -1))
    }

    @Test
    fun `service toggles beat enabled state when receiving ACTION_TOGGLE_BEAT`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        val initialBeats = repository.beats.value
        assertEquals(4, initialBeats.size)
        assertTrue("Beat at index 1 should initially be enabled", initialBeats[1].isEnabled)

        val toggleIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_TOGGLE_BEAT
            putExtra(MetronomeService.EXTRA_VALUE, 1)
        }
        service.onStartCommand(toggleIntent, 0, 2)

        var updatedBeats = repository.beats.value
        assertFalse("Beat at index 1 should now be disabled", updatedBeats[1].isEnabled)
        assertTrue("Unmodified beat at index 0 should remain enabled", updatedBeats[0].isEnabled)

        service.onStartCommand(toggleIntent, 0, 3)

        updatedBeats = repository.beats.value
        assertTrue("Beat at index 1 should now be enabled again", updatedBeats[1].isEnabled)

        controller.destroy()
    }

    @Test
    fun `service ignores ACTION_TOGGLE_BEAT with invalid index`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        val setSubdivisionIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_SET_SUBDIVISION
            putExtra(MetronomeService.EXTRA_VALUE, 3)
        }
        service.onStartCommand(setSubdivisionIntent, 0, 1)

        val beatsBefore = repository.beats.value
        assertTrue(beatsBefore.size == 3)

        // Send ACTION_TOGGLE_BEAT with out-of-bounds index 4
        val invalidToggleIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_TOGGLE_BEAT
            putExtra(MetronomeService.EXTRA_VALUE, 4)
        }
        service.onStartCommand(invalidToggleIntent, 0, 2)

        val beatsAfter = repository.beats.value
        assertEquals(beatsBefore, beatsAfter)

        controller.destroy()
    }

    @Test
    fun `handleStart sets isRunning state to true and starts foreground notification`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }
        service.onStartCommand(startIntent, 0, 1)

        assertTrue(repository.isRunning.value)

        val shadowService = Shadows.shadowOf(service)
        assertNotNull("Foreground notification should be posted", shadowService.lastForegroundNotification)
        assertEquals(420, shadowService.lastForegroundNotificationId)


        controller.destroy()
    }

    @Test
    fun `handleStop sets isRunning state to false and stops service`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        // Start playback first
        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }, 0, 1)
        assertTrue(repository.isRunning.value)

        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_STOP_METRONOME
        }, 0, 2)

        assertFalse(repository.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `toggle command switches isRunning state back and forth`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        assertFalse(repository.isRunning.value)

        val toggleIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_TOGGLE_METRONOME
        }

        // Toggle 1: Start
        service.onStartCommand(toggleIntent, 0, 1)
        assertTrue(repository.isRunning.value)

        // Toggle 2: Stop
        service.onStartCommand(toggleIntent, 0, 2)
        assertFalse(repository.isRunning.value)
    }

    @Test
    fun `handleStart syncs repository beats into engine state`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        // Seed repository with customized beats
        val customBeats = listOf(Beat(0, isEnabled = false), Beat(1, isEnabled = true))
        repository.updateBeatMeasure(customBeats)

        val startIntent = Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }
        service.onStartCommand(startIntent, 0, 1)

        assertEquals(2, repository.beats.value.size)
        assertFalse(repository.beats.value[0].isEnabled)
    }

    @Test
    fun `onDestroy stops playback and resets running state`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        controller.create().get()

        repository.setRunning(true)

        // Destroy service
        controller.destroy()

        assertFalse(repository.isRunning.value)
    }

    @Test
    fun `onTaskRemoved cleans up audio and resets state`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        repository.setRunning(true)

        service.onTaskRemoved(Intent())

        assertFalse(repository.isRunning.value)
    }

    @Test
    fun `ACTION_AUDIO_BECOMING_NOISY stops playback`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        // Start playback
        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }, 0, 1)
        assertTrue(repository.isRunning.value)

        // Simulate unplugging headphones
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        // Verify playback stopped automatically
        assertFalse(repository.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `audio focus loss stops playback`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }, 0, 1)
        assertTrue(repository.isRunning.value)

        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        // Use .listener on Robolectric's AudioFocusRequest wrapper
        shadowAudioManager.lastAudioFocusRequest?.listener?.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS
        )

        // Drain the main looper in case stopSelf / cleanup posted runnables
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(repository.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }

    @Test
    fun `onDestroy stops playback and cleans up`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }, 0, 1)
        assertTrue(repository.isRunning.value)

        controller.destroy()

        assertFalse(repository.isRunning.value)
    }

    @Test
    fun `onTaskRemoved stops playback and calls stopSelf`() {
        val controller = Robolectric.buildService(MetronomeService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, MetronomeService::class.java).apply {
            action = MetronomeService.ACTION_START_METRONOME
        }, 0, 1)
        assertTrue(repository.isRunning.value)

        service.onTaskRemoved(Intent())

        assertFalse(repository.isRunning.value)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }
}
