package com.rj.islamove.ui.screens.driver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.DriverEarnings
import com.rj.islamove.data.models.EarningsTransaction
import com.rj.islamove.data.models.EarningsStatus
import com.rj.islamove.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val matchingRepository: com.rj.islamove.data.repository.DriverMatchingRepository,
    private val userRepository: com.rj.islamove.data.repository.UserRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<EarningsUiState>(EarningsUiState.Loading)
    val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val TAG = "EarningsViewModel"
    }
    
    fun loadDriverEarnings(driverId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading driver earnings for: $driverId")
                _uiState.value = EarningsUiState.Loading

                // Load persistent earnings from User document in Firebase
                userRepository.getUserByUid(driverId).fold(
                    onSuccess = { user ->
                        val driverData = user.driverData
                        if (driverData != null) {
                            Log.d(TAG, "Loaded persistent earnings: ₱${driverData.totalEarnings}, trips: ${driverData.totalTrips}")

                            val earnings = DriverEarnings(
                                driverId = driverId,
                                totalEarnings = driverData.totalEarnings,
                                tripsCompleted = driverData.totalTrips,
                                pendingPayouts = 0.0
                            )

                            // For now, create empty transactions list since we're not tracking individual transactions yet
                            // In the future, this could be enhanced to load actual transaction history
                            val transactions = emptyList<EarningsTransaction>()

                            _uiState.value = EarningsUiState.Success(
                                earnings = earnings,
                                transactions = transactions
                            )
                        } else {
                            Log.w(TAG, "User has no driver data")
                            _uiState.value = EarningsUiState.Error("Driver data not found")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error loading user data", e)
                        _uiState.value = EarningsUiState.Error("Failed to load earnings data: ${e.message}")
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading driver earnings", e)
                _uiState.value = EarningsUiState.Error("Failed to load earnings data")
            }
        }
    }
    
    private fun calculateEarningsFromRides(driverId: String, completedRides: List<com.rj.islamove.data.repository.DriverRequest>): DriverEarnings {
        val now = System.currentTimeMillis()
        val todayStart = getTodayStartTime()
        val thisWeekStart = getThisWeekStartTime()
        val thisMonthStart = getThisMonthStartTime()
        
        // Filter rides by time periods
        val todayRides = completedRides.filter { it.secondChanceExpirationTime >= todayStart }
        val weekRides = completedRides.filter { it.secondChanceExpirationTime >= thisWeekStart }
        val monthRides = completedRides.filter { it.secondChanceExpirationTime >= thisMonthStart }
        
        // Calculate earnings
        val todayEarnings = todayRides.sumOf { it.fareEstimate.totalEstimate }
        val weekEarnings = weekRides.sumOf { it.fareEstimate.totalEstimate }
        val monthEarnings = monthRides.sumOf { it.fareEstimate.totalEstimate }
        val totalEarnings = completedRides.sumOf { it.fareEstimate.totalEstimate }
        
        return DriverEarnings(
            driverId = driverId,
            totalEarnings = totalEarnings,
            tripsCompleted = completedRides.size,
            pendingPayouts = 0.0 // Removed as requested
        )
    }
    
    private fun createTransactionsFromRides(completedRides: List<com.rj.islamove.data.repository.DriverRequest>): List<EarningsTransaction> {
        return completedRides.map { ride ->
            val commission = ride.fareEstimate.totalEstimate * 0.15 // 15% platform commission
            val netEarnings = ride.fareEstimate.totalEstimate - commission
            
            EarningsTransaction(
                id = ride.requestId,
                driverId = ride.driverId,
                bookingId = ride.bookingId,
                amount = ride.fareEstimate.totalEstimate,
                commission = commission,
                netEarnings = netEarnings,
                date = ride.secondChanceExpirationTime,
                status = EarningsStatus.PROCESSED
            )
        }.sortedByDescending { it.date }
    }
    
    private fun getTodayStartTime(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getThisWeekStartTime(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getThisMonthStartTime(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    fun refreshEarnings(driverId: String) {
        loadDriverEarnings(driverId)
    }
}

sealed class EarningsUiState {
    object Loading : EarningsUiState()
    data class Error(val message: String) : EarningsUiState()
    data class Success(
        val earnings: DriverEarnings,
        val transactions: List<EarningsTransaction>
    ) : EarningsUiState()
}