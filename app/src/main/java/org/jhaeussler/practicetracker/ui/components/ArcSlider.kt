package org.jhaeussler.practicetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ArcSlider(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startAngle: Float = 135f,
    sweepAngle: Float = 270f,
    strokeWidth: Dp = 10.dp,
    thumbRadius: Dp = 14.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    thumbCenterColor: Color = MaterialTheme.colorScheme.surface
) {
    // Avoid stale callbacks during ongoing drag gestures
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // Safe progress calculation (prevents division by zero)
    val range = (maxValue - minValue).takeIf { it > 0 } ?: 1
    val progress = ((value - minValue).toFloat() / range).coerceIn(0f, 1f)
    val activeSweep = progress * sweepAngle
    val currentThumbAngle = startAngle + activeSweep

    Canvas(
        modifier = modifier
            .pointerInput(minValue, maxValue, enabled, startAngle, sweepAngle)
            {
                if (!enabled) return@pointerInput

                val strokePx = strokeWidth.toPx()
                val thumbRadiusPx = thumbRadius.toPx()
                val arcPadding  = max(strokePx / 2, thumbRadiusPx)

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val circleCenter = Offset(size.width / 2f, size.width / 2f)
                    val touchRadius = (size.width - 2 * arcPadding) / 2f

                    val dx = down.position.x - circleCenter.x
                    val dy = down.position.y - circleCenter.y
                    val touchDistance = hypot(dx, dy)

                    // Ignore touches inside the inner 60% of the dial
                    if (touchDistance >= touchRadius * 0.6f) {
                        down.consume()
                        updateValueFromTouch(
                            touchPos = down.position,
                            center = circleCenter,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            minValue = minValue,
                            maxValue = maxValue,
                            onValueChange = currentOnValueChange
                        )

                        drag(down.id) { change ->
                            change.consume()
                            updateValueFromTouch(
                                touchPos = change.position,
                                center = circleCenter,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                minValue = minValue,
                                maxValue = maxValue,
                                onValueChange = currentOnValueChange
                            )
                        }
                    }
                }
            }
    ) {
        val strokePx = strokeWidth.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val padding = max(strokePx / 2, thumbRadiusPx)

        val arcDiameter = size.width - 2 * padding
        val arcSize = Size(arcDiameter, arcDiameter)
        val arcTopLeft = Offset(padding, padding)
        val radius = arcSize.width / 2f
        val circleCenter = Offset(size.width / 2f, size.width / 2f)

        // Background Arch
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Active highlighted track
        if (activeSweep > 0f) {
            drawArc(
                color = activeTrackColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Sliding Thumb / Grip Point
        val angleRad = Math.toRadians(currentThumbAngle.toDouble())
        val thumbX = circleCenter.x + radius * cos(angleRad).toFloat()
        val thumbY = circleCenter.y + radius * sin(angleRad).toFloat()
        val thumbCenter = Offset(thumbX, thumbY)

        // Outer handle circle
        drawCircle(
            color = thumbColor,
            radius = thumbRadiusPx,
            center = thumbCenter
        )
        // Inner decorative cutout/dot
        drawCircle(
            color = thumbCenterColor,
            radius = thumbRadiusPx * 0.45f,
            center = thumbCenter
        )
    }
}

/**
 * Calculates the value from a touch offset using polar coordinates
 */
private fun updateValueFromTouch(
    touchPos: Offset,
    center: Offset,
    startAngle: Float,
    sweepAngle: Float,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    val dx = touchPos.x - center.x
    val dy = touchPos.y - center.y

    // Convert Cartesian to angle in degrees (0..360)
    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angleDeg < 0f) angleDeg += 360f

    // Normalize relative to start angle (135°)
    val relativeAngle = (angleDeg - startAngle + 360f) % 360f

    // 0..sweepAngle is the active track; (sweepAngle..360) is the bottom gap
    val fraction = when {
        relativeAngle <= sweepAngle -> relativeAngle / sweepAngle
        relativeAngle > sweepAngle + (360f - sweepAngle) / 2f -> 0f  // Closer to start (clamp min)
        else -> 1f                                                   // Closer to end (clamp max)
    }

    val calculatedValue = (minValue + fraction * (maxValue - minValue)).roundToInt()
    onValueChange(calculatedValue)
}