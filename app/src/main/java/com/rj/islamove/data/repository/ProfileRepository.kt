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
    private val cloudinaryDirectRepository: CloudinaryDirectRepository
) {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val PROFILE_IMAGES_PATH = "profile_images"
        private const val DRIVER_DOCUMENTS_PATH = "driver_documents"
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
            // Get current profile image URL before uploading new one
            val currentUser = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .await()

            val oldProfileImageUrl = currentUser.getString("profileImageUrl")
            android.util.Log.d("ProfileRepository", "Old profile image URL: $oldProfileImageUrl")

            // Upload new profile image to Cloudinary
            val uploadResult = cloudinaryDirectRepository.uploadProfilePicture(
                context = context,
                imageUri = imageUri,
                userId = uid
            )

            uploadResult.fold(
                onSuccess = { newImageUrl ->
                    android.util.Log.d("ProfileRepository", "New profile image uploaded: $newImageUrl")

                    // Delete old profile image if it exists
                    if (!oldProfileImageUrl.isNullOrEmpty() && oldProfileImageUrl.contains("cloudinary.com")) {
                        try {
                            val publicId = cloudinaryDirectRepository.extractPublicId(oldProfileImageUrl)
                            if (publicId != null) {
                                android.util.Log.d("ProfileRepository", "Deleting old profile image with public_id: $publicId")
                                cloudinaryDirectRepository.deleteImage(publicId)
                                android.util.Log.d("ProfileRepository", "Old profile image deleted successfully")
                            } else {
                                android.util.Log.w("ProfileRepository", "Could not extract public_id from old URL")
                            }
                        } catch (e: Exception) {
                            // Don't fail the entire operation if deletion fails
                            android.util.Log.e("ProfileRepository", "Failed to delete old profile image, but new image uploaded successfully", e)
                        }
                    }

                    Result.success(newImageUrl)
                },
                onFailure = { error ->
                    android.util.Log.e("ProfileRepository", "Failed to upload new profile image", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepository", "Upload profile image error", e)
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
            val uploadResult = cloudinaryDirectRepository.uploadRegistrationDocument(
                context = context,
                imageUri = imageUri,
                documentType = "student_id",
                tempUserId = uid
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