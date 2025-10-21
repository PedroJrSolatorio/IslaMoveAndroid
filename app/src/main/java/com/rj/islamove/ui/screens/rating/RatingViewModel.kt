package com.rj.islamove.ui.screens.rating

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.*
import com.rj.islamove.data.repository.AuthRepository
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.RatingRepository
import com.rj.islamove.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingUiState(
    val bookingId: String = "",
    val toUserId: String = "",
    val toUserType: UserType = UserType.DRIVER,
    val toUserName: String = "",
    val overallRating: Int = 0,
    val categoryRatings: RatingCategories = RatingCategories(),
    val review: String = "",
    val isAnonymous: Boolean = true, // Default to anonymous for honest feedback
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val error: String? = null,
    val booking: Booking? = null,
    val driverUser: User? = null,
    val driverRatingStats: UserRatingStats? = null,
    val passengerUser: User? = null,
    val passengerRatingStats: UserRatingStats? = null
)

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val ratingRepository: RatingRepository,
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RatingViewModel"
    }

    private val _uiState = MutableStateFlow(RatingUiState())
    val uiState: StateFlow<RatingUiState> = _uiState.asStateFlow()

    fun initializeRating(bookingId: String, toUserId: String, toUserType: UserType) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initializing rating: bookingId=$bookingId, toUserId=$toUserId, toUserType=$toUserType")

                // Check if we need to lookup the actual user ID from booking
                if (toUserId.isBlank() || toUserId == "LOOKUP_REQUIRED") {
                    Log.d(TAG, "toUserId is missing, looking up from booking...")
                    lookupUserIdFromBooking(bookingId, toUserType)
                } else {
                    _uiState.value = _uiState.value.copy(
                        bookingId = bookingId,
                        toUserId = toUserId,
                        toUserType = toUserType,
                        error = null
                    )

                    // Load booking details
                    loadBookingDetails(bookingId)

                    // Load user details
                    loadUserDetails(toUserId)

                    // Check if user can still rate (not expired)
                    checkRatingEligibility(bookingId)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing rating", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load trip details: ${e.message}"
                )
            }
        }
    }

    private suspend fun lookupUserIdFromBooking(bookingId: String, toUserType: UserType) {
        Log.d(TAG, "Looking up user ID from booking for toUserType: $toUserType")
        bookingRepository.getBooking(bookingId).fold(
            onSuccess = { booking ->
                if (booking != null) {
                    // Determine which user ID to use based on who we're rating
                    val actualToUserId = when (toUserType) {
                        UserType.PASSENGER -> booking.passengerId
                        UserType.DRIVER -> booking.driverId
                        UserType.ADMIN -> null // Admins don't get rated
                    }

                    Log.d(TAG, "Booking lookup result:")
                    Log.d(TAG, "  Booking ID: ${booking.id}")
                    Log.d(TAG, "  Passenger ID: '${booking.passengerId}'")
                    Log.d(TAG, "  Driver ID: '${booking.driverId}'")
                    Log.d(TAG, "  Resolved toUserId: '$actualToUserId' for toUserType: $toUserType")

                    if (!actualToUserId.isNullOrBlank()) {
                        Log.d(TAG, "Successfully looked up user ID: $actualToUserId")
                        // Initialize normally with the looked-up user ID
                        _uiState.value = _uiState.value.copy(
                            bookingId = bookingId,
                            toUserId = actualToUserId,
                            toUserType = toUserType,
                            error = null
                        )

                        loadBookingDetails(bookingId)
                        loadUserDetails(actualToUserId)
                        checkRatingEligibility(bookingId)
                    } else {
                        Log.e(TAG, "User ID is still blank after lookup")
                        _uiState.value = _uiState.value.copy(
                            error = "Unable to find ${toUserType.name.lowercase()} information for this trip"
                        )
                    }
                } else {
                    Log.e(TAG, "Booking not found")
                    _uiState.value = _uiState.value.copy(
                        error = "Trip information not found"
                    )
                }
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to lookup booking", exception)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load trip information: ${exception.message}"
                )
            }
        )
    }

    private suspend fun loadBookingDetails(bookingId: String) {
        bookingRepository.getBooking(bookingId).fold(
            onSuccess = { booking ->
                _uiState.value = _uiState.value.copy(booking = booking)
            },
            onFailure = { error ->
                Log.w(TAG, "Could not load booking details", error)
                // Not critical for rating, continue anyway
            }
        )
    }

    private suspend fun loadUserDetails(userId: String) {
        userRepository.getUserByUid(userId).fold(
            onSuccess = { user ->
                val isDriver = user.userType == UserType.DRIVER

                Log.d(TAG, "User details loaded: ${user.displayName} (${user.userType})")

                _uiState.value = _uiState.value.copy(
                    toUserName = user.displayName.ifEmpty { "User" },
                    driverUser = if (isDriver) user else null,
                    passengerUser = if (!isDriver) user else null
                )

                // Load rating stats based on user type
                if (isDriver) {
                    loadDriverRatingStats(userId)
                } else {
                    loadPassengerRatingStats(userId)
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Could not load user details", error)
                _uiState.value = _uiState.value.copy(toUserName = "User")
            }
        )
    }

    private suspend fun loadDriverRatingStats(driverId: String) {
        Log.d(TAG, "Loading driver rating stats for driver: $driverId")
        ratingRepository.getUserRatingStats(driverId).fold(
            onSuccess = { stats ->
                Log.d(TAG, "Driver rating stats loaded successfully:")
                Log.d(TAG, "  - Overall rating: ${stats.overallRating}")
                Log.d(TAG, "  - Total ratings: ${stats.totalRatings}")
                Log.d(TAG, "  - Rating breakdown: 5★:${stats.ratingBreakdown.fiveStars}, 4★:${stats.ratingBreakdown.fourStars}, 3★:${stats.ratingBreakdown.threeStars}, 2★:${stats.ratingBreakdown.twoStars}, 1★:${stats.ratingBreakdown.oneStar}")
                
                _uiState.value = _uiState.value.copy(driverRatingStats = stats)
                println("DEBUG: Driver stats loaded - Overall: ${stats.overallRating}, Total: ${stats.totalRatings}")
            },
            onFailure = { error ->
                Log.w(TAG, "Could not load driver rating stats for driver $driverId: ${error.message}", error)
                println("DEBUG: Failed to load driver stats: ${error.message}")
                // Not critical, continue without stats
            }
        )
    }

    private suspend fun loadPassengerRatingStats(passengerId: String) {
        Log.d(TAG, "Loading passenger rating stats for passenger: $passengerId")

        ratingRepository.getUserRatingStats(passengerId).fold(
            onSuccess = { stats ->
                Log.d(TAG, "Passenger rating stats loaded successfully:")
                Log.d(TAG, "  - Overall rating: ${stats.overallRating}")
                Log.d(TAG, "  - Total ratings: ${stats.totalRatings}")
                Log.d(TAG, "  - Rating breakdown: 5★:${stats.ratingBreakdown.fiveStars}, 4★:${stats.ratingBreakdown.fourStars}, 3★:${stats.ratingBreakdown.threeStars}, 2★:${stats.ratingBreakdown.twoStars}, 1★:${stats.ratingBreakdown.oneStar}")

                // Check if stats are empty but should have data
                if (stats.totalRatings == 0 && stats.overallRating == 0.0) {
                    Log.w(TAG, "WARNING: Passenger rating stats are empty. This may indicate the rating aggregation system is not working properly.")
                    Log.w(TAG, "The passenger may have ratings in the 'ratings' collection that are not being aggregated into 'user_rating_stats'.")
                }

                _uiState.value = _uiState.value.copy(passengerRatingStats = stats)
                println("DEBUG: Passenger stats loaded - Overall: ${stats.overallRating}, Total: ${stats.totalRatings}")
            },
            onFailure = { error ->
                Log.w(TAG, "Could not load passenger rating stats for passenger $passengerId: ${error.message}", error)
                println("DEBUG: Failed to load passenger stats: ${error.message}")
                // Not critical, continue without stats
            }
        )
    }

    private suspend fun checkRatingEligibility(bookingId: String) {
        val currentUserId = authRepository.getCurrentUser()?.uid ?: return
        
        ratingRepository.canUserRate(bookingId, currentUserId).fold(
            onSuccess = { canRate ->
                if (!canRate) {
                    _uiState.value = _uiState.value.copy(
                        error = "Rating period has expired or you have already rated this trip."
                    )
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Could not check rating eligibility", error)
                // Continue anyway - let server validation handle it
            }
        )
    }

    fun updateOverallRating(rating: Int) {
        if (rating in 1..5) {
            _uiState.value = _uiState.value.copy(
                overallRating = rating,
                error = null // Clear any previous errors
            )
        }
    }

    fun updateCategoryRating(category: String, rating: Int) {
        if (rating !in 1..5) return

        val currentCategories = _uiState.value.categoryRatings
        val updatedCategories = when (category) {
            "drivingSkill" -> currentCategories.copy(drivingSkill = rating)
            "vehicleCondition" -> currentCategories.copy(vehicleCondition = rating)
            "punctuality" -> currentCategories.copy(punctuality = rating)
            "friendliness" -> currentCategories.copy(friendliness = rating)
            "routeKnowledge" -> currentCategories.copy(routeKnowledge = rating)
            "politeness" -> currentCategories.copy(politeness = rating)
            "cleanliness" -> currentCategories.copy(cleanliness = rating)
            "communication" -> currentCategories.copy(communication = rating)
            "respectfulness" -> currentCategories.copy(respectfulness = rating)
            "onTime" -> currentCategories.copy(onTime = rating)
            else -> currentCategories
        }

        _uiState.value = _uiState.value.copy(categoryRatings = updatedCategories)
    }

    fun updateReview(review: String) {
        // Limit review to 500 characters
        val limitedReview = if (review.length > 500) review.take(500) else review
        _uiState.value = _uiState.value.copy(review = limitedReview)
    }

    fun updateAnonymous(isAnonymous: Boolean) {
        _uiState.value = _uiState.value.copy(isAnonymous = isAnonymous)
    }

    fun submitRating() {
        try {
            Log.d(TAG, "submitRating() called")
            val currentState = _uiState.value
            Log.d(TAG, "Current state - rating: ${currentState.overallRating}, submitting: ${currentState.isSubmitting}")
            
            // Validation
            if (currentState.overallRating == 0) {
                Log.w(TAG, "Rating validation failed: No overall rating")
                _uiState.value = currentState.copy(error = "Please provide an overall rating")
                return
            }

            if (currentState.isSubmitting) {
                Log.w(TAG, "Rating submission already in progress")
                return
            }

            Log.d(TAG, "Starting rating submission...")
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Setting submitting state to true")
                    _uiState.value = currentState.copy(
                        isSubmitting = true,
                        error = null
                    )

                    Log.d(TAG, "Getting current user...")
                    val currentUserId = authRepository.getCurrentUser()?.uid
                    if (currentUserId == null) {
                        Log.e(TAG, "User not authenticated")
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            error = "User not authenticated"
                        )
                        return@launch
                    }
                    Log.d(TAG, "Current user ID: $currentUserId")

                    Log.d(TAG, "Creating rating submission...")
                    val ratingSubmission = RatingSubmission(
                        bookingId = currentState.bookingId,
                        toUserId = currentState.toUserId,
                        stars = currentState.overallRating,
                        review = currentState.review.trim(),
                        categories = currentState.categoryRatings,
                        isAnonymous = currentState.isAnonymous
                    )
                    Log.d(TAG, "Rating submission created: $ratingSubmission")

                    Log.d(TAG, "Submitting rating to repository...")
                    ratingRepository.submitRating(currentUserId, ratingSubmission).fold(
                        onSuccess = { ratingId ->
                            Log.d(TAG, "Rating submitted successfully: $ratingId")
                            println("DEBUG: Repository returned success, updating state...")
                            val newState = _uiState.value.copy(
                                isSubmitting = false,
                                isSubmitted = true,
                                error = null
                            )
                            _uiState.value = newState
                            Log.d(TAG, "State updated - isSubmitted: ${newState.isSubmitted}")
                            println("DEBUG: State AFTER update - isSubmitted = ${_uiState.value.isSubmitted}")
                            println("DEBUG: Rating submission successful, isSubmitted = ${newState.isSubmitted}")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Error submitting rating", error)
                            println("DEBUG: Rating submission failed: ${error.message}")
                            
                            // If user has already rated, treat it as success for navigation purposes
                            val isAlreadyRated = error.message?.contains("already submitted") == true ||
                                                error.message?.contains("already rated") == true
                            
                            if (isAlreadyRated) {
                                println("DEBUG: User already rated - treating as success for navigation")
                                _uiState.value = _uiState.value.copy(
                                    isSubmitting = false,
                                    isSubmitted = true, // Allow navigation
                                    error = null // Clear error so navigation can proceed
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isSubmitting = false,
                                    error = when {
                                        error.message?.contains("not found") == true -> 
                                            "Trip or user not found"
                                        error.message?.contains("expired") == true -> 
                                            "Rating period has expired"
                                        else -> "Failed to submit rating: ${error.message}"
                                    }
                                )
                            }
                        }
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in viewModelScope.launch", e)
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in submitRating()", e)
            try {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = "Critical error occurred: ${e.message}"
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to update error state", e2)
            }
        }
    }

    // Helper function to get rating description
    fun getRatingDescription(rating: Int): String {
        return when (rating) {
            5 -> "Excellent"
            4 -> "Good"
            3 -> "Average"
            2 -> "Below Average"
            1 -> "Poor"
            else -> ""
        }
    }

    // Helper function to check if all required fields are filled
    fun isRatingComplete(): Boolean {
        return _uiState.value.overallRating > 0
    }

    // Helper function to get completion percentage
    fun getCompletionPercentage(): Float {
        val state = _uiState.value
        var completed = 0
        var total = 2 // Overall rating + at least one category rating

        // Overall rating
        if (state.overallRating > 0) completed++

        // Category ratings (at least one)
        val hasAnyCategoryRating = with(state.categoryRatings) {
            listOfNotNull(
                drivingSkill, vehicleCondition, punctuality, friendliness, routeKnowledge,
                politeness, cleanliness, communication, respectfulness, onTime
            ).isNotEmpty()
        }
        if (hasAnyCategoryRating) completed++

        return completed.toFloat() / total
    }
}