/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.datastorage

import android.content.Context

interface AppContainer {
    val practiceTimeRepository: PracticeTimeRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val practiceTimeRepository: PracticeTimeRepository by lazy {
        OfflinePracticeTimeRepository(context = context)
    }
}
