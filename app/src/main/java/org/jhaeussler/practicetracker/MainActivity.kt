/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.jhaeussler.practicetracker.ui.PracticeTrackerMain
import org.jhaeussler.practicetracker.ui.theme.PracticeTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PracticeTrackerTheme {
                PracticeTrackerMain()
            }
        }
    }
}

fun triggerAppRestart(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)

    if (intent != null) {
        val restartIntent = Intent.makeRestartActivityTask(intent.component)
        context.startActivity(restartIntent)

        if (context is Activity) {
            context.finishAffinity()
        }

        // make sure memory is actually cleared
        Runtime.getRuntime().exit(0)
    }
}