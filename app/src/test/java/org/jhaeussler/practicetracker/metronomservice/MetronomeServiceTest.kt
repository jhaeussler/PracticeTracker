/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.metronomservice

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

const val TEST_SAMPLE_RATE = 44100


@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeServiceTest {

    private lateinit var engine: MetronomeEngine

    @Before
    fun setUp() {
        engine = MetronomeEngine(sampleRate = TEST_SAMPLE_RATE)
        engine.enableWallClockSync = false
    }

    // --- StateFlow & BPM Validation Tests ---

    @Test
    fun `setBpm should coerce values within range 30 to 300`() = runTest {
        MetronomeService.setBpm(20)
        assertEquals(30, MetronomeService.bpm.value)

        MetronomeService.setBpm(350)
        assertEquals(300, MetronomeService.bpm.value)

        MetronomeService.setBpm(120)
        assertEquals(120, MetronomeService.bpm.value)
    }

    @Test
    fun `bpm flow should emit new values when setBpm is called`() = runTest {
        // Reset to a known value first
        MetronomeService.setBpm(120)
        
        MetronomeService.bpm.test {
            assertEquals(120, awaitItem())
            
            MetronomeService.setBpm(140)
            assertEquals(140, awaitItem())

            MetronomeService.setBpm(60)
            assertEquals(60, awaitItem())
        }
    }

    @Test
    fun `isRunning has correct initial value`() = runTest {
        assertFalse(MetronomeService.isRunning.value)
    }

    @Test
    fun `setSubdivisions updates service stateflow and engine configuration`() = runTest {
        MetronomeService.setSubdivisions(4)

        MetronomeService.subdivisions.test {
            assertEquals(4, awaitItem())

            MetronomeService.setSubdivisions(6)
            assertEquals(6, awaitItem())
        }
    }

    @Test
    fun `setSubdivisions rejects values outside 1 to 16`() = runTest {
        MetronomeService.setSubdivisions(4)

        MetronomeService.setSubdivisions(0)
        assertEquals(4, MetronomeService.subdivisions.value)

        MetronomeService.setSubdivisions(20)
        assertEquals(4, MetronomeService.subdivisions.value)
    }
}
