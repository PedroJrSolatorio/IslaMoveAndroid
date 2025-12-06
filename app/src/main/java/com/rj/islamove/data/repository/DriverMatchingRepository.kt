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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val estimatedDistance: Double = 0.0,
    val passengerFare: Double = 0.0,
    val companionFares: List<CompanionFare> = emptyList(),
    val fareBreakdown: String = ""
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
        estimatedDistance = estimatedDistance,
        passengerFare = passengerFare,
        companionFares = companionFares,
        fareBreakdown = fareBreakdown
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
            estimatedDistance = estimate.estimatedDistance,
            passengerFare = estimate.passengerFare,
            companionFares = estimate.companionFares,
            fareBreakdown = estimate.fareBreakdown
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
    val status: DriverRequestStatus = DriverRequestStatus.PENDING,
    val specialInstructions: String = "", // Passenger comment for identification
    val passengerDiscountPercentage: Int? = null, // null = no discount, 20, 50, etc.
    val companions: List<Companion> = emptyList(),
    val totalPassengers: Int = 1
) {
    // Simplified helper functions - no second chance
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime >= expirationTime || status == DriverRequestStatus.EXPIRED
    }

    fun getTimeRemaining(currentTime: Long = System.currentTimeMillis()): Long {
        return if (currentTime < expirationTime && status == DriverRequestStatus.PENDING) {
            (expirationTime - currentTime) / 1000
        } else {
            0
        }
    }

    fun getPhase(currentTime: Long = System.currentTimeMillis()): RequestPhase {
        return if (currentTime < expirationTime && status == DriverRequestStatus.PENDING) {
            RequestPhase.ACTIVE
        } else {
            RequestPhase.EXPIRED
        }
    }
}

enum class RequestPhase {
    ACTIVE,      // First 30 seconds
    EXPIRED       // After 30 seconds or declined
}

enum class DriverRequestStatus {
    PENDING,
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
        private const val GLOBAL_BOOKING_TIMEOUT_MS = 60000L // 1 minute total for entire booking
        private const val MAX_ACCEPTED_RIDES_PER_DRIVER = 5 // Maximum number of accepted rides a driver can have

