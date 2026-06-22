package org.jhaeussler.practicetracker.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import kotlin.collections.listOf

class DbDataViewModel (
    private val practiceTimeRepository: PracticeTimeRepository
) : ViewModel() {

    val dbEntriesUiState: StateFlow<List<PracticeTime>> =
        practiceTimeRepository.getAllPracticeTimesStream()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = listOf()
            )

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }

    suspend fun deleteEntry (entry: PracticeTime) {
        practiceTimeRepository.deletePracticeTime(entry)
    }

    fun prepareForExport() {
        viewModelScope.launch {
            practiceTimeRepository.makeSureDbIsUpToDate()
        }
    }

    fun getInternalDbFile(): File {
        return practiceTimeRepository.getDatabaseFile()
    }

    fun handleImport(inputStream: InputStream) : Boolean {
        return practiceTimeRepository.importDatabase(inputStream)
    }
}