package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverDetailsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val driver: User? = null,
    val isProcessingAction: Boolean = false,
    val actionMessage: String? = null
)

@HiltViewModel
class DriverDetailsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDetailsUiState())
    val uiState: StateFlow<DriverDetailsUiState> = _uiState.asStateFlow()

    fun loadDriverDetails(driverUid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                userRepository.getUserByUid(driverUid)
                    .onSuccess { driver ->
                        Log.d("DriverDetailsVM", "Loaded driver details for $driverUid")
                        Log.d("DriverDetailsVM", "Driver discount: ${driver.discountPercentage}")
                        Log.d("DriverDetailsVM", "Driver type: ${driver.userType}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            driver = driver,
                            errorMessage = null
                        )
                    }
                    .onFailure { exception ->
                        Log.e("DriverDetailsVM", "Error loading driver details", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load driver details: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in loadDriverDetails", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading driver details: ${e.message}"
                )
            }
        }
    }

    fun approveDriver(driverUid: String, notes: String = "Approved by admin") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                // First, approve all documents that are currently under review in bulk
                userRepository.approveAllDriverDocuments(driverUid)

                // Then update the overall driver verification status
                userRepository.updateDriverVerificationStatus(
                    uid = driverUid,
                    status = VerificationStatus.APPROVED,
                    notes = notes.ifEmpty { "Approved by admin" }
                ).onSuccess {
                    // Reload the driver details to get updated status
                    loadDriverDetails(driverUid)

                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver approved successfully"
                    )

                    Log.d("DriverDetailsVM", "Driver $driverUid approved")
                }.onFailure { exception ->
                    Log.e("DriverDetailsVM", "Error approving driver", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to approve driver: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in approveDriver", e)
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
                val rejectionNotes = "Rejected: ${reason.trim()}"

                // First, reject all documents that are currently under review in bulk
                userRepository.rejectAllDriverDocuments(driverUid, reason)

                // Then update the overall driver verification status
                userRepository.updateDriverVerificationStatus(
                    uid = driverUid,
                    status = VerificationStatus.REJECTED,
                    notes = rejectionNotes
                ).onSuccess {
                    // Reload the driver details to get updated status
                    loadDriverDetails(driverUid)

                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver rejected"
                    )

                    Log.d("DriverDetailsVM", "Driver $driverUid rejected: $reason")
                }.onFailure { exception ->
                    Log.e("DriverDetailsVM", "Error rejecting driver", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to reject driver: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in rejectDriver", e)
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
                    // Reload the driver details to get updated status
                    loadDriverDetails(driverUid)
                    
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Driver marked for review"
                    )
                    
                    Log.d("DriverDetailsVM", "Driver $driverUid marked for review")
                }.onFailure { exception ->
                    Log.e("DriverDetailsVM", "Error marking driver for review", exception)
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to mark driver for review: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in markDriverUnderReview", e)
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

    fun approveDocument(driverUid: String, documentType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                if (documentType == "passenger_id") {
                    // Handle passenger ID document approval
                    userRepository.approveStudentDocument(driverUid)
                        .onSuccess {
                            // Force reload from Firestore to ensure state is synchronized
                            loadDriverDetails(driverUid)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                actionMessage = "ID document approved successfully"
                            )
                            Log.d("DriverDetailsVM", "Passenger ID document approved for $driverUid")
                        }.onFailure { exception ->
                            Log.e("DriverDetailsVM", "Error approving passenger ID document", exception)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                errorMessage = "Failed to approve ID document: ${exception.message}"
                            )
                        }
                } else {
                    // Handle driver document approval
                    userRepository.approveDocument(driverUid, documentType)
                        .onSuccess {
                            // Force reload from Firestore to ensure state is synchronized
                            loadDriverDetails(driverUid)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                actionMessage = "Document approved successfully"
                            )
                            Log.d("DriverDetailsVM", "Document $documentType approved for driver $driverUid")
                        }.onFailure { exception ->
                            Log.e("DriverDetailsVM", "Error approving document", exception)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                errorMessage = "Failed to approve document: ${exception.message}"
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in approveDocument", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error approving document: ${e.message}"
                )
            }
        }
    }

    fun rejectDocument(driverUid: String, documentType: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                if (documentType == "passenger_id") {
                    // Handle passenger ID document rejection
                    userRepository.rejectStudentDocument(driverUid, reason)
                        .onSuccess {
                            // Force reload from Firestore to ensure state is synchronized
                            loadDriverDetails(driverUid)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                actionMessage = "ID document rejected"
                            )
                            Log.d("DriverDetailsVM", "Passenger ID document rejected for $driverUid: $reason")
                        }.onFailure { exception ->
                            Log.e("DriverDetailsVM", "Error rejecting passenger ID document", exception)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                errorMessage = "Failed to reject ID document: ${exception.message}"
                            )
                        }
                } else {
                    // Handle driver document rejection
                    userRepository.rejectDocument(driverUid, documentType, reason)
                        .onSuccess {
                            // Force reload from Firestore to ensure state is synchronized
                            loadDriverDetails(driverUid)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                actionMessage = "Document rejected"
                            )
                            Log.d("DriverDetailsVM", "Document $documentType rejected for driver $driverUid: $reason")
                        }.onFailure { exception ->
                            Log.e("DriverDetailsVM", "Error rejecting document", exception)
                            _uiState.value = _uiState.value.copy(
                                isProcessingAction = false,
                                errorMessage = "Failed to reject document: ${exception.message}"
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in rejectDocument", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error rejecting document: ${e.message}"
                )
            }
        }
    }

    fun approveStudentDocument(studentUid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                userRepository.approveStudentDocument(studentUid)
                    .onSuccess {
                        // Reload the student details to get updated status
                        loadDriverDetails(studentUid)

                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            actionMessage = "ID verification approved successfully"
                        )

                        Log.d("DriverDetailsVM", "ID verification for $studentUid approved")
                    }.onFailure { exception ->
                        Log.e("DriverDetailsVM", "Error approving ID verification", exception)
                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            errorMessage = "Failed to approve ID verification: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in approveStudentDocument", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error approving student verification: ${e.message}"
                )
            }
        }
    }

    fun rejectStudentDocument(studentUid: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                userRepository.rejectStudentDocument(studentUid, reason)
                    .onSuccess {
                        // Reload the student details to get updated status
                        loadDriverDetails(studentUid)

                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            actionMessage = "ID verification rejected"
                        )

                        Log.d("DriverDetailsVM", "ID verification for $studentUid rejected: $reason")
                    }.onFailure { exception ->
                        Log.e("DriverDetailsVM", "Error rejecting ID verification", exception)
                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            errorMessage = "Failed to reject ID verification: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in rejectStudentDocument", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error rejecting student verification: ${e.message}"
                )
            }
        }
    }

    fun updateUserDiscount(userUid: String, discountPercentage: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                userRepository.updateUserDiscount(userUid, discountPercentage)
                    .onSuccess {
                        // Reload the user details to get updated discount
                        loadDriverDetails(userUid)

                        val message = if (discountPercentage != null) {
                            "Discount set to $discountPercentage%"
                        } else {
                            "Discount removed"
                        }

                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            actionMessage = message
                        )

                        Log.d("DriverDetailsVM", "User $userUid discount updated to $discountPercentage%")
                    }.onFailure { exception ->
                        Log.e("DriverDetailsVM", "Error updating user discount", exception)
                        _uiState.value = _uiState.value.copy(
                            isProcessingAction = false,
                            errorMessage = "Failed to update discount: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("DriverDetailsVM", "Error in updateUserDiscount", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Error updating discount: ${e.message}"
                )
            }
        }
    }
}