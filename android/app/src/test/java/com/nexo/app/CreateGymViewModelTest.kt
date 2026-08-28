package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.ui.gym.CreateGymViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGymViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun joinCodePreview_fallsBackToNameDerivedPreview_whenNoCustomCodeEntered() {
        val viewModel = CreateGymViewModel(FakeBackendRepository())
        viewModel.updateGymName("Iron Temple")

        assertEquals("IRON99", viewModel.uiState.value.joinCodePreview)
    }

    @Test
    fun joinCodePreview_usesSanitizedCustomCode_whenProvided() {
        val viewModel = CreateGymViewModel(FakeBackendRepository())
        viewModel.updateGymName("Iron Temple")
        viewModel.updateCustomJoinCode("my-code!")

        assertEquals("MYCODE", viewModel.uiState.value.joinCodePreview)
    }

    @Test
    fun toggleCategory_addsThenRemoves() {
        val viewModel = CreateGymViewModel(FakeBackendRepository())

        viewModel.toggleCategory("Yoga")
        assertTrue("Yoga" in viewModel.uiState.value.selectedWorkoutTypes)

        viewModel.toggleCategory("Yoga")
        assertTrue("Yoga" !in viewModel.uiState.value.selectedWorkoutTypes)
    }

    @Test
    fun addCustomCategory_addsToAllCategories_andSelectsIt() {
        val viewModel = CreateGymViewModel(FakeBackendRepository())

        viewModel.addCustomCategory("Olympic Lifting")

        assertTrue("Olympic Lifting" in viewModel.uiState.value.allCategories)
        assertTrue("Olympic Lifting" in viewModel.uiState.value.selectedWorkoutTypes)
    }

    @Test
    fun createGym_createsIt_withGeneralFitnessFallback_whenNoCategoriesSelected() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        val viewModel = CreateGymViewModel(repo)
        viewModel.updateGymName("Iron Temple")

        viewModel.createGym()

        val created = viewModel.uiState.value.createdGym
        assertEquals("Iron Temple", created?.name)
        assertEquals(listOf("General Fitness"), created?.workoutTypes)
    }

    @Test
    fun createGym_isNoOp_whenNameIsBlank() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        val viewModel = CreateGymViewModel(repo)

        viewModel.createGym()

        assertEquals(null, viewModel.uiState.value.createdGym)
    }
}
