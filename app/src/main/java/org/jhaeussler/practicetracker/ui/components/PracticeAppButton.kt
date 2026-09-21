package org.jhaeussler.practicetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PracticeAppButton(
    onClick: () -> Unit,
    @StringRes text: Int,
    modifier: Modifier = Modifier,
    fontSize: Int = 17
) {
    PracticeAppButtonRawString(
        onClick,
        stringResource(text),
        modifier,
        fontSize
    )
}

@Composable
fun PracticeAppButtonRawString(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 17
) {
    Button(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}