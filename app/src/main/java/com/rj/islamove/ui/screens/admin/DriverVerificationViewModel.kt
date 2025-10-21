package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserVerificationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Driver verification lists
    val pendingDrivers: List<User> = emptyList(),
    val underReviewDrivers: List<User> = emptyList(),
    val rejectedDrivers: List<User> = emptyList(),
    val approvedDrivers: List<User> = emptyList(),

    // Passenger verification lists
    val passengersWithStudentDocuments: List<User> = emptyList(),
    val allPassengers: List<User> = emptyList(),

    // General user verification lists
    val usersRequiringVerification: List<User> = emptyList(),

    // Action states
    val isProcessingAction: Boolean = false,
    val actionMessage: String? = null
)

@HiltViewModel
class DriverVerificationViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserVerificationUiState())
    val uiState: StateFlow<UserVerificationUiState> = _uiState.asStateFlow()

    init {
        loadPendingUsers()
    }

    fun loadPendingUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                combine(
                    userRepository.getUsersByType(UserType.DRIVER),
                    userRepository.getUsersByType(UserType.PASSENGER),
                    userRepository.getPassengersWithStudentDocuments()
                ) { driverResult, allPassengerResult, studentPassengerResult ->
                    Triple(driverResult, allPassengerResult, studentPassengerResult)
                }.collect { (driverResult, allPassengerResult, studentPassengerResult) ->

                    var hasError = false
                    var errorMessage = ""

                    // Process driver results
                    driverResult.onSuccess { drivers ->
                        val pendingDrivers = drivers.filter {
                            it.driverData?.verificationStatus == VerificationStatus.PENDING ||
                            it.driverData?.verificationStatus == VerificationStatus.UNDER_REVIEW
                        }
                        val underReviewDrivers = drivers.filter {
                            it.driverData?.verificationStatus == VerificationStatus.UNDER_REVIEW
                        }
                        val rejectedDrivers = drivers.filter {
                            it.driverData?.verificationStatus == VerificationStatus.REJECTED
                        }
                        val approvedDrivers = drivers.filter {
                            it.driverData?.verificationStatus == VerificationStatus.APPROVED
                        }

                        _uiState.value = _uiState.value.copy(
                            pendingDrivers = pendingDrivers.sortedByDescending { it.createdAt },
                            underReviewDrivers = underReviewDrivers.sortedByDescending { it.updatedAt },
                            rejectedDrivers = rejectedDrivers.sortedByDescending { it.updatedAt },
                            approvedDrivers = approvedDrivers.sortedByDescending { it.updatedAt }
                        )

                        Log.d("UserVerificationVM", "Loaded ${pendingDrivers.size} pending drivers")
                    }.onFailure { exception ->
                        Log.e("UserVerificationVM", "Error loading drivers", exception)
                        hasError = true
                        errorMessage = "Failed to load drivers: ${exception.message}"
                    }

                    // Process all passengers
                    allPassengerResult.onSuccess { allPassengers ->
                        // Filter passengers that might need verification (e.g., incomplete profiles, flagged accounts)
                        val usersRequiringVerification = allPassengers.filter { user ->
                            user.displayName.isBlank() ||
                            user.phoneNumber.isBlank() ||
                            !user.isActive
                        }

                        _uiState.value = _uiState.value.copy(
                            allPassengers = allPassengers.sortedByDescending { it.createdAt },
                            usersRequiringVerification = usersRequiringVerification.sortedByDescending { it.createdAt }
                        )

                        Log.d("UserVerificationVM", "Loaded ${allPassengers.size} total passengers, ${usersRequiringVerification.size} requiring verification")
                    }.onFailure { exception ->
                        Log.e("UserVerificationVM", "Error loading all passengers", exception)
                        hasError = true
                        errorMessage = "Failed to load all passengers: ${exception.message}"
                    }

                    // Process passengers with student documents
                    studentPassengerResult.onSuccess { studentPassengers ->
                        _uiState.value = _uiState.value.copy(
                            passengersWithStudentDocuments = studentPassengers.sortedByDescending {
                                it.studentDocument?.uploadedAt ?: it.createdAt
                            }
                        )

                        Log.d("UserVerificationVM", "Loaded ${studentPassengers.size} passengers with student documents")
                    }.onFailure { exception ->
                        Log.e("UserVerificationVM", "Error loading student passengers", exception)
                        hasError = true
                        errorMessage = "Failed to load student passengers: ${exception.message}"
                    }

                    // Update loading state
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = if (hasError) errorMessage else null
                    )
                }
            } catch (e: Exception) {
                Log.e("UserVerificationVM", "Error in loadPendingUsers", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading users: ${e.message}"
                )
            }
        }
    }


    fun approveDriver(driverUid: String, notes: String = "Approved by admin") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)
            
            try {
                userRepository.updateDriverVerificationStatus(
                    uid = driverUid,
                    status = VerificationStatus.APPROVED,
                    notes = notes
                ).onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver approved successfully"
                    )
                    
                    // Refresh the data
                    loadPendingUsers()
                    
                    Log.d("DriverVerificationVM", "Driver $driverUid approved")
                }.onFailure { exception ->
                    Log.e("DriverVerificationVM", "Error approving driver", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to approve driver: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverVerificationVM", "Error in approveDriver", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error approving driver: ${e.message}"
                )
            }
        }
    }

    fun rejectDriver(driverUid: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)
            
            try {
                userRepository.updateDriverVerificationStatus(
                    uid = driverUid,
                    status = VerificationStatus.REJECTED,
                    notes = "Rejected: $reason"
                ).onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver rejected"
                    )
                    
                    // Refresh the data
                    loadPendingUsers()
                    
                    Log.d("DriverVerificationVM", "Driver $driverUid rejected: $reason")
                }.onFailure { exception ->
                    Log.e("DriverVerificationVM", "Error rejecting driver", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to reject driver: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverVerificationVM", "Error in rejectDriver", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error rejecting driver: ${e.message}"
                )
            }
        }
    }

    fun markDriverUnderReview(driverUid: String, notes: String = "Under review by admin") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)
            
            try {
                userRepository.updateDriverVerificationStatus(
                    uid = driverUid,
                    status = VerificationStatus.UNDER_REVIEW,
                    notes = notes
                ).onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver marked for review"
                    )
                    
                    // Refresh the data
                    loadPendingUsers()
                    
                    Log.d("DriverVerificationVM", "Driver $driverUid marked for review")
                }.onFailure { exception ->
                    Log.e("DriverVerificationVM", "Error marking driver for review", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to mark driver for review: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverVerificationVM", "Error in markDriverUnderReview", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error marking driver for review: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    fun refreshDrivers() {
        loadPendingUsers()
    }
}