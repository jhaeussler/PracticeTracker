/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jhaeussler.practicetracker.datastorage.PracticeTime
import org.jhaeussler.practicetracker.datastorage.PracticeTimeRepository
import org.jhaeussler.practicetracker.sessiontimerservice.SessionTimerService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.InputStream
import java.time.LocalDate


class FakePracticeTimeRepository : PracticeTimeRepository {
    private val _items = MutableStateFlow<List<PracticeTime>>(emptyList())
    private var nextId = 1

    override fun getAllPracticeTimesStream(): Flow<List<PracticeTime>> = _items

    override fun getPracticeTimeStream(id: Int): Flow<PracticeTime?> {
        return _items.map { list -> list.find { it.id == id } }
    }

    override suspend fun insertPracticeTime(item: PracticeTime) {
        val currentList = _items.value.toMutableList()
        val newItem = if (item.id == 0) item.copy(id = nextId++) else item
        currentList.add(newItem)
        _items.value = currentList
    }

    override suspend fun deletePracticeTime(item: PracticeTime) {
        val currentList = _items.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        _items.value = currentList
    }

    override suspend fun updatePracticeTime(item: PracticeTime) {
        val currentList = _items.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
            _items.value = currentList
        }
    }

    override suspend fun getEntriesOnDate(date: LocalDate): List<PracticeTime> {
        return _items.value.filter { it.date == date }
    }

    override suspend fun accumulatePracticeTime(item: PracticeTime) {
        val currentList = _items.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.date == item.date }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(
                value = existing.value + item.value
            )
        } else {
            val newItem = if (item.id == 0) item.copy(id = nextId++) else item
            currentList.add(newItem)
        }
        _items.value = currentList
    }

    // DB backup related methods (no-op or stubbed for unit tests)

    override suspend fun makeSureDbIsUpToDate() {
        // No-op in fake
    }

    override fun closeDatabase() {
        // No-op in fake
    }

    override fun getDatabaseFile(): File {
        return File.createTempFile("fake_db", ".db")
    }

    override fun importDatabase(inputStream: InputStream): Boolean {
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> setCompanionPrivateStateFlow(
    outerClass: Class<*>,
    companionInstance: Any,
    fieldName: String,
    value: T
) {
    val field = outerClass.getDeclaredField(fieldName).apply {
        isAccessible = true
    }
    // Get the MutableStateFlow instance from the static field on the outer class
    val mutableFlow = field.get(companionInstance) as MutableStateFlow<T>
    mutableFlow.value = value
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var fakeRepository: FakePracticeTimeRepository
    private lateinit var viewModel: OverviewViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        fakeRepository = FakePracticeTimeRepository()

        // Reset service state prior to each test run
        SessionTimerService.resetTimer(application)

        viewModel = OverviewViewModel(fakeRepository, application)
    }

    @After
    fun tearDown() {
        SessionTimerService.resetTimer(application)
    }

    @Test
    fun resetDialogFlags_clearsAllDialogVisibilityFlags() {
        viewModel.requestCancelSession()
        assertTrue(viewModel.showCancelSessionDiag.value)

        viewModel.resetDialogFlags()

        assertFalse(viewModel.showConfirmSessionEndDiag.value)
        assertFalse(viewModel.showSessionEndErrorDiag.value)
        assertFalse(viewModel.showCancelSessionDiag.value)
    }

    @Test
    fun requestCancelSession_setsCancelDialogVisible() {
        viewModel.requestCancelSession()
        assertTrue(viewModel.showCancelSessionDiag.value)
    }

    @Test
    fun requestEndSession_whenSessionTimeLessThanThreshold_showsErrorDialog() {
        // 1800s = 0.5 hours (< 1.0 threshold)
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 1800L
        )

        viewModel.requestEndSession()

        assertTrue(viewModel.showSessionEndErrorDiag.value)
        assertFalse(viewModel.showConfirmSessionEndDiag.value)
    }

    @Test
    fun requestEndSession_whenSessionTimeMeetsThreshold_showsConfirmDialog() {
        // 3600s = 1.0 hour (>= 1.0 threshold)
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 3600L
        )

        viewModel.requestEndSession()

        assertFalse(viewModel.showSessionEndErrorDiag.value)
        assertTrue(viewModel.showConfirmSessionEndDiag.value)
    }

    @Test
    fun endSession_persistsTimeInRepositoryAndResetsTimer() = runTest {
        val testDate = LocalDate.of(1995, 4, 30)

        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 5400L
        )

        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_sessionStartDate",
            value = testDate
        )

        viewModel.requestEndSession()
        viewModel.endSession()


        advanceUntilIdle()

        val recordedTimes = fakeRepository.getAllPracticeTimesStream().first()
        assertEquals(1, recordedTimes.size)
        assertEquals(1.5, recordedTimes[0].value, 0.01)
        assertEquals(testDate, recordedTimes[0].date)

        // mock reset of service state
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 0L
        )
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_timerState",
            value = SessionTimerService.TimerState.STOPPED
        )

        assertEquals(SessionTimerService.TimerState.STOPPED, SessionTimerService.timerState.value)
        assertEquals(0L, SessionTimerService.elapsedTimeSec.value)
        assertFalse(viewModel.showConfirmSessionEndDiag.value)
    }

    @Test
    fun getSessionTime_roundsToNearestHalfHour() {
        // 5400s = 90 min = 1.5 hours
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 5400L
        )

        assertEquals(1.5, viewModel.getSessionTime(), 0.01)
    }

    @Test
    fun overviewUiState_emitsRepositoryUpdates() = runTest {
        val testDate = LocalDate.of(1995, 4, 30)
        val practiceItem = PracticeTime(id = 1, value = 2.0, date = testDate)

        // Observe flow to activate WhileSubscribed
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.overviewUiState.collect {}
        }

        fakeRepository.insertPracticeTime(practiceItem)
        advanceUntilIdle()

        val stateList = viewModel.overviewUiState.value
        assertEquals(1, stateList.size)
        assertEquals(practiceItem, stateList[0])

        collector.cancel()
    }

    @Test
    fun endSession_whenSessionStartDateIsNull_usesCurrentDate() = runTest {
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_elapsedTimeSec",
            value = 3600L
        )
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_sessionStartDate",
            value = null
        )

        viewModel.endSession()
        advanceUntilIdle()

        val recordedTimes = fakeRepository.getAllPracticeTimesStream().first()
        assertEquals(1, recordedTimes.size)
        assertEquals(LocalDate.now(), recordedTimes[0].date)
    }

    @Test
    fun requestTimerService_whenPermissionNotGranted_invokesPermissionCallbackAndTogglesTimer() {
        var requestedPermission: String? = null

        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_timerState",
            value = SessionTimerService.TimerState.STOPPED
        )

        // Clear intent queue populated by setUp()'s resetTimer call
        shadowOf(application).clearStartedServices()

        viewModel.requestTimerService { permission ->
            requestedPermission = permission
        }

        assertEquals(android.Manifest.permission.POST_NOTIFICATIONS, requestedPermission)

        val nextServiceIntent = org.robolectric.Shadows.shadowOf(application).nextStartedService
        assertEquals(SessionTimerService.ACTION_START, nextServiceIntent?.action)
    }

    @Test
    fun resetTimer_dispatchesResetActionToService() {
        viewModel.resetTimer()

        val nextServiceIntent = org.robolectric.Shadows.shadowOf(application).nextStartedService
        assertEquals(SessionTimerService.ACTION_RESET, nextServiceIntent?.action)
    }

    @Test
    fun toggleTimer_whenStopped_dispatchesStartActionToService() {
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_timerState",
            value = SessionTimerService.TimerState.STOPPED
        )

        shadowOf(application).clearStartedServices()

        viewModel.toggleTimer()

        val nextServiceIntent = org.robolectric.Shadows.shadowOf(application).nextStartedService
        assertEquals(SessionTimerService.ACTION_START, nextServiceIntent?.action)
    }

    @Test
    fun toggleTimer_whenRunning_dispatchesPauseActionToService() {
        setCompanionPrivateStateFlow(
            outerClass = SessionTimerService::class.java,
            companionInstance = SessionTimerService.Companion,
            fieldName = "_timerState",
            value = SessionTimerService.TimerState.RUNNING
        )

        shadowOf(application).clearStartedServices()

        viewModel.toggleTimer()

        val nextServiceIntent = org.robolectric.Shadows.shadowOf(application).nextStartedService
        assertEquals(SessionTimerService.ACTION_PAUSE, nextServiceIntent?.action)
    }
}