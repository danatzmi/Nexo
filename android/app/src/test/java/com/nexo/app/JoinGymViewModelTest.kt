package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.gym.JoinGymViewModel
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
class JoinGymViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun filteredGyms_matchesNameOrCityOrJoinCode() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1", city = "Tel Aviv", joinCode = "IRON99"), UserRole.OWNER, "owner-1")
        repo.seedGym(Gym(id = "gym-2", name = "Zen Studio", ownerUID = "owner-2", city = "Haifa", joinCode = "ZEN01"), UserRole.OWNER, "owner-2")

        val viewModel = JoinGymViewModel(repo)
        viewModel.updateSearchText("tel aviv")

        assertEquals(listOf("gym-1"), viewModel.uiState.value.filteredGyms.map { it.id })
    }

    @Test
    fun joinGym_enrollsAsAMember_andSetsJoinedGym() = runTest {
        val repo = FakeBackendRepository()
        // Seeded via createGymForCurrentUser (not raw seedGym) so the join-code
        // lookup joinGymByCode relies on is actually registered.
        repo.signedInUID = "owner-1"
        val gym = repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())
        repo.signedInUID = "member-1"

        val viewModel = JoinGymViewModel(repo)
        viewModel.joinGym(repo.fetchAvailableGyms().first { it.id == gym.id })

        assertEquals(gym.id, viewModel.uiState.value.joinedGym?.id)
        assertEquals(UserRole.MEMBER, repo.fetchMyGyms().first { it.first.id == gym.id }.second)
    }

    @Test
    fun joinGym_surfacesError_whenGymHasNoJoinCode() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1", joinCode = null), UserRole.OWNER, "owner-1")
        repo.signedInUID = "member-1"

        val viewModel = JoinGymViewModel(repo)
        viewModel.joinGym(repo.fetchAvailableGyms().first())

        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun updateCodeInput_looksUpTheCode_onceFourCharactersLong() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())

        val viewModel = JoinGymViewModel(repo)
        viewModel.updateCodeInput("iron99")

        assertEquals("Iron Temple", viewModel.uiState.value.codeLookupResult?.name)
    }

    @Test
    fun submitCode_joinsTheResolvedGym() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())

        repo.signedInUID = "member-1"
        val viewModel = JoinGymViewModel(repo)
        viewModel.updateCodeInput("IRON99")
        viewModel.submitCode()

        assertEquals("Iron Temple", viewModel.uiState.value.joinedGym?.name)
    }
}
