package com.rj.islamove.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Ride(
    val id: String = "",
    val passengerId: String = "",
    val driverId: String? = null,
    val pickupLocation: Location = Location(),
    val destination: Location = Location(),
    val status: RideStatus = RideStatus.REQUESTED,
    val fare: Fare = Fare(),
    val requestTime: Long = System.currentTimeMillis(),
    val acceptTime: Long? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val route: Route? = null,
    val paymentMethod: String = "cash",
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val rating: SimpleRating? = null,
    val specialInstructions: String = "",
    val rideType: RideType = RideType.STANDARD
)

@Serializable
data class Fare(
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val timeFare: Double = 0.0,
    val surgeFare: Double = 0.0,
    val totalFare: Double = 0.0,
    val discount: Double = 0.0,
    val finalAmount: Double = 0.0
)

@Serializable
data class Route(
    val distance: Double = 0.0, // in km
    val duration: Long = 0L, // in minutes
    val waypoints: List<Location> = emptyList(),
    val routeGeometry: String = "" // Encoded polyline
)

@Serializable
data class SimpleRating(
    val driverRating: Int? = null,
    val passengerRating: Int? = null,
    val driverFeedback: String = "",
    val passengerFeedback: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class RideStatus {
    REQUESTED,
    ACCEPTED,
    DRIVER_ARRIVED,
    STARTED,
    COMPLETED,
    CANCELLED_BY_PASSENGER,
    CANCELLED_BY_DRIVER,
    NO_DRIVER_FOUND
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

enum class RideType {
    STANDARD,
    PREMIUM,
    SHARE
}