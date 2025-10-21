package com.rj.islamove.ui.screens.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.Rating
import com.rj.islamove.data.models.RatingFilter
import com.rj.islamove.data.models.UserRatingStats
import com.rj.islamove.data.repository.RatingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val ratingStats: UserRatingStats? = null,
    val reviews: List<Rating> = emptyList(),
    val allReviews: List<Rating> = emptyList(), // Keep original list for filtering
    val currentFilter: RatingFilter = RatingFilter.ALL
)

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val ratingRepository: RatingRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewsUiState())
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    /**
     * Load user reviews and rating statistics
     */
    fun loadReviews() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "User not authenticated"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                // Load rating statistics
                val statsResult = ratingRepository.getUserRatingStats(currentUser.uid)
                val ratingStats = statsResult.getOrNull()

                // Load all reviews (no limit for full view)
                // Note: Fallback to simple query if composite indexes are still building
                val reviewsResult = try {
                    ratingRepository.getUserRatings(
                        userId = currentUser.uid,
                        filter = RatingFilter.ALL,
                        limit = 100 // Increased limit for complete view
                    )
                } catch (e: Exception) {
                    // If composite index is still building, show appropriate message
                    if (e.message?.contains("index") == true && e.message?.contains("building") == true) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Reviews are loading... Firestore indexes are still building. Please try again in a few minutes."
                        )
                        return@launch
                    } else {
                        Result.failure(e)
                    }
                }

                reviewsResult.fold(
                    onSuccess = { reviews ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            ratingStats = ratingStats,
                            reviews = reviews,
                            allReviews = reviews,
                            errorMessage = null
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Failed to load reviews"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Apply filter to reviews
     */
    fun setFilter(filter: RatingFilter) {
        val allReviews = _uiState.value.allReviews
        val filteredReviews = when (filter) {
            RatingFilter.ALL -> allReviews
            RatingFilter.FIVE_STARS -> allReviews.filter { it.stars == 5 }
            RatingFilter.FOUR_STARS -> allReviews.filter { it.stars == 4 }
            RatingFilter.THREE_STARS -> allReviews.filter { it.stars == 3 }
            RatingFilter.TWO_STARS -> allReviews.filter { it.stars == 2 }
            RatingFilter.ONE_STAR -> allReviews.filter { it.stars == 1 }
            RatingFilter.WITH_REVIEW -> allReviews.filter { it.review.isNotEmpty() }
            RatingFilter.WITHOUT_REVIEW -> allReviews.filter { it.review.isEmpty() }
            RatingFilter.RECENT -> allReviews.sortedByDescending { it.createdAt }
            RatingFilter.OLDEST -> allReviews.sortedBy { it.createdAt }
        }

        _uiState.value = _uiState.value.copy(
            reviews = filteredReviews,
            currentFilter = filter
        )
    }

    /**
     * Refresh reviews data
     */
    fun refreshReviews() {
        loadReviews()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}