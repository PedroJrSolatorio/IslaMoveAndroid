package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.BookingRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminHomeUiState(
    val adminUser: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    
    // Dashboard Statistics
    val pendingDriversCount: Int = 0,
    val activeRidesCount: Int = 0,
    val onlineDriversCount: Int = 0,
    val verifiedDriversCount: Int = 0,
    val totalUsersCount: Int = 0,
    
    // System Status
    val systemHealth: SystemHealth = SystemHealth.GOOD
)

enum class SystemHealth {
    GOOD, WARNING, ERROR
}

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminHomeUiState())
    val uiState: StateFlow<AdminHomeUiState> = _uiState.asStateFlow()

    init {
        loadAdminDashboard()
    }

    private fun loadAdminDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Load current admin user
                loadCurrentAdminUser()
                
                // Load dashboard statistics
                loadDashboardStats()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                
            } catch (e: Exception) {
                Log.e("AdminHomeViewModel", "Error loading admin dashboard", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load dashboard: ${e.message}"
                )
            }
        }
    }

    private fun loadCurrentAdminUser() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId != null) {
                userRepository.getUserByUid(currentUserId)
                    .onSuccess { user ->
                        if (user.userType == UserType.ADMIN) {
                            _uiState.value = _uiState.value.copy(adminUser = user)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Unauthorized: Admin access required"
                            )
                        }
                    }
                    .onFailure { exception ->
                        Log.e("AdminHomeViewModel", "Error loading current user", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to load user: ${exception.message}"
                        )
                    }
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No authenticated user"
                )
            }
        }
    }

    private fun loadDashboardStats() {
        viewModelScope.launch {
            try {
                // Use collect() for real-time users updates
                userRepository.getAllUsers().collect { result ->
                    result.onSuccess { users ->
                        val pendingDrivers = users.count { user ->
                            user.userType == UserType.DRIVER &&
                            (user.driverData?.verificationStatus == VerificationStatus.PENDING ||
                             user.driverData?.verificationStatus == VerificationStatus.UNDER_REVIEW ||
                             user.driverData?.verificationStatus == VerificationStatus.REJECTED)
                        }

                        val pendingPassengers = users.count { user ->
                            user.userType == UserType.PASSENGER &&
                            (user.studentDocument?.status == DocumentStatus.PENDING_REVIEW ||
                             user.studentDocument?.status == DocumentStatus.PENDING ||
                             user.studentDocument?.status == DocumentStatus.REJECTED)
                        }

                        val totalPendingApplications = pendingDrivers + pendingPassengers

                        val verifiedDrivers = users.count { user ->
                            user.userType == UserType.DRIVER &&
                            user.driverData?.verificationStatus == VerificationStatus.APPROVED
                        }

                        val onlineDrivers = users.count { user ->
                            user.userType == UserType.DRIVER &&
                            user.driverData?.online == true &&
                            user.driverData?.verificationStatus == VerificationStatus.APPROVED
                        }

                        val totalUsers = users.size

                        _uiState.value = _uiState.value.copy(
                            pendingDriversCount = totalPendingApplications,
                            onlineDriversCount = onlineDrivers,
                            verifiedDriversCount = verifiedDrivers,
                            totalUsersCount = totalUsers
                        )

                        // Log real-time updates for debugging
                        Log.d("AdminHomeViewModel", "Real-time update: Pending=$totalPendingApplications, Online=$onlineDrivers, Verified=$verifiedDrivers, Total=$totalUsers")
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminHomeViewModel", "Error loading dashboard statistics", e)
                // Don't crash on stats loading failure
                _uiState.value = _uiState.value.copy(
                    errorMessage = null // Clear any previous errors
                )
            }
        }

        // Separate coroutine for active bookings to avoid conflicts
        viewModelScope.launch {
            try {
                bookingRepository.getActiveBookings().collect { result ->
                    result.onSuccess { bookings ->
                        _uiState.value = _uiState.value.copy(
                            activeRidesCount = bookings.size
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminHomeViewModel", "Error loading active bookings", e)
            }
        }
    }

    fun refreshDashboard() {
        loadDashboardStats()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}