/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.triggerAppRestart
import org.jhaeussler.practicetracker.ui.components.DurationEntryList
import org.jhaeussler.practicetracker.ui.components.PracticeAppButton
import org.jhaeussler.practicetracker.ui.components.ScreenContainer
import org.jhaeussler.practicetracker.ui.viewModels.DbDataViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EntryListScreen(
    viewModel: DbDataViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val dbEntries by viewModel.dbEntriesUiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { destinationUri ->
            // Use the scope to move file IO off the main thread
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    viewModel.prepareForExport()

                    context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                        val dbFile = viewModel.getInternalDbFile()
                        if (dbFile.exists()) {
                            dbFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup saved!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val success = viewModel.handleImport(inputStream)

                    if (success) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Restore successful. Restarting...", Toast.LENGTH_SHORT).show()
                            triggerAppRestart(context)
                        }
                    }
                }
            }
        }
    }

    ScreenContainer(
        title = R.string.current_entry_list_screen_title
    ) {
        if (dbEntries.isNotEmpty()) {
            DurationEntryList(
                entries = dbEntries.sortedByDescending { it.date },
                deleteAction = { coroutineScope.launch {
                    viewModel.deleteEntry(it)
                } },
                modifier = Modifier.height(365.dp)
            )
        }
        else {
            Text(
                text = stringResource(R.string.no_entries_yet_txt),
                fontSize = 37.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row (
            horizontalArrangement = Arrangement.spacedBy(
                30.dp, alignment = Alignment.CenterHorizontally
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)
        ) {
            if (dbEntries.isNotEmpty()) {
                PracticeAppButton(
                    onClick = {
                        coroutineScope.launch {
                            exportLauncher.launch("database.db")
                        }
                    },
                    text = R.string.backup_db_btn_text,
                    fontSize = 21,
                    modifier = Modifier.height(60.dp).weight(0.5f)
                )
            }
            PracticeAppButton(
                onClick  = {
                    coroutineScope.launch {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                },
                text = R.string.import_db_backup,
                fontSize = 21,
                modifier = Modifier.height(60.dp).weight(0.5f)
            )
        }
    }
}