        // Distance thresholds in meters
        private const val MAX_DRIVER_TO_PICKUP_RADIUS_METERS = 250.0 // Maximum radius for drivers to receive ride requests (250m)
    }

    /**
     * Auto-cleanup stuck bookings for a driver
     * Cancels bookings stuck in DRIVER_ARRIVING/DRIVER_ARRIVED for more than 15 minutes
     */
    suspend fun cleanupStuckBookings(driverId: String): Result<Int> {
        return try {
//            android.util.Log.d("DriverMatching", "Starting stuck booking cleanup for driver $driverId...")

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

//                android.util.Log.d("DriverMatching", "   - Checking booking $bookingId: status=$status, stuckFor=${minutesSinceUpdate}min")

                // Cancel if stuck for more than 15 minutes
                if (timeSinceUpdate > timeoutThreshold) {
//                    android.util.Log.w("DriverMatching", "Cancelling stuck booking $bookingId (stuck for ${minutesSinceUpdate}min)")

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
//                    android.util.Log.d("DriverMatching", "Successfully cancelled stuck booking $bookingId")
                }
            }

//            android.util.Log.d("DriverMatching", "Cleanup completed: Cancelled $cleanedCount stuck bookings for driver $driverId")
            Result.success(cleanedCount)

        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Failed to cleanup stuck bookings for driver $driverId", e)
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
//            android.util.Log.d("DriverMatching", "Driver $driverId has $count active accepted bookings:")

            // Log each active booking for debugging
            bookingsSnapshot.documents.forEach { doc ->
                val status = doc.getString("status")
                val bookingId = doc.id
//                android.util.Log.d("DriverMatching", "   - Booking $bookingId with status $status")
            }

            count
        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Failed to get active bookings count for driver $driverId", e)
            0 // Return 0 on error to avoid blocking driver from receiving requests
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
                
//                android.util.Log.d("DriverMatching", "Assigning driver to booking: '$actualBookingId'")
                
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
                        // Skip CANCELLED requests immediately
                        if (request.status == DriverRequestStatus.CANCELLED) {
                            requestSnapshot.ref.removeValue()
                            android.util.Log.d("DriverMatching", "Auto-removed cancelled request ${request.requestId}")
                            return@forEach
                        }

                        // Skip EXPIRED status requests
                        if (request.status == DriverRequestStatus.EXPIRED) {
                            android.util.Log.d("DriverMatching", "Filtering out EXPIRED request ${request.requestId}")
                            return@forEach
                        }

                        // Auto-cleanup expired requests (older than 2 minutes)
                        if (request.isExpired(currentTime) &&
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

                        // Only include recent requests (not older than 1 hour)
                        val isRecentRequest = (currentTime - request.requestTime) <= 3600000 // 1 hour
                        if (!isRecentRequest) {
                            return@forEach
                        }

                        // ONLY show ACTIVE requests (within 30 seconds) with PENDING status
                        if (!request.isExpired(currentTime) &&
                            request.status == DriverRequestStatus.PENDING) {
                            requests.add(request)
                            android.util.Log.d("DriverMatching", "Including ACTIVE request: ${request.requestId}")
                        } else {
                            android.util.Log.d("DriverMatching", "Filtering out request: ${request.requestId}, phase: ${request.getPhase(currentTime)}, status: ${request.status}")
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

    suspend fun declineRequest(requestId: String, driverId: String): Result<Unit> = withContext(
        Dispatchers.IO) {
        try {
            val requestRef = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)

            // Update status to DECLINED
            requestRef.child("status").setValue(DriverRequestStatus.DECLINED.name).await()

            // Auto-remove declined requests after 5 seconds (cleanup)
            delay(5000)
            requestRef.removeValue().await()

            android.util.Log.d("DriverMatching", "Request $requestId declined and removed")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to decline request", e)
            Result.failure(e)
        }
    }

    /**
     * Find and notify available drivers for a new booking
     * @param booking The booking to find drivers for
     * @param maxDrivers Maximum number of drivers to notify (default 10)
     */
    suspend fun findAndNotifyDrivers(
        booking: Booking,
        maxDrivers: Int = 10
    ): Result<Int> {
        return try {
            android.util.Log.d("DriverMatching", "🔍 Finding drivers for booking ${booking.id}")

            // Get nearby drivers
            val nearbyDriversResult = driverRepository.getNearbyDrivers(
                centerLat = booking.pickupLocation.coordinates.latitude,
                centerLng = booking.pickupLocation.coordinates.longitude,
                radiusKm = 10.0
            )

            if (nearbyDriversResult.isFailure) {
                android.util.Log.e("DriverMatching", "Failed to get nearby drivers")
                return Result.failure(nearbyDriversResult.exceptionOrNull() ?: Exception("Failed to get drivers"))
            }

            val nearbyDrivers = nearbyDriversResult.getOrNull() ?: emptyList()

            // Filter drivers by vehicle category, online status, distance, and capacity
            val availableDrivers = mutableListOf<DriverLocation>()
            for (driver in nearbyDrivers) {
                if (driver.vehicleCategory != booking.vehicleCategory || !driver.online) {
                    continue
                }

                val distanceToPickupMeters = calculateDistance(
                    driver.latitude, driver.longitude,
                    booking.pickupLocation.coordinates.latitude,
                    booking.pickupLocation.coordinates.longitude
                )

                if (distanceToPickupMeters > MAX_DRIVER_TO_PICKUP_RADIUS_METERS) {
                    continue
                }

                val activeBookingsCount = getDriverActiveBookingsCount(driver.driverId)
                if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
                    continue
                }

                if (activeBookingsCount > 0) {
                    val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                        driverId = driver.driverId,
                        driverLat = driver.latitude,
                        driverLng = driver.longitude,
                        newDestination = booking.destination,
                        newDestinationCoords = Pair(
                            booking.destination.coordinates.latitude,
                            booking.destination.coordinates.longitude
                        ),
                        newPickupCoords = Pair(
                            booking.pickupLocation.coordinates.latitude,
                            booking.pickupLocation.coordinates.longitude
                        )
                    )

                    if (!isDestinationCompatible) {
                        continue
                    }
                }

                availableDrivers.add(driver)

                if (availableDrivers.size >= maxDrivers) {
                    break
                }
            }

            if (availableDrivers.isEmpty()) {
                android.util.Log.w("DriverMatching", "No available drivers found for booking ${booking.id}")
                bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                return Result.success(0)
            }

            android.util.Log.i("DriverMatching", "Found ${availableDrivers.size} available drivers")

            // NOW start global timeout - we have drivers to try
            startGlobalBookingTimeout(booking.id)

            // Send request to highest-rated driver first
            val topDriver = availableDrivers.first()
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
                passengerDiscountPercentage = booking.passengerDiscountPercentage,
                companions = booking.companions,
                totalPassengers = booking.totalPassengers
            )

            database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(topDriver.driverId)
                .child(driverRequest.requestId)
                .setValue(driverRequest)
                .await()

            sendDriverNotification(topDriver.driverId, booking)

            // Start monitoring with remaining drivers as backup
            startRequestMonitoring(
                bookingId = booking.id,
                driverIds = listOf(topDriver.driverId),
                booking = booking,
                attemptNumber = 1,
                remainingDrivers = availableDrivers.drop(1),
                bookingStartTime = System.currentTimeMillis()
            )

            android.util.Log.i("DriverMatching", "Successfully notified driver ${topDriver.driverId}")
            Result.success(1)

        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Error finding drivers", e)
            Result.failure(e)
        }
    }

    private fun startGlobalBookingTimeout(bookingId: String) {
        kotlinx.coroutines.GlobalScope.launch {
            val startTime = System.currentTimeMillis()
            android.util.Log.d("DriverMatching", "⏰ Started global 1-minute timeout for booking $bookingId")

            // Wait for 1 minute
            delay(GLOBAL_BOOKING_TIMEOUT_MS)

            // Check if booking was accepted
            val responseSnapshot = database.reference
                .child(DRIVER_RESPONSES_PATH)
                .child(bookingId)
                .get()
                .await()

            if (!responseSnapshot.exists()) {
                // No driver accepted within 1 minute - expire the booking
                android.util.Log.w("DriverMatching", "⏰ GLOBAL TIMEOUT: No driver accepted booking $bookingId within 1 minute")

                // Get current booking status
                val bookingResult = bookingRepository.getBooking(bookingId)
                bookingResult.fold(
                    onSuccess = { booking ->
                        if (booking != null && booking.status == BookingStatus.PENDING) {
                            // Cancel all pending driver requests
                            cancelAllDriverRequestsForBooking(bookingId)

                            // Update booking status to EXPIRED
                            bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)

//                            android.util.Log.i("DriverMatching", "✅ Booking $bookingId marked as EXPIRED after 1-minute timeout")
                        } else {
                            android.util.Log.d("DriverMatching", "Booking $bookingId already processed (status: ${booking?.status})")
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("DriverMatching", "Failed to get booking $bookingId for timeout check", error)
                    }
                )
            } else {
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
//                android.util.Log.d("DriverMatching", "✅ Booking $bookingId was accepted within ${elapsedTime}s (before 1-minute timeout)")
            }
        }
    }

    private suspend fun cancelAllDriverRequestsForBooking(bookingId: String) {
        try {
            android.util.Log.d("DriverMatching", "🧹 Cancelling all driver requests for booking $bookingId")

            // Get all driver requests for this booking
            val requestsSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()

            var cancelledCount = 0
            requestsSnapshot.children.forEach { driverSnapshot ->
                driverSnapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId) {
                        // Remove the request
                        requestSnapshot.ref.removeValue().await()
                        cancelledCount++
                        android.util.Log.d("DriverMatching", "   Cancelled request for driver ${request.driverId}")
                    }
                }
            }

