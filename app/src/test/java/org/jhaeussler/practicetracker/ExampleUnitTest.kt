package org.jhaeussler.practicetracker
//
//import org.junit.Test
//
//import org.junit.Assert.*
//
//import app.cash.turbine.test
//import io.mockk.coEvery
//import io.mockk.mockk
//import kotlinx.coroutines.test.runTest
//import org.junit.Assert.assertEquals
//import org.junit.Rule
//import org.junit.Test
//
//class UserViewModelTest {
//
//    @get:Rule
//    val mainDispatcherRule = MainDispatcherRule()
//
//    private val repository: UserRepository = mockk()
//    private lateinit var viewModel: UserViewModel
//
//    @Test
//    fun `initial state is empty`() = runTest {
//        // We initialize inside the test or a @Before block
//        viewModel = UserViewModel(repository)
//
//        assertEquals("", viewModel.uiState.value.userName)
//    }
//
//    @Test
//    fun `loadUser updates state with name from repository`() = runTest {
//        // 1. Arrange (Mock the dependency)
//        coEvery { repository.getName() } returns "Gemini"
//        viewModel = UserViewModel(repository)
//
//        // 2. Act
//        viewModel.loadUser()
//
//        // 3. Assert (Using Turbine to test StateFlow)
//        viewModel.uiState.test {
//            assertEquals("Gemini", awaitItem().userName)
//        }
//    }
//}
//
///**
// * Example local unit test, which will execute on the development machine (host).
// *
// * See [testing documentation](http://d.android.com/tools/testing).
// */
//class ExampleUnitTest {
//    @Test
//    fun addition_isCorrect() {
//        assertEquals(4, 2 + 2)
//    }
//}