/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker

import android.app.Application
import org.jhaeussler.practicetracker.datastorage.AppContainer
import org.jhaeussler.practicetracker.datastorage.AppDataContainer

class PracticeTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
    }
}