//            android.util.Log.d("DriverMatching", "✅ Cancelled $cancelledCount driver requests for booking $bookingId")
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to cancel driver requests for booking $bookingId", e)
        }
    }
    
    /**
     * Send FCM notification to driver using NotificationService
     */
    private suspend fun sendDriverNotification(driverId: String, booking: Booking) {
        try {
//            android.util.Log.d("DriverMatching", "Sending FCM notification to driver: $driverId")
            
            // Use the proper NotificationService to send FCM notifications
            val notificationResult = notificationService.sendRideRequestToDrivers(booking, listOf(driverId))
            
            if (notificationResult.isSuccess) {
//                android.util.Log.d("DriverMatching", "Successfully queued FCM notification for driver: $driverId")
            } else {
//                android.util.Log.w("DriverMatching", "Failed to queue FCM notification for driver $driverId: ${notificationResult.exceptionOrNull()?.message}")
                
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
                
//                android.util.Log.d("DriverMatching", "Stored notification fallback in database for driver: $driverId")
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
        remainingDrivers: List<DriverLocation> = emptyList(),
        bookingStartTime: Long = System.currentTimeMillis()
    ) {
        kotlinx.coroutines.GlobalScope.launch {
            val driverId = driverIds.firstOrNull()

//            android.util.Log.d("DriverMatching", "⏰ Monitoring request for driver $driverId (attempt $attemptNumber)")

            // Flag to track if we should continue waiting
            var shouldContinueWaiting = true

            // Set up listener for IMMEDIATE decline detection
            val declineListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
//                        android.util.Log.i("DriverMatching", "🚫 Driver $driverId DECLINED immediately - moving to next driver NOW")

                        // Stop waiting for the 30-second timeout
                        shouldContinueWaiting = false

                        // Clean up
                        snapshot.ref.removeEventListener(this)
                        snapshot.ref.removeValue()

                        kotlinx.coroutines.GlobalScope.launch {
                            // Check if we're still within 1-minute window
                            val elapsedTime = System.currentTimeMillis() - bookingStartTime
                            if (elapsedTime >= GLOBAL_BOOKING_TIMEOUT_MS) {
//                                android.util.Log.w("DriverMatching", "⏰ Global timeout reached - not reassigning")
                                bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
                                cancelAllDriverRequestsForBooking(bookingId)
                                return@launch
                            }

                            // IMMEDIATELY move to next driver
                            handleDriverDeclineOrTimeout(
                                bookingId = bookingId,
                                driverId = driverId,
                                booking = booking,
                                attemptNumber = attemptNumber,
                                remainingDrivers = remainingDrivers,
                                wasDeclined = true,
                                bookingStartTime = bookingStartTime
                            )
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("DriverMatching", "Error listening for decline", error.toException())
                }
            }

            if (driverId != null) {
                val declineRef = database.reference
                    .child("driver_declines")
                    .child(bookingId)
                    .child(driverId)

                declineRef.addValueEventListener(declineListener)
            }

            // Wait for 30 seconds OR until driver declines (whichever comes first)
//            android.util.Log.d("DriverMatching", "Waiting up to 30 seconds for driver $driverId to respond...")

            // Check every 500ms if driver declined (shouldContinueWaiting flag)
            val startTime = System.currentTimeMillis()
            while (shouldContinueWaiting && (System.currentTimeMillis() - startTime) < REQUEST_TIMEOUT_MS) {
                delay(500) // Check every half second

                // Also check if driver accepted during this time
                val responseSnapshot = database.reference
                    .child(DRIVER_RESPONSES_PATH)
                    .child(bookingId)
                    .get()
                    .await()

                if (responseSnapshot.exists()) {
//                    android.util.Log.d("DriverMatching", "✅ Driver $driverId ACCEPTED during monitoring")
                    // Clean up decline listener
                    if (driverId != null) {
                        database.reference
                            .child("driver_declines")
                            .child(bookingId)
                            .child(driverId)
                            .removeEventListener(declineListener)
                    }
                    return@launch
                }
            }

            // If we exit the loop because driver declined, the decline listener already handled it
            if (!shouldContinueWaiting) {
//                android.util.Log.d("DriverMatching", "Skipped 30s wait due to immediate decline")
                return@launch
            }

//            android.util.Log.d("DriverMatching", "30 seconds elapsed for driver $driverId (no response)")

            // Remove decline listener
            if (driverId != null) {
                database.reference
                    .child("driver_declines")
                    .child(bookingId)
                    .child(driverId)
                    .removeEventListener(declineListener)
            }

            // Check if driver accepted (final check after 30s)
            val responseSnapshot = database.reference
                .child(DRIVER_RESPONSES_PATH)
                .child(bookingId)
                .get()
                .await()

            if (!responseSnapshot.exists()) {
//                android.util.Log.i("DriverMatching", "Driver $driverId TIMED OUT (no response in 30s)")

                // Check if we're still within 1-minute window
                val elapsedTime = System.currentTimeMillis() - bookingStartTime
//                android.util.Log.d("DriverMatching", "Elapsed time: ${elapsedTime}ms / ${GLOBAL_BOOKING_TIMEOUT_MS}ms")

                if (elapsedTime >= GLOBAL_BOOKING_TIMEOUT_MS) {
//                    android.util.Log.w("DriverMatching", "Global 1-minute timeout reached - booking expired")
                    bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
                    cancelAllDriverRequestsForBooking(bookingId)
                    return@launch
                }

                // Move to next driver after timeout
                if (driverId != null) {
                    handleDriverDeclineOrTimeout(
                        bookingId = bookingId,
                        driverId = driverId,
                        booking = booking,
                        attemptNumber = attemptNumber,
                        remainingDrivers = remainingDrivers,
                        wasDeclined = false, // This was a timeout, not a decline
                        bookingStartTime = bookingStartTime
                    )
                }
            } else {
                val elapsedTime = System.currentTimeMillis() - bookingStartTime
                android.util.Log.d("DriverMatching", "Driver $driverId accepted booking $bookingId (after ${elapsedTime}ms)")
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
        wasDeclined: Boolean,
        bookingStartTime: Long = System.currentTimeMillis()
    ) {
        // Check global timeout first
        val elapsedTime = System.currentTimeMillis() - bookingStartTime
        if (elapsedTime >= GLOBAL_BOOKING_TIMEOUT_MS) {
//            android.util.Log.w("DriverMatching", "Global timeout reached - booking expired")
            bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
            cancelAllDriverRequestsForBooking(bookingId)
            return
        }

        val remainingTime = GLOBAL_BOOKING_TIMEOUT_MS - elapsedTime
//        android.util.Log.d("DriverMatching", "Time remaining: ${remainingTime}ms")

        // FIXED: Only remove request from THIS specific driver (not all drivers)
        if (driverId != null) {
            try {
                val driverRequests = database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(driverId)
                    .get()
                    .await()

                driverRequests.children.forEach { snapshot ->
                    val request = snapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId) {
                        snapshot.ref.removeValue().await()
//                        android.util.Log.d("DriverMatching", "Removed request ${request.requestId} from driver $driverId")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverMatching", "Failed to cleanup request for driver $driverId", e)
            }
        }

        // Try next driver from the remaining list
        if (remainingDrivers.isNotEmpty()) {
            val nextDriver = remainingDrivers.first()
//            android.util.Log.i("DriverMatching", "Moving to next driver: ${nextDriver.driverId} (${remainingDrivers.size} remaining)")

            try {
                val activeBookingsCount = getDriverActiveBookingsCount(nextDriver.driverId)

                if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
//                    android.util.Log.w("DriverMatching", "Driver ${nextDriver.driverId} at capacity - skipping")
                    handleDriverDeclineOrTimeout(
                        bookingId, null, booking, attemptNumber,
                        remainingDrivers.drop(1), wasDeclined, bookingStartTime
                    )
                    return
                }

                if (activeBookingsCount > 0) {
                    val isDestinationCompatible = isDestinationCompatibleWithExistingRides(
                        driverId = nextDriver.driverId,
                        driverLat = nextDriver.latitude,
                        driverLng = nextDriver.longitude,
                        newDestination = booking.destination,
                        newDestinationCoords = Pair(
                            booking.destination.coordinates.latitude,
                            booking.destination.coordinates.longitude
                        ),
                        newPickupCoords = Pair(
                            booking.pickupLocation.coordinates.latitude,
                            booking.pickupLocation.coordinates.longitude
                        )
                    )

                    if (!isDestinationCompatible) {
//                        android.util.Log.w("DriverMatching", "Driver ${nextDriver.driverId} - opposite direction - skipping")
                        handleDriverDeclineOrTimeout(
                            bookingId, null, booking, attemptNumber,
                            remainingDrivers.drop(1), wasDeclined, bookingStartTime
                        )
                        return
                    }
                }

                // Check if this driver already has this request (avoid duplicates)
                val existingRequests = database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(nextDriver.driverId)
                    .get()
                    .await()

                var hasRequest = false
                existingRequests.children.forEach { snapshot ->
                    val request = snapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId) {
                        hasRequest = true
                        return@forEach
                    }
                }

                if (hasRequest) {
//                    android.util.Log.w("DriverMatching", "Driver ${nextDriver.driverId} already has this request - skipping")
                    handleDriverDeclineOrTimeout(
                        bookingId, null, booking, attemptNumber,
                        remainingDrivers.drop(1), wasDeclined, bookingStartTime
                    )
                    return
                }

//                android.util.Log.i("DriverMatching", "Sending request to driver ${nextDriver.driverId}")
                sendRequestToDriver(nextDriver, booking, bookingStartTime, attemptNumber, remainingDrivers.drop(1))

            } catch (e: Exception) {
//                android.util.Log.e("DriverMatching", "Failed to send to next driver", e)
                handleDriverDeclineOrTimeout(
                    bookingId, null, booking, attemptNumber,
                    remainingDrivers.drop(1), wasDeclined, bookingStartTime
                )
            }
        } else {
            // NO MORE DRIVERS IN INITIAL LIST - keep searching
//            android.util.Log.w("DriverMatching", "Initial driver list exhausted, searching for new drivers...")

            if (remainingTime > 5000) {
                searchForNewDrivers(booking, attemptNumber, bookingStartTime)
            } else {
//                android.util.Log.w("DriverMatching", "Insufficient time - booking expired")
                bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED)
                cancelAllDriverRequestsForBooking(bookingId)
            }
        }
    }

    private suspend fun searchForNewDrivers(
        booking: Booking,
        attemptNumber: Int,
        bookingStartTime: Long
    ) {
        try {
            val elapsedTime = System.currentTimeMillis() - bookingStartTime
            val remainingTime = GLOBAL_BOOKING_TIMEOUT_MS - elapsedTime

//            android.util.Log.d("DriverMatching", "Searching for new drivers (attempt ${attemptNumber + 1})")

            val notifiedDrivers = getNotifiedDriversForBooking(booking.id)

            val searchRadius = 10.0 + (attemptNumber * 5.0)
            val nearbyDriversResult = driverRepository.getNearbyDrivers(
                centerLat = booking.pickupLocation.coordinates.latitude,
                centerLng = booking.pickupLocation.coordinates.longitude,
                radiusKm = searchRadius
            )

            if (nearbyDriversResult.isFailure) {
//                android.util.Log.e("DriverMatching", "Failed to search for new drivers")
                if (remainingTime > 10000) {
                    delay(5000)
                    searchForNewDrivers(booking, attemptNumber + 1, bookingStartTime)
                } else {
                    bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                    cancelAllDriverRequestsForBooking(booking.id)
                }
                return
            }

            val allDrivers = nearbyDriversResult.getOrNull() ?: emptyList()

            val newDrivers = allDrivers.filter { driver ->
                !notifiedDrivers.contains(driver.driverId) &&
                        driver.vehicleCategory == booking.vehicleCategory &&
                        driver.online &&
                        calculateDistance(
                            driver.latitude, driver.longitude,
                            booking.pickupLocation.coordinates.latitude,
                            booking.pickupLocation.coordinates.longitude
                        ) <= MAX_DRIVER_TO_PICKUP_RADIUS_METERS
            }

            val availableNewDrivers = mutableListOf<DriverLocation>()
            for (driver in newDrivers) {
                // CRITICAL FIX: Skip if driver already has this request
                val existingRequest = database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(driver.driverId)
                    .orderByChild("bookingId")
                    .equalTo(booking.id)
                    .get()
                    .await()

                if (existingRequest.exists()) {
//                    android.util.Log.d("DriverMatching", "Skip ${driver.driverId} - already has request")
                    continue
                }

                val activeBookingsCount = getDriverActiveBookingsCount(driver.driverId)
                if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
                    continue
                }

                if (activeBookingsCount > 0) {
                    val isCompatible = isDestinationCompatibleWithExistingRides(
                        driverId = driver.driverId,
                        driverLat = driver.latitude,
                        driverLng = driver.longitude,
                        newDestination = booking.destination,
                        newDestinationCoords = Pair(
                            booking.destination.coordinates.latitude,
                            booking.destination.coordinates.longitude
                        ),
                        newPickupCoords = Pair(
                            booking.pickupLocation.coordinates.latitude,
                            booking.pickupLocation.coordinates.longitude
                        )
                    )
                    if (!isCompatible) continue
                }

                availableNewDrivers.add(driver)
            }

            if (availableNewDrivers.isNotEmpty()) {
//                android.util.Log.i("DriverMatching", "Found ${availableNewDrivers.size} new driver(s)!")
                val topDriver = availableNewDrivers.first()
                sendRequestToDriver(topDriver, booking, bookingStartTime, attemptNumber, availableNewDrivers.drop(1))
            } else {
//                android.util.Log.w("DriverMatching", "No new drivers found")

                val newRemainingTime = GLOBAL_BOOKING_TIMEOUT_MS - (System.currentTimeMillis() - bookingStartTime)

                if (newRemainingTime > 10000) {
//                    android.util.Log.d("DriverMatching", "Will search again in 5s...")
                    delay(5000)
                    searchForNewDrivers(booking, attemptNumber + 1, bookingStartTime)
                } else {
//                    android.util.Log.w("DriverMatching", "Time expired")
                    bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                    cancelAllDriverRequestsForBooking(booking.id)
                }
            }

        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Error searching for new drivers", e)
            bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
            cancelAllDriverRequestsForBooking(booking.id)
        }
    }

    suspend fun declineRideRequest(requestId: String, driverId: String): Result<Unit> {
        return try {
//            android.util.Log.d("DriverMatching", "Driver $driverId declining request $requestId")

            // Get the request to find the bookingId
            val requestSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .child(driverId)
                .child(requestId)
                .get()
                .await()

            val request = requestSnapshot.getValue(DriverRequest::class.java)

            if (request != null) {
                // Write to driver_declines path to trigger instant reassignment
                database.reference
                    .child("driver_declines")
                    .child(request.bookingId)
                    .child(driverId)
                    .setValue(true)
                    .await()

//                android.util.Log.d("DriverMatching", "Decline signal written")

                // Update request status to DECLINED
                database.reference
                    .child(DRIVER_REQUESTS_PATH)
                    .child(driverId)
                    .child(requestId)
                    .child("status")
                    .setValue(DriverRequestStatus.DECLINED.name)
                    .await()

//                android.util.Log.d("DriverMatching", "Status updated to DECLINED")

                Result.success(Unit)
            } else {
                Result.failure(Exception("Request not found"))
            }
        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Error declining request", e)
            Result.failure(e)
        }
    }

    private suspend fun getNotifiedDriversForBooking(bookingId: String): Set<String> {
        return try {
            val notifiedDrivers = mutableSetOf<String>()

            // Check all driver request paths for this booking
            val allDriversSnapshot = database.reference
                .child(DRIVER_REQUESTS_PATH)
                .get()
                .await()

            allDriversSnapshot.children.forEach { driverSnapshot ->
                val driverId = driverSnapshot.key ?: return@forEach

                driverSnapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId) {
                        notifiedDrivers.add(driverId)
                    }
                }
            }

