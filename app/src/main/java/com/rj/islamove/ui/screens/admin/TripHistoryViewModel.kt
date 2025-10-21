package com.rj.islamove.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.Booking
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.data.models.Ride
import com.rj.islamove.data.models.RideStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class TripHistoryUiState(
    val rides: List<Ride> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripHistoryUiState())
    val uiState: StateFlow<TripHistoryUiState> = _uiState.asStateFlow()

    fun loadTripHistory(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Load completed bookings for this user (both as passenger and driver)
                val completedBookings = getCompletedBookings(userId)

                // Convert bookings to rides for display consistency
                val rides = completedBookings.map { booking -> convertBookingToRide(booking) }

                _uiState.value = _uiState.value.copy(
                    rides = rides,
                    bookings = completedBookings,
                    isLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load trip history: ${e.message}"
                )
            }
        }
    }

    private suspend fun getCompletedBookings(userId: String): List<Booking> {
        val allBookings = mutableListOf<Booking>()

        try {
            // Get completed bookings where user is passenger
            val passengerCompletedBookings = firestore.collection("bookings")
                .whereEqualTo("passengerId", userId)
                .whereEqualTo("status", BookingStatus.COMPLETED)
                .limit(25)
                .get()
                .await()

            passengerCompletedBookings.documents.forEach { doc ->
                doc.toObject(Booking::class.java)?.let { booking ->
                    allBookings.add(booking.copy(id = doc.id))
                }
            }

            // Get cancelled bookings where user is passenger
            val passengerCancelledBookings = firestore.collection("bookings")
                .whereEqualTo("passengerId", userId)
                .whereEqualTo("status", BookingStatus.CANCELLED)
                .limit(25)
                .get()
                .await()

            passengerCancelledBookings.documents.forEach { doc ->
                doc.toObject(Booking::class.java)?.let { booking ->
                    allBookings.add(booking.copy(id = doc.id))
                }
            }

            // Get completed bookings where user is driver
            val driverCompletedBookings = firestore.collection("bookings")
                .whereEqualTo("driverId", userId)
                .whereEqualTo("status", BookingStatus.COMPLETED)
                .limit(25)
                .get()
                .await()

            driverCompletedBookings.documents.forEach { doc ->
                doc.toObject(Booking::class.java)?.let { booking ->
                    allBookings.add(booking.copy(id = doc.id))
                }
            }

            // Get cancelled bookings where user is driver
            val driverCancelledBookings = firestore.collection("bookings")
                .whereEqualTo("driverId", userId)
                .whereEqualTo("status", BookingStatus.CANCELLED)
                .limit(25)
                .get()
                .await()

            driverCancelledBookings.documents.forEach { doc ->
                doc.toObject(Booking::class.java)?.let { booking ->
                    allBookings.add(booking.copy(id = doc.id))
                }
            }
        } catch (e: Exception) {
            // If specific queries fail, try a simpler approach
            val passengerBookings = firestore.collection("bookings")
                .whereEqualTo("passengerId", userId)
                .limit(50)
                .get()
                .await()

            val driverBookings = firestore.collection("bookings")
                .whereEqualTo("driverId", userId)
                .limit(50)
                .get()
                .await()

            // Filter completed/cancelled bookings in code
            (passengerBookings.documents + driverBookings.documents).forEach { doc ->
                doc.toObject(Booking::class.java)?.let { booking ->
                    if (booking.status == BookingStatus.COMPLETED || booking.status == BookingStatus.CANCELLED) {
                        allBookings.add(booking.copy(id = doc.id))
                    }
                }
            }
        }

        // Sort by completion time descending and remove duplicates
        return allBookings
            .distinctBy { it.id }
            .sortedByDescending { it.completionTime ?: it.requestTime }
            .take(50) // Limit to 50 results
    }

    private fun convertBookingToRide(booking: Booking): Ride {
        return Ride(
            id = booking.id,
            passengerId = booking.passengerId,
            driverId = booking.driverId,
            pickupLocation = com.rj.islamove.data.models.Location(
                address = booking.pickupLocation.address,
                latitude = booking.pickupLocation.coordinates.latitude,
                longitude = booking.pickupLocation.coordinates.longitude
            ),
            destination = com.rj.islamove.data.models.Location(
                address = booking.destination.address,
                latitude = booking.destination.coordinates.latitude,
                longitude = booking.destination.coordinates.longitude
            ),
            status = when (booking.status) {
                BookingStatus.COMPLETED -> RideStatus.COMPLETED
                BookingStatus.CANCELLED -> RideStatus.CANCELLED_BY_PASSENGER
                else -> RideStatus.REQUESTED
            },
            fare = com.rj.islamove.data.models.Fare(
                totalFare = booking.actualFare ?: booking.fareEstimate.totalEstimate,
                finalAmount = booking.actualFare ?: booking.fareEstimate.totalEstimate
            ),
            requestTime = booking.requestTime,
            startTime = booking.pickupTime,
            endTime = booking.completionTime
        )
    }
}