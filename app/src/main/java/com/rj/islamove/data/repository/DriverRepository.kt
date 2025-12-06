package com.rj.islamove.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

data class DriverLocation(
    val driverId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val online: Boolean = false,
    val vehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
    val lastUpdate: Long = System.currentTimeMillis(),
    val heading: Double = 0.0,
    val speed: Double = 0.0,
    val rating: Double = 5.0, // Driver's rating (default 5.0 for new drivers)
    val totalTrips: Int = 0 // Total number of trips completed
)

data class DriverProfile(
    val driverId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val rating: Double = 0.0,
    val totalTrips: Int = 0,
    val profileImageUrl: String = "",
    val online: Boolean = false,
    val vehicleCategory: String = VehicleCategory.STANDARD.name,
    val lastStatusUpdate: Long = System.currentTimeMillis()
)

data class VehicleInfo(
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val color: String = "",
    val plateNumber: String = "",
    val category: VehicleCategory = VehicleCategory.STANDARD
)

@Singleton
class DriverRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val DRIVERS_LOCATION_PATH = "driver_locations"
        private const val DRIVERS_COLLECTION = "drivers"
        private const val DRIVER_STATUS_PATH = "driver_status"
        private const val MIN_DISTANCE_UPDATE_METERS = 20.0 // Only update location every 20m for more real-time updates
    }

    // Store last updated location for each driver to implement 20m threshold
    private val lastUpdatedLocations = mutableMapOf<String, Pair<Double, Double>>()
    
    /**
     * Update driver's real-time location in Firebase Realtime Database
     */
    suspend fun updateDriverLocation(
        latitude: Double,
        longitude: Double,
        heading: Double = 0.0,
        speed: Double = 0.0,
        online: Boolean? = null // Allow forcing a specific online status
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val driverId = currentUser.uid

            // Check if driver has moved at least 20m from last update
            val lastLocation = lastUpdatedLocations[driverId]
            val shouldUpdate = if (lastLocation != null) {
                val distanceInKm = calculateDistance(
                    lastLocation.first, lastLocation.second,
                    latitude, longitude
                )
                val distanceInMeters = distanceInKm * 1000

                // Only update if moved at least 100m or if online status is being explicitly set
                if (distanceInMeters >= MIN_DISTANCE_UPDATE_METERS || online != null) {
                    println("DEBUG: Driver moved ${String.format("%.2f", distanceInMeters)}m - updating location")
                    true
                } else {
                    println("DEBUG: Driver moved only ${String.format("%.2f", distanceInMeters)}m - skipping update (threshold: ${MIN_DISTANCE_UPDATE_METERS}m - 20m for real-time updates)")
                    false
                }
            } else {
                // First location update for this driver
                println("DEBUG: First location update for driver $driverId")
                true
            }

            if (!shouldUpdate && online == null) {
                // Don't update location, but return success
                return Result.success(Unit)
            }

            val currentTime = System.currentTimeMillis()
            val locationUpdate = mutableMapOf<String, Any>(
                "latitude" to latitude,
                "longitude" to longitude,
                "heading" to heading,
                "speed" to speed,
                "lastUpdate" to currentTime
            )

            // Note: We no longer store 'online' status in driver_locations
            // Online status is only managed in driver_status node to avoid redundancy

            println("DEBUG: Updating driver $driverId location: lat=$latitude, lng=$longitude, time=$currentTime")

            database.reference
                .child(DRIVERS_LOCATION_PATH)
                .child(driverId)
                .updateChildren(locationUpdate)
                .await()

            // Store this location as the last updated location
            lastUpdatedLocations[driverId] = Pair(latitude, longitude)

            // IMPORTANT: Always update lastUpdate in driver_status to keep status fresh
            // This prevents drivers from being marked as "stale" when they're actively moving
            database.reference
                .child(DRIVER_STATUS_PATH)
                .child(driverId)
                .child("lastUpdate")
                .setValue(currentTime)
                .await()

            // If online status is explicitly provided, update it in driver_status
            if (online != null) {
                println("DEBUG: Also updating driver online status to: $online")
                database.reference
                    .child(DRIVER_STATUS_PATH)
                    .child(driverId)
                    .child("online")
                    .setValue(online)
                    .await()
            }

            println("DEBUG: Driver location update successful")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Driver location update failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Set driver online/offline status
     */
    suspend fun updateDriverStatus(
        online: Boolean,
        vehicleCategory: VehicleCategory = VehicleCategory.STANDARD
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val driverId = currentUser.uid
            
            val statusUpdate = mapOf(
                "online" to online,
                "vehicleCategory" to vehicleCategory.name,
                "lastUpdate" to System.currentTimeMillis()
            )
            
            // Update in Realtime Database for real-time queries
            database.reference
                .child(DRIVER_STATUS_PATH)
                .child(driverId)
                .updateChildren(statusUpdate)
                .await()
            
            // Update location node with vehicle category (but NOT online status)
            val locationStatusUpdate = mapOf(
                "vehicleCategory" to vehicleCategory.name,
                "lastUpdate" to System.currentTimeMillis()
            )
            
            database.reference
                .child(DRIVERS_LOCATION_PATH)
                .child(driverId)
                .updateChildren(locationStatusUpdate)
                .await()
            
            // CLEANUP: Remove redundant online/isOnline fields from driver_locations
            try {
                val locationRef = database.reference
                    .child(DRIVERS_LOCATION_PATH)
                    .child(driverId)
                
                locationRef.child("online").removeValue().await()
                locationRef.child("isOnline").removeValue().await()
                println("DEBUG: Successfully removed redundant online fields from driver_locations")
            } catch (e: Exception) {
                println("DEBUG: Could not remove redundant online fields (might not exist): ${e.message}")
            }
            
            // IMPORTANT: Also update driver's online status in Firestore drivers collection
            val firestoreUpdate = mapOf(
                "online" to online,
                "vehicleCategory" to vehicleCategory.name,
                "lastStatusUpdate" to System.currentTimeMillis()
            )
            
            firestore.collection(DRIVERS_COLLECTION)
                .document(driverId)
                .update(firestoreUpdate)
                .await()
            
            // CRITICAL: Also update the users collection to sync driver online status
            val userUpdate = mapOf(
                "driverData.online" to online,
                "driverData.vehicleCategory" to vehicleCategory.name,
                "updatedAt" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(driverId)
                .update(userUpdate)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get driver's current online status from Firebase
     */
    suspend fun getDriverOnlineStatus(driverId: String): Result<Boolean> {
        return try {
            val statusSnapshot = database.reference
                .child(DRIVER_STATUS_PATH)
                .child(driverId)
                .get()
                .await()

            val isOnline = statusSnapshot.child("online").getValue(Boolean::class.java) ?: false
//            android.util.Log.d("DriverRepository", "Retrieved online status for $driverId: $isOnline")

            Result.success(isOnline)
        } catch (e: Exception) {
//            android.util.Log.e("DriverRepository", "Failed to get online status for $driverId", e)
            Result.failure(e)
        }
    }

    /**
     * Cleanup stale online drivers - called on app startup and periodically
     * Automatically sets drivers offline if they haven't been seen for more than 1 minute
     */
    suspend fun cleanupStaleOnlineDrivers(): Result<Unit> {
        return try {
//            android.util.Log.d("DriverRepository", "Starting cleanup of stale online drivers...")

            val currentTime = System.currentTimeMillis()
            val timeoutThreshold = currentTime - 60000L // 1 minute ago (reduced from 3 minutes)

            // Get all drivers marked as online
            val statusSnapshot = database.reference
                .child(DRIVER_STATUS_PATH)
                .orderByChild("online")
                .equalTo(true)
                .get()
                .await()

            var cleanupCount = 0
            var checkedCount = 0

            for (driverSnapshot in statusSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue
                checkedCount++

                val lastUpdate = driverSnapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                val timeSinceUpdate = currentTime - lastUpdate

//                android.util.Log.d("DriverRepository", "Checking driver $driverId: lastUpdate=$lastUpdate, timeSince=${timeSinceUpdate}ms")

                // Skip current user
                if (driverId == auth.currentUser?.uid) {
//                    android.util.Log.d("DriverRepository", "Skipping current user: $driverId")
                    continue
                }

                if (lastUpdate < timeoutThreshold) {
//                    android.util.Log.w("DriverRepository", "Setting stale driver offline: $driverId (inactive for ${timeSinceUpdate}ms / ${timeSinceUpdate/1000}s)")

                    // Set driver offline
                    val statusUpdate = mapOf(
                        "online" to false,
                        "lastUpdate" to currentTime,
                        "autoOfflineReason" to "timeout_cleanup",
                        "timeoutDuration" to timeSinceUpdate
                    )

                    // Update all relevant locations
                    database.reference
                        .child(DRIVER_STATUS_PATH)
                        .child(driverId)
                        .updateChildren(statusUpdate)
                        .await()

                    // Update Firestore as well
                    try {
                        firestore.collection(DRIVERS_COLLECTION)
                            .document(driverId)
                            .update(mapOf(
                                "online" to false,
                                "lastStatusUpdate" to currentTime
                            ))
                            .await()

                        firestore.collection("users")
                            .document(driverId)
                            .update(mapOf(
                                "driverData.online" to false,
                                "updatedAt" to currentTime
                            ))
                            .await()

//                        android.util.Log.d("DriverRepository", "Successfully set driver $driverId offline in all databases")
                    } catch (e: Exception) {
                        android.util.Log.w("DriverRepository", "Failed to update Firestore for stale driver: $driverId", e)
                    }

                    cleanupCount++
                } else {
//                    android.util.Log.d("DriverRepository", "Driver $driverId is still active (${timeSinceUpdate/1000}s ago)")
                }
            }

            android.util.Log.d("DriverRepository", "🏁 Cleanup completed. Checked $checkedCount drivers, set $cleanupCount stale drivers offline.")
            Result.success(Unit)

        } catch (e: Exception) {
//            android.util.Log.e("DriverRepository", "Failed to cleanup stale online drivers", e)
            Result.failure(e)
        }
    }

    /**
     * Get nearby online drivers within specified radius
     */
    /**
     * FR-3.2.1: Get nearby drivers using enhanced distance calculation
     * (GeoFire can be added later with proper version compatibility)
     * FIXED: Now properly checks online status from driver_status node
     */
    suspend fun getNearbyDrivers(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double = 10.0
    ): Result<List<DriverLocation>> {
        return try {
            // Don't check pickup location boundaries here - focus on finding drivers within service boundaries
            // The pickup location validation should be done at the booking level, not driver fetching level

            // First, get online driver IDs from driver_status
            val statusSnapshot = database.reference
                .child(DRIVER_STATUS_PATH)
                .get()
                .await()

            val onlineDriverIds = mutableSetOf<String>()
            val maxStatusAge = 5 * 60 * 1000L // 5 minutes - status must be fresh
            val currentTime = System.currentTimeMillis()

            statusSnapshot.children.forEach { driverSnapshot ->
                val driverId = driverSnapshot.key
                val isOnline = driverSnapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastStatusUpdate = driverSnapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                val statusAge = currentTime - lastStatusUpdate

                if (isOnline && driverId != null) {
                    if (statusAge <= maxStatusAge) {
                        onlineDriverIds.add(driverId)
//                        android.util.Log.d("DriverRepository", "Driver $driverId is online with fresh status (${statusAge}ms ago)")
                    } else {
//                        android.util.Log.d("DriverRepository", "Driver $driverId has stale online status (${statusAge}ms ago), ignoring")
                    }
                }
            }

            if (onlineDriverIds.isEmpty()) {
                return Result.success(emptyList())
            }

            // Now get location data for online drivers
            val locationSnapshot = database.reference
                .child(DRIVERS_LOCATION_PATH)
                .get()
                .await()

            val nearbyDrivers = mutableListOf<DriverLocation>()

            // Fetch driver ratings from Firestore for all online drivers
            val driverRatings = mutableMapOf<String, Pair<Double, Int>>() // Map of driverId to (rating, totalTrips)
            onlineDriverIds.forEach { driverId ->
                try {
                    val driverDoc = firestore.collection("users")
                        .document(driverId)
                        .get()
                        .await()

                    val rating = driverDoc.getDouble("driverData.rating") ?: 5.0
                    val totalTrips = driverDoc.getLong("driverData.totalTrips")?.toInt() ?: 0
                    driverRatings[driverId] = Pair(rating, totalTrips)
                } catch (e: Exception) {
//                    android.util.Log.w("DriverRepository", "Could not fetch rating for driver $driverId, using default", e)
                    driverRatings[driverId] = Pair(5.0, 0)
                }
            }

            locationSnapshot.children.forEach { driverSnapshot ->
                val driverId = driverSnapshot.key ?: return@forEach

                // Only process if driver is online
                if (onlineDriverIds.contains(driverId)) {
                    val driverLocation = driverSnapshot.getValue(DriverLocation::class.java)
                    if (driverLocation != null) {
                        // Check if driver is within operational boundaries
                        val isDriverInBoundaries = isPointWithinBoundaries(driverLocation.latitude, driverLocation.longitude)
                        if (!isDriverInBoundaries) {
//                            android.util.Log.d("DriverRepository", "Driver $driverId is outside operational boundaries")
                            return@forEach
                        }

                        val distance = calculateDistance(
                            centerLat, centerLng,
                            driverLocation.latitude, driverLocation.longitude
                        )

                        if (distance <= radiusKm) {
                            // Check if location is recent (within 15 minutes) or show driver with warning if older
                            val fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000)
                            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                            if (driverLocation.lastUpdate > fifteenMinutesAgo || driverLocation.lastUpdate > oneHourAgo) {
                                // Get rating and totalTrips from the fetched data
                                val (rating, totalTrips) = driverRatings[driverId] ?: Pair(5.0, 0)

                                nearbyDrivers.add(driverLocation.copy(
                                    driverId = driverId,
                                    online = true, // Explicitly set to true since we know they're online
                                    rating = rating,
                                    totalTrips = totalTrips
                                ))
                            }
                        }
                    }
                }
            }

            // Multi-level sorting for fair driver assignment:
            // 1. Rating (highest first) - quality
            // 2. Distance to pickup (closest first) - passenger benefit (faster pickup)
            // 3. Total trips (fewest first) - fairness (distribute rides to less busy drivers)
            nearbyDrivers.sortWith(
                compareByDescending<DriverLocation> { it.rating }
                    .thenBy { calculateDistance(centerLat, centerLng, it.latitude, it.longitude) }
                    .thenBy { it.totalTrips }
            )

//            android.util.Log.d("DriverRepository", "Found ${nearbyDrivers.size} drivers within boundaries and ${radiusKm}km radius, sorted by rating→distance→trips")
            nearbyDrivers.forEach { driver ->
                val distance = calculateDistance(centerLat, centerLng, driver.latitude, driver.longitude)
//                android.util.Log.d("DriverRepository", "Driver ${driver.driverId}: rating=${driver.rating}, distance=${String.format("%.2f", distance)}km, trips=${driver.totalTrips}")
            }
            Result.success(nearbyDrivers)
        } catch (e: Exception) {
//            android.util.Log.e("DriverRepository", "getNearbyDrivers failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create or update driver profile in Firestore
     */
    suspend fun createDriverProfile(
        name: String = "Driver User",
        phoneNumber: String = "",
        vehicleInfo: VehicleInfo = VehicleInfo()
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val driverId = currentUser.uid
            
            val profile = DriverProfile(
                driverId = driverId,
                name = name,
                phoneNumber = phoneNumber,
                vehicleInfo = vehicleInfo,
                rating = 5.0,
                totalTrips = 0,
                profileImageUrl = ""
            )
            
            firestore.collection(DRIVERS_COLLECTION)
                .document(driverId)
                .set(profile)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get driver profile information
     */
    suspend fun getDriverProfile(driverId: String): Result<DriverProfile> {
        return try {
            val docSnapshot = firestore.collection(DRIVERS_COLLECTION)
                .document(driverId)
                .get()
                .await()
            
            if (docSnapshot.exists()) {
                val profile = docSnapshot.toObject(DriverProfile::class.java)
                if (profile != null) {
                    Result.success(profile.copy(driverId = driverId))
                } else {
                    Result.failure(Exception("Failed to parse driver profile"))
                }
            } else {
                // If profile doesn't exist, create a default one
                createDriverProfile()
                    .onSuccess {
                        // Return the newly created profile
                        val defaultProfile = DriverProfile(
                            driverId = driverId,
                            name = "Driver User",
                            rating = 5.0
                        )
                        return Result.success(defaultProfile)
                    }
                    .onFailure { 
                        return Result.failure(Exception("Driver profile not found and could not create one"))
                    }
                
                Result.failure(Exception("Driver profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Listen to driver location updates in real-time
     */
    fun observeDriverLocation(driverId: String): Flow<DriverLocation?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val driverLocation = snapshot.getValue(DriverLocation::class.java)
                val currentTime = System.currentTimeMillis()
                val locationAge = if (driverLocation != null) currentTime - driverLocation.lastUpdate else Long.MAX_VALUE

                // Log location age for debugging
//                android.util.Log.d("DriverRepository", "Received driver location for $driverId: lat=${driverLocation?.latitude}, lng=${driverLocation?.longitude}, age=${locationAge}ms")

                // Only send if location is recent (within 30 seconds) to avoid stale data
                if (driverLocation != null && locationAge < 30000) {
                    trySend(driverLocation.copy(driverId = driverId))
                } else if (driverLocation != null) {
//                    android.util.Log.w("DriverRepository", "Ignoring stale driver location for $driverId (age: ${locationAge}ms)")
                } else {
//                    android.util.Log.w("DriverRepository", "Received null driver location for $driverId")
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
//                android.util.Log.e("DriverRepository", "Driver location listener cancelled for $driverId: ${error.message}")
                trySend(null)
            }
        }

        val ref = database.reference.child(DRIVERS_LOCATION_PATH).child(driverId)
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLngRad = Math.toRadians(lng2 - lng1)

        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLngRad / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Check if a point is within any operational boundary
     */
    private suspend fun isPointWithinBoundaries(latitude: Double, longitude: Double): Boolean {
        return try {
//            android.util.Log.d("DriverRepository", "Checking if point ($latitude, $longitude) is within boundaries")
            // Get all service areas with boundaries from Firestore
            val serviceAreasSnapshot = firestore.collection("service_areas")
                .whereEqualTo("isActive", true)
                .get()
                .await()

//            android.util.Log.d("DriverRepository", "Found ${serviceAreasSnapshot.documents.size} active service areas")

            var foundServiceBoundary = false
            for (areaDoc in serviceAreasSnapshot.documents) {
                val area = areaDoc.toObject(ServiceArea::class.java)
//                android.util.Log.d("DriverRepository", "Checking area: ${area?.name}, has boundary: ${area?.boundary != null}, points: ${area?.boundary?.points?.size ?: 0}")

                // Check for service area boundaries (not zone boundaries)
                // Use same logic as other repositories - check if name contains "ZONE" in uppercase
                if (area != null && !area.name.uppercase().contains("ZONE")) {
                    area.boundary?.let { boundary ->
                        if (boundary.points.isNotEmpty()) {
                            foundServiceBoundary = true
                            val isInside = isPointInPolygon(
                                latitude, longitude,
                                boundary.points.map { Point.fromLngLat(it.longitude, it.latitude) }
                            )
                            android.util.Log.d("DriverRepository", "Point is ${if (isInside) "INSIDE" else "OUTSIDE"} service boundary: ${area.name}")
                            if (isInside) {
                                return true
                            }
                        }
                    }
                }
            }

            // If no service area boundaries are defined, allow all locations
            if (!foundServiceBoundary) {
//                android.util.Log.d("DriverRepository", "No service boundaries found, allowing all locations")
            } else {
//                android.util.Log.d("DriverRepository", "Point is outside all service boundaries")
            }
            !foundServiceBoundary // Only allow if no boundaries defined
        } catch (e: Exception) {
//            android.util.Log.e("DriverRepository", "Error checking boundaries", e)
            true // Default to allowing if boundary check fails
        }
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     */
    private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Point>): Boolean {
        if (polygon.size < 3) return false

        var intersections = 0
        val n = polygon.size

        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]

            // Check if the point is on an edge
            if (isPointOnLineSegment(lat, lng, p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude())) {
                return true
            }

            // Check for ray casting intersection
            if (((p1.latitude() <= lat && lat < p2.latitude()) || (p2.latitude() <= lat && lat < p1.latitude())) &&
                (lng < (p2.longitude() - p1.longitude()) * (lat - p1.latitude()) / (p2.latitude() - p1.latitude()) + p1.longitude())
            ) {
                intersections++
            }
        }

        return intersections % 2 == 1
    }

    /**
     * Check if a point is on a line segment (with small tolerance)
     */
    private fun isPointOnLineSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val tolerance = 0.000001 // Very small tolerance for floating point comparison
        val crossProduct = (py - y1) * (x2 - x1) - (px - x1) * (y2 - y1)

        if (abs(crossProduct) > tolerance) {
            return false
        }

        val dotProduct = (px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)
        val segmentLengthSquared = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)

        if (dotProduct < -tolerance || dotProduct > segmentLengthSquared + tolerance) {
            return false
        }

        return true
    }
    
    /**
     * Observe all online drivers in real-time for passenger map view
     * Uses driver_status for online status and driver_locations for location data
     */
    fun observeOnlineDrivers(): Flow<List<DriverLocation>> = callbackFlow {
        println("DEBUG: Starting to observe online drivers within boundaries")

        val statusListener = object : ValueEventListener {
            override fun onDataChange(statusSnapshot: DataSnapshot) {
                println("DEBUG: Driver status changed, processing...")

                // Get list of online driver IDs - ONLY drivers explicitly marked online with recent status
                val onlineDriverIds = mutableListOf<String>()
                val maxStatusAge = 5 * 60 * 1000L // 5 minutes - status must be fresh
                val currentTime = System.currentTimeMillis()

                statusSnapshot.children.forEach { driverSnapshot ->
                    val driverId = driverSnapshot.key
                    val isOnline = driverSnapshot.child("online").getValue(Boolean::class.java)
                    val lastStatusUpdate = driverSnapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                    val statusAge = currentTime - lastStatusUpdate

                    // STRICT CHECK: Only include if explicitly true AND status is recent
                    if (isOnline == true && driverId != null) {
                        if (statusAge <= maxStatusAge) {
                            onlineDriverIds.add(driverId)
//                            println("DEBUG: Driver $driverId is online (last status update: ${statusAge}ms ago)")
                        } else {
//                            println("DEBUG: Driver $driverId has stale online status (${statusAge}ms ago), ignoring")
                        }
                    } else if (driverId != null) {
//                        println("DEBUG: Driver $driverId is offline (online: $isOnline)")
                    }
                }

//                println("DEBUG: Found ${onlineDriverIds.size} online drivers")

                // ALWAYS send result, even if empty, to ensure UI updates
                if (onlineDriverIds.isEmpty()) {
//                    println("DEBUG: No online drivers, sending empty list")
                    trySend(emptyList())
                    return
                }

                // Now get location data for all online drivers
                database.reference.child(DRIVERS_LOCATION_PATH).get()
                    .addOnSuccessListener { locationSnapshot ->
                        val onlineDrivers = mutableListOf<DriverLocation>()

                        onlineDriverIds.forEach { driverId ->
                            val driverLocationSnapshot = locationSnapshot.child(driverId)
                            val driverLocation = driverLocationSnapshot.getValue(DriverLocation::class.java)

                            if (driverLocation != null) {
                                // Check if location is recent (within 5 minutes)
                                // Note: Boundary filtering is done in getNearbyDrivers function
                                val fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000)
                                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                                if (driverLocation.lastUpdate > fifteenMinutesAgo) {
                                    onlineDrivers.add(driverLocation.copy(
                                        driverId = driverId,
                                        online = true
                                    ))
                                    println("DEBUG: Added driver $driverId with recent location within boundaries")
                                } else if (driverLocation.lastUpdate > oneHourAgo) {
                                    // Driver has older location but still within reasonable time, add them anyway
                                    onlineDrivers.add(driverLocation.copy(
                                        driverId = driverId,
                                        online = true
                                    ))
                                    println("DEBUG: Added driver $driverId with older location within boundaries (${System.currentTimeMillis() - driverLocation.lastUpdate}ms ago)")
                                } else {
                                    println("DEBUG: Driver $driverId has very old location (${System.currentTimeMillis() - driverLocation.lastUpdate}ms ago), excluding")
                                }
                            } else {
                                println("DEBUG: No location data for online driver $driverId")
                            }
                        }

                        println("DEBUG: Sending ${onlineDrivers.size} online drivers within boundaries to passenger")
                        trySend(onlineDrivers)
                    }
                    .addOnFailureListener { error ->
                        println("DEBUG: Failed to get driver locations: ${error.message}")
                        trySend(emptyList())
                    }
            }
            
            override fun onCancelled(error: DatabaseError) {
                println("DEBUG: Status listener cancelled: ${error.message}")
                trySend(emptyList())
            }
        }
        
        val statusRef = database.reference.child(DRIVER_STATUS_PATH)
        statusRef.addValueEventListener(statusListener)
        
        awaitClose {
            statusRef.removeEventListener(statusListener)
        }
    }
    
    /**
     * Get online drivers in a specific area with radius
     * Uses driver_status for online status and driver_locations for location data
     */
    fun observeOnlineDriversInArea(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double = 10.0
    ): Flow<List<DriverLocation>> = callbackFlow {
        println("DEBUG AREA: Starting to observe drivers in area (${centerLat}, ${centerLng}) within ${radiusKm}km")
        
        val statusListener = object : ValueEventListener {
            override fun onDataChange(statusSnapshot: DataSnapshot) {
                println("DEBUG AREA: Driver status changed, processing for area query...")

                // Get list of online driver IDs with recent status
                val onlineDriverIds = mutableListOf<String>()
                val maxStatusAge = 5 * 60 * 1000L // 5 minutes - status must be fresh
                val currentTime = System.currentTimeMillis()

                statusSnapshot.children.forEach { driverSnapshot ->
                    val driverId = driverSnapshot.key
                    val isOnline = driverSnapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastStatusUpdate = driverSnapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                    val statusAge = currentTime - lastStatusUpdate

                    if (isOnline && driverId != null) {
                        if (statusAge <= maxStatusAge) {
                            onlineDriverIds.add(driverId)
                            println("DEBUG AREA: Driver $driverId is online (status age: ${statusAge}ms)")
                        } else {
                            println("DEBUG AREA: Driver $driverId has stale online status (${statusAge}ms ago), ignoring")
                        }
                    }
                }
                
                println("DEBUG AREA: Found ${onlineDriverIds.size} online drivers total")
                
                if (onlineDriverIds.isEmpty()) {
                    println("DEBUG AREA: No online drivers, sending empty list")
                    trySend(emptyList())
                    return
                }
                
                // Get location data for online drivers in the area
                database.reference.child(DRIVERS_LOCATION_PATH).get()
                    .addOnSuccessListener { locationSnapshot ->
                        val nearbyDrivers = mutableListOf<DriverLocation>()
                        
                        onlineDriverIds.forEach { driverId ->
                            val driverLocationSnapshot = locationSnapshot.child(driverId)
                            val driverLocation = driverLocationSnapshot.getValue(DriverLocation::class.java)
                            
                            if (driverLocation != null) {
                                val distance = calculateDistance(
                                    centerLat, centerLng,
                                    driverLocation.latitude, driverLocation.longitude
                                )
                                
                                println("DEBUG AREA: Driver $driverId distance: ${distance}km (limit: ${radiusKm}km)")
                                
                                if (distance <= radiusKm) {
                                    // Check if location is recent (within 15 minutes) or show driver with warning if older
                                    val fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000)
                                    val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                                    val locationAge = System.currentTimeMillis() - driverLocation.lastUpdate
                                    println("DEBUG AREA: Driver $driverId location age: ${locationAge}ms (limit: 900000ms)")

                                    if (driverLocation.lastUpdate > fifteenMinutesAgo) {
                                        nearbyDrivers.add(driverLocation.copy(
                                            driverId = driverId,
                                            online = true
                                        ))
                                        println("DEBUG AREA: Added driver $driverId to nearby drivers")
                                    } else if (driverLocation.lastUpdate > oneHourAgo) {
                                        // Driver has older location but still within reasonable time, add them anyway
                                        nearbyDrivers.add(driverLocation.copy(
                                            driverId = driverId,
                                            online = true
                                        ))
                                        println("DEBUG AREA: Added driver $driverId with older location (${locationAge}ms ago)")
                                    } else {
                                        println("DEBUG AREA: Driver $driverId has very old location (${locationAge}ms ago), excluding")
                                    }
                                } else {
                                    println("DEBUG AREA: Driver $driverId is too far (${distance}km > ${radiusKm}km)")
                                }
                            } else {
                                println("DEBUG AREA: No location data for online driver $driverId")
                            }
                        }
                        
                        // Sort by distance
                        nearbyDrivers.sortBy { driver ->
                            calculateDistance(centerLat, centerLng, driver.latitude, driver.longitude)
                        }
                        
                        println("DEBUG AREA: Sending ${nearbyDrivers.size} nearby drivers to passenger")
                        trySend(nearbyDrivers)
                    }
                    .addOnFailureListener { error ->
                        println("DEBUG AREA: Failed to get driver locations: ${error.message}")
                        trySend(emptyList())
                    }
            }
            
            override fun onCancelled(error: DatabaseError) {
                println("DEBUG AREA: Status listener cancelled: ${error.message}")
                trySend(emptyList())
            }
        }
        
        val statusRef = database.reference.child(DRIVER_STATUS_PATH)
        statusRef.addValueEventListener(statusListener)
        
        awaitClose {
            statusRef.removeEventListener(statusListener)
        }
    }

    /**
     * Force refresh online drivers list - useful when drivers aren't detected
     */
    fun forceRefreshOnlineDrivers() {
        println("DEBUG: Force refreshing online drivers")
        // This will trigger the listeners to fire again
        database.reference.child(DRIVER_STATUS_PATH).get()
            .addOnSuccessListener {
                println("DEBUG: Force refresh triggered status check")
            }
            .addOnFailureListener { e ->
                println("DEBUG: Force refresh failed: ${e.message}")
            }
    }
}