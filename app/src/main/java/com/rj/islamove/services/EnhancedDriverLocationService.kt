package com.rj.islamove.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.rj.islamove.data.repository.DriverLocation
import com.rj.islamove.utils.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced driver location service with improved real-time updates and battery optimization
 */
class EnhancedDriverLocationService(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val LOCATION_UPDATE_INTERVAL_MS = 1000L // 1 second (reduced from 1.5s for real-time)
        private const val BATTERY_SAVE_INTERVAL_MS = 4000L // 4 seconds (reduced from 5s)
        private const val HIGH_PRECISION_INTERVAL_MS = 500L // 0.5 seconds during active pickup (reduced from 0.75s)
        private const val MIN_MOVEMENT_THRESHOLD = 0.1 // 0.1 meters - very low for virtual location testing
    }

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var locationUpdateJob: Job? = null
    private var lastLocation: Point? = null
    private var lastUpdateTime: Long = 0
    private var isHighPrecisionMode = false
    private var isBatterySaveMode = false

    fun startLocationUpdates(
        onLocationUpdate: (Point) -> Unit,
        onError: (Exception) -> Unit,
        isActiveRide: Boolean = false,
        driverId: String? = null
    ) {
        stopLocationUpdates()

        val actualDriverId = driverId ?: auth.currentUser?.uid
        require(actualDriverId != null) { "Driver ID is required" }

        isHighPrecisionMode = isActiveRide
        val updateInterval = getUpdateInterval()

        locationUpdateJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val currentLocation = getCurrentLocation()
                    currentLocation?.let { location ->
                        val currentTime = System.currentTimeMillis()
                        if (shouldUpdateLocation(location, currentTime)) {
                            // Update local state
                            withContext(Dispatchers.Main) {
                                onLocationUpdate(location)
                            }

                            // Update Firebase with optimized data structure
                            updateLocationInFirebase(actualDriverId, location)

                            lastLocation = location
                            lastUpdateTime = currentTime
                        }
                    }

                    delay(updateInterval)

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                    delay(5000)
                }
            }
        }
    }

    fun stopLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = null
    }

    fun setHighPrecisionMode(enabled: Boolean) {
        isHighPrecisionMode = enabled
    }

    fun setBatterySaveMode(enabled: Boolean) {
        isBatterySaveMode = enabled
    }

    private suspend fun getCurrentLocation(): Point? = withContext(Dispatchers.IO) {
        try {
            // Use your existing location utility
            val locationUtils = com.rj.islamove.utils.LocationUtils(context)
            if (locationUtils.hasLocationPermissions()) {
                locationUtils.getCurrentLocation()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun shouldUpdateLocation(newLocation: Point, currentTime: Long): Boolean {
        // Validate coordinates first
        if (!isValidCoordinates(newLocation)) {
            android.util.Log.w("EnhancedDriverLocationService", "Invalid coordinates: ${newLocation.latitude()}, ${newLocation.longitude()}")
            return false
        }

        // Always update if no previous location
        if (lastLocation == null) {
            lastUpdateTime = currentTime
            return true
        }

        // Check movement threshold
        val distance = calculateDistance(lastLocation!!, newLocation)
        val timeSinceLastUpdate = currentTime - lastUpdateTime

        // For real-time updates: always update if it's been more than 2 seconds, even if small movement
        val shouldUpdateByTime = timeSinceLastUpdate > 2000L // 2 seconds (reduced from 3s)

        val shouldUpdate = distance > MIN_MOVEMENT_THRESHOLD || shouldUpdateByTime

        if (shouldUpdate) {
            android.util.Log.d("EnhancedDriverLocationService",
                "Location update: distance=${distance}m, timeSinceLast=${timeSinceLastUpdate}ms, threshold=$MIN_MOVEMENT_THRESHOLD")
        }

        return shouldUpdate
    }

    private fun isValidCoordinates(point: Point): Boolean {
        return point.latitude() in -90.0..90.0 && point.longitude() in -180.0..180.0
    }

    private fun getUpdateInterval(): Long {
        return when {
            isHighPrecisionMode -> HIGH_PRECISION_INTERVAL_MS
            isBatterySaveMode -> BATTERY_SAVE_INTERVAL_MS
            else -> LOCATION_UPDATE_INTERVAL_MS
        }
    }

    private fun updateLocationInFirebase(driverId: String, location: Point) {
        val driverLocationRef = database.reference.child("driver_locations").child(driverId)

        val driverLocation = DriverLocation(
            driverId = driverId,
            latitude = location.latitude(),
            longitude = location.longitude(),
            lastUpdate = System.currentTimeMillis(),
            heading = 0.0,
            speed = 0.0,
            online = true
        )

        // Update with priority for real-time queries
        driverLocationRef.setValue(driverLocation)
        driverLocationRef.setPriority(System.currentTimeMillis())

        // Also update in active rides collection for faster passenger queries
        updateActiveRidesLocation(driverId, location)
    }

    private fun updateActiveRidesLocation(driverId: String, location: Point) {
        val activeRidesRef = database.reference.child("active_drivers")

        val locationData = mapOf(
            "latitude" to location.latitude(),
            "longitude" to location.longitude(),
            "timestamp" to System.currentTimeMillis(),
            "driverId" to driverId
        )

        activeRidesRef.child(driverId).setValue(locationData)
        activeRidesRef.child(driverId).setPriority(System.currentTimeMillis())
    }

    private fun calculateDistance(point1: Point, point2: Point): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(point1.latitude())
        val lat2Rad = Math.toRadians(point2.latitude())
        val deltaLatRad = Math.toRadians(point2.latitude() - point1.latitude())
        val deltaLngRad = Math.toRadians(point2.longitude() - point1.longitude())

        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Clean up driver location data when going offline
     */
    fun cleanupDriverLocation(driverId: String? = null) {
        val actualDriverId = driverId ?: auth.currentUser?.uid
        actualDriverId ?: return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Remove from active drivers
                database.reference.child("active_drivers").child(actualDriverId).removeValue()

                // Mark as offline in main location
                database.reference.child("driver_locations").child(actualDriverId)
                    .child("online").setValue(false)

            } catch (e: Exception) {
                android.util.Log.e("EnhancedDriverLocationService", "Error cleaning up location", e)
            }
        }
    }
}