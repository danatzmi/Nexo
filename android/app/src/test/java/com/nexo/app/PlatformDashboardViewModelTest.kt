package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.platform.PlatformDashboardViewModel
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
class PlatformDashboardViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_populatesGymsAndUsers() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        repo.seedPlatformUser(PlatformUser(id = "user-1", firstName = "Dana", lastName = "Cohen", email = "dana@example.com"))

        val viewModel = PlatformDashboardViewModel(repo)

        assertEquals(1, viewModel.uiState.value.gyms.size)
        assertEquals(1, viewModel.uiState.value.users.size)
    }

    @Test
    fun confirmDeleteGym_removesTheGymFromTheList() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        val viewModel = PlatformDashboardViewModel(repo)

        viewModel.requestDeleteGym(viewModel.uiState.value.gyms.first())
        viewModel.confirmDeleteGym()

        assertTrue(viewModel.uiState.value.gyms.isEmpty())
        assertEquals(null, viewModel.uiState.value.gymToDelete)
    }

    @Test
    fun dismissDeleteGymPrompt_doesNotDeleteTheGym() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        val viewModel = PlatformDashboardViewModel(repo)

        viewModel.requestDeleteGym(viewModel.uiState.value.gyms.first())
        viewModel.dismissDeleteGymPrompt()

        assertEquals(1, viewModel.uiState.value.gyms.size)
        assertEquals(null, viewModel.uiState.value.gymToDelete)
    }

    @Test
    fun updateUserRole_promotesToAdmin() = runTest {
        val repo = FakeBackendRepository()
        repo.seedPlatformUser(PlatformUser(id = "user-1", firstName = "Dana", lastName = "Cohen", email = "dana@example.com"))
        val viewModel = PlatformDashboardViewModel(repo)

        viewModel.updateUserRole(viewModel.uiState.value.users.first(), PlatformRole.ADMIN)

        assertEquals(PlatformRole.ADMIN, viewModel.uiState.value.users.first().role)
    }
}
