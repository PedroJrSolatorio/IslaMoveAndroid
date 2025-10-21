package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveBookingRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    
    private val activeBookingsRef = database.getReference("active_bookings")
    private val driverStatusRef = database.getReference("driver_status")
    
    companion object {
        private const val TAG = "ActiveBookingRepository"
    }
    
    /**
     * FR-3.1.3: Store active booking in Firebase Realtime Database
     * Active bookings are stored in real-time for immediate driver matching
     */
    suspend fun createActiveBooking(booking: Booking): Result<Unit> {
        return try {
            Log.d(TAG, "Creating active booking: ${booking.id}")
            
            val activeBooking = ActiveBooking(
                bookingId = booking.id,
                passengerId = booking.passengerId,
                pickupLocation = RealtimeLocation(booking.pickupLocation),
                destination = RealtimeLocation(booking.destination),
                fareEstimate = RealtimeFareEstimate(booking.fareEstimate),
                vehicleCategory = booking.vehicleCategory.name,
                status = ActiveBookingStatus.SEARCHING_DRIVER,
                requestTime = booking.requestTime,
                scheduledTime = booking.scheduledTime,
                expiresAt = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes timeout
            )
            
            activeBookingsRef.child(booking.id).setValue(activeBooking).await()
            Log.d(TAG, "Active booking created successfully: ${booking.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating active booking: ${booking.id}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update active booking status
     */
    suspend fun updateActiveBookingStatus(
        bookingId: String,
        status: ActiveBookingStatus,
        driverId: String? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "lastUpdated" to System.currentTimeMillis()
            )
            
            driverId?.let { updates["assignedDriverId"] = it }
            
            activeBookingsRef.child(bookingId).updateChildren(updates).await()
            Log.d(TAG, "Active booking status updated: $bookingId -> $status")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating active booking status: $bookingId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Assign driver to active booking
     */
    suspend fun assignDriverToBooking(
        bookingId: String,
        driverId: String,
        driverLocation: BookingLocation,
        estimatedArrival: Int
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "assignedDriverId" to driverId,
                "driverLocation" to RealtimeLocation(driverLocation),
                "estimatedDriverArrival" to estimatedArrival,
                "status" to ActiveBookingStatus.DRIVER_ASSIGNED.name,
                "lastUpdated" to System.currentTimeMillis()
            )
            
            activeBookingsRef.child(bookingId).updateChildren(updates).await()
            Log.d(TAG, "Driver assigned to booking: $bookingId -> $driverId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning driver to booking: $bookingId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove active booking (when completed, cancelled, or expired)
     */
    suspend fun removeActiveBooking(bookingId: String): Result<Unit> {
        return try {
            activeBookingsRef.child(bookingId).removeValue().await()
            Log.d(TAG, "Active booking removed: $bookingId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing active booking: $bookingId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get active booking by ID (real-time)
     */
    fun getActiveBooking(bookingId: String): Flow<ActiveBooking?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeBooking = snapshot.getValue(ActiveBooking::class.java)
                trySend(activeBooking)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error listening to active booking: $bookingId", error.toException())
                trySend(null)
            }
        }
        
        activeBookingsRef.child(bookingId).addValueEventListener(listener)
        
        awaitClose {
            activeBookingsRef.child(bookingId).removeEventListener(listener)
        }
    }
    
    /**
     * Get all active bookings for a passenger (real-time)
     */
    fun getPassengerActiveBookings(passengerId: String): Flow<List<ActiveBooking>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeBookings = mutableListOf<ActiveBooking>()
                
                for (child in snapshot.children) {
                    child.getValue(ActiveBooking::class.java)?.let { booking ->
                        if (booking.passengerId == passengerId) {
                            activeBookings.add(booking)
                        }
                    }
                }
                
                trySend(activeBookings)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error listening to passenger active bookings: $passengerId", error.toException())
                trySend(emptyList())
            }
        }
        
        activeBookingsRef.addValueEventListener(listener)
        
        awaitClose {
            activeBookingsRef.removeEventListener(listener)
        }
    }
    
    /**
     * Get active bookings available for driver assignment (real-time)
     */
    fun getAvailableBookingsForDriver(): Flow<List<ActiveBooking>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val availableBookings = mutableListOf<ActiveBooking>()
                val currentTime = System.currentTimeMillis()
                
                for (child in snapshot.children) {
                    child.getValue(ActiveBooking::class.java)?.let { booking ->
                        // Only include bookings that are searching for drivers and not expired
                        if (booking.status == ActiveBookingStatus.SEARCHING_DRIVER &&
                            booking.expiresAt > currentTime &&
                            booking.assignedDriverId.isNullOrEmpty()) {
                            availableBookings.add(booking)
                        }
                    }
                }
                
                // Sort by request time (oldest first)
                availableBookings.sortBy { it.requestTime }
                trySend(availableBookings)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error listening to available bookings", error.toException())
                trySend(emptyList())
            }
        }
        
        activeBookingsRef.addValueEventListener(listener)
        
        awaitClose {
            activeBookingsRef.removeEventListener(listener)
        }
    }
    
    /**
     * Update driver's real-time location for active booking
     */
    suspend fun updateDriverLocation(
        bookingId: String,
        driverLocation: BookingLocation,
        estimatedArrival: Int? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "driverLocation" to RealtimeLocation(driverLocation),
                "lastUpdated" to System.currentTimeMillis()
            )
            
            estimatedArrival?.let { updates["estimatedDriverArrival"] = it }
            
            activeBookingsRef.child(bookingId).updateChildren(updates).await()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating driver location for booking: $bookingId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clean up expired active bookings
     */
    suspend fun cleanupExpiredBookings(): Result<Int> {
        return try {
            val currentTime = System.currentTimeMillis()
            val snapshot = activeBookingsRef.get().await()
            var cleanedCount = 0
            
            for (child in snapshot.children) {
                child.getValue(ActiveBooking::class.java)?.let { booking ->
                    if (booking.expiresAt <= currentTime) {
                        child.ref.removeValue().await()
                        cleanedCount++
                        Log.d(TAG, "Cleaned expired booking: ${booking.bookingId}")
                    }
                }
            }
            
            Log.d(TAG, "Cleaned up $cleanedCount expired bookings")
            Result.success(cleanedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up expired bookings", e)
            Result.failure(e)
        }
    }
}

