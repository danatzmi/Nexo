package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now = System.currentTimeMillis()

    @Test
    fun load_populatesProfileGymAndRoleFields() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedPlatformRole("member-1", PlatformRole.USER)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.COACH, "member-1")

        val viewModel = ProfileViewModel(repo, "gym-1")

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Dana Cohen", state.fullName)
        assertEquals("dana@example.com", state.email)
        assertEquals(PlatformRole.USER, state.platformRole)
        assertEquals("Iron Temple", state.gymName)
        assertEquals("Coach", state.roleDisplayName)
        assertEquals(UserRole.COACH, state.userRole)
    }

    @Test
    fun load_populatesActivePlans_forAMember() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 4, expiresAtMillis = Long.MAX_VALUE / 2))

        val viewModel = ProfileViewModel(repo, "gym-1")

        val state = viewModel.uiState.value
        assertEquals(UserRole.MEMBER, state.userRole)
        assertEquals(listOf("plan-1"), state.activePlans.map { it.id })
        assertEquals(4, state.activePlans.first().remainingCredits)
    }

    @Test
    fun refresh_updatesActivePlansFromExternalMutation() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")

        val viewModel = ProfileViewModel(repo, "gym-1")
        assertTrue(viewModel.uiState.value.activePlans.isEmpty())

        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        viewModel.refresh()

        assertEquals(listOf("plan-1"), viewModel.uiState.value.activePlans.map { it.id })
    }

    @Test
    fun load_reflectsPlatformAdminRole() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"
        repo.seedProfile("admin-1", Member(id = "admin-1", fullName = "Admin User", email = "admin@example.com"))
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)

        val viewModel = ProfileViewModel(repo, "gym-1")

        assertEquals(PlatformRole.ADMIN, viewModel.uiState.value.platformRole)
    }

    @Test
    fun load_populatesUpcomingBookings_sortedByStartTime_excludingPastClasses() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        val past = GymClass(id = "class-past", title = "Yesterday's WOD", coach = "Alex", startTimeMillis = now - 100_000, capacity = 10, currentAttendees = 1)
        val soon = GymClass(id = "class-soon", title = "Tonight's WOD", coach = "Alex", startTimeMillis = now + 50_000, capacity = 10, currentAttendees = 1)
        val later = GymClass(id = "class-later", title = "Tomorrow's WOD", coach = "Alex", startTimeMillis = now + 200_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", past)
        repo.seedClass("gym-1", soon)
        repo.seedClass("gym-1", later)
        repo.seedBooking("gym-1", "class-past", "member-1")
        repo.seedBooking("gym-1", "class-soon", "member-1")
        repo.seedBooking("gym-1", "class-later", "member-1")

        val viewModel = ProfileViewModel(repo, "gym-1")

        assertEquals(listOf("class-soon", "class-later"), viewModel.uiState.value.upcomingBookings.map { it.id })
    }

    @Test
    fun cancelBooking_removesTheClassFromUpcomingBookings() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedBooking("gym-1", "class-1", "member-1")

        val viewModel = ProfileViewModel(repo, "gym-1")
        viewModel.cancelBooking("class-1")

        val state = viewModel.uiState.value
        assertTrue(state.upcomingBookings.isEmpty())
        assertNull(state.actionInProgressClassId)
    }

    @Test
    fun updateProfilePicture_succeeds_updatesBase64AndClearsUploadingFlag() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        val viewModel = ProfileViewModel(repo, "gym-1")
        viewModel.updateProfilePicture("base64-jpeg-data")

        val state = viewModel.uiState.value
        assertEquals("base64-jpeg-data", state.profilePicBase64)
        assertEquals(false, state.isUploadingPhoto)
        assertEquals("base64-jpeg-data", repo.fetchMyProfile()?.profilePicBase64)
    }

    @Test
    fun updateProfilePicture_surfacesErrorMessage_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        val viewModel = ProfileViewModel(repo, "gym-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.updateProfilePicture("base64-jpeg-data")

        val state = viewModel.uiState.value
        assertNull(state.profilePicBase64)
        assertEquals(false, state.isUploadingPhoto)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }
}
