package org.jhaeussler.practicetracker.utils

import kotlin.time.Duration.Companion.seconds

data class  HoursAndMins(
    val hours: Long,
    val minutes: Long
) {
    companion object {
        fun fromSeconds(totalSeconds: Long): HoursAndMins {
            val duration = totalSeconds.seconds

            return HoursAndMins(
                hours = duration.inWholeHours,
                minutes = duration.inWholeMinutes % 60
            )
        }
    }

    fun roundedToNextHalf() : Double {
        return hours.toDouble() + when (minutes) {
            in 0..19 -> 0.0
            in 20..49 -> 0.5
            else -> 1.0
        }
    }
}

fun secondsToNiceString(seconds: Long) : String {
    val nicifySmallValues : (Long) -> String = { value ->
        if(value < 10) "0$value" else "$value"
    }

    if(seconds < 60) {
        return "${nicifySmallValues(seconds)} sec."
    }
    else {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        if(minutes < 60) {
            return "$minutes:${nicifySmallValues(remainingSeconds)} min."
        }
        else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            return "$hours:${nicifySmallValues(remainingMinutes)}:" +
                   "${nicifySmallValues(remainingSeconds)} h"
        }
    }
}