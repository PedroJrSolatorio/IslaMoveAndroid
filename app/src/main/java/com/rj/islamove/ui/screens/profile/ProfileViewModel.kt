package com.rj.islamove.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserPreferences
import com.rj.islamove.data.models.UserRatingStats
import com.rj.islamove.data.repository.ProfileRepository
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.RatingRepository
import com.rj.islamove.data.repository.DriverRepository
import com.rj.islamove.data.services.DriverLocationService
import com.rj.islamove.data.models.UserType
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
data class ProfileUiState(
    val user: User? = null,
    val userRatingStats: UserRatingStats? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false,
    val updateSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val ratingRepository: RatingRepository,
    private val auth: FirebaseAuth,
    private val driverRepository: DriverRepository,  // ADD THIS
    private val driverLocationService: DriverLocationService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadUserProfile()
    }
    
    /**
     * FR-2.2.1: Load user profile data from Firestore
     */
    fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "User not authenticated"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Use real-time flow to automatically sync with Firebase changes
            userRepository.getUserFlow(currentUser.uid).collect { user ->
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoading = false,
                        errorMessage = null
                    )
                    // Load rating stats
                    loadUserRatingStats(currentUser.uid)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load user profile"
                    )
                }
            }
        }
    }
    
    /**
     * FR-2.2.1: Update user profile data
     */
    fun updateProfile(
        context: Context,
        displayName: String,
        email: String,
        phoneNumber: String,
        profileImageUri: Uri? = null
    ) {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)

            try {
                var profileImageUrl: String? = null

                // Upload profile image if provided
                if (profileImageUri != null) {
                    profileRepository.uploadProfileImage(context, currentUser.uid, profileImageUri)
                        .onSuccess { url ->
                            profileImageUrl = url
                        }
                        .onFailure { exception ->
                            _uiState.value = _uiState.value.copy(
                                isUpdating = false,
                                errorMessage = "Failed to upload image: ${exception.message}"
                            )
                            return@launch
                        }
                }

                // Update profile data
                profileRepository.updateUserProfile(
                    uid = currentUser.uid,
                    displayName = displayName,
                    email = email,
                    phoneNumber = phoneNumber,
                    profileImageUrl = profileImageUrl
                ).onSuccess {
                    // Clear current user state and reload fresh data
                    _uiState.value = _uiState.value.copy(
                        user = null,
                        isUpdating = false,
                        updateSuccess = true
                    )
                    // Reload profile to get updated data immediately
                    loadUserProfile()
                }.onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = exception.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    errorMessage = e.message ?: "Update failed"
                )
            }
        }
    }
    
    /**
     * FR-2.2.3: Update user preferences
     */
    fun updatePreferences(preferences: UserPreferences) {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            profileRepository.updateUserPreferences(currentUser.uid, preferences)
                .onSuccess {
                    loadUserProfile() // Reload to get updated preferences
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = exception.message
                    )
                }
        }
    }
    
    /**
     * Clear update success state
     */
    fun clearUpdateSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * FR-2.2.1: Update user password
     */
    fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)

            try {
                profileRepository.updatePassword(currentPassword, newPassword)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateSuccess = true
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            errorMessage = exception.message
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    errorMessage = e.message ?: "Password update failed"
                )
            }
        }
    }

    /**
     * FR-2.2.1: Reset user password
     */
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)

            try {
                profileRepository.resetPassword(email)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateSuccess = true
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            errorMessage = exception.message
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    errorMessage = e.message ?: "Password reset failed"
                )
            }
        }
    }

    /**
     * Load user rating statistics
     */
    private fun loadUserRatingStats(userId: String) {
        viewModelScope.launch {
            try {
                ratingRepository.getUserRatingStats(userId)
                    .onSuccess { ratingStats ->
                        _uiState.value = _uiState.value.copy(
                            userRatingStats = ratingStats
                        )
                    }
                    .onFailure { exception ->
                        // Don't show error for rating stats as it's not critical
                        android.util.Log.w("ProfileViewModel", "Failed to load rating stats", exception)
                    }
            } catch (e: Exception) {
                android.util.Log.w("ProfileViewModel", "Error loading rating stats", e)
            }
        }
    }

    /**
     * Sign out user - sets driver offline before signing out
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser

                if (currentUser != null) {
                    android.util.Log.d("ProfileViewModel", "🚪 Starting logout process for user: ${currentUser.uid}")

                    // Check if user is a driver
                    val user = _uiState.value.user
                    if (user != null && user.userType == UserType.DRIVER) {
                        android.util.Log.d("ProfileViewModel", "👤 User is a DRIVER - setting offline and stopping location services...")

                        // STEP 1: Stop location service FIRST (prevents new updates)
                        try {
                            driverLocationService.setDriverOnlineStatus(false).onSuccess {
                                android.util.Log.d("ProfileViewModel", "✅ Location service stopped successfully")
                            }.onFailure { exception ->
                                android.util.Log.e("ProfileViewModel", "⚠️ Failed to stop location service", exception)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileViewModel", "⚠️ Error stopping location service", e)
                        }

                        // STEP 2: Set driver status to offline in repository
                        try {
                            driverRepository.updateDriverStatus(online = false).onSuccess {
                                android.util.Log.d("ProfileViewModel", "✅ Driver status set to OFFLINE in repository")
                            }.onFailure { exception ->
                                android.util.Log.e("ProfileViewModel", "⚠️ Failed to set driver offline in repository", exception)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileViewModel", "⚠️ Error setting driver offline", e)
                        }

                        // STEP 3: Explicitly update driver location with offline=false
                        try {
                            driverRepository.updateDriverLocation(
                                latitude = 0.0,  // Dummy coordinates
                                longitude = 0.0,
                                online = false  // CRITICAL: Set online to false
                            ).onSuccess {
                                android.util.Log.d("ProfileViewModel", "✅ Driver location updated with offline status")
                            }.onFailure { exception ->
                                android.util.Log.e("ProfileViewModel", "⚠️ Failed to update driver location", exception)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileViewModel", "⚠️ Error updating driver location", e)
                        }

                        // STEP 4: Wait a moment to ensure all updates complete
                        delay(500) // 500ms delay to ensure Firebase writes complete

                        android.util.Log.d("ProfileViewModel", "✅ Driver cleanup completed before logout")
                    } else {
                        android.util.Log.d("ProfileViewModel", "ℹ️ User is a PASSENGER or user data not available")
                    }
                } else {
                    android.util.Log.w("ProfileViewModel", "⚠️ No current user found")
                }

                // STEP 5: Now sign out from Firebase
                auth.signOut()
                android.util.Log.d("ProfileViewModel", "✅ User signed out successfully from Firebase")

            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "❌ Error during logout", e)
                // Still sign out even if there's an error
                auth.signOut()
            }
        }
    }
}