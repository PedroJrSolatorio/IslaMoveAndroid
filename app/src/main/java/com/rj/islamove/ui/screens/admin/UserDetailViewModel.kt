package com.rj.islamove.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DriverReport
import com.rj.islamove.data.models.SupportComment
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.DriverReportRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDetailViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val driverReportRepository: DriverReportRepository,
    private val supportCommentRepository: SupportCommentRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    init {
        viewModelScope.launch {
            userRepository.tempFixPasswords()
        }
    }

    private val _uiState = MutableStateFlow(UserDetailUiState())
    val uiState: StateFlow<UserDetailUiState> = _uiState

    fun updateUserDiscount(userUid: String, discountPercentage: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            userRepository.updateUserDiscount(userUid, discountPercentage)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = if (discountPercentage != null) "Discount set to $discountPercentage%" else "Discount removed"
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to update discount: ${exception.message}"
                    )
                }
        }
    }

    fun updatePassengerVerification(userUid: String, isVerified: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            val newStatus = if (isVerified) DocumentStatus.APPROVED else DocumentStatus.REJECTED

            userRepository.updatePassengerDocumentStatus(userUid, newStatus)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = if (isVerified) "Passenger verified" else "Passenger verification removed"
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to update verification: ${exception.message}"
                    )
                }
        }
    }

    fun updateUserPersonalInfo(userUid: String, updatedInfo: Map<String, Any>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            userRepository.updateUserPersonalInfo(userUid, updatedInfo)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Personal information updated"
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to update personal info: ${exception.message}"
                    )
                }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            try {
                firebaseAuth.sendPasswordResetEmail(email)
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    actionMessage = "Password reset email sent to $email"
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingAction = false,
                    errorMessage = "Failed to send password reset email: ${exception.message}"
                )
            }
        }
    }

    fun updateUserPassword(uid: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAction = true)

            userRepository.updateUserPassword(uid, newPassword)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        actionMessage = "Password updated successfully"
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to update password: ${exception.message}"
                    )
                }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            actionMessage = null,
            errorMessage = null
        )
    }

    fun loadDriverReports(driverId: String) {
        viewModelScope.launch {
            try {
                val result = driverReportRepository.getReportsForDriver(driverId)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        driverReports = result.getOrNull() ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to load driver reports: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error loading driver reports: ${e.message}"
                )
            }
        }
    }

    fun loadSupportComments(userId: String) {
        viewModelScope.launch {
            try {
                val result = supportCommentRepository.getUserComments(userId)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        supportComments = result.getOrNull()?.sortedByDescending { it.timestamp } ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to load support comments: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error loading support comments: ${e.message}"
                )
            }
        }
    }
}

data class UserDetailUiState(
    val isProcessingAction: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val driverReports: List<DriverReport> = emptyList(),
    val supportComments: List<SupportComment> = emptyList()
)