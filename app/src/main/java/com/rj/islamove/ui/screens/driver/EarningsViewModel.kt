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
}

sealed class EarningsUiState {
    object Loading : EarningsUiState()
    data class Error(val message: String) : EarningsUiState()
    data class Success(
        val earnings: DriverEarnings,
        val transactions: List<EarningsTransaction>
    ) : EarningsUiState()
}