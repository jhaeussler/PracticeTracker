package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.ui.components.SimpleRoundButtonWithIcon
import org.jhaeussler.practicetracker.ui.components.repeatingClickable
import org.jhaeussler.practicetracker.ui.components.ArcSlider


@Composable
fun BpmControlSection(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    minBpm: Int = 30,
    maxBpm: Int = 260
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditBpmDialog(
            initialBpm = bpm,
            onDismiss = { showEditDialog = false },
            onConfirm = { newBpm ->
                onBpmChange(newBpm)
                showEditDialog = false
            }
        )
    }

    val decrementBpm = { onBpmChange(bpm - 1) }
    val incrementBpm = { onBpmChange(bpm + 1) }

    val strokeWidth = 10.dp
    val thumbRadius = 14.dp
    val dialWidth = 300.dp

    // Content top padding scaled proportionally to dialWidth (~16% of width)
    val contentTopPadding = dialWidth * 0.16f

    val padding = maxOf(strokeWidth / 2, thumbRadius)
    val radius = (dialWidth - padding * 2) / 2
    val sin45 = 0.70710677f
    val dialHeight = (dialWidth / 2) + (radius * sin45) + thumbRadius

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(dialWidth)
            .height(dialHeight)
    ) {

        ArcSlider(
            bpm,
            minBpm,
            maxBpm,
            onBpmChange,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier
                .padding(top = 30.dp)
                .clickable { showEditDialog = true }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(140.dp).padding(10.dp)
            ) {
                Text(
                    text = "$bpm",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = stringResource(R.string.bpm),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

