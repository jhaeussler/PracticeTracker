/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
