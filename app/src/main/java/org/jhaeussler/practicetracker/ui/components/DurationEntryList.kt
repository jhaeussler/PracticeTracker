package org.jhaeussler.practicetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.jhaeussler.practicetracker.R
import java.time.format.DateTimeFormatter

@Composable
fun DurationEntryList(
    entries: List<PracticeTime>,
    deleteAction: (PracticeTime) -> Unit,
    modifier: Modifier = Modifier) {

    Box (
        modifier = modifier.background(
            color = Color.Black.copy(alpha = 0.3f),
            shape = RoundedCornerShape(10.dp),
        )
    ) {
        LazyColumn(
            modifier = Modifier.padding(10.dp),
            contentPadding = PaddingValues(5.dp)
        ) {
            items(items = entries, key = { it.id }) { duration ->
                DurationEntry(
                    duration,
                    deleteAction = deleteAction,
                    Modifier.fillMaxWidth(0.8f)
                )
            }
        }
    }
}

@Composable
fun DurationEntry(
    duration : PracticeTime,
    deleteAction: (PracticeTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteConfirmationRequired by rememberSaveable { mutableStateOf(false) }
    Row (
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "${duration.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}: ",
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Left
        )
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            text = "${duration.value} h",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Right
        )
        Spacer(modifier = Modifier.weight(0.5f))
        Button(
            onClick = { deleteConfirmationRequired = true }
        ) {
            Text( text = "Delete")
        }
        if (deleteConfirmationRequired) {
            DeleteConfirmationDialog(
                onDeleteConfirm = {
                    deleteConfirmationRequired = false
                    deleteAction(duration)
                },
                onDeleteCancel = { deleteConfirmationRequired = false },
                modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium))
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(onDismissRequest = { /* Do nothing */ },
        title = { Text(stringResource(R.string.attention)) },
        text = { Text(stringResource(R.string.delete_question)) },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDeleteCancel) {
                Text(stringResource(R.string.no))
            }
        },
        confirmButton = {
            TextButton(onClick = onDeleteConfirm) {
                Text(stringResource(R.string.yes))
            }
        }
    )
}