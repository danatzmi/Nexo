package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.ui.admin.MemberDetailViewModel
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
class MemberDetailViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now = System.currentTimeMillis()
    private val member = GymMember(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com")

    @Test
    fun load_populatesBookingsSplitByPastAndUpcoming_activePlansAndAvailablePlans() = runTest {
        val repo = FakeBackendRepository()
        val past = GymClass(id = "past", title = "Yesterday's WOD", coach = "Alex", startTimeMillis = now - 100_000, capacity = 10, currentAttendees = 1)
        val upcoming = GymClass(id = "upcoming", title = "Tomorrow's WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", past)
        repo.seedClass("gym-1", upcoming)
        repo.seedBooking("gym-1", "past", "member-1")
        repo.seedBooking("gym-1", "upcoming", "member-1")
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.seedMembershipPlan("gym-1", MembershipPlan(id = "plan-template-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10))))

        val viewModel = MemberDetailViewModel(repo, "gym-1", member)

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(listOf("upcoming"), state.upcomingBookings.map { it.id })
        assertEquals(listOf("past"), state.pastBookings.map { it.id })
        assertEquals(listOf("plan-1"), state.activePlans.map { it.id })
        assertEquals(listOf("plan-template-1"), state.availablePlans.map { it.id })
    }

    @Test
    fun grantPlan_addsToActivePlans() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = MemberDetailViewModel(repo, "gym-1", member)

        val plan = MembershipPlan(id = "plan-template-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        viewModel.grantPlan(plan)

        val state = viewModel.uiState.value
        assertEquals(1, state.activePlans.size)
        assertEquals("10 Credits", state.activePlans.first().planName)
        assertEquals(10, state.activePlans.first().remainingCredits)
    }

    @Test
    fun grantPlan_withCustomExpiresAtMillis_appliesItToTheGrantedItem() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = MemberDetailViewModel(repo, "gym-1", member)
        val customExpiresAt = now + 86_400_000L * 60

        val plan = MembershipPlan(id = "plan-template-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        viewModel.grantPlan(plan, customExpiresAtMillis = customExpiresAt)

        assertEquals(customExpiresAt, viewModel.uiState.value.activePlans.first().expiresAtMillis)
    }

    @Test
    fun revokeActivePlan_removesFromActivePlans() = runTest {
        val repo = FakeBackendRepository()
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))

        val viewModel = MemberDetailViewModel(repo, "gym-1", member)
        viewModel.revokeActivePlan("plan-1")

        assertTrue(viewModel.uiState.value.activePlans.isEmpty())
    }

    @Test
    fun cancelBooking_removesTheClassFromBookings() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedBooking("gym-1", "class-1", "member-1")

        val viewModel = MemberDetailViewModel(repo, "gym-1", member)
        viewModel.cancelBooking("class-1")

        assertTrue(viewModel.uiState.value.upcomingBookings.isEmpty())
        assertEquals(0, repo.fetchClasses("gym-1").first { it.id == "class-1" }.currentAttendees)
    }

    @Test
    fun removeMember_setsDidRemove() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGymMember("gym-1", member)

        val viewModel = MemberDetailViewModel(repo, "gym-1", member)
        viewModel.removeMember()

        assertTrue(viewModel.uiState.value.didRemove)
        assertTrue(repo.fetchGymMembers("gym-1").isEmpty())
    }

    @Test
    fun removeMember_surfacesErrorMessage_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = MemberDetailViewModel(repo, "gym-1", member)
        repo.errorToThrow = RuntimeException("network error")

        viewModel.removeMember()

        val state = viewModel.uiState.value
        assertEquals(false, state.didRemove)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }
}
