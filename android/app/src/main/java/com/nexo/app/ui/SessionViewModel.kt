package com.nexo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.SessionStore
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the app's top-level routing decision — mirrors the session-routing
 * logic `NexoApp`/`ContentView` does on iOS after `AuthView`, kept out of
 * the Composable per CLAUDE.md's "business logic stays out of views" rule.
 */
class SessionViewModel(
    private val repository: BackendRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    sealed interface SessionState {
        data object Loading : SessionState
        data object SignedOut : SessionState
        /** No gym memberships yet — [ui.gym.GymPickerScreen]'s "I am a Gym Member" / "I am a Gym Owner" onboarding hub. */
        data object NoGyms : SessionState
        /** A Platform Admin with no specific gym entered — mirrors iOS's `PlatformDashboardView` being shown in place of a gym-scoped Home. Reachable on first launch (before ever selecting a gym) or via the gym switcher's "Platform Dashboard" entry. */
        data class PlatformDashboard(val myGyms: List<Pair<Gym, UserRole>>) : SessionState
        data class Ready(val gymId: String, val myGyms: List<Pair<Gym, UserRole>>, val platformRole: PlatformRole) : SessionState
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init { refresh() }

    /** Re-evaluates auth/gym-membership state — called on launch, after sign-in, and after sign-up. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = SessionState.Loading

            val uid = repository.currentUID()
            if (uid == null) {
                _state.value = SessionState.SignedOut
                return@launch
            }

            val gyms = repository.fetchMyGyms()
            val platformRole = repository.fetchPlatformRole()

            if (gyms.isEmpty()) {
                _state.value = if (platformRole == PlatformRole.ADMIN) SessionState.PlatformDashboard(gyms) else SessionState.NoGyms
                return@launch
            }

            val lastGymId = sessionStore.getLastGymId()
            if (platformRole == PlatformRole.ADMIN && lastGymId == null) {
                // A platform admin with no prior gym selection lands on the
                // dashboard by default, rather than being dropped into
                // whichever gym happens to sort first.
                _state.value = SessionState.PlatformDashboard(gyms)
                return@launch
            }

            // Auto-select: prefer the last selected gym if the user still
            // belongs to it, otherwise fall back to the first membership.
            val selectedGymId = gyms.firstOrNull { it.first.id == lastGymId }?.first?.id ?: gyms.first().first.id
            sessionStore.setLastGymId(selectedGymId)
            _state.value = SessionState.Ready(selectedGymId, gyms, platformRole)
        }
    }

    /** Switches the active gym without a full reload/restart — [gyms] was already fetched by [refresh]. No-op if [gymId] isn't one of the user's memberships. */
    fun switchGym(gymId: String) {
        val current = _state.value
        if (current !is SessionState.Ready) return
        if (current.myGyms.none { it.first.id == gymId }) return

        sessionStore.setLastGymId(gymId)
        _state.value = current.copy(gymId = gymId)
    }

    /** Enters a specific gym from [SessionState.PlatformDashboard] or after creating/joining a gym. */
    fun enterGym(gymId: String) {
        viewModelScope.launch {
            val gyms = repository.fetchMyGyms()
            if (gyms.none { it.first.id == gymId }) return@launch
            val platformRole = repository.fetchPlatformRole()
            sessionStore.setLastGymId(gymId)
            _state.value = SessionState.Ready(gymId, gyms, platformRole)
        }
    }

    /** Returns to the Platform Admin Dashboard from a specific gym — the gym switcher's "Platform Dashboard" entry. */
    fun enterPlatformDashboard() {
        val current = _state.value
        if (current !is SessionState.Ready) return
        sessionStore.setLastGymId(null)
        _state.value = SessionState.PlatformDashboard(current.myGyms)
    }

    fun signOut() {
        repository.signOut()
        sessionStore.setLastGymId(null)
        _state.value = SessionState.SignedOut
    }

    // MARK: - Deep link: join-by-code

    data class DirectJoinState(
        val code: String,
        val isLoading: Boolean = true,
        val gym: Gym? = null,
        val isJoining: Boolean = false,
        val errorMessage: String? = null
    )

    private val _directJoinState = MutableStateFlow<DirectJoinState?>(null)
    val directJoinState: StateFlow<DirectJoinState?> = _directJoinState.asStateFlow()

    /** Looks up [code] and shows a confirmation dialog — call once signed in (any [SessionState] other than [SessionState.Loading]/[SessionState.SignedOut]). */
    fun presentDeepLinkCode(code: String) {
        _directJoinState.value = DirectJoinState(code = code)
        viewModelScope.launch {
            val gym = try {
                repository.fetchGymByJoinCode(code)
            } catch (e: Exception) {
                null
            }
            _directJoinState.value = _directJoinState.value?.copy(
                isLoading = false,
                gym = gym,
                errorMessage = if (gym == null) "No gym found with that code." else null
            )
        }
    }

    /** Joins the gym from the current [DirectJoinState] — or just switches into it, without re-joining, if the user is already a member (e.g. they opened their own gym's invite link). */
    fun confirmDirectJoin() {
        val current = _directJoinState.value ?: return
        val gym = current.gym ?: return

        viewModelScope.launch {
            _directJoinState.value = current.copy(isJoining = true)
            try {
                val existingGyms = repository.fetchMyGyms()
                val targetGymId = if (existingGyms.any { it.first.id == gym.id }) gym.id else repository.joinGymByCode(current.code).id
                _directJoinState.value = null
                enterGym(targetGymId)
            } catch (e: Exception) {
                _directJoinState.value = current.copy(isJoining = false, errorMessage = "Failed to join: ${e.message}")
            }
        }
    }

    fun dismissDirectJoin() {
        _directJoinState.value = null
    }
}
