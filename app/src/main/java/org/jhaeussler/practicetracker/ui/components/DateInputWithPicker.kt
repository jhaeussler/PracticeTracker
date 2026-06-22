package org.jhaeussler.practicetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jhaeussler.practicetracker.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputWithPicker(
    textFieldHeight: Int,
    selectedDateChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = Instant.now().toEpochMilli()
    )
    val selectedDate = datePickerState.selectedDateMillis?.let {
        convertMillisToDate(it)
    }.also { selectedDateChanged(it ?: "") } ?: ""

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
    ) {
        TextField(
            // Adding the delegate's getter and setter imports (see imports above) lets you read
            // and set amountInput without referring to the MutableState's 'value' property.
            value = selectedDate,
            onValueChange = { },
            singleLine = true,
            label = { Text("Select Date") },
            readOnly = true,
            interactionSource = interactionSource,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.calendar_month),
                    contentDescription = "Select date"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(textFieldHeight.dp)
        )

        if(interactionSource.collectIsPressedAsState().value)
            showDatePicker = true

        if (showDatePicker) {
            Popup(
                onDismissRequest = { showDatePicker = false },
                alignment = Alignment.TopCenter
            ) {
                Box (
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .offset(y = textFieldHeight.dp)
                        .shadow(elevation = 4.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }
        }
    }
}

fun convertMillisToDate(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}