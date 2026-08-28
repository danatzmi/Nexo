package com.nexo.app

import com.nexo.app.data.SessionStore
import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.SessionViewModel
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

/** An in-memory [SessionStore] — the fake-object counterpart to [FakeBackendRepository], for unit-testing gym auto-select/persistence without Android's real `SharedPreferences`. */
private class InMemorySessionStore : SessionStore {
    var stored: String? = null
    override fun getLastGymId(): String? = stored
    override fun setLastGymId(gymId: String?) { stored = gymId }
}

/**
 * Covers [SessionViewModel]'s routing decision — signed-out vs. no-gyms
 * vs. ready, and the auto-select/persist-last-gym logic FEEDBACK.md's
 * Phase 3 spec calls for, which is exactly the kind of "easy to get
 * subtly wrong" state CLAUDE.md flags for test coverage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_isSignedOut_whenNoUserIsSignedIn() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        assertEquals(SessionViewModel.SessionState.SignedOut, viewModel.state.value)
    }

    @Test
    fun state_isNoGyms_whenSignedInUserHasNoMemberships() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        assertEquals(SessionViewModel.SessionState.NoGyms, viewModel.state.value)
    }

    @Test
    fun state_isReady_withTheFirstGym_whenNoLastGymWasSaved() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedGym(Gym(id = "gym-2", name = "Gym Two", ownerUID = "owner-2"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore()

        val viewModel = SessionViewModel(repo, store)

        val state = viewModel.state.value
        assertEquals(true, state is SessionViewModel.SessionState.Ready)
        assertEquals("gym-1", (state as SessionViewModel.SessionState.Ready).gymId)
        assertEquals("gym-1", store.stored) // persisted for next launch
    }

    @Test
    fun state_isReady_withThePreviouslySavedGym_whenStillAMember() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedGym(Gym(id = "gym-2", name = "Gym Two", ownerUID = "owner-2"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore().apply { stored = "gym-2" }

        val viewModel = SessionViewModel(repo, store)

        val state = viewModel.state.value as SessionViewModel.SessionState.Ready
        assertEquals("gym-2", state.gymId)
    }

    @Test
    fun state_fallsBackToFirstGym_whenSavedGymIsNoLongerAMembership() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore().apply { stored = "stale-gym-no-longer-a-member" }

        val viewModel = SessionViewModel(repo, store)

        val state = viewModel.state.value as SessionViewModel.SessionState.Ready
        assertEquals("gym-1", state.gymId)
    }

    @Test
    fun switchGym_updatesGymId_andPersistsIt_whenTargetIsAMembership() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedGym(Gym(id = "gym-2", name = "Gym Two", ownerUID = "owner-2"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore()
        val viewModel = SessionViewModel(repo, store)

        viewModel.switchGym("gym-2")

        val state = viewModel.state.value as SessionViewModel.SessionState.Ready
        assertEquals("gym-2", state.gymId)
        assertEquals("gym-2", store.stored)
    }

    @Test
    fun switchGym_isNoOp_whenTargetIsNotAMembership() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore()
        val viewModel = SessionViewModel(repo, store)

        viewModel.switchGym("not-a-membership")

        val state = viewModel.state.value as SessionViewModel.SessionState.Ready
        assertEquals("gym-1", state.gymId)
    }

    @Test
    fun signOut_clearsRepositorySessionAndSavedGym_andReturnsToSignedOut() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"
        val store = InMemorySessionStore()
        val viewModel = SessionViewModel(repo, store)

        viewModel.signOut()

        assertEquals(SessionViewModel.SessionState.SignedOut, viewModel.state.value)
        assertNull(repo.currentUID())
        assertNull(store.stored)
    }

    // MARK: - Platform Admin Dashboard

    @Test
    fun state_isPlatformDashboard_forAnAdminWithNoPriorGymSelection_evenWithMemberships() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.OWNER, "admin-1")
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"

        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        assertTrue(viewModel.state.value is SessionViewModel.SessionState.PlatformDashboard)
    }

    @Test
    fun state_isPlatformDashboard_forAnAdminWithNoGymsAtAll() = runTest {
        val repo = FakeBackendRepository()
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"

        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        assertTrue(viewModel.state.value is SessionViewModel.SessionState.PlatformDashboard)
    }

    @Test
    fun state_isReady_forAnAdminWithAPreviouslySavedGym() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.OWNER, "admin-1")
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"
        val store = InMemorySessionStore().apply { stored = "gym-1" }

        val viewModel = SessionViewModel(repo, store)

        val state = viewModel.state.value
        assertTrue(state is SessionViewModel.SessionState.Ready)
        assertEquals("gym-1", (state as SessionViewModel.SessionState.Ready).gymId)
    }

    @Test
    fun enterGym_fromPlatformDashboard_transitionsToReady() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.OWNER, "admin-1")
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"
        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        viewModel.enterGym("gym-1")

        val state = viewModel.state.value
        assertTrue(state is SessionViewModel.SessionState.Ready)
        assertEquals("gym-1", (state as SessionViewModel.SessionState.Ready).gymId)
    }

    @Test
    fun enterPlatformDashboard_fromReady_returnsToDashboard() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Gym One", ownerUID = "owner-1"), UserRole.OWNER, "admin-1")
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"
        val store = InMemorySessionStore().apply { stored = "gym-1" }
        val viewModel = SessionViewModel(repo, store)

        viewModel.enterPlatformDashboard()

        assertTrue(viewModel.state.value is SessionViewModel.SessionState.PlatformDashboard)
        assertNull(store.stored)
    }

    // MARK: - Deep link: join-by-code

    @Test
    fun presentDeepLinkCode_resolvesTheGymForTheDialog() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())
        val viewModel = SessionViewModel(repo, InMemorySessionStore())

        viewModel.presentDeepLinkCode("IRON99")

        assertEquals("Iron Temple", viewModel.directJoinState.value?.gym?.name)
        assertEquals(false, viewModel.directJoinState.value?.isLoading)
    }

    @Test
    fun confirmDirectJoin_entersTheGym_afterJoining() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())

        repo.signedInUID = "member-1"
        val viewModel = SessionViewModel(repo, InMemorySessionStore())
        viewModel.presentDeepLinkCode("IRON99")

        viewModel.confirmDirectJoin()

        val state = viewModel.state.value
        assertTrue(state is SessionViewModel.SessionState.Ready)
        assertNull(viewModel.directJoinState.value)
    }

    @Test
    fun confirmDirectJoin_forAnAlreadyEnrolledGym_switchesInto_withoutRejoining() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "owner-1"
        val gym = repo.createGymForCurrentUser("Iron Temple", null, joinCode = "IRON99", workoutTypes = emptyList())
        val viewModel = SessionViewModel(repo, InMemorySessionStore())
        viewModel.presentDeepLinkCode("IRON99")

        viewModel.confirmDirectJoin()

        val state = viewModel.state.value as SessionViewModel.SessionState.Ready
        assertEquals(gym.id, state.gymId)
        // Still the Owner — confirmDirectJoin must not have re-joined as Member.
        assertEquals(UserRole.OWNER, repo.fetchMyGyms().first { it.first.id == gym.id }.second)
    }
}
