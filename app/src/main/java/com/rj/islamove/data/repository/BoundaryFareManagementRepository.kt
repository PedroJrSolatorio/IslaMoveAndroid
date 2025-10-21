package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.BoundaryFareBatch
import com.rj.islamove.data.models.BoundaryFareRule
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoundaryFareManagementRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val boundaryFaresCollection = firestore.collection("boundary_fares")

    companion object {
        private const val TAG = "BoundaryFareMgmtRepo"
    }

    /**
     * Get all boundary fare batches
     */
    suspend fun getAllFareBatches(): Result<List<BoundaryFareBatch>> {
        return try {
            Log.d(TAG, "Fetching all boundary fare batches")
            val snapshot = boundaryFaresCollection
                .get()
                .await()

            val batches = snapshot.toObjects(BoundaryFareBatch::class.java)
            Log.d(TAG, "Successfully fetched ${batches.size} fare batches")

            // Filter active batches and their active rules
            val activeBatches = batches.filter { batch -> batch.isActive }
                .map { batch -> batch.copy(rules = batch.rules.filter { rule -> rule.isActive }) }

            Result.success(activeBatches)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fare batches", e)
            Result.failure(e)
        }
    }

    /**
     * Get fare rules for a specific boundary
     */
    suspend fun getFaresByBoundary(fromBoundary: String): Result<List<BoundaryFareRule>> {
        return try {
            Log.d(TAG, "Fetching fare rules for boundary: $fromBoundary")
            val batches = getAllFareBatches().getOrThrow()

            val rules = batches.flatMap { batch ->
                batch.rules.filter { rule ->
                    rule.fromBoundary.equals(fromBoundary, ignoreCase = true) && rule.isActive
                }
            }

            Log.d(TAG, "Found ${rules.size} active fare rules for boundary: $fromBoundary")
            Result.success(rules)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fares by boundary", e)
            Result.failure(e)
        }
    }

    /**
     * Get fare for specific boundary to destination combination
     */
    suspend fun getFareForRoute(fromBoundary: String, toLocation: String): Result<Double?> {
        return try {
            Log.d(TAG, "Fetching fare for route: $fromBoundary -> $toLocation")
            val rulesResult = getFaresByBoundary(fromBoundary)

            if (rulesResult.isSuccess) {
                val rule = rulesResult.getOrThrow().find { it.toLocation.equals(toLocation, ignoreCase = true) }
                Result.success(rule?.fare)
            } else {
                Result.failure(rulesResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fare for route", e)
            Result.failure(e)
        }
    }

    /**
     * Add new fare batch
     */
    suspend fun addFareBatch(batch: BoundaryFareBatch): Result<Unit> {
        return try {
            val batchWithId = batch.copy(
                id = boundaryFaresCollection.document().id,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "Adding fare batch: ${batchWithId.name}")
            boundaryFaresCollection.document(batchWithId.id).set(batchWithId).await()
            Log.d(TAG, "Successfully added fare batch: ${batchWithId.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding fare batch: ${batch.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Update fare batch
     */
    suspend fun updateFareBatch(batch: BoundaryFareBatch): Result<Unit> {
        return try {
            val updatedBatch = batch.copy(lastUpdated = System.currentTimeMillis())

            Log.d(TAG, "Updating fare batch: ${updatedBatch.name}")
            boundaryFaresCollection.document(updatedBatch.id).set(updatedBatch).await()
            Log.d(TAG, "Successfully updated fare batch: ${updatedBatch.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating fare batch: ${batch.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete fare batch
     */
    suspend fun deleteFareBatch(batchId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting fare batch: $batchId")
            boundaryFaresCollection.document(batchId).delete().await()
            Log.d(TAG, "Successfully deleted fare batch: $batchId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting fare batch: $batchId", e)
            Result.failure(e)
        }
    }

    /**
     * Add fare rule to batch
     */
    suspend fun addFareRuleToBatch(batchId: String, rule: BoundaryFareRule): Result<Unit> {
        return try {
            Log.d(TAG, "Adding fare rule ${rule.fromBoundary} -> ${rule.toLocation} to batch $batchId")

            // Get the current batch
            val batchDoc = boundaryFaresCollection.document(batchId).get().await()
            val currentBatch = batchDoc.toObject(BoundaryFareBatch::class.java)
                ?: return Result.failure(Exception("Fare batch not found"))

            // Check if rule already exists
            val ruleExists = currentBatch.rules.any { existingRule ->
                existingRule.fromBoundary.equals(rule.fromBoundary, ignoreCase = true) &&
                existingRule.toLocation.equals(rule.toLocation, ignoreCase = true)
            }

            if (ruleExists) {
                return Result.failure(Exception("Fare rule already exists for this route"))
            }

            // Add the new rule with generated ID
            val ruleWithId = rule.copy(
                id = generateRuleId(),
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )

            val updatedRules = currentBatch.rules.toMutableList()
            updatedRules.add(ruleWithId)

            val updatedBatch = currentBatch.copy(
                rules = updatedRules,
                lastUpdated = System.currentTimeMillis()
            )

            boundaryFaresCollection.document(batchId).set(updatedBatch).await()
            Log.d(TAG, "Successfully added fare rule to batch $batchId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding fare rule to batch", e)
            Result.failure(e)
        }
    }

    /**
     * Update fare rule in batch
     */
    suspend fun updateFareRuleInBatch(batchId: String, rule: BoundaryFareRule): Result<Unit> {
        return try {
            Log.d(TAG, "Updating fare rule ${rule.fromBoundary} -> ${rule.toLocation} in batch $batchId")

            // Get the current batch
            val batchDoc = boundaryFaresCollection.document(batchId).get().await()
            val currentBatch = batchDoc.toObject(BoundaryFareBatch::class.java)
                ?: return Result.failure(Exception("Fare batch not found"))

            // Update the rule
            val updatedRules = currentBatch.rules.map { existingRule ->
                if (existingRule.id == rule.id) {
                    rule.copy(lastUpdated = System.currentTimeMillis())
                } else {
                    existingRule
                }
            }

            val updatedBatch = currentBatch.copy(
                rules = updatedRules,
                lastUpdated = System.currentTimeMillis()
            )

            boundaryFaresCollection.document(batchId).set(updatedBatch).await()
            Log.d(TAG, "Successfully updated fare rule in batch $batchId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating fare rule in batch", e)
            Result.failure(e)
        }
    }

    /**
     * Remove fare rule from batch
     */
    suspend fun removeFareRuleFromBatch(batchId: String, ruleId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Removing fare rule $ruleId from batch $batchId")

            // Get the current batch
            val batchDoc = boundaryFaresCollection.document(batchId).get().await()
            val currentBatch = batchDoc.toObject(BoundaryFareBatch::class.java)
                ?: return Result.failure(Exception("Fare batch not found"))

            // Remove the rule
            val updatedRules = currentBatch.rules.filter { it.id != ruleId }

            val updatedBatch = currentBatch.copy(
                rules = updatedRules,
                lastUpdated = System.currentTimeMillis()
            )

            boundaryFaresCollection.document(batchId).set(updatedBatch).await()
            Log.d(TAG, "Successfully removed fare rule from batch $batchId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing fare rule from batch", e)
            Result.failure(e)
        }
    }

    /**
     * Get available boundaries from San Jose data
     */
    fun getAvailableBoundaries(): List<String> {
        return listOf(
            "POBLACION",
            "STA. CRUZ",
            "CUARINTA",
            "LUNA",
            "AURELIO",
            "DON RUBEN",
            "MAHAYAHAY",
            "MATINGBE",
            "JACQUEZ"
        )
    }

    /**
     * Get common destinations (includes boundary names for within-boundary pricing)
     */
    fun getCommonDestinations(): List<String> {
        // Include boundary names so admins can set boundary-to-itself prices
        val boundaries = getAvailableBoundaries()
        val commonPlaces = listOf(
            "Municipal Hall",
            "San Jose Poblacion",
            "San Jose Market",
            "San Jose Port",
            "PBMA Headquarters",
            "San Jose Jeepney Terminal",
            "San Jose Tricycle Terminal",
            "Islander's Castle",
            "Bito Beach",
            "San Jose Beach Resort"
        )

        // Return boundaries first, then common places (for better UX)
        return boundaries + commonPlaces
    }

    private fun generateRuleId(): String {
        return "rule_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}