//            android.util.Log.d("DriverMatching", "Found ${notifiedDrivers.size} previously notified drivers for booking $bookingId")
            notifiedDrivers
        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Failed to get notified drivers", e)
            emptySet()
        }
    }

    private suspend fun sendRequestToDriver(
        driver: DriverLocation,
        booking: Booking,
        bookingStartTime: Long,
        attemptNumber: Int,
        remainingDrivers: List<DriverLocation>
    ) {
        val uniqueTimestamp = System.currentTimeMillis() + kotlin.random.Random.nextInt(0, 1000)
        val driverRequest = DriverRequest(
            requestId = "${booking.id}_${driver.driverId}_${uniqueTimestamp}",
            bookingId = booking.id,
            driverId = driver.driverId,
            passengerId = booking.passengerId,
            pickupLocation = DatabaseLocation.fromBookingLocation(booking.pickupLocation),
            destination = DatabaseLocation.fromBookingLocation(booking.destination),
            fareEstimate = DatabaseFareEstimate.fromFareEstimate(booking.fareEstimate),
            requestTime = System.currentTimeMillis(),
            expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
            specialInstructions = booking.specialInstructions,
            passengerDiscountPercentage = booking.passengerDiscountPercentage,
            companions = booking.companions,
            totalPassengers = booking.totalPassengers
        )

        database.reference
            .child(DRIVER_REQUESTS_PATH)
            .child(driver.driverId)
            .child(driverRequest.requestId)
            .setValue(driverRequest)
            .await()

        sendDriverNotification(driver.driverId, booking)

        // Continue monitoring
        startRequestMonitoring(
            bookingId = booking.id,
            driverIds = listOf(driver.driverId),
            booking = booking,
            attemptNumber = attemptNumber,
            remainingDrivers = remainingDrivers,
            bookingStartTime = bookingStartTime
        )

//        android.util.Log.i("DriverMatching", "Sent request to driver ${driver.driverId}")
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
//                android.util.Log.e("DriverMatching", "Failed to get nearby drivers for reassignment")
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
//                android.util.Log.w("DriverMatching", "No new drivers available for reassignment of booking ${booking.id}")
                // Cancel all pending driver requests to clean up
                cancelOtherRequests(booking.id, "")
                bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                updateBookingAssignmentStatus(booking.id, "EXPIRED", "No available drivers found in expanded search")
                return
            }

