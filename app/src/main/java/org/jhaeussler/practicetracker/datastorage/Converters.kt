package org.jhaeussler.practicetracker.datastorage

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import java.time.LocalDate

object Converters {
    @TypeConverter
    fun fromTimestamp(value: String): LocalDate {
        return LocalDate.parse(value)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @TypeConverter
    fun dateToTimestamp(date: LocalDate): String {
        return date.toString()
    }
}