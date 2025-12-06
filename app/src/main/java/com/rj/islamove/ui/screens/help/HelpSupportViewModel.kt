package com.rj.islamove.ui.screens.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.SupportRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.rj.islamove.data.models.SupportComment
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.User
import com.rj.islamove.data.repository.AuthRepository
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class HelpSupportUiState(
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HelpSupportViewModel @Inject constructor(
    private val supportRepository: SupportRepository,
    private val supportCommentRepository: SupportCommentRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpSupportUiState())
    val uiState: StateFlow<HelpSupportUiState> = _uiState.asStateFlow()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                authRepository.getUserData(firebaseUser.uid)
                    .onSuccess { user ->
                        _currentUser.value = user
                    }
                    .onFailure { error ->
                        android.util.Log.e("HelpSupportViewModel", "Failed to load user: ${error.message}")
                    }
            }
        }
    }

    fun requestAccountDeletion(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val result = authRepository.requestAccountDeletion(password)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Account deletion scheduled. You will be logged out."
                    )
                    delay(1500)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to schedule deletion"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.message ?: "An error occurred"
                )
            }
        }
    }

    fun cancelAccountDeletion() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val result = authRepository.cancelAccountDeletion()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Account deletion cancelled successfully"
                    )
                    loadCurrentUser() // Refresh user data
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to cancel deletion"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.message ?: "An error occurred"
                )
            }
        }
    }

    fun submitSupportTicket(description: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = true,
                    successMessage = null,
                    errorMessage = null
                )

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "You must be logged in to submit a support ticket"
                    )
                    return@launch
                }

                // Create support ticket
                val ticketId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                supportRepository.createSupportTicket(
                    ticketId = ticketId,
                    description = description,
                    timestamp = timestamp
                )

                // Get user info for the comment
                val userResult = userRepository.getUserByUid(currentUser.uid)
                val userName = userResult.getOrNull()?.displayName ?: "Unknown User"

                // Also save to support_comments collection so admin can see it
                val comment = SupportComment(
                    userId = currentUser.uid,
                    userName = userName,
                    message = description,
                    timestamp = timestamp
                )

                val commentResult = supportCommentRepository.submitComment(comment)

                if (commentResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Support ticket submitted successfully. Ticket ID: $ticketId"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Failed to submit support ticket. Please try again."
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Failed to submit support ticket. Please try again."
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }
}