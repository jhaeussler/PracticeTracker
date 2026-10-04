package org.jhaeussler.practicetracker.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 350L,
    minIntervalMillis: Long = 40L,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()

    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onPress = {
                currentOnClick()
                val job = coroutineScope.launch {
                    delay(initialDelayMillis.milliseconds)
                    var currentInterval = 120L
                    while (isActive) {
                        currentOnClick()
                        delay(currentInterval.milliseconds)
                        // Gradually accelerate while holding
                        currentInterval = (currentInterval * 0.90f).toLong()
                            .coerceAtLeast(minIntervalMillis)
                    }
                }
                tryAwaitRelease()
                job.cancel()
            }
        )
    }
}
