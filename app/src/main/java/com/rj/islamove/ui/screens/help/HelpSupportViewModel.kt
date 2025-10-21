package com.rj.islamove.ui.screens.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.SupportRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.rj.islamove.data.models.SupportComment
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpSupportUiState())
    val uiState: StateFlow<HelpSupportUiState> = _uiState.asStateFlow()

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