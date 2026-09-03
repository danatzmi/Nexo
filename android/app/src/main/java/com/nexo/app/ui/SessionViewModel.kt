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
        /** No gym memberships yet — [ui.gym.GymPickerScreen]'s "awaiting gym enrollment" waiting screen; there's no self-serve creation or public join, only a gym owner adding this user by email. */
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

            val platformRole = repository.fetchPlatformRole()
            val gyms = resolveMyGyms(platformRole, repository)

            if (platformRole == PlatformRole.ADMIN) {
                // Platform admins land on the Platform Dashboard by default on launch/sign-in (matching iOS)
                _state.value = SessionState.PlatformDashboard(gyms)
                return@launch
            }

            if (gyms.isEmpty()) {
                _state.value = SessionState.NoGyms
                return@launch
            }

            // Auto-select: prefer the last selected gym if the user still
            // belongs to it, otherwise fall back to the first membership.
            val lastGymId = sessionStore.getLastGymId()
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
            val platformRole = repository.fetchPlatformRole()
            val gyms = resolveMyGyms(platformRole, repository)
            if (gyms.none { it.first.id == gymId }) return@launch
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

}

/**
 * Resolves which gyms populate the session's list for the given platform role.
 * Mirrors iOS's `resolveMyGyms` in `ContentView.swift`. Platform admins see all
 * gyms in the system as owners for management purposes.
 */
suspend fun resolveMyGyms(role: PlatformRole, repository: BackendRepository): List<Pair<Gym, UserRole>> {
    return if (role == PlatformRole.ADMIN) {
        val allGyms = try { repository.fetchAvailableGyms() } catch (e: Exception) { emptyList() }
        allGyms.map { it to UserRole.OWNER }
    } else {
        try { repository.fetchMyGyms() } catch (e: Exception) { emptyList() }
    }
}

