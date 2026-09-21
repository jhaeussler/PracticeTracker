/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/**
 * Repository that provides insert, update, delete, and retrieve of [PracticeTime] from a given data source.
 */
interface PracticeTimeRepository {
    /**
     * Retrieve all the items from the the given data source.
     */
    fun getAllPracticeTimesStream(): Flow<List<PracticeTime>>

    /**
     * Retrieve an item from the given data source that matches with the [id].
     */
    fun getPracticeTimeStream(id: Int): Flow<PracticeTime?>

    /**
     * Insert item in the data source
     */
    suspend fun insertPracticeTime(item: PracticeTime)

    /**
     * Delete item from the data source
     */
    suspend fun deletePracticeTime(item: PracticeTime)

    /**
     * Update item in the data source
     */
    suspend fun updatePracticeTime(item: PracticeTime)

    suspend fun getEntriesOnDate(date: LocalDate): List<PracticeTime>

    suspend fun accumulatePracticeTime(item: PracticeTime)

    // DB backup related methods

    suspend fun makeSureDbIsUpToDate()

    fun closeDatabase()

    fun getDatabaseFile(): File

    fun importDatabase(inputStream: InputStream) : Boolean
}
