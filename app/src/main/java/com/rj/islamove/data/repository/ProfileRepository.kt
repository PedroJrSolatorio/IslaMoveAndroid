package com.rj.islamove.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserPreferences
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val cloudinaryRepository: CloudinaryRepository
) {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val PROFILE_IMAGES_PATH = "profile_images"
        private const val DRIVER_DOCUMENTS_PATH = "driver_documents"
    }
    
    /**
     * FR-2.2.1: Get user profile data from Firestore
     */
    suspend fun getUserProfile(uid: String): Result<User> {
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
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.1: Update user profile data in Firestore
     */
    suspend fun updateUserProfile(
        uid: String,
        displayName: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
        profileImageUrl: String? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis()
            )
            
            displayName?.let { updates["displayName"] = it }
            phoneNumber?.let { updates["phoneNumber"] = it }
            email?.let { updates["email"] = it }
            profileImageUrl?.let { updates["profileImageUrl"] = it }
            
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
     * FR-2.2.1: Upload profile image to Cloudinary
     */
    suspend fun uploadProfileImage(context: Context, uid: String, imageUri: Uri): Result<String> {
        return try {
            // Upload to Cloudinary instead of Firebase Storage
            cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = "profile_images",
                publicId = "profile_$uid"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload student ID document to Cloudinary for passenger discount eligibility
     */
    suspend fun uploadStudentDocument(
        context: Context,
        uid: String,
        imageUri: Uri,
        studentIdNumber: String,
        school: String
    ): Result<String> {
        return try {
            // Upload to Cloudinary
            val uploadResult = cloudinaryRepository.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = "student_documents",
                publicId = "student_${uid}_${System.currentTimeMillis()}"
            )

            uploadResult.fold(
                onSuccess = { imageUrl ->
                    // Update user document with student ID info
                    val studentDocument = com.rj.islamove.data.models.StudentDocument(
                        studentIdUrl = imageUrl,
                        studentIdNumber = studentIdNumber,
                        school = school,
                        status = com.rj.islamove.data.models.DocumentStatus.PENDING_REVIEW,
                        uploadedAt = System.currentTimeMillis()
                    )

                    firestore.collection(USERS_COLLECTION)
                        .document(uid)
                        .update(
                            mapOf(
                                "studentDocument" to studentDocument,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        .await()

                    Result.success(imageUrl)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.2: Upload driver documents to Firebase Storage with metadata
     */
    suspend fun uploadDriverDocument(
        uid: String,
        documentUri: Uri,
        documentType: String, // "license", "registration", "insurance", etc.
        fileName: String
    ): Result<String> {
        return try {
            val documentRef = storage.reference
                .child(DRIVER_DOCUMENTS_PATH)
                .child(uid)
                .child(documentType)
                .child(fileName)
            
            // Add metadata
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setCustomMetadata("documentType", documentType)
                .setCustomMetadata("uploadedBy", uid)
                .setCustomMetadata("uploadTime", System.currentTimeMillis().toString())
                .build()
            
            val uploadTask = documentRef.putFile(documentUri, metadata).await()
            val downloadUrl = documentRef.downloadUrl.await()
            
            // Store document reference in Firestore
            val documentData = mapOf(
                "documentType" to documentType,
                "fileName" to fileName,
                "downloadUrl" to downloadUrl.toString(),
                "storagePath" to documentRef.path,
                "uploadTime" to System.currentTimeMillis(),
                "status" to "pending_review"
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection("documents")
                .document(documentType)
                .set(documentData)
                .await()
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.2: Get driver documents
     */
    suspend fun getDriverDocuments(uid: String): Result<Map<String, Any>> {
        return try {
            val documentsSnapshot = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection("documents")
                .get()
                .await()
            
            val documents = mutableMapOf<String, Any>()
            for (doc in documentsSnapshot.documents) {
                documents[doc.id] = doc.data ?: emptyMap<String, Any>()
            }
            
            Result.success(documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.3: Update user preferences in Firestore sub-collections
     */
    suspend fun updateUserPreferences(
        uid: String,
        preferences: UserPreferences
    ): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .update("preferences", preferences, "updatedAt", System.currentTimeMillis())
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.3: Get user preferences
     */
    suspend fun getUserPreferences(uid: String): Result<UserPreferences> {
        return try {
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .await()
            
            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)
                Result.success(user?.preferences ?: UserPreferences())
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile image from Firebase Storage
     */
    suspend fun deleteProfileImage(uid: String): Result<Unit> {
        return try {
            val imageRef = storage.reference
                .child(PROFILE_IMAGES_PATH)
                .child("$uid.jpg")
            
            imageRef.delete().await()
            
            // Remove image URL from user profile
            updateUserProfile(uid, profileImageUrl = "")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.1: Update user password in Firebase Auth
     */
    suspend fun updatePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))

            // Re-authenticate user before password change
            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(user.email ?: "", currentPassword)

            user.reauthenticate(credential).await()

            // Update password
            user.updatePassword(newPassword).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FR-2.2.1: Reset user password via email
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete driver document
     */
    suspend fun deleteDriverDocument(uid: String, documentType: String): Result<Unit> {
        return try {
            // Get document info first
            val docRef = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection("documents")
                .document(documentType)
            
            val docSnapshot = docRef.get().await()
            if (docSnapshot.exists()) {
                val storagePath = docSnapshot.getString("storagePath")
                
                // Delete from storage
                storagePath?.let { path ->
                    storage.reference.child(path).delete().await()
                }
                
                // Delete document record
                docRef.delete().await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.4: Get ride history with proper pagination
     */
    suspend fun getRideHistory(
        uid: String,
        limit: Int = 20,
        lastDocumentId: String? = null
    ): Result<RideHistoryResult> {
        return try {
            // Use a simple query without orderBy to avoid composite index requirement
            // We'll sort in memory instead
            val query = firestore.collection("bookings")
                .whereEqualTo("passengerId", uid)
                .limit((limit * 2).toLong()) // Get more documents to account for sorting and filtering

            val querySnapshot = query.get().await()
            val allBookings = querySnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(com.rj.islamove.data.models.Booking::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }

            // Filter for completed rides only (matching RideHistoryViewModel logic)
            val completedStatuses = listOf(
                com.rj.islamove.data.models.BookingStatus.COMPLETED.name,
                com.rj.islamove.data.models.BookingStatus.CANCELLED.name,
                com.rj.islamove.data.models.BookingStatus.EXPIRED.name
            )
            val completedBookings = allBookings.filter { booking ->
                booking.status.name in completedStatuses
            }

            // Sort by requestTime in descending order (most recent first) in memory
            val sortedBookings = completedBookings.sortedByDescending { it.requestTime }

            // Apply pagination in memory if needed
            val paginatedBookings = if (lastDocumentId != null) {
                // Find the index of the last document and take items after it
                val lastIndex = sortedBookings.indexOfFirst { it.id == lastDocumentId }
                if (lastIndex >= 0 && lastIndex < sortedBookings.size - 1) {
                    sortedBookings.drop(lastIndex + 1).take(limit)
                } else {
                    emptyList()
                }
            } else {
                // First page - take the first 'limit' items
                sortedBookings.take(limit)
            }

            val result = RideHistoryResult(
                rides = paginatedBookings,
                hasMore = paginatedBookings.size == limit && sortedBookings.size > paginatedBookings.size,
                lastDocumentId = paginatedBookings.lastOrNull()?.id
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Result for paginated ride history
 */
data class RideHistoryResult(
    val rides: List<com.rj.islamove.data.models.Booking>,
    val hasMore: Boolean,
    val lastDocumentId: String?
)