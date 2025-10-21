package com.rj.islamove.data.models

import kotlinx.serialization.Serializable
import com.google.firebase.firestore.IgnoreExtraProperties

@Serializable
@IgnoreExtraProperties
data class DriverReport(
    val id: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val rideId: String = "",
    val reportType: ReportType = ReportType.OTHER,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: ReportStatus = ReportStatus.PENDING
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", "", "", "", ReportType.OTHER, "", 0L, ReportStatus.PENDING)
}

enum class ReportType {
    UNSAFE_DRIVING,
    INAPPROPRIATE_BEHAVIOR,
    VEHICLE_CONDITION,
    ROUTE_ISSUES,
    HYGIENE_CONCERNS,
    OVERCHARGING,
    CANCELLATION_ABUSE,
    OTHER
}

enum class ReportStatus {
    PENDING,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}