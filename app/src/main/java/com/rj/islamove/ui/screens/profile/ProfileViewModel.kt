package com.rj.islamove.ui.screens.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.User
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
data class ProfileUiState(
    val user: User? = null,
    val userRatingStats: UserRatingStats? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false,
    val updateSuccess: Boolean = false,
    val updateMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val ratingRepository: RatingRepository,
    private val auth: FirebaseAuth,
    private val driverRepository: DriverRepository,
    private val driverLocationService: DriverLocationService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()
    // Store the flow collection job so we can track it
    private var userFlowJob: kotlinx.coroutines.Job? = null
    
    init {
        loadUserProfile()
    }

    fun uploadProfileImage(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            _isUploading.value = true
            _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)

            try {
                Log.d("ProfileViewModel", "Starting profile image upload for user: ${currentUser.uid}")

                // Call repository to upload the image
                val uploadResult = profileRepository.uploadProfileImage(
                    context = context,
                    uid = currentUser.uid,
                    imageUri = imageUri
                )

                uploadResult.fold(
                    onSuccess = { imageUrl ->
                        // If upload is successful, update the user's profileImageUrl in Firestore
                        profileRepository.updateUserProfile(
                            uid = currentUser.uid,
                            profileImageUrl = imageUrl
                        ).onSuccess {
                            Log.d("ProfileViewModel", "Profile image URL saved to Firestore")
                            _uiState.value = _uiState.value.copy(
                                isUpdating = false,
                                updateSuccess = true,
                                updateMessage = "Profile picture updated successfully"
                            )

                            // Auto-clear success message after 2 seconds
                            viewModelScope.launch {
                                delay(2000)
                                _uiState.value = _uiState.value.copy(
                                    updateSuccess = false,
                                    updateMessage = null
                                )
                            }

                            // Reload user profile to update UI
                            loadUserProfile()
                        }.onFailure { exception ->
                            Log.e("ProfileViewModel", "Failed to save image URL", exception)
                            _uiState.value = _uiState.value.copy(
                                isUpdating = false,
                                errorMessage = "Failed to save profile picture: ${exception.message}"
                            )
                        }
                    },
                    onFailure = { exception ->
                        Log.e("ProfileViewModel", "Upload failed", exception)
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            errorMessage = "Upload failed: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Upload exception", e)
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    errorMessage = "Upload error: ${e.message}"
                )
            } finally {
                _isUploading.value = false
            }
        }
    }
    
    /**
     * FR-2.2.1: Load user profile data from Firestore
     */
    fun loadUserProfile() {
        android.util.Log.d("ProfileViewModel", "🔄 ========================================")
        android.util.Log.d("ProfileViewModel", "🔄 loadUserProfile() START")
        android.util.Log.d("ProfileViewModel", "🔄 ViewModel hashCode: ${this.hashCode()}")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            android.util.Log.e("ProfileViewModel", "❌ User not authenticated")
            _uiState.value = _uiState.value.copy(
                errorMessage = "User not authenticated"
            )
            return
        }

        Log.d("ProfileViewModel", "✅ Current user: ${currentUser.uid}")
        Log.d("ProfileViewModel", "✅ Current user email: ${currentUser.email}")

        // Cancel any existing flow collection
        userFlowJob?.cancel()
        Log.d("ProfileViewModel", "🔄 Cancelled existing flow job (if any)")

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        Log.d("ProfileViewModel", "🔄 Set loading state to true")

        // Launch flow collection in viewModelScope so it persists
        userFlowJob = viewModelScope.launch {
            Log.d("ProfileViewModel", "🔄 Launched coroutine in viewModelScope")
            Log.d("ProfileViewModel", "🔄 Starting getUserFlow collection for ${currentUser.uid}")
            Log.d("ProfileViewModel", "🔄 Coroutine context: ${coroutineContext}")

            try {
                userRepository.getUserFlow(currentUser.uid)
                    .catch { error ->
                        android.util.Log.e("ProfileViewModel", "❌ Flow error: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load user profile: ${error.message}"
                        )
                    }
                    .collect { user ->
                        Log.d("ProfileViewModel", "📱 ========== FLOW EMITTED NEW DATA ==========")
                        Log.d("ProfileViewModel", "📱 Thread: ${Thread.currentThread().name}")
                        Log.d("ProfileViewModel", "📱 ViewModel hashCode: ${this@ProfileViewModel.hashCode()}")
                        Log.d("ProfileViewModel", "📱 User: ${user?.displayName}")
                        Log.d("ProfileViewModel", "📱 User Type: ${user?.userType}")
                        Log.d("ProfileViewModel", "📱 Passenger Rating: ${user?.passengerRating}")
                        Log.d("ProfileViewModel", "📱 Passenger Total Trips: ${user?.passengerTotalTrips}")
                        Log.d("ProfileViewModel", "📱 Driver Rating: ${user?.driverData?.rating}")
                        Log.d("ProfileViewModel", "📱 Driver Total Trips: ${user?.driverData?.totalTrips}")
                        Log.d("ProfileViewModel", "📱 ==========================================")

                        if (user != null) {
                            _uiState.value = _uiState.value.copy(
                                user = user,
                                isLoading = false,
                                errorMessage = null
                            )

                            Log.d("ProfileViewModel", "✅ UI State updated successfully")
                            Log.d("ProfileViewModel", "✅ New state - Rating: ${_uiState.value.user?.passengerRating}, Trips: ${_uiState.value.user?.passengerTotalTrips}")

                            // Load rating stats each time user data updates
                            loadUserRatingStats(currentUser.uid)
                        } else {
                            Log.e("ProfileViewModel", "❌ User data is null")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "Failed to load user profile"
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ Exception in flow collection: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load user profile: ${e.message}"
                )
            }
        }

        Log.d("ProfileViewModel", "🔄 Flow job started with id: ${userFlowJob?.hashCode()}")
        Log.d("ProfileViewModel", "🔄 Flow job active: ${userFlowJob?.isActive}")
        Log.d("ProfileViewModel", "🔄 ========================================")
    }

    override fun onCleared() {
        super.onCleared()
        android.util.Log.d("ProfileViewModel", "🧹 ProfileViewModel.onCleared() called - ViewModel is being destroyed")
        userFlowJob?.cancel()
    }

    /**
     * Diagnostic function to check ViewModel state
     */
    fun logCurrentState() {
        android.util.Log.d("ProfileViewModel", "🔍 ========== DIAGNOSTIC INFO ==========")
        android.util.Log.d("ProfileViewModel", "🔍 ViewModel hashCode: ${this.hashCode()}")
        android.util.Log.d("ProfileViewModel", "🔍 Flow job active: ${userFlowJob?.isActive}")
        android.util.Log.d("ProfileViewModel", "🔍 Flow job completed: ${userFlowJob?.isCompleted}")
        android.util.Log.d("ProfileViewModel", "🔍 Flow job cancelled: ${userFlowJob?.isCancelled}")
        android.util.Log.d("ProfileViewModel", "🔍 Current UI State:")
        android.util.Log.d("ProfileViewModel", "🔍   - User: ${_uiState.value.user?.displayName}")
        android.util.Log.d("ProfileViewModel", "🔍   - Rating: ${_uiState.value.user?.passengerRating}")
        android.util.Log.d("ProfileViewModel", "🔍   - Trips: ${_uiState.value.user?.passengerTotalTrips}")
        android.util.Log.d("ProfileViewModel", "🔍   - Loading: ${_uiState.value.isLoading}")
        android.util.Log.d("ProfileViewModel", "🔍   - Error: ${_uiState.value.errorMessage}")
        android.util.Log.d("ProfileViewModel", "🔍 ========================================")
    }

    fun updatePhoneNumber(phoneNumber: String) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, updateMessage = null)

            try {
                profileRepository.updateUserProfile(
                    uid = currentUser.uid,
                    phoneNumber = phoneNumber.trim()
                ).onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateSuccess = true,
                        updateMessage = "Phone number updated successfully"
                    )
                    Log.d("ProfileViewModel", "✅ Phone number updated successfully")
                }.onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateMessage = "Error saving phone number: ${exception.message}"
                    )
                    Log.e("ProfileViewModel", "❌ Phone number update failed", exception)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    updateMessage = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Update password with success/error messaging
     */
    fun updatePassword(currentPassword: String, newPassword: String, onResult: (String) -> Unit) {
        val user = auth.currentUser
        val email = user?.email

        if (email == null) {
            onResult("User email not found")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, updateMessage = null)

            try {
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)

                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(newPassword)
                            .addOnSuccessListener {
                                _uiState.value = _uiState.value.copy(
                                    isUpdating = false,
                                    updateMessage = "Password updated successfully"
                                )
                                onResult("Password updated successfully")
                                Log.d("ProfileViewModel", "✅ Password updated successfully")
                            }
                            .addOnFailureListener { e ->
                                _uiState.value = _uiState.value.copy(
                                    isUpdating = false,
                                    updateMessage = "Error updating password: ${e.message}"
                                )
                                onResult("Error updating password: ${e.message}")
                                Log.e("ProfileViewModel", "❌ Password update failed", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateMessage = "Current password is incorrect"
                        )
                        onResult("Current password is incorrect")
                        Log.e("ProfileViewModel", "❌ Re-authentication failed", e)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    updateMessage = "Error: ${e.message}"
                )
                onResult("Error: ${e.message}")
            }
        }
    }

    /**
     * Clear update message
     */
    fun clearUpdateMessage() {
        _uiState.value = _uiState.value.copy(updateMessage = null)
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
     * Load user rating statistics
     */
    private fun loadUserRatingStats(userId: String) {
        viewModelScope.launch {
            try {
                ratingRepository.getUserRatingStats(userId)
                    .onSuccess { ratingStats ->
                        Log.d("ProfileViewModel", "📊 Rating stats loaded: ${ratingStats.totalRatings} ratings, avg: ${ratingStats.overallRating}")
                        _uiState.value = _uiState.value.copy(
                            userRatingStats = ratingStats
                        )
                    }
                    .onFailure { exception ->
                        // Don't show error for rating stats as it's not critical
                        Log.w("ProfileViewModel", "Failed to load rating stats", exception)
                    }
            } catch (e: Exception) {
                Log.w("ProfileViewModel", "Error loading rating stats", e)
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