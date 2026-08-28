package com.nexo.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.MembershipPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordination logic for [MemberDetailSheet] — mirrors iOS's
 * `MemberDetailViewModel` (credit wallet grant/revoke, bookings with
 * staff-initiated cancel, remove-from-gym), kept out of the Composable
 * per CLAUDE.md's "business logic stays out of views" rule.
 */
class MemberDetailViewModel(
    private val repository: BackendRepository,
    private val gymId: String,
    val member: GymMember
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val bookings: List<GymClass> = emptyList(),
        val activePlans: List<ActivePlanItem> = emptyList(),
        val availablePlans: List<MembershipPlan> = emptyList(),
        val didRemove: Boolean = false,
        val errorMessage: String? = null
    ) {
        val upcomingBookings: List<GymClass>
            get() = bookings.filter { it.startTimeMillis >= System.currentTimeMillis() }.sortedBy { it.startTimeMillis }

        val pastBookings: List<GymClass>
            get() = bookings.filter { it.startTimeMillis < System.currentTimeMillis() }.sortedByDescending { it.startTimeMillis }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val bookings = repository.fetchMemberBookings(gymId, member.id)
                val activePlans = repository.fetchActivePlans(gymId, member.id)
                val availablePlans = repository.fetchMembershipPlans(gymId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bookings = bookings,
                    activePlans = activePlans,
                    availablePlans = availablePlans
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading member: ${e.message}")
            }
        }
    }

    /** Staff-initiated cancel — mirrors iOS's `cancelBooking(gymId:classId:onBehalfOf:)`. */
    fun cancelBooking(classId: String) {
        viewModelScope.launch {
            try {
                repository.cancelBooking(gymId, classId, onBehalfOf = member.id)
                _uiState.value = _uiState.value.copy(bookings = _uiState.value.bookings.filter { it.id != classId })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to cancel booking: ${e.message}")
            }
        }
    }

    fun grantPlan(plan: MembershipPlan, customExpiresAtMillis: Long? = null) {
        viewModelScope.launch {
            try {
                repository.grantPlanToMember(gymId, member.id, plan, customExpiresAtMillis)
                _uiState.value = _uiState.value.copy(activePlans = repository.fetchActivePlans(gymId, member.id))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to grant plan: ${e.message}")
            }
        }
    }

    fun revokeActivePlan(activePlanId: String) {
        viewModelScope.launch {
            try {
                repository.revokeActivePlan(gymId, member.id, activePlanId)
                _uiState.value = _uiState.value.copy(activePlans = _uiState.value.activePlans.filter { it.id != activePlanId })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to revoke plan: ${e.message}")
            }
        }
    }

    /** Removes the member from the gym entirely. Sets [UiState.didRemove] on success so the screen knows to dismiss. */
    fun removeMember() {
        viewModelScope.launch {
            try {
                repository.removeMember(gymId, member.id)
                _uiState.value = _uiState.value.copy(didRemove = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to remove member: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
