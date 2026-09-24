/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker

import android.util.Log
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import org.jhaeussler.practicetracker.ui.viewModels.EnterValueOnDateViewModel
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import java.time.LocalDate

class EnterDateValueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: PracticeTimeRepository = mockk()
    private lateinit var viewModel: EnterValueOnDateViewModel

    // TODO: Replace Static Log::class calls from the view model with a logger
    //  that is being injected to the ViewModles Constructor
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        // Add other log levels (d, i, w) if you use them
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `valid duration and date string inputs to PracticeTime`() = runTest {
        // We initialize inside the test or a @Before block
        viewModel = EnterValueOnDateViewModel(repository)

        val duration = "3.5"
        val expectedDuration = 3.5

        var testDate = "01.01.2026"

        var expectedDate = LocalDate.of(
            2026,
            1,
            1
        )

        var result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertEquals(result?.value, expectedDuration)
        assertEquals(result?.date, expectedDate)

        // Leap Year
        testDate = "29.02.2028"

        expectedDate = LocalDate.of(
            2028,
            2,
            29
        )

        result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertEquals(result?.date, expectedDate)
    }

    @Test
    fun `invalid date string inputs don't convert to PracticeTime`() = runTest {
        // We initialize inside the test or a @Before block
        viewModel = EnterValueOnDateViewModel(repository)

        val duration = "3.5"
        var testDate = "00.01.2026"

        var result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertNull(result)

        testDate = "01.13.2026"

        result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertNull(result)

        testDate = "31.12.2023"

        result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertNull(result)

        testDate = "30.02.2026"

        result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertNull(result)

        // 2024 & 2028 are leap years
        testDate = "29.02.2026"

        result = viewModel.inputToDuration(
            durationInput = duration,
            dateInput = testDate
        )

        assertNull(result)
    }
}
