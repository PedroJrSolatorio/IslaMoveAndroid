package com.rj.islamove.data.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.rj.islamove.utils.Point
import com.rj.islamove.data.models.BookingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassengerLocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val realtimeDatabase: FirebaseDatabase
) {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationUpdateJob: Job? = null
    private var isTrackingStarted = false
    private var currentBookingId: String? = null

    companion object {
        private const val TAG = "PassengerLocationService"
        private const val LOCATION_UPDATE_INTERVAL = 1000L // 1 second
        private const val LOCATION_FASTEST_INTERVAL = 1000L // 1 second
        private const val PASSENGER_LOCATION_PATH = "passenger_locations"
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
     * Start tracking passenger location during active booking
     */
    @SuppressLint("MissingPermission")
    suspend fun startLocationTracking(bookingId: String): Flow<Point> = callbackFlow {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            close()
            return@callbackFlow
        }

        currentBookingId = bookingId
        isTrackingStarted = true

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                setWaitForAccurateLocation(false)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        val point = Point.fromLngLat(location.longitude, location.latitude)
                        
                        // Send location to Firebase for driver to see
                        updateLocationInDatabase(bookingId, location)
                        
                        // Send location to flow
                        trySend(point)
                        
                        Log.d(TAG, "Passenger location updated: ${location.latitude}, ${location.longitude}")
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "Passenger location not available")
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            // Get initial location
            fusedLocationClient?.lastLocation?.await()?.let { location ->
                val point = Point.fromLngLat(location.longitude, location.latitude)
                updateLocationInDatabase(bookingId, location)
                trySend(point)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting passenger location tracking", e)
            close(e)
        }

        awaitClose {
            stopLocationTracking()
        }
    }

    /**
     * Stop tracking passenger location
     */
    fun stopLocationTracking() {
        if (!isTrackingStarted) return

        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }

        // Remove passenger location from database
        currentBookingId?.let { bookingId ->
            removeLocationFromDatabase(bookingId)
        }

        locationCallback = null
        fusedLocationClient = null
        isTrackingStarted = false
        currentBookingId = null

        Log.d(TAG, "Passenger location tracking stopped")
    }

    /**
     * Get passenger location for a specific booking
     */
    fun getPassengerLocationFlow(bookingId: String): Flow<Point?> = callbackFlow {
        val ref = realtimeDatabase.reference
            .child(PASSENGER_LOCATION_PATH)
            .child(bookingId)

        val listener = ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java)
                    val lng = snapshot.child("longitude").getValue(Double::class.java)
                    
                    if (lat != null && lng != null) {
                        trySend(Point.fromLngLat(lng, lat))
                    } else {
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Error getting passenger location", error.toException())
                trySend(null)
            }
        })

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Update passenger location in Firebase Realtime Database
     */
    private fun updateLocationInDatabase(bookingId: String, location: Location) {
        val userId = auth.currentUser?.uid ?: return

        val locationData = mapOf(
            "userId" to userId,
            "bookingId" to bookingId,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to System.currentTimeMillis(),
            "accuracy" to location.accuracy.toDouble()
        )

        realtimeDatabase.reference
            .child(PASSENGER_LOCATION_PATH)
            .child(bookingId)
            .setValue(locationData)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update passenger location in database", e)
            }
    }

    /**
     * Remove passenger location from database
     */
    private fun removeLocationFromDatabase(bookingId: String) {
        realtimeDatabase.reference
            .child(PASSENGER_LOCATION_PATH)
            .child(bookingId)
            .removeValue()
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove passenger location from database", e)
            }
    }

    /**
     * Get current location once
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Point? {
        if (!hasLocationPermissions()) return null

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = client.lastLocation.await()
            location?.let { Point.fromLngLat(it.longitude, it.latitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }

    /**
     * Handle booking status changes to automatically start/stop tracking
     * This is the key method to ensure passenger location is only tracked during active trips
     */
    suspend fun handleBookingStatusChange(bookingId: String?, status: BookingStatus?): Result<Unit> {
        return try {
            when (status) {
                BookingStatus.ACCEPTED, 
                BookingStatus.DRIVER_ARRIVING,
                BookingStatus.DRIVER_ARRIVED,
                BookingStatus.IN_PROGRESS -> {
                    // Start tracking when driver accepts or trip is in progress
                    if (bookingId != null && currentBookingId != bookingId) {
                        stopLocationTracking() // Stop any existing tracking
                        startLocationTrackingSync(bookingId)
                        Log.d(TAG, "Started passenger location tracking for booking: $bookingId")
                    }
                    Result.success(Unit)
                }
                
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                null -> {
                    // Stop tracking when trip ends
                    if (isTrackingStarted) {
                        stopLocationTracking()
                        Log.d(TAG, "Stopped passenger location tracking - trip ended")
                    }
                    Result.success(Unit)
                }
                
                else -> {
                    // For PENDING status, don't track location yet (privacy)
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle booking status change", e)
            Result.failure(e)
        }
    }

    /**
     * Start tracking synchronously (non-flow version for easier integration)
     */
    @SuppressLint("MissingPermission")
    suspend fun startLocationTrackingSync(bookingId: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (!hasLocationPermissions()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }

            if (isTrackingStarted && currentBookingId == bookingId) {
                return@withContext Result.success(Unit)
            }

            // Stop any existing tracking
            if (isTrackingStarted) {
                stopLocationTracking()
            }

            currentBookingId = bookingId
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                setWaitForAccurateLocation(false)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        // Send location to Firebase for driver to see
                        updateLocationInDatabase(bookingId, location)
                        Log.v(TAG, "Passenger location updated: ${location.latitude}, ${location.longitude}")
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "Passenger location not available")
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )?.await()

            // Get and send initial location
            fusedLocationClient?.lastLocation?.await()?.let { location ->
                updateLocationInDatabase(bookingId, location)
            }

            isTrackingStarted = true
            Log.d(TAG, "Started passenger location tracking for booking: $bookingId")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start passenger location tracking", e)
            Result.failure(e)
        }
    }

    /**
     * Check if currently tracking a specific booking
     */
    fun isTrackingBooking(bookingId: String): Boolean {
        return isTrackingStarted && currentBookingId == bookingId
    }

    /**
     * Get passenger location for driver (one-time fetch)
     */
    suspend fun getPassengerLocation(bookingId: String, passengerId: String): Result<Point> = withContext(Dispatchers.IO) {
        try {
            val passengerRef = realtimeDatabase.reference
                .child(PASSENGER_LOCATION_PATH)
                .child(bookingId)

            val snapshot = passengerRef.get().await()
            
            if (snapshot.exists()) {
                val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                
                // Check if location is recent (within last 2 minutes)
                val now = System.currentTimeMillis()
                val locationAge = now - timestamp
                val maxAge = 2 * 60 * 1000 // 2 minutes
                
                if (locationAge > maxAge) {
                    return@withContext Result.failure(Exception("Passenger location is stale (${locationAge / 1000}s old)"))
                }
                
                val location = Point.fromLngLat(longitude, latitude)
                Result.success(location)
            } else {
                Result.failure(Exception("Passenger location not found for booking $bookingId"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get passenger location", e)
            Result.failure(e)
        }
    }
}