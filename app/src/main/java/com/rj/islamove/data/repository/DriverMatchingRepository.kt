package com.rj.islamove.data.repository

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging
import com.rj.islamove.R
import com.rj.islamove.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Firebase Realtime Database compatible location model
data class DatabaseLocation(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeId: String? = null,
    val placeName: String? = null,
    val placeType: String? = null
) {
    // Convert to GeoPoint for Firestore compatibility
    fun toGeoPoint() = GeoPoint(latitude, longitude)
    
    // Convert to BookingLocation
    fun toBookingLocation() = BookingLocation(
        address = address,
        coordinates = GeoPoint(latitude, longitude),
        placeId = placeId,
        placeName = placeName,
        placeType = placeType
    )
    
    companion object {
        // Convert from BookingLocation to DatabaseLocation
        fun fromBookingLocation(location: BookingLocation) = DatabaseLocation(
            address = location.address,
            latitude = location.coordinates.latitude,
            longitude = location.coordinates.longitude,
            placeId = location.placeId,
            placeName = location.placeName,
            placeType = location.placeType
        )
    }
}

// Firebase Realtime Database compatible fare estimate model
data class DatabaseFareEstimate(
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val timeFare: Double = 0.0,
    val surgeFactor: Double = 1.0,
    val totalEstimate: Double = 0.0,
    val currency: String = "PHP",
    val estimatedDuration: Int = 0,
    val estimatedDistance: Double = 0.0
) {
    // Convert to FareEstimate
    fun toFareEstimate() = FareEstimate(
        baseFare = baseFare,
        distanceFare = distanceFare,
        timeFare = timeFare,
        surgeFactor = surgeFactor,
        totalEstimate = totalEstimate,
        currency = currency,
        estimatedDuration = estimatedDuration,
        estimatedDistance = estimatedDistance
    )
    
    companion object {
        fun fromFareEstimate(estimate: FareEstimate) = DatabaseFareEstimate(
            baseFare = estimate.baseFare,
            distanceFare = estimate.distanceFare,
            timeFare = estimate.timeFare,
            surgeFactor = estimate.surgeFactor,
            totalEstimate = estimate.totalEstimate,
            currency = estimate.currency,
            estimatedDuration = estimate.estimatedDuration,
            estimatedDistance = estimate.estimatedDistance
        )
    }
}

data class DriverRequest(
    val requestId: String = "",
    val bookingId: String = "",
    val driverId: String = "",
    val passengerId: String = "",
    val pickupLocation: DatabaseLocation = DatabaseLocation(),
    val destination: DatabaseLocation = DatabaseLocation(),
    val fareEstimate: DatabaseFareEstimate = DatabaseFareEstimate(),
    val requestTime: Long = System.currentTimeMillis(),
    val expirationTime: Long = System.currentTimeMillis() + 30000, // 30 seconds initial
    val secondChanceExpirationTime: Long = System.currentTimeMillis() + 210000, // 3.5 minutes total (30s + 3min)
    val status: DriverRequestStatus = DriverRequestStatus.PENDING,
    val specialInstructions: String = "", // Passenger comment for identification
    val passengerDiscountPercentage: Int? = null // null = no discount, 20, 50, etc.
) {
    // Helper functions to determine request phase
    fun isInInitialPhase(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime < expirationTime && status == DriverRequestStatus.PENDING
    }
    
    fun isInSecondChance(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime >= expirationTime && 
               currentTime < secondChanceExpirationTime && 
               (status == DriverRequestStatus.PENDING || status == DriverRequestStatus.SECOND_CHANCE)
    }
    
    fun isFullyExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime >= secondChanceExpirationTime || 
               status == DriverRequestStatus.EXPIRED ||
               status == DriverRequestStatus.ACCEPTED_BY_OTHER ||
               status == DriverRequestStatus.CANCELLED
    }
    
    fun getTimeRemaining(currentTime: Long = System.currentTimeMillis()): Long {
        return when {
            isInInitialPhase(currentTime) -> (expirationTime - currentTime) / 1000
            isInSecondChance(currentTime) -> (secondChanceExpirationTime - currentTime) / 1000
            else -> 0
        }
    }
    
    fun getPhase(currentTime: Long = System.currentTimeMillis()): RequestPhase {
        return when {
            isInInitialPhase(currentTime) -> RequestPhase.INITIAL
            isInSecondChance(currentTime) -> RequestPhase.SECOND_CHANCE
            else -> RequestPhase.EXPIRED
        }
    }
}

enum class RequestPhase {
    INITIAL,      // First 30 seconds
    SECOND_CHANCE, // Next 3 minutes  
    EXPIRED       // Fully expired
}

enum class DriverRequestStatus {
    PENDING,
    SECOND_CHANCE,      // Moved to second chance phase
    ACCEPTED,
    DECLINED,
    EXPIRED,
    ACCEPTED_BY_OTHER,  // Another driver accepted this request
    CANCELLED
}