//            android.util.Log.i("DriverMatching", "Found ${availableDrivers.size} new drivers for reassignment attempt $attemptNumber (sorted by rating)")

            // Send to highest-rated driver first
            val topDriver = availableDrivers.first()
//            android.util.Log.i("DriverMatching", "Reassigning to highest-rated available driver: ${topDriver.driverId} (rating: ${topDriver.rating})")

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
                passengerDiscountPercentage = booking.passengerDiscountPercentage,
                companions = booking.companions,
                totalPassengers = booking.totalPassengers
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

//                android.util.Log.i("DriverMatching", "Reassignment attempt $attemptNumber completed for booking ${booking.id}, notified driver ${topDriver.driverId} (rating: ${topDriver.rating})")
            } catch (e: Exception) {
//                android.util.Log.w("DriverMatching", "Failed to notify driver ${topDriver.driverId} in reassignment", e)

                // Try next driver if available
                if (availableDrivers.size > 1) {
                    val allExcluded = excludedDriverIds + topDriver.driverId
                    reassignBooking(booking, attemptNumber, radiusKm, allExcluded)
                } else {
                    bookingRepository.updateBookingStatus(booking.id, BookingStatus.EXPIRED)
                }
            }

        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Error in reassignment for booking ${booking.id}", e)
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
                            // UPDATED: Only check for PENDING status
                            if (request?.status == DriverRequestStatus.PENDING) {
                                requestSnapshot.ref.child("status")
                                    .setValue(DriverRequestStatus.EXPIRED.name)
//                                android.util.Log.d("DriverMatching", "Fully expired request ${request.requestId} for driver $driverId")
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.w("DriverMatching", "Failed to fully expire request for driver $driverId", e)
            }
        }
    }
    
    /**
     * Process queued bookings when a driver comes online
     * This should be called by DriverRepository when a driver's status changes to online
     */
    suspend fun processQueuedBookingsForNewDriver(driverId: String): Result<Int> {
        return try {
//            android.util.Log.d("DriverMatching", "Starting processQueuedBookingsForNewDriver for driver: $driverId")

            // Get driver location to check vehicle category and position
            val nearbyDriversResult = driverRepository.getNearbyDrivers(0.0, 0.0, 50.0) // Get all drivers
            if (nearbyDriversResult.isFailure) {
//                android.util.Log.e("DriverMatching", "Failed to get nearby drivers: ${nearbyDriversResult.exceptionOrNull()?.message}")
                return Result.failure(Exception("Could not get driver details"))
            }

            val allDrivers = nearbyDriversResult.getOrNull() ?: emptyList()
//            android.util.Log.d("DriverMatching", "Found ${allDrivers.size} total drivers in system")

            val driverLocation = allDrivers.find { it.driverId == driverId }
            if (driverLocation == null) {
//                android.util.Log.e("DriverMatching", "Driver $driverId not found in nearby drivers list")
                allDrivers.forEach { driver ->
//                    android.util.Log.d("DriverMatching", "Available driver: ${driver.driverId}")
                }
                return Result.failure(Exception("Driver location not found"))
            }

//            android.util.Log.d("DriverMatching", "Found driver $driverId - Vehicle: ${driverLocation.vehicleCategory}, Lat: ${driverLocation.latitude}, Lng: ${driverLocation.longitude}")

            // Check if driver already has max accepted bookings
            val activeBookingsCount = getDriverActiveBookingsCount(driverId)
            if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
//                android.util.Log.d("DriverMatching", "Driver $driverId already has $activeBookingsCount accepted bookings (max: $MAX_ACCEPTED_RIDES_PER_DRIVER) - not processing queued bookings")
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
//                    android.util.Log.d("DriverMatching", "Driver $driverId would reach capacity - not sending more queued bookings (sent: $processedCount, capacity remaining: $remainingCapacity)")
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
//                                android.util.Log.d("DriverMatching", "Skipping queued booking $bookingId for driver $driverId - OPPOSITE DIRECTION (strict filtering)")
                                continue
                            }

                            val reason = "destination/direction compatible"
//                            android.util.Log.d("DriverMatching", "Queued booking $bookingId eligible for driver $driverId - $reason")
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
                            requestTime = System.currentTimeMillis(),
                            expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                            specialInstructions = (bookingData["specialInstructions"] as? String) ?: "",
                            passengerDiscountPercentage = (bookingData["passengerDiscountPercentage"] as? Number)?.toInt(),
                            companions = emptyList(),
                            totalPassengers = (bookingData["totalPassengers"] as? Number)?.toInt() ?: 1
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
//                        android.util.Log.i("DriverMatching", "Sent queued booking $bookingId to newly online driver $driverId")

                        // Monitor this booking for acceptance to auto-remove from other drivers
                        monitorBookingAcceptance(bookingId)
                    }
                }
            }

            // ENHANCEMENT: Also check for active pending requests that this driver could handle
            val activePendingCount = processActivePendingRequestsForNewDriver(driverId, driverLocation)

            val totalProcessed = processedCount + activePendingCount
