package com.rj.islamove.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.database.FirebaseDatabase
import com.rj.islamove.data.models.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val sanJoseLocationRepository: SanJoseLocationRepository,
    private val activeBookingRepository: ActiveBookingRepository,
    private val mapboxRepository: MapboxRepository,
    private val ratingRepository: RatingRepository,
    private val database: FirebaseDatabase,
    private val notificationService: com.rj.islamove.data.services.NotificationService
) {
    
    // Cache fare estimates to avoid recalculation for same routes
    private data class FareCacheKey(
        val pickupLat: String,
        val pickupLng: String,
        val destLat: String,
        val destLng: String,
        val vehicleCategory: VehicleCategory
    )

    private val fareCache = mutableMapOf<FareCacheKey, FareEstimate>()
    private val maxFareCacheSize = 50

    /**
     * Migration helper: Convert legacy booking distances from km to meters
     * Old bookings stored estimatedDistance in kilometers, but new code expects meters
     */
    private fun migrateBookingDistance(booking: Booking): Booking {
        val fareEstimate = booking.fareEstimate

        // Check if distance needs migration (likely km if < 100 and > 0.01)
        val needsMigration = fareEstimate.estimatedDistance > 0.01 && fareEstimate.estimatedDistance < 100.0

        return if (needsMigration) {
            val migratedDistance = fareEstimate.estimatedDistance * 1000.0 // Convert km to meters
            val migratedFareEstimate = fareEstimate.copy(estimatedDistance = migratedDistance)

            android.util.Log.i("BookingRepository", "🔄 MIGRATED booking ${booking.id}: distance ${fareEstimate.estimatedDistance}km -> ${migratedDistance}m")

            // Update the booking in Firestore with migrated distance
            firestore.collection(BOOKINGS_COLLECTION)
                .document(booking.id)
                .update("fareEstimate.estimatedDistance", migratedDistance)
                .addOnSuccessListener {
                    android.util.Log.i("BookingRepository", "✅ Updated booking ${booking.id} in Firestore with migrated distance")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("BookingRepository", "❌ Failed to update booking ${booking.id} in Firestore", e)
                }

            booking.copy(fareEstimate = migratedFareEstimate)
        } else {
            booking
        }
    }

    private fun generateFareCacheKey(
        pickup: BookingLocation,
        destination: BookingLocation,
        vehicleCategory: VehicleCategory
    ): FareCacheKey {
        return FareCacheKey(
            pickupLat = "%.3f".format(pickup.coordinates.latitude),
            pickupLng = "%.3f".format(pickup.coordinates.longitude),
            destLat = "%.3f".format(destination.coordinates.latitude),
            destLng = "%.3f".format(destination.coordinates.longitude),
            vehicleCategory = vehicleCategory
        )
    }

    companion object {
        private const val BOOKINGS_COLLECTION = "bookings"
        private const val ACTIVE_BOOKINGS_COLLECTION = "active_bookings"
        
        // Note: Fare calculations now use San Jose Municipal Fare Matrix only
        // No distance or time-based calculations - matrix covers all routes
    }
    
    /**
     * FR-3.1.2: Calculate fare estimates using San Jose Municipal Fare Matrix only
     */
    suspend fun calculateFareEstimate(
        pickupLocation: GeoPoint,
        destination: GeoPoint,
        vehicleCategory: VehicleCategory = VehicleCategory.STANDARD
    ): FareEstimate {
        // Create temporary BookingLocation objects for the San Jose fare calculation
        val pickup = BookingLocation(
            address = "Pickup Location",
            coordinates = pickupLocation
        )
        val dest = BookingLocation(
            address = "Destination Location", 
            coordinates = destination
        )
        
        return calculateFareEstimate(pickup, dest, vehicleCategory)
    }
    
    /**
     * Calculate fare estimate using San Jose Municipal Fare Matrix only
     */
    suspend fun calculateFareEstimate(
        pickupLocation: BookingLocation,
        destination: BookingLocation,
        vehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
        discountPercentage: Int? = null
    ): FareEstimate {
        // Check cache first to avoid unnecessary calculation
        val cacheKey = generateFareCacheKey(pickupLocation, destination, vehicleCategory)
        fareCache[cacheKey]?.let { cachedFare ->
            return cachedFare
        }

        return try {
            // Use only San Jose municipal fare matrix - no distance/time calculations
            val fareEstimate = sanJoseLocationRepository.calculateFareEstimate(
                pickupLocation, destination, vehicleCategory, discountPercentage
            )

            // Cache the result
            if (fareCache.size >= maxFareCacheSize) {
                // Remove oldest entry to make room
                fareCache.remove(fareCache.keys.first())
            }
            fareCache[cacheKey] = fareEstimate

            fareEstimate
        } catch (e: Exception) {
            // Fallback to municipal calculation on any error
            val fareEstimate = sanJoseLocationRepository.calculateFareEstimate(
                pickupLocation, destination, vehicleCategory, discountPercentage
            )

            // Cache the fallback result too
            if (fareCache.size < maxFareCacheSize) {
                fareCache[cacheKey] = fareEstimate
            }

            fareEstimate
        }
    }
    
    /**
     * FR-3.1.3 & FR-3.1.4: Create booking and handle active/scheduled logic
     */
        suspend fun createBooking(booking: Booking): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            
            // IMPORTANT: Validate that destination is within San Jose, Dinagat Islands service area
            // Pickup can be from anywhere to bring people into San Jose (e.g., airport pickups)
            val destLat = booking.destination.coordinates.latitude
            val destLng = booking.destination.coordinates.longitude
            
            if (!sanJoseLocationRepository.isWithinSanJose(destLat, destLng)) {
                return Result.failure(Exception("Destination must be within San Jose, Dinagat Islands service area. Pickup can be from anywhere."))
            }
            
            val bookingWithId = booking.copy(
                passengerId = currentUser.uid,
                requestTime = System.currentTimeMillis()
            )
            
            // Store in Firestore for persistence and history
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingWithId.id)
                .set(bookingWithId)
                .await()
            
            // Handle immediate vs scheduled bookings
            if (booking.scheduledTime == null || booking.scheduledTime!! <= System.currentTimeMillis() + (5 * 60 * 1000)) {
                // Immediate booking or scheduled within 5 minutes - create active booking
                // BUT first check if booking hasn't been cancelled in the meantime
                val currentBookingDoc = firestore.collection(BOOKINGS_COLLECTION)
                    .document(bookingWithId.id)
                    .get()
                    .await()

                val currentBooking = currentBookingDoc.toObject(Booking::class.java)
                if (currentBooking?.status == BookingStatus.CANCELLED) {
                    android.util.Log.d("BookingRepository", "Booking was cancelled before active booking creation - skipping active booking")
                    return@createBooking Result.success(bookingWithId.id)
                }

                val activeResult = activeBookingRepository.createActiveBooking(bookingWithId)
                if (activeResult.isFailure) {
                    // If active booking creation fails, still keep the main booking record
                    throw activeResult.exceptionOrNull() ?: Exception("Failed to create active booking")
                }
            } else {
                // Scheduled booking - will be moved to active bookings by Cloud Function
                // Update status to indicate it's scheduled
                firestore.collection(BOOKINGS_COLLECTION)
                    .document(bookingWithId.id)
                    .update("status", BookingStatus.SCHEDULED)
                    .await()
            }
            
            Result.success(bookingWithId.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNewBookingId(): String {
        return firestore.collection(BOOKINGS_COLLECTION).document().id
    }
    
    /**
     * Get booking by ID
     */
    suspend fun getBooking(bookingId: String): Result<Booking> {
        return try {
            val bookingDoc = firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .get()
                .await()
            
            if (bookingDoc.exists()) {
                // Log the raw Firestore data to see what fields exist
                val rawData = bookingDoc.data
                android.util.Log.d("BookingRepository", "Raw Firestore document fields:")
                rawData?.forEach { (key, value) ->
                    android.util.Log.d("BookingRepository", "  $key: '$value' (${value?.javaClass?.simpleName})")
                }

                val booking = bookingDoc.toObject(Booking::class.java)
                if (booking != null) {
                    // Apply distance migration if needed
                    val migratedBooking = migrateBookingDistance(booking)

                    // Ensure the booking ID is set from the document ID
                    var bookingWithId = migratedBooking.copy(id = bookingDoc.id)

                    // If passengerId is empty, try manual extraction from raw data
                    if (bookingWithId.passengerId.isBlank()) {
                        val manualPassengerId = rawData?.get("passengerId") as? String
                        android.util.Log.w("BookingRepository", "passengerId empty in toObject(), trying manual extraction: '$manualPassengerId'")

                        if (!manualPassengerId.isNullOrBlank()) {
                            bookingWithId = bookingWithId.copy(passengerId = manualPassengerId)
                            android.util.Log.i("BookingRepository", "Fixed passengerId with manual extraction: '$manualPassengerId'")
                        }
                    }

                    android.util.Log.d("BookingRepository", "After toObject() conversion:")
                    android.util.Log.d("BookingRepository", "  booking.id: '${bookingWithId.id}'")
                    android.util.Log.d("BookingRepository", "  booking.passengerId: '${bookingWithId.passengerId}' (length: ${bookingWithId.passengerId.length})")
                    android.util.Log.d("BookingRepository", "  booking.driverId: '${bookingWithId.driverId}'")
                    android.util.Log.d("BookingRepository", "  booking.status: ${bookingWithId.status}")
                    Result.success(bookingWithId)
                } else {
                    Result.failure(Exception("Failed to parse booking data"))
                }
            } else {
                Result.failure(Exception("Booking not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.2.4: Retrieve ride history with pagination
     */
    suspend fun getUserBookingHistory(limit: Int = 20): Result<List<Booking>> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            try {
                // Try the optimized query first (requires composite index)
                val querySnapshot = firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("passengerId", currentUser.uid)
                    .orderBy("requestTime", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val bookings = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Booking::class.java)?.let { migrateBookingDistance(it) }?.copy(id = doc.id)
                }

                Result.success(bookings)
            } catch (indexException: Exception) {
                // Fallback: Get data without ordering, then sort in memory
                android.util.Log.w("BookingRepository", "Composite index not available, using fallback query")

                val querySnapshot = firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("passengerId", currentUser.uid)
                    .limit(50L) // Get more to account for sorting
                    .get()
                    .await()

                val bookings = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Booking::class.java)?.let { migrateBookingDistance(it) }?.copy(id = doc.id)
                }.sortedByDescending { it.requestTime }
                  .take(limit)

                Result.success(bookings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Cancel booking - handles both Firestore and Realtime Database cleanup
     * Also cancels all pending driver requests to make them disappear immediately
     */
    suspend fun cancelBooking(
        bookingId: String,
        reason: String = "",
        cancelledBy: String = "passenger"  // "passenger" or "driver"
    ): Result<Unit> {
        return try {
            android.util.Log.d("BookingRepository", "🔒 ATOMIC: Starting cancellation transaction for booking: '$bookingId'")

            val bookingRef = firestore.collection(BOOKINGS_COLLECTION).document(bookingId)

            // Use Firestore transaction for atomic operation
            firestore.runTransaction { transaction ->
                val bookingDoc = transaction.get(bookingRef)

                if (!bookingDoc.exists()) {
                    throw Exception("Booking not found: $bookingId")
                }

                val currentStatus = bookingDoc.getString("status")
                val currentDriverId = bookingDoc.getString("driverId")

                // RACE CONDITION CHECK: Verify booking can still be cancelled
                when {
                    currentStatus == BookingStatus.CANCELLED.name -> {
                        // Booking is already cancelled - this is actually the desired outcome
                        android.util.Log.d("BookingRepository", "✅ ATOMIC: Booking already cancelled - treating as success")
                        return@runTransaction null // Return null to indicate success without further updates
                    }
                    currentStatus == BookingStatus.COMPLETED.name -> {
                        throw Exception("RACE_CONDITION: Booking already completed")
                    }
                    currentStatus in listOf(BookingStatus.DRIVER_ARRIVING.name, BookingStatus.DRIVER_ARRIVED.name, BookingStatus.IN_PROGRESS.name)
                            && currentDriverId != null && currentDriverId.isNotBlank()
                            && cancelledBy == "passenger" -> {
                        // Passenger cancelling after driver accepted - this is allowed
                        android.util.Log.d("BookingRepository", "Passenger cancelling accepted booking with driver $currentDriverId")
                    }
                }

                // Proceed with cancellation
                val updates = mapOf(
                    "status" to BookingStatus.CANCELLED.name,
                    "completionTime" to System.currentTimeMillis(),
                    "specialInstructions" to reason,
                    "cancelledBy" to cancelledBy,
                    "lastUpdateTime" to System.currentTimeMillis()
                )
                transaction.update(bookingRef, updates)

                // Return booking data for post-transaction processing
                mapOf(
                    "driverId" to currentDriverId,
                    "status" to currentStatus,
                    "cancelledBy" to cancelledBy
                )
            }.await().also { transactionResult ->
                // Handle case where booking was already cancelled (transactionResult is null)
                if (transactionResult == null) {
                    android.util.Log.d("BookingRepository", "✅ ATOMIC: Booking was already cancelled - still need to clean up driver requests")

                    // Even if booking was already cancelled, we still need to cancel driver requests
                    // to prevent stale requests from appearing on driver side
                    if (cancelledBy == "passenger") {
                        android.util.Log.d("BookingRepository", "Passenger cancelled - clearing all driver requests for booking: $bookingId")
                        try {
                            cancelDriverRequestsDirectly(bookingId)
                            android.util.Log.d("BookingRepository", "✅ Successfully cancelled all driver requests for already-cancelled booking")
                        } catch (e: Exception) {
                            android.util.Log.e("BookingRepository", "❌ Failed to cancel driver requests for already-cancelled booking", e)
                        }
                    }
                    return@also
                }

                // Process post-transaction actions
                val driverId = transactionResult["driverId"] as? String
                val previousStatus = transactionResult["status"] as? String
                val actualCancelledBy = transactionResult["cancelledBy"] as? String

                android.util.Log.d("BookingRepository", "✅ ATOMIC: Cancellation transaction successful for booking: $bookingId")

                // Remove from active bookings in Realtime Database
                activeBookingRepository.removeActiveBooking(bookingId)

                // Cancel all pending driver requests if PASSENGER cancelled (clears Maps tab)
                if (actualCancelledBy == "passenger") {
                    android.util.Log.d("BookingRepository", "Passenger cancelled - clearing all driver requests for booking: $bookingId")
                    try {
                        cancelDriverRequestsDirectly(bookingId)
                        android.util.Log.d("BookingRepository", "✅ Successfully cancelled all driver requests")
                    } catch (e: Exception) {
                        android.util.Log.e("BookingRepository", "❌ Failed to cancel driver requests", e)
                    }
                }

                // Notify driver if PASSENGER cancelled and driver was assigned
                if (!driverId.isNullOrEmpty() &&
                    previousStatus in listOf(BookingStatus.ACCEPTED.name, BookingStatus.DRIVER_ARRIVING.name, BookingStatus.DRIVER_ARRIVED.name) &&
                    actualCancelledBy == "passenger") {

                    android.util.Log.i("BookingRepository", "🔔 Notifying assigned driver $driverId of passenger cancellation")
                    try {
                        // Get full booking details for notification
                        val bookingDoc = firestore.collection(BOOKINGS_COLLECTION).document(bookingId).get().await()
                        val booking = bookingDoc.toObject(Booking::class.java)
                        if (booking != null) {
                            notificationService.sendRideCancellationToDriver(booking, driverId, reason)
                            android.util.Log.d("BookingRepository", "✅ Driver notification sent successfully")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BookingRepository", "❌ Failed to notify driver of cancellation", e)
                    }
                }

                // Cancel all driver requests so they disappear from driver UIs immediately
                cancelAllDriverRequestsForBooking(bookingId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            when {
                e.message?.contains("RACE_CONDITION") == true -> {
                    android.util.Log.w("BookingRepository", "Race condition detected during cancellation: ${e.message}")
                    Result.failure(Exception(e.message))
                }
                e.message?.contains("Booking not found") == true -> {
                    android.util.Log.w("BookingRepository", "Booking not found - may be timing issue, retrying once...")

                    // Retry once for immediate cancellations (when booking might still be creating)
                    return try {
                        delay(500) // Small delay to allow booking creation to complete
                        cancelBooking(bookingId, reason, cancelledBy)
                    } catch (retryException: Exception) {
                        if (retryException.message?.contains("Booking not found") == true) {
                            // If still not found after retry, consider it a successful cancellation
                            // since the booking likely never fully created
                            android.util.Log.d("BookingRepository", "Booking still not found after retry - assuming cancellation successful")
                            Result.success(Unit)
                        } else {
                            android.util.Log.e("BookingRepository", "Retry failed for booking: $bookingId", retryException)
                            Result.failure(retryException)
                        }
                    }
                }
                else -> {
                    android.util.Log.e("BookingRepository", "Failed to cancel booking: $bookingId", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * Cancel all driver requests when passenger cancels booking
     * This ensures requests immediately disappear from driver UIs
     */
    private suspend fun cancelAllDriverRequestsForBooking(bookingId: String) {
        try {
            android.util.Log.d("BookingRepository", "Cancelling all driver requests for booking: $bookingId")
            
            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child("driver_requests")
                .get()
                .await()
            
            var cancelledCount = 0
            
            for (driverSnapshot in allDriversSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue
                
                // Check all requests for this driver
                driverSnapshot.children.forEach { requestSnapshot ->
                    val request = requestSnapshot.getValue(DriverRequest::class.java)
                    if (request?.bookingId == bookingId && 
                        (request.status == DriverRequestStatus.PENDING || request.status == DriverRequestStatus.SECOND_CHANCE)) {
                        
                        // Mark as cancelled (passenger cancelled)
                        requestSnapshot.ref.child("status").setValue(DriverRequestStatus.CANCELLED.name)
                        
                        cancelledCount++
                        android.util.Log.d("BookingRepository", "Cancelled request ${request.requestId} for driver $driverId (passenger cancelled booking)")
                    }
                }
            }
            
            android.util.Log.d("BookingRepository", "Successfully cancelled $cancelledCount driver requests for booking $bookingId")
            
        } catch (e: Exception) {
            android.util.Log.e("BookingRepository", "Failed to cancel driver requests for booking $bookingId", e)
            // Don't fail the main cancellation if this fails
        }
    }
    
    /**
     * Update booking status - syncs between Firestore and Realtime Database
     */
    suspend fun updateBookingStatus(bookingId: String, status: BookingStatus): Result<Unit> {
        return try {
            // Enhanced logging for debugging
            android.util.Log.d("BookingRepository", "Received bookingId: '$bookingId' (length: ${bookingId.length})")
            
            // Validate bookingId is not empty or just "booking"
            if (bookingId.isBlank()) {
                android.util.Log.e("BookingRepository", "BookingId is blank or empty")
                return Result.failure(Exception("Booking ID is empty. Please try again or restart the app."))
            }
            
            if (bookingId.equals("booking", ignoreCase = true)) {
                android.util.Log.e("BookingRepository", "BookingId is literally 'booking' - this is invalid")
                return Result.failure(Exception("Invalid booking reference. Please restart the app."))
            }
            
            // Check for other suspicious values
            if (bookingId.length < 10) {
                android.util.Log.w("BookingRepository", "BookingId seems suspiciously short: '$bookingId'")
            }
            
            android.util.Log.d("BookingRepository", "Updating booking status - ID: '$bookingId', Status: $status")
            
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            
            // Add completion time for terminal states
            if (status in listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED)) {
                updates["completionTime"] = System.currentTimeMillis()
            }
            
            // Update in Firestore with additional validation
            try {
                firestore.collection(BOOKINGS_COLLECTION)
                    .document(bookingId)
                    .update(updates)
                    .await()
                    
                android.util.Log.d("BookingRepository", "Firestore update successful for booking: $bookingId")
            } catch (firestoreException: Exception) {
                if (firestoreException.message?.contains("NOT_FOUND") == true) {
                    android.util.Log.w("BookingRepository", "Booking document not found for update. Status: $status")
                    
                    // For certain status updates, we can proceed without the document
                    // This handles cases where status is updated before the booking document is fully created
                    if (status in listOf(BookingStatus.DRIVER_ARRIVED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED)) {
                        android.util.Log.i("BookingRepository", "Proceeding with status update $status even though document doesn't exist")
                        // Continue execution - don't throw error
                    } else {
                        android.util.Log.e("BookingRepository", "Document not found and status $status requires existing document")
                        throw Exception("Booking document not found for required status update: $status. BookingId: $bookingId")
                    }
                } else {
                    android.util.Log.e("BookingRepository", "Firestore update failed for booking: $bookingId", firestoreException)
                    throw firestoreException
                }
            }
            
            // Create pending ratings when trip is completed successfully
            if (status == BookingStatus.COMPLETED) {
                // Get booking details to extract passenger and driver IDs
                getBooking(bookingId).fold(
                    onSuccess = { booking ->
                        if (booking.driverId != null) {
                            // Create pending ratings for both passenger and driver
                            ratingRepository.createPendingRating(
                                bookingId = bookingId,
                                passengerId = booking.passengerId,
                                driverId = booking.driverId!!
                            )
                        }
                    },
                    onFailure = { 
                        // Log error but don't fail the booking update
                        // The trip is still completed even if rating creation fails
                    }
                )
            }
            
            // Log status changes for debugging
            android.util.Log.d("BookingRepository", "Booking $bookingId status updated to: $status")
            android.util.Log.d("BookingRepository", "Firestore update completed for booking: $bookingId")
            
            // Handle active booking updates
            if (status in listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
                // For COMPLETED status, first update active booking to COMPLETED so passenger can see it
                if (status == BookingStatus.COMPLETED) {
                    activeBookingRepository.updateActiveBookingStatus(bookingId, ActiveBookingStatus.COMPLETED)
                    // Schedule removal after delay to allow passenger to see completion
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(3000) // 3 second delay
                        activeBookingRepository.removeActiveBooking(bookingId)
                    }
                } else {
                    // Remove from active bookings immediately for CANCELLED/EXPIRED
                    activeBookingRepository.removeActiveBooking(bookingId)
                }
            } else {
                // Update active booking status for non-terminal states
                val activeStatus = when (status) {
                    BookingStatus.ACCEPTED -> ActiveBookingStatus.DRIVER_ASSIGNED
                    BookingStatus.IN_PROGRESS -> ActiveBookingStatus.IN_PROGRESS
                    BookingStatus.DRIVER_ARRIVING -> ActiveBookingStatus.DRIVER_ARRIVING
                    BookingStatus.DRIVER_ARRIVED -> ActiveBookingStatus.DRIVER_ARRIVED
                    else -> ActiveBookingStatus.SEARCHING_DRIVER
                }
                activeBookingRepository.updateActiveBookingStatus(bookingId, activeStatus)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Assign driver to booking (called when driver accepts) - ATOMIC OPERATION
     * Uses transaction to prevent race conditions with simultaneous cancellations
     */
    suspend fun assignDriverToBooking(
        bookingId: String,
        driverId: String,
        driverLocation: BookingLocation,
        estimatedArrival: Int,
        status: BookingStatus = BookingStatus.ACCEPTED
    ): Result<Unit> {
        return try {
            android.util.Log.d("BookingRepository", "🔒 ATOMIC: Starting driver assignment transaction for booking: '$bookingId'")

            val bookingRef = firestore.collection(BOOKINGS_COLLECTION).document(bookingId)

            // Use Firestore transaction for atomic operation
            firestore.runTransaction { transaction ->
                val bookingDoc = transaction.get(bookingRef)

                if (!bookingDoc.exists()) {
                    android.util.Log.e("BookingRepository", "❌ ATOMIC: Booking document does not exist: $bookingId")
                    throw Exception("Booking not found: $bookingId")
                }

                val currentStatus = bookingDoc.getString("status")
                val currentDriverId = bookingDoc.getString("driverId")
                val cancelledBy = bookingDoc.getString("cancelledBy")

                android.util.Log.d("BookingRepository", "🔒 ATOMIC: Current booking state - status: $currentStatus, driverId: $currentDriverId, cancelledBy: $cancelledBy")

                // RACE CONDITION CHECK: Only proceed if booking is still available for acceptance
                when {
                    // Case 1: Booking is already cancelled - REJECT
                    currentStatus == BookingStatus.CANCELLED.name -> {
                        android.util.Log.w("BookingRepository", "🚫 ATOMIC: Booking already cancelled by $cancelledBy - rejecting driver assignment")
                        throw Exception("RACE_CONDITION: Booking was cancelled by passenger during driver acceptance")
                    }

                    // Case 2: Booking already has a driver - REJECT
                    currentDriverId != null && currentDriverId.isNotBlank() -> {
                        android.util.Log.w("BookingRepository", "🚫 ATOMIC: Booking already assigned to driver $currentDriverId - rejecting duplicate assignment")
                        throw Exception("RACE_CONDITION: Booking already assigned to another driver")
                    }

                    // Case 3: Booking is in terminal state - REJECT
                    currentStatus in listOf(BookingStatus.COMPLETED.name, BookingStatus.EXPIRED.name) -> {
                        android.util.Log.w("BookingRepository", "🚫 ATOMIC: Booking is in terminal state ($currentStatus) - rejecting assignment")
                        throw Exception("RACE_CONDITION: Booking is no longer available")
                    }

                    // Case 4: Booking is available - PROCEED WITH ASSIGNMENT
                    else -> {
                        android.util.Log.d("BookingRepository", "✅ ATOMIC: Booking is available, proceeding with driver assignment")

                        val updates = mapOf(
                            "status" to status.name,
                            "driverId" to driverId,
                            "pickupTime" to System.currentTimeMillis(),
                            "lastUpdateTime" to System.currentTimeMillis() // For race condition detection
                        )

                        transaction.update(bookingRef, updates)
                        android.util.Log.d("BookingRepository", "✅ ATOMIC: Driver assignment committed to Firestore")
                    }
                }
            }.await()

            android.util.Log.d("BookingRepository", "🎉 ATOMIC: Driver $driverId successfully assigned to booking $bookingId")

            // Update active booking with driver details (outside transaction)
            activeBookingRepository.assignDriverToBooking(
                bookingId, driverId, driverLocation, estimatedArrival
            )

            Result.success(Unit)

        } catch (e: Exception) {
            when {
                e.message?.contains("RACE_CONDITION") == true -> {
                    android.util.Log.w("BookingRepository", "⚠️ ATOMIC: Race condition detected - $driverId lost race for booking $bookingId")
                    Result.failure(Exception(e.message)) // Pass the specific race condition error
                }
                else -> {
                    android.util.Log.e("BookingRepository", "💥 ATOMIC: Transaction failed for booking $bookingId", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * Observe booking changes in real-time
     */
    fun observeBooking(bookingId: String): Flow<Result<Booking?>> = callbackFlow {
        val listener = firestore.collection(BOOKINGS_COLLECTION)
            .document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val booking = snapshot.toObject(Booking::class.java)?.let { migrateBookingDistance(it) }
                    trySend(Result.success(booking))
                } else {
                    trySend(Result.success(null))
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }

    /**
     * Search San Jose locations using municipal data and Google Maps geocoding
     */
    suspend fun searchLocations(query: String): Result<List<BookingLocation>> {
        return try {
            // Try Google Maps geocoding first for better search results
            val googleMapsResult = mapboxRepository.searchLocations(query)
            
            if (googleMapsResult.isSuccess) {
                val googleMapsResults = googleMapsResult.getOrNull()!!
                val bookingLocations = googleMapsResults.map { searchResult ->
                    BookingLocation(
                        address = searchResult.shortAddress,
                        coordinates = searchResult.coordinates,
                        placeName = searchResult.name,
                        placeType = searchResult.placeType
                    )
                }
                Result.success(bookingLocations)
            } else {
                // Fallback to San Jose municipal data
                sanJoseLocationRepository.searchLocations(query)
            }
        } catch (e: Exception) {
            // Final fallback to municipal data
            sanJoseLocationRepository.searchLocations(query)
        }
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLonRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Get all active bookings (admin functionality)
     */
    fun getActiveBookings(): Flow<Result<List<Booking>>> = callbackFlow {
        // Only get bookings from the last 24 hours to avoid old stale data
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        val listener: ListenerRegistration = firestore.collection(BOOKINGS_COLLECTION)
            .whereIn("status", listOf(
                BookingStatus.PENDING.name,
                BookingStatus.ACCEPTED.name,
                BookingStatus.DRIVER_ARRIVING.name,
                BookingStatus.DRIVER_ARRIVED.name,
                BookingStatus.IN_PROGRESS.name
            ))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                try {
                    val allBookings = snapshot?.documents?.mapNotNull { document ->
                        document.toObject(Booking::class.java)?.let { migrateBookingDistance(it) }?.copy(id = document.id)
                    } ?: emptyList()

                    // Filter for recent bookings (last 24 hours) and truly active statuses
                    val recentBookings = allBookings.filter { booking ->
                        booking.requestTime > twentyFourHoursAgo
                    }

                    // Additional filtering to ensure we only get truly active rides
                    val activeBookings = recentBookings.filter { booking ->
                        when (booking.status) {
                            BookingStatus.PENDING,
                            BookingStatus.ACCEPTED,
                            BookingStatus.DRIVER_ARRIVING,
                            BookingStatus.DRIVER_ARRIVED,
                            BookingStatus.IN_PROGRESS -> true
                            else -> false
                        }
                    }

                    val sortedBookings = activeBookings.sortedByDescending { it.requestTime }

                    // Debug logging to understand the data
                    println("DEBUG: getActiveBookings - Total from DB: ${allBookings.size}, Recent: ${recentBookings.size}, Active: ${sortedBookings.size}")

                    if (allBookings.size != sortedBookings.size) {
                        println("DEBUG: Filtered out ${allBookings.size - sortedBookings.size} old or inactive bookings")
                    }

                    // Group by status for debugging
                    val statusCounts = sortedBookings.groupBy { it.status }.mapValues { it.value.size }
                    println("DEBUG: Active status breakdown: $statusCounts")

                    trySend(Result.success(sortedBookings))
                } catch (e: Exception) {
                    println("DEBUG: Error in getActiveBookings: ${e.message}")
                    trySend(Result.failure(e))
                }
            }
        
        awaitClose { listener.remove() }
    }

    /**
     * Cancel all driver requests for a booking directly in Firebase Realtime Database
     * This avoids circular dependency with DriverMatchingRepository
     */
    private suspend fun cancelDriverRequestsDirectly(bookingId: String) {
        try {
            android.util.Log.d("BookingRepository", "Cancelling all driver requests for booking: $bookingId")

            // Get all driver requests for this booking
            val allDriversSnapshot = database.reference
                .child("driver_requests") // DRIVER_REQUESTS_PATH constant value
                .get()
                .await()

            var cancelledCount = 0

            for (driverSnapshot in allDriversSnapshot.children) {
                val driverId = driverSnapshot.key ?: continue

                // Check all requests for this driver
                driverSnapshot.children.forEach { requestSnapshot ->
                    @Suppress("UNCHECKED_CAST")
                    val requestData = requestSnapshot.value as? Map<String, Any>
                    val requestBookingId = requestData?.get("bookingId") as? String
                    val requestStatus = requestData?.get("status") as? String

                    if (requestBookingId == bookingId &&
                        (requestStatus == "PENDING" || requestStatus == "SECOND_CHANCE")) {

                        // Mark as cancelled (passenger cancelled)
                        requestSnapshot.ref.child("status").setValue("CANCELLED")

                        cancelledCount++
                        val requestId = requestData?.get("requestId") as? String ?: "unknown"
                        android.util.Log.d("BookingRepository", "Cancelled request $requestId for driver $driverId (passenger cancelled booking)")
                    }
                }
            }

            android.util.Log.d("BookingRepository", "Successfully cancelled $cancelledCount driver requests for booking $bookingId")

        } catch (e: Exception) {
            android.util.Log.e("BookingRepository", "Failed to cancel driver requests directly", e)
            throw e // Re-throw so calling code knows it failed
        }
    }
}