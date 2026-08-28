package com.nexo.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.MembershipPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for the Manage tab's Plans list — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class MembershipPlansViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val plans: List<MembershipPlan> = emptyList(),
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val plans = repository.fetchMembershipPlans(gymId)
                _uiState.value = _uiState.value.copy(isLoading = false, plans = plans)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading plans: ${e.message}")
            }
        }
    }

    fun createPlan(plan: MembershipPlan) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.createMembershipPlan(gymId, plan)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to create plan: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun updatePlan(plan: MembershipPlan) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.updateMembershipPlan(gymId, plan)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update plan: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMembershipPlan(gymId, planId)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete plan: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