//            android.util.Log.i("DriverMatching", "Driver $driverId received $processedCount queued + $activePendingCount active requests = $totalProcessed total")

            Result.success(totalProcessed)

        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Failed to process queued bookings for driver $driverId", e)
            Result.failure(e)
        }
    }

    /**
     * Process active pending requests for a newly online driver
     * This handles ongoing ride requests that are currently sent to other drivers
     */
    private suspend fun processActivePendingRequestsForNewDriver(driverId: String, driverLocation: DriverLocation): Int {
        return try {
//            android.util.Log.d("DriverMatching", "Checking active pending requests for newly online driver: $driverId")
//            android.util.Log.d("DriverMatching", "Driver location - Lat: ${driverLocation.latitude}, Lng: ${driverLocation.longitude}, Vehicle: ${driverLocation.vehicleCategory}, Online: ${driverLocation.online}")

            // Get all current bookings that are in PENDING status (ride requests sent to drivers but not yet accepted)
            val pendingBookingsSnapshot = firestore.collection("bookings")
                .whereEqualTo("status", BookingStatus.PENDING.name)
                .get()
                .await()

            val pendingBookings = pendingBookingsSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Booking::class.java)?.copy(id = doc.id)
            }

//            android.util.Log.d("DriverMatching", "Found ${pendingBookings.size} pending bookings to check for driver $driverId")
            pendingBookings.forEach { booking ->
//                android.util.Log.d("DriverMatching", "Pending booking: ${booking.id}, vehicle: ${booking.vehicleCategory}, passenger: ${booking.passengerId}")
            }

            // Check if driver already has max accepted bookings
            val activeBookingsCount = getDriverActiveBookingsCount(driverId)
            if (activeBookingsCount >= MAX_ACCEPTED_RIDES_PER_DRIVER) {
//                android.util.Log.d("DriverMatching", "Driver $driverId already has $activeBookingsCount accepted bookings (max: $MAX_ACCEPTED_RIDES_PER_DRIVER) - not sending pending requests")
                return 0
            }

            var sentRequestsCount = 0
            val remainingCapacity = MAX_ACCEPTED_RIDES_PER_DRIVER - activeBookingsCount

            for (booking in pendingBookings) {
                try {
                    // Stop if driver would exceed capacity with this booking
                    if (sentRequestsCount >= remainingCapacity) {
//                        android.util.Log.d("DriverMatching", "Driver $driverId would reach capacity - not sending more requests (sent: $sentRequestsCount, capacity remaining: $remainingCapacity)")
                        break
                    }

                    // Check if this driver matches the booking requirements
                    if (booking.vehicleCategory != driverLocation.vehicleCategory) {
//                        android.util.Log.d("DriverMatching", "Skipping booking ${booking.id}: vehicle category mismatch (need ${booking.vehicleCategory}, driver has ${driverLocation.vehicleCategory})")
                        continue
                    }

                    // Check distance from driver to pickup location (already in meters)
                    val distanceMeters = calculateDistance(
                        driverLocation.latitude, driverLocation.longitude,
                        booking.pickupLocation.coordinates.latitude, booking.pickupLocation.coordinates.longitude
                    )

                    if (distanceMeters > MAX_DRIVER_TO_PICKUP_RADIUS_METERS) {
//                        android.util.Log.d("DriverMatching", "Skipping booking ${booking.id}: too far (${String.format("%.0f", distanceMeters)}m > ${MAX_DRIVER_TO_PICKUP_RADIUS_METERS.toInt()}m)")
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
//                        android.util.Log.d("DriverMatching", "Skipping booking ${booking.id}: driver already has this request")
                        continue
                    }

                    // CRITICAL FIX: Check if another driver currently has an active (non-expired) PENDING request
                    // This prevents sending the same booking to multiple drivers simultaneously
                    val hasActiveRequestElsewhere = checkIfBookingHasActiveRequest(booking.id, driverId)
                    if (hasActiveRequestElsewhere) {
//                        android.util.Log.d("DriverMatching", "Skipping booking ${booking.id}: another driver currently has an active request (within 30s window)")
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
//                            android.util.Log.d("DriverMatching", "Skipping pending booking ${booking.id} for driver $driverId - OPPOSITE DIRECTION (strict filtering)")
                            continue
                        }

                        val reason = "destination/direction compatible"
//                        android.util.Log.d("DriverMatching", "Pending booking ${booking.id} eligible for driver $driverId - $reason")
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
                        requestTime = System.currentTimeMillis(),
                        expirationTime = System.currentTimeMillis() + REQUEST_TIMEOUT_MS,
                        specialInstructions = booking.specialInstructions ?: "",
                        passengerDiscountPercentage = booking.passengerDiscountPercentage,
                        companions = booking.companions,
                        totalPassengers = booking.totalPassengers
                    )

                    // Send request to newly online driver
                    database.reference
                        .child(DRIVER_REQUESTS_PATH)
                        .child(driverId)
                        .child(driverRequest.requestId)
                        .setValue(driverRequest)
                        .await()

                    sentRequestsCount++
//                    android.util.Log.i("DriverMatching", "Sent active pending booking ${booking.id} to newly online driver $driverId (distance: ${String.format("%.0f", distanceMeters)}m)")

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

//            android.util.Log.d("DriverMatching", "Sent $sentRequestsCount active pending requests to driver $driverId")
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
                            !request.isExpired(currentTime)) {
//                            android.util.Log.d("DriverMatching", "Found active request for booking $bookingId with driver $driverId (expires in ${request.getTimeRemaining(currentTime)}s)")
                            return true
                        }
                    }
                }
            }

            // No active requests found