@Singleton
class DriverMatchingRepository @Inject constructor(
    private val context: Context,
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val driverRepository: DriverRepository,
    private val bookingRepository: BookingRepository,
    private val messaging: FirebaseMessaging,
    private val auth: FirebaseAuth,
    private val notificationService: com.rj.islamove.data.services.NotificationService,
    private val sanJoseLocationRepository: SanJoseLocationRepository,
    private val serviceAreaRepository: ServiceAreaManagementRepository,
    private val zoneBoundaryRepository: ZoneBoundaryRepository
) {
    
    companion object {
        private const val DRIVER_REQUESTS_PATH = "driver_requests"
        private const val DRIVER_RESPONSES_PATH = "driver_responses"
        private const val BOOKING_ASSIGNMENTS_PATH = "booking_assignments"
        private const val QUEUED_BOOKINGS_PATH = "queued_bookings"
        private const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds
        private const val MAX_REASSIGNMENT_ATTEMPTS = 3 // Maximum times to reassign a booking
        private const val MAX_ACCEPTED_RIDES_PER_DRIVER = 5 // Maximum number of accepted rides a driver can have
        private const val DESTINATION_PROXIMITY_THRESHOLD_KM = 0.5 // Maximum distance between destinations to be considered compatible (500m)
        private const val PICKUP_PROXIMITY_THRESHOLD_KM = 0.5 // Maximum distance from driver location to pickup when driver has passengers (500m)

        // Distance thresholds in meters
        private const val DESTINATION_PROXIMITY_THRESHOLD_METERS = 500.0 // Maximum distance between destinations to be considered compatible (500m)
        private const val PICKUP_PROXIMITY_THRESHOLD_METERS = 500.0 // Maximum distance from driver location to pickup when driver has passengers (500m)
        private const val MAX_DRIVER_TO_PICKUP_RADIUS_METERS = 500.0 // Maximum radius for drivers to receive ride requests (500m)

        // Direction filtering uses compass sectors with directional grouping:
        // - Sectors: N, NE, E, SE, S, SW, W, NW (45° each)
        // - Groups: NORTH (N, NE, NW), EAST (E, NE, SE), SOUTH (S, SE, SW), WEST (W, NW, SW)
        // - Compatible if sectors share at least one group (e.g., NW and NE both share NORTH)
    }

    /**
     * Auto-cleanup stuck bookings for a driver
     * Cancels bookings stuck in DRIVER_ARRIVING/DRIVER_ARRIVED for more than 15 minutes
     */
    suspend fun cleanupStuckBookings(driverId: String): Result<Int> {
        return try {
            android.util.Log.d("DriverMatching", "🧹 Starting stuck booking cleanup for driver $driverId...")

            val stuckStatuses = listOf(
                BookingStatus.DRIVER_ARRIVING.name,
                BookingStatus.DRIVER_ARRIVED.name
            )

            val bookingsSnapshot = firestore.collection("bookings")
                .whereEqualTo("driverId", driverId)
                .whereIn("status", stuckStatuses)
                .get()
                .await()

            val currentTime = System.currentTimeMillis()
            val timeoutThreshold = 15 * 60 * 1000L // 15 minutes
            var cleanedCount = 0

            for (doc in bookingsSnapshot.documents) {
                val bookingId = doc.id
                val status = doc.getString("status")
                val updatedAt = doc.getLong("updatedAt") ?: doc.getLong("createdAt") ?: currentTime
                val timeSinceUpdate = currentTime - updatedAt
                val minutesSinceUpdate = timeSinceUpdate / (60 * 1000)

                android.util.Log.d("DriverMatching", "   - Checking booking $bookingId: status=$status, stuckFor=${minutesSinceUpdate}min")

                // Cancel if stuck for more than 15 minutes
                if (timeSinceUpdate > timeoutThreshold) {
                    android.util.Log.w("DriverMatching", "   ⚠️  Cancelling stuck booking $bookingId (stuck for ${minutesSinceUpdate}min)")

                    firestore.collection("bookings")
                        .document(bookingId)
                        .update(
                            mapOf(
                                "status" to BookingStatus.CANCELLED.name,
                                "cancellationReason" to "Auto-cancelled: Stuck in $status for ${minutesSinceUpdate} minutes",
                                "cancelledBy" to "SYSTEM",
                                "updatedAt" to currentTime
                            )
                        )
                        .await()

                    cleanedCount++
                    android.util.Log.d("DriverMatching", "   ✅ Successfully cancelled stuck booking $bookingId")
                }
            }

            android.util.Log.d("DriverMatching", "🧹 Cleanup completed: Cancelled $cleanedCount stuck bookings for driver $driverId")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "❌ Failed to cleanup stuck bookings for driver $driverId", e)
            Result.failure(e)
        }
    }

    /**
     * Count the number of active accepted bookings for a driver
     * Active statuses: ACCEPTED, DRIVER_ARRIVING, DRIVER_ARRIVED, IN_PROGRESS
     */
    private suspend fun getDriverActiveBookingsCount(driverId: String): Int {
        return try {
            val activeStatuses = listOf(
                BookingStatus.ACCEPTED.name,
                BookingStatus.DRIVER_ARRIVING.name,
                BookingStatus.DRIVER_ARRIVED.name,
                BookingStatus.IN_PROGRESS.name
            )

            android.util.Log.d("DriverMatching", "📊 Querying active bookings for driver $driverId...")

            val bookingsSnapshot = firestore.collection("bookings")
                .whereEqualTo("driverId", driverId)
                .whereIn("status", activeStatuses)
                .get()
                .await()

            val count = bookingsSnapshot.size()
            android.util.Log.d("DriverMatching", "📊 Driver $driverId has $count active accepted bookings:")

            // Log each active booking for debugging
            bookingsSnapshot.documents.forEach { doc ->
                val status = doc.getString("status")
                val bookingId = doc.id
                android.util.Log.d("DriverMatching", "   - Booking $bookingId with status $status")
            }

            count
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "❌ Failed to get active bookings count for driver $driverId", e)
            0 // Return 0 on error to avoid blocking driver from receiving requests
        }
    }

    /**
     * FR-3.2.1 & FR-3.2.4: Find and notify nearby drivers with auto-reassignment support
     * Now filters drivers based on active zone boundaries and booking capacity (max 5 accepted rides)
     */
    suspend fun findAndNotifyDrivers(booking: Booking, attemptNumber: Int = 1): Result<List<String>> {
        return try {
            val pickupLat = booking.pickupLocation.coordinates.latitude
            val pickupLng = booking.pickupLocation.coordinates.longitude
            val destLat = booking.destination.coordinates.latitude
            val destLng = booking.destination.coordinates.longitude

            println("DEBUG MATCHING: Pickup location - Lat: $pickupLat, Lng: $pickupLng")
            println("DEBUG MATCHING: Destination location - Lat: $destLat, Lng: $destLng")

            // Get active service boundary for driver filtering
            val serviceBoundaryResult = serviceAreaRepository.getActiveServiceBoundary()
            val serviceBoundary = serviceBoundaryResult.getOrNull()

            if (serviceBoundary != null && serviceBoundary.boundary != null) {
                println("DEBUG MATCHING: Using service boundary '${serviceBoundary.name}' for driver filtering")

                // Convert boundary points to BoundaryPointData format
                val boundaryPoints = serviceBoundary.boundary.points.map {
                    com.rj.islamove.data.models.BoundaryPointData(it.latitude, it.longitude)
                }

                // Validate that destination is within the service boundary
                if (!sanJoseLocationRepository.isWithinBoundary(destLat, destLng, boundaryPoints)) {
                    println("DEBUG MATCHING: Destination outside defined service boundary")
                    return Result.failure(Exception("Destination must be within the service area: ${serviceBoundary.name}"))
                }

                println("DEBUG MATCHING: Destination is within service boundary")
            } else {
                // Fallback to hardcoded San Jose bounds if no service boundary is defined
                println("DEBUG MATCHING: No service boundary defined, using default San Jose bounds")
                if (!sanJoseLocationRepository.isWithinSanJose(destLat, destLng)) {
                    println("DEBUG MATCHING: Destination outside San Jose, Dinagat Islands")
                    return Result.failure(Exception("Destination must be within San Jose, Dinagat Islands service area"))
                }
            }

            // Get ALL available drivers using pickup location as center
            val nearbyDriversResult = driverRepository.getNearbyDrivers(
                centerLat = pickupLat,
                centerLng = pickupLng,
                radiusKm = 0.5 // 500 meters
            )
            
            if (nearbyDriversResult.isFailure) {
                return Result.failure(nearbyDriversResult.exceptionOrNull() ?: Exception("Failed to get nearby drivers"))
            }

            var nearbyDrivers = nearbyDriversResult.getOrNull() ?: emptyList()

            println("DEBUG MATCHING: Found ${nearbyDrivers.size} drivers worldwide")

            // Filter drivers by service boundary if active
            if (serviceBoundary != null && serviceBoundary.boundary != null) {
                val boundaryPoints = serviceBoundary.boundary.points.map {
                    com.rj.islamove.data.models.BoundaryPointData(it.latitude, it.longitude)
                }

                nearbyDrivers = nearbyDrivers.filter { driver ->
                    val isWithinBoundary = sanJoseLocationRepository.isWithinBoundary(
                        driver.latitude,
                        driver.longitude,
                        boundaryPoints
                    )
                    if (!isWithinBoundary) {
                        println("DEBUG MATCHING: Driver ${driver.driverId} filtered out - outside service boundary (${driver.latitude}, ${driver.longitude})")
                    }
                    isWithinBoundary
                }

                println("DEBUG MATCHING: After boundary filtering: ${nearbyDrivers.size} drivers within service area '${serviceBoundary.name}'")
            }

            nearbyDrivers.forEach { driver ->
                println("DEBUG MATCHING: Driver ${driver.driverId} - Location: (${driver.latitude}, ${driver.longitude}), Vehicle: ${driver.vehicleCategory}, Online: ${driver.online}")
            }
            println("DEBUG MATCHING: Booking vehicleCategory: ${booking.vehicleCategory}")

            // Filter drivers by vehicle category and online status
            val categoryFilteredDrivers = nearbyDrivers.filter { driver ->
                driver.vehicleCategory == booking.vehicleCategory && driver.online
            }

            println("DEBUG MATCHING: Found ${categoryFilteredDrivers.size} drivers after vehicle category filtering")

            // Only check booking capacity for the top-rated driver (since we send to one at a time)
            // This avoids unnecessary Firestore queries for all drivers
            val suitableDrivers = mutableListOf<DriverLocation>()

            for (driver in categoryFilteredDrivers) {
                val activeBookingsCount = getDriverActiveBookingsCount(driver.driverId)

                // Calculate distance to pickup location (already in meters)
                val distanceToPickupMeters = calculateDistance(
                    driver.latitude, driver.longitude,
                    pickupLat, pickupLng
                )

                println("DEBUG MATCHING: 🚗 Evaluating driver ${driver.driverId}:")
                println("DEBUG MATCHING:    - Active bookings: $activeBookingsCount/$MAX_ACCEPTED_RIDES_PER_DRIVER")
                println("DEBUG MATCHING:    - Distance to pickup: ${String.format("%.0f", distanceToPickupMeters)}m")

                // CRITICAL: Only send requests to drivers within configured radius of pickup location
                if (distanceToPickupMeters > MAX_DRIVER_TO_PICKUP_RADIUS_METERS) {
                    println("DEBUG MATCHING:    ❌ REJECTED - Driver too far from pickup (${String.format("%.0f", distanceToPickupMeters)}m > ${MAX_DRIVER_TO_PICKUP_RADIUS_METERS.toInt()}m)")
                    continue
                }

                println("DEBUG MATCHING:    ✅ Driver within ${MAX_DRIVER_TO_PICKUP_RADIUS_METERS.toInt()}m radius of pickup")

                if (activeBookingsCount < MAX_ACCEPTED_RIDES_PER_DRIVER) {
                    // Accept driver if they have capacity (no direction checking)
                    suitableDrivers.add(driver)
                    println("DEBUG MATCHING:    ✅ ACCEPTED - driver has capacity ($activeBookingsCount/$MAX_ACCEPTED_RIDES_PER_DRIVER)")
                } else {
                    println("DEBUG MATCHING:    ❌ REJECTED - at capacity ($activeBookingsCount bookings)")
                }
            }

            println("DEBUG MATCHING: Found ${suitableDrivers.size} suitable drivers after all filtering (including booking capacity)")

            // ========== BOUNDARY COMPATIBILITY PRIORITIZATION ==========
            // Prioritize drivers who already have active bookings with compatible destination boundaries
            val prioritizedDrivers = prioritizeDriversWithCompatibleRides(suitableDrivers, booking)
            println("DEBUG MATCHING: After boundary compatibility prioritization: ${prioritizedDrivers.size} drivers (${prioritizedDrivers.take(3).map { it.driverId }}...)")

            if (prioritizedDrivers.isEmpty()) {
                println("DEBUG MATCHING: No suitable drivers - vehicle category mismatch or offline drivers")

                // Queue the booking for when drivers come online (with 5-minute expiration)
                queueBookingForFutureMatching(booking)

                // Schedule booking expiration after queue timeout (5 minutes)
                // This ensures the passenger's booking doesn't stay in PENDING forever
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(300000L) // 5 minutes

                    // Check if booking is still in PENDING state (not accepted by a driver who came online)
                    val bookingCheck = bookingRepository.getBooking(booking.id).getOrNull()
                    if (bookingCheck?.status == BookingStatus.PENDING) {
                        android.util.Log.i("DriverMatching", "Queued booking ${booking.id} expired after 5 minutes with no available drivers")
                        bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                        updateBookingAssignmentStatus(booking.id, "EXPIRED", "No drivers became available within the queue timeout period")
                    }
                }

                // Provide more descriptive error message
                val serviceAreaName = serviceBoundary?.name ?: "San Jose, Dinagat Islands"
                val errorMessage = if (nearbyDrivers.isEmpty()) {
                    "No drivers are currently available in $serviceAreaName. Your ride has been queued and you'll be notified when a driver becomes available."
                } else {
                    val onlineDrivers = nearbyDrivers.count { it.online }
                    val rightCategoryDrivers = nearbyDrivers.count { it.vehicleCategory == booking.vehicleCategory && it.online }
                    when {
                        onlineDrivers == 0 -> "No drivers are currently online in $serviceAreaName. Your ride has been queued."
                        rightCategoryDrivers == 0 -> "No ${booking.vehicleCategory.displayName.lowercase()} vehicles are currently available in $serviceAreaName. Your ride has been queued."
                        else -> "No suitable drivers found in $serviceAreaName. Your ride has been queued and you'll be notified when a driver becomes available."
                    }
                }

                return Result.failure(Exception(errorMessage))
            }
            
            // Send request to highest priority driver first (prioritized by compatibility, then rating)
            val topDriver = prioritizedDrivers.firstOrNull()
            if (topDriver == null) {
                return Result.failure(Exception("No suitable drivers available"))
            }

            val notifiedDrivers = mutableListOf<String>()

            println("DEBUG MATCHING: Sending request to highest-rated driver: ${topDriver.driverId} (rating: ${topDriver.rating})")

            // Ensure booking ID is not empty or null to prevent malformed requestId
            val safeBookingId = if (booking.id.isBlank()) {
                android.util.Log.w("DriverMatching", "Booking ID is blank, using fallback ID")
                "booking_${System.currentTimeMillis()}"
            } else {
                booking.id
            }

            val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
            val driverRequest = DriverRequest(
                requestId = "${safeBookingId}_${topDriver.driverId}_${uniqueTimestamp}",
                bookingId = safeBookingId,
                driverId = topDriver.driverId,
                passengerId = booking.passengerId,
                pickupLocation = DatabaseLocation.fromBookingLocation(booking.pickupLocation),
                destination = DatabaseLocation.fromBookingLocation(booking.destination),
                fareEstimate = DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
                requestTime = System.currentTimeMillis(),
                expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                specialInstructions = booking.specialInstructions,
                passengerDiscountPercentage = booking.passengerDiscountPercentage
            )
            android.util.Log.e("DriverMatching", "==== CREATING DRIVER REQUEST ====")
            android.util.Log.e("DriverMatching", "passengerId from booking: '${booking.passengerId}'")
            android.util.Log.e("DriverMatching", "passengerId in driverRequest: '${driverRequest.passengerId}'")
            android.util.Log.e("DriverMatching", "Full driverRequest object: $driverRequest")

            // Store request in database
            println("DEBUG MATCHING: Storing request in database for driver ${topDriver.driverId}")
            database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(topDriver.driverId)
                .child(driverRequest.requestId)
                .setValue(driverRequest)
                .await()

            println("DEBUG MATCHING: Database request stored successfully for driver ${topDriver.driverId}")

            // Send real FCM notification using NotificationService
            try {
                println("DEBUG MATCHING: Attempting to send notification to driver ${topDriver.driverId}")
                // This will queue the notification for FCM processing
                sendDriverNotification(topDriver.driverId, booking)
                notifiedDrivers.add(topDriver.driverId)
                println("DEBUG MATCHING: Successfully notified driver ${topDriver.driverId}")
            } catch (e: Exception) {
                // Log error and fail if we can't notify the driver
                println("DEBUG MATCHING: Failed to notify driver ${topDriver.driverId}: ${e.message}")
                android.util.Log.w("DriverMatching", "Failed to notify driver ${topDriver.driverId}", e)
                return Result.failure(Exception("Failed to notify driver"))
            }

            // Track booking assignment attempt
            trackBookingAssignment(booking.id, attemptNumber, notifiedDrivers.size)

            println("DEBUG MATCHING: Successfully notified driver ${topDriver.driverId} (rating: ${topDriver.rating})")

            // Start monitoring for responses with auto-reassignment to next driver if declined/expired
            startRequestMonitoring(booking.id, notifiedDrivers, booking, attemptNumber, prioritizedDrivers.drop(1))

            println("DEBUG MATCHING: Driver matching completed successfully")
            Result.success(notifiedDrivers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-3.2.2: Driver accepts a ride request
     */
    suspend fun acceptRideRequest(requestId: String, driverId: String): Result<Unit> {
        return try {
            // Update request status to accepted
            database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)
                .child("status")
                .setValue(DriverRequestStatus.ACCEPTED.name)
                .await()
            
            // Get the request details
            val requestSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)
                .get()
                .await()
            
            val request = requestSnapshot.getValue(DriverRequest::class.java)
            if (request != null) {
                // Extract booking ID from request (handle case where bookingId is empty)
                val actualBookingId = if (request.bookingId.isNotBlank()) {
                    android.util.Log.d("DriverMatching", "Using bookingId from request: '${request.bookingId}'")
                    request.bookingId
                } else {
                    // Extract booking ID from requestId format: "bookingId_driverId_timestamp"
                    // Handle case where bookingId might start with underscore or be malformed
                    val requestIdParts = request.requestId.split("_")
                    val extractedId = if (requestIdParts.size >= 3) {
                        // Reconstruct bookingId by taking all parts except the last 2 (driverId and timestamp)
                        val result = requestIdParts.dropLast(2).joinToString("_")
                        
                        // Check if result is empty (malformed requestId that started with underscore)
                        if (result.isBlank()) {
                            android.util.Log.w("DriverMatching", "Malformed requestId - booking ID is empty, creating fallback")
                            "fallback_${driverId}_${System.currentTimeMillis()}"
                        } else {
                            result
                        }
                    } else {
                        // Fallback: use the requestId as-is if format doesn't match expected pattern
                        request.requestId
                    }
                    android.util.Log.w("DriverMatching", "bookingId is empty, extracted from requestId: '$extractedId' (from '${request.requestId}')")
                    extractedId
                }
                
                android.util.Log.d("DriverMatching", "Assigning driver to booking: '$actualBookingId'")
                
                // Update booking with driver assignment - ONLY when driver accepts
                bookingRepository.assignDriverToBooking(
                    bookingId = actualBookingId,
                    driverId = driverId,
                    driverLocation = BookingLocation(
                        address = "Driver Location",
                        coordinates = GeoPoint(0.0, 0.0) // This should be actual driver location
                    ),
                    estimatedArrival = 10, // Default 10 minutes, should be calculated from actual location
                    status = BookingStatus.ACCEPTED
                )
                
                // Store driver response
                val response = mapOf(
                    "requestId" to requestId,
                    "driverId" to driverId,
                    "bookingId" to actualBookingId,
                    "status" to "ACCEPTED",
                    "responseTime" to System.currentTimeMillis()
                )
                
                database.reference
                    .child(DRIVER_RESPONSES_PATH)
                    .child(actualBookingId)
                    .setValue(response)
                    .await()
                
                // Cancel other pending requests for this booking
                cancelOtherRequests(actualBookingId, driverId)
                
                // Update assignment tracking as successful
                updateBookingAssignmentStatus(actualBookingId, "ACCEPTED", "Driver $driverId accepted")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-3.2.3: Driver declines a ride request
     * Now triggers immediate reassignment to next highest-rated driver
     * ALSO checks if there are other pending requests, and if not, expires the booking
     */
    suspend fun declineRideRequest(requestId: String, driverId: String): Result<Unit> {
        return try {
            // Get the request details first to extract bookingId
            val requestSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)
                .get()
                .await()

            val request = requestSnapshot.getValue(DriverRequest::class.java)

            // Update request status to declined
            database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)
                .child("status")
                .setValue(DriverRequestStatus.DECLINED.name)
                .await()

            // Signal that this driver declined so monitoring can immediately reassign
            if (request != null) {
                database.reference
                    .child("driver_declines")
                    .child(request.bookingId)
                    .child(driverId)
                    .setValue(mapOf(
                        "requestId" to requestId,
                        "driverId" to driverId,
                        "bookingId" to request.bookingId,
                        "declineTime" to System.currentTimeMillis()
                    ))
                    .await()

                android.util.Log.i("DriverMatching", "Driver $driverId declined booking ${request.bookingId} - signaling for immediate reassignment")

                // FALLBACK: Check if there are any other pending requests for this booking
                // If not, expire the booking (handles case where passenger app isn't monitoring)
                kotlinx.coroutines.GlobalScope.launch {
                    // Wait a brief moment for potential reassignment
                    kotlinx.coroutines.delay(2000L) // 2 seconds (reduced from 5)

                    android.util.Log.d("DriverMatching", "🔍 FALLBACK CHECK: Checking if booking ${request.bookingId} should be expired after driver $driverId declined")

                    // Check all driver requests for this booking
                    val allDriversSnapshot = database.reference
                        .child(DRIVER_REQUESTS_PATH)
                        .get()
                        .await()

                    var hasPendingRequests = false
                    var totalRequests = 0
                    for (driverSnapshot in allDriversSnapshot.children) {
                        driverSnapshot.children.forEach { requestSnapshot ->
                            val req = requestSnapshot.getValue(DriverRequest::class.java)
                            if (req?.bookingId == request.bookingId) {
                                totalRequests++
                                android.util.Log.d("DriverMatching", "   Found request for driver ${req.driverId}: status = ${req.status}")
                                if (req.status == DriverRequestStatus.PENDING) {
                                    hasPendingRequests = true
                                    android.util.Log.d("DriverMatching", "   ✅ Found PENDING request - booking will NOT be expired")
                                }
                            }
                        }
                    }

                    android.util.Log.d("DriverMatching", "📊 Summary for booking ${request.bookingId}: Total requests = $totalRequests, Has pending = $hasPendingRequests")

                    // If no pending requests exist, check the booking status and expire if still PENDING
                    if (!hasPendingRequests) {
                        val bookingCheck = bookingRepository.getBooking(request.bookingId).getOrNull()
                        android.util.Log.d("DriverMatching", "💡 Booking ${request.bookingId} current status in Firestore: ${bookingCheck?.status}")

                        if (bookingCheck?.status == BookingStatus.PENDING) {
                            android.util.Log.w("DriverMatching", "❌ EXPIRING booking ${request.bookingId} - no pending requests after decline")
                            bookingRepository.updateBookingStatus(request.bookingId, BookingStatus.EXPIRED)
                            updateBookingAssignmentStatus(request.bookingId, "EXPIRED", "All drivers declined and no monitoring active")
                        } else {
                            android.util.Log.d("DriverMatching", "✅ Booking ${request.bookingId} already in terminal state: ${bookingCheck?.status}")
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Listen for ride requests for a specific driver - filters requests properly for Second Chance system
     */
    fun observeDriverRequests(driverId: String): Flow<List<DriverRequest>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = mutableListOf<DriverRequest>()
                val currentTime = System.currentTimeMillis()
                
                snapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request != null) {
                        // Skip CANCELLED requests immediately - they should never be shown to drivers
                        if (request.status == DriverRequestStatus.CANCELLED) {
                            // Auto-cleanup cancelled requests immediately
                            requestSnapshot.ref.removeValue()
                            android.util.Log.d("DriverMatching", "Auto-removed cancelled request ${request.requestId}")
                            return@forEach
                        }

                        // Auto-cleanup expired requests (older than 2 minutes)
                        if (request.getPhase(currentTime) == RequestPhase.EXPIRED &&
                            (currentTime - request.requestTime) > 120000) { // 2 minutes
                            requestSnapshot.ref.removeValue()
                            android.util.Log.d("DriverMatching", "Auto-removed expired request ${request.requestId}")
                            return@forEach
                        }

                        // Auto-cleanup accepted requests (older than 30 minutes)
                        if (request.status == DriverRequestStatus.ACCEPTED &&
                            (currentTime - request.requestTime) > 1800000) { // 30 minutes
                            requestSnapshot.ref.removeValue()
                            android.util.Log.d("DriverMatching", "Auto-removed old accepted request ${request.requestId}")
                            return@forEach
                        }

                        // Only include recent requests (not older than 1 hour) for UI display
                        val isRecentRequest = (currentTime - request.requestTime) <= 3600000 // 1 hour
                        if (!isRecentRequest) {
                            // Skip old requests to prevent UI clutter
                            return@forEach
                        }
                        when {
                            // Active requests (first 30 seconds)
                            request.isInInitialPhase(currentTime) -> {
                                requests.add(request)
                            }
                            // Second chance requests (30 seconds to 3.5 minutes)
                            request.isInSecondChance(currentTime) -> {
                                requests.add(request)
                            }
                            // Keep all expired requests for earnings history - no auto-cleanup
                            request.isFullyExpired(currentTime) -> {
                                requests.add(request)
                            }
                        }
                    }
                }
                trySend(requests)
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("DriverMatching", "Error observing driver requests", error.toException())
            }
        }
        
        val ref = database.reference
            .child(DRIVER_REQUESTS_PATH)
            .child(driverId)
        
        ref.addValueEventListener(listener)
        
        awaitClose {
            ref.removeEventListener(listener)
        }
    }
    
    /**
     * Send FCM notification to driver using NotificationService
     */
    private suspend fun sendDriverNotification(driverId: String, booking: Booking) {
        try {
            android.util.Log.d("DriverMatching", "Sending FCM notification to driver: $driverId")
            
            // Use the proper NotificationService to send FCM notifications
            val notificationResult = notificationService.sendRideRequestToDrivers(booking, listOf(driverId))
            
            if (notificationResult.isSuccess) {
                android.util.Log.d("DriverMatching", "Successfully queued FCM notification for driver: $driverId")
            } else {
                android.util.Log.w("DriverMatching", "Failed to queue FCM notification for driver $driverId: ${notificationResult.exceptionOrNull()?.message}")
                
                // Fallback: Store in Firebase Realtime Database for driver apps to poll
                database.reference
                    .child("driver_requests")
                    .child(driverId)
                    .child("ride_${booking.id}_${System.currentTimeMillis()}")
                    .setValue(mapOf(
                        "type" to "ride_request",
                        "booking_id" to booking.id,
                        "pickup_address" to booking.pickupLocation.address,
                        "destination_address" to booking.destination.address,
                        "fare_estimate" to booking.fareEstimate.totalEstimate.toString(),
                        "timestamp" to System.currentTimeMillis(),
                        "driver_id" to driverId,
                        "passenger_id" to booking.passengerId
                    ))
                    .await()
                
                android.util.Log.d("DriverMatching", "Stored notification fallback in database for driver: $driverId")
            }
            
            // ALSO show local notification if current user is the target driver (for testing)
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == driverId) {
                // Show local notification for testing when the same device is both passenger and driver
                showLocalRideRequestNotification(mapOf(
                    "type" to "ride_request",
                    "booking_id" to booking.id,
                    "pickup_address" to booking.pickupLocation.address,
                    "destination_address" to booking.destination.address,
                    "fare_estimate" to booking.fareEstimate.totalEstimate.toString(),
                    "title" to "New Ride Request",
                    "body" to "₱${booking.fareEstimate.totalEstimate} • ${booking.pickupLocation.address}"
                ))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Error sending notification to driver $driverId", e)
        }
    }
    
    /**
     * Simulate local notification for development
     * In production, this would be handled by Firebase Cloud Functions
     */
    private fun simulateLocalNotification(driverId: String, data: Map<String, String>) {
        // Log the notification details
        println("=== RIDE REQUEST NOTIFICATION ===")
        println("Driver ID: $driverId")
        println("Title: ${data["title"]}")
        println("Body: ${data["body"]}")
        println("Pickup: ${data["pickup_address"]}")
        println("Destination: ${data["destination_address"]}")
        println("Fare: ₱${data["fare_estimate"]}")
        println("Booking ID: ${data["booking_id"]}")
        println("=================================")
        
        // For development, show actual notification if the current user is a driver
        // This simulates what would happen in a real multi-device environment
        try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                // In a real app, you'd check if current user is the target driver
                // For demo, we'll show notification for any driver request
                showLocalRideRequestNotification(data)
            }
        } catch (e: Exception) {
            println("Error showing local notification: ${e.message}")
        }
    }
    
    /**
     * Show actual system notification for ride request
     */
    private fun showLocalRideRequestNotification(data: Map<String, String>) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val notification = NotificationCompat.Builder(context, "ride_requests")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(data["title"] ?: "New Ride Request")
                .setContentText(data["body"] ?: "Ride request available")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Pickup: ${data["pickup_address"]}\nDestination: ${data["destination_address"]}\nFare: ₱${data["fare_estimate"]}")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(1000, 1000, 1000))
                .build()
            
            notificationManager.notify(data["booking_id"]?.hashCode() ?: 0, notification)
            
        } catch (e: Exception) {
            println("Error creating system notification: ${e.message}")
        }
    }
    
    /**
     * Cancel other pending requests when a driver accepts
     */
    private suspend fun cancelOtherRequests(bookingId: String, acceptedDriverId: String) {
        try {
            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()
            
            allDriversSnapshot.children.forEach { driverSnapshot ->
                val driverId = driverSnapshot.key ?: return@forEach
                
                if (driverId != acceptedDriverId) {
                    driverSnapshot.children.forEach { requestSnapshot ->
                        val request = requestSnapshot.getValue(DriverRequest::class.java)
                        if (request?.bookingId == bookingId && 
                            request.status == DriverRequestStatus.PENDING) {
                            
                            // Mark as cancelled
                            database.reference
                                .child(DRIVER_REQUESTS_PATH)
                                .child(driverId)
                                .child(request.requestId)
                                .child("status")
                                .setValue(DriverRequestStatus.CANCELLED.name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail the main operation
            println("Error cancelling other requests: ${e.message}")
        }
    }
    
    /**
     * FR-3.2.3 & FR-3.2.4: Enhanced monitoring with priority-based reassignment
     * Now monitors single driver and moves to next highest-rated driver on decline/timeout
     * Listens for immediate decline events to avoid waiting for timeout
     */
    private fun startRequestMonitoring(
        bookingId: String,
        driverIds: List<String>,
        booking: Booking,
        attemptNumber: Int,
        remainingDrivers: List<DriverLocation> = emptyList()
    ) {
        kotlinx.coroutines.GlobalScope.launch {
            val driverId = driverIds.firstOrNull()

            // Set up listener for immediate decline detection
            val declineListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        android.util.Log.i("DriverMatching", "Driver $driverId declined immediately - reassigning to next driver")
                        // Clean up listener
                        snapshot.ref.removeEventListener(this)
                        // Clean up decline signal
                        snapshot.ref.removeValue()

                        // Immediately send to next driver
                        kotlinx.coroutines.GlobalScope.launch {
                            handleDriverDeclineOrTimeout(
                                bookingId = bookingId,
                                driverId = driverId,
                                booking = booking,
                                attemptNumber = attemptNumber,
                                remainingDrivers = remainingDrivers,
                                wasDeclined = true
                            )
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("DriverMatching", "Error listening for decline", error.toException())
                }
            }

            if (driverId != null) {
                // Listen for decline events
                val declineRef = database.reference
                    .child("driver_declines")
                    .child(bookingId)
                    .child(driverId)

                declineRef.addValueEventListener(declineListener)
            }

            // Wait for initial 30-second period
            delay(REQUEST_TIMEOUT_MS)

            // Remove decline listener after timeout
            if (driverId != null) {
                database.reference
                    .child("driver_declines")
                    .child(bookingId)
                    .child(driverId)
                    .removeEventListener(declineListener)
            }

            // Check if driver accepted
            val responseSnapshot = database.reference
                .child(DRIVER_RESPONSES_PATH)
                .child(bookingId)
                .get()
                .await()

            if (!responseSnapshot.exists()) {
                android.util.Log.i("DriverMatching", "Driver did not accept booking $bookingId within ${REQUEST_TIMEOUT_MS}ms")

                // Check if driver declined or just timed out
                if (driverId != null) {
                    val requestSnapshot = database.reference
                        .child(DRIVER_REQUESTS_PATH)
                        .child(driverId)
                        .orderByChild("bookingId")
                        .equalTo(bookingId)
                        .get()
                        .await()

                    var wasDeclined = false
                    requestSnapshot.children.forEach { requestSnap ->
                        val request = requestSnap.getValue(DriverRequest::class.java)
                        if (request?.status == DriverRequestStatus.DECLINED) {
                            wasDeclined = true
                            android.util.Log.i("DriverMatching", "Driver $driverId declined booking $bookingId")
                        }
                    }

                    if (!wasDeclined) {
                        android.util.Log.i("DriverMatching", "Driver $driverId did not respond to booking $bookingId (timeout)")
                    }

                    // Handle decline/timeout
                    handleDriverDeclineOrTimeout(
                        bookingId = bookingId,
                        driverId = driverId,
                        booking = booking,
                        attemptNumber = attemptNumber,
                        remainingDrivers = remainingDrivers,
                        wasDeclined = wasDeclined
                    )
                }
            }
        }
    }

    /**
     * Handle driver decline or timeout - reassign to next driver or give up
     */
    private suspend fun handleDriverDeclineOrTimeout(
        bookingId: String,
        driverId: String?,
        booking: Booking,
        attemptNumber: Int,
        remainingDrivers: List<DriverLocation>,
        wasDeclined: Boolean
    ) {
        // Expire current driver's request
        if (driverId != null) {
            fullyExpireDriverRequests(bookingId, listOf(driverId))
        }

        // Try next driver in priority queue
        if (remainingDrivers.isNotEmpty()) {
            val nextDriver = remainingDrivers.first()
            android.util.Log.i("DriverMatching", "Moving to next driver: ${nextDriver.driverId} (rating: ${nextDriver.rating})")

            // CRITICAL FIX: Recheck driver state before sending (may have accepted another booking since initial filtering)
            try {
                val activeBookingsCount = getDriverActiveBookingsCount(nextDriver.driverId)

                // Check if driver exceeded capacity
                if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
                    android.util.Log.w("DriverMatching", "⏭️ Driver ${nextDriver.driverId} now at capacity ($activeBookingsCount bookings) - skipping")
                    if (remainingDrivers.size > 1) {
                        handleDriverDeclineOrTimeout(bookingId, null, booking, attemptNumber, remainingDrivers.drop(1), wasDeclined)
                    } else {
                        android.util.Log.w("DriverMatching", "No more compatible drivers, attempting wider search")
                        val excludedDrivers = if (driverId != null) listOf(driverId) else emptyList()
                        reassignBooking(booking, attemptNumber + 1, 10.0 + (attemptNumber * 5.0), excludedDrivers)
                    }
                    return
                }

                // STRICT direction filtering if driver now has active passengers
                if (activeBookingsCount > 0) {
                    android.util.Log.d("DriverMatching", "Driver ${nextDriver.driverId} now has $activeBookingsCount active booking(s) - checking direction compatibility")

                    val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                        driverId = nextDriver.driverId,
                        driverLat = nextDriver.latitude,
                        driverLng = nextDriver.longitude,
                        newDestination = booking.destination,
                        newDestinationCoords = Pair(booking.destination.coordinates.latitude, booking.destination.coordinates.longitude),
                        newPickupCoords = Pair(booking.pickupLocation.coordinates.latitude, booking.pickupLocation.coordinates.longitude)
                    )

                    if (!isDestinationCompatible) {
                        android.util.Log.w("DriverMatching", "⏭️ Driver ${nextDriver.driverId} - OPPOSITE DIRECTION (driver state changed) - skipping")
                        if (remainingDrivers.size > 1) {
                            handleDriverDeclineOrTimeout(bookingId, null, booking, attemptNumber, remainingDrivers.drop(1), wasDeclined)
                        } else {
                            android.util.Log.w("DriverMatching", "No more compatible drivers, attempting wider search")
                            val excludedDrivers = if (driverId != null) listOf(driverId) else emptyList()
                            reassignBooking(booking, attemptNumber + 1, 10.0 + (attemptNumber * 5.0), excludedDrivers)
                        }
                        return
                    }

                    android.util.Log.d("DriverMatching", "✅ Driver ${nextDriver.driverId} direction compatible - proceeding")
                }

                val safeBookingId = booking.id
                val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
                val driverRequest = DriverRequest(
                    requestId = "${safeBookingId}_${nextDriver.driverId}_${uniqueTimestamp}",
                    bookingId = safeBookingId,
                    driverId = nextDriver.driverId,
                    passengerId = booking.passengerId,
                    pickupLocation = DatabaseLocation.fromBookingLocation(booking.pickupLocation),
                    destination = DatabaseLocation.fromBookingLocation(booking.destination),
                    fareEstimate = DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
                    requestTime = System.currentTimeMillis(),
                    expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                    specialInstructions = booking.specialInstructions,
                    passengerDiscountPercentage = booking.passengerDiscountPercentage
                )

                database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(nextDriver.driverId)
                    .child(driverRequest.requestId)
                    .setValue(driverRequest)
                    .await()

                sendDriverNotification(nextDriver.driverId, booking)

                // Continue monitoring with remaining drivers
                startRequestMonitoring(bookingId, listOf(nextDriver.driverId), booking, attemptNumber, remainingDrivers.drop(1))

                android.util.Log.i("DriverMatching", "Successfully sent request to next driver ${nextDriver.driverId}")
            } catch (e: Exception) {
                android.util.Log.e("DriverMatching", "Failed to send request to next driver", e)
                // Continue to next driver or give up
                if (remainingDrivers.size > 1) {
                    handleDriverDeclineOrTimeout(bookingId, null, booking, attemptNumber, remainingDrivers.drop(1), wasDeclined)
                } else {
                    android.util.Log.w("DriverMatching", "No more drivers available, marking booking as expired")
                    bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
                    updateBookingAssignmentStatus(bookingId, "EXPIRED", "No drivers accepted after trying all available drivers")
                }
            }
        } else {
            // No more drivers in current pool, try wider search if attempts remaining
            if (attemptNumber < MAX_REASSIGNMENT_ATTEMPTS) {
                android.util.Log.i("DriverMatching", "No more drivers in priority queue, attempting wider search (attempt ${attemptNumber + 1}/$MAX_REASSIGNMENT_ATTEMPTS)")
                val expandedRadius = 10.0 + (attemptNumber * 5.0)
                val excludedDrivers = if (driverId != null) listOf(driverId) else emptyList()
                reassignBooking(booking, attemptNumber + 1, expandedRadius, excludedDrivers)
            } else {
                android.util.Log.w("DriverMatching", "Max reassignment attempts reached for booking $bookingId, marking as expired")
                bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
                updateBookingAssignmentStatus(bookingId, "EXPIRED", "No drivers available after trying all drivers and $MAX_REASSIGNMENT_ATTEMPTS expanded searches")
            }
        }
    }
    
    /**
     * FR-3.2.4: Auto-reassignment logic for unaccepted bookings
     * Now uses rating-based prioritization
     */
    private suspend fun reassignBooking(
        booking: Booking,
        attemptNumber: Int,
        radiusKm: Double,
        excludedDriverIds: List<String>
    ) {
        try {
            // Get ALL available drivers worldwide for reassignment (already sorted by rating)
            val nearbyDriversResult = driverRepository.getNearbyDrivers(
                centerLat = booking.pickupLocation.coordinates.latitude,
                centerLng = booking.pickupLocation.coordinates.longitude,
                radiusKm = radiusKm
            )

            if (nearbyDriversResult.isFailure) {
                android.util.Log.e("DriverMatching", "Failed to get nearby drivers for reassignment")
                bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                return
            }

            val nearbyDrivers = nearbyDriversResult.getOrNull() ?: emptyList()

            // Filter out previously notified drivers and by vehicle category
            // Drivers are already sorted by rating from getNearbyDrivers()
            val categoryFilteredDrivers = nearbyDrivers.filter { driver ->
                val isRightVehicle = driver.vehicleCategory == booking.vehicleCategory && driver.online
                val isNotPreviouslyNotified = !excludedDriverIds.contains(driver.driverId)

                println("DEBUG REASSIGNMENT: Driver ${driver.driverId} - Rating: ${driver.rating}, Location: ${driver.latitude}, ${driver.longitude}, Vehicle: ${driver.vehicleCategory}, Previously notified: ${excludedDriverIds.contains(driver.driverId)}")

                isRightVehicle && isNotPreviouslyNotified
            }

            // Also filter by booking capacity (max 5 accepted rides per driver), distance, and destination compatibility
            val availableDrivers = mutableListOf<DriverLocation>()
            for (driver in categoryFilteredDrivers) {
                // Check distance to pickup - must be within 500m (already in meters)
                val distanceToPickupMeters = calculateDistance(
                    driver.latitude, driver.longitude,
                    booking.pickupLocation.coordinates.latitude,
                    booking.pickupLocation.coordinates.longitude
                )

                if (distanceToPickupMeters > MAX_DRIVER_TO_PICKUP_RADIUS_METERS) {
                    println("DEBUG REASSIGNMENT: Driver ${driver.driverId} too far from pickup (${String.format("%.0f", distanceToPickupMeters)}m > ${MAX_DRIVER_TO_PICKUP_RADIUS_METERS.toInt()}m)")
                    continue
                }

                val activeBookingsCount = getDriverActiveBookingsCount(driver.driverId)
                if (activeBookingsCount < MAX_ACCEPTED_RIDES_PER_DRIVER) {
                    // STRICT direction filtering when driver has existing rides
                    if (activeBookingsCount > 0) {
                        // STRICT: Check destination compatibility (includes STRICT direction filtering)
                        val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                            driverId = driver.driverId,
                            driverLat = driver.latitude,
                            driverLng = driver.longitude,
                            newDestination = booking.destination,
                            newDestinationCoords = Pair(booking.destination.coordinates.latitude, booking.destination.coordinates.longitude),
                            newPickupCoords = Pair(booking.pickupLocation.coordinates.latitude, booking.pickupLocation.coordinates.longitude)
                        )

                        // STRICT: Only accept if direction is compatible (NOT opposite)
                        if (isDestinationCompatible) {
                            availableDrivers.add(driver)
                            println("DEBUG REASSIGNMENT: Driver ${driver.driverId} is available - has $activeBookingsCount/$MAX_ACCEPTED_RIDES_PER_DRIVER active bookings, destination/direction compatible")
                        } else {
                            println("DEBUG REASSIGNMENT: Driver ${driver.driverId} filtered out - OPPOSITE DIRECTION (strict filtering)")
                        }
                    } else {
                        // Driver has no active rides, can accept any destination
                        availableDrivers.add(driver)
                        println("DEBUG REASSIGNMENT: Driver ${driver.driverId} is available - has $activeBookingsCount/$MAX_ACCEPTED_RIDES_PER_DRIVER active bookings (no destination restrictions)")
                    }
                } else {
                    println("DEBUG REASSIGNMENT: Driver ${driver.driverId} filtered out - already has $activeBookingsCount accepted bookings (max: $MAX_ACCEPTED_RIDES_PER_DRIVER)")
                }
            }

            if (availableDrivers.isEmpty()) {
                android.util.Log.w("DriverMatching", "No new drivers available for reassignment of booking ${booking.id}")
                // Cancel all pending driver requests to clean up
                cancelOtherRequests(booking.id, "")
                bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                updateBookingAssignmentStatus(booking.id, "EXPIRED", "No available drivers found in expanded search")
                return
            }

            android.util.Log.i("DriverMatching", "Found ${availableDrivers.size} new drivers for reassignment attempt $attemptNumber (sorted by rating)")

            // Send to highest-rated driver first
            val topDriver = availableDrivers.first()
            android.util.Log.i("DriverMatching", "Reassigning to highest-rated available driver: ${topDriver.driverId} (rating: ${topDriver.rating})")

            val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
            val driverRequest = DriverRequest(
                requestId = "${booking.id}_${topDriver.driverId}_${uniqueTimestamp}",
                bookingId = booking.id,
                driverId = topDriver.driverId,
                passengerId = booking.passengerId,
                pickupLocation = DatabaseLocation.fromBookingLocation(booking.pickupLocation),
                destination = DatabaseLocation.fromBookingLocation(booking.destination),
                fareEstimate = DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
                requestTime = System.currentTimeMillis(),
                expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                specialInstructions = booking.specialInstructions,
                passengerDiscountPercentage = booking.passengerDiscountPercentage
            )

            // Store request in database
            database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(topDriver.driverId)
                .child(driverRequest.requestId)
                .setValue(driverRequest)
                .await()

            // Send notification
            try {
                sendDriverNotification(topDriver.driverId, booking)

                // Track reassignment attempt
                trackBookingAssignment(booking.id, attemptNumber, 1)

                // Start monitoring again with remaining drivers
                startRequestMonitoring(booking.id, listOf(topDriver.driverId), booking, attemptNumber, availableDrivers.drop(1))

                android.util.Log.i("DriverMatching", "Reassignment attempt $attemptNumber completed for booking ${booking.id}, notified driver ${topDriver.driverId} (rating: ${topDriver.rating})")
            } catch (e: Exception) {
                android.util.Log.w("DriverMatching", "Failed to notify driver ${topDriver.driverId} in reassignment", e)

                // Try next driver if available
                if (availableDrivers.size > 1) {
                    val allExcluded = excludedDriverIds + topDriver.driverId
                    reassignBooking(booking, attemptNumber, radiusKm, allExcluded)
                } else {
                    bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Error in reassignment for booking ${booking.id}", e)
            bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
        }
    }
    
    /**
     * Track booking assignment attempts for analytics and debugging
     */
    private suspend fun trackBookingAssignment(bookingId: String, attemptNumber: Int, driversNotified: Int) {
        try {
            val assignmentData = mapOf(
                "bookingId" to bookingId,
                "attemptNumber" to attemptNumber,
                "driversNotified" to driversNotified,
                "timestamp" to System.currentTimeMillis(),
                "status" to "PENDING"
            )
            
            database.reference
                .child(BOOKING_ASSIGNMENTS_PATH)
                .child(bookingId)
                .child("attempt_$attemptNumber")
                .setValue(assignmentData)
                .await()
                
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to track booking assignment", e)
        }
    }
    
    /**
     * Update booking assignment status
     */
    private suspend fun updateBookingAssignmentStatus(bookingId: String, status: String, reason: String? = null) {
        try {
            val updates = mutableMapOf<String, Any>(
                "finalStatus" to status,
                "finalTimestamp" to System.currentTimeMillis()
            )
            reason?.let { updates["reason"] = it }
            
            database.reference
                .child(BOOKING_ASSIGNMENTS_PATH)
                .child(bookingId)
                .updateChildren(updates)
                .await()
                
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to update booking assignment status", e)
        }
    }
    
    /**
     * Transition driver requests to Second Chance phase
     */
    private suspend fun transitionToSecondChance(bookingId: String, driverIds: List<String>) {
        driverIds.forEach { driverId ->
            try {
                database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(driverId)
                    .orderByChild("bookingId")
                    .equalTo(bookingId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.children.forEach { requestSnapshot ->
                            val request = requestSnapshot.getValue(DriverRequest::class.java)
                            if (request?.status == DriverRequestStatus.PENDING) {
                                // Update status to SECOND_CHANCE instead of EXPIRED
                                requestSnapshot.ref.child("status")
                                    .setValue(DriverRequestStatus.SECOND_CHANCE.name)
                                android.util.Log.d("DriverMatching", "Transitioned request ${request.requestId} to Second Chance for driver $driverId")
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.w("DriverMatching", "Failed to transition request to Second Chance for driver $driverId", e)
            }
        }
    }
    
    /**
     * Mark driver requests as fully expired (after Second Chance period)
     */
    private suspend fun fullyExpireDriverRequests(bookingId: String, driverIds: List<String>) {
        driverIds.forEach { driverId ->
            try {
                database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(driverId)
                    .orderByChild("bookingId")
                    .equalTo(bookingId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.children.forEach { requestSnapshot ->
                            val request = requestSnapshot.getValue(DriverRequest::class.java)
                            if (request?.status == DriverRequestStatus.SECOND_CHANCE || 
                                request?.status == DriverRequestStatus.PENDING) {
                                requestSnapshot.ref.child("status")
                                    .setValue(DriverRequestStatus.EXPIRED.name)
                                android.util.Log.d("DriverMatching", "Fully expired request ${request.requestId} for driver $driverId")
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.w("DriverMatching", "Failed to fully expire request for driver $driverId", e)
            }
        }
    }
    
    /**
     * Mark driver requests as expired (legacy function - kept for compatibility)
     */
    private suspend fun expireDriverRequests(bookingId: String, driverIds: List<String>) {
        fullyExpireDriverRequests(bookingId, driverIds)
    }
    
    /**
     * Queue booking for future matching when drivers come online
     * Only keeps bookings from the last 5 minutes
     */
    private suspend fun queueBookingForFutureMatching(booking: Booking) {
        try {
            val queueData = mapOf(
                "bookingId" to booking.id,
                "passengerId" to booking.passengerId,
                "pickupLocation" to DatabaseLocation.fromBookingLocation(booking.pickupLocation),
                "destination" to DatabaseLocation.fromBookingLocation(booking.destination),
                "fareEstimate" to DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
                "vehicleCategory" to booking.vehicleCategory.name,
                "requestTime" to booking.requestTime,
                "queuedTime" to System.currentTimeMillis(),
                "expirationTime" to System.currentTimeMillis() + 300000L // 5 minutes from now
            )
            
            database.reference
                .child(QUEUED_BOOKINGS_PATH)
                .child(booking.id)
                .setValue(queueData)
                .await()
                
            android.util.Log.i("DriverMatching", "Queued booking ${booking.id} for future matching")
            
            // Set up cleanup of old queued bookings
            cleanupExpiredQueuedBookings()
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to queue booking ${booking.id}", e)
        }
    }
    
    /**
     * Clean up expired queued bookings (older than 5 minutes)
     */
    private suspend fun cleanupExpiredQueuedBookings() {
        try {
            val cutoffTime = System.currentTimeMillis() - 300000L // 5 minutes ago
            
            val expiredBookings = database.reference
                .child(QUEUED_BOOKINGS_PATH)
                .orderByChild("queuedTime")
                .endAt(cutoffTime.toDouble())
                .get()
                .await()
            
            expiredBookings.children.forEach { snapshot ->
                snapshot.ref.removeValue()
            }
            
            android.util.Log.d("DriverMatching", "Cleaned up ${expiredBookings.childrenCount} expired queued bookings")
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to cleanup expired queued bookings", e)
        }
    }
    
    /**
     * Process queued bookings when a driver comes online
     * This should be called by DriverRepository when a driver's status changes to online
     */
    suspend fun processQueuedBookingsForNewDriver(driverId: String): Result<Int> {
        return try {
            android.util.Log.d("DriverMatching", "🔄 Starting processQueuedBookingsForNewDriver for driver: $driverId")

            // Get driver location to check vehicle category and position
            val nearbyDriversResult = driverRepository.getNearbyDrivers(0.0, 0.0, 50.0) // Get all drivers
            if (nearbyDriversResult.isFailure) {
                android.util.Log.e("DriverMatching", "❌ Failed to get nearby drivers: ${nearbyDriversResult.exceptionOrNull()?.message}")
                return Result.failure(Exception("Could not get driver details"))
            }

            val allDrivers = nearbyDriversResult.getOrNull() ?: emptyList()
            android.util.Log.d("DriverMatching", "📍 Found ${allDrivers.size} total drivers in system")

            val driverLocation = allDrivers.find { it.driverId == driverId }
            if (driverLocation == null) {
                android.util.Log.e("DriverMatching", "❌ Driver $driverId not found in nearby drivers list")
                allDrivers.forEach { driver ->
                    android.util.Log.d("DriverMatching", "Available driver: ${driver.driverId}")
                }
                return Result.failure(Exception("Driver location not found"))
            }

            android.util.Log.d("DriverMatching", "✅ Found driver $driverId - Vehicle: ${driverLocation.vehicleCategory}, Lat: ${driverLocation.latitude}, Lng: ${driverLocation.longitude}")

            // Check if driver already has max accepted bookings
            val activeBookingsCount = getDriverActiveBookingsCount(driverId)
            if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
                android.util.Log.d("DriverMatching", "⏭️ Driver $driverId already has $activeBookingsCount accepted bookings (max: $MAX_ACCEPTED_RIDES_PER_DRIVER) - not processing queued bookings")
                return Result.success(0)
            }

            // Get all non-expired queued bookings
            val currentTime = System.currentTimeMillis()
            val queuedBookingsSnapshot = database.reference
                .child(QUEUED_BOOKINGS_PATH)
                .orderByChild("expirationTime")
                .startAt(currentTime.toDouble())
                .get()
                .await()

            var processedCount = 0
            val remainingCapacity = MAX_ACCEPTED_RIDES_PER_DRIVER - activeBookingsCount

            for (bookingSnapshot in queuedBookingsSnapshot.children) {
                // Stop if driver would exceed capacity
                if (processedCount >= remainingCapacity) {
                    android.util.Log.d("DriverMatching", "⏭️ Driver $driverId would reach capacity - not sending more queued bookings (sent: $processedCount, capacity remaining: $remainingCapacity)")
                    break
                }

                val bookingData = bookingSnapshot.value as? Map<String, Any> ?: continue
                val vehicleCategory = bookingData["vehicleCategory"] as? String ?: continue
                val pickupLocation = bookingData["pickupLocation"] as? Map<String, Any> ?: continue
                val pickupLat = pickupLocation["latitude"] as? Double ?: continue
                val pickupLng = pickupLocation["longitude"] as? Double ?: continue

                // Check if driver matches vehicle category and is nearby
                if (vehicleCategory == driverLocation.vehicleCategory.name) {
                    val distanceMeters = calculateDistance(
                        driverLocation.latitude, driverLocation.longitude,
                        pickupLat, pickupLng
                    ) // Already in meters

                    if (distanceMeters <= MAX_DRIVER_TO_PICKUP_RADIUS_METERS) { // Within configured radius only
                        val bookingId = bookingData["bookingId"] as? String ?: continue

                        // NEW: Check destination compatibility and pickup proximity if driver has existing rides
                        if (activeBookingsCount > 0) {
                            val destinationData = bookingData["destination"] as? Map<String, Any> ?: continue
                            val destLat = destinationData["latitude"] as? Double ?: continue
                            val destLng = destinationData["longitude"] as? Double ?: continue
                            val destAddress = (destinationData["address"] as? String) ?: "Unknown"

                            // Create BookingLocation for compatibility check
                            val newDestination = BookingLocation(
                                address = destAddress,
                                coordinates = GeoPoint(destLat, destLng)
                            )

                            // STRICT direction filtering: Check destination compatibility only
                            val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                                driverId = driverId,
                                driverLat = driverLocation.latitude,
                                driverLng = driverLocation.longitude,
                                newDestination = newDestination,
                                newDestinationCoords = Pair(destLat, destLng),
                                newPickupCoords = Pair(pickupLat, pickupLng)
                            )

                            // STRICT: Only accept if direction is compatible (NOT opposite)
                            if (!isDestinationCompatible) {
                                android.util.Log.d("DriverMatching", "⏭️ Skipping queued booking $bookingId for driver $driverId - OPPOSITE DIRECTION (strict filtering)")
                                continue
                            }

                            val reason = "destination/direction compatible"
                            android.util.Log.d("DriverMatching", "✅ Queued booking $bookingId eligible for driver $driverId - $reason")
                        }

                        // Create and send driver request
                        val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
                        val driverRequest = DriverRequest(
                            requestId = "${bookingId}_${driverId}_${uniqueTimestamp}",
                            bookingId = bookingId,
                            driverId = driverId,
                            passengerId = bookingData["passengerId"] as? String ?: "",
                            pickupLocation = DatabaseLocation(
                                address = (pickupLocation["address"] as? String) ?: "Unknown",
                                latitude = pickupLat,
                                longitude = pickupLng
                            ),
                            destination = DatabaseLocation(
                                address = ((bookingData["destination"] as? Map<String, Any>)?.get("address") as? String) ?: "Unknown",
                                latitude = ((bookingData["destination"] as? Map<String, Any>)?.get("latitude") as? Double) ?: 0.0,
                                longitude = ((bookingData["destination"] as? Map<String, Any>)?.get("longitude") as? Double) ?: 0.0
                            ),
                            fareEstimate = DatabaseFareEstimate(
                                totalEstimate = ((bookingData["fareEstimate"] as? Map<String, Any>)?.get("totalEstimate") as? Double) ?: 0.0,
                                estimatedDuration = ((bookingData["fareEstimate"] as? Map<String, Any>)?.get("estimatedDuration") as? Number)?.toInt() ?: 0,
                                estimatedDistance = ((bookingData["fareEstimate"] as? Map<String, Any>)?.get("estimatedDistance") as? Double) ?: 0.0
                            ),
                            expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                            secondChanceExpirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS + 180000L, // 30s + 3min
                            specialInstructions = (bookingData["specialInstructions"] as? String) ?: "",
                            passengerDiscountPercentage = (bookingData["passengerDiscountPercentage"] as? Number)?.toInt()
                        )

                        // Send request to driver
                        database.reference
                            .child(DRIVER_REQUESTS_PATH)
                            .child(driverId)
                            .child(driverRequest.requestId)
                            .setValue(driverRequest)
                            .await()

                        // Remove from queue since we've processed it
                        bookingSnapshot.ref.removeValue().await()

                        processedCount++
                        android.util.Log.i("DriverMatching", "Sent queued booking $bookingId to newly online driver $driverId")

                        // Monitor this booking for acceptance to auto-remove from other drivers
                        monitorBookingAcceptance(bookingId)
                    }
                }
            }

            // ENHANCEMENT: Also check for active pending requests that this driver could handle
            val activePendingCount = processActivePendingRequestsForNewDriver(driverId, driverLocation)

            val totalProcessed = processedCount + activePendingCount
            android.util.Log.i("DriverMatching", "Driver $driverId received $processedCount queued + $activePendingCount active requests = $totalProcessed total")

            Result.success(totalProcessed)

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to process queued bookings for driver $driverId", e)
            Result.failure(e)
        }
    }

    /**
     * Process active pending requests for a newly online driver
     * This handles ongoing ride requests that are currently sent to other drivers
     */
    private suspend fun processActivePendingRequestsForNewDriver(driverId: String, driverLocation: DriverLocation): Int {
        return try {
            android.util.Log.d("DriverMatching", "🔍 Checking active pending requests for newly online driver: $driverId")
            android.util.Log.d("DriverMatching", "🚗 Driver location - Lat: ${driverLocation.latitude}, Lng: ${driverLocation.longitude}, Vehicle: ${driverLocation.vehicleCategory}, Online: ${driverLocation.online}")

            // Get all current bookings that are in PENDING status (ride requests sent to drivers but not yet accepted)
            val pendingBookingsSnapshot = firestore.collection("bookings")
                .whereEqualTo("status", BookingStatus.PENDING.name)
                .get()
                .await()

            val pendingBookings = pendingBookingsSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Booking::class.java)?.copy(id = doc.id)
            }

            android.util.Log.d("DriverMatching", "📋 Found ${pendingBookings.size} pending bookings to check for driver $driverId")
            pendingBookings.forEach { booking ->
                android.util.Log.d("DriverMatching", "📋 Pending booking: ${booking.id}, vehicle: ${booking.vehicleCategory}, passenger: ${booking.passengerId}")
            }

            // Check if driver already has max accepted bookings
            val activeBookingsCount = getDriverActiveBookingsCount(driverId)
            if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
                android.util.Log.d("DriverMatching", "⏭️ Driver $driverId already has $activeBookingsCount accepted bookings (max: $MAX_ACCEPTED_RIDES_PER_DRIVER) - not sending pending requests")
                return 0
            }

            var sentRequestsCount = 0
            val remainingCapacity = MAX_ACCEPTED_RIDES_PER_DRIVER - activeBookingsCount

            for (booking in pendingBookings) {
                try {
                    // Stop if driver would exceed capacity with this booking
                    if (sentRequestsCount >= remainingCapacity) {
                        android.util.Log.d("DriverMatching", "⏭️ Driver $driverId would reach capacity - not sending more requests (sent: $sentRequestsCount, capacity remaining: $remainingCapacity)")
                        break
                    }

                    // Check if this driver matches the booking requirements
                    if (booking.vehicleCategory != driverLocation.vehicleCategory) {
                        android.util.Log.d("DriverMatching", "⏭️ Skipping booking ${booking.id}: vehicle category mismatch (need ${booking.vehicleCategory}, driver has ${driverLocation.vehicleCategory})")
                        continue
                    }

                    // Check distance from driver to pickup location (already in meters)
                    val distanceMeters = calculateDistance(
                        driverLocation.latitude, driverLocation.longitude,
                        booking.pickupLocation.coordinates.latitude, booking.pickupLocation.coordinates.longitude
                    )

                    if (distanceMeters > MAX_DRIVER_TO_PICKUP_RADIUS_METERS) {
                        android.util.Log.d("DriverMatching", "⏭️ Skipping booking ${booking.id}: too far (${String.format("%.0f", distanceMeters)}m > ${MAX_DRIVER_TO_PICKUP_RADIUS_METERS.toInt()}m)")
                        continue
                    }

                    // Check if this driver already has this request
                    val existingRequest = database.reference
                        .child(DRIVER_REQUESTS_PATH)
                        .child(driverId)
                        .orderByChild("bookingId")
                        .equalTo(booking.id)
                        .get()
                        .await()

                    if (existingRequest.exists()) {
                        android.util.Log.d("DriverMatching", "⏭️ Skipping booking ${booking.id}: driver already has this request")
                        continue
                    }

                    // CRITICAL FIX: Check if another driver currently has an active (non-expired) PENDING request
                    // This prevents sending the same booking to multiple drivers simultaneously
                    val hasActiveRequestElsewhere = checkIfBookingHasActiveRequest(booking.id, driverId)
                    if (hasActiveRequestElsewhere) {
                        android.util.Log.d("DriverMatching", "⏭️ Skipping booking ${booking.id}: another driver currently has an active request (within 30s window)")
                        continue
                    }

                    // STRICT direction filtering when driver has existing rides
                    if (activeBookingsCount > 0) {
                        // STRICT: Check destination compatibility (includes STRICT direction filtering)
                        val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                            driverId = driverId,
                            driverLat = driverLocation.latitude,
                            driverLng = driverLocation.longitude,
                            newDestination = booking.destination,
                            newDestinationCoords = Pair(booking.destination.coordinates.latitude, booking.destination.coordinates.longitude),
                            newPickupCoords = Pair(booking.pickupLocation.coordinates.latitude, booking.pickupLocation.coordinates.longitude)
                        )

                        // STRICT: Only accept if direction is compatible (NOT opposite)
                        if (!isDestinationCompatible) {
                            android.util.Log.d("DriverMatching", "⏭️ Skipping pending booking ${booking.id} for driver $driverId - OPPOSITE DIRECTION (strict filtering)")
                            continue
                        }

                        val reason = "destination/direction compatible"
                        android.util.Log.d("DriverMatching", "✅ Pending booking ${booking.id} eligible for driver $driverId - $reason")
                    }

                    // Create and send driver request
                    val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
                    val driverRequest = DriverRequest(
                        requestId = "${booking.id}_${driverId}_${uniqueTimestamp}",
                        bookingId = booking.id,
                        driverId = driverId,
                        passengerId = booking.passengerId,
                        pickupLocation = DatabaseLocation.fromBookingLocation(booking.pickupLocation),
                        destination = DatabaseLocation.fromBookingLocation(booking.destination),
                        fareEstimate = DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
                        expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                        secondChanceExpirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS + 180000L,
                        specialInstructions = booking.specialInstructions ?: "",
                        passengerDiscountPercentage = booking.passengerDiscountPercentage
                    )

                    // Send request to newly online driver
                    database.reference
                        .child(DRIVER_REQUESTS_PATH)
                        .child(driverId)
                        .child(driverRequest.requestId)
                        .setValue(driverRequest)
                        .await()

                    sentRequestsCount++
                    android.util.Log.i("DriverMatching", "✅ Sent active pending booking ${booking.id} to newly online driver $driverId (distance: ${String.format("%.0f", distanceMeters)}m)")

                    // Optional: Send notification to driver
                    try {
                        sendDriverNotification(driverId, booking)
                    } catch (e: Exception) {
                        android.util.Log.w("DriverMatching", "Failed to send notification for active pending request", e)
                    }

                } catch (e: Exception) {
                    android.util.Log.e("DriverMatching", "Error processing booking ${booking.id} for driver $driverId", e)
                }
            }

            android.util.Log.d("DriverMatching", "🏁 Sent $sentRequestsCount active pending requests to driver $driverId")
            sentRequestsCount

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to process active pending requests for driver $driverId", e)
            0
        }
    }

    /**
     * Calculate distance between two points in meters using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    /**
     * Calculate bearing (direction) from point 1 to point 2 in degrees (0-360)
     * 0° = North, 90° = East, 180° = South, 270° = West
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
        val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
                kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)

        val bearingRad = kotlin.math.atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)

        // Normalize to 0-360
        return (bearingDeg + 360) % 360
    }

    /**
     * Enhanced Turf.js-inspired functions for better route compatibility analysis
     */

    /**
     * Calculate cross-track distance (Turf.js concept) - perpendicular distance from point to line
     * More accurate than our previous implementation with better error handling
     */
    private fun crossTrackDistance(
        pointLat: Double, pointLng: Double,
        lineStartLat: Double, lineStartLng: Double,
        lineEndLat: Double, lineEndLng: Double
    ): Double {
        val distAP = calculateDistance(lineStartLat, lineStartLng, pointLat, pointLng)
        val bearingAP = calculateBearing(lineStartLat, lineStartLng, pointLat, pointLng)
        val bearingAB = calculateBearing(lineStartLat, lineStartLng, lineEndLat, lineEndLng)

        val R = 6371000.0
        val delta13 = distAP / R
        val theta13 = Math.toRadians(bearingAP)
        val theta12 = Math.toRadians(bearingAB)

        return kotlin.math.abs(kotlin.math.asin(kotlin.math.sin(delta13) * kotlin.math.sin(theta13 - theta12))) * R
    }

    /**
     * Calculate along-track distance - distance along the route from start to perpendicular projection
     */
    private fun alongTrackDistance(
        pointLat: Double, pointLng: Double,
        lineStartLat: Double, lineStartLng: Double,
        lineEndLat: Double, lineEndLng: Double
    ): Double {
        val distAP = calculateDistance(lineStartLat, lineStartLng, pointLat, pointLng)
        val bearingAP = calculateBearing(lineStartLat, lineStartLng, pointLat, pointLng)
        val bearingAB = calculateBearing(lineStartLat, lineStartLng, lineEndLat, lineEndLng)

        val R = 6371000.0
        val delta13 = distAP / R
        val theta13 = Math.toRadians(bearingAP)
        val theta12 = Math.toRadians(bearingAB)

        val deltaXT = kotlin.math.acos(kotlin.math.cos(delta13) * kotlin.math.cos(theta13 - theta12))
        return deltaXT * R
    }

    /**
     * Enhanced point-to-line distance (Turf.js concept) with better accuracy
     * Uses cross-track and along-track calculations for more precise results
     */
    private fun pointToLineDistanceEnhanced(
        pointLat: Double, pointLng: Double,
        lineStartLat: Double, lineStartLng: Double,
        lineEndLat: Double, lineEndLng: Double
    ): Double {
        val lineDistance = calculateDistance(lineStartLat, lineStartLng, lineEndLat, lineEndLng)

        // If line is very short, treat as a point
        if (lineDistance < 10.0) {
            return calculateDistance(lineStartLat, lineStartLng, pointLat, pointLng)
        }

        val crossTrack = crossTrackDistance(pointLat, pointLng, lineStartLat, lineStartLng, lineEndLat, lineEndLng)
        val alongTrack = alongTrackDistance(pointLat, pointLng, lineStartLat, lineStartLng, lineEndLat, lineEndLng)

        return when {
            alongTrack < 0 -> calculateDistance(lineStartLat, lineStartLng, pointLat, pointLng)
            alongTrack > lineDistance -> calculateDistance(lineEndLat, lineEndLng, pointLat, pointLng)
            else -> crossTrack
        }
    }

    /**
     * Check if a point is close to a route (line segment) - Enhanced with Turf.js concepts
     *
     * @param maxDistance Maximum acceptable distance in meters
     */
    private fun isPointCloseToRoute(
        pointLat: Double, pointLng: Double,
        routeStartLat: Double, routeStartLng: Double,
        routeEndLat: Double, routeEndLng: Double,
        maxDistance: Double = 1000.0 // 1km default
    ): Boolean {
        val distance = pointToLineDistanceEnhanced(pointLat, pointLng, routeStartLat, routeStartLng, routeEndLat, routeEndLng)
        return distance <= maxDistance
    }

    /**
     * Turf.js-inspired route efficiency calculator
     * Calculates if two destinations can be efficiently served on the same route
     */
    private fun calculateRouteEfficiency(
        existingPickupLat: Double, existingPickupLng: Double,
        existingDestLat: Double, existingDestLng: Double,
        newPickupLat: Double, newPickupLng: Double,
        newDestLat: Double, newDestLng: Double
    ): Double {
        // Calculate the most efficient order for serving both destinations
        // Option 1: existing -> new
        val distanceExistingToNew = calculateDistance(existingDestLat, existingDestLng, newDestLat, newDestLng)
        val combinedRoute1 = calculateDistance(existingPickupLat, existingPickupLng, existingDestLat, existingDestLng) +
                           distanceExistingToNew

        // Option 2: new -> existing
        val distanceNewToExisting = calculateDistance(newDestLat, newDestLng, existingDestLat, existingDestLng)
        val combinedRoute2 = calculateDistance(newPickupLat, newPickupLng, newDestLat, newDestLng) +
                           distanceNewToExisting

        // Choose the more efficient combined route
        val optimalCombinedRoute = kotlin.math.min(combinedRoute1, combinedRoute2)

        // Calculate separate trips (worst case scenario)
        val separateTrips = calculateDistance(existingPickupLat, existingPickupLng, existingDestLat, existingDestLng) +
                           calculateDistance(existingDestLat, existingDestLng, newPickupLat, newPickupLng) +
                           calculateDistance(newPickupLat, newPickupLng, newDestLat, newDestLng)

        // Return efficiency ratio (lower is better)
        return optimalCombinedRoute / separateTrips
    }

    /**
     * Compass sector enum for better direction matching
     */
    private enum class CompassSector {
        N,    // North: 337.5° - 22.5°
        NE,   // Northeast: 22.5° - 67.5°
        E,    // East: 67.5° - 112.5°
        SE,   // Southeast: 112.5° - 157.5°
        S,    // South: 157.5° - 202.5°
        SW,   // Southwest: 202.5° - 247.5°
        W,    // West: 247.5° - 292.5°
        NW    // Northwest: 292.5° - 337.5°
    }

    /**
     * Get compass sector for a bearing
     */
    private fun getCompassSector(bearing: Double): CompassSector {
        val normalizedBearing = (bearing + 360) % 360
        return when {
            normalizedBearing >= 337.5 || normalizedBearing < 22.5 -> CompassSector.N
            normalizedBearing >= 22.5 && normalizedBearing < 67.5 -> CompassSector.NE
            normalizedBearing >= 67.5 && normalizedBearing < 112.5 -> CompassSector.E
            normalizedBearing >= 112.5 && normalizedBearing < 157.5 -> CompassSector.SE
            normalizedBearing >= 157.5 && normalizedBearing < 202.5 -> CompassSector.S
            normalizedBearing >= 202.5 && normalizedBearing < 247.5 -> CompassSector.SW
            normalizedBearing >= 247.5 && normalizedBearing < 292.5 -> CompassSector.W
            else -> CompassSector.NW
        }
    }

    /**
     * Get directional groups for a compass sector
     * Each sector belongs to 1-2 groups (e.g., NW belongs to both Northern and Western groups)
     */
    private fun getDirectionalGroups(sector: CompassSector): Set<String> {
        return when (sector) {
            CompassSector.N -> setOf("NORTH")
            CompassSector.NE -> setOf("NORTH", "EAST")
            CompassSector.E -> setOf("EAST")
            CompassSector.SE -> setOf("SOUTH", "EAST")
            CompassSector.S -> setOf("SOUTH")
            CompassSector.SW -> setOf("SOUTH", "WEST")
            CompassSector.W -> setOf("WEST")
            CompassSector.NW -> setOf("NORTH", "WEST")
        }
    }

    /**
     * Check if compass sectors are cardinal (N, E, S, W) vs diagonal (NE, SE, SW, NW)
     */
    private fun isCardinalSector(sector: CompassSector): Boolean {
        return sector in listOf(CompassSector.N, CompassSector.E, CompassSector.S, CompassSector.W)
    }

    /**
     * Check if two compass sectors are adjacent (next to each other on the compass)
     * Adjacent sectors are generally going in similar directions even if they don't share groups
     */
    private fun areAdjacentSectors(sector1: CompassSector, sector2: CompassSector): Boolean {
        return when (sector1) {
            CompassSector.N -> sector2 in listOf(CompassSector.NE, CompassSector.NW)
            CompassSector.NE -> sector2 in listOf(CompassSector.N, CompassSector.E)
            CompassSector.E -> sector2 in listOf(CompassSector.NE, CompassSector.SE)
            CompassSector.SE -> sector2 in listOf(CompassSector.E, CompassSector.S)
            CompassSector.S -> sector2 in listOf(CompassSector.SE, CompassSector.SW)
            CompassSector.SW -> sector2 in listOf(CompassSector.S, CompassSector.W)
            CompassSector.W -> sector2 in listOf(CompassSector.SW, CompassSector.NW)
            CompassSector.NW -> sector2 in listOf(CompassSector.W, CompassSector.N)
        }
    }

    /**
     * Check if two compass sectors are compatible
     * Stricter rules to prevent false positives:
     * 1. Same sector → Always compatible
     * 2. Both diagonal sectors (NE, NW, SE, SW) → Must share a directional group
     * 3. One diagonal + one cardinal → Only compatible if the cardinal is one of the diagonal's groups AND within 45°
     * 4. Both cardinal → Must be the same (N≠E, E≠S, etc.)
     */
    private fun areSectorsCompatible(sector1: CompassSector, sector2: CompassSector, bearing1: Double, bearing2: Double): Boolean {
        if (sector1 == sector2) return true

        val isCardinal1 = isCardinalSector(sector1)
        val isCardinal2 = isCardinalSector(sector2)

        // Get directional groups
        val groups1 = getDirectionalGroups(sector1)
        val groups2 = getDirectionalGroups(sector2)
        val sharedGroups = groups1.intersect(groups2)

        // Calculate angular difference
        var diff = kotlin.math.abs(bearing1 - bearing2)
        if (diff > 180) {
            diff = 360 - diff
        }

        // NEW RULE: Adjacent sectors are always compatible if within reasonable angle
        // This handles cases where sectors don't share groups but are geographically adjacent
        // Stricter threshold (45° instead of 75°) to prevent mismatched directions
        if (areAdjacentSectors(sector1, sector2) && diff <= 45.0) {
            return true
        }

        // Rule: Both cardinal sectors must be the same (already handled above)
        if (isCardinal1 && isCardinal2) {
            return false // Different cardinal directions are never compatible
        }

        // Rule: Both diagonal sectors → Must share a group, be within 135°, AND not have opposite components
        // This allows NW (NORTH+WEST) and NE (NORTH+EAST) = both have NORTH
        // But rejects NW (NORTH+WEST) and SW (SOUTH+WEST) = share WEST but NORTH vs SOUTH are opposite
        if (!isCardinal1 && !isCardinal2) {
            if (sharedGroups.isEmpty() || diff > 135.0) {
                return false
            }

            // Additional check: Reject if one has NORTH and the other has SOUTH (vertically opposite)
            val hasNorth1 = groups1.contains("NORTH")
            val hasSouth1 = groups1.contains("SOUTH")
            val hasNorth2 = groups2.contains("NORTH")
            val hasSouth2 = groups2.contains("SOUTH")

            if ((hasNorth1 && hasSouth2) || (hasSouth1 && hasNorth2)) {
                return false // Vertically opposite directions are incompatible
            }

            return true
        }

        // Rule: One cardinal + one diagonal → Must share a directional group and be reasonable
        // The diagonal must "lean toward" the cardinal direction
        // For example: W (257°) and SW (217°) share WEST group with 40° difference → ACCEPT
        // This allows more reasonable compatibility while preventing truly opposite directions
        if (sharedGroups.isEmpty()) {
            return false
        }

        // For cardinal + diagonal: must be within 45° (stricter threshold)
        // This ensures the diagonal actually leans toward the cardinal direction
        // Prevents matching NW (298°) with W (261°) which are 37° apart but different primary directions
        return diff <= 45.0
    }

    /**
     * Check if two bearings are in compatible directions
     * Returns true if the bearings share a directional group and are within 90° of each other
     */
    private fun areDirectionsCompatible(bearing1: Double, bearing2: Double): Boolean {
        val sector1 = getCompassSector(bearing1)
        val sector2 = getCompassSector(bearing2)
        return areSectorsCompatible(sector1, sector2, bearing1, bearing2)
    }

    /**
     * Check if adding a new destination creates an efficient route
     * This considers the actual path the driver would need to take
     */
    private fun isRouteEfficient(
        driverLat: Double,
        driverLng: Double,
        newPickupCoords: Pair<Double, Double>,
        newDestinationCoords: Pair<Double, Double>,
        activeBookings: List<Booking>
    ): Boolean {
        if (activeBookings.isEmpty()) return true

        for (booking in activeBookings) {
            val existingDestLat = booking.destination.coordinates.latitude
            val existingDestLng = booking.destination.coordinates.longitude

            // Calculate bearings from driver's CURRENT location
            val bearingToExisting = calculateBearing(driverLat, driverLng, existingDestLat, existingDestLng)
            val bearingToNew = calculateBearing(driverLat, driverLng, newDestinationCoords.first, newDestinationCoords.second)

            android.util.Log.d("DestinationFiltering", "   - Bearing to existing '${booking.destination.address}': ${String.format("%.1f", bearingToExisting)}°")
            android.util.Log.d("DestinationFiltering", "   - Bearing to new destination: ${String.format("%.1f", bearingToNew)}°")

            // Calculate angle difference, handling wrap-around properly
            var diff = kotlin.math.abs(bearingToExisting - bearingToNew)
            if (diff > 180) {
                diff = 360 - diff
            }

            // Special handling for directions that are both pointing generally north/south/east/west
            // For example: 350° (slightly west of north) and 10° (slightly east of north) should be 20° apart, not 340°
            val existingGeneral = getGeneralDirection(bearingToExisting)
            val newGeneral = getGeneralDirection(bearingToNew)

            android.util.Log.d("DestinationFiltering", "   - General directions: existing='$existingGeneral', new='$newGeneral'")

            // If both directions are generally the same (NORTH, SOUTH, EAST, or WEST),
            // and the angle difference is large, they're likely on opposite sides
            if (existingGeneral == newGeneral && diff > 60) {
                // They're pointing generally the same way but on opposite sides
                // Calculate the angle across the 0°/360° boundary
                val crossBoundaryDiff = 360 - diff

                // For directions generally north, if one is ~300° and other is ~60°,
                // the actual difference should be much smaller
                val adjustedDiff = if (existingGeneral == "NORTH" || existingGeneral == "SOUTH") {
                    // For north/south, check if they're on opposite sides of the cardinal direction
                    val existingOffset = kotlin.math.abs((bearingToExisting % 90) - 45)
                    val newOffset = kotlin.math.abs((bearingToNew % 90) - 45)
                    existingOffset + newOffset
                } else {
                    // For east/west, use cross-boundary difference
                    crossBoundaryDiff
                }

                if (adjustedDiff < diff) {
                    android.util.Log.d("DestinationFiltering", "   - Adjusting angle difference from ${String.format("%.1f", diff)}° to ${String.format("%.1f", adjustedDiff)}° (same general direction)")
                    diff = adjustedDiff
                }
            }

            android.util.Log.d("DestinationFiltering", "   - Direction similarity check: ${String.format("%.1f", diff)}° apart")

            // Calculate total route distance if this destination is added
            val currentRouteToExisting = calculateDistance(driverLat, driverLng, existingDestLat, existingDestLng)
            val routeViaNewDestination = calculateDistance(driverLat, driverLng, newPickupCoords.first, newPickupCoords.second) +
                                         calculateDistance(newPickupCoords.first, newPickupCoords.second, newDestinationCoords.first, newDestinationCoords.second) +
                                         calculateDistance(newDestinationCoords.first, newDestinationCoords.second, existingDestLat, existingDestLng)

            // Calculate detour percentage
            val detourPercentage = ((routeViaNewDestination - currentRouteToExisting) / currentRouteToExisting) * 100

            android.util.Log.d("DestinationFiltering", "   - Current route to '${booking.destination.address}': ${String.format("%.0f", currentRouteToExisting)}m")
            android.util.Log.d("DestinationFiltering", "   - Route via new pickup and destination: ${String.format("%.0f", routeViaNewDestination)}m")
            android.util.Log.d("DestinationFiltering", "   - Detour: ${String.format("%.1f", detourPercentage)}%")

            // Allow if detour is reasonable (less than 50% longer) OR directions are very similar
            val isReasonableDetour = detourPercentage < 50.0
            val areDirectionsSimilar = diff < 45.0 // Increased threshold for direction similarity

            if (!isReasonableDetour && !areDirectionsSimilar) {
                android.util.Log.d("DestinationFiltering", "   - Route not efficient: ${String.format("%.1f", detourPercentage)}% detour and ${String.format("%.1f", diff)}° apart")
                return false
            }
        }

        return true
    }

    /**
     * Get the general cardinal direction (NORTH, SOUTH, EAST, WEST) from a bearing
     */
    private fun getGeneralDirection(bearing: Double): String {
        val normalizedBearing = (bearing + 360) % 360
        return when {
            normalizedBearing in 315.0..360.0 || normalizedBearing in 0.0..45.0 -> "NORTH"
            normalizedBearing in 45.0..135.0 -> "EAST"
            normalizedBearing in 135.0..225.0 -> "SOUTH"
            normalizedBearing in 225.0..315.0 -> "WEST"
            else -> "UNKNOWN"
        }
    }

    /**
     * Check if a booking currently has an active (non-expired) PENDING request with another driver
     * This prevents sending the same booking to multiple drivers simultaneously
     *
     * @param bookingId The booking to check
     * @param excludeDriverId The driver ID to exclude from the check (the driver we're considering sending to)
     * @return true if another driver has an active request, false otherwise
     */
    private suspend fun checkIfBookingHasActiveRequest(bookingId: String, excludeDriverId: String): Boolean {
        return try {
            val currentTime = System.currentTimeMillis()

            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()

            // Check each driver's requests
            for (driverSnapshot in allDriversSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue

                // Skip the driver we're checking for (exclude current driver)
                if (driverId == excludeDriverId) continue

                // Check all requests for this driver
                for (requestSnapshot in driverSnapshot.children) {
                    val request = requestSnapshot.getValue(DriverRequest::class.java) ?: continue

                    // Check if this request is for our booking
                    if (request.bookingId == bookingId) {
                        // Check if request is still active (PENDING and not expired)
                        if (request.status == DriverRequestStatus.PENDING &&
                            request.isInInitialPhase(currentTime)) {
                            android.util.Log.d("DriverMatching", "✋ Found active request for booking $bookingId with driver $driverId (expires in ${request.getTimeRemaining(currentTime)}s)")
                            return true
                        }
                    }
                }
            }

            // No active requests found
            android.util.Log.d("DriverMatching", "✅ No active requests found for booking $bookingId (excluding driver $excludeDriverId)")
            false

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "❌ Error checking for active requests for booking $bookingId", e)
            // On error, assume there IS an active request (conservative approach to avoid conflicts)
            true
        }
    }

    /**
     * Prioritize drivers with active bookings that have compatible destination boundaries
     * This ensures ride pooling efficiency by matching new rides with drivers already heading
     * to compatible destinations
     *
     * Returns list ordered by:
     * 1. Drivers with compatible active rides (sorted by rating)
     * 2. Drivers without active rides (sorted by rating)
     */
    private suspend fun prioritizeDriversWithCompatibleRides(
        drivers: List<DriverLocation>,
        newBooking: Booking
    ): List<DriverLocation> {
        return try {
            android.util.Log.d("DriverPrioritization", "🎯 Prioritizing ${drivers.size} drivers for booking destination: ${newBooking.destination.address}")

            // Determine new booking's destination boundary
            val newDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                newBooking.destination.coordinates,
                zoneBoundaryRepository
            )

            if (newDestBoundary == null) {
                android.util.Log.d("DriverPrioritization", "⚠️ New booking destination not in any boundary - no prioritization needed")
                return drivers // Return original list if no boundary
            }

            android.util.Log.d("DriverPrioritization", "📍 New booking destination boundary: $newDestBoundary")

            // Get all zone boundaries for compatibility checking
            val allBoundaries = zoneBoundaryRepository.getAllZoneBoundaries().getOrNull() ?: emptyList()
            android.util.Log.d("DriverPrioritization", "📋 Loaded ${allBoundaries.size} zone boundaries for compatibility check")

            // Categorize drivers
            val driversWithCompatibleRides = mutableListOf<DriverLocation>()
            val driversWithoutActiveRides = mutableListOf<DriverLocation>()
            val driversWithIncompatibleRides = mutableListOf<DriverLocation>()

            for (driver in drivers) {
                // Get driver's active bookings
                val activeBookingsSnapshot = firestore.collection("bookings")
                    .whereEqualTo("driverId", driver.driverId)
                    .whereIn("status", listOf(
                        BookingStatus.ACCEPTED.name,
                        BookingStatus.DRIVER_ARRIVING.name,
                        BookingStatus.DRIVER_ARRIVED.name,
                        BookingStatus.IN_PROGRESS.name
                    ))
                    .get()
                    .await()

                val activeBookings = activeBookingsSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Booking::class.java)?.copy(id = doc.id)
                }

                if (activeBookings.isEmpty()) {
                    android.util.Log.d("DriverPrioritization", "Driver ${driver.driverId}: No active bookings")
                    driversWithoutActiveRides.add(driver)
                    continue
                }

                android.util.Log.d("DriverPrioritization", "Driver ${driver.driverId}: Has ${activeBookings.size} active booking(s)")

                // Check if any active booking has compatible destination
                var hasCompatibleRide = false
                for (booking in activeBookings) {
                    val activeDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                        booking.destination.coordinates,
                        zoneBoundaryRepository
                    )

                    if (activeDestBoundary == null) {
                        android.util.Log.d("DriverPrioritization", "  Active booking ${booking.id}: No boundary - skipping")
                        continue
                    }

                    android.util.Log.d("DriverPrioritization", "  Active booking ${booking.id}: destination = $activeDestBoundary")

                    // FIRST: Check boundary compatibility settings
                    val activeBoundary = allBoundaries.find { it.name == activeDestBoundary }
                    val newBoundary = allBoundaries.find { it.name == newDestBoundary }

                    var isBoundaryCompatible = true
                    if (activeBoundary != null && newBoundary != null && activeBoundary.name != newBoundary.name) {
                        // Check if new destination is in the compatible boundaries list of the active boundary
                        isBoundaryCompatible = activeBoundary.compatibleBoundaries.contains(newBoundary.name)
                        android.util.Log.d("DriverPrioritization", "  🔗 Boundary compatibility check: $activeDestBoundary → $newDestBoundary = $isBoundaryCompatible")
                        android.util.Log.d("DriverPrioritization", "    Available compatible boundaries for $activeDestBoundary: ${activeBoundary.compatibleBoundaries}")
                    } else if (activeBoundary?.name == newBoundary?.name) {
                        android.util.Log.d("DriverPrioritization", "  🔗 Same boundary - automatically compatible")
                    }

                    // If boundaries are not compatible, skip this driver
                    if (!isBoundaryCompatible) {
                        android.util.Log.d("DriverPrioritization", "  ❌ BOUNDARIES NOT COMPATIBLE - skipping route analysis")
                        continue
                    }

                    // SECOND: ALWAYS check if routes overlap using Turf.js concepts
                    // Even if same boundary, destinations might be in opposite directions!
                    android.util.Log.d("DriverPrioritization", "  🔍 Checking route overlap (boundary: $activeDestBoundary vs $newDestBoundary)...")

                    // Get route coordinates
                    val existingPickupLat = booking.pickupLocation.coordinates.latitude
                    val existingPickupLng = booking.pickupLocation.coordinates.longitude
                    val existingDestLat = booking.destination.coordinates.latitude
                    val existingDestLng = booking.destination.coordinates.longitude

                    val newPickupLat = newBooking.pickupLocation.coordinates.latitude
                    val newPickupLng = newBooking.pickupLocation.coordinates.longitude
                    val newDestLat = newBooking.destination.coordinates.latitude
                    val newDestLng = newBooking.destination.coordinates.longitude

                    // BEARING-BASED DIRECTION CHECK ONLY
                    // Compare where the driver is heading vs where the passenger wants to go
                    // From the new passenger's pickup point, check if they want to go in the same direction as the driver
                    val driverDirectionFromNewPickup = calculateBearing(newPickupLat, newPickupLng, existingDestLat, existingDestLng)
                    val passengerDirection = calculateBearing(newPickupLat, newPickupLng, newDestLat, newDestLng)
                    val bearingDiff = kotlin.math.abs(driverDirectionFromNewPickup - passengerDirection).let {
                        if (it > 180) 360 - it else it
                    }
                    val isSimilarDirection = bearingDiff <= 45.0  // Only accept if within 45° - same general direction

                    // Final compatibility: ONLY bearing-based direction matching
                    val routesOverlap = isSimilarDirection

                    android.util.Log.d("DriverPrioritization", "  📊 Route compatibility details (BEARING-BASED ONLY):")
                    android.util.Log.d("DriverPrioritization", "    - Driver bearing from new pickup: ${String.format("%.1f", driverDirectionFromNewPickup)}°")
                    android.util.Log.d("DriverPrioritization", "    - Passenger bearing from pickup: ${String.format("%.1f", passengerDirection)}°")
                    android.util.Log.d("DriverPrioritization", "    - Bearing difference: ${String.format("%.1f", bearingDiff)}°")
                    android.util.Log.d("DriverPrioritization", "    - Similar direction (≤45°): $isSimilarDirection")
                    android.util.Log.d("DriverPrioritization", "    - Final result: $routesOverlap")

                    if (routesOverlap) {
                        android.util.Log.d("DriverPrioritization", "  ✅ BOUNDARY COMPATIBLE + SAME DIRECTION - bearing match!")
                        hasCompatibleRide = true
                        break
                    }
                    // Different boundary and no route overlap = not compatible
                }

                if (hasCompatibleRide) {
                    android.util.Log.d("DriverPrioritization", "Driver ${driver.driverId}: ⭐ COMPATIBLE - will be PRIORITIZED")
                    driversWithCompatibleRides.add(driver)
                } else {
                    android.util.Log.d("DriverPrioritization", "Driver ${driver.driverId}: ❌ No compatible active rides")
                    driversWithIncompatibleRides.add(driver)
                }
            }

            // Build filtered list: ONLY compatible + no-active-rides drivers
            // EXCLUDE incompatible drivers entirely (they should NOT receive this request)
            val filteredList = driversWithCompatibleRides + driversWithoutActiveRides

            android.util.Log.d("DriverPrioritization", "📊 Filtering complete (boundary + BEARING-BASED route compatibility):")
            android.util.Log.d("DriverPrioritization", "  - ${driversWithCompatibleRides.size} drivers with COMPATIBLE active rides (boundary + bearing)")
            android.util.Log.d("DriverPrioritization", "  - ${driversWithoutActiveRides.size} drivers with NO active rides")
            android.util.Log.d("DriverPrioritization", "  - ${driversWithIncompatibleRides.size} drivers with INCOMPATIBLE active rides (FILTERED OUT - bearing criteria)")

            if (driversWithCompatibleRides.isNotEmpty()) {
                android.util.Log.d("DriverPrioritization", "🎯 TOP PRIORITY: ${driversWithCompatibleRides.first().driverId} (has compatible active ride)")
            }

            if (filteredList.isEmpty()) {
                android.util.Log.d("DriverPrioritization", "⚠️ No compatible drivers available - all drivers have incompatible active rides")
            }

            filteredList

        } catch (e: Exception) {
            android.util.Log.e("DriverPrioritization", "❌ Error prioritizing drivers", e)
            // On error, return original list
            drivers
        }
    }

    /**
     * Check if a new ride is compatible with driver's existing rides
     * STRICT DIRECTION FILTERING:
     * - FIRST checks if direction is opposite → if yes, REJECT immediately (even if close)
     * - ONLY if direction is OK → then check proximity/other conditions
     *
     * This ensures drivers NEVER get rides going in opposite directions
     */
    // Compatibility checking using admin-configured boundary compatibility matrix
    private suspend fun isDestinationCompatibleWithExistingRides(
        driverId: String,
        driverLat: Double,
        driverLng: Double,
        newDestination: BookingLocation,
        newDestinationCoords: Pair<Double, Double>,
        newPickupCoords: Pair<Double, Double>
    ): Boolean {
        return try {
            android.util.Log.d("CompatibilityCheck", "🎯 Checking ride compatibility for driver $driverId")

            // Get driver's active bookings
            val activeBookingsSnapshot = firestore.collection("bookings")
                .whereEqualTo("driverId", driverId)
                .whereIn("status", listOf(
                    BookingStatus.ACCEPTED.name,
                    BookingStatus.DRIVER_ARRIVING.name,
                    BookingStatus.DRIVER_ARRIVED.name,
                    BookingStatus.IN_PROGRESS.name
                ))
                .get()
                .await()

            val activeBookings = activeBookingsSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Booking::class.java)?.copy(id = doc.id)
            }

            android.util.Log.d("CompatibilityCheck", "📊 Driver has ${activeBookings.size} active bookings")

            if (activeBookings.isEmpty()) {
                android.util.Log.d("CompatibilityCheck", "✅ No active bookings - automatically compatible")
                return true
            }

            // Check compatibility using boundary configuration
            val newDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                GeoPoint(newDestinationCoords.first, newDestinationCoords.second),
                zoneBoundaryRepository
            )

            if (newDestBoundary == null) {
                android.util.Log.d("CompatibilityCheck", "⚠️ New destination not in any boundary - defaulting to compatible")
                return true
            }

            android.util.Log.d("CompatibilityCheck", "🗺️ New destination boundary: $newDestBoundary")

            // Check each active booking
            for (booking in activeBookings) {
                val existingDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                    booking.destination.coordinates,
                    zoneBoundaryRepository
                )

                if (existingDestBoundary == null) {
                    android.util.Log.d("CompatibilityCheck", "⚠️ Existing booking destination not in any boundary - allowing")
                    continue
                }

                android.util.Log.d("CompatibilityCheck", "🗺️ Existing booking (${booking.id}) destination boundary: $existingDestBoundary")

                // ALWAYS check if routes overlap using Turf.js concepts
                // Even if same boundary, destinations might be in opposite directions!
                android.util.Log.d("CompatibilityCheck", "🔍 Checking route overlap (boundary: $existingDestBoundary vs $newDestBoundary)...")

                // Get existing booking's route (pickup → destination)
                val existingPickupLat = booking.pickupLocation.coordinates.latitude
                val existingPickupLng = booking.pickupLocation.coordinates.longitude
                val existingDestLat = booking.destination.coordinates.latitude
                val existingDestLng = booking.destination.coordinates.longitude

                // Get new booking's coordinates
                val newPickupLat = newPickupCoords.first
                val newPickupLng = newPickupCoords.second
                val newDestLat = newDestinationCoords.first
                val newDestLng = newDestinationCoords.second

                // BEARING-BASED DIRECTION CHECK ONLY
                // Compare where the driver is heading vs where the passenger wants to go
                // From the new passenger's pickup point, check if they want to go in the same direction as the driver
                val driverDirectionFromNewPickup = calculateBearing(newPickupLat, newPickupLng, existingDestLat, existingDestLng)
                val passengerDirection = calculateBearing(newPickupLat, newPickupLng, newDestLat, newDestLng)
                val bearingDifference = kotlin.math.abs(driverDirectionFromNewPickup - passengerDirection).let {
                    if (it > 180) 360 - it else it
                }
                val isSimilarDirection = bearingDifference <= 45.0 // Only accept if within 45° - same general direction

                android.util.Log.d("CompatibilityCheck", "  📊 Route compatibility details (BEARING-BASED ONLY):")
                android.util.Log.d("CompatibilityCheck", "    - Driver bearing from new pickup: ${String.format("%.1f", driverDirectionFromNewPickup)}°")
                android.util.Log.d("CompatibilityCheck", "    - Passenger bearing from pickup: ${String.format("%.1f", passengerDirection)}°")
                android.util.Log.d("CompatibilityCheck", "    - Bearing difference: ${String.format("%.1f", bearingDifference)}°")
                android.util.Log.d("CompatibilityCheck", "    - Similar direction (≤45°): $isSimilarDirection")

                // Routes overlap if bearings are similar (same direction)
                val routesOverlap = isSimilarDirection

                if (routesOverlap) {
                    android.util.Log.d("CompatibilityCheck", "✅ SAME DIRECTION - bearing match!")
                    return true
                } else {
                    android.util.Log.d("CompatibilityCheck", "❌ DIFFERENT DIRECTION - bearing mismatch")
                    return false
                }
            }

            // If we reach here, all bookings passed (should not happen with the logic above)
            android.util.Log.d("CompatibilityCheck", "✅ All bookings checked - compatible")
            return true

        } catch (e: Exception) {
            android.util.Log.e("CompatibilityCheck", "❌ Error checking compatibility", e)
            // On error, default to compatible to avoid blocking rides unexpectedly
            return true
        }
    }

    /**
     * Monitor a booking for acceptance and auto-remove requests from other drivers
     */
    private fun monitorBookingAcceptance(bookingId: String) {
        // Listen for booking status changes
        database.reference
            .child("bookings")  // Assuming bookings are stored here
            .child(bookingId)
            .child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java)
                    if (status == BookingStatus.ACCEPTED.name) {
                        // Booking was accepted by a driver, remove requests from all other drivers
                        GlobalScope.launch {
                            removeBookingRequestsFromAllDrivers(bookingId)
                        }
                        // Remove the listener since we only need to monitor once
                        snapshot.ref.removeEventListener(this)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("DriverMatching", "Failed to monitor booking acceptance", error.toException())
                }
            })
    }
    
    /**
     * Remove requests for a specific booking from all drivers when another driver accepts
     */
    private suspend fun removeBookingRequestsFromAllDrivers(bookingId: String) {
        try {
            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()
            
            for (driverSnapshot in allDriversSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue
                
                // Check all requests for this driver
                driverSnapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId && 
                        (request.status == DriverRequestStatus.PENDING || 
                         request.status == DriverRequestStatus.SECOND_CHANCE)) {
                        
                        // Mark as accepted by another driver
                        requestSnapshot.ref.child("status")
                            .setValue(DriverRequestStatus.ACCEPTED_BY_OTHER.name)
                        
                        android.util.Log.d("DriverMatching", "Removed booking $bookingId request from driver $driverId (accepted by another)")
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to remove booking requests from other drivers", e)
        }
    }
    
    /**
     * Cancel all driver requests when passenger cancels booking
     * This ensures requests immediately disappear from driver UIs
     */
    suspend fun cancelBookingRequestsForAllDrivers(bookingId: String): Result<Unit> {
        return try {
            android.util.Log.d("DriverMatching", "Cancelling all driver requests for booking: $bookingId")
            
            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()
            
            var cancelledCount = 0
            
            for (driverSnapshot in allDriversSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue
                
                // Check all requests for this driver
                driverSnapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId && 
                        (request.status == DriverRequestStatus.PENDING || 
                         request.status == DriverRequestStatus.SECOND_CHANCE)) {
                        
                        // Mark as cancelled (passenger cancelled)
                        requestSnapshot.ref.child("status")
                            .setValue(DriverRequestStatus.CANCELLED.name)
                        
                        cancelledCount++
                        android.util.Log.d("DriverMatching", "Cancelled request ${request.requestId} for driver $driverId (passenger cancelled booking)")
                    }
                }
            }
            
            android.util.Log.d("DriverMatching", "Successfully cancelled $cancelledCount driver requests for booking $bookingId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to cancel booking requests for all drivers", e)
            Result.failure(e)
        }
    }
}
