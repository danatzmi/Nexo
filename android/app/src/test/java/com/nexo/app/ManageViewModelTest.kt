package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.admin.GymMembersViewModel
import com.nexo.app.ui.admin.MembershipPlansViewModel
import com.nexo.app.ui.admin.TeamViewModel
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
class ManageViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // MARK: - GymMembersViewModel

    @Test
    fun gymMembersViewModel_filteredMembers_matchesNameOrEmail_caseInsensitively() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGymMember("gym-1", GymMember(id = "1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGymMember("gym-1", GymMember(id = "2", fullName = "Sam Lee", email = "sam@example.com"))

        val viewModel = GymMembersViewModel(repo, "gym-1")
        viewModel.updateSearchText("dana")

        assertEquals(listOf("Dana Cohen"), viewModel.uiState.value.filteredMembers.map { it.fullName })
    }

    @Test
    fun gymMembersViewModel_filteredMembers_isEverything_whenSearchIsBlank() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGymMember("gym-1", GymMember(id = "1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedGymMember("gym-1", GymMember(id = "2", fullName = "Sam Lee", email = "sam@example.com"))

        val viewModel = GymMembersViewModel(repo, "gym-1")

        assertEquals(2, viewModel.uiState.value.filteredMembers.size)
    }

    @Test
    fun gymMembersViewModel_addMember_addsToMembers_onSuccess() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("user-1", Member(id = "user-1", fullName = "Dana Cohen", email = "dana@example.com"))
        val viewModel = GymMembersViewModel(repo, "gym-1")

        var result: Boolean? = null
        viewModel.addMember("dana@example.com") { result = it }

        assertEquals(true, result)
        assertEquals(listOf("Dana Cohen"), viewModel.uiState.value.members.map { it.fullName })
        assertEquals(false, viewModel.uiState.value.isAddingMember)
    }

    @Test
    fun gymMembersViewModel_addMember_surfacesError_andReturnsFalse_whenEmailNotFound() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = GymMembersViewModel(repo, "gym-1")

        var result: Boolean? = null
        viewModel.addMember("nobody@example.com") { result = it }

        assertEquals(false, result)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun gymMembersViewModel_registerMember_addsToMembers_onSuccess() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = GymMembersViewModel(repo, "gym-1")

        var result: Boolean? = null
        viewModel.registerMember("Dana", "Cohen", "dana@example.com", "password123") { result = it }

        assertEquals(true, result)
        assertEquals(listOf("Dana Cohen"), viewModel.uiState.value.members.map { it.fullName })
        assertEquals(false, viewModel.uiState.value.isAddingMember)
    }

    // MARK: - TeamViewModel

    @Test
    fun teamViewModel_addTeamMember_addsToTeam_onSuccess() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("coach-1", Member(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com"))

        val viewModel = TeamViewModel(repo, "gym-1")
        var result: Boolean? = null
        viewModel.addTeamMember("sam@example.com", UserRole.COACH, "Sam Lee") { result = it }

        assertEquals(true, result)
        assertEquals(listOf("Sam Lee"), viewModel.uiState.value.team.map { it.fullName })
    }

    @Test
    fun teamViewModel_addTeamMember_surfacesError_andReturnsFalse_whenEmailNotFound() = runTest {
        val repo = FakeBackendRepository()

        val viewModel = TeamViewModel(repo, "gym-1")
        var result: Boolean? = null
        viewModel.addTeamMember("nobody@example.com", UserRole.COACH, "Nobody") { result = it }

        assertEquals(false, result)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun teamViewModel_removeTeamMember_removesFromTeam() = runTest {
        val repo = FakeBackendRepository()
        repo.seedTeamMember("gym-1", TeamMember(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com", role = UserRole.COACH))

        val viewModel = TeamViewModel(repo, "gym-1")
        viewModel.removeTeamMember("coach-1")

        assertTrue(viewModel.uiState.value.team.isEmpty())
    }

    @Test
    fun teamViewModel_updateTeamMemberRole_updatesTheirRoleInTeam() = runTest {
        val repo = FakeBackendRepository()
        repo.seedTeamMember("gym-1", TeamMember(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com", role = UserRole.COACH))

        val viewModel = TeamViewModel(repo, "gym-1")
        viewModel.updateTeamMemberRole("coach-1", UserRole.OWNER)

        assertEquals(UserRole.OWNER, viewModel.uiState.value.team.first { it.id == "coach-1" }.role)
    }

    @Test
    fun teamViewModel_registerTeamMember_addsToTeam_onSuccess() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = TeamViewModel(repo, "gym-1")

        var result: Boolean? = null
        viewModel.registerTeamMember("Sam", "Lee", "sam@example.com", "password123", UserRole.COACH) { result = it }

        assertEquals(true, result)
        assertEquals(listOf("Sam Lee"), viewModel.uiState.value.team.map { it.fullName })
        assertEquals(false, viewModel.uiState.value.isAddingMember)
    }

    // MARK: - MembershipPlansViewModel

    @Test
    fun membershipPlansViewModel_createPlan_addsToPlans() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = MembershipPlansViewModel(repo, "gym-1")

        val plan = MembershipPlan(id = "plan-1", name = "Unlimited Monthly", price = 99.0, components = listOf(PlanComponent(type = PlanComponentType.UNLIMITED)))
        viewModel.createPlan(plan)

        assertEquals(listOf(plan), viewModel.uiState.value.plans)
    }

    @Test
    fun membershipPlansViewModel_deletePlan_removesFromPlans() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        repo.seedMembershipPlan("gym-1", plan)

        val viewModel = MembershipPlansViewModel(repo, "gym-1")
        viewModel.deletePlan("plan-1")

        assertTrue(viewModel.uiState.value.plans.isEmpty())
    }

    @Test
    fun membershipPlansViewModel_updatePlan_overwritesItInPlans() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        repo.seedMembershipPlan("gym-1", plan)

        val viewModel = MembershipPlansViewModel(repo, "gym-1")
        val updated = plan.copy(name = "20 Credits", price = 90.0, components = listOf(plan.components.first().copy(creditCount = 20)))
        viewModel.updatePlan(updated)

        assertEquals(listOf(updated), viewModel.uiState.value.plans)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }
}
