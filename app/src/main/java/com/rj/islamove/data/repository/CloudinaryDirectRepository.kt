package com.rj.islamove.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.rj.islamove.BuildConfig
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class CloudinaryDirectRepository @Inject constructor() {

    companion object {
        private const val TAG = "CloudinaryDirect"

        // These are SAFE to expose for registration uploads
        // They only allow uploads to registration_temp folder
        private val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
        private val UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET
        private val PROFILE_PRESET = BuildConfig.CLOUDINARY_PROFILE_PRESET
    }

    /**
     * Initialize Cloudinary - call this once in Application onCreate
     */
    fun initialize(context: Context) {
        try {
            val config = mapOf(
                "cloud_name" to CLOUD_NAME,
                "secure" to true
            )
            MediaManager.init(context, config)
            Log.d(TAG, "Cloudinary initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cloudinary", e)
        }
    }

    /**
     * Direct upload to Cloudinary (fast, for registration only)
     */
    suspend fun uploadRegistrationDocument(
        context: Context,
        imageUri: Uri,
        documentType: String,
        tempUserId: String,
        publicId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val finalPublicId = publicId ?: "${documentType}_${System.currentTimeMillis()}"
                val requestId = MediaManager.get().upload(imageUri)
                    .unsigned(UPLOAD_PRESET)
                    .option("folder", "registration_temp/$tempUserId")
                    .option("public_id", finalPublicId)
                    .option("resource_type", "image")
                    .option("tags", arrayOf("registration", tempUserId, documentType))
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
//                            Log.d(TAG, "Upload started: $requestId (publicId: $finalPublicId)")
                        }

                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
//                            Log.d(TAG, "Upload progress: $progress%")
                        }

                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            if (secureUrl != null) {
//                                Log.d(TAG, "✅ Upload successful: $secureUrl")
                                continuation.resume(Result.success(secureUrl))
                            } else {
//                                Log.e(TAG, "No secure_url in response")
                                continuation.resume(Result.failure(Exception("No URL in response")))
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
//                            Log.e(TAG, "Upload error: ${error.description}")
                            continuation.resume(Result.failure(Exception(error.description)))
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {
//                            Log.w(TAG, "Upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()

                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                    Log.d(TAG, "Upload cancelled: $requestId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Upload exception: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Upload profile picture (for authenticated users)
     */
    suspend fun uploadProfilePicture(
        context: Context,
        imageUri: Uri,
        userId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val requestId = MediaManager.get().upload(imageUri)
                    .unsigned(PROFILE_PRESET)  // ✅ Use profile preset
                    .option("folder", "profile_pictures/$userId")  // ✅ User-specific folder
                    .option("public_id", "profile_${System.currentTimeMillis()}")
                    .option("resource_type", "image")
                    .option("tags", arrayOf("profile", userId))
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Profile upload started: $requestId")
                        }

                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
                            Log.d(TAG, "Profile upload progress: $progress%")
                        }

                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            if (secureUrl != null) {
                                Log.d(TAG, "✅ Profile upload successful: $secureUrl")
                                continuation.resume(Result.success(secureUrl))
                            } else {
                                Log.e(TAG, "No secure_url in response")
                                continuation.resume(Result.failure(Exception("No URL in response")))
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "Profile upload error: ${error.description}")
                            continuation.resume(Result.failure(Exception(error.description)))
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "Profile upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()

                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                    Log.d(TAG, "Profile upload cancelled: $requestId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Profile upload exception: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Delete an image from Cloudinary via backend
     */
    suspend fun deleteImage(publicId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext Result.failure(Exception("Failed to get auth token"))

            val requestBody = JSONObject().apply {
                put("publicId", publicId)
            }

            val request = Request.Builder()
                .url("${com.rj.islamove.BuildConfig.RENDER_BASE_URL}/api/delete-image")
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .delete(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d(TAG, "Deleting image from Cloudinary: $publicId")
            val client = OkHttpClient()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Image deleted successfully: $publicId")
                Result.success(Unit)
            } else {
                val error = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Delete failed: $error")
                Result.failure(Exception("Delete failed: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Extract public_id from Cloudinary URL
     * Example URL: https://res.cloudinary.com/dlh5Tq5oi/image/upload/v1769875291/profile_pictures/Qow2ACjJ9iZ0iccGr2a9PjgLKL58/profile_1764039485129.jpg
     * Returns: profile_pictures/Qow2ACjJ9iZ0iccGr2a9PjgLKL58/profile_1764039485129
     */
    fun extractPublicId(cloudinaryUrl: String): String? {
        return try {
            // Split by "upload/" and take everything after it
            val parts = cloudinaryUrl.split("/upload/")
            if (parts.size < 2) return null

            // Remove version parameter (v1234567890/) and file extension
            val pathWithVersion = parts[1]
            val pathWithoutVersion = pathWithVersion.substringAfter("/") // Remove v1234567890/
            val pathWithoutExtension = pathWithoutVersion.substringBeforeLast(".") // Remove .jpg

            Log.d(TAG, "Extracted public_id: $pathWithoutExtension from URL: $cloudinaryUrl")
            pathWithoutExtension
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract public_id from URL: $cloudinaryUrl", e)
            null
        }
    }
}