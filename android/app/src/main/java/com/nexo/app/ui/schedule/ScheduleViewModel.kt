package com.nexo.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import com.nexo.app.domain.model.isOnLocalDate
import com.nexo.app.domain.model.weekDatesFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Coordination logic for the Schedule/booking screen — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class ScheduleViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val selectedDate: LocalDate = LocalDate.now(),
        val allClasses: List<GymClass> = emptyList(),
        val bookedClassIds: Set<String> = emptySet(),
        val waitlistedClassIds: Set<String> = emptySet(),
        /** The signed-in user's position in a given class's waitlist — populated per-row by [loadRowDetails] as each `ClassRow` appears, mirroring iOS's `ScheduleViewModel.waitlistPositions`. */
        val waitlistPositions: Map<String, Int> = emptyMap(),
        /** Checked-in attendee count per class, staff-only — same per-row loading rationale as [waitlistPositions]. */
        val checkedInCounts: Map<String, Int> = emptyMap(),
        /** Non-null once a Book/Waitlist action completes (optimistically, on tap) — the view shows the success popup then clears this via [clearSuccessMessage]. */
        val successMessage: SuccessMessage? = null,
        /** This member's active wallet items for this gym — loaded once alongside booked/waitlisted IDs, used by [bookingBlockedReason] to proactively dim the Book button before a doomed attempt is even made. Owners/Coaches/Platform Admins bypass this check entirely (the screen skips calling [bookingBlockedReason] for them), so it doesn't matter that this is fetched for them too. */
        val activePlans: List<ActivePlanItem> = emptyList(),
        val errorMessage: String? = null
    ) {
        val weekDates: List<LocalDate> get() = weekDatesFor(selectedDate)

        /** Classes from the live [BackendRepository.observeClasses] listener (all dates), filtered client-side to [selectedDate]. */
        val classesForSelectedDate: List<GymClass>
            get() = allClasses.filter { isOnLocalDate(it.startTimeMillis, selectedDate) }.sortedBy { it.startTimeMillis }

        /** null when this member can book [gymClass] (or bypasses the check — gated via `canManage`); otherwise a short reason to show in place of a dimmed Book button. Mirrors iOS's `ScheduleViewModel.bookingBlockedReason(for:)`. */
        fun bookingBlockedReason(gymClass: GymClass): String? {
            val matching = activePlans.filter { it.matches(gymClass) }
            if (matching.any { it.type == PlanComponentType.UNLIMITED || it.availableCredits() > 0 }) return null
            return if (matching.isEmpty()) "No active plan" else "No credits remaining"
        }
    }

    data class SuccessMessage(val title: String, val message: String, val isWaitlist: Boolean)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val booked = repository.fetchMyBookedClassIds(gymId)
                val waitlisted = repository.fetchMyWaitlistedClassIds(gymId)
                _uiState.value = _uiState.value.copy(bookedClassIds = booked, waitlistedClassIds = waitlisted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Error loading schedule: ${e.message}")
            }
            repository.currentUID()?.let { uid ->
                val plans = try { repository.fetchActivePlans(gymId, uid) } catch (e: Exception) { emptyList() }
                _uiState.value = _uiState.value.copy(activePlans = plans)
                viewModelScope.launch {
                    repository.observeActivePlans(gymId, uid).collect { livePlans ->
                        _uiState.value = _uiState.value.copy(activePlans = livePlans)
                    }
                }
            }
            observeClasses()
        }
    }

    /**
     * Subscribes to [BackendRepository.observeClasses] for the lifetime of
     * this ViewModel — mirrors iOS's `startObserving()`. Android doesn't
     * need an explicit `stopObserving()` teardown: `viewModelScope` is
     * cancelled automatically in `onCleared()`, which cancels this
     * collection (and, on Firebase, removes the underlying snapshot
     * listener via `awaitClose`) along with it. `isLoading` clears on the
     * first emission, same as iOS clearing it inside the listener callback.
     */
    private fun observeClasses() {
        viewModelScope.launch {
            repository.observeClasses(gymId).collect { classes ->
                _uiState.value = _uiState.value.copy(isLoading = false, allClasses = classes)
            }
        }
    }

    /** Silently re-reads booked/waitlisted IDs without showing a full-screen loading indicator (e.g. on resume after returning from ClassDetailScreen). Classes themselves stay live via [observeClasses] and don't need a manual refresh. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val booked = repository.fetchMyBookedClassIds(gymId)
                val waitlisted = repository.fetchMyWaitlistedClassIds(gymId)
                _uiState.value = _uiState.value.copy(bookedClassIds = booked, waitlistedClassIds = waitlisted)
            } catch (e: Exception) {
                // Keep existing state on silent refresh error
            }
            repository.currentUID()?.let { uid ->
                try {
                    val plans = repository.fetchActivePlans(gymId, uid)
                    _uiState.value = _uiState.value.copy(activePlans = plans)
                } catch (e: Exception) {
                    // Keep existing state on silent refresh error
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    /** Shifts [UiState.selectedDate] (and with it, the visible week strip) by [weeks] whole weeks — negative shifts backward. */
    fun shiftWeek(weeks: Long) {
        _uiState.value = _uiState.value.copy(selectedDate = _uiState.value.selectedDate.plusWeeks(weeks))
    }

    /**
     * Optimistic booking/waitlist actions — update [_uiState] immediately
     * (0ms perceived latency) instead of waiting on a network round-trip,
     * reverting on failure. Mirrors iOS's `ScheduleViewModel.bookClass`/
     * `cancelBooking`/`joinWaitlist`/`leaveWaitlist`.
     */
    fun cancel(classId: String) {
        val gymClass = _uiState.value.allClasses.firstOrNull { it.id == classId }
        val previousPlans = _uiState.value.activePlans
        val updatedPlans = if (gymClass != null) refundCreditLocally(previousPlans, gymClass) else previousPlans

        _uiState.value = _uiState.value.copy(
            bookedClassIds = _uiState.value.bookedClassIds - classId,
            activePlans = updatedPlans
        )
        updateLocalClass(classId) { it.copy(currentAttendees = (it.currentAttendees - 1).coerceAtLeast(0)) }

        viewModelScope.launch {
            try {
                repository.cancelBooking(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bookedClassIds = _uiState.value.bookedClassIds + classId,
                    activePlans = previousPlans,
                    errorMessage = "Failed to cancel: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(currentAttendees = it.currentAttendees + 1) }
            }
        }
    }

    fun leaveWaitlist(classId: String) {
        _uiState.value = _uiState.value.copy(waitlistedClassIds = _uiState.value.waitlistedClassIds - classId)
        updateLocalClass(classId) { it.copy(waitlistCount = (it.waitlistCount - 1).coerceAtLeast(0)) }

        viewModelScope.launch {
            try {
                repository.leaveWaitlist(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    waitlistedClassIds = _uiState.value.waitlistedClassIds + classId,
                    errorMessage = "Failed to leave waitlist: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(waitlistCount = it.waitlistCount + 1) }
            }
        }
    }

    fun book(classId: String) {
        val gymClass = _uiState.value.allClasses.firstOrNull { it.id == classId }
        val previousPlans = _uiState.value.activePlans
        val updatedPlans = if (gymClass != null) consumeCreditLocally(previousPlans, gymClass) else previousPlans

        _uiState.value = _uiState.value.copy(
            bookedClassIds = _uiState.value.bookedClassIds + classId,
            activePlans = updatedPlans,
            successMessage = SuccessMessage(
                title = "Booked!",
                message = "${gymClass?.title.orEmpty()} · ${gymClass?.formattedTime.orEmpty()}",
                isWaitlist = false
            )
        )
        updateLocalClass(classId) { it.copy(currentAttendees = it.currentAttendees + 1) }

        viewModelScope.launch {
            try {
                repository.bookClass(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bookedClassIds = _uiState.value.bookedClassIds - classId,
                    activePlans = previousPlans,
                    successMessage = null,
                    errorMessage = "Failed to book: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(currentAttendees = (it.currentAttendees - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun joinWaitlist(classId: String) {
        val gymClass = _uiState.value.allClasses.firstOrNull { it.id == classId }
        _uiState.value = _uiState.value.copy(
            waitlistedClassIds = _uiState.value.waitlistedClassIds + classId,
            successMessage = SuccessMessage(
                title = "Waitlisted!",
                message = "${gymClass?.title.orEmpty()} · ${gymClass?.formattedTime.orEmpty()}",
                isWaitlist = true
            )
        )
        updateLocalClass(classId) { it.copy(waitlistCount = it.waitlistCount + 1) }

        viewModelScope.launch {
            try {
                repository.joinWaitlist(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    waitlistedClassIds = _uiState.value.waitlistedClassIds - classId,
                    successMessage = null,
                    errorMessage = "Failed to join waitlist: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(waitlistCount = (it.waitlistCount - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    /**
     * Fetches this row's waitlist position (if the user is waitlisted for
     * it) and checked-in count (if [canManage]) — called from `ClassRow`
     * as each row appears, so the Schedule list can show the same
     * "Attendees: (x/y) · Waitlist: (...) · Checked In: (...)" text as
     * `ClassDetailScreen` without fetching this for every class up front.
     */
    fun loadRowDetails(classId: String, canManage: Boolean) {
        viewModelScope.launch {
            if (classId in _uiState.value.waitlistedClassIds) {
                val position = repository.fetchWaitlistPosition(gymId, classId)
                if (position != null) {
                    _uiState.value = _uiState.value.copy(waitlistPositions = _uiState.value.waitlistPositions + (classId to position))
                }
            }
            if (canManage) {
                val checkedInCount = repository.fetchAttendees(gymId, classId).count { it.isCheckedIn }
                _uiState.value = _uiState.value.copy(checkedInCounts = _uiState.value.checkedInCounts + (classId to checkedInCount))
            }
        }
    }

    private fun updateLocalClass(classId: String, transform: (GymClass) -> GymClass) {
        _uiState.value = _uiState.value.copy(
            allClasses = _uiState.value.allClasses.map { if (it.id == classId) transform(it) else it }
        )
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
