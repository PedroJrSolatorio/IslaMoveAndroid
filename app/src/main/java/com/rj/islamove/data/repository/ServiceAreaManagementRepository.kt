package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.data.models.ServiceDestination
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceAreaManagementRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val serviceAreasCollection = firestore.collection("service_areas")

    companion object {
        private const val TAG = "ServiceAreaManagementRepo"
    }

    suspend fun getAllServiceAreas(): Result<List<ServiceArea>> {
        return try {
            Log.d(TAG, "Fetching all service areas")
            val snapshot = serviceAreasCollection
                .get()
                .await()

            val serviceAreas = snapshot.toObjects<ServiceArea>()
            Log.d(TAG, "Successfully fetched ${serviceAreas.size} service areas")

            // Debug: Log each document and its destinations
            serviceAreas.forEachIndexed { index, area ->
                Log.d(TAG, "Area $index: id=${area.id}, name=${area.name}, isActive=${area.isActive}")
                area.destinations.forEachIndexed { destIndex, dest ->
                    Log.d(TAG, "  Destination $destIndex: id=${dest.id}, name=${dest.name}, isActive=${dest.isActive}")
                }
            }

            // Filter active areas and their active destinations
            val activeAreas = serviceAreas.filter { area ->
                area.isActive
            }.map { area ->
                area.copy(destinations = area.destinations.filter { it.isActive })
            }
            Log.d(TAG, "Active areas after filter: ${activeAreas.size}")

            // Log final active destinations count
            val totalActiveDestinations = activeAreas.sumOf { it.destinations.size }
            Log.d(TAG, "Total active destinations across all areas: $totalActiveDestinations")
            Result.success(activeAreas)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching service areas", e)
            Result.failure(e)
        }
    }

    suspend fun addServiceArea(serviceArea: ServiceArea): Result<Unit> {
        return try {
            val areaWithId = serviceArea.copy(
                id = serviceAreasCollection.document().id,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "Adding service area: ${areaWithId.name}, isActive: ${areaWithId.isActive}")
            serviceAreasCollection.document(areaWithId.id).set(areaWithId).await()
            Log.d(TAG, "Successfully added service area: ${areaWithId.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding service area: ${serviceArea.name}", e)
            Result.failure(e)
        }
    }

    suspend fun updateServiceArea(serviceArea: ServiceArea): Result<Unit> {
        return try {
            val updatedArea = serviceArea.copy(lastUpdated = System.currentTimeMillis())

            Log.d(TAG, "Updating service area: ${updatedArea.name}")
            serviceAreasCollection.document(updatedArea.id).set(updatedArea).await()
            Log.d(TAG, "Successfully updated service area: ${updatedArea.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service area: ${serviceArea.name}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteServiceArea(areaId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to delete service area with ID: $areaId")

            // First verify the document exists
            val doc = serviceAreasCollection.document(areaId).get().await()
            if (!doc.exists()) {
                Log.e(TAG, "Service area document not found: $areaId")
                return Result.failure(Exception("Service area not found"))
            }

            Log.d(TAG, "Found service area document, proceeding with deletion")
            serviceAreasCollection.document(areaId).delete().await()

            // Verify deletion by checking again
            val verifyDoc = serviceAreasCollection.document(areaId).get().await()
            if (verifyDoc.exists()) {
                Log.e(TAG, "Document still exists after deletion attempt: $areaId")
                return Result.failure(Exception("Deletion verification failed - document still exists"))
            }

            Log.d(TAG, "Successfully deleted and verified service area: $areaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting service area: $areaId", e)
            Result.failure(e)
        }
    }

    suspend fun addDestinationToArea(areaId: String, destination: ServiceDestination): Result<Unit> {
        return try {
            Log.d(TAG, "Adding destination ${destination.name} to area $areaId")

            // Get the current service area
            val areaDoc = serviceAreasCollection.document(areaId).get().await()
            val currentArea = areaDoc.toObject(ServiceArea::class.java)
                ?: return Result.failure(Exception("Service area not found"))

            // Add the new destination with generated ID
            val destinationWithId = destination.copy(
                id = generateDestinationId(),
                createdAt = System.currentTimeMillis()
            )

            val updatedDestinations = currentArea.destinations.toMutableList()
            updatedDestinations.add(destinationWithId)

            val updatedArea = currentArea.copy(
                destinations = updatedDestinations,
                lastUpdated = System.currentTimeMillis()
            )

            serviceAreasCollection.document(areaId).set(updatedArea).await()
            Log.d(TAG, "Successfully added destination ${destination.name} to area $areaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding destination to area", e)
            Result.failure(e)
        }
    }

    suspend fun updateDestinationInArea(areaId: String, destination: ServiceDestination): Result<Unit> {
        return try {
            Log.d(TAG, "Updating destination ${destination.name} in area $areaId")

            // Get the current service area
            val areaDoc = serviceAreasCollection.document(areaId).get().await()
            val currentArea = areaDoc.toObject(ServiceArea::class.java)
                ?: return Result.failure(Exception("Service area not found"))

            // Update the destination
            val updatedDestinations = currentArea.destinations.map { dest ->
                if (dest.id == destination.id) destination else dest
            }

            val updatedArea = currentArea.copy(
                destinations = updatedDestinations,
                lastUpdated = System.currentTimeMillis()
            )

            serviceAreasCollection.document(areaId).set(updatedArea).await()
            Log.d(TAG, "Successfully updated destination ${destination.name} in area $areaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating destination in area", e)
            Result.failure(e)
        }
    }

    suspend fun removeDestinationFromArea(areaId: String, destinationId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Removing destination $destinationId from area $areaId")

            // Get the current service area
            val areaDoc = serviceAreasCollection.document(areaId).get().await()
            val currentArea = areaDoc.toObject(ServiceArea::class.java)
                ?: return Result.failure(Exception("Service area not found"))

            // Remove the destination
            val updatedDestinations = currentArea.destinations.filter { it.id != destinationId }

            val updatedArea = currentArea.copy(
                destinations = updatedDestinations,
                lastUpdated = System.currentTimeMillis()
            )

            serviceAreasCollection.document(areaId).set(updatedArea).await()
            Log.d(TAG, "Successfully removed destination $destinationId from area $areaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing destination from area", e)
            Result.failure(e)
        }
    }

    private fun generateDestinationId(): String {
        return "dest_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Get the active zone boundary for driver filtering
     * Returns the first active service area that has a boundary defined
     */
    suspend fun getActiveZoneBoundary(): Result<ServiceArea?> {
        return try {
            Log.d(TAG, "Fetching active zone boundary")
            val snapshot = serviceAreasCollection
                .whereEqualTo("isActive", true)
                .get()
                .await()

            val serviceAreas = snapshot.toObjects<ServiceArea>()
            val zoneBoundary = serviceAreas.firstOrNull { it.boundary != null && it.boundary.points.isNotEmpty() }

            if (zoneBoundary != null) {
                Log.d(TAG, "Found active zone boundary: ${zoneBoundary.name} with ${zoneBoundary.boundary?.points?.size} points")
            } else {
                Log.d(TAG, "No active zone boundary found")
            }

            Result.success(zoneBoundary)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active zone boundary", e)
            Result.failure(e)
        }
    }

    /**
     * Get the active service boundary for driver filtering
     * Returns the service boundary that is used to filter which drivers can accept rides
     */
    suspend fun getActiveServiceBoundary(): Result<ServiceArea?> {
        return try {
            Log.d(TAG, "Fetching active service boundary for driver filtering")
            val snapshot = serviceAreasCollection
                .whereEqualTo("isActive", true)
                .get()
                .await()

            val serviceAreas = snapshot.toObjects<ServiceArea>()
            Log.d(TAG, "Found ${serviceAreas.size} active service areas total")

            // Debug: Log all service areas
            serviceAreas.forEachIndexed { index, area ->
                Log.d(TAG, "Area $index: name='${area.name}', isActive=${area.isActive}, hasBoundary=${area.boundary != null}, boundaryPoints=${area.boundary?.points?.size ?: 0}")
            }

            // Find service boundary specifically (not zone boundary)
            // Zone boundaries have "ZONE" in uppercase in their name
            val serviceBoundary = serviceAreas
                .filter {
                    val hasBoundary = it.boundary != null && it.boundary.points.isNotEmpty()
                    val notZone = !it.name.uppercase().contains("ZONE")
                    Log.d(TAG, "Filtering '${it.name}': hasBoundary=$hasBoundary, notZone=$notZone")
                    hasBoundary && notZone
                }
                .maxByOrNull { it.lastUpdated }

            if (serviceBoundary != null) {
                Log.d(TAG, "✅ Found active service boundary: ${serviceBoundary.name} with ${serviceBoundary.boundary?.points?.size} points")
            } else {
                Log.d(TAG, "❌ No active service boundary found, driver filtering will use default area")
            }

            Result.success(serviceBoundary)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active service boundary", e)
            Result.failure(e)
        }
    }
}