package com.nexo.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for the Manage tab's Team list — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class TeamViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val team: List<TeamMember> = emptyList(),
        val searchText: String = "",
        val isAddingMember: Boolean = false,
        val currentUID: String? = null,
        val errorMessage: String? = null
    ) {
        val filteredTeam: List<TeamMember>
            get() = if (searchText.isBlank()) team
            else team.filter { it.fullName.contains(searchText, ignoreCase = true) || it.email.contains(searchText, ignoreCase = true) }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val team = repository.fetchTeam(gymId)
                _uiState.value = _uiState.value.copy(isLoading = false, team = team, currentUID = repository.currentUID())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading team: ${e.message}")
            }
        }
    }

    fun updateSearchText(value: String) {
        _uiState.value = _uiState.value.copy(searchText = value)
    }

    fun addTeamMember(email: String, role: UserRole, name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingMember = true)
            try {
                repository.addTeamMember(gymId, email, role, name)
                load()
                onResult(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to add team member: ${e.message}")
                onResult(false)
            } finally {
                _uiState.value = _uiState.value.copy(isAddingMember = false)
            }
        }
    }

    fun registerTeamMember(firstName: String, lastName: String, email: String, password: String, role: UserRole, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingMember = true)
            try {
                repository.registerTeamMember(gymId, firstName, lastName, email, password, role)
                load()
                onResult(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to register team member: ${e.message}")
                onResult(false)
            } finally {
                _uiState.value = _uiState.value.copy(isAddingMember = false)
            }
        }
    }

    fun updateTeamMemberRole(userId: String, role: UserRole) {
        viewModelScope.launch {
            try {
                repository.updateTeamMemberRole(gymId, userId, role)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update role: ${e.message}")
            }
        }
    }

    fun removeTeamMember(memberId: String) {
        viewModelScope.launch {
            try {
                repository.removeTeamMember(gymId, memberId)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to remove team member: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
