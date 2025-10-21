package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.ui.screens.admin.SystemConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val systemConfigCollection = firestore.collection("system_config")
    private val configDocumentId = "app_config"

    fun getSystemConfig(): Flow<Result<SystemConfig>> = callbackFlow {
        val listener = systemConfigCollection.document(configDocumentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SystemConfigRepo", "Error listening to system config", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                try {
                    val config = if (snapshot?.exists() == true) {
                        snapshot.toObject(SystemConfig::class.java) ?: getDefaultSystemConfig()
                    } else {
                        // Create default config if doesn't exist
                        val defaultConfig = getDefaultSystemConfig()
                        systemConfigCollection.document(configDocumentId).set(defaultConfig)
                        defaultConfig
                    }
                    trySend(Result.success(config))
                } catch (e: Exception) {
                    Log.e("SystemConfigRepo", "Error parsing system config", e)
                    trySend(Result.failure(e))
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun updateSystemConfig(config: SystemConfig): Result<Unit> {
        return try {
            systemConfigCollection.document(configDocumentId)
                .set(config)
                .await()
            
            Log.d("SystemConfigRepo", "System config updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SystemConfigRepo", "Failed to update system config", e)
            Result.failure(e)
        }
    }

    suspend fun updateMaintenanceMode(enabled: Boolean): Result<Unit> {
        return try {
            systemConfigCollection.document(configDocumentId)
                .update(mapOf(
                    "maintenanceMode" to enabled,
                    "lastUpdated" to System.currentTimeMillis()
                ))
                .await()
            
            Log.d("SystemConfigRepo", "Maintenance mode updated to: $enabled")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SystemConfigRepo", "Failed to update maintenance mode", e)
            Result.failure(e)
        }
    }

    suspend fun getFareConfig(): Result<FareConfig> {
        return try {
            val doc = firestore.collection("system_config")
                .document("fare_config")
                .get()
                .await()
            
            val fareConfig = if (doc.exists()) {
                doc.toObject(FareConfig::class.java) ?: getDefaultFareConfig()
            } else {
                val defaultConfig = getDefaultFareConfig()
                firestore.collection("system_config")
                    .document("fare_config")
                    .set(defaultConfig)
                defaultConfig
            }
            
            Result.success(fareConfig)
        } catch (e: Exception) {
            Log.e("SystemConfigRepo", "Failed to get fare config", e)
            Result.failure(e)
        }
    }

    suspend fun updateFareConfig(fareConfig: FareConfig): Result<Unit> {
        return try {
            firestore.collection("system_config")
                .document("fare_config")
                .set(fareConfig.copy(lastUpdated = System.currentTimeMillis()))
                .await()
            
            Log.d("SystemConfigRepo", "Fare config updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SystemConfigRepo", "Failed to update fare config", e)
            Result.failure(e)
        }
    }

    private fun getDefaultSystemConfig(): SystemConfig {
        return SystemConfig(
            maintenanceMode = false,
            allowNewRegistrations = true,
            enableNotifications = true,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun getDefaultFareConfig(): FareConfig {
        return FareConfig(
            baseFare = 40.0, // ₱40 base fare
            perKmRate = 12.0, // ₱12 per km
            perMinuteRate = 2.5, // ₱2.50 per minute
            surgeEnabled = true,
            maxSurgeMultiplier = 2.0,
            minimumFare = 50.0, // ₱50 minimum
            cancellationFee = 20.0, // ₱20 cancellation fee
            currency = "PHP",
            lastUpdated = System.currentTimeMillis()
        )
    }
}

data class FareConfig(
    val baseFare: Double = 40.0,
    val perKmRate: Double = 12.0,
    val perMinuteRate: Double = 2.5,
    val surgeEnabled: Boolean = true,
    val maxSurgeMultiplier: Double = 2.0,
    val minimumFare: Double = 50.0,
    val cancellationFee: Double = 20.0,
    val currency: String = "PHP",
    val lastUpdated: Long = System.currentTimeMillis()
)