package com.rj.islamove.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.rj.islamove.BuildConfig
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException

@Singleton
class CloudinaryRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val httpClient: OkHttpClient,
    private val cloudinaryDirectRepository: CloudinaryDirectRepository
) {
    companion object {
        // ✅ Point to your backend (update this URL)
        private const val BACKEND_URL = BuildConfig.RENDER_BASE_URL
        private const val TAG = "CloudinaryRepository"
    }

    /**
     * Main upload method - routes to correct endpoint based on auth status
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        folder: String = "driver_documents",
        publicId: String? = null,
        tempUserId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser

            if (currentUser != null && tempUserId == null) {
                // ✅ User is authenticated - use secure backend upload
//                Log.d(TAG, "User authenticated, uploading via backend")
                uploadViaBackend(context, imageUri, folder, currentUser.uid)
            } else if (tempUserId != null) {
                // ✅ Re-uploading documents (authenticated but using temp storage)
//                Log.d(TAG, "Re-uploading to temp storage for user: $tempUserId")
                cloudinaryDirectRepository.uploadRegistrationDocument(
                    context, imageUri, folder, tempUserId, publicId
                )
            } else {
                // ✅ User NOT authenticated - use DIRECT Cloudinary upload for registration
                return@withContext Result.failure(
                    Exception("Temporary user ID required for registration uploads")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Upload for authenticated users (goes through backend with token)
     */
    private suspend fun uploadViaBackend(
        context: Context,
        imageUri: Uri,
        folder: String,
        userId: String,
        publicId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
//            Log.d(TAG, "Getting auth token...")
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext Result.failure(Exception("Failed to get auth token"))

            val file = uriToFile(context, imageUri)
            Log.d(TAG, "File prepared: ${file.name}, size: ${file.length()} bytes")

            // ✅ Detect actual MIME type
            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
//            Log.d(TAG, "Detected MIME type: $mimeType")

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("userId", userId)
                .addFormDataPart("documentType", folder)
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody(mimeType.toMediaTypeOrNull()) // ✅ Use actual MIME type
                )

            if (publicId != null) {
                builder.addFormDataPart("publicId", publicId)
            }

            val requestBody = builder.build()

            val request = Request.Builder()
                .url("$BACKEND_URL/api/upload-document")
                .addHeader("Authorization", "Bearer $idToken")
                .post(requestBody)
                .build()

//            Log.d(TAG, "Sending request to: ${request.url}")
            val response = httpClient.newCall(request).execute()

            val responseBody = response.body?.string()
//            Log.d(TAG, "Response code: ${response.code}")
//            Log.d(TAG, "Response body: $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody ?: "{}")
                val imageUrl = json.getString("imageUrl")

                file.delete()
//                Log.d(TAG, "✅ Upload successful: $imageUrl")
                Result.success(imageUrl)
            } else {
                file.delete()
                Result.failure(Exception("Upload failed: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend upload error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Convert Android URI to File
     */
    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open input stream for URI: $uri")

        val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
        val outputStream = FileOutputStream(tempFile)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}