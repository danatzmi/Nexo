package com.nexo.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.calculateWeeklyAveragePreviousMonth
import com.nexo.app.domain.model.personalRecords as computePersonalRecords
import com.nexo.app.domain.model.previousMonthName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordination logic for the Logbook screen's two segments — kept out of
 * the Composable per CLAUDE.md's "business logic stays out of views"
 * rule. Mirrors `LogbookViewModel` on iOS: [UiState.activityTimeline]
 * (past bookings) backs the "Activity" segment;
 * [UiState.displayedMovements]/[UiState.personalRecords] back the
 * "Exercises" segment.
 */
class LogbookViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val workoutLogs: List<WorkoutLog> = emptyList(),
        val activityTimeline: List<GymClass> = emptyList(),
        val totalWorkouts: Int = 0,
        val formattedWeeklyAverage: String = "0.0",
        val previousMonthLabel: String = "",
        val errorMessage: String? = null
    ) {
        /** Every movement/activity name the member has logged, alphabetically — no fixed baseline list, so this fits any gym type. Mirrors iOS's `displayedMovements`. */
        val displayedMovements: List<String>
            get() = workoutLogs.map { it.movement }.toSortedSet().toList()

        /** The best-scored (or, if none are scored, earliest) logged entry per movement. Mirrors iOS's `personalRecords`. */
        val personalRecords: Map<String, WorkoutLog>
            get() = computePersonalRecords(workoutLogs)

        /** All logs for [movement], most recent first — backs the movement history sheet. Mirrors iOS's `logs(for:)`. */
        fun logs(movement: String): List<WorkoutLog> =
            workoutLogs.filter { it.movement == movement }.sortedByDescending { it.dateMillis }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val (logs, timeline) = fetchLogsAndTimeline()
                _uiState.value = UiState(
                    isLoading = false,
                    workoutLogs = logs,
                    activityTimeline = timeline,
                    totalWorkouts = timeline.size,
                    formattedWeeklyAverage = "%.1f".format(calculateWeeklyAveragePreviousMonth(timeline.map { it.startTimeMillis })),
                    previousMonthLabel = previousMonthName()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading logbook: ${e.message}")
            }
        }
    }

    /** Silently reloads activity timeline and workout logs without showing a full-screen loading spinner (e.g. on resume). */
    fun refresh() {
        viewModelScope.launch {
            try {
                val (logs, timeline) = fetchLogsAndTimeline()
                _uiState.value = _uiState.value.copy(
                    workoutLogs = logs,
                    activityTimeline = timeline,
                    totalWorkouts = timeline.size,
                    formattedWeeklyAverage = "%.1f".format(calculateWeeklyAveragePreviousMonth(timeline.map { it.startTimeMillis })),
                    previousMonthLabel = previousMonthName()
                )
            } catch (e: Exception) {
                // Keep existing state on silent refresh error
            }
        }
    }

    /** [UiState.activityTimeline] source is the signed-in user's own past bookings — mirrors iOS's `fetchMemberBookings(gymId:userId:)` filtered to `startTime < now`, rather than the older fetchClasses+fetchMyBookedClassIds combination this screen used before. */
    private suspend fun fetchLogsAndTimeline(): Pair<List<WorkoutLog>, List<GymClass>> {
        val uid = repository.currentUID()
        val logs = repository.fetchWorkoutLogs(gymId).sortedByDescending { it.dateMillis }
        val now = System.currentTimeMillis()
        val timeline = (uid?.let { repository.fetchMemberBookings(gymId, it) } ?: emptyList())
            .filter { it.startTimeMillis < now }
            .sortedByDescending { it.startTimeMillis }
        return logs to timeline
    }

    fun addLog(movement: String, score: Double?, reps: Int?, sets: Int?, dateMillis: Long) {
        viewModelScope.launch {
            try {
                repository.addWorkoutLog(
                    gymId,
                    WorkoutLog(id = UUID.randomUUID().toString(), movement = movement, score = score, reps = reps, sets = sets, dateMillis = dateMillis)
                )
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save activity: ${e.message}")
            }
        }
    }

    fun updateLog(log: WorkoutLog, movement: String, score: Double?, reps: Int?, sets: Int?, dateMillis: Long) {
        viewModelScope.launch {
            try {
                repository.updateWorkoutLog(gymId, log.copy(movement = movement, score = score, reps = reps, sets = sets, dateMillis = dateMillis))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update activity: ${e.message}")
            }
        }
    }

    fun deleteLog(logId: String) {
        viewModelScope.launch {
            try {
                repository.deleteWorkoutLog(gymId, logId)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete activity: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
