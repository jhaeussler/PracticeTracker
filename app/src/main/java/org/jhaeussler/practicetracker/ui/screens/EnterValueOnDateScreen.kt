/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.ui.components.DateInputWithPicker
import org.jhaeussler.practicetracker.ui.components.NumberInputField
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.viewmodels.EnterValueOnDateViewModel
import kotlinx.coroutines.launch

@Composable
fun EnterValueOnDateScreen(
    viewModel: EnterValueOnDateViewModel = viewModel(factory = AppViewModelProvider.Factory),
    navigateBack: () -> Unit
) {
    var inputHoursText by remember { mutableStateOf("") }
    var inputDateText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    ScreenContainer(
        title = R.string.add_time_screen_title
    ) {
        NumberInputField(
            label = R.string.time_input_field_label,
            leadingIcon = R.drawable.timelapse,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            value = inputHoursText,
            onValueChanged = {
                inputHoursText = it
            },
            onDone = {},
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(70.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        DateInputWithPicker(
            textFieldHeight = 70,
            selectedDateChanged = { inputDateText = it },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(70.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        PracticeAppButton(
            text = R.string.save_btn_txt,
            onClick = {
                val newEntry : PracticeTime? = viewModel.inputToDuration(inputHoursText, inputDateText)
                if (newEntry != null) coroutineScope.launch {
                    viewModel.saveEntry(newEntry)
                    navigateBack()
                }
                inputHoursText = ""
            },
        )
        Spacer(modifier = Modifier.height(40.dp))
    }
}