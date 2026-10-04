/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconImage: ImageVector? = null,
    text: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
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

/**
 * Add onClick behavior via modifier
 */
@Composable
fun SimpleRoundButtonWithIcon(
    modifier: Modifier,
    icon: ImageVector,
    description: String = "",
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = CircleShape,
        color = color,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description)
        }
    }
}