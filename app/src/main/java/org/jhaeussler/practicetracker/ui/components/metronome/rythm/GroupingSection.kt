package org.jhaeussler.practicetracker.ui.components.metronome.rythm

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.drummachine.grid.*
import org.jhaeussler.practicetracker.ui.components.BackgroundCard
import org.jhaeussler.practicetracker.ui.components.metronome.BeatColorMapping

@Composable
fun GroupingSection(
    primaryMeter: MeterTrack,
    activeBeatIndex: Int?,
    onAdjustGroupSize: (groupIndex: Int, delta: Int) -> Unit,
    onSplitGroup: (Int) -> Unit,
    onMergeGroupWithPrevious: (Int) -> Unit,
    onResetGrouping: () -> Unit,
    onApplyGroupingAccents: () -> Unit,
    onResetToDefaultAccents: () -> Unit,
    onToggleBeat: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = if (meterIsValid(primaryMeter)) {
        primaryMeter.grouping
    } else {
        MeterTrack.defaultGroupingFor(primaryMeter.beatCount)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        BackgroundCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "METRIC GROUPING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Reset Accents to 1st beat only
                        TextButton(
                            onClick = onResetToDefaultAccents,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear Accents", style = MaterialTheme.typography.labelSmall)
                        }

                        // Auto-Accent downbeat of each group
                        TextButton(
                            onClick = onApplyGroupingAccents,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Auto-Accent", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Formula summary: e.g. "4 + 4 + 4 = 12 beats"
                    Text(
                        text = "${groups.joinToString(" + ")} = ${primaryMeter.beatCount} beats",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Reset Groups to Default
                    IconButton(
                        onClick = onResetGrouping,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt,
                            contentDescription = "Reset Groups",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sub-Groups List (Each group in its own row)
                var cumulativeBeatIndex = 0

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEachIndexed { groupIndex, groupSize ->
                        val startBeat = cumulativeBeatIndex
                        cumulativeBeatIndex += groupSize

                        // Can decrement if there is another block that has room (< 8)
                        val canDecrement = groups.size > 1 &&
                                groups.indices.any { it != groupIndex && groups[it] < 8 }

                        // Can increment if this block is < 8 and another block has at least 1 beat to give
                        val canIncrement = groups.size > 1 &&
                                groupSize < 8 &&
                                groups.indices.any { it != groupIndex && groups[it] >= 1 }

                        GroupRowCard(
                            groupSize = groupSize,
                            startBeatIndex = startBeat,
                            primaryMeter = primaryMeter,
                            activeBeatIndex = activeBeatIndex,
                            canSplit = groupSize >= 2,
                            canMerge = groupIndex > 0 && groups[groupIndex - 1] < 8,
                            canIncrement = canIncrement,
                            canDecrement = canDecrement,
                            onDecrement = { onAdjustGroupSize(groupIndex, -1) },
                            onIncrement = { onAdjustGroupSize(groupIndex, +1) },
                            onSplit = { onSplitGroup(groupIndex) },
                            onMerge = { onMergeGroupWithPrevious(groupIndex) },
                            onToggleBeat = onToggleBeat
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GroupRowCard(
    groupSize: Int,
    startBeatIndex: Int,
    primaryMeter: MeterTrack,
    activeBeatIndex: Int?,
    canSplit: Boolean,
    canMerge: Boolean,
    canIncrement: Boolean,
    canDecrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onToggleBeat: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Beat Circles Row: Filled = Accented, Outlined = Normal
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp)
            ) {
                for (offset in 0 until groupSize) {
                    val beatIndex = startBeatIndex + offset
                    val isAccented = primaryMeter.isAccented(beatIndex)
                    val isMuted = primaryMeter.isMuted(beatIndex)
                    val beatNumber = beatIndex + 1

                    MiniBeatCircle(
                        beatNumber = beatNumber,
                        isCurrent = (((activeBeatIndex != null) && (beatNumber == activeBeatIndex + 1))),
                        isAccented = isAccented,
                        isMuted = isMuted,
                        onClick = { onToggleBeat(beatIndex) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Decrease block (-1 to here, +1 to last block)
                FilledTonalIconButton(
                    onClick = onDecrement,
                    enabled = canDecrement,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Remove,
                        contentDescription = "Decrease block",
                        modifier = Modifier.size(16.dp))
                }

                // Increase block (+1 to here, -1 from last block)
                FilledTonalIconButton(
                    onClick = onIncrement,
                    enabled = canIncrement,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Add,
                        contentDescription = "Increase block",
                        modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Split Group
                if (canSplit) {
                    IconButton(
                        onClick = onSplit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Split Group",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Merge Group
                if (canMerge) {
                    IconButton(
                        onClick = onMerge,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MergeType,
                            contentDescription = "Merge Next",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Small circular beat indicator:
 * - Accented: Filled circle
 * - Unaccented: Empty outlined circle
 * - Muted: Dimmed strike circle
 */
@Composable
private fun MiniBeatCircle(
    beatNumber: Int,
    isCurrent: Boolean,
    isAccented: Boolean,
    isMuted: Boolean,
    onClick: () -> Unit
) {
    val colors = BeatColorMapping.getBeatContentAndContainerColorPair(
        state = if (isAccented) BeatState.ACCENTED else if (isMuted) BeatState.MUTED else BeatState.NORMAL,
        isCurrent = isCurrent,
        isDownbeat = isAccented
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (isAccented) Modifier.border(0.dp, Color.Transparent, CircleShape)
                else Modifier.border(1.5.dp, if (isMuted) Color.DarkGray else Color.Gray, CircleShape)
            )
            .then(
                if (colors.second != Color.Transparent) Modifier.clip(CircleShape).then(
                    Modifier.border(0.dp, Color.Transparent, CircleShape)
                ) else Modifier
            )
    ) {
        Surface(
            color = colors.second,
            shape = CircleShape,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$beatNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isAccented) FontWeight.Bold else FontWeight.Normal,
                    color = colors.first
                )
            }
        }
    }
}

private fun meterIsValid(meter: MeterTrack): Boolean = meter.grouping.sum() == meter.beatCount
