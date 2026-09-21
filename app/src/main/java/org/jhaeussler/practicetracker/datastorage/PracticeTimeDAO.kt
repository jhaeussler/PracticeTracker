/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PracticeTimeDAO {
    // Mark the function with the suspend keyword to let it run on a separate thread.
    // The database operations can take a long time to execute,
    // so they need to run on a separate thread.
    // Room doesn't allow database access on the main thread.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(practiceTime: PracticeTime)

    @Update
    suspend fun update(practiceTime: PracticeTime)

    @Delete
    suspend fun delete(practiceTime: PracticeTime)

    // Because of the Flow return type, Room also runs the query on the background thread.
    // You don't need to explicitly make it a suspend function and call it inside a coroutine scope.
    @Query("SELECT * from practice_time WHERE id = :id")
    fun getValue(id: Int): Flow<PracticeTime>

    @Query("SELECT * from practice_time ORDER BY date ASC")
    fun getAllValues(): Flow<List<PracticeTime>>

    @Query("SELECT * from practice_time WHERE date = :date")
    suspend fun getEntriesOnDate(date: LocalDate): List<PracticeTime>
}