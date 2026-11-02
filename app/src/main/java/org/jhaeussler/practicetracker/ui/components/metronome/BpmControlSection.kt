package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.ui.components.ArcSlider


@Composable
fun BpmControlSection(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minBpm: Int = 20,
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

    val strokeWidth = 10.dp
    val thumbRadius = 14.dp
    val dialWidth = 300.dp

    val padding = maxOf(strokeWidth / 2, thumbRadius)
    val radius = (dialWidth - padding * 2) / 2
    val sin45 = 0.70710677f
    val dialHeight = (dialWidth / 2) + (radius * sin45) + thumbRadius

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
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

            BpmDisplay(
                bpm,
                Modifier
                    .padding(top = 30.dp)
                    .clickable { showEditDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Quick Adjustment Row: -5, -1, +1, +5
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BpmStepButton(label = "-5", onClick = { onAdjustBpm(-5) })
            BpmStepButton(label = "-1", onClick = { onAdjustBpm(-1) })
            BpmStepButton(label = "+1", onClick = { onAdjustBpm(+1) })
            BpmStepButton(label = "+5", onClick = { onAdjustBpm(+5) })
        }
    }
}

@Composable
private fun BpmStepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // Matches BpmDisplay color
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.defaultMinSize(minWidth = 56.dp, minHeight = 36.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

