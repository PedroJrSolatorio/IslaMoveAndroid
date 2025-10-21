package com.rj.islamove.utils

import android.content.Context
import com.rj.islamove.data.repository.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optimized location update manager that reduces Firebase API calls while maintaining live tracking
 */
class OptimizedLocationUpdateManager(
    private val context: Context,
    private val driverRepository: DriverRepository,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 1500L // 1.5 seconds minimum (was 3s)
        private const val MIN_DISTANCE_METERS = 5.0 // 5 meters minimum movement (was 10m)
        private const val MAX_UPDATE_INTERVAL_MS = 5000L // 5 seconds maximum (was 15s)
        private const val ACTIVE_RIDE_UPDATE_INTERVAL_MS = 1000L // 1 second during active rides (was 2s)
    }

    private var updateJob: Job? = null
    private var lastLocation: Point? = null
    private var lastUpdateTime = 0L
    private var isActiveRide = false

    // Location smoothing for route calculation
    private val locationHistory = mutableListOf<Point>()
    private val maxHistorySize = 3

    fun startLocationUpdates(
        onLocationUpdate: (Point) -> Unit,
        onError: (Exception) -> Unit,
        isActiveRide: Boolean = false
    ) {
        this.isActiveRide = isActiveRide
        stopLocationUpdates()

        updateJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val location = getCurrentLocation()
                    if (location != null && shouldUpdateLocation(location)) {
                        withContext(Dispatchers.Main) {
                            onLocationUpdate(location)
                        }

                        // Update Firebase with throttled frequency
                        updateDriverLocationInFirebase(location)

                        lastLocation = location
                        lastUpdateTime = System.currentTimeMillis()
                    }

                    delay(getUpdateInterval())

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                    delay(5000) // Wait 5 seconds on error
                }
            }
        }
    }

    fun stopLocationUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    private suspend fun getCurrentLocation(): Point? = withContext(Dispatchers.IO) {
        try {
            val locationUtils = LocationUtils(context)
            if (locationUtils.hasLocationPermissions()) {
                locationUtils.getCurrentLocation()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun shouldUpdateLocation(newLocation: Point): Boolean {
        // Validate coordinates first
        if (!isValidCoordinates(newLocation)) {
            android.util.Log.w("LocationUpdateManager", "Invalid coordinates: ${newLocation.latitude()}, ${newLocation.longitude()}")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastUpdateTime

        // Always update if it's been too long
        if (timeSinceLastUpdate > MAX_UPDATE_INTERVAL_MS) {
            return true
        }

        // Check time threshold
        if (timeSinceLastUpdate < getUpdateInterval()) {
            return false
        }

        // Check distance threshold
        lastLocation?.let { last ->
            val distance = calculateDistance(last, newLocation)
            return distance > MIN_DISTANCE_METERS
        }

        return true
    }

    private fun isValidCoordinates(point: Point): Boolean {
        return point.latitude() in -90.0..90.0 && point.longitude() in -180.0..180.0
    }

    private fun getUpdateInterval(): Long {
        return if (isActiveRide) ACTIVE_RIDE_UPDATE_INTERVAL_MS else MIN_UPDATE_INTERVAL_MS
    }

    private fun updateDriverLocationInFirebase(location: Point) {
        coroutineScope.launch {
            try {
                driverRepository.updateDriverLocation(
                    latitude = location.latitude(),
                    longitude = location.longitude()
                )
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("LocationUpdateManager", "Failed to update Firebase location", e)
            }
        }
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
     * Get smoothed location for route calculation to reduce API calls
     */
    fun getSmoothedLocation(currentLocation: Point): Point {
        // Validate current location first
        if (!isValidCoordinates(currentLocation)) {
            android.util.Log.w("OptimizedLocationUpdateManager", "Invalid current location: ${currentLocation.latitude()}, ${currentLocation.longitude()}")
            return currentLocation
        }

        locationHistory.add(currentLocation)
        if (locationHistory.size > maxHistorySize) {
            locationHistory.removeAt(0)
        }

        return if (locationHistory.size >= 2) {
            // Calculate weighted average for smoother location
            // FIX: totalWeight should be sum of (1+2+3+...+n), not just n
            val totalWeight = (locationHistory.size * (locationHistory.size + 1)) / 2.0
            val avgLat = locationHistory.mapIndexed { index, point -> point.latitude() * (index + 1) }.sum() / totalWeight
            val avgLng = locationHistory.mapIndexed { index, point -> point.longitude() * (index + 1) }.sum() / totalWeight

            // Validate the calculated coordinates
            val smoothedLat = avgLat.coerceIn(-90.0, 90.0)
            val smoothedLng = avgLng.coerceIn(-180.0, 180.0)

            // Log if coordinates were corrected
            if (smoothedLat != avgLat || smoothedLng != avgLng) {
                android.util.Log.w("OptimizedLocationUpdateManager", "Corrected coordinates from ($avgLat, $avgLng) to ($smoothedLat, $smoothedLng)")
            }

            Point.fromLngLat(smoothedLng, smoothedLat)
        } else {
            currentLocation
        }
    }
}