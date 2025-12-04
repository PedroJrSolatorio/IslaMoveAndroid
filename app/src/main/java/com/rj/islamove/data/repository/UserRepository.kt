package com.rj.islamove.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.DriverData
import com.rj.islamove.data.models.VerificationStatus
import android.util.Log
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.DriverDocument
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DocumentImage
import com.rj.islamove.data.services.NotificationService
import android.content.Context
import android.net.Uri
import com.rj.islamove.data.api.RenderApiService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val renderApiService: RenderApiService,
    private val cloudinaryRepository: CloudinaryRepository,
    private val notificationService: NotificationService
) {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val ADMIN_COLLECTION = "admins"
        private const val DRIVER_DOCUMENTS_COLLECTION = "driver_documents"

        /**
         * Extract a file pattern from a URL to identify potential duplicates
         * This handles cases where the same file might have slightly different URLs
         */
        private fun extractFilePattern(url: String): String {
            // Extract the base filename without timestamp or unique identifiers
            return url.substringAfterLast("/")
                .substringBeforeLast("_") // Remove timestamp suffix
                .substringBeforeLast(".")
                .lowercase()
        }
    }
    
    /**
     * FR-2.1.3: Get user by UID and verify role
     */
    suspend fun getUserByUid(uid: String): Result<User> {
        return try {
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .await()
            
            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to parse user data"))
                }
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.1.3: Update user role (admin functionality)
     */
    suspend fun updateUserRole(uid: String, newUserType: UserType): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "userType" to newUserType,
                "updatedAt" to System.currentTimeMillis()
            )
            
            // If changing to driver, initialize driver data
            if (newUserType == UserType.DRIVER) {
                updates["driverData"] = DriverData(
                    verificationStatus = VerificationStatus.PENDING
                )
            }
            
            // If changing from driver, remove driver data
            if (newUserType != UserType.DRIVER) {
                updates["driverData"] = FieldValue.delete()
            }
            
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()
            
            // Handle admin collection
            when (newUserType) {
                UserType.ADMIN -> {
                    // Add to admin collection if not already there
                    val user = getUserByUid(uid).getOrNull()
                    user?.let {
                        firestore.collection(ADMIN_COLLECTION)
                            .document(uid)
                            .set(mapOf(
                                "uid" to uid,
                                "email" to it.email,
                                "displayName" to it.displayName,
                                "createdAt" to it.createdAt
                            ))
                            .await()
                    }
                }
                else -> {
                    // Remove from admin collection if exists
                    firestore.collection(ADMIN_COLLECTION)
                        .document(uid)
                        .delete()
                        .await()
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FR-2.1.5: Store driver verification status with document references
     */
    suspend fun updateDriverVerificationStatus(
        uid: String,
        status: VerificationStatus,
        documentRefs: List<String> = emptyList(),
        notes: String = ""
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "driverData.verificationStatus" to status,
                "driverData.documentReferences" to documentRefs,
                "driverData.verificationNotes" to notes,
                "driverData.verificationDate" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()
            
            // Create verification audit log
            firestore.collection("verification_logs")
                .add(mapOf(
                    "driverUid" to uid,
                    "status" to status,
                    "documentRefs" to documentRefs,
                    "notes" to notes,
                    "timestamp" to System.currentTimeMillis(),
                    "updatedBy" to firebaseAuth.currentUser?.uid
                ))
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Real-time listener for user changes
     */
    fun getUserFlow(uid: String): Flow<User?> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                // Debug: Log raw Firebase document data
                android.util.Log.d("UserRepository", "Raw Firebase document data for $uid:")
                snapshot?.data?.forEach { (key, value) ->
                    android.util.Log.d("UserRepository", "  $key: $value (${value?.javaClass?.simpleName})")
                }

                val user = snapshot?.toObject(User::class.java)
                android.util.Log.d("UserRepository", "Deserialized User object: uid=${user?.uid}, isActive=${user?.isActive}")

                // Fix for deserialization issues
                val correctedUser = if (user != null && snapshot?.data != null) {
                    val rawData = snapshot.data!!
                    var updatedUser = user

                    // Fix isActive field
                    val rawIsActive = rawData["active"] as? Boolean
                    if (rawIsActive != null && rawIsActive != user.isActive) {
                        android.util.Log.d("UserRepository", "Correcting isActive from ${user.isActive} to $rawIsActive")
                        updatedUser = updatedUser.copy(isActive = rawIsActive)
                    }

                    // Fix passengerTotalTrips (Long -> Int conversion)
                    val rawTotalTrips = rawData["passengerTotalTrips"]
                    if (rawTotalTrips != null) {
                        val totalTripsInt = when (rawTotalTrips) {
                            is Long -> rawTotalTrips.toInt()
                            is Int -> rawTotalTrips
                            else -> user.passengerTotalTrips
                        }
                        if (totalTripsInt != user.passengerTotalTrips) {
                            android.util.Log.d("UserRepository", "Correcting passengerTotalTrips from ${user.passengerTotalTrips} to $totalTripsInt")
                            updatedUser = updatedUser.copy(passengerTotalTrips = totalTripsInt)
                        }
                    }

                    updatedUser
                } else {
                    user
                }

                android.util.Log.d("UserRepository", "Final corrected user: passengerRating=${correctedUser?.passengerRating}, passengerTotalTrips=${correctedUser?.passengerTotalTrips}")
                trySend(correctedUser)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update user profile information
     */
    suspend fun updateUserProfile(
        uid: String,
        displayName: String? = null,
        profileImageUrl: String? = null,
        preferences: Map<String, Any>? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis()
            )

            displayName?.let { updates["displayName"] = it }
            profileImageUrl?.let { updates["profileImageUrl"] = it }
            preferences?.let { updates["preferences"] = it }

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update driver earnings and trip count
     */
    suspend fun updateDriverEarnings(
        uid: String,
        additionalEarnings: Double,
        incrementTrips: Boolean = true
    ): Result<Unit> {
        return try {
            // Get current user data to add to existing earnings
            val currentUser = getUserByUid(uid).getOrNull()
            val currentEarnings = currentUser?.driverData?.totalEarnings ?: 0.0
            val currentTrips = currentUser?.driverData?.totalTrips ?: 0

            val updates = mutableMapOf<String, Any>(
                "driverData.totalEarnings" to (currentEarnings + additionalEarnings),
                "updatedAt" to System.currentTimeMillis()
            )

            if (incrementTrips) {
                updates["driverData.totalTrips"] = currentTrips + 1
            }

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user active status (admin functionality)
     */
    suspend fun updateUserStatus(uid: String, isActive: Boolean): Result<Unit> {
        return try {
            val updates = mapOf(
                "isActive" to isActive,
                "updatedAt" to System.currentTimeMillis(),
                // Remove the old "active" field if it exists to prevent conflicts
                "active" to FieldValue.delete()
            )

            android.util.Log.d("UserRepository", "Updating user $uid with isActive=$isActive")
            android.util.Log.d("UserRepository", "Update data: $updates")

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            android.util.Log.d("UserRepository", "Successfully updated user $uid status to isActive=$isActive")

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to update user $uid status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update user discount percentage (admin functionality)
     * Only applicable for verified passengers
     */
    suspend fun updateUserDiscount(uid: String, discountPercentage: Int?): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis()
            )

            // Add discount percentage to updates (null removes the field)
            if (discountPercentage != null) {
                updates["discountPercentage"] = discountPercentage
                android.util.Log.d("UserRepository", "Setting user $uid discount to $discountPercentage%")
            } else {
                updates["discountPercentage"] = FieldValue.delete()
                android.util.Log.d("UserRepository", "Removing discount for user $uid")
            }

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            android.util.Log.d("UserRepository", "Successfully updated user $uid discount")

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to update user $uid discount: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update passenger document verification status
     */
    suspend fun updatePassengerDocumentStatus(uid: String, status: DocumentStatus): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "studentDocument.status" to status,
                "updatedAt" to System.currentTimeMillis()
            )

            // If status is APPROVED, set verification date
            if (status == DocumentStatus.APPROVED) {
                updates["studentDocument.verificationDate"] = System.currentTimeMillis()
                // In a real app, you might want to track which admin verified this
                // updates["studentDocument.verifiedBy"] = FirebaseAuth.getInstance().currentUser?.uid
            }

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            Log.d("UserRepository", "Updated passenger $uid document status to $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to update passenger $uid document status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update user personal information
     */
    suspend fun updateUserPersonalInfo(uid: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val allUpdates = updates.toMutableMap()
            allUpdates["updatedAt"] = System.currentTimeMillis()

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(allUpdates)
                .await()

            Log.d("UserRepository", "Updated personal info for user $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to update personal info for user $uid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all users (admin functionality)
     */
    fun getAllUsers(): Flow<Result<List<User>>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                try {
                    val users = snapshot?.documents?.mapNotNull { document ->
                        val user = document.toObject(User::class.java)?.copy(uid = document.id)

                        // Handle legacy documents that might have "active" field instead of "isActive"
                        if (user != null) {
                            val documentData = document.data
                            val hasActiveField = documentData?.containsKey("active") == true
                            val hasIsActiveField = documentData?.containsKey("isActive") == true

                            // DEBUG: Log user discount data
                            Log.d("UserRepository", "Loading user: ${user.uid}, discountPercentage: ${user.discountPercentage}, userType: ${user.userType}")

                            if (hasActiveField && !hasIsActiveField) {
                                // Legacy document: use "active" field value for "isActive"
                                val activeValue = documentData?.get("active") as? Boolean ?: true
                                user.copy(isActive = activeValue)
                            } else {
                                // Modern document or document with both fields: trust "isActive"
                                user
                            }
                        } else {
                            null
                        }
                    } ?: emptyList()

                    trySend(Result.success(users))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * One-time cleanup function to migrate legacy "active" field to "isActive"
     * This should be called once by admin to clean up any legacy documents
     */
    suspend fun cleanupLegacyActiveFields(): Result<Unit> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .get()
                .await()

            var batch = firestore.batch()
            var batchSize = 0

            snapshot.documents.forEach { document ->
                val documentData = document.data
                val hasActiveField = documentData?.containsKey("active") == true
                val hasIsActiveField = documentData?.containsKey("isActive") == true

                if (hasActiveField) {
                    val activeValue = documentData?.get("active") as? Boolean ?: true
                    val updates = mutableMapOf<String, Any>(
                        "updatedAt" to System.currentTimeMillis(),
                        "active" to FieldValue.delete()
                    )

                    // Only set isActive if it doesn't exist
                    if (!hasIsActiveField) {
                        updates["isActive"] = activeValue
                    }

                    batch.update(document.reference, updates)
                    batchSize++

                    // Firestore batch limit is 500 operations
                    if (batchSize >= 500) {
                        batch.commit().await()
                        // Create a new batch
                        batch = firestore.batch()
                        batchSize = 0
                    }
                }
            }

            // Commit remaining operations
            if (batchSize > 0) {
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get users by type (admin functionality)
     */
    fun getUsersByType(userType: UserType): Flow<Result<List<User>>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("userType", userType.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                try {
                    val users = snapshot?.documents?.mapNotNull { document ->
                        document.toObject(User::class.java)?.copy(uid = document.id)
                    } ?: emptyList()
                    
                    trySend(Result.success(users))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }
        
        awaitClose { listener.remove() }
    }

    fun getPassengersWithStudentDocuments(): Flow<Result<List<User>>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("userType", UserType.PASSENGER.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                try {
                    val users = snapshot?.documents?.mapNotNull { document ->
                        document.toObject(User::class.java)?.copy(uid = document.id)
                    }?.filter { user ->
                        // Only include passengers with student documents that need review
                        user.studentDocument != null &&
                        (user.studentDocument.status == DocumentStatus.PENDING ||
                         user.studentDocument.status == DocumentStatus.PENDING_REVIEW)
                    } ?: emptyList()

                    trySend(Result.success(users))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Save a place (home, work, favorite) for a user
     */
    suspend fun saveUserPlace(uid: String, placeType: String, location: BookingLocation): Result<Unit> {
        return try {
            val updates = mapOf(
                "preferences.savedPlaces.$placeType" to location,
                "updatedAt" to System.currentTimeMillis()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all saved places for a user
     */
    suspend fun getUserSavedPlaces(uid: String): Result<Map<String, BookingLocation>> {
        return try {
            val user = getUserByUid(uid).getOrNull()
            Result.success(user?.preferences?.savedPlaces ?: emptyMap())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a saved place for a user
     */
    suspend fun removeUserPlace(uid: String, placeType: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "preferences.savedPlaces.$placeType" to FieldValue.delete(),
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload driver document image to Cloudinary and update Firestore
     */
    suspend fun uploadDriverDocument(
        context: Context,
        driverUid: String,
        documentType: String,
        imageUri: Uri,
        imageDescription: String = "",
        isRegistration: Boolean = false,
        tempUserId: String? = null
    ): Result<String> {
        return try {
            // ✅ Upload to registration_temp/{tempUserId} when re-uploading
            val uploadResult = cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = documentType,
                publicId = null,
                tempUserId = if (isRegistration) tempUserId else null
            )

            uploadResult.fold(
                onSuccess = { imageUrl ->
                    // ALWAYS update Firestore (removed the isRegistration check)
                    // Get existing document or create new one
                    val existingUser = getUserByUid(driverUid).getOrNull()
                    val existingDocument = existingUser?.driverData?.documents?.get(documentType)

                    val newImage = DocumentImage(
                        url = imageUrl,
                        description = imageDescription,
                        uploadedAt = System.currentTimeMillis()
                    )

                    val updatedImages = if (existingDocument != null) {
                        listOf(newImage)
                    } else {
                        listOf(newImage)
                    }

                    val updatedDocument = DriverDocument(
                        images = updatedImages,
                        status = DocumentStatus.PENDING, // Just uploaded, not submitted for review yet
                        uploadedAt = existingDocument?.uploadedAt ?: System.currentTimeMillis()
                    )

                    val updates = mapOf(
                        "driverData.documents.$documentType" to updatedDocument,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    firestore.collection(USERS_COLLECTION)
                        .document(driverUid)
                        .update(updates)
                        .await()

                    Result.success(imageUrl)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to upload driver document", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a specific image from a document
     */
    suspend fun removeDocumentImage(
        driverUid: String,
        documentType: String,
        imageUrl: String
    ): Result<Unit> {
        return try {
            val user = getUserByUid(driverUid).getOrNull()
            val document = user?.driverData?.documents?.get(documentType)

            if (document != null) {
                val updatedImages = document.images.filter { it.url != imageUrl }

                val updatedDocument = if (updatedImages.isEmpty()) {
                    // If no images left, remove the document entirely
                    val updates = mapOf(
                        "driverData.documents.$documentType" to FieldValue.delete(),
                        "updatedAt" to System.currentTimeMillis()
                    )

                    firestore.collection(USERS_COLLECTION)
                        .document(driverUid)
                        .update(updates)
                        .await()
                } else {
                    // Update with remaining images
                    val updatedDoc = document.copy(images = updatedImages)
                    val updates = mapOf(
                        "driverData.documents.$documentType" to updatedDoc,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    firestore.collection(USERS_COLLECTION)
                        .document(driverUid)
                        .update(updates)
                        .await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update document status (admin functionality)
     */
    suspend fun updateDocumentStatus(
        driverUid: String,
        documentType: String,
        status: DocumentStatus,
        rejectionReason: String? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "driverData.documents.$documentType.status" to status,
                "updatedAt" to System.currentTimeMillis()
            )

            if (status == DocumentStatus.REJECTED && rejectionReason != null) {
                updates["driverData.documents.$documentType.rejectionReason"] = rejectionReason
            } else if (status == DocumentStatus.APPROVED) {
                // Clear rejection reason when approved
                updates["driverData.documents.$documentType.rejectionReason"] = FieldValue.delete()
            }

            firestore.collection(USERS_COLLECTION)
                .document(driverUid)
                .update(updates)
                .await()

            // Log the document action
            firestore.collection("document_verification_logs")
                .add(mapOf(
                    "driverUid" to driverUid,
                    "documentType" to documentType,
                    "status" to status.name,
                    "rejectionReason" to rejectionReason,
                    "timestamp" to System.currentTimeMillis(),
                    "adminUid" to firebaseAuth.currentUser?.uid
                ))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Approve individual document (admin functionality)
     */
    suspend fun approveDocument(
        driverUid: String,
        documentType: String
    ): Result<Unit> {
        return try {
            // Update document status
            val updateResult = updateDocumentStatus(driverUid, documentType, DocumentStatus.APPROVED)

            if (updateResult.isSuccess) {
                // Send notification to driver
                notificationService.sendDocumentStatusNotification(
                    driverUid = driverUid,
                    documentType = documentType,
                    status = DocumentStatus.APPROVED
                )

                // Check if all documents are approved and send verification complete notification
                checkAndNotifyIfAllDocumentsApproved(driverUid)
            }

            updateResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject individual document (admin functionality)
     */
    suspend fun rejectDocument(
        driverUid: String,
        documentType: String,
        rejectionReason: String
    ): Result<Unit> {
        return try {
            // Update document status
            val updateResult = updateDocumentStatus(driverUid, documentType, DocumentStatus.REJECTED, rejectionReason)

            if (updateResult.isSuccess) {
                // Send notification to driver
                notificationService.sendDocumentStatusNotification(
                    driverUid = driverUid,
                    documentType = documentType,
                    status = DocumentStatus.REJECTED,
                    rejectionReason = rejectionReason
                )
            }

            updateResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if all required documents are approved and send verification complete notification
     */
    private suspend fun checkAndNotifyIfAllDocumentsApproved(driverUid: String) {
        try {
            val user = getUserByUid(driverUid).getOrNull()
            val documents = user?.driverData?.documents

            if (documents != null) {
                val requiredDocuments = listOf("license", "vehicle_registration", "insurance", "vehicle_inspection", "profile_photo")
                val allApproved = requiredDocuments.all { docType ->
                    documents[docType]?.status == DocumentStatus.APPROVED
                }

                if (allApproved) {
                    // Update driver verification status to APPROVED
                    updateDriverVerificationStatus(
                        uid = driverUid,
                        status = VerificationStatus.APPROVED,
                        notes = "All documents approved - driver verification complete"
                    )

                    // Send congratulations notification
                    notificationService.sendDriverVerificationCompleteNotification(driverUid)
                }
            }
        } catch (e: Exception) {
            // Log but don't fail the operation
            android.util.Log.e("UserRepository", "Failed to check all documents approval", e)
        }
    }

    /**
     * Submit all uploaded documents for admin review
     */
    suspend fun submitAllDocumentsForReview(driverUid: String): Result<Unit> {
        return try {
            val user = getUserByUid(driverUid).getOrNull()
            val documents = user?.driverData?.documents ?: emptyMap()

            // Create batch update for all documents with PENDING status
            val updates = mutableMapOf<String, Any>()
            documents.forEach { (docType, document) ->
                if (document.status == DocumentStatus.PENDING && document.images.isNotEmpty()) {
                    updates["driverData.documents.$docType.status"] = DocumentStatus.PENDING_REVIEW
                }
            }

            // Update driver verification status to UNDER_REVIEW
            updates["driverData.verificationStatus"] = VerificationStatus.UNDER_REVIEW
            updates["driverData.verificationNotes"] = "Driver submitted documents for review"
            updates["updatedAt"] = System.currentTimeMillis()

            firestore.collection(USERS_COLLECTION)
                .document(driverUid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get driver documents for verification
     */
    suspend fun getDriverDocuments(driverUid: String): Result<Map<String, DriverDocument>> {
        return try {
            val user = getUserByUid(driverUid).getOrNull()
            val documents = user?.driverData?.documents ?: emptyMap()
            Result.success(documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Approve student document (admin functionality)
     */
    suspend fun approveStudentDocument(studentUid: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "studentDocument.status" to DocumentStatus.APPROVED,
                "studentDocument.verificationDate" to System.currentTimeMillis(),
                "studentDocument.rejectionReason" to null,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(studentUid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject student document (admin functionality)
     */
    suspend fun rejectStudentDocument(studentUid: String, rejectionReason: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "studentDocument.status" to DocumentStatus.REJECTED,
                "studentDocument.rejectionReason" to rejectionReason,
                "studentDocument.verificationDate" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(studentUid)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload student ID document (passenger functionality)
     */
    suspend fun uploadStudentDocument(
        context: Context,
        studentUid: String,
        imageUri: Uri,
        studentIdNumber: String = "",
        school: String = "",
        isRegistration: Boolean = false,
        tempUserId: String? = null
    ): Result<String> {
        return try {
            // Upload to registration_temp/{tempUserId} when re-uploading
            val imageUrl = cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = "passenger_id",
                publicId = null,
                tempUserId = if (isRegistration) tempUserId else null
            ).getOrThrow()

            // ALWAYS update Firestore (removed the isRegistration check)
            // The difference is: re-uploads go to temp storage, but we still save the URL
            val updates = mapOf(
                "studentDocument.studentIdUrl" to imageUrl,
                "studentDocument.studentIdNumber" to studentIdNumber,
                "studentDocument.school" to school,
                "studentDocument.status" to DocumentStatus.PENDING_REVIEW,
                "studentDocument.uploadedAt" to System.currentTimeMillis(),
                "studentDocument.rejectionReason" to null,
                "studentDocument.expiryDate" to null,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(studentUid)
                .update(updates)
                .await()

            Result.success(imageUrl)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to upload student document", e)
            Result.failure(e)
        }
    }

    /**
     * Approve all driver documents in bulk (admin functionality)
     */
    suspend fun approveAllDriverDocuments(driverUid: String): Result<Unit> {
        return try {
            val user = getUserByUid(driverUid).getOrNull()
            val documents = user?.driverData?.documents ?: emptyMap()

            // Create batch update for all documents
            val updates = mutableMapOf<String, Any>()
            documents.forEach { (docType, document) ->
                if (document.status == DocumentStatus.PENDING_REVIEW ||
                    document.status == DocumentStatus.PENDING) {
                    updates["driverData.documents.$docType.status"] = DocumentStatus.APPROVED
                    updates["driverData.documents.$docType.rejectionReason"] = FieldValue.delete()
                }
            }

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()

                firestore.collection(USERS_COLLECTION)
                    .document(driverUid)
                    .update(updates)
                    .await()

                // Log bulk approval action
                firestore.collection("document_verification_logs")
                    .add(mapOf(
                        "driverUid" to driverUid,
                        "action" to "BULK_APPROVE",
                        "documentsApproved" to documents.keys.toList(),
                        "timestamp" to System.currentTimeMillis(),
                        "adminUid" to firebaseAuth.currentUser?.uid
                    ))
                    .await()

                // Send notifications for all approved documents
                documents.keys.forEach { docType ->
                    notificationService.sendDocumentStatusNotification(
                        driverUid = driverUid,
                        documentType = docType,
                        status = DocumentStatus.APPROVED
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject all driver documents in bulk (admin functionality)
     */
    suspend fun rejectAllDriverDocuments(driverUid: String, rejectionReason: String): Result<Unit> {
        return try {
            val user = getUserByUid(driverUid).getOrNull()
            val documents = user?.driverData?.documents ?: emptyMap()

            // Create batch update for all documents
            val updates = mutableMapOf<String, Any>()
            documents.forEach { (docType, document) ->
                if (document.status == DocumentStatus.PENDING_REVIEW ||
                    document.status == DocumentStatus.PENDING) {
                    updates["driverData.documents.$docType.status"] = DocumentStatus.REJECTED
                    updates["driverData.documents.$docType.rejectionReason"] = rejectionReason
                }
            }

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()

                firestore.collection(USERS_COLLECTION)
                    .document(driverUid)
                    .update(updates)
                    .await()

                // Log bulk rejection action
                firestore.collection("document_verification_logs")
                    .add(mapOf(
                        "driverUid" to driverUid,
                        "action" to "BULK_REJECT",
                        "documentsRejected" to documents.keys.toList(),
                        "rejectionReason" to rejectionReason,
                        "timestamp" to System.currentTimeMillis(),
                        "adminUid" to firebaseAuth.currentUser?.uid
                    ))
                    .await()

                // Send notifications for all rejected documents
                documents.keys.forEach { docType ->
                    notificationService.sendDocumentStatusNotification(
                        driverUid = driverUid,
                        documentType = docType,
                        status = DocumentStatus.REJECTED,
                        rejectionReason = rejectionReason
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserPassword(uid: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                try {
                    // Get current admin ID
                    val adminId = firebaseAuth.currentUser?.uid ?: ""

                    // Get auth token
                    firebaseAuth.currentUser?.getIdToken(false)?.addOnSuccessListener { tokenResult ->
                        val token = tokenResult.token ?: ""

                        // Call Render API to update password in Firebase Auth
                        renderApiService.updateUserPassword(uid, newPassword, adminId, token) { success, error ->
                            if (success) {
                                // Also update Firestore for admin display (for compatibility)
                                val updates = mapOf(
                                    "plainTextPassword" to newPassword,
                                    "updatedAt" to System.currentTimeMillis()
                                )

                                firestore.collection(USERS_COLLECTION)
                                    .document(uid)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Log.d("UserRepository", "Successfully updated password for user $uid using Render API")
                                        continuation.resume(Result.success(Unit))
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("UserRepository", "Failed to update Firestore for user $uid: ${e.message}", e)
                                        continuation.resume(Result.failure(e))
                                    }
                            } else {
                                val exception = Exception(error ?: "Failed to update password via Render API")
                                Log.e("UserRepository", "Failed to update password for user $uid: ${exception.message}", exception)
                                continuation.resume(Result.failure(exception))
                            }
                        }
                    }?.addOnFailureListener { e ->
                        Log.e("UserRepository", "Failed to get auth token: ${e.message}", e)
                        continuation.resume(Result.failure(e))
                    }
                } catch (e: Exception) {
                    Log.e("UserRepository", "Failed to update password for user $uid: ${e.message}", e)
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    /**
     * Hard delete user (completely remove from Firebase Auth and Firestore)
     */
    suspend fun deleteUser(uid: String, adminId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                try {
                    // Get auth token
                    firebaseAuth.currentUser?.getIdToken(false)?.addOnSuccessListener { tokenResult ->
                        val token = tokenResult.token ?: ""

                        // Call Render API to hard delete user in both Firebase Auth and Firestore
                        renderApiService.deleteUser(uid, adminId, token) { success, error ->
                            if (success) {
                                Log.d("UserRepository", "Successfully permanently deleted user $uid using Render API")
                                continuation.resume(Result.success(Unit))
                            } else {
                                val exception = Exception(error ?: "Failed to delete user via Render API")
                                Log.e("UserRepository", "Failed to delete user $uid: ${exception.message}", exception)
                                continuation.resume(Result.failure(exception))
                            }
                        }
                    }?.addOnFailureListener { e ->
                        Log.e("UserRepository", "Failed to get auth token: ${e.message}", e)
                        continuation.resume(Result.failure(e))
                    }
                } catch (e: Exception) {
                    Log.e("UserRepository", "Failed to delete user $uid: ${e.message}", e)
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    suspend fun tempFixPasswords(): Result<Unit> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION).get().await()
            val batch = firestore.batch()
            for (document in snapshot.documents) {
                val user = document.toObject(User::class.java)
                if (user != null && user.plainTextPassword == null) {
                    batch.update(document.reference, "plainTextPassword", "password123")
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAdditionalStudentPhoto(
        context: Context,
        studentUid: String,
        photoType: String,
        imageUri: Uri,
        tempUserId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Upload to Cloudinary with special naming for additional photos
            val imageUrl = cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = "passenger_id",
                publicId = "passenger_id_additional_${photoType}_${System.currentTimeMillis()}",
                tempUserId = tempUserId
            ).getOrThrow()

            // Update Firestore - add to additionalPhotos map
            val updates = mapOf(
                "studentDocument.additionalPhotos.$photoType" to imageUrl,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(studentUid)
                .update(updates)
                .await()

            Log.d("UserRepository", "Successfully uploaded additional student photo: $photoType")
            Result.success(imageUrl)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to upload additional student photo", e)
            Result.failure(e)
        }
    }

    suspend fun uploadAdditionalDriverPhoto(
        context: Context,
        driverUid: String,
        documentType: String,
        photoType: String,
        imageUri: Uri,
        tempUserId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Upload to Cloudinary with special naming
            val imageUrl = cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = documentType,
                publicId = "${documentType}_additional_${photoType}_${System.currentTimeMillis()}",
                tempUserId = tempUserId
            ).getOrThrow()

            // Update Firestore - add to additionalPhotos map
            val updates = mapOf(
                "driverData.documents.$documentType.additionalPhotos.$photoType" to imageUrl,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(USERS_COLLECTION)
                .document(driverUid)
                .update(updates)
                .await()

            Log.d("UserRepository", "Successfully uploaded additional driver photo: $photoType for $documentType")
            Result.success(imageUrl)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to upload additional driver photo", e)
            Result.failure(e)
        }
    }
}