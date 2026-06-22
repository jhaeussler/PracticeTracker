package org.jhaeussler.practicetracker

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.jhaeussler.practicetracker.ui.viewModels.DbDataViewModel
import org.jhaeussler.practicetracker.ui.viewModels.EnterValueOnDateViewModel
import org.jhaeussler.practicetracker.ui.viewModels.statisticsData.StatisticsViewModel
import org.jhaeussler.practicetracker.ui.viewModels.OverviewViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        // Initializer for ItemEditViewModel
        initializer {
            val application =
                checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])

            OverviewViewModel(
                practiceTrackerApplication().container.practiceTimeRepository,
                application
            )
        }
        initializer {
            EnterValueOnDateViewModel(practiceTrackerApplication().container.practiceTimeRepository)
        }
        initializer {
            StatisticsViewModel(practiceTrackerApplication().container.practiceTimeRepository)
        }
        initializer {
            DbDataViewModel(practiceTrackerApplication().container.practiceTimeRepository)
        }
    }
}

/**
 * Extension function to queries for [Application] object and returns an instance of
 * [PracticeTrackerApplication].
 */
fun CreationExtras.practiceTrackerApplication(): PracticeTrackerApplication =
    (this[AndroidViewModelFactory.APPLICATION_KEY] as PracticeTrackerApplication)