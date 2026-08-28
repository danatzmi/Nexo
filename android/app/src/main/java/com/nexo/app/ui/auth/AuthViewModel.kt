package com.nexo.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordination logic for [AuthScreen] — kept out of the Composable per CLAUDE.md's "business logic stays out of views" rule. */
class AuthViewModel(private val repository: BackendRepository) : ViewModel() {

    enum class Mode { LOGIN, SIGN_UP }

    data class UiState(
        val mode: Mode = Mode.LOGIN,
        val email: String = "",
        val password: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    ) {
        val canSubmit: Boolean
            get() = email.isNotBlank() && password.isNotBlank() &&
                (mode == Mode.LOGIN || (firstName.isNotBlank() && lastName.isNotBlank()))
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            mode = if (_uiState.value.mode == Mode.LOGIN) Mode.SIGN_UP else Mode.LOGIN,
            errorMessage = null
        )
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun updateFirstName(value: String) {
        _uiState.value = _uiState.value.copy(firstName = value)
    }

    fun updateLastName(value: String) {
        _uiState.value = _uiState.value.copy(lastName = value)
    }

    fun submit(onAuthenticated: () -> Unit) {
        val state = _uiState.value
        if (!state.canSubmit || state.isLoading) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                if (state.mode == Mode.LOGIN) {
                    repository.signIn(state.email.trim(), state.password)
                } else {
                    repository.signUp(state.email.trim(), state.password, state.firstName.trim(), state.lastName.trim())
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onAuthenticated()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Something went wrong. Please try again.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
