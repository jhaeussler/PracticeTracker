package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jhaeussler.practicetracker.drummachine.grid.BeatState

object BeatColorMapping
{
    @Composable
    fun getBeatContentAndContainerColorPair(
        state: BeatState,
        isCurrent: Boolean,
        isDownbeat: Boolean
    ) : Pair<Color, Color> = when {
        isCurrent && state == BeatState.MUTED -> Pair(Color.DarkGray, Color.Gray)
        isCurrent && (isDownbeat || state == BeatState.ACCENTED) -> Pair(Color.White, MaterialTheme.colorScheme.onSurface)
        isCurrent -> Pair(Color.Black, Color.White) // Normal Active Pulse
        state == BeatState.ACCENTED -> Pair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary)
        state == BeatState.MUTED -> Pair(Color.Gray, Color.DarkGray)
        else -> Pair(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainerHigh) // Inactive Normal
    }
}