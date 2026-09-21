/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate


/**
 * Entity data class represents a single row in the database.
 */
@Entity(tableName = "practice_time")
data class PracticeTime(
    // Assign the id a default value of 0, which is necessary for the id to auto generate id values.
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val value: Double,
    val date: LocalDate
)