//            android.util.Log.d("DriverMatching", "No active requests found for booking $bookingId (excluding driver $excludeDriverId)")
            false

        } catch (e: Exception) {
//            android.util.Log.e("DriverMatching", "Error checking for active requests for booking $bookingId", e)
            // On error, assume there IS an active request (conservative approach to avoid conflicts)
            true
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
//            android.util.Log.d("CompatibilityCheck", "Checking ride compatibility for driver $driverId")

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

//            android.util.Log.d("CompatibilityCheck", "Driver has ${activeBookings.size} active bookings")

            if (activeBookings.isEmpty()) {
                android.util.Log.d("CompatibilityCheck", "No active bookings - automatically compatible")
                return true
            }

            // Check compatibility using boundary configuration
            val newDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                GeoPoint(newDestinationCoords.first, newDestinationCoords.second),
                zoneBoundaryRepository
            )

            if (newDestBoundary == null) {
                android.util.Log.d("CompatibilityCheck", "New destination not in any boundary - defaulting to compatible")
                return true
            }

//            android.util.Log.d("CompatibilityCheck", "New destination boundary: $newDestBoundary")

            // Check each active booking
            for (booking in activeBookings) {
                val existingDestBoundary = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                    booking.destination.coordinates,
                    zoneBoundaryRepository
                )

                if (existingDestBoundary == null) {
                    android.util.Log.d("CompatibilityCheck", "Existing booking destination not in any boundary - allowing")
                    continue
                }

