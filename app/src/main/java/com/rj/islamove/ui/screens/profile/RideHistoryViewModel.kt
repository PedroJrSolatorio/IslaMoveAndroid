package com.rj.islamove.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rj.islamove.data.models.Booking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RideHistoryUiState())
    val uiState: StateFlow<RideHistoryUiState> = _uiState.asStateFlow()
    
    private var lastDocument: DocumentSnapshot? = null
    private val pageSize = 20
    
    companion object {
        private const val TAG = "RideHistoryViewModel"
        private const val BOOKINGS_COLLECTION = "bookings"
    }
    
    fun loadRideHistory(userId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading ride history for user: $userId")
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                    return@launch
                }
                
                // Query for user's completed bookings (only show finished rides)
                // Use enum names for proper Firestore query
                val completedStatuses = listOf(
                    com.rj.islamove.data.models.BookingStatus.COMPLETED.name,
                    com.rj.islamove.data.models.BookingStatus.CANCELLED.name,
                    com.rj.islamove.data.models.BookingStatus.EXPIRED.name
                )

                Log.d(TAG, "Querying for bookings with statuses: $completedStatuses")

                val query = firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("passengerId", userId)
                    .whereIn("status", completedStatuses)
                    .orderBy("requestTime", Query.Direction.DESCENDING)
                    .limit(pageSize.toLong())
                
                val querySnapshot = query.get().await()
                
                val rides = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        val booking = doc.toObject(Booking::class.java)?.copy(id = doc.id)
                        Log.d(TAG, "Parsed booking: ${booking?.id}, status: ${booking?.status}")
                        booking
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse booking: ${doc.id}", e)
                        null
                    }
                }
                
                // Set the last document for pagination
                lastDocument = querySnapshot.documents.lastOrNull()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    rides = rides,
                    hasMore = querySnapshot.documents.size == pageSize,
                    error = null
                )
                
                Log.d(TAG, "Loaded ${rides.size} rides for user $userId")

                if (rides.isEmpty()) {
                    Log.w(TAG, "No completed rides found. This could mean:")
                    Log.w(TAG, "1. User has no completed rides yet")
                    Log.w(TAG, "2. Firestore query index is not created")
                    Log.w(TAG, "3. Booking status values don't match query")
                    Log.w(TAG, "4. PassengerId field doesn't match user ID")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ride history", e)

                // If the query failed (possibly due to missing index), try a simpler query
                if (e.message?.contains("index") == true) {
                    Log.d(TAG, "Composite index not available, falling back to simple query")
                    trySimpleQuery(userId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load ride history: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun loadMoreHistory() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || lastDocument == null) {
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading more ride history")
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
                
                val currentUser = auth.currentUser ?: return@launch
                
                // Query for next page (only show finished rides)
                val completedStatuses = listOf(
                    com.rj.islamove.data.models.BookingStatus.COMPLETED.name,
                    com.rj.islamove.data.models.BookingStatus.CANCELLED.name,
                    com.rj.islamove.data.models.BookingStatus.EXPIRED.name
                )

                val query = firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("passengerId", currentUser.uid)
                    .whereIn("status", completedStatuses)
                    .orderBy("requestTime", Query.Direction.DESCENDING)
                    .startAfter(lastDocument!!)
                    .limit(pageSize.toLong())
                
                val querySnapshot = query.get().await()
                
                val newRides = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Booking::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse booking: ${doc.id}", e)
                        null
                    }
                }
                
                // Update last document for next pagination
                lastDocument = querySnapshot.documents.lastOrNull()
                
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    rides = currentState.rides + newRides,
                    hasMore = querySnapshot.documents.size == pageSize
                )
                
                Log.d(TAG, "Loaded ${newRides.size} more rides")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more ride history", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Failed to load more rides: ${e.message}"
                )
            }
        }
    }
    
    fun refreshHistory(userId: String) {
        lastDocument = null
        loadRideHistory(userId)
    }
    
    private suspend fun trySimpleQuery(userId: String) {
        try {
            Log.d(TAG, "Trying simple query without status filter")

            // Simple query - filter in memory
            val query = firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo("passengerId", userId)
                .orderBy("requestTime", Query.Direction.DESCENDING)
                .limit((pageSize * 2).toLong()) // Get more to account for filtering

            val querySnapshot = query.get().await()

            val allRides = querySnapshot.documents.mapNotNull { doc ->
                try {
                    val booking = doc.toObject(Booking::class.java)?.copy(id = doc.id)
                    Log.d(TAG, "Simple query - booking: ${booking?.id}, status: ${booking?.status}")
                    booking
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse booking: ${doc.id}", e)
                    null
                }
            }

            // Filter completed rides in memory
            val completedStatuses = listOf(
                com.rj.islamove.data.models.BookingStatus.COMPLETED.name,
                com.rj.islamove.data.models.BookingStatus.CANCELLED.name,
                com.rj.islamove.data.models.BookingStatus.EXPIRED.name
            )

            val completedRides = allRides.filter { booking ->
                booking.status.name in completedStatuses
            }.take(pageSize)

            Log.d(TAG, "Found ${completedRides.size} completed rides out of ${allRides.size} total rides")

            lastDocument = if (completedRides.isNotEmpty()) {
                // Find the original document for the last completed ride
                querySnapshot.documents.find { doc ->
                    doc.id == completedRides.last().id
                }
            } else null

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                rides = completedRides,
                hasMore = completedRides.size == pageSize,
                error = null
            )

            Log.d(TAG, "Simple query loaded ${completedRides.size} completed rides")

        } catch (e: Exception) {
            Log.e(TAG, "Simple query also failed", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Failed to load ride history: ${e.message}"
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class RideHistoryUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val rides: List<Booking> = emptyList(),
    val hasMore: Boolean = true,
    val error: String? = null
)