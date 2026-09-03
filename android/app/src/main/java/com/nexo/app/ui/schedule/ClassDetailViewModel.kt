package com.nexo.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordination logic for [ClassDetailScreen] — kept out of the Composable
 * per CLAUDE.md's "business logic stays out of views" rule. Mirrors
 * `ClassDetailViewModel` on iOS, with one structural difference: iOS's
 * SwiftUI navigation passes the already-loaded `GymClass` value directly
 * into the view model's initializer, so `gymClass` is non-optional there.
 * Compose Navigation only carries a plain [classId] string through the
 * nav route, so [UiState.gymClass] is loaded here (looked up from
 * `fetchClasses`) and is `null` until that completes — the screen shows a
 * full-screen spinner for that brief gap, same as every other screen in
 * this app.
 */
class ClassDetailViewModel(
    private val repository: BackendRepository,
    private val gymId: String,
    private val classId: String
) : ViewModel() {

    data class UiState(
        val gymClass: GymClass? = null,
        val attendees: List<Member> = emptyList(),
        val isLoadingAttendees: Boolean = true,
        val isBooked: Boolean = false,
        val isWaitlisted: Boolean = false,
        val waitlistPosition: Int? = null,
        val isActionInProgress: Boolean = false,
        /** Non-null once a Book/Waitlist action completes (optimistically, on tap) — the screen shows the success popup then clears this via [clearSuccessMessage]. */
        val successMessage: SuccessMessage? = null,
        /** This member's active wallet items for this gym — loaded once alongside booking status, used by [bookingBlockedReason] to proactively dim the "Book Class" button before a doomed attempt is even made. Owners/Coaches/Platform Admins bypass this check entirely (the screen skips it via `canManage`), so it doesn't matter that this is fetched for them too. */
        val activePlans: List<ActivePlanItem> = emptyList(),
        val didDelete: Boolean = false,
        val errorMessage: String? = null
    ) {
        val checkedInCount: Int get() = attendees.count { it.isCheckedIn }

        /** null when this member has an active plan/credit balance covering [gymClass] (or when the caller bypasses this check — gated via `canManage`); otherwise a short reason to show in place of a dimmed "Book Class" button. Mirrors `ScheduleViewModel.UiState.bookingBlockedReason(gymClass:)`. */
        val bookingBlockedReason: String?
            get() {
                val target = gymClass ?: return null
                val matching = activePlans.filter { it.matches(target) }
                if (matching.any { it.type == PlanComponentType.UNLIMITED || it.availableCredits() > 0 }) return null
                return if (matching.isEmpty()) "No active plan" else "No credits remaining"
            }
    }

    data class SuccessMessage(val title: String, val message: String, val isWaitlist: Boolean)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadClassAndBookingStatus()
        loadAttendees()
    }

    private fun loadClassAndBookingStatus() {
        viewModelScope.launch {
            try {
                val gymClass = repository.fetchClasses(gymId).firstOrNull { it.id == classId }
                val booked = repository.fetchMyBookedClassIds(gymId)
                val waitlisted = repository.fetchMyWaitlistedClassIds(gymId)
                val isWaitlisted = classId in waitlisted
                val position = if (isWaitlisted) repository.fetchWaitlistPosition(gymId, classId) else null
                _uiState.value = _uiState.value.copy(
                    gymClass = gymClass,
                    isBooked = classId in booked,
                    isWaitlisted = isWaitlisted,
                    waitlistPosition = position
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Error loading class: ${e.message}")
            }
            repository.currentUID()?.let { uid ->
                val plans = try { repository.fetchActivePlans(gymId, uid) } catch (e: Exception) { emptyList() }
                _uiState.value = _uiState.value.copy(activePlans = plans)
            }
        }
    }

    fun loadAttendees() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAttendees = true)
            try {
                val attendees = repository.fetchAttendees(gymId, classId)
                _uiState.value = _uiState.value.copy(isLoadingAttendees = false, attendees = attendees)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingAttendees = false, errorMessage = "Error loading attendees: ${e.message}")
            }
        }
    }

    /**
     * Optimistic booking/waitlist actions — update [_uiState] immediately
     * (0ms perceived latency) instead of waiting on a network round-trip,
     * reverting on failure. Mirrors iOS's `ClassDetailViewModel.book`/
     * `cancelBooking`/`joinWaitlist`/`leaveWaitlist`.
     */
    fun cancel() {
        val gymClass = _uiState.value.gymClass ?: return
        val previousPlans = _uiState.value.activePlans
        val updatedPlans = refundCreditLocally(previousPlans, gymClass)

        _uiState.value = _uiState.value.copy(
            isBooked = false,
            activePlans = updatedPlans,
            gymClass = gymClass.copy(currentAttendees = (gymClass.currentAttendees - 1).coerceAtLeast(0))
        )

        viewModelScope.launch {
            try {
                repository.cancelBooking(gymId, classId)
            } catch (e: Exception) {
                val current = _uiState.value.gymClass ?: gymClass
                _uiState.value = _uiState.value.copy(
                    isBooked = true,
                    activePlans = previousPlans,
                    gymClass = current.copy(currentAttendees = current.currentAttendees + 1),
                    errorMessage = "Failed to cancel: ${e.message}"
                )
            }
        }
    }

    fun leaveWaitlist() {
        val gymClass = _uiState.value.gymClass ?: return
        _uiState.value = _uiState.value.copy(
            isWaitlisted = false,
            gymClass = gymClass.copy(waitlistCount = (gymClass.waitlistCount - 1).coerceAtLeast(0))
        )

        viewModelScope.launch {
            try {
                repository.leaveWaitlist(gymId, classId)
            } catch (e: Exception) {
                val current = _uiState.value.gymClass ?: gymClass
                _uiState.value = _uiState.value.copy(
                    isWaitlisted = true,
                    gymClass = current.copy(waitlistCount = current.waitlistCount + 1),
                    errorMessage = "Failed to leave waitlist: ${e.message}"
                )
            }
        }
    }

    fun book() {
        val gymClass = _uiState.value.gymClass ?: return
        val previousPlans = _uiState.value.activePlans
        val updatedPlans = consumeCreditLocally(previousPlans, gymClass)

        _uiState.value = _uiState.value.copy(
            isBooked = true,
            activePlans = updatedPlans,
            gymClass = gymClass.copy(currentAttendees = gymClass.currentAttendees + 1),
            successMessage = SuccessMessage(
                title = "Booked!",
                message = "${gymClass.title} · ${gymClass.formattedTime}",
                isWaitlist = false
            )
        )

        viewModelScope.launch {
            try {
                repository.bookClass(gymId, classId)
            } catch (e: Exception) {
                val current = _uiState.value.gymClass ?: gymClass
                _uiState.value = _uiState.value.copy(
                    isBooked = false,
                    activePlans = previousPlans,
                    gymClass = current.copy(currentAttendees = (current.currentAttendees - 1).coerceAtLeast(0)),
                    successMessage = null,
                    errorMessage = "Failed to book: ${e.message}"
                )
            }
        }
    }

    fun joinWaitlist() {
        val gymClass = _uiState.value.gymClass ?: return
        _uiState.value = _uiState.value.copy(
            isWaitlisted = true,
            gymClass = gymClass.copy(waitlistCount = gymClass.waitlistCount + 1),
            successMessage = SuccessMessage(
                title = "Waitlisted!",
                message = "${gymClass.title} · ${gymClass.formattedTime}",
                isWaitlist = true
            )
        )

        viewModelScope.launch {
            try {
                repository.joinWaitlist(gymId, classId)
            } catch (e: Exception) {
                val current = _uiState.value.gymClass ?: gymClass
                _uiState.value = _uiState.value.copy(
                    isWaitlisted = false,
                    gymClass = current.copy(waitlistCount = (current.waitlistCount - 1).coerceAtLeast(0)),
                    successMessage = null,
                    errorMessage = "Failed to join waitlist: ${e.message}"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    /** Toggles [member]'s check-in for this class — optimistic UI update, rolled back if the repository call fails. Owner/Coach/Platform Admin only (gated in the UI layer via `canManageGym`). */
    fun toggleAttendance(member: Member) {
        val classId = _uiState.value.gymClass?.id ?: return
        val newValue = !member.isCheckedIn
        val previousAttendees = _uiState.value.attendees

        _uiState.value = _uiState.value.copy(
            attendees = previousAttendees.map {
                if (it.id == member.id) it.copy(isCheckedIn = newValue, checkedInAtMillis = if (newValue) System.currentTimeMillis() else null) else it
            }
        )

        viewModelScope.launch {
            try {
                repository.toggleAttendance(gymId, classId, member.id, newValue)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(attendees = previousAttendees, errorMessage = "Failed to update attendance: ${e.message}")
            }
        }
    }

    /** Deletes just this occurrence, or (when [applyToSeries] and the class belongs to a series) this and every future occurrence. */
    fun deleteClass(applyToSeries: Boolean) {
        val gymClass = _uiState.value.gymClass ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            try {
                if (applyToSeries && gymClass.seriesId != null) {
                    repository.deleteClassSeries(gymId, gymClass.seriesId, gymClass.startTimeMillis)
                } else {
                    repository.deleteClass(gymId, classId)
                }
                _uiState.value = _uiState.value.copy(isActionInProgress = false, didDelete = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isActionInProgress = false, errorMessage = "Failed to delete class: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun consumeCreditLocally(plans: List<ActivePlanItem>, gymClass: GymClass): List<ActivePlanItem> {
        if (plans.any { it.matches(gymClass) && it.type == PlanComponentType.UNLIMITED }) return plans
        val list = plans.toMutableList()
        val targetIndex = list.indexOfFirst {
            it.matches(gymClass) && it.type == PlanComponentType.CREDITS && it.availableCredits() > 0
        }
        if (targetIndex != -1) {
            val item = list[targetIndex]
            val updated = if (item.resetPeriod == PlanResetPeriod.MONTHLY) {
                item.copy(cycleCreditsUsed = item.cycleCreditsUsed + 1)
            } else {
                item.copy(remainingCredits = (item.remainingCredits - 1).coerceAtLeast(0))
            }
            list[targetIndex] = updated
        }
        return list
    }

    private fun refundCreditLocally(plans: List<ActivePlanItem>, gymClass: GymClass): List<ActivePlanItem> {
        if (plans.any { it.matches(gymClass) && it.type == PlanComponentType.UNLIMITED }) return plans
        val list = plans.toMutableList()
        val targetIndex = list.indexOfFirst {
            it.matches(gymClass) && it.type == PlanComponentType.CREDITS
        }
        if (targetIndex != -1) {
            val item = list[targetIndex]
            val updated = if (item.resetPeriod == PlanResetPeriod.MONTHLY) {
                item.copy(cycleCreditsUsed = (item.cycleCreditsUsed - 1).coerceAtLeast(0))
            } else {
                item.copy(remainingCredits = item.remainingCredits + 1)
            }
            list[targetIndex] = updated
        }
        return list
    }
}