/**
 * Location model compatible with Firebase Realtime Database
 * Uses simple lat/lng instead of Firestore GeoPoint
 */
data class RealtimeLocation(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeId: String? = null,
    val placeName: String? = null,
    val placeType: String? = null
) {
    // Convert from BookingLocation (Firestore) to RealtimeLocation (Realtime DB)
    constructor(bookingLocation: BookingLocation) : this(
        address = bookingLocation.address,
        latitude = bookingLocation.coordinates.latitude,
        longitude = bookingLocation.coordinates.longitude,
        placeId = bookingLocation.placeId,
        placeName = bookingLocation.placeName,
        placeType = bookingLocation.placeType
    )

    // Convert to BookingLocation for UI usage
    fun toBookingLocation(): BookingLocation = BookingLocation(
        address = address,
        coordinates = com.google.firebase.firestore.GeoPoint(latitude, longitude),
        placeId = placeId,
        placeName = placeName,
        placeType = placeType
    )
}

/**
 * Fare estimate model compatible with Firebase Realtime Database
 */
data class RealtimeFareEstimate(
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val timeFare: Double = 0.0,
    val surgeFactor: Double = 1.0,
    val totalEstimate: Double = 0.0,
    val currency: String = "PHP",
    val estimatedDuration: Int = 0,
    val estimatedDistance: Double = 0.0
) {
    constructor(fareEstimate: FareEstimate) : this(
        baseFare = fareEstimate.baseFare,
        distanceFare = fareEstimate.distanceFare,
        timeFare = fareEstimate.timeFare,
        surgeFactor = fareEstimate.surgeFactor,
        totalEstimate = fareEstimate.totalEstimate,
        currency = fareEstimate.currency,
        estimatedDuration = fareEstimate.estimatedDuration,
        estimatedDistance = fareEstimate.estimatedDistance
    )

    fun toFareEstimate(): FareEstimate = FareEstimate(
        baseFare = baseFare,
        distanceFare = distanceFare,
        timeFare = timeFare,
        surgeFactor = surgeFactor,
        totalEstimate = totalEstimate,
        currency = currency,
        estimatedDuration = estimatedDuration,
        estimatedDistance = estimatedDistance
    )
}

/**
 * Active booking model for Firebase Realtime Database
 * Optimized for real-time driver matching and status updates
 */
data class ActiveBooking(
    val bookingId: String = "",
    val passengerId: String = "",
    val assignedDriverId: String? = null,
    val pickupLocation: RealtimeLocation = RealtimeLocation(),
    val destination: RealtimeLocation = RealtimeLocation(),
    val driverLocation: RealtimeLocation? = null,
    val fareEstimate: RealtimeFareEstimate = RealtimeFareEstimate(),
    val vehicleCategory: String = "STANDARD", // Use String instead of enum
    val status: ActiveBookingStatus = ActiveBookingStatus.SEARCHING_DRIVER,
    val requestTime: Long = System.currentTimeMillis(),
    val scheduledTime: Long? = null,
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000), // 10 minutes
    val lastUpdated: Long = System.currentTimeMillis(),
    val estimatedDriverArrival: Int? = null, // minutes
    val specialRequests: String = ""
)

/**
 * Active booking status for real-time tracking
 */
enum class ActiveBookingStatus {
    SEARCHING_DRIVER,    // Looking for available drivers
    DRIVER_ASSIGNED,     // Driver has been assigned
    DRIVER_ARRIVING,     // Driver is en route to pickup
    DRIVER_ARRIVED,      // Driver has arrived at pickup location
    IN_PROGRESS,         // Trip is in progress
    COMPLETED,           // Trip completed (will be removed soon)
    CANCELLED,           // Booking cancelled (will be removed soon)
    EXPIRED              // Booking expired (will be removed)
}