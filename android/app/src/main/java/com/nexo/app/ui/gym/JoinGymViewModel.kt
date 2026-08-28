package com.nexo.app.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.Gym
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The search-first gym directory — mirrors iOS's `JoinGymView`. Kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class JoinGymViewModel(private val repository: BackendRepository) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val availableGyms: List<Gym> = emptyList(),
        val searchText: String = "",
        val joiningGymId: String? = null,
        val joinedGym: Gym? = null,
        val errorMessage: String? = null,
        val codeInput: String = "",
        val codeLookupResult: Gym? = null,
        val isLookingUpCode: Boolean = false,
        val isJoiningByCode: Boolean = false,
        val codeErrorMessage: String? = null
    ) {
        val filteredGyms: List<Gym>
            get() {
                val query = searchText.trim()
                if (query.isEmpty()) return availableGyms
                return availableGyms.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.city?.contains(query, ignoreCase = true) == true ||
                        it.joinCode?.contains(query, ignoreCase = true) == true
                }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val gyms = repository.fetchAvailableGyms()
                _uiState.value = _uiState.value.copy(isLoading = false, availableGyms = gyms)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading gyms: ${e.message}")
            }
        }
    }

    fun updateSearchText(value: String) {
        _uiState.value = _uiState.value.copy(searchText = value)
    }

    /** [BackendRepository] only exposes join-by-code (matching `FEEDBACK.md`'s method list — no generic `joinGym(gymId)`), so directory rows without a code (shouldn't happen for gyms created via [CreateGymViewModel], but not guaranteed for older/imported data) surface an error instead of silently doing nothing. */
    fun joinGym(gym: Gym) {
        val code = gym.joinCode
        if (code == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "This gym doesn't have a join code yet — ask them for an invite instead.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(joiningGymId = gym.id)
            try {
                repository.joinGymByCode(code)
                _uiState.value = _uiState.value.copy(joiningGymId = null, joinedGym = gym)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(joiningGymId = null, errorMessage = "Failed to join: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // MARK: - Fallback join-code entry

    fun updateCodeInput(value: String) {
        _uiState.value = _uiState.value.copy(codeInput = value, codeLookupResult = null, codeErrorMessage = null)
        val clean = value.trim().uppercase()
        if (clean.length >= 4) lookupCode(clean)
    }

    private fun lookupCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLookingUpCode = true)
            val gym = try {
                repository.fetchGymByJoinCode(code)
            } catch (e: Exception) {
                null
            }
            // Ignore a stale lookup if the user kept typing past the point this one started.
            if (_uiState.value.codeInput.trim().uppercase() == code) {
                _uiState.value = _uiState.value.copy(isLookingUpCode = false, codeLookupResult = gym)
            }
        }
    }

    fun submitCode() {
        val code = _uiState.value.codeInput.trim().uppercase()
        if (code.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isJoiningByCode = true, codeErrorMessage = null)
            try {
                val gym = repository.joinGymByCode(code)
                _uiState.value = _uiState.value.copy(isJoiningByCode = false, joinedGym = gym)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isJoiningByCode = false, codeErrorMessage = "Failed to join: ${e.message}")
            }
        }
    }
}
