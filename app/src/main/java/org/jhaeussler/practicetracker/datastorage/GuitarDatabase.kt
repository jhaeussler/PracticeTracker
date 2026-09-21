/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PracticeTime::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GuitarDatabase : RoomDatabase() {
    abstract fun durationInHoursDao(): PracticeTimeDAO

    // You only need one instance of the RoomDatabase for the whole app,
    // so make the RoomDatabase a singleton
    companion object {

        // The value of a volatile variable is never cached, and all reads and writes are to and
        // from the main memory. These features help ensure the value of Instance is always
        // up to date and is the same for all execution threads. It means that changes made
        // by one thread to Instance are immediately visible to all other threads.
        @Volatile
        private var Instance: GuitarDatabase? = null

        fun getDatabase(context:Context): GuitarDatabase {
        // Multiple threads can potentially ask for a database instance at the same time,
        // which results in two databases instead of one. This issue is known as a race condition.
        // Wrapping the code to get the database inside a synchronized block means that only
        // one thread of execution at a time can enter this block of code, which makes sure
        // the database only gets initialized once.
           return Instance ?: synchronized(this) {
               Room.databaseBuilder(context, GuitarDatabase::class.java, "database")
                   .addMigrations(MIGRATION_2_3)
                   .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // Forces data into the main file
                   .build()
                   .also { Instance = it }
           }
        }

        private val MIGRATION_2_3 = object : Migration(startVersion = 2, endVersion = 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE duration_in_hours RENAME TO practice_time;")
            }
        }
    }
}