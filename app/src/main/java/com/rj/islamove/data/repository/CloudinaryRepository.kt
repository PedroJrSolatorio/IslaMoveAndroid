package com.rj.islamove.data.repository

import android.content.Context
import android.net.Uri
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryRepository @Inject constructor(
    private val cloudinary: Cloudinary
) {

    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        folder: String = "driver_documents",
        publicId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Convert URI to File
            val file = uriToFile(context, imageUri)

            val uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                "quality", "auto:good",
                "format", "jpg",
                "type", "authenticated"
            ).apply {
                publicId?.let { put("public_id", it) }
            }

            val uploadResult = cloudinary.uploader().upload(file, uploadParams)
            val imageUrl = uploadResult["secure_url"] as? String
                ?: throw Exception("Failed to get image URL")

            // Clean up temp file
            file.delete()

            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteImage(publicId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
            val deletionResult = result["result"] as? String
            Result.success(deletionResult == "ok")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open input stream")

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