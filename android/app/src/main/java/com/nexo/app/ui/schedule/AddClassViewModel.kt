package com.nexo.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.RecurrenceType
import com.nexo.app.domain.model.generateRecurrenceDates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Coordination logic for [AddClassSheet] — create or edit a class, with
 * recurring-series creation and the "this class only" vs. "this & future"
 * edit-series choice. Kept out of the Composable per CLAUDE.md's
 * "business logic stays out of views" rule. Mirrors `AddClassView`'s
 * inline state on iOS (that view has no separate view model — its
 * `@State` vars are the model — but Android keeps ViewModels for all
 * screens per this codebase's existing convention).
 */
class AddClassViewModel(
    private val repository: BackendRepository,
    private val gymId: String,
    private val availableClassTypes: List<String>,
    private val existingClass: GymClass?
) : ViewModel() {

    val isEditMode: Boolean = existingClass != null

    data class UiState(
        val classType: String,
        val coach: String,
        val isPremium: Boolean,
        val description: String,
        val startTimeMillis: Long,
        val durationMinutes: Int,
        val capacity: Int,
        val recurrenceType: RecurrenceType = RecurrenceType.NONE,
        val selectedWeekdays: Set<Int> = emptySet(),
        val repeatEndMillis: Long,
        val availableCoaches: List<String> = emptyList(),
        val isLoadingCoaches: Boolean = true,
        val isSaving: Boolean = false,
        val showSeriesPrompt: Boolean = false,
        val didSave: Boolean = false,
        val errorMessage: String? = null
    ) {
        val isValid: Boolean get() = classType.isNotBlank() && durationMinutes in 15..180 && capacity in 1..50
    }

    private val _uiState = MutableStateFlow(
        UiState(
            classType = existingClass?.classType ?: availableClassTypes.firstOrNull() ?: "CrossFit WOD",
            coach = existingClass?.coach ?: "",
            isPremium = existingClass?.isPremium ?: false,
            description = existingClass?.description ?: "",
            startTimeMillis = existingClass?.startTimeMillis ?: (System.currentTimeMillis() + 3_600_000L),
            durationMinutes = existingClass?.durationMinutes ?: 60,
            capacity = existingClass?.capacity ?: 12,
            repeatEndMillis = defaultRepeatEndMillis()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadCoaches() }

    private fun loadCoaches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCoaches = true)
            try {
                val team = repository.fetchTeam(gymId)
                var names = team.filter { it.role.canManageClasses }.map { it.fullName }
                val existingCoach = existingClass?.coach
                if (!existingCoach.isNullOrBlank() && existingCoach !in names) names = names + existingCoach
                _uiState.value = _uiState.value.copy(isLoadingCoaches = false, availableCoaches = names.sorted())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingCoaches = false, errorMessage = "Error loading coaches: ${e.message}")
            }
        }
    }

    fun updateClassType(value: String) { _uiState.value = _uiState.value.copy(classType = value) }
    fun updateCoach(value: String) { _uiState.value = _uiState.value.copy(coach = value) }
    fun updateIsPremium(value: Boolean) { _uiState.value = _uiState.value.copy(isPremium = value) }
    fun updateDescription(value: String) { _uiState.value = _uiState.value.copy(description = value) }
    fun updateStartTime(value: Long) { _uiState.value = _uiState.value.copy(startTimeMillis = value) }
    fun updateDurationMinutes(value: Int) { _uiState.value = _uiState.value.copy(durationMinutes = value.coerceIn(15, 180)) }
    fun updateCapacity(value: Int) { _uiState.value = _uiState.value.copy(capacity = value.coerceIn(1, 50)) }
    fun updateRecurrenceType(value: RecurrenceType) { _uiState.value = _uiState.value.copy(recurrenceType = value) }
    fun updateRepeatEndMillis(value: Long) { _uiState.value = _uiState.value.copy(repeatEndMillis = value) }

    fun toggleWeekday(day: Int) {
        val current = _uiState.value.selectedWeekdays
        _uiState.value = _uiState.value.copy(selectedWeekdays = if (day in current) current - day else current + day)
    }

    /** Tapping Save on an existing recurring class needs a "this class only" vs. "this & future" choice first; every other case saves immediately. */
    fun handleSaveTapped() {
        if (isEditMode && existingClass?.seriesId != null) {
            _uiState.value = _uiState.value.copy(showSeriesPrompt = true)
        } else {
            save(applyToSeries = false)
        }
    }

    fun dismissSeriesPrompt() {
        _uiState.value = _uiState.value.copy(showSeriesPrompt = false)
    }

    fun save(applyToSeries: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, showSeriesPrompt = false, errorMessage = null)
            try {
                if (existingClass != null) {
                    val updated = existingClass.copy(
                        title = state.classType,
                        coach = state.coach,
                        startTimeMillis = state.startTimeMillis,
                        durationMinutes = state.durationMinutes,
                        capacity = state.capacity,
                        classType = state.classType,
                        isPremium = state.isPremium,
                        description = state.description
                    )
                    if (applyToSeries && existingClass.seriesId != null) {
                        repository.updateClassSeries(gymId, existingClass.seriesId, existingClass.startTimeMillis, updated)
                    } else {
                        repository.updateClass(gymId, updated)
                    }
                } else if (state.recurrenceType != RecurrenceType.NONE) {
                    val seriesId = UUID.randomUUID().toString()
                    val dates = generateRecurrenceDates(state.recurrenceType, state.startTimeMillis, state.repeatEndMillis, state.selectedWeekdays)
                    val instances = dates.map { date -> newClass(state, startTimeMillis = date, seriesId = seriesId) }
                    repository.createClasses(gymId, instances)
                } else {
                    repository.createClass(gymId, newClass(state, startTimeMillis = state.startTimeMillis, seriesId = null))
                }
                _uiState.value = _uiState.value.copy(isSaving = false, didSave = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = "Error saving class: ${e.message}")
            }
        }
    }

    private fun newClass(state: UiState, startTimeMillis: Long, seriesId: String?) = GymClass(
        id = UUID.randomUUID().toString(),
        title = state.classType,
        coach = state.coach,
        startTimeMillis = startTimeMillis,
        capacity = state.capacity,
        currentAttendees = 0,
        durationMinutes = state.durationMinutes,
        description = state.description,
        classType = state.classType,
        isPremium = state.isPremium,
        seriesId = seriesId
    )

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

private fun defaultRepeatEndMillis(): Long =
    Instant.now().atZone(ZoneId.systemDefault()).plusMonths(3).toInstant().toEpochMilli()
