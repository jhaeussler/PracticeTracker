/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.time.LocalDate

class OfflinePracticeTimeRepository(private val context: Context) : PracticeTimeRepository {

    private val database = GuitarDatabase.getDatabase(context)

    override fun getAllPracticeTimesStream(): Flow<List<PracticeTime>> = database.durationInHoursDao().getAllValues()

    override fun getPracticeTimeStream(id: Int): Flow<PracticeTime?> = database.durationInHoursDao().getValue(id)

    override suspend fun insertPracticeTime(item: PracticeTime) = database.durationInHoursDao().insert(item)

    override suspend fun deletePracticeTime(item: PracticeTime) = database.durationInHoursDao().delete(item)

    override suspend fun updatePracticeTime(item: PracticeTime) = database.durationInHoursDao().update(item)

    override suspend fun getEntriesOnDate(date: LocalDate) = database.durationInHoursDao().getEntriesOnDate(date)

    override suspend fun accumulatePracticeTime(item: PracticeTime) {
        val existingEntriesOnDate = getEntriesOnDate(item.date)
        if (existingEntriesOnDate.isEmpty()) {
            insertPracticeTime(item)
        } else {
            val currentAmount = existingEntriesOnDate.sumOf { it.value }
            if(existingEntriesOnDate.count() > 1) {
                Log.w("DbBackup", "More then one entry on date ${item.date}. Merging existing entries.")
                existingEntriesOnDate.forEach{ deletePracticeTime(it) }
                insertPracticeTime(PracticeTime(0, currentAmount + item.value, item.date))
            } else {
                val updatedEntry = PracticeTime(
                    existingEntriesOnDate.first().id,
                    currentAmount + item.value,
                    item.date
                )
                updatePracticeTime(updatedEntry)
            }
        }
    }

    override suspend fun makeSureDbIsUpToDate() {
        try {
            val db = database.openHelper.writableDatabase
            // VACUUM rebuilds the database into a single, clean file
            db.query("VACUUM").close()

            val dbFile = context.getDatabasePath("database")
        } catch (e: Exception) {
            Log.e("DbBackup", "Vacuum failed", e)
        }
    }

    override fun closeDatabase() {
        if (database.isOpen) {
            database.close()
        }
    }

    override fun getDatabaseFile(): File {
        return context.getDatabasePath("database")
    }

    override fun importDatabase(inputStream: InputStream) : Boolean {
        return try {
            val dbFile = getDatabaseFile()

            if (database.isOpen) {
                database.close()
            }

            dbFile.outputStream().use { outStream ->
                inputStream.copyTo(outStream)
            }

            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            true
        } catch (e: Exception) {
            Log.e("DbBackup", "Import failed", e)
            false
        }
    }
}
