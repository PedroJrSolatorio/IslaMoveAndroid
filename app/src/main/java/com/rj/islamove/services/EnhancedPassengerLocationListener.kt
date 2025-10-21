package com.rj.islamove.services

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.rj.islamove.data.repository.DriverLocation
import com.rj.islamove.utils.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Enhanced passenger location listener with improved real-time driver tracking
 */
class EnhancedPassengerLocationListener(
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val LOCATION_TIMEOUT_MS = 5000L // 5 seconds timeout (was 30s!)
        private const val MAX_STALE_LOCATION_AGE_MS = 3000L // 3 seconds (was 15s!)
    }

    private val database = FirebaseDatabase.getInstance()
    private var driverLocationListener: ValueEventListener? = null
    private var locationTimeoutJob: Job? = null
    private var driverQuery: Query? = null

    private val _driverLocation = MutableStateFlow<DriverLocation?>(null)
    val driverLocation: StateFlow<DriverLocation?> = _driverLocation

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _lastUpdateTime = MutableStateFlow<Long>(0)
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime

    enum class ConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        TIMEOUT
    }

    fun startListeningForDriverLocation(
        driverId: String,
        onLocationUpdate: (DriverLocation?) -> Unit,
        onConnectionStatusChange: (ConnectionStatus) -> Unit = {}
    ) {
        stopListening()

        _connectionStatus.value = ConnectionStatus.CONNECTING
        onConnectionStatusChange(ConnectionStatus.CONNECTING)

        // Query driver locations for real-time updates
        driverQuery = database.reference
            .child("driver_locations")
            .child(driverId)

        driverLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handleLocationUpdate(snapshot, onLocationUpdate, onConnectionStatusChange)
            }

            override fun onCancelled(error: DatabaseError) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                onConnectionStatusChange(ConnectionStatus.DISCONNECTED)
                android.util.Log.e("PassengerLocationListener", "Firebase listener cancelled: ${error.message}")
            }
        }

        driverQuery?.addValueEventListener(driverLocationListener!!)

        // Start timeout monitoring
        startLocationTimeoutMonitoring(onConnectionStatusChange)

        // Also listen to main driver location as fallback
        startMainLocationListener(driverId, onLocationUpdate)
    }

    fun stopListening() {
        driverLocationListener?.let { listener ->
            driverQuery?.removeEventListener(listener)
        }
        driverLocationListener = null
        driverQuery = null

        locationTimeoutJob?.cancel()
        locationTimeoutJob = null

        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun handleLocationUpdate(
        snapshot: DataSnapshot,
        onLocationUpdate: (DriverLocation?) -> Unit,
        onConnectionStatusChange: (ConnectionStatus) -> Unit
    ) {
        try {
            android.util.Log.d("PassengerLocationListener", "🔥 Received location update from Firebase")
            android.util.Log.d("PassengerLocationListener", "   - Snapshot exists: ${snapshot.exists()}")
            android.util.Log.d("PassengerLocationListener", "   - Snapshot value: ${snapshot.value}")

            if (!snapshot.exists()) {
                android.util.Log.w("PassengerLocationListener", "⚠️ Snapshot is empty - no location data")
                onLocationUpdate(null)
                return
            }

            val locationData = snapshot.getValue(Map::class.java)
            android.util.Log.d("PassengerLocationListener", "   - Parsed location data: $locationData")

            locationData?.let { data ->
                val driverLocation = DriverLocation(
                    driverId = data["driverId"] as? String ?: "",
                    latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                    lastUpdate = (data["lastUpdate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    heading = (data["heading"] as? Number)?.toDouble() ?: 0.0,
                    speed = (data["speed"] as? Number)?.toDouble() ?: 0.0,
                    online = (data["online"] as? Boolean) ?: true
                )

                android.util.Log.d("PassengerLocationListener", "✅ Created DriverLocation: lat=${driverLocation.latitude}, lng=${driverLocation.longitude}")

                // Only update if location is fresh
                val currentTime = System.currentTimeMillis()
                val locationAge = currentTime - driverLocation.lastUpdate
                android.util.Log.d("PassengerLocationListener", "   - Location age: ${locationAge}ms (max allowed: ${MAX_STALE_LOCATION_AGE_MS}ms)")

                if (locationAge < MAX_STALE_LOCATION_AGE_MS) {
                    _driverLocation.value = driverLocation
                    _lastUpdateTime.value = currentTime
                    onLocationUpdate(driverLocation)
                    android.util.Log.d("PassengerLocationListener", "🎯 Location update sent to passenger")

                    if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        onConnectionStatusChange(ConnectionStatus.CONNECTED)
                        android.util.Log.d("PassengerLocationListener", "🔗 Connection status changed to CONNECTED")
                    }

                    // Reset timeout monitoring
                    restartLocationTimeoutMonitoring(onConnectionStatusChange)
                } else {
                    android.util.Log.w("PassengerLocationListener", "⏰ Ignoring stale location data: ${locationAge}ms old")
                }
            } ?: run {
                android.util.Log.e("PassengerLocationListener", "❌ Failed to parse location data from snapshot")
                onLocationUpdate(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PassengerLocationListener", "💥 Error parsing location data", e)
            onLocationUpdate(null)
        }
    }

    private fun startMainLocationListener(
        driverId: String,
        onLocationUpdate: (DriverLocation?) -> Unit
    ) {
        val mainLocationRef = database.reference.child("driver_locations").child(driverId)

        mainLocationRef.get().addOnSuccessListener { snapshot ->
            val mainLocation = snapshot.getValue(DriverLocation::class.java)
            mainLocation?.let { location ->
                // Only use main location if active location is not available
                if (_driverLocation.value == null) {
                    _driverLocation.value = location
                    _lastUpdateTime.value = System.currentTimeMillis()
                    onLocationUpdate(location)
                }
            }
        }
    }

    private fun startLocationTimeoutMonitoring(onConnectionStatusChange: (ConnectionStatus) -> Unit) {
        restartLocationTimeoutMonitoring(onConnectionStatusChange)
    }

    private fun restartLocationTimeoutMonitoring(onConnectionStatusChange: (ConnectionStatus) -> Unit) {
        locationTimeoutJob?.cancel()

        locationTimeoutJob = coroutineScope.launch {
            delay(LOCATION_TIMEOUT_MS)

            if (_driverLocation.value != null) {
                val timeSinceLastUpdate = System.currentTimeMillis() - _lastUpdateTime.value
                if (timeSinceLastUpdate >= LOCATION_TIMEOUT_MS) {
                    _connectionStatus.value = ConnectionStatus.TIMEOUT
                    onConnectionStatusChange(ConnectionStatus.TIMEOUT)
                    android.util.Log.w("PassengerLocationListener", "Location update timeout: ${timeSinceLastUpdate}ms")
                }
            }
        }
    }

    /**
     * Get current connection status and location info for debugging
     */
    fun getConnectionInfo(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val lastUpdateAge = if (_lastUpdateTime.value > 0) currentTime - _lastUpdateTime.value else -1

        return mapOf(
            "connectionStatus" to _connectionStatus.value.name,
            "lastUpdateTime" to _lastUpdateTime.value,
            "lastUpdateAgeMs" to lastUpdateAge,
            "hasDriverLocation" to (_driverLocation.value != null),
            "driverId" to (_driverLocation.value?.driverId ?: "none"),
            "isListening" to (driverLocationListener != null)
        )
    }

    /**
     * Check if driver location is fresh enough for navigation
     */
    fun isLocationFresh(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastUpdateAge = currentTime - _lastUpdateTime.value
        return lastUpdateAge < MAX_STALE_LOCATION_AGE_MS && _driverLocation.value != null
    }

    /**
     * Get driver location as Point for map usage
     */
    fun getDriverLocationPoint(): Point? {
        return _driverLocation.value?.let { location ->
            Point.fromLngLat(location.longitude, location.latitude)
        }
    }
}