//                android.util.Log.d("CompatibilityCheck", "Existing booking (${booking.id}) destination boundary: $existingDestBoundary")

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
                val isSimilarDirection = bearingDifference <= 30.0 // Only accept if within 30° - same general direction

//                android.util.Log.d("CompatibilityCheck", "  Route compatibility details (BEARING-BASED ONLY):")
//                android.util.Log.d("CompatibilityCheck", "    - Driver bearing from new pickup: ${String.format("%.1f", driverDirectionFromNewPickup)}°")
//                android.util.Log.d("CompatibilityCheck", "    - Passenger bearing from pickup: ${String.format("%.1f", passengerDirection)}°")
//                android.util.Log.d("CompatibilityCheck", "    - Bearing difference: ${String.format("%.1f", bearingDifference)}°")
//                android.util.Log.d("CompatibilityCheck", "    - Similar direction (≤30°): $isSimilarDirection")

                // Routes overlap if bearings are similar (same direction)
                val routesOverlap = isSimilarDirection

                if (routesOverlap) {
//                    android.util.Log.d("CompatibilityCheck", "SAME DIRECTION - bearing match!")
                    return true
                } else {
//                    android.util.Log.d("CompatibilityCheck", "DIFFERENT DIRECTION - bearing mismatch")
                    return false
                }
            }

            // If we reach here, all bookings passed (should not happen with the logic above)
//            android.util.Log.d("CompatibilityCheck", "All bookings checked - compatible")
            return true

        } catch (e: Exception) {
//            android.util.Log.e("CompatibilityCheck", "Error checking compatibility", e)
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
                    if (request?.bookingId == bookingId && request.status == DriverRequestStatus.PENDING) {
                        
                        // Mark as accepted by another driver
                        requestSnapshot.ref.child("status")
                            .setValue(DriverRequestStatus.ACCEPTED_BY_OTHER.name)
                        
//                        android.util.Log.d("DriverMatching", "Removed booking $bookingId request from driver $driverId (accepted by another)")
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DriverMatching", "Failed to remove booking requests from other drivers", e)
        }
    }
}
