package com.nexo.app.ui.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for [PlatformDashboardScreen] — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. Mirrors iOS's `PlatformDashboardView`. */
class PlatformDashboardViewModel(private val repository: BackendRepository) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val gyms: List<Gym> = emptyList(),
        val users: List<PlatformUser> = emptyList(),
        val gymToDelete: Gym? = null,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val gyms = repository.fetchAvailableGyms()
                val users = repository.fetchAllUsers()
                _uiState.value = _uiState.value.copy(isLoading = false, gyms = gyms, users = users)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading dashboard: ${e.message}")
            }
        }
    }

    fun requestDeleteGym(gym: Gym) {
        _uiState.value = _uiState.value.copy(gymToDelete = gym)
    }

    fun dismissDeleteGymPrompt() {
        _uiState.value = _uiState.value.copy(gymToDelete = null)
    }

    fun confirmDeleteGym() {
        val gym = _uiState.value.gymToDelete ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(gymToDelete = null)
            try {
                repository.deleteGym(gym.id)
                _uiState.value = _uiState.value.copy(gyms = _uiState.value.gyms.filterNot { it.id == gym.id })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete gym: ${e.message}")
            }
        }
    }

    fun updateUserRole(user: PlatformUser, role: PlatformRole) {
        viewModelScope.launch {
            try {
                repository.updatePlatformRole(user.id, role)
                _uiState.value = _uiState.value.copy(
                    users = _uiState.value.users.map { if (it.id == user.id) it.copy(role = role) else it }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update role: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
