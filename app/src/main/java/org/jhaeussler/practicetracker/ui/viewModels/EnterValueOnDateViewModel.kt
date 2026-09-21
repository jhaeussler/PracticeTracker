/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.DateTimeException
import java.time.LocalDate

class EnterValueOnDateViewModel(
    private val practiceTimeRepository: PracticeTimeRepository
) : ViewModel() {

    suspend fun saveEntry (newEntry : PracticeTime) {
        practiceTimeRepository.accumulatePracticeTime(newEntry)
    }

    fun inputToDuration(durationInput: String, dateInput: String) : PracticeTime? {
        val hours : Double? = durationInput.toDoubleOrNull()
        var date : LocalDate? = null
        val dateInputs = dateInput.split(".")

        if (dateInputs.size != 3) {
            Log.w("Warning", "Invalid input cannot be split into 3 substrings: $dateInput")
            return null
        }
        try {
            date = LocalDate.of(
                dateInputs[2].toInt(),
                dateInputs[1].toInt(),
                dateInputs[0].toInt()
            )
        } catch (ex : NumberFormatException) {
            Log.e("InputError", "Invalid input for number conversion: $dateInput")
        } catch (ex : DateTimeException) {
            Log.e("InputError", "Not a valid Date: $dateInput")
        }

        if (hours != null && hours != 0.0 && date != null && date.year >= 2024)
            return PracticeTime(value = hours, date = date)

        return null
    }
}