package org.jhaeussler.practicetracker.ui.components.metronome.rythm

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.drummachine.grid.*
import org.jhaeussler.practicetracker.ui.components.BackgroundCard

@Composable
fun MeterSection(
    primaryMeter: MeterTrack,
    subdivision: Subdivision,
    timingSummary: String,
    onIncrementBeatCount: () -> Unit,
    onDecrementBeatCount: () -> Unit,
    onBeatUnitSelected: (BeatUnit) -> Unit,
    onSubdivisionSelected: (Subdivision) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        BackgroundCard {
            TimeSignatureBlock(
                beatCount = primaryMeter.beatCount,
                beatUnit = primaryMeter.beatUnit,
                subdivision = subdivision,
                timingSummary = timingSummary,
                onIncrement = onIncrementBeatCount,
                onDecrement = onDecrementBeatCount,
                onUnitSelected = onBeatUnitSelected,
                onSubdivisionSelected = onSubdivisionSelected,
                modifier = Modifier.padding(13.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun TimeSignatureBlock(
    beatCount: Int,
    beatUnit: BeatUnit,
    subdivision: Subdivision,
    timingSummary: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onUnitSelected: (BeatUnit) -> Unit,
    onSubdivisionSelected: (Subdivision) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "TIME SIGNATURE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Numerator Stepper
        BeatCountInput(
            beatCount = beatCount,
            onIncrement = onIncrement,
            onDecrement = onDecrement
        )

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            thickness = 2.dp,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(0.5f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Denominator Chips (/2, /4, /8, /16)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BeatUnit.entries.forEach { unit ->
                val isSelected = (unit == beatUnit)
                FilterChip(
                    selected = isSelected,
                    onClick = { onUnitSelected(unit) },
                    label = {
                        Text(
                            text = "/${unit.denominator} ${unit.symbol}",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Subdivision:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )

            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isDropdownExpanded = true }
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = subdivision.getLabelFor(beatUnit),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Subdivision",
                            modifier = Modifier.size(18.dp),
                            tint = Color.Gray
                        )
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    Subdivision.entries.forEach { sub ->
                        val isCurrent = (sub == subdivision)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sub.getLabelFor(beatUnit),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                            },
                            onClick = {
                                onSubdivisionSelected(sub)
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Timing Calculation Subtitle
        Text(
            text = timingSummary,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Gray
        )
    }
}