package org.jhaeussler.practicetracker.ui.components.metronome.rythm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BeatCountInput(
    beatCount: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        FilledTonalIconButton(
            onClick = onDecrement,
            enabled = beatCount > 1,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease beats")
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(70.dp).height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$beatCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        FilledTonalIconButton(
            onClick = onIncrement,
            enabled = beatCount < 64,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase beats")
        }
    }
}