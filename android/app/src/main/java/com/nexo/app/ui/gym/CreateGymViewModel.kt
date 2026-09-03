package com.nexo.app.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Platform-Admin-only "Create a Gym" form — creates the gym and assigns its owner by email. Mirrors iOS's `CreateGymView`. Kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class CreateGymViewModel(private val repository: BackendRepository) : ViewModel() {

    companion object {
        val SUGGESTED_CATEGORIES = listOf(
            "CrossFit", "HIIT", "Strength", "Yoga", "Pilates",
            "Boxing", "Open Gym", "Spinning", "Cardio", "Mobility"
        )
    }

    data class UiState(
        val gymName: String = "",
        val city: String = "",
        val selectedWorkoutTypes: Set<String> = emptySet(),
        val customCategories: List<String> = emptyList(),
        val ownerFirstName: String = "",
        val ownerLastName: String = "",
        val ownerEmail: String = "",
        val ownerPassword: String = "",
        val isSaving: Boolean = false,
        val createdGym: Gym? = null,
        val errorMessage: String? = null
    ) {
        val trimmedName: String get() = gymName.trim()
        val trimmedOwnerEmail: String get() = ownerEmail.trim()
        val allCategories: List<String> get() = SUGGESTED_CATEGORIES + customCategories.filter { it !in SUGGESTED_CATEGORIES }
        val isValid: Boolean get() = trimmedName.isNotBlank() && ownerFirstName.isNotBlank() && trimmedOwnerEmail.contains("@") && ownerPassword.length >= 6 && !isSaving
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateGymName(value: String) { _uiState.value = _uiState.value.copy(gymName = value) }
    fun updateCity(value: String) { _uiState.value = _uiState.value.copy(city = value) }
    fun updateOwnerFirstName(value: String) { _uiState.value = _uiState.value.copy(ownerFirstName = value) }
    fun updateOwnerLastName(value: String) { _uiState.value = _uiState.value.copy(ownerLastName = value) }
    fun updateOwnerEmail(value: String) { _uiState.value = _uiState.value.copy(ownerEmail = value) }
    fun updateOwnerPassword(value: String) { _uiState.value = _uiState.value.copy(ownerPassword = value) }

    fun toggleCategory(category: String) {
        val current = _uiState.value.selectedWorkoutTypes
        _uiState.value = _uiState.value.copy(selectedWorkoutTypes = if (category in current) current - category else current + category)
    }

    fun addCustomCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        _uiState.value = state.copy(
            customCategories = if (trimmed in state.customCategories) state.customCategories else state.customCategories + trimmed,
            selectedWorkoutTypes = state.selectedWorkoutTypes + trimmed
        )
    }

    fun createGym() {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            try {
                val categories = state.selectedWorkoutTypes.takeIf { it.isNotEmpty() }?.sorted() ?: listOf("General Fitness")
                val gym = repository.createGym(
                    name = state.trimmedName,
                    city = state.city.trim().takeIf { it.isNotEmpty() },
                    workoutTypes = categories,
                    ownerFirstName = state.ownerFirstName.trim(),
                    ownerLastName = state.ownerLastName.trim(),
                    ownerEmail = state.trimmedOwnerEmail,
                    ownerPassword = state.ownerPassword
                )
                _uiState.value = _uiState.value.copy(isSaving = false, createdGym = gym)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = "Error creating gym: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
