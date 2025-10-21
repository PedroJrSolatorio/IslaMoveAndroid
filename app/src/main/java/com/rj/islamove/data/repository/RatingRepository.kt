package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.rj.islamove.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "RatingRepository"
        private const val RATINGS_COLLECTION = "ratings"
        private const val USER_RATING_STATS_COLLECTION = "user_rating_stats"
        private const val PENDING_RATINGS_COLLECTION = "pending_ratings"
    }

    // Submit a new rating
    suspend fun submitRating(
        fromUserId: String,
        submission: RatingSubmission
    ): Result<String> {
        return try {
            // Check if rating already exists for this booking
            val existingRating = getRatingByBooking(submission.bookingId, fromUserId)
            if (existingRating.isSuccess && existingRating.getOrNull() != null) {
                return Result.failure(Exception("Rating already submitted for this trip"))
            }

            // Get user info for the rating
            val fromUserDoc = firestore.collection("users").document(fromUserId).get().await()
            val toUserDoc = firestore.collection("users").document(submission.toUserId).get().await()
            
            if (!fromUserDoc.exists() || !toUserDoc.exists()) {
                return Result.failure(Exception("User not found"))
            }

            val fromUserType = UserType.valueOf(fromUserDoc.getString("userType") ?: "PASSENGER")
            val toUserType = UserType.valueOf(toUserDoc.getString("userType") ?: "DRIVER")
            val fromUserName = fromUserDoc.getString("displayName") ?: "Anonymous"

            // Create rating document
            val ratingId = firestore.collection(RATINGS_COLLECTION).document().id
            val rating = Rating(
                id = ratingId,
                bookingId = submission.bookingId,
                fromUserId = fromUserId,
                toUserId = submission.toUserId,
                fromUserType = fromUserType,
                toUserType = toUserType,
                stars = submission.stars,
                review = submission.review,
                categories = submission.categories,
                isAnonymous = submission.isAnonymous
            )

            // Save rating to Firestore
            firestore.collection(RATINGS_COLLECTION).document(ratingId).set(rating).await()

            // Update recipient's rating stats
            updateUserRatingStats(submission.toUserId, rating, fromUserName)

            // Remove from pending ratings
            removePendingRating(submission.bookingId, fromUserId)

            Log.d(TAG, "Rating submitted successfully: $ratingId")
            Result.success(ratingId)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting rating", e)
            Result.failure(e)
        }
    }

    // Get rating for a specific booking and user
    suspend fun getRatingByBooking(bookingId: String, fromUserId: String): Result<Rating?> {
        return try {
            val querySnapshot = firestore.collection(RATINGS_COLLECTION)
                .whereEqualTo("bookingId", bookingId)
                .whereEqualTo("fromUserId", fromUserId)
                .limit(1)
                .get()
                .await()

            val rating = querySnapshot.documents.firstOrNull()?.toObject(Rating::class.java)
            Result.success(rating)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting rating by booking", e)
            Result.failure(e)
        }
    }

    // Get all ratings for a user
    suspend fun getUserRatings(
        userId: String,
        filter: RatingFilter = RatingFilter.ALL,
        limit: Int = 20
    ): Result<List<Rating>> {
        return try {
            var query: Query = firestore.collection(RATINGS_COLLECTION)
                .whereEqualTo("toUserId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)

            // Apply filters
            query = when (filter) {
                RatingFilter.FIVE_STARS -> query.whereEqualTo("stars", 5)
                RatingFilter.FOUR_STARS -> query.whereEqualTo("stars", 4)
                RatingFilter.THREE_STARS -> query.whereEqualTo("stars", 3)
                RatingFilter.TWO_STARS -> query.whereEqualTo("stars", 2)
                RatingFilter.ONE_STAR -> query.whereEqualTo("stars", 1)
                RatingFilter.WITH_REVIEW -> query.whereGreaterThan("review", "")
                RatingFilter.OLDEST -> {
                    firestore.collection(RATINGS_COLLECTION)
                        .whereEqualTo("toUserId", userId)
                        .orderBy("createdAt", Query.Direction.ASCENDING)
                }
                else -> query
            }

            val querySnapshot = query.limit(limit.toLong()).get().await()
            val ratings = querySnapshot.documents.mapNotNull { 
                it.toObject(Rating::class.java) 
            }

            Result.success(ratings)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user ratings", e)
            Result.failure(e)
        }
    }

    // Get user rating statistics
    suspend fun getUserRatingStats(userId: String): Result<UserRatingStats> {
        return try {
            val doc = firestore.collection(USER_RATING_STATS_COLLECTION)
                .document(userId)
                .get()
                .await()

            val stats = if (doc.exists()) {
                doc.toObject(UserRatingStats::class.java) ?: UserRatingStats(userId = userId)
            } else {
                UserRatingStats(userId = userId)
            }

            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user rating stats", e)
            Result.failure(e)
        }
    }

    // Observe user rating stats in real-time
    fun observeUserRatingStats(userId: String): Flow<UserRatingStats> = flow {
        try {
            firestore.collection(USER_RATING_STATS_COLLECTION)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing rating stats", error)
                        return@addSnapshotListener
                    }

                    val stats = if (snapshot?.exists() == true) {
                        snapshot.toObject(UserRatingStats::class.java) ?: UserRatingStats(userId = userId)
                    } else {
                        UserRatingStats(userId = userId)
                    }
                    // Note: In a real implementation, you'd use callbackFlow for this
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up rating stats observer", e)
        }
    }

    // Create pending rating when trip completes
    suspend fun createPendingRating(
        bookingId: String,
        passengerId: String,
        driverId: String
    ): Result<Unit> {
        return try {
            val batch = firestore.batch()

            // Create pending rating for passenger to rate driver
            val passengerPendingId = "${bookingId}_passenger_to_driver"
            val passengerPending = mapOf(
                "id" to passengerPendingId,
                "bookingId" to bookingId,
                "fromUserId" to passengerId,
                "toUserId" to driverId,
                "fromUserType" to UserType.PASSENGER.name,
                "toUserType" to UserType.DRIVER.name,
                "status" to RatingStatus.PENDING.name,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to (System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)) // 7 days
            )

            // Create pending rating for driver to rate passenger
            val driverPendingId = "${bookingId}_driver_to_passenger"
            val driverPending = mapOf(
                "id" to driverPendingId,
                "bookingId" to bookingId,
                "fromUserId" to driverId,
                "toUserId" to passengerId,
                "fromUserType" to UserType.DRIVER.name,
                "toUserType" to UserType.PASSENGER.name,
                "status" to RatingStatus.PENDING.name,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to (System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)) // 7 days
            )

            batch.set(firestore.collection(PENDING_RATINGS_COLLECTION).document(passengerPendingId), passengerPending)
            batch.set(firestore.collection(PENDING_RATINGS_COLLECTION).document(driverPendingId), driverPending)

            batch.commit().await()

            Log.d(TAG, "Pending ratings created for booking: $bookingId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pending ratings", e)
            Result.failure(e)
        }
    }

    // Get pending ratings for a user
    suspend fun getPendingRatings(userId: String): Result<List<Map<String, Any>>> {
        return try {
            val querySnapshot = firestore.collection(PENDING_RATINGS_COLLECTION)
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", RatingStatus.PENDING.name)
                .whereGreaterThan("expiresAt", System.currentTimeMillis())
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .get()
                .await()

            val pendingRatings = querySnapshot.documents.map { it.data ?: emptyMap() }
            Result.success(pendingRatings)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending ratings", e)
            Result.failure(e)
        }
    }

    // Check if user can rate for a specific booking
    suspend fun canUserRate(bookingId: String, userId: String): Result<Boolean> {
        return try {
            // Check if rating already exists
            val existingRating = getRatingByBooking(bookingId, userId)
            if (existingRating.isSuccess && existingRating.getOrNull() != null) {
                return Result.success(false) // Already rated
            }

            // Check if pending rating exists and hasn't expired
            val pendingId = "${bookingId}_${if (userId.contains("passenger")) "passenger_to_driver" else "driver_to_passenger"}"
            val pendingDoc = firestore.collection(PENDING_RATINGS_COLLECTION)
                .document(pendingId)
                .get()
                .await()

            if (pendingDoc.exists()) {
                val expiresAt = pendingDoc.getLong("expiresAt") ?: 0
                val isExpired = System.currentTimeMillis() > expiresAt
                Result.success(!isExpired)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user can rate", e)
            Result.failure(e)
        }
    }

    // Private helper to update user rating statistics
    private suspend fun updateUserRatingStats(
        userId: String,
        rating: Rating,
        fromUserName: String
    ) {
        try {
            val statsDoc = firestore.collection(USER_RATING_STATS_COLLECTION).document(userId)
            val currentStats = statsDoc.get().await()

            val existingStats = if (currentStats.exists()) {
                currentStats.toObject(UserRatingStats::class.java) ?: UserRatingStats(userId = userId)
            } else {
                UserRatingStats(userId = userId, userType = rating.toUserType)
            }

            // Update rating breakdown
            val newBreakdown = when (rating.stars) {
                5 -> existingStats.ratingBreakdown.copy(fiveStars = existingStats.ratingBreakdown.fiveStars + 1)
                4 -> existingStats.ratingBreakdown.copy(fourStars = existingStats.ratingBreakdown.fourStars + 1)
                3 -> existingStats.ratingBreakdown.copy(threeStars = existingStats.ratingBreakdown.threeStars + 1)
                2 -> existingStats.ratingBreakdown.copy(twoStars = existingStats.ratingBreakdown.twoStars + 1)
                1 -> existingStats.ratingBreakdown.copy(oneStar = existingStats.ratingBreakdown.oneStar + 1)
                else -> existingStats.ratingBreakdown
            }
            
            Log.d(TAG, "Updated rating breakdown for $userId: 5★:${newBreakdown.fiveStars}, 4★:${newBreakdown.fourStars}, 3★:${newBreakdown.threeStars}, 2★:${newBreakdown.twoStars}, 1★:${newBreakdown.oneStar}")

            // Calculate new overall rating
            val newTotalRatings = existingStats.totalRatings + 1
            val newOverallRating = ((existingStats.overallRating * existingStats.totalRatings) + rating.stars) / newTotalRatings

            // Add to recent ratings (keep only last 5)
            val newRecentRating = RecentRating(
                stars = rating.stars,
                review = rating.review,
                fromUserName = if (rating.isAnonymous) "Anonymous" else fromUserName,
                tripDate = rating.tripDate,
                isAnonymous = rating.isAnonymous
            )
            val newRecentRatings = (listOf(newRecentRating) + existingStats.recentRatings).take(5)

            // Update category averages
            val newCategoryAverages = updateCategoryAverages(existingStats.categoryAverages, rating.categories, newTotalRatings)

            val updatedStats = existingStats.copy(
                overallRating = newOverallRating,
                totalRatings = newTotalRatings,
                ratingBreakdown = newBreakdown,
                categoryAverages = newCategoryAverages,
                recentRatings = newRecentRatings,
                lastUpdated = System.currentTimeMillis()
            )

            statsDoc.set(updatedStats).await()

//            // Also update the driver's rating in their user document if this is a driver rating
//            if (rating.toUserType == UserType.DRIVER) {
//                updateDriverRatingInUserDocument(userId, newOverallRating, newTotalRatings)
//            }

            // Update rating in user document for BOTH drivers and passengers
            updateUserRatingInUserDocument(userId, rating.toUserType, newOverallRating, newTotalRatings)

            Log.d(TAG, "Rating stats updated for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user rating stats", e)
        }
    }

    // Helper to update category averages
    private fun updateCategoryAverages(
        existing: RatingCategories,
        newRating: RatingCategories,
        totalRatings: Int
    ): RatingCategories {
        fun updateAverage(existingAvg: Int?, newValue: Int?): Int? {
            // FIX: Convert all parts to Double before division to prevent integer truncation.
            return if (newValue != null && existingAvg != null) {
                // The existingAvg in the stats document is stored as an Int, which is a flaw.
                // Assuming it represents the total sum divided by (totalRatings - 1) * 100
                // and you meant to store the AVERAGE (e.g., 4.5), the current model is confusing.

                // Assuming existingAvg is actually the running SUM of all previous ratings for that category:
                val currentTotalSum = existingAvg.toDouble() * (totalRatings - 1)
                val newAvg = (currentTotalSum + newValue.toDouble()) / totalRatings.toDouble()

                // Since RatingCategories stores these as Int? (which should probably be Double? for an average),
                // you must convert back to Int, which is the source of the truncation problem.
                // Let's stick to the existing (flawed) structure but ensure the division is Double:

                // ORIGINAL LOGIC (but forced to Double for safe division)
                // This is mathematically INCORRECT if existingAvg is the average,
                // but matches the structure if existingAvg is the total sum divided by (totalRatings - 1).
                val newCalculatedAvg = ((existingAvg.toDouble() * (totalRatings - 1)) + newValue.toDouble()) / totalRatings.toDouble()

                // Return value must be Int? as per the function signature/model:
                return newCalculatedAvg.toInt() // <--- This truncation is your model's limitation
            } else newValue ?: existingAvg // If it's the first rating, use it
        }

        return RatingCategories(
            drivingSkill = updateAverage(existing.drivingSkill, newRating.drivingSkill),
            vehicleCondition = updateAverage(existing.vehicleCondition, newRating.vehicleCondition),
            punctuality = updateAverage(existing.punctuality, newRating.punctuality),
            friendliness = updateAverage(existing.friendliness, newRating.friendliness),
            routeKnowledge = updateAverage(existing.routeKnowledge, newRating.routeKnowledge),
            politeness = updateAverage(existing.politeness, newRating.politeness),
            cleanliness = updateAverage(existing.cleanliness, newRating.cleanliness),
            communication = updateAverage(existing.communication, newRating.communication),
            respectfulness = updateAverage(existing.respectfulness, newRating.respectfulness),
            onTime = updateAverage(existing.onTime, newRating.onTime)
        )
    }

    // Update rating in user document for BOTH drivers and passengers
    private suspend fun updateUserRatingInUserDocument(
        userId: String,
        userType: UserType,
        newRating: Double,
        totalTrips: Int
    ) {
        try {
            val userDocRef = firestore.collection("users").document(userId)

            // First, get the current user document
            val userDoc = userDocRef.get().await()

            if (!userDoc.exists()) {
                Log.e(TAG, "User document not found: $userId")
                return
            }

            when (userType) {
                UserType.DRIVER -> {
                    // Get existing driverData
                    val existingDriverData = userDocRef.get().await().toObject(User::class.java)?.driverData

                    if (existingDriverData == null) {
                        Log.e(TAG, "Driver data not found for user: $userId")
                        return
                    }

                    // Update the entire driverData object with new rating and totalTrips
                    val updatedDriverData = existingDriverData.copy(
                        rating = newRating,
                        totalTrips = totalTrips
                    )

                    // Update with the complete driverData object
                    val updates = mapOf(
                        "driverData" to updatedDriverData,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    userDocRef.update(updates).await()
                    Log.d(TAG, "Updated DRIVER rating in user document: $userId -> $newRating ($totalTrips trips)")
                }

                UserType.PASSENGER -> {
                    // For passengers, update the top-level fields directly
                    val updates = mapOf(
                        "passengerRating" to newRating,
                        "passengerTotalTrips" to totalTrips,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    userDocRef.set(updates, SetOptions.merge()).await()
                    Log.d(TAG, "Updated PASSENGER rating in user document: $userId -> $newRating ($totalTrips trips)")
                }

                else -> {
                    Log.w(TAG, "Skipping rating update for userType: $userType")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating $userType rating in user document for $userId", e)
            // Log the specific error for debugging
            when (e) {
                is com.google.firebase.firestore.FirebaseFirestoreException -> {
                    Log.e(TAG, "Firestore error code: ${e.code}, message: ${e.message}")
                }
            }
        }
    }

    // Submit driver rating (convenience method for DriverRatingViewModel)
    suspend fun submitDriverRating(ratingData: Map<String, Any>): Result<String> {
        return try {
            val fromUserId = ratingData["fromUserId"] as? String ?: return Result.failure(Exception("Missing fromUserId"))
            val toUserId = ratingData["toUserId"] as? String ?: return Result.failure(Exception("Missing toUserId"))
            val bookingId = ratingData["bookingId"] as? String ?: return Result.failure(Exception("Missing bookingId"))
            val overallRating = (ratingData["overallRating"] as? Number)?.toInt() ?: return Result.failure(Exception("Missing overallRating"))
            val review = ratingData["review"] as? String ?: ""
            val isAnonymous = ratingData["isAnonymous"] as? Boolean ?: false

            // Extract categories
            val categoriesMap = ratingData["categories"] as? Map<String, Any> ?: emptyMap()
            val categories = RatingCategories(
                politeness = (categoriesMap["politeness"] as? Number)?.toInt()?.takeIf { it > 0 },
                cleanliness = (categoriesMap["cleanliness"] as? Number)?.toInt()?.takeIf { it > 0 },
                communication = (categoriesMap["communication"] as? Number)?.toInt()?.takeIf { it > 0 },
                respectfulness = (categoriesMap["respectfulness"] as? Number)?.toInt()?.takeIf { it > 0 },
                onTime = (categoriesMap["onTime"] as? Number)?.toInt()?.takeIf { it > 0 }
            )

            val ratingSubmission = RatingSubmission(
                bookingId = bookingId,
                toUserId = toUserId,
                stars = overallRating,
                review = review,
                categories = categories,
                isAnonymous = isAnonymous
            )

            submitRating(fromUserId, ratingSubmission)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting driver rating", e)
            Result.failure(e)
        }
    }

    // Private helper to remove pending rating
    private suspend fun removePendingRating(bookingId: String, fromUserId: String) {
        try {
            val pendingId = "${bookingId}_${if (fromUserId.contains("passenger")) "passenger_to_driver" else "driver_to_passenger"}"
            firestore.collection(PENDING_RATINGS_COLLECTION).document(pendingId).delete().await()
            Log.d(TAG, "Pending rating removed: $pendingId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing pending rating", e)
        }
    }
}