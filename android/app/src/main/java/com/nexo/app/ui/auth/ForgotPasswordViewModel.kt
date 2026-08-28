package com.nexo.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for [ForgotPasswordSheet] — mirrors iOS's `ForgotPasswordViewModel`. */
class ForgotPasswordViewModel(private val repository: BackendRepository) : ViewModel() {

    data class UiState(
        val email: String = "",
        val isLoading: Boolean = false,
        val didSucceed: Boolean = false,
        val errorMessage: String? = null
    ) {
        val isValid: Boolean get() = email.isNotBlank() && email.contains("@")
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, didSucceed = false)
    }

    fun sendResetLink() {
        val state = _uiState.value
        if (!state.isValid || state.isLoading) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                repository.sendPasswordReset(state.email.trim())
                _uiState.value = _uiState.value.copy(isLoading = false, didSucceed = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Something went wrong. Please try again.")
            }
        }
    }
}
