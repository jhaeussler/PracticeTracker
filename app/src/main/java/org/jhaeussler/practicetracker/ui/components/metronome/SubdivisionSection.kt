package org.jhaeussler.practicetracker.ui.components.metronome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jhaeussler.practicetracker.metronomservice.Beat
import org.jhaeussler.practicetracker.ui.components.RoundButtonWithIcon

@Composable
fun SubdivisionSection(
    beats: List<Beat>,
    onToggleBeat: (Int) -> Unit,
    currentBeatInMeasure: Int,
    decrementSubdivision: () -> Unit,
    addSubdivision: () -> Unit
) {
    val firstRow = if (beats.size == 4 || beats.size > 6)
        beats.take(4)
    else
        beats.take(3)

    val secondRow = beats.drop(firstRow.size)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SubdivisionButtonRow(
            firstRow,
            0,
            onToggleBeat,
            currentBeatInMeasure = currentBeatInMeasure
        )
        if (secondRow.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SubdivisionButtonRow(
                secondRow,
                firstRow.size,
                onToggleBeat,
                currentBeatInMeasure = currentBeatInMeasure
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ChangeSubdivisionRow(
            decrementSubdivision,
            addSubdivision
        )
    }
}

@Composable
fun SubdivisionButtonRow(
    beats: List<Beat>,
    firstIndex: Int = 0,
    onToggleBeat: (Int) -> Unit,
    currentBeatInMeasure: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        beats.forEachIndexed { i, beat ->
            val realIndex = firstIndex + i

            RoundButtonWithIcon(
                { onToggleBeat(realIndex) },
                "${realIndex + 1}",
                text = "${realIndex + 1}",
                modifier = Modifier.size(56.dp),
                contentColor = if (beat.isEnabled) Color.White else Color.DarkGray,
                containerColor = if (beat.index == currentBeatInMeasure) {
                    if (beat.isEnabled) IconButtonDefaults.filledIconButtonColors().containerColor else Color.White
                }
                else {
                    if (beat.isEnabled) Color.DarkGray else Color.LightGray
                }
            )
        }
    }
}
@Composable
fun ChangeSubdivisionRow(
    decrementSubdivision: () -> Unit,
    addSubdivision: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            20.dp, alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButtonWithIcon(
            decrementSubdivision,
            "Decrease Subdivisions",
            iconImage = Icons.Default.Remove,
            modifier = Modifier.size(56.dp)
        )

        RoundButtonWithIcon(
            addSubdivision,
            "Add Subdivisions",
            iconImage = Icons.Default.Add,
            modifier = Modifier.size(56.dp)
        )
    }
}