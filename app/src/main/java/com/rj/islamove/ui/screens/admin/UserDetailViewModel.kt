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
}

data class UserDetailUiState(
    val isProcessingAction: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val driverReports: List<DriverReport> = emptyList(),
    val supportComments: List<SupportComment> = emptyList()
)