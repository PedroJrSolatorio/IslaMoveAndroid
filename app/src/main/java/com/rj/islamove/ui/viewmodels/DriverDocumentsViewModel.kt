package com.rj.islamove.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Log.d
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.DriverDocument
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Job

@HiltViewModel
class DriverDocumentsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDocumentsUiState())
    val uiState: StateFlow<DriverDocumentsUiState> = _uiState.asStateFlow()

    private val _documents = MutableStateFlow<Map<String, DriverDocument>>(emptyMap())
    val documents: StateFlow<Map<String, DriverDocument>> = _documents.asStateFlow()

    private val _pendingImages = MutableStateFlow<Map<String, List<Uri>>>(emptyMap())
    val pendingImages: StateFlow<Map<String, List<Uri>>> = _pendingImages.asStateFlow()

    private var documentsListenerJob: Job? = null
    private var isPassengerMode = false
    private var isRegistrationMode = false
    private var registrationTempId: String? = null
    private var userEmail: String? = null

    /**
     * Set registration mode before loading documents
     * Call this from your registration screen
     */
    fun setRegistrationMode(tempUserId: String) {
        isRegistrationMode = true
        registrationTempId = tempUserId
        Log.d("DriverDocumentsViewModel", "Registration mode enabled for: $tempUserId")
    }

    fun loadDriverDocuments(driverId: String, isPassengerMode: Boolean = false) {
        this.isPassengerMode = isPassengerMode

        // Skip loading if in registration mode (no documents exist yet)
        if (isRegistrationMode) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // FETCH USER EMAIL when loading documents
            userRepository.getUserByUid(driverId).fold(
                onSuccess = { user ->
                    userEmail = user.email  // Store the email for later use
                    Log.d("DriverDocumentsViewModel", "User email loaded: $userEmail")
                },
                onFailure = { error ->
                    Log.e("DriverDocumentsViewModel", "Failed to load user email", error)
                }
            )

            if (isPassengerMode) {
                // For passengers, load student document and convert to driver document format
                userRepository.getUserByUid(driverId).fold(
                    onSuccess = { user ->
                        val studentDoc = user.studentDocument
                        val passengerDocs = if (studentDoc != null) {
                            mapOf(
                                "passenger_id" to DriverDocument(
                                    images = if (studentDoc.studentIdUrl.isNotEmpty()) {
                                        listOf(
                                            com.rj.islamove.data.models.DocumentImage(
                                                url = studentDoc.studentIdUrl,
                                                description = "Valid ID",
                                                uploadedAt = studentDoc.uploadedAt
                                            )
                                        )
                                    } else emptyList(),
                                    status = studentDoc.status,
                                    rejectionReason = studentDoc.rejectionReason,
                                    additionalPhotosRequired = studentDoc.additionalPhotosRequired,
                                    additionalPhotos = studentDoc.additionalPhotos,
                                    expiryDate = studentDoc.expiryDate,
                                    isExpired = studentDoc.isExpired
                                )
                            )
                        } else {
                            emptyMap()
                        }

                        val previousDocs = _documents.value
                        _documents.value = passengerDocs
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null
                        )

                        // Check for status changes and show messages
                        checkForDocumentStatusChanges(previousDocs, passengerDocs)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                )
            } else {
                // For drivers, use the existing logic
                userRepository.getDriverDocuments(driverId).fold(
                    onSuccess = { docs ->
                        val previousDocs = _documents.value
                        _documents.value = docs
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null
                        )

                        // Check for status changes and show messages
                        checkForDocumentStatusChanges(previousDocs, docs)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                )
            }
        }
    }

    private fun checkForDocumentStatusChanges(
        previousDocuments: Map<String, DriverDocument>,
        newDocuments: Map<String, DriverDocument>
    ) {
        // Only show messages if we have previous documents (not on first load)
        if (previousDocuments.isEmpty()) return

        newDocuments.forEach { (docType, newDoc) ->
            val previousDoc = previousDocuments[docType]

            // Check if status changed
            if (previousDoc != null && previousDoc.status != newDoc.status) {
                when (newDoc.status) {
                    com.rj.islamove.data.models.DocumentStatus.APPROVED -> {
                        _uiState.value = _uiState.value.copy(
                            successMessage = "✅ ${getDocumentDisplayName(docType)} approved!"
                        )
                    }
                    com.rj.islamove.data.models.DocumentStatus.REJECTED -> {
                        _uiState.value = _uiState.value.copy(
                            error = "❌ ${getDocumentDisplayName(docType)} rejected. Please check the reason and resubmit."
                        )
                    }
                    else -> {} // No message for other status changes
                }
            }
        }
    }

    private fun getDocumentDisplayName(documentType: String): String {
        return when (documentType) {
            "license" -> "Driver's License"
            "vehicle_registration" -> "Certificate of Registration (CR)"
            "insurance" -> "SJMODA Certification"
            "vehicle_inspection" -> "Official Receipt (OR)"
            "profile_photo" -> "Profile Photo"
            else -> documentType.replace("_", " ").split(" ").joinToString(" ") {
                it.replaceFirstChar { char -> char.uppercase() }
            }
        }
    }

    fun addPendingImages(
        documentType: String,
        imageUris: List<Uri>
    ) {
        val currentPending = _pendingImages.value.toMutableMap()

        // ALWAYS replace with only the latest image (limit to 1)
        currentPending[documentType] = listOf(imageUris.last())
        _pendingImages.value = currentPending

        _uiState.value = _uiState.value.copy(
            successMessage = "Image selected. Click 'Submit for Review' to upload it."
        )
    }

    fun removePendingImage(
        documentType: String,
        imageUri: Uri
    ) {
        val currentPending = _pendingImages.value.toMutableMap()
        val existingImages = currentPending[documentType] ?: emptyList()
        currentPending[documentType] = existingImages.filter { it != imageUri }
        if (currentPending[documentType]?.isEmpty() == true) {
            currentPending.remove(documentType)
        }
        _pendingImages.value = currentPending
    }

    private suspend fun uploadDocument(
        context: Context,
        driverId: String,
        documentType: String,
        imageUri: Uri,
        imageDescription: String = ""
    ): Result<String> {
        val tempId = userEmail ?: driverId  // Fallback to driverId if email not loaded
        return if (isPassengerMode && documentType == "passenger_id") {
            // For passenger ID uploads, use direct Cloudinary upload to registration_temp
            userRepository.uploadStudentDocument(
                context = context,
                studentUid = driverId,
                imageUri = imageUri,
                isRegistration = true,
                tempUserId = tempId
            )
        } else {
            // For driver documents, use direct Cloudinary upload to registration_temp
            userRepository.uploadDriverDocument(
                context = context,
                driverUid = driverId,
                documentType = documentType,
                imageUri = imageUri,
                imageDescription = imageDescription,
                isRegistration = true,
                tempUserId = tempId
            )
        }
    }

    fun removeDocumentImage(
        driverId: String,
        documentType: String,
        imageUrl: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            userRepository.removeDocumentImage(
                driverUid = driverId,
                documentType = documentType,
                imageUrl = imageUrl
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        successMessage = "Image removed successfully"
                    )
                    // Reload documents to show updated status
                    loadDriverDocuments(driverId, isPassengerMode)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "Failed to remove image: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Add pending images for additional photo requests
     * documentType format: "documentType_additional_photoType" e.g., "license_additional_back_of_id"
     */
    fun addPendingImagesForAdditional(
        originalDocType: String,
        photoType: String,
        imageUris: List<Uri>
    ) {
        val currentPending = _pendingImages.value.toMutableMap()

        // Store with special key format to identify as additional photo
        val key = "${originalDocType}_additional_${photoType}"
        currentPending[key] = listOf(imageUris.last()) // Only keep latest image

        _pendingImages.value = currentPending

        _uiState.value = _uiState.value.copy(
            successMessage = "Additional photo selected for $photoType. Click 'Submit for Review' to upload."
        )
    }

    /**
     * Upload additional photo to Cloudinary and update Firestore
     */
    private suspend fun uploadAdditionalPhoto(
        context: Context,
        driverId: String,
        originalDocType: String,
        photoType: String,
        imageUri: Uri
    ): Result<String> {
        val tempId = userEmail ?: driverId

        return if (isPassengerMode && originalDocType == "passenger_id") {
            // For passenger additional photos
            userRepository.uploadAdditionalStudentPhoto(
                context = context,
                studentUid = driverId,
                photoType = photoType,
                imageUri = imageUri,
                tempUserId = tempId
            )
        } else {
            // For driver additional photos
            userRepository.uploadAdditionalDriverPhoto(
                context = context,
                driverUid = driverId,
                documentType = originalDocType,
                photoType = photoType,
                imageUri = imageUri,
                tempUserId = tempId
            )
        }
    }

    fun submitForReview(context: Context, driverId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            val currentPendingImages = _pendingImages.value
            val currentDocuments = documents.value

            // Check if there are any pending images to upload
            if (currentPendingImages.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Please select at least one document to upload before submitting"
                )
                return@launch
            }

            // First upload all pending images
            var uploadErrors = mutableListOf<String>()

            currentPendingImages.forEach { (key, imageUris) ->
                // Check if this is an additional photo request
                if (key.contains("_additional_")) {
                    // Parse the key: "documentType_additional_photoType"
                    val parts = key.split("_additional_")
                    if (parts.size == 2) {
                        val originalDocType = parts[0]
                        val photoType = parts[1]

                        imageUris.forEach { imageUri ->
                            uploadAdditionalPhoto(
                                context = context,
                                driverId = driverId,
                                originalDocType = originalDocType,
                                photoType = photoType,
                                imageUri = imageUri
                            ).fold(
                                onSuccess = {
                                    d("DriverDocumentsViewModel", "✅ Uploaded additional photo: $photoType for $originalDocType")
                                },
                                onFailure = { error ->
                                    uploadErrors.add("Failed to upload $photoType: ${error.message}")
                                }
                            )
                        }
                    }
                } else {
                    // Regular document upload (existing code)
                    imageUris.forEach { imageUri ->
                        uploadDocument(
                            context = context,
                            driverId = driverId,
                            documentType = key,
                            imageUri = imageUri
                        ).fold(
                            onSuccess = { /* Success */ },
                            onFailure = { error ->
                                uploadErrors.add("Failed to upload ${getDocumentDisplayName(key)}: ${error.message}")
                            }
                        )
                    }
                }
            }

            if (uploadErrors.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = uploadErrors.joinToString("\n")
                )
                return@launch
            }

            // Clear pending images after successful upload
            _pendingImages.value = emptyMap()

            // Wait a moment for uploads to complete, then submit for review
            kotlinx.coroutines.delay(1000)

            // Submit all documents for review
            userRepository.submitAllDocumentsForReview(driverId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        successMessage = "Documents uploaded and submitted for review! You will be notified once they are verified."
                    )
                    // Reload documents to show updated status
                    loadDriverDocuments(driverId, isPassengerMode)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "Failed to submit documents: ${error.message}"
                    )
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        documentsListenerJob?.cancel()
    }
}

data class DriverDocumentsUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val pendingImages: Map<String, List<Uri>> = emptyMap() // Track pending images not yet uploaded
)