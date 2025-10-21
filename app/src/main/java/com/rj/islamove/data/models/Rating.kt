package com.rj.islamove.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Rating(
    val id: String = "",
    val bookingId: String = "",
    val fromUserId: String = "", // Who gave the rating
    val toUserId: String = "", // Who received the rating
    val fromUserType: UserType = UserType.PASSENGER, // Who gave the rating (PASSENGER or DRIVER)
    val toUserType: UserType = UserType.DRIVER, // Who received the rating
    val stars: Int = 5, // Rating from 1 to 5
    val review: String = "", // Optional written review
    val categories: RatingCategories = RatingCategories(), // Detailed rating breakdown
    val isAnonymous: Boolean = true, // Anonymous by default for honest feedback
    val tripDate: Long = System.currentTimeMillis(), // Date of the actual trip
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isReported: Boolean = false, // For inappropriate reviews
    val adminNotes: String = "" // Admin moderation notes
)

@Serializable
data class RatingCategories(
    // For rating drivers (from passenger perspective)
    val drivingSkill: Int? = null, // 1-5 stars
    val vehicleCondition: Int? = null, // 1-5 stars
    val punctuality: Int? = null, // 1-5 stars
    val friendliness: Int? = null, // 1-5 stars
    val routeKnowledge: Int? = null, // 1-5 stars
    
    // For rating passengers (from driver perspective)
    val politeness: Int? = null, // 1-5 stars
    val cleanliness: Int? = null, // 1-5 stars
    val communication: Int? = null, // 1-5 stars
    val respectfulness: Int? = null, // 1-5 stars
    val onTime: Int? = null // 1-5 stars (was passenger ready on time)
)

@Serializable
data class UserRatingStats(
    val userId: String = "",
    val userType: UserType = UserType.PASSENGER,
    val overallRating: Double = 0.0, // Average of all ratings
    val totalRatings: Int = 0,
    val ratingBreakdown: RatingBreakdown = RatingBreakdown(),
    val categoryAverages: RatingCategories = RatingCategories(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val recentRatings: List<RecentRating> = emptyList() // Last 5 ratings for display
)

@Serializable
data class RatingBreakdown(
    val fiveStars: Int = 0,
    val fourStars: Int = 0,
    val threeStars: Int = 0,
    val twoStars: Int = 0,
    val oneStar: Int = 0
)

@Serializable
data class RecentRating(
    val stars: Int = 5,
    val review: String = "",
    val fromUserName: String = "", // Display name of reviewer
    val tripDate: Long = System.currentTimeMillis(),
    val isAnonymous: Boolean = true
)

@Serializable
data class RatingSubmission(
    val bookingId: String = "",
    val toUserId: String = "",
    val stars: Int = 5,
    val review: String = "",
    val categories: RatingCategories = RatingCategories(),
    val isAnonymous: Boolean = true
)

enum class RatingFilter {
    ALL,
    FIVE_STARS,
    FOUR_STARS,
    THREE_STARS,
    TWO_STARS,
    ONE_STAR,
    WITH_REVIEW,
    WITHOUT_REVIEW,
    RECENT,
    OLDEST
}

enum class RatingStatus {
    PENDING,    // Waiting for user to submit rating
    COMPLETED,  // Rating has been submitted
    EXPIRED     // Rating period expired (e.g., after 7 days)
}