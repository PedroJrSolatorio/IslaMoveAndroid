package com.rj.islamove.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class SplashUiState(
    val isLoading: Boolean = true,
    val isUserLoggedIn: Boolean = false,
    val userType: UserType? = null,
    val needsUserTypeSelection: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkAuthenticationState()
    }

    private fun checkAuthenticationState() {
        viewModelScope.launch {
            // Show splash for minimum 2 seconds
            delay(2000)

            val currentUser = auth.currentUser
            if (currentUser != null) {
                // User is logged in, check if they have user data in Firestore
                authRepository.getUserData(currentUser.uid)
                    .onSuccess { user ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUserLoggedIn = true,
                            userType = user.userType
                        )
                    }
                    .onFailure {
                        // User is authenticated but no user data in Firestore
                        // This means they need to select user type
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUserLoggedIn = true,
                            needsUserTypeSelection = true
                        )
                    }
            } else {
                // User is not logged in
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isUserLoggedIn = false
                )
            }
        }
    }
}