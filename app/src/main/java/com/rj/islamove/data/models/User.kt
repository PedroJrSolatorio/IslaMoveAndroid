package com.rj.islamove.data.models

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@Serializable
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val phoneNumber: String = "",
    val email: String? = null,
    val plainTextPassword: String? = null,
    val displayName: String = "",
    val profileImageUrl: String? = null,
    val address: String? = null,
    val userType: UserType = UserType.PASSENGER,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Driver specific fields
    val driverData: DriverData? = null,

    // Passenger specific fields
    val studentDocument: StudentDocument? = null,
    val dateOfBirth: String? = null, // Format: YYYY-MM-DD
    val gender: String? = null, // Options: "Male", "Female", "Other"
    val passengerRating: Double = 0.0,
    val passengerTotalTrips: Int = 0,

    // Discount settings (for passengers)
    val discountPercentage: Int? = null, // null = no discount, 20, 50, etc.

    // Preferences
    val preferences: UserPreferences = UserPreferences()
)

@Serializable
@IgnoreExtraProperties
data class DriverData(
    val licenseNumber: String = "",
    val vehicleData: VehicleData = VehicleData(),
    val documentsVerified: Boolean = false,
    val online: Boolean = false,
    val currentLocation: Location? = null,
    val rating: Double = 0.0,
    val totalTrips: Int = 0,
    val totalEarnings: Double = 0.0,
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING,
    // Document URLs from Cloudinary
    val documents: Map<String, DriverDocument> = emptyMap(), // documentType -> DriverDocument
    val verificationNotes: String = "",
    val verificationDate: Long? = null,
    val verifiedBy: String? = null // Admin UID who verified
)

@Serializable
data class DriverDocument(
    val images: List<DocumentImage> = emptyList(),
    val status: DocumentStatus = DocumentStatus.PENDING_REVIEW,
    val rejectionReason: String? = null,
    val uploadedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DocumentImage(
    val url: String = "",
    val description: String = "", // e.g., "front", "back", "selfie"
    val uploadedAt: Long = System.currentTimeMillis()
)

@Serializable
data class StudentDocument(
    val studentIdUrl: String = "",
    val studentIdNumber: String = "",
    val school: String = "",
    val status: DocumentStatus = DocumentStatus.PENDING_REVIEW,
    val rejectionReason: String? = null,
    val uploadedAt: Long = System.currentTimeMillis(),
    val verificationDate: Long? = null,
    val verifiedBy: String? = null // Admin UID who verified
)

@Serializable
data class VehicleData(
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val color: String = "",
    val plateNumber: String = "",
    val vehicleType: VehicleType = VehicleType.SEDAN
)

@Serializable
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val preferredPaymentMethod: String = "cash",
    val language: String = "en",
    val savedPlaces: Map<String, BookingLocation> = emptyMap(),
    val cancellationCount: Int = 0, // Track passenger cancellation count (max 3 per day)
    val lastCancellationDate: String? = null, // Track last cancellation date (YYYY-MM-DD format)
    val serviceBoundary: UserServiceBoundary? = null // User-defined service area boundary
)

@Serializable
data class UserServiceBoundary(
    val name: String = "",
    val points: List<BoundaryPointData> = emptyList(), // Polygon boundary points
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class BoundaryPointData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

enum class UserType {
    PASSENGER, DRIVER, ADMIN
}

enum class VehicleType {
    SEDAN, SUV, HATCHBACK, MOTORCYCLE
}

enum class VerificationStatus {
    PENDING, APPROVED, REJECTED, UNDER_REVIEW
}

enum class DocumentStatus {
    PENDING,           // Just uploaded, not submitted for review yet
    PENDING_REVIEW,    // Submitted for admin review
    APPROVED,
    REJECTED
}