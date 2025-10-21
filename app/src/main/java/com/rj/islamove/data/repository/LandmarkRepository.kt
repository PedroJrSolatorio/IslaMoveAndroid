package com.rj.islamove.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.CustomLandmark
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LandmarkRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val landmarksCollection = firestore.collection("admin_landmarks")

    suspend fun getAllLandmarks(): List<CustomLandmark> {
        return try {
            val snapshot = landmarksCollection.get().await()
            snapshot.documents.mapNotNull { document ->
                document.toObject(CustomLandmark::class.java)?.copy(id = document.id)
            }
        } catch (e: Exception) {
            throw Exception("Failed to fetch landmarks: ${e.message}")
        }
    }

    suspend fun saveLandmark(landmark: CustomLandmark) {
        try {
            landmarksCollection.document(landmark.id).set(landmark).await()
        } catch (e: Exception) {
            throw Exception("Failed to save landmark: ${e.message}")
        }
    }

    suspend fun deleteLandmark(landmarkId: String) {
        try {
            landmarksCollection.document(landmarkId).delete().await()
        } catch (e: Exception) {
            throw Exception("Failed to delete landmark: ${e.message}")
        }
    }

    suspend fun clearAllLandmarks() {
        try {
            val snapshot = landmarksCollection.get().await()
            val batch = firestore.batch()

            snapshot.documents.forEach { document ->
                batch.delete(document.reference)
            }

            batch.commit().await()
        } catch (e: Exception) {
            throw Exception("Failed to clear all landmarks: ${e.message}")
        }
    }

    suspend fun getLandmarkById(id: String): CustomLandmark? {
        return try {
            val document = landmarksCollection.document(id).get().await()
            document.toObject(CustomLandmark::class.java)?.copy(id = document.id)
        } catch (e: Exception) {
            null
        }
    }
}