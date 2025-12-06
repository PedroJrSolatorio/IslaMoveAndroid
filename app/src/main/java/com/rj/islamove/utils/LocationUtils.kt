package com.rj.islamove.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationUtils @Inject constructor(
    private val context: Context
) {
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10000L // 10 seconds
    ).apply {
        setMinUpdateIntervalMillis(5000L) // 5 seconds
        setMaxUpdateDelayMillis(15000L) // 15 seconds
    }.build()
    
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun getLocationErrorMessage(): String {
        return when {
            !hasLocationPermissions() -> "Location permission denied. Please enable location permissions in app settings."
            !isLocationServicesEnabled() -> "Location services are disabled. Please enable location services in device settings."
            else -> "Unable to get your current location. Please try again or move to an area with better signal."
        }
    }
    
    suspend fun getCurrentLocation(): Point? {
        if (!hasLocationPermissions()) {
            return null
        }

        if (!isLocationServicesEnabled()) {
            return null
        }

        return try {
            val location = getLastKnownLocationSuspend()
            location?.let { Point.fromLngLat(it.longitude, it.latitude) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationSuspend(): Location? =
        suspendCancellableCoroutine { continuation ->
            try {
                // Double-check permissions before accessing location
                if (!hasLocationPermissions()) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val task: Task<Location> = fusedLocationClient.lastLocation
                task.addOnSuccessListener { location: Location? ->
                    continuation.resume(location)
                }.addOnFailureListener { exception ->
                    android.util.Log.w("LocationUtils", "Failed to get last known location", exception)
                    continuation.resume(null)
                }
            } catch (e: SecurityException) {
                android.util.Log.w("LocationUtils", "Security exception getting location", e)
                continuation.resume(null)
            } catch (e: Exception) {
                android.util.Log.e("LocationUtils", "Unexpected error getting location", e)
                continuation.resume(null)
            }
        }
    
    @android.annotation.SuppressLint("MissingPermission")
    fun startLocationUpdates(
        onLocationUpdate: (Point) -> Unit,
        onError: (Exception) -> Unit = {}
    ): (() -> Unit)? {
        if (!hasLocationPermissions()) {
            android.util.Log.w("LocationUtils", "Location permissions not granted")
            onError(Exception("Location permission denied. Please enable location permissions in app settings."))
            return null
        }

        if (!isLocationServicesEnabled()) {
            android.util.Log.w("LocationUtils", "Location services not enabled")
            onError(Exception("Location services are disabled. Please enable location services in device settings."))
            return null
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationUpdate(Point.fromLngLat(location.longitude, location.latitude))
//                    android.util.Log.d("LocationUtils", "Location update: ${location.latitude}, ${location.longitude}")
                } ?: run {
//                    android.util.Log.w("LocationUtils", "Location result received but location is null")
                    // Try to get last known location as fallback
                    tryFallbackLocation(onLocationUpdate, onError)
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
//                    android.util.Log.w("LocationUtils", "Location not available - trying fallback methods")
                    // Don't immediately error - try fallback location first
                    tryFallbackLocation(onLocationUpdate, onError)
                } else {
//                    android.util.Log.d("LocationUtils", "Location is available again")
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Return function to stop location updates
            return {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: SecurityException) {
            onError(e)
            return null
        }
    }
    
    private fun tryFallbackLocation(
        onLocationUpdate: (Point) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // Try to get last known location first
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
//                        android.util.Log.d("LocationUtils", "Fallback: Using last known location")
                        onLocationUpdate(Point.fromLngLat(location.longitude, location.latitude))
                    } else {
                        // Last known location not available - try alternative providers
//                        android.util.Log.w("LocationUtils", "Fallback: No last known location available")
                        tryAlternativeLocationMethods(onLocationUpdate, onError)
                    }
                }
                .addOnFailureListener { exception ->
//                    android.util.Log.e("LocationUtils", "Fallback: Last known location failed", exception)
                    tryAlternativeLocationMethods(onLocationUpdate, onError)
                }
        } catch (e: SecurityException) {
//            android.util.Log.e("LocationUtils", "Fallback: Security exception", e)
            onError(Exception("Location permission denied"))
        }
    }
    
    private fun tryAlternativeLocationMethods(
        onLocationUpdate: (Point) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // Create a single location request with different settings
            val singleLocationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, // Less power-intensive
                0L // Single location request
            ).apply {
                setMaxUpdateDelayMillis(30000L) // 30 second timeout
                setMinUpdateIntervalMillis(0L)
                setWaitForAccurateLocation(false) // Don't wait for high accuracy
            }.build()
            
            val singleLocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
//                        android.util.Log.d("LocationUtils", "Alternative method: Got location ${location.latitude}, ${location.longitude}")
                        onLocationUpdate(Point.fromLngLat(location.longitude, location.latitude))
                        // Remove this single-use callback
                        fusedLocationClient.removeLocationUpdates(this)
                    } ?: run {
//                        android.util.Log.w("LocationUtils", "Alternative method: Location result is null")
                        // If all else fails, report error after timeout
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onError(Exception("Unable to obtain location after trying all methods"))
                        }, 5000L) // 5 second delay before final error
                    }
                }
                
                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
//                        android.util.Log.w("LocationUtils", "Alternative method: Location still not available")
                        // Final fallback - report meaningful error
                        onError(Exception("GPS signal unavailable. Please ensure location services are enabled and try moving to an area with better signal"))
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(
                singleLocationRequest,
                singleLocationCallback,
                android.os.Looper.getMainLooper()
            )
            
            // Set a timeout to remove the callback if no location is received
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                fusedLocationClient.removeLocationUpdates(singleLocationCallback)
            }, 35000L) // 35 second timeout
            
        } catch (e: SecurityException) {
            onError(Exception("Location permission denied"))
        } catch (e: Exception) {
            onError(Exception("Location service unavailable: ${e.message}"))
        }
    }
    
    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        /**
         * Test if specific coordinates are within San Jose, Dinagat Islands service area
         * For debugging purposes only
         * 
         * POLICY:
         * - Users can use the app from anywhere in the world
         * - Drivers can be located anywhere in the world
         * - Pickup locations can be anywhere in the world (e.g., airport pickups)
         * - Destination locations MUST be within San Jose, Dinagat Islands service area
         */
        fun testCoordinates(latitude: Double, longitude: Double): String {
            val minLat = 9.8  // South bound
            val maxLat = 10.3 // North bound  
            val minLng = 125.3 // West bound
            val maxLng = 125.8 // East bound
            
            val latInBounds = latitude in minLat..maxLat
            val lngInBounds = longitude in minLng..maxLng
            val isWithin = latInBounds && lngInBounds
            
            return buildString {
                appendLine("=== COORDINATE TEST ===")
                appendLine("Test coordinates: $latitude, $longitude")
                appendLine("Expected bounds: Lat[$minLat-$maxLat], Lng[$minLng-$maxLng]")
                appendLine("Latitude check: $latInBounds (${latitude} in $minLat..$maxLat)")
                appendLine("Longitude check: $lngInBounds (${longitude} in $minLng..$maxLng)")
                appendLine("RESULT: ${if (isWithin) "✅ WITHIN SERVICE AREA" else "❌ OUTSIDE SERVICE AREA"}")
                appendLine("Policy: ✅ PICKUP allowed anywhere, DESTINATION ${if (isWithin) "✅ allowed" else "❌ rejected"}")
                appendLine("=====================")
            }
        }
        
        /**
         * Test booking scenario with pickup and destination coordinates
         */
        fun testBookingScenario(pickupLat: Double, pickupLng: Double, destLat: Double, destLng: Double): String {
            val minLat = 9.8
            val maxLat = 10.3
            val minLng = 125.3
            val maxLng = 125.8
            
            val destInBounds = destLat in minLat..maxLat && destLng in minLng..maxLng
            val bookingAllowed = destInBounds // Only destination needs to be in bounds
            
            return buildString {
                appendLine("=== BOOKING SCENARIO TEST ===")
                appendLine("Pickup: $pickupLat, $pickupLng (✅ Always allowed)")
                appendLine("Destination: $destLat, $destLng ${if (destInBounds) "(✅ Within service area)" else "(❌ Outside service area)"}")
                appendLine("Service bounds: Lat[$minLat-$maxLat], Lng[$minLng-$maxLng]")
                appendLine("BOOKING STATUS: ${if (bookingAllowed) "✅ ALLOWED" else "❌ REJECTED - Destination must be in San Jose, Dinagat Islands"}")
                appendLine("============================")
            }
        }
    }
}