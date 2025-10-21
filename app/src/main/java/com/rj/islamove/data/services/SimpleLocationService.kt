package com.rj.islamove.data.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.rj.islamove.utils.Point
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleLocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var fusedLocationClient: FusedLocationProviderClient? = null

    companion object {
        private const val TAG = "SimpleLocationService"
        private const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L // 1 second
        private const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5
    }

    /**
     * Get current location flow
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocationFlow(): Flow<Point> = callbackFlow {
        val locationEngine = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, DEFAULT_INTERVAL_IN_MILLISECONDS)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(DEFAULT_MAX_WAIT_TIME)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Point.fromLngLat(location.longitude, location.latitude))
                }
            }
        }

        locationEngine.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        awaitClose {
            locationEngine.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * Get last known location
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Result<Point> {
        return try {
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
}