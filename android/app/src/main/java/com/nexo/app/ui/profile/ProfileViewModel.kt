package com.nexo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordination logic for the Profile screen — kept out of the Composable
 * per CLAUDE.md's "business logic stays out of views" rule. Only upcoming
 * bookings are surfaced here (no past-booking history), mirroring the
 * "Remove Booking History Card from Profile" decision already made on iOS.
 */
class ProfileViewModel(
    private val repository: BackendRepository,
    private val gymId: String
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val fullName: String = "",
        val email: String = "",
        val profilePicBase64: String? = null,
        val platformRole: PlatformRole = PlatformRole.USER,
        val gymName: String = "",
        val roleDisplayName: String = "",
        val userRole: UserRole? = null,
        val activePlans: List<ActivePlanItem> = emptyList(),
        val upcomingBookings: List<GymClass> = emptyList(),
        val actionInProgressClassId: String? = null,
        val isUploadingPhoto: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = repository.fetchMyProfile()
                val platformRole = repository.fetchPlatformRole()
                val gymEntry = repository.fetchMyGyms().firstOrNull { it.first.id == gymId }
                val classes = repository.fetchClasses(gymId)
                val bookedIds = repository.fetchMyBookedClassIds(gymId)
                val now = System.currentTimeMillis()
                val upcoming = classes
                    .filter { it.id in bookedIds && it.startTimeMillis >= now }
                    .sortedBy { it.startTimeMillis }
                val plans = repository.currentUID()?.let { repository.fetchActivePlans(gymId, it) }.orEmpty()

                _uiState.value = UiState(
                    isLoading = false,
                    fullName = profile?.fullName.orEmpty(),
                    email = profile?.email.orEmpty(),
                    profilePicBase64 = profile?.profilePicBase64,
                    platformRole = platformRole,
                    gymName = gymEntry?.first?.name.orEmpty(),
                    roleDisplayName = gymEntry?.second?.displayName.orEmpty(),
                    userRole = gymEntry?.second,
                    activePlans = plans,
                    upcomingBookings = upcoming
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Error loading profile: ${e.message}")
            }
        }
    }

    fun cancelBooking(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgressClassId = classId)
            try {
                repository.cancelBooking(gymId, classId)
                val classes = repository.fetchClasses(gymId)
                val bookedIds = repository.fetchMyBookedClassIds(gymId)
                val now = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    upcomingBookings = classes.filter { it.id in bookedIds && it.startTimeMillis >= now }.sortedBy { it.startTimeMillis }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to cancel: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(actionInProgressClassId = null)
            }
        }
    }

    /** [base64] is expected to already be downscaled/compressed — see `ProfilePhotoProcessing.kt`, invoked from the photo picker's callback before this is called. */
    fun updateProfilePicture(base64: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                repository.updateProfilePicture(base64)
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false, profilePicBase64 = base64)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false, errorMessage = "Failed to update profile picture: ${e.message}")
            }
        }
    }

    /** Silently reloads profile and upcoming bookings without showing a full-screen loading spinner (e.g. on resume after returning from ClassDetailScreen). */
    fun refresh() {
        viewModelScope.launch {
            try {
                val profile = repository.fetchMyProfile()
                val platformRole = repository.fetchPlatformRole()
                val gymEntry = repository.fetchMyGyms().firstOrNull { it.first.id == gymId }
                val classes = repository.fetchClasses(gymId)
                val bookedIds = repository.fetchMyBookedClassIds(gymId)
                val now = System.currentTimeMillis()
                val upcoming = classes
                    .filter { it.id in bookedIds && it.startTimeMillis >= now }
                    .sortedBy { it.startTimeMillis }
                val plans = repository.currentUID()?.let { repository.fetchActivePlans(gymId, it) }.orEmpty()

                _uiState.value = _uiState.value.copy(
                    fullName = profile?.fullName.orEmpty(),
                    email = profile?.email.orEmpty(),
                    profilePicBase64 = profile?.profilePicBase64,
                    platformRole = platformRole,
                    gymName = gymEntry?.first?.name.orEmpty(),
                    roleDisplayName = gymEntry?.second?.displayName.orEmpty(),
                    userRole = gymEntry?.second,
                    activePlans = plans,
                    upcomingBookings = upcoming
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
