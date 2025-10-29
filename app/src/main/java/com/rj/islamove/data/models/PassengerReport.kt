package com.rj.islamove.data.models

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@Serializable
@IgnoreExtraProperties
data class PassengerReport(
    val reportId: String = "",
    val passengerId: String = "",
    val reportedBy: String = "", // Driver UID who reported
    val reporterType: String = "driver",
    val bookingId: String = "",
    val reportType: ReportType = ReportType.OTHER,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending", // pending, reviewed, resolved
    val adminNotes: String = "",
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null
)