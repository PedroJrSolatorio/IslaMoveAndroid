package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import com.rj.islamove.data.models.ZoneBoundary
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneBoundaryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val zoneBoundariesCollection = firestore.collection("zone_boundaries")

    companion object {
        private const val TAG = "ZoneBoundaryRepository"
    }

    /**
     * Get all zone boundaries from Firestore
     */
    suspend fun getAllZoneBoundaries(): Result<List<ZoneBoundary>> {
        return try {
            Log.d(TAG, "Fetching all zone boundaries")
            val snapshot = zoneBoundariesCollection
                .get(com.google.firebase.firestore.Source.SERVER)  // Force server fetch to avoid cache issues
                .await()

            val boundaries = snapshot.toObjects<ZoneBoundary>()
            Log.d(TAG, "Successfully fetched ${boundaries.size} zone boundaries")

            // Filter active boundaries locally to ensure consistency
            val activeBoundaries = boundaries.filter { it.isActive }
            Log.d(TAG, "Active boundaries after filter: ${activeBoundaries.size}")

            Result.success(activeBoundaries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching zone boundaries", e)
            Result.failure(e)
        }
    }

    /**
     * Get all zone boundaries (including inactive ones) for admin purposes
     */
    suspend fun getAllZoneBoundariesIncludingInactive(): Result<List<ZoneBoundary>> {
        return try {
            Log.d(TAG, "Fetching all zone boundaries including inactive")
            val snapshot = zoneBoundariesCollection
                .get(com.google.firebase.firestore.Source.SERVER)  // Force server fetch to avoid cache issues
                .await()

            val boundaries = snapshot.toObjects<ZoneBoundary>()
            Log.d(TAG, "Successfully fetched ${boundaries.size} total boundaries (active and inactive)")

            Result.success(boundaries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all zone boundaries", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific zone boundary by ID
     */
    suspend fun getZoneBoundaryById(boundaryId: String): Result<ZoneBoundary?> {
        return try {
            Log.d(TAG, "Fetching zone boundary: $boundaryId")
            val snapshot = zoneBoundariesCollection
                .document(boundaryId)
                .get()
                .await()

            val boundary = snapshot.toObject(ZoneBoundary::class.java)
            Log.d(TAG, "Successfully fetched zone boundary: ${boundary?.name}")
            Result.success(boundary)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching zone boundary: $boundaryId", e)
            Result.failure(e)
        }
    }

    /**
     * Add a new zone boundary
     */
    suspend fun addZoneBoundary(zoneBoundary: ZoneBoundary): Result<String> {
        return try {
            val docRef = zoneBoundariesCollection.document()
            val boundaryWithId = zoneBoundary.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "Adding zone boundary: ${boundaryWithId.name}, points: ${boundaryWithId.points.size}")
            docRef.set(boundaryWithId).await()
            Log.d(TAG, "Successfully added zone boundary: ${boundaryWithId.name}")
            Result.success(boundaryWithId.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding zone boundary: ${zoneBoundary.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing zone boundary
     */
    suspend fun updateZoneBoundary(zoneBoundary: ZoneBoundary): Result<Unit> {
        return try {
            val updatedBoundary = zoneBoundary.copy(lastUpdated = System.currentTimeMillis())

            Log.d(TAG, "Updating zone boundary: ${updatedBoundary.name}")
            zoneBoundariesCollection.document(updatedBoundary.id).set(updatedBoundary).await()
            Log.d(TAG, "Successfully updated zone boundary: ${updatedBoundary.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating zone boundary: ${zoneBoundary.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a zone boundary (HARD DELETE - permanently removes from Firebase)
     */
    suspend fun deleteZoneBoundary(boundaryId: String): Result<Unit> {
        return try {
            Log.d(TAG, "HARD deleting zone boundary from Firebase: $boundaryId")

            // First verify the document exists
            val doc = zoneBoundariesCollection.document(boundaryId).get().await()
            if (!doc.exists()) {
                Log.e(TAG, "Boundary document not found: $boundaryId")
                return Result.failure(Exception("Boundary not found"))
            }

            Log.d(TAG, "Found boundary document, proceeding with hard delete")
            zoneBoundariesCollection.document(boundaryId).delete().await()

            // Verify the deletion worked
            val verifyDoc = zoneBoundariesCollection.document(boundaryId).get().await()
            if (verifyDoc.exists()) {
                Log.e(TAG, "Document still exists after deletion: $boundaryId")
                return Result.failure(Exception("Deletion verification failed"))
            }

            Log.d(TAG, "Successfully HARD deleted zone boundary from Firebase: $boundaryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting zone boundary: $boundaryId", e)
            Result.failure(e)
        }
    }

    /**
     * Reactivate a zone boundary (set isActive = true)
     */
    suspend fun reactivateZoneBoundary(boundaryId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Reactivating zone boundary: $boundaryId")
            zoneBoundariesCollection.document(boundaryId)
                .update("isActive", true, "lastUpdated", System.currentTimeMillis())
                .await()
            Log.d(TAG, "Successfully reactivated zone boundary: $boundaryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error reactivating zone boundary: $boundaryId", e)
            Result.failure(e)
        }
    }

    /**
     * Hard delete a zone boundary (permanently remove from database)
     */
    suspend fun hardDeleteZoneBoundary(boundaryId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Hard deleting zone boundary: $boundaryId")
            zoneBoundariesCollection.document(boundaryId).delete().await()
            Log.d(TAG, "Successfully hard deleted zone boundary: $boundaryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error hard deleting zone boundary: $boundaryId", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a zone boundary name already exists
     */
    suspend fun boundaryNameExists(name: String, excludeId: String? = null): Result<Boolean> {
        return try {
            Log.d(TAG, "Checking if boundary name exists: $name")
            val snapshot = zoneBoundariesCollection
                .whereEqualTo("name", name)
                .whereEqualTo("isActive", true)
                .get()
                .await()

            val exists = if (excludeId != null) {
                snapshot.documents.any { it.id != excludeId }
            } else {
                !snapshot.isEmpty
            }

            Log.d(TAG, "Boundary name '$name' exists: $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking boundary name: $name", e)
            Result.failure(e)
        }
    }
}