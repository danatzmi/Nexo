package com.nexo.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for the Manage tab's Members list — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class GymMembersViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val members: List<GymMember> = emptyList(),
        val searchText: String = "",
        val isAddingMember: Boolean = false,
        val errorMessage: String? = null
    ) {
        val filteredMembers: List<GymMember>
            get() = if (searchText.isBlank()) members
            else members.filter { it.fullName.contains(searchText, ignoreCase = true) || it.email.contains(searchText, ignoreCase = true) }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val members = repository.fetchGymMembers(gymId)
                _uiState.value = _uiState.value.copy(isLoading = false, members = members)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading members: ${e.message}")
            }
        }
    }

    fun updateSearchText(value: String) {
        _uiState.value = _uiState.value.copy(searchText = value)
    }

    fun addMember(email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingMember = true)
            try {
                repository.addMember(gymId, email)
                load()
                onResult(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to add member: ${e.message}")
                onResult(false)
            } finally {
                _uiState.value = _uiState.value.copy(isAddingMember = false)
            }
        }
    }

    fun registerMember(firstName: String, lastName: String, email: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingMember = true)
            try {
                repository.registerMember(gymId, firstName, lastName, email, password)
                load()
                onResult(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to register member: ${e.message}")
                onResult(false)
            } finally {
                _uiState.value = _uiState.value.copy(isAddingMember = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
