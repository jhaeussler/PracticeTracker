package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.R

@Composable
fun BpmDisplay(
    bpm: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier
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