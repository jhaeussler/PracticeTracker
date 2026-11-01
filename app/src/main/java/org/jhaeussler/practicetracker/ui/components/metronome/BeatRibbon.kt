package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.drummachine.grid.BeatState
import org.jhaeussler.practicetracker.drummachine.grid.MeterTrack

@Composable
fun BeatRibbon(
    primaryMeter: MeterTrack,
    activeBeatIndex: Int?,
    currentWindowIndex: Int,
    onToggleBeat: (Int) -> Unit,
    onPreviousWindow: () -> Unit,
    onNextWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windows = primaryMeter.getBeatWindows()
    val safeWindowIndex = currentWindowIndex.coerceIn(0, maxOf(0, windows.size - 1))
    val currentRange = if (windows.isNotEmpty()) windows[safeWindowIndex] else (0 until primaryMeter.beatCount)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(13.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // Previous Window Arrow (Visible if more than 1 window)
            if (windows.size > 1) {
                IconButton(
                    onClick = onPreviousWindow,
                    enabled = safeWindowIndex > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Previous Beat Window",
                        tint = if (safeWindowIndex > 0) MaterialTheme.colorScheme.onSurface else Color.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // The Active Beat-Window Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (beatIndex in currentRange) {
                    val isCurrent = (activeBeatIndex == beatIndex)
                    val beatState = primaryMeter.getBeatState(beatIndex)
                    val isDownbeat = (beatIndex == 0)

                    InteractiveBeatButton(
                        beatNumber = beatIndex + 1,
                        state = beatState,
                        isCurrent = isCurrent,
                        isDownbeat = isDownbeat,
                        onClick = { onToggleBeat(beatIndex) }
                    )
                }
            }

            // Next Window Arrow (Visible if more than 1 window)
            if (windows.size > 1) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onNextWindow,
                    enabled = safeWindowIndex < windows.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Next Beat Window",
                        tint = if (safeWindowIndex < windows.size - 1) MaterialTheme.colorScheme.onSurface else Color.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Sub-area label for complex meters (e.g. "Beats 6 - 10 of 21")
        if (windows.size > 1) {
            Text(
                text = "Beats ${currentRange.first + 1} - ${currentRange.last + 1} of ${primaryMeter.beatCount}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun InteractiveBeatButton(
    beatNumber: Int,
    state: BeatState,
    isCurrent: Boolean,
    isDownbeat: Boolean,
    onClick: () -> Unit
) {
    val targetContainerColor = when {
        isCurrent && state == BeatState.MUTED -> Color.Gray
        isCurrent && isDownbeat -> MaterialTheme.colorScheme.onSurface // Bright Red Downbeat
        isCurrent && state == BeatState.ACCENTED -> MaterialTheme.colorScheme.primary
        isCurrent -> Color.White // Normal Active Pulse
        state == BeatState.ACCENTED -> MaterialTheme.colorScheme.primary
        state == BeatState.MUTED -> Color.DarkGray
        else -> MaterialTheme.colorScheme.surfaceContainerHigh // Inactive Normal
    }

    val targetContentColor = when {
        isCurrent && (isDownbeat || state == BeatState.ACCENTED) -> Color.White
        isCurrent && state == BeatState.MUTED -> Color.DarkGray
        isCurrent -> Color.Black
        state == BeatState.MUTED -> Color.Gray
        state == BeatState.ACCENTED -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.onSurface
    }

    val animatedBg by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 60),
        label = "BeatButtonBg"
    )

    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = animatedBg,
            contentColor = targetContentColor
        ),
        modifier = Modifier
            .size(50.dp)
            .alpha(if (state == BeatState.MUTED && !isCurrent) 0.45f else 1.0f)
            .then(
                if (state == BeatState.ACCENTED && !isCurrent) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else if (isDownbeat && !isCurrent) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else Modifier
            )
    ) {
        Text(
            text = "$beatNumber",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (state == BeatState.ACCENTED || isCurrent) FontWeight.Bold else FontWeight.Medium
        )
    }
}