/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun RoundButtonWithIcon(
    onClick: () -> Unit,
    description: String,
    enabled: Boolean = true,
    iconImage: ImageVector? = null,
    text: String? = null,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors()
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.size(56.dp)
    ) {
        if (iconImage != null) {
            Icon(
                imageVector = iconImage,
                contentDescription = description
            )
        }
        else if (text != null) {
            Text(
                text = text
            )
        }
    }
}