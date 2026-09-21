package org.jhaeussler.practicetracker.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import org.jhaeussler.practicetracker.AppViewModelProvider
import org.jhaeussler.practicetracker.R
import org.jhaeussler.practicetracker.ui.screens.OverviewScreen
import org.jhaeussler.practicetracker.ui.screens.EnterValueOnDateScreen
import org.jhaeussler.practicetracker.ui.screens.datascreens.GeneralStatisticsScreen
import org.jhaeussler.practicetracker.ui.screens.datascreens.AllTimeDataScreen
import org.jhaeussler.practicetracker.ui.screens.datascreens.CurrentWeekDataScreen
import org.jhaeussler.practicetracker.ui.screens.EntryListScreen
import org.jhaeussler.practicetracker.ui.screens.MetronomeScreen
import org.jhaeussler.practicetracker.ui.screens.datascreens.ExtraInfoScreen
import org.jhaeussler.practicetracker.ui.screens.datascreens.WeeklyDataScreen
import org.jhaeussler.practicetracker.ui.viewModels.statisticsData.StatisticsViewModel

enum class AppDestination(val title: Int) {
    OverviewScreen(title = R.string.overview_destination),
    EnterTimeScreen(title =  R.string.add_time_destination),
    StatisticsRoute(title = R.string.statistics_view_model_route),
    GeneralStatisticsScreen(title = R.string.general_statistics_destination),
    AllTimeStatsScreen(title = R.string.all_time_stats_destination),
    CurrentWeekStatsScreen(title = R.string.current_week_stats_destination),
    WeeklyStatsScreen(title = R.string.weekly_stats_destination),
    ExtraInfoScreen(title = R.string.extra_info_destination),
    EntryListScreen(title = R.string.entry_list_destination),

    MetronomeScreen(title = R.string.metronom_screen)
}

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun getStatisticsViewModel(navController: NavController): StatisticsViewModel {
    val parentEntry = remember(AppDestination.StatisticsRoute.name) {
        navController.getBackStackEntry(AppDestination.StatisticsRoute.name)
    }

    val sharedViewModel = viewModel<StatisticsViewModel>(
        factory = AppViewModelProvider.Factory,
        viewModelStoreOwner = parentEntry
    )

    return sharedViewModel
}

fun NavGraphBuilder.statisticsGraph(navController: NavController) {
    navigation(
        startDestination = AppDestination.GeneralStatisticsScreen.name,
        route = AppDestination.StatisticsRoute.name
    ) {
        composable(AppDestination.GeneralStatisticsScreen.name) { backStackEntry ->
            GeneralStatisticsScreen(
                navigateToAllTimeStatScreen = {
                    navController.navigate(AppDestination.AllTimeStatsScreen.name)
                },
                navigateToCurrentWeekStatScreen = {
                    navController.navigate(AppDestination.CurrentWeekStatsScreen.name)
                },
                navigateToWeeklyStatScreen = {
                    navController.navigate(AppDestination.WeeklyStatsScreen.name)
                },
                navigateToExtraInfoScreen = {
                    navController.navigate(AppDestination.ExtraInfoScreen.name)
                },
                navigateToEntryListScreen = {
                    navController.navigate(AppDestination.EntryListScreen.name)
                },
                viewModel = getStatisticsViewModel(navController)
            )
        }
        composable(AppDestination.AllTimeStatsScreen.name) { backStackEntry->
            AllTimeDataScreen(
                viewModel = getStatisticsViewModel(navController)
            )
        }
        composable(AppDestination.CurrentWeekStatsScreen.name) { backStackEntry->
            CurrentWeekDataScreen(
                viewModel = getStatisticsViewModel(navController)
            )
        }
        composable(AppDestination.WeeklyStatsScreen.name) { backStackEntry->
            WeeklyDataScreen(
                viewModel = getStatisticsViewModel(navController)
            )
        }
        composable(AppDestination.ExtraInfoScreen.name) { backStackEntry->
            ExtraInfoScreen(
                viewModel = getStatisticsViewModel(navController)
            )
        }
        composable(AppDestination.EntryListScreen.name) { backStackEntry->
            EntryListScreen()
        }
    }
}

@Composable
fun PracticeAppNavHost(navController: NavHostController)
{
    NavHost(
        navController = navController,
        startDestination = AppDestination.OverviewScreen.name)
    {
        composable(route = AppDestination.OverviewScreen.name) {
            OverviewScreen (
                onAddTimeClicked = { navController.navigate(AppDestination.EnterTimeScreen.name) },
                onToMetronomeScreenClicked = { navController.navigate(AppDestination.MetronomeScreen.name) },
                onStatisticsBtnClicked = {
                    navController.navigate(AppDestination.StatisticsRoute.name)
                }
            )
        }
        composable(route = AppDestination.EnterTimeScreen.name) {
            EnterValueOnDateScreen (
                navigateBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.MetronomeScreen.name) { backStackEntry->
            MetronomeScreen(
                navigateBack = { navController.navigateUp() },
            )
        }
        statisticsGraph(navController)
    }
}
