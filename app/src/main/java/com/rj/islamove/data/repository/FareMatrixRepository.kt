package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.FareMatrixEntry
import com.rj.islamove.data.models.SanJoseLocationsData
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FareMatrixRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val FARE_MATRIX_COLLECTION = "fare_matrix"
        private const val FARE_MATRIX_DOCUMENT = "san_jose_matrix"
    }

    /**
     * Get all fare matrix entries from Firestore, fallback to local data
     */
    suspend fun getAllFareEntries(): Result<List<FareMatrixEntry>> {
        return try {
            val doc = firestore.collection(FARE_MATRIX_COLLECTION)
                .document(FARE_MATRIX_DOCUMENT)
                .get()
                .await()

            if (doc.exists()) {
                val fareEntries = doc.get("entries") as? List<Map<String, Any>>
                val entries = fareEntries?.mapNotNull { entry ->
                    try {
                        FareMatrixEntry(
                            id = entry["id"] as? String ?: "",
                            fromLocation = entry["fromLocation"] as? String ?: "",
                            toLocation = entry["toLocation"] as? String ?: "",
                            regularFare = (entry["regularFare"] as? Number)?.toDouble() ?: 0.0,
                            discountFare = (entry["discountFare"] as? Number)?.toDouble() ?: 0.0,
                            isActive = entry["isActive"] as? Boolean ?: true,
                            lastUpdated = (entry["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Log.e("FareMatrixRepository", "Error parsing fare entry", e)
                        null
                    }
                } ?: emptyList()
                Result.success(entries)
            } else {
                // Initialize with empty fare matrix (no default entries needed)
                val defaultEntries = listOf<FareMatrixEntry>()
                saveFareMatrix(defaultEntries)
                Result.success(defaultEntries)
            }
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error getting fare entries", e)
            // Fallback to empty list
            val fallbackEntries = listOf<FareMatrixEntry>()
            Result.success(fallbackEntries)
        }
    }

    /**
     * Save entire fare matrix to Firestore
     */
    suspend fun saveFareMatrix(entries: List<FareMatrixEntry>): Result<Unit> {
        return try {
            val data = mapOf(
                "entries" to entries.map { entry ->
                    mapOf(
                        "id" to entry.id,
                        "fromLocation" to entry.fromLocation,
                        "toLocation" to entry.toLocation,
                        "regularFare" to entry.regularFare,
                        "discountFare" to entry.discountFare,
                        "isActive" to entry.isActive,
                        "lastUpdated" to entry.lastUpdated
                    )
                },
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection(FARE_MATRIX_COLLECTION)
                .document(FARE_MATRIX_DOCUMENT)
                .set(data)
                .await()

            Log.d("FareMatrixRepository", "Fare matrix saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error saving fare matrix", e)
            Result.failure(e)
        }
    }

    /**
     * Update a single fare entry
     */
    suspend fun updateFareEntry(entry: FareMatrixEntry): Result<Unit> {
        return try {
            val allEntries = getAllFareEntries().getOrThrow().toMutableList()
            val index = allEntries.indexOfFirst { it.id == entry.id }

            if (index != -1) {
                allEntries[index] = entry.copy(lastUpdated = System.currentTimeMillis())
                saveFareMatrix(allEntries)
            } else {
                Result.failure(Exception("Fare entry not found"))
            }
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error updating fare entry", e)
            Result.failure(e)
        }
    }

    /**
     * Add new fare entry
     */
    suspend fun addFareEntry(entry: FareMatrixEntry): Result<Unit> {
        return try {
            val allEntries = getAllFareEntries().getOrThrow().toMutableList()
            val exists = allEntries.any {
                it.fromLocation == entry.fromLocation && it.toLocation == entry.toLocation
            }

            if (!exists) {
                val newEntry = entry.copy(
                    id = "entry_${System.currentTimeMillis()}",
                    lastUpdated = System.currentTimeMillis()
                )
                allEntries.add(newEntry)
                saveFareMatrix(allEntries)
            } else {
                Result.failure(Exception("Fare entry already exists for this route"))
            }
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error adding fare entry", e)
            Result.failure(e)
        }
    }

    /**
     * Delete fare entry
     */
    suspend fun deleteFareEntry(entryId: String): Result<Unit> {
        return try {
            val allEntries = getAllFareEntries().getOrThrow().toMutableList()
            val removed = allEntries.removeIf { it.id == entryId }

            if (removed) {
                saveFareMatrix(allEntries)
            } else {
                Result.failure(Exception("Fare entry not found"))
            }
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error deleting fare entry", e)
            Result.failure(e)
        }
    }

    /**
     * Get unique location names for dropdowns
     */
    suspend fun getUniqueLocations(): Result<List<String>> {
        return try {
            val entries = getAllFareEntries().getOrThrow()
            val locations = mutableSetOf<String>()

            entries.forEach { entry ->
                locations.add(entry.fromLocation)
                locations.add(entry.toLocation)
            }

            // Add locations from SanJoseLocationsData as well
            SanJoseLocationsData.locations.forEach { location ->
                locations.add(location.name)
                locations.add("Barangay ${location.barangay}")
            }

            Result.success(locations.sorted())
        } catch (e: Exception) {
            Log.e("FareMatrixRepository", "Error getting unique locations", e)
            Result.failure(e)
        }
    }
}