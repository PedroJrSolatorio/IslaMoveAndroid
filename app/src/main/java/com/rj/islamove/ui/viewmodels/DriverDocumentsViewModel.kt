package com.rj.islamove.ui.viewmodels

import android.content.Context
import android.net.Uri
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

    fun loadDriverDocuments(driverId: String, isPassengerMode: Boolean = false) {
        this.isPassengerMode = isPassengerMode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

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
                                    rejectionReason = studentDoc.rejectionReason
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
        val existingImages = currentPending[documentType] ?: emptyList()

        // Check for duplicates by comparing URI paths and replace if found
        val newImages = imageUris.toMutableList()
        val finalImages = existingImages.toMutableList()

        newImages.forEach { newUri ->
            val duplicateIndex = finalImages.indexOfFirst { existingUri ->
                existingUri.path == newUri.path || existingUri.toString() == newUri.toString()
            }

            if (duplicateIndex != -1) {
                // Replace the duplicate image
                finalImages[duplicateIndex] = newUri
            } else {
                // Add as new image
                finalImages.add(newUri)
            }
        }

        currentPending[documentType] = finalImages
        _pendingImages.value = currentPending

        val message = if (imageUris.size == 1) {
            "Image selected. Click 'Submit for Review' to upload it."
        } else {
            "${imageUris.size} images selected. Click 'Submit for Review' to upload them."
        }

        _uiState.value = _uiState.value.copy(
            successMessage = message
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
        return if (isPassengerMode && documentType == "passenger_id") {
            // For passenger ID uploads, use the student document upload method
            userRepository.uploadStudentDocument(
                context = context,
                studentUid = driverId,
                imageUri = imageUri
            )
        } else {
            // For driver documents, use the existing method
            userRepository.uploadDriverDocument(
                context = context,
                driverUid = driverId,
                documentType = documentType,
                imageUri = imageUri,
                imageDescription = imageDescription
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

    private fun submitSingleDocumentForReview(driverId: String, documentType: String) {
        viewModelScope.launch {
            userRepository.submitSingleDocumentForReview(driverId, documentType)
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

            currentPendingImages.forEach { (documentType, imageUris) ->
                imageUris.forEach { imageUri ->
                    uploadDocument(
                        context = context,
                        driverId = driverId,
                        documentType = documentType,
                        imageUri = imageUri
                    ).fold(
                        onSuccess = { /* Success handled below */ },
                        onFailure = { error ->
                            uploadErrors.add("Failed to upload ${getDocumentDisplayName(documentType)}: ${error.message}")
                        }
                    )
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