package com.nexo.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
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
        val errorMessage: String? = null
    ) {
        val weekDates: List<LocalDate> get() = weekDatesFor(selectedDate)

        /** Classes from the live [BackendRepository.observeClasses] listener (all dates), filtered client-side to [selectedDate]. */
        val classesForSelectedDate: List<GymClass>
            get() = allClasses.filter { isOnLocalDate(it.startTimeMillis, selectedDate) }.sortedBy { it.startTimeMillis }
    }

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
     * `cancelBooking`/`joinWaitlist`/`leaveWaitlist`, which never re-fetch
     * after a successful mutation either — the live [observeClasses]
     * listener is what eventually reconciles `allClasses` with the
     * backend (it naturally re-emits once the write this triggers lands),
     * same as on iOS.
     *
     * Deliberately no "action in progress" flag: since `bookedClassIds`/
     * `waitlistedClassIds` flip synchronously below, the row's button
     * already switches to the opposite action (Book → Cancel, etc.)
     * before the network call even starts — disabling it during the
     * in-flight call would key off the *new* state and show the wrong
     * label/spinner for however long the call takes, which defeats the
     * instant feedback this is meant to give. A rapid double-tap is
     * harmless: `bookClass`/`joinWaitlist` are idempotent no-ops if
     * already booked/waitlisted, and by the time a second tap could land,
     * Compose has already recomposed the row into the other branch.
     */
    fun book(classId: String) {
        _uiState.value = _uiState.value.copy(bookedClassIds = _uiState.value.bookedClassIds + classId)
        updateLocalClass(classId) { it.copy(currentAttendees = it.currentAttendees + 1) }

        viewModelScope.launch {
            try {
                repository.bookClass(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bookedClassIds = _uiState.value.bookedClassIds - classId,
                    errorMessage = "Failed to book: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(currentAttendees = (it.currentAttendees - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun cancel(classId: String) {
        _uiState.value = _uiState.value.copy(bookedClassIds = _uiState.value.bookedClassIds - classId)
        updateLocalClass(classId) { it.copy(currentAttendees = (it.currentAttendees - 1).coerceAtLeast(0)) }

        viewModelScope.launch {
            try {
                repository.cancelBooking(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bookedClassIds = _uiState.value.bookedClassIds + classId,
                    errorMessage = "Failed to cancel: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(currentAttendees = it.currentAttendees + 1) }
            }
        }
    }

    fun joinWaitlist(classId: String) {
        _uiState.value = _uiState.value.copy(waitlistedClassIds = _uiState.value.waitlistedClassIds + classId)
        updateLocalClass(classId) { it.copy(waitlistCount = it.waitlistCount + 1) }

        viewModelScope.launch {
            try {
                repository.joinWaitlist(gymId, classId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    waitlistedClassIds = _uiState.value.waitlistedClassIds - classId,
                    errorMessage = "Failed to join waitlist: ${e.message}"
                )
                updateLocalClass(classId) { it.copy(waitlistCount = (it.waitlistCount - 1).coerceAtLeast(0)) }
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

    private fun updateLocalClass(classId: String, transform: (GymClass) -> GymClass) {
        _uiState.value = _uiState.value.copy(
            allClasses = _uiState.value.allClasses.map { if (it.id == classId) transform(it) else it }
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
