package com.rj.islamove.data.models

import com.google.firebase.firestore.GeoPoint

data class Booking(
    val id: String = "",
    val passengerId: String = "",
    val driverId: String? = null,
    val pickupLocation: BookingLocation = BookingLocation(),
    val destination: BookingLocation = BookingLocation(),
    val fareEstimate: FareEstimate = FareEstimate(),
    val actualFare: Double? = null,
    val status: BookingStatus = BookingStatus.PENDING,
    val requestTime: Long = System.currentTimeMillis(),
    val scheduledTime: Long? = null,
    val pickupTime: Long? = null,
    val completionTime: Long? = null,
    val acceptedTime: Long? = null, // Time when driver accepted the ride
    val paymentStatus: BookingPaymentStatus = BookingPaymentStatus.PENDING,
    val vehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
    val specialInstructions: String = "",
    val route: RouteInfo? = null,
    val cancelledBy: String = "", // "passenger", "driver", or empty if not cancelled
    val canCancelWithoutPenalty: Boolean = true, // Track if passenger can still cancel within 20s window
    val passengerDiscountPercentage: Int? = null // null = no discount, 20, 50, etc.
)

data class BookingLocation(
    val address: String = "",
    val coordinates: GeoPoint = GeoPoint(0.0, 0.0),
    val placeId: String? = null,
    val placeName: String? = null,
    val placeType: String? = null
)

data class FareEstimate(
    val baseFare: Double = 0.0,        // San Jose Municipal Fare Matrix amount
    val distanceFare: Double = 0.0,    // Not used - matrix covers all fares
    val timeFare: Double = 0.0,        // Not used - matrix covers all fares
    val surgeFactor: Double = 1.0,
    val totalEstimate: Double = 0.0,   // Equals baseFare from matrix
    val currency: String = "PHP",
    val estimatedDuration: Int = 0,    // in minutes (for display only)
    val estimatedDistance: Double = 0.0 // in meters (for display only)
)

data class RouteInfo(
    val waypoints: List<GeoPoint> = emptyList(),
    val totalDistance: Double = 0.0, // in meters
    val estimatedDuration: Int = 0, // in minutes
    val routeId: String = "",
    val turnByTurnInstructions: List<NavigationInstruction> = emptyList()
)

data class NavigationInstruction(
    val instruction: String, // "Turn right onto Main Street"
    val distance: Double, // Distance to next instruction in meters
    val duration: Double, // Duration to next instruction in seconds
    val type: String, // "turn", "continue", "arrive", etc.
    val modifier: String = "", // "right", "left", "straight", etc.
    val location: GeoPoint, // Location where instruction should be given
    val bearing: Double = 0.0, // Direction bearing in degrees
    val exitNumber: Int? = null // For roundabouts
)

enum class BookingStatus {
    PENDING,        // Waiting for driver
    LOOKING_FOR_DRIVER, // No drivers online, booking queued
    SCHEDULED,      // Scheduled for future time
    ACCEPTED,       // Driver accepted
    DRIVER_ARRIVING,// Driver is on the way to pickup
    DRIVER_ARRIVED, // Driver at pickup
    IN_PROGRESS,    // Trip started
    COMPLETED,      // Trip completed
    CANCELLED,      // Cancelled by passenger or driver
    EXPIRED         // No driver found within time limit
}

enum class BookingPaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}

enum class VehicleCategory(val displayName: String, val baseMultiplier: Double) {
    STANDARD("Standard", 1.0),
    PREMIUM("Premium", 1.5),
    LARGE("Large Vehicle", 1.3)
}