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
        repo.signedInUID = "admin-1"
        val viewModel = CreateGymViewModel(repo)
        viewModel.updateGymName("Iron Temple")
        viewModel.updateOwnerFirstName("Dana")
        viewModel.updateOwnerLastName("Cohen")
        viewModel.updateOwnerEmail("dana@example.com")
        viewModel.updateOwnerPassword("password123")

        viewModel.createGym()

        val created = viewModel.uiState.value.createdGym
        assertEquals("Iron Temple", created?.name)
        assertEquals(listOf("General Fitness"), created?.workoutTypes)
        assertEquals("dana@example.com", repo.fetchTeam(created!!.id).first().email)
    }

    @Test
    fun createGym_isNoOp_whenNameIsBlank() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"
        val viewModel = CreateGymViewModel(repo)
        viewModel.updateOwnerFirstName("Dana")
        viewModel.updateOwnerEmail("dana@example.com")
        viewModel.updateOwnerPassword("password123")

        viewModel.createGym()

        assertEquals(null, viewModel.uiState.value.createdGym)
    }

    @Test
    fun createGym_isNoOp_whenOwnerFieldsAreIncomplete() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"
        val viewModel = CreateGymViewModel(repo)
        viewModel.updateGymName("Iron Temple")

        viewModel.createGym()

        assertEquals(null, viewModel.uiState.value.createdGym)
    }
}
