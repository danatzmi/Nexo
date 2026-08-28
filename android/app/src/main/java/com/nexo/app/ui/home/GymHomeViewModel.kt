package com.nexo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for the Home dashboard — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class GymHomeViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val gymName: String = "",
        val roleDisplayName: String = "",
        val userDisplayName: String = "",
        val nextBookedClass: GymClass? = null,
        val activePlans: List<ActivePlanItem> = emptyList(),
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val myGyms = repository.fetchMyGyms()
                val (gym, role) = myGyms.firstOrNull { it.first.id == gymId } ?: (null to null)
                val profile = repository.fetchMyProfile()
                val classes = repository.fetchClasses(gymId)
                val bookedIds = repository.fetchMyBookedClassIds(gymId)
                val now = System.currentTimeMillis()
                val next = classes
                    .filter { it.id in bookedIds && it.startTimeMillis >= now }
                    .minByOrNull { it.startTimeMillis }
                val plans = repository.currentUID()?.let { repository.fetchActivePlans(gymId, it) }.orEmpty()

                _uiState.value = UiState(
                    isLoading = false,
                    gymName = gym?.name.orEmpty(),
                    roleDisplayName = role?.displayName.orEmpty(),
                    userDisplayName = profile?.fullName.orEmpty(),
                    nextBookedClass = next,
                    activePlans = plans
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading home: ${e.message}")
            }
        }
    }

    /** Silently reloads Home dashboard state without showing a full-screen loading spinner (e.g. on resume after returning from ClassDetailScreen). */
    fun refresh() {
        viewModelScope.launch {
            try {
                val myGyms = repository.fetchMyGyms()
                val (gym, role) = myGyms.firstOrNull { it.first.id == gymId } ?: (null to null)
                val profile = repository.fetchMyProfile()
                val classes = repository.fetchClasses(gymId)
                val bookedIds = repository.fetchMyBookedClassIds(gymId)
                val now = System.currentTimeMillis()
                val next = classes
                    .filter { it.id in bookedIds && it.startTimeMillis >= now }
                    .minByOrNull { it.startTimeMillis }
                val plans = repository.currentUID()?.let { repository.fetchActivePlans(gymId, it) }.orEmpty()

                _uiState.value = _uiState.value.copy(
                    gymName = gym?.name.orEmpty(),
                    roleDisplayName = role?.displayName.orEmpty(),
                    userDisplayName = profile?.fullName.orEmpty(),
                    nextBookedClass = next,
                    activePlans = plans
                )
            } catch (e: Exception) {
                // Keep existing state on silent refresh error
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
