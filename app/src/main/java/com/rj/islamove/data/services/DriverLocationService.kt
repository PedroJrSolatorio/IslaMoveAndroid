package com.rj.islamove.data.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.rj.islamove.utils.Point
import com.rj.islamove.data.repository.DriverRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverLocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val realtimeDatabase: FirebaseDatabase,
    private val driverRepository: DriverRepository, // Inject DriverRepository
    private val serviceAreaRepository: com.rj.islamove.data.repository.ServiceAreaManagementRepository,
    private val sanJoseLocationRepository: com.rj.islamove.data.repository.SanJoseLocationRepository
) {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationUpdateJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isTrackingStarted = false
    private var online: Boolean = false

    companion object {
        private const val TAG = "DriverLocationService"
        private const val LOCATION_UPDATE_INTERVAL = 500L // 500ms for faster real-time tracking
        private const val LOCATION_FASTEST_INTERVAL = 500L // 500ms minimum interval
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds heartbeat
        private const val OFFLINE_TIMEOUT = 180000L // 3 minutes = 180 seconds
        private const val DRIVERS_LOCATION_PATH = "driver_locations"
        private const val DRIVER_STATUS_PATH = "driver_status"
        private const val DRIVER_PRESENCE_PATH = "driver_presence"
    }

    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start tracking driver location and updating Firebase Realtime Database every 3 seconds
     */
    suspend fun startLocationTracking(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.failure(Exception("User not authenticated"))
            }

            if (!hasLocationPermissions()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }

            if (isTrackingStarted) {
                return@withContext Result.success(Unit)
            }

            // Initialize location client
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // Create location request optimized for real-time tracking
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(0) // No batching - send updates immediately
                .setMinUpdateDistanceMeters(0f) // Update even for small movements
                .build()

            // Create location callback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        // Update location via DriverRepository
                        CoroutineScope(Dispatchers.IO).launch {
                            driverRepository.updateDriverLocation(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                heading = location.bearing.toDouble(),
                                speed = location.speed.toDouble(),
                                online = this@DriverLocationService.online // Pass the current online status
                            )
                        }
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "Location not available")
                    }
                }
            }

            // Start location updates
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )?.await()

            isTrackingStarted = true
            Log.d(TAG, "Started location tracking for driver: ${currentUser.uid}")

            Result.success(Unit)

        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking", e)
            Result.failure(e)
        }
    }


    /**
     * Stop location tracking
     */
    fun stopLocationTracking(): Result<Unit> {
        return try {
            locationCallback?.let { callback ->
                fusedLocationClient?.removeLocationUpdates(callback)
            }
            locationUpdateJob?.cancel()
            
            isTrackingStarted = false
            
            // Update driver status to offline in Firebase
            auth.currentUser?.let { user ->
                val statusRef = realtimeDatabase.reference
                    .child(DRIVER_STATUS_PATH)
                    .child(user.uid)
                
                statusRef.child("online").setValue(false)
                statusRef.child("lastSeen").setValue(System.currentTimeMillis())
            }
            
            Log.d(TAG, "Stopped location tracking")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking", e)
            Result.failure(e)
        }
    }

    /**
     * Get current location as a Flow
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocationFlow(): Flow<Point> = callbackFlow {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .setMaxUpdateDelayMillis(0) // No batching - send updates immediately
            .setMinUpdateDistanceMeters(0f) // Update even for small movements
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Point.fromLngLat(location.longitude, location.latitude))
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Set driver online status
     */
    suspend fun setDriverOnlineStatus(online: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        this@DriverLocationService.online = online // Update local online status
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.failure(Exception("User not authenticated"))
            }

            // If going online, check if driver is within service boundary
            if (online) {
                // Get current location first - try fresh location, fallback to last known
                Log.d(TAG, "Attempting to get driver location for boundary check...")
                val location = withContext(Dispatchers.Main) {
                    // Ensure fusedLocationClient is initialized
                    val locationClient = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context)

                    try {
                        Log.d(TAG, "Requesting fresh current location with high accuracy...")
                        val freshLocation = locationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            null
                        ).await()
                        if (freshLocation != null) {
                            Log.d(TAG, "Got fresh location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                        } else {
                            Log.w(TAG, "getCurrentLocation returned null")
                        }
                        freshLocation
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get current location, trying last known", e)
                        try {
                            val lastLoc = locationClient.lastLocation.await()
                            if (lastLoc != null) {
                                Log.d(TAG, "Got last known location: ${lastLoc.latitude}, ${lastLoc.longitude}")
                            } else {
                                Log.w(TAG, "lastLocation also returned null")
                            }
                            lastLoc
                        } catch (e2: Exception) {
                            Log.e(TAG, "Could not get last known location either", e2)
                            null
                        }
                    }
                }

                // Check service boundary first to see if we need location
                val serviceBoundaryResult = serviceAreaRepository.getActiveServiceBoundary()
                val serviceBoundary = serviceBoundaryResult.getOrNull()

                if (location != null) {
                    Log.d(TAG, "Driver location: ${location.latitude}, ${location.longitude}")

                    val isWithinBoundary = if (serviceBoundary != null && serviceBoundary.boundary != null) {
                        Log.d(TAG, "Checking against service boundary: ${serviceBoundary.name}")
                        val boundaryPoints = serviceBoundary.boundary.points.map {
                            com.rj.islamove.data.models.BoundaryPointData(it.latitude, it.longitude)
                        }
                        sanJoseLocationRepository.isWithinBoundary(
                            location.latitude,
                            location.longitude,
                            boundaryPoints
                        )
                    } else {
                        // Fallback to San Jose bounds if no service boundary
                        Log.d(TAG, "No service boundary, checking against San Jose bounds")
                        sanJoseLocationRepository.isWithinSanJose(location.latitude, location.longitude)
                    }

                    if (!isWithinBoundary) {
                        val areaName = serviceBoundary?.name ?: "San Jose, Dinagat Islands"
                        Log.w(TAG, "Driver cannot go online - outside service boundary: $areaName")
                        return@withContext Result.failure(Exception("You must be within $areaName to go online"))
                    }

                    Log.d(TAG, "Driver is within service boundary - allowed to go online")
                } else {
                    // If there's an active service boundary, we MUST have location to verify
                    if (serviceBoundary != null && serviceBoundary.boundary != null) {
                        Log.w(TAG, "Cannot verify location - service boundary is active but location unavailable")
                        return@withContext Result.failure(Exception("Unable to get your location. Please enable GPS and try again."))
                    }
                    // If no service boundary, allow without location check
                    Log.w(TAG, "Could not get driver location, but no service boundary defined - allowing to go online")
                }
            }

            // Use DriverRepository to update status, which now handles both RTDB and Firestore
            val result = driverRepository.updateDriverStatus(online)

            if (result.isSuccess) {
                if (online) {
                    startLocationTracking()
                    setupPresenceSystem(currentUser.uid)
                } else {
                    stopLocationTracking()
                    stopPresenceSystem(currentUser.uid)
                }
                Log.d(TAG, "Successfully updated driver status to online=$online for ${currentUser.uid}")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to update driver status for ${currentUser.uid}", result.exceptionOrNull())
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error updating status"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update driver status", e)
            Result.failure(e)
        }
    }

    /**
     * Get driver location from Firebase Realtime Database
     */
    suspend fun getDriverLocation(driverId: String): Result<Point> = withContext(Dispatchers.IO) {
        try {
            val locationRef = realtimeDatabase.reference
                .child(DRIVERS_LOCATION_PATH)
                .child(driverId)

            val snapshot = locationRef.get().await()
            
            if (snapshot.exists()) {
                val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                val location = Point.fromLngLat(longitude, latitude)
                Result.success(location)
            } else {
                Result.failure(Exception("Driver location not found"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get driver location", e)
            Result.failure(e)
        }
    }

    /**
     * Listen to driver location updates
     */
    fun observeDriverLocation(driverId: String): Flow<Point> = callbackFlow {
        val locationRef = realtimeDatabase.reference
            .child(DRIVERS_LOCATION_PATH)
            .child(driverId)

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    trySend(Point.fromLngLat(longitude, latitude))
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Failed to observe driver location", error.toException())
            }
        }

        locationRef.addValueEventListener(listener)

        awaitClose {
            locationRef.removeEventListener(listener)
        }
    }

    /**
     * Check if location tracking is currently active
     */
    fun isLocationTrackingActive(): Boolean = isTrackingStarted

    /**
     * Get last known location without starting continuous tracking
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Result<Point> = withContext(Dispatchers.IO) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedClient.lastLocation.await()
            
            if (location != null) {
                Result.success(Point.fromLngLat(location.longitude, location.latitude))
            } else {
                Result.failure(Exception("No last known location available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last known location", e)
            Result.failure(e)
        }
    }

    /**
     * Setup Firebase presence system for automatic offline detection
     * This ensures driver goes offline if app is closed/crashed for 3 minutes
     */
    private fun setupPresenceSystem(driverId: String) {
        try {
            Log.d(TAG, "Setting up presence system for driver: $driverId")

            val presenceRef = realtimeDatabase.reference
                .child(DRIVER_PRESENCE_PATH)
                .child(driverId)

            val statusRef = realtimeDatabase.reference
                .child(DRIVER_STATUS_PATH)
                .child(driverId)

            // Set up disconnect handler - this runs on Firebase servers
            presenceRef.onDisconnect().setValue(mapOf(
                "online" to false,
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "disconnectedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
            ))

            // Also set main status to offline on disconnect
            statusRef.onDisconnect().updateChildren(mapOf(
                "online" to false,
                "lastUpdate" to com.google.firebase.database.ServerValue.TIMESTAMP
            ))

            // Set initial presence
            presenceRef.setValue(mapOf(
                "online" to true,
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "connectedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
            ))

            // Start heartbeat system
            startHeartbeat(driverId)

            Log.d(TAG, "Presence system setup completed for driver: $driverId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup presence system for driver: $driverId", e)
        }
    }

    /**
     * Stop the presence system when driver goes offline
     */
    private fun stopPresenceSystem(driverId: String) {
        try {
            Log.d(TAG, "Stopping presence system for driver: $driverId")

            // Stop heartbeat
            stopHeartbeat()

            val presenceRef = realtimeDatabase.reference
                .child(DRIVER_PRESENCE_PATH)
                .child(driverId)

            // Cancel disconnect handlers
            presenceRef.onDisconnect().cancel()

            val statusRef = realtimeDatabase.reference
                .child(DRIVER_STATUS_PATH)
                .child(driverId)

            statusRef.onDisconnect().cancel()

            // Set final offline status
            presenceRef.setValue(mapOf(
                "online" to false,
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "manuallyDisconnected" to true
            ))

            Log.d(TAG, "Presence system stopped for driver: $driverId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop presence system for driver: $driverId", e)
        }
    }

    /**
     * Start heartbeat system - sends periodic updates to show driver is active
     */
    private fun startHeartbeat(driverId: String) {
        // Cancel any existing heartbeat
        stopHeartbeat()

        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && online) {
                try {
                    val currentTime = System.currentTimeMillis()

                    // Update both presence and main status with timestamp
                    val presenceRef = realtimeDatabase.reference
                        .child(DRIVER_PRESENCE_PATH)
                        .child(driverId)

                    val statusRef = realtimeDatabase.reference
                        .child(DRIVER_STATUS_PATH)
                        .child(driverId)

                    // Update presence with server timestamp
                    presenceRef.updateChildren(mapOf(
                        "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
                        "lastSeenClient" to currentTime,
                        "online" to true,
                        "heartbeat" to true
                    )).await()

                    // Also update main status timestamp
                    statusRef.updateChildren(mapOf(
                        "lastUpdate" to com.google.firebase.database.ServerValue.TIMESTAMP,
                        "lastUpdateClient" to currentTime
                    )).await()

                    Log.d(TAG, "💓 Heartbeat sent for driver: $driverId at $currentTime")

                    // Wait for next heartbeat
                    delay(HEARTBEAT_INTERVAL)

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send heartbeat for driver: $driverId", e)
                    // Continue trying in case of temporary network issues
                    delay(5000) // Wait 5 seconds before retry
                }
            }
            Log.d(TAG, "💔 Heartbeat stopped for driver: $driverId (online=$online, isActive=$isActive)")
        }
    }

    /**
     * Stop heartbeat system
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped")
    }
}