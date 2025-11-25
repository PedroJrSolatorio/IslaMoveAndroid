package com.rj.islamove.ui.screens.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.repository.AuthRepository
import com.rj.islamove.data.repository.CloudinaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val needsUserTypeSelection: Boolean = false,
    val userExists: Boolean = false,
    val showMultiDeviceAlert: Boolean = false,
    val pendingUserId: String? = null,
    val showForgotPasswordDialog: Boolean = false,
    val resetPasswordSuccess: Boolean = false,
    val resetPasswordMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudinaryRepository: CloudinaryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    // Check if user data exists in Firestore
                    authRepository.getUserData(user.uid)
                        .onSuccess { userData ->
                            // Check for existing sessions (skip for admins)
                            if (userData.userType != UserType.ADMIN) {
                                authRepository.checkExistingSession(user.uid)
                                    .onSuccess { hasOtherSession ->
                                        if (hasOtherSession) {
                                            // Show multi-device alert
                                            _uiState.value = _uiState.value.copy(
                                                isLoading = false,
                                                showMultiDeviceAlert = true,
                                                pendingUserId = user.uid
                                            )
                                        } else {
                                            // No other session, session already created in signInWithEmail
                                            // Add small delay before marking success to ensure monitoring picks up session
                                            kotlinx.coroutines.delay(200)
                                            _uiState.value = _uiState.value.copy(
                                                isLoading = false,
                                                isSuccess = true,
                                                userExists = true
                                            )
                                        }
                                    }
                                    .onFailure {
                                        // If session check fails, proceed anyway
                                        kotlinx.coroutines.delay(200)
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            isSuccess = true,
                                            userExists = true
                                        )
                                    }
                            } else {
                                // Admin user - allow multiple devices
                                kotlinx.coroutines.delay(200)
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isSuccess = true,
                                    userExists = true
                                )
                            }
                        }
                        .onFailure {
                            // User exists in Auth but not in Firestore, needs user type selection
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                needsUserTypeSelection = true
                            )
                        }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Sign in failed"
                    )
                }
        }
    }

    fun createAccountWithRole(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String,
        userType: UserType,
        dateOfBirth: String,
        gender: String?,
        address: String,
        idDocumentUri: Uri? = null,
        driverLicenseUri: Uri? = null,
        sjmodaUri: Uri? = null,
        orUri: Uri? = null,
        crUri: Uri? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Use email as tempUserId for registration uploads
            val tempUserId = email

            // Upload passenger ID document to Cloudinary if provided
            var documentUrl: String? = null
            if (idDocumentUri != null) {
                cloudinaryRepository.uploadImage(
                    context = context,
                    imageUri = idDocumentUri,
                    folder = "passenger_documents",
                    tempUserId = tempUserId
                ).onSuccess { url ->
                    documentUrl = url
                }.onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to upload ID document: ${exception.message}"
                    )
                    return@launch
                }
            }

            // Upload driver documents to Cloudinary if provided
            var licenseUrl: String? = null
            var sjmodaUrl: String? = null
            var orUrl: String? = null
            var crUrl: String? = null

            if (userType == UserType.DRIVER) {
                // Upload Driver's License
                if (driverLicenseUri != null) {
                    cloudinaryRepository.uploadImage(
                        context = context,
                        imageUri = driverLicenseUri,
                        folder = "driver_documents/license",
                        tempUserId = tempUserId
                    ).onSuccess { url ->
                        licenseUrl = url
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to upload Driver's License: ${exception.message}"
                        )
                        return@launch
                    }
                }

                // Upload SJMODA Certification
                if (sjmodaUri != null) {
                    cloudinaryRepository.uploadImage(
                        context = context,
                        imageUri = sjmodaUri,
                        folder = "driver_documents/insurance",
                        tempUserId = tempUserId
                    ).onSuccess { url ->
                        sjmodaUrl = url
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to upload Franchise Certificate: ${exception.message}"
                        )
                        return@launch
                    }
                }

                // Upload Official Receipt
                if (orUri != null) {
                    cloudinaryRepository.uploadImage(
                        context = context,
                        imageUri = orUri,
                        folder = "driver_documents/vehicle_inspection",
                        tempUserId = tempUserId
                    ).onSuccess { url ->
                        orUrl = url
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to upload Official Receipt: ${exception.message}"
                        )
                        return@launch
                    }
                }

                // Upload Certificate of Registration
                if (crUri != null) {
                    cloudinaryRepository.uploadImage(
                        context = context,
                        imageUri = crUri,
                        folder = "driver_documents/vehicle_registration",
                        tempUserId = tempUserId
                    ).onSuccess { url ->
                        crUrl = url
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to upload Certificate of Registration: ${exception.message}"
                        )
                        return@launch
                    }
                }
            }

            authRepository.createUserWithEmail(
                email = email,
                password = password,
                displayName = displayName,
                phoneNumber = phoneNumber,
                userType = userType,
                dateOfBirth = dateOfBirth,
                gender = gender,
                address = address,
                idDocumentUrl = documentUrl,
                driverLicenseUrl = licenseUrl,
                sjmodaUrl = sjmodaUrl,
                orUrl = orUrl,
                crUrl = crUrl
            ).onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true
                )
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Account creation failed"
                )
            }
        }
    }
    
    fun continueWithMultiDeviceLogin() {
        viewModelScope.launch {
            val userId = _uiState.value.pendingUserId
            if (userId != null) {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Force logout other devices
                authRepository.forceLogoutOtherDevices(userId)
                    .onSuccess {
                        // Create session for current device after forcing logout others
                        authRepository.createSession(userId)

                        // Wait to ensure session is written
                        kotlinx.coroutines.delay(500)

                        // Proceed with login
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isSuccess = true,
                            showMultiDeviceAlert = false,
                            pendingUserId = null
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to logout other devices: ${exception.message}",
                            showMultiDeviceAlert = false
                        )
                    }
            }
        }
    }

    fun cancelMultiDeviceLogin() {
        viewModelScope.launch {
            // Sign out current session
            authRepository.signOut()

            _uiState.value = _uiState.value.copy(
                showMultiDeviceAlert = false,
                pendingUserId = null,
                isLoading = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetState() {
        _uiState.value = LoginUiState()
    }

    fun showForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showForgotPasswordDialog = true,
            resetPasswordSuccess = false,
            resetPasswordMessage = null
        )
    }

    fun dismissForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showForgotPasswordDialog = false,
            resetPasswordSuccess = false,
            resetPasswordMessage = null
        )
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, resetPasswordMessage = null)

            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            resetPasswordSuccess = true,
                            resetPasswordMessage = "Password reset email sent to $email. Please check your inbox."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            resetPasswordSuccess = false,
                            resetPasswordMessage = task.exception?.message ?: "Failed to send password reset email"
                        )
                    }
                }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resetPasswordSuccess = false,
                    resetPasswordMessage = exception.message ?: "Failed to send password reset email"
                )
            }
        }
    }
}