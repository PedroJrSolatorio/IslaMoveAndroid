package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.DriverRepository
import com.rj.islamove.data.repository.DriverLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import javax.inject.Inject

data class LiveMonitoringUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLiveMonitoring: Boolean = false,
    val lastUpdated: Long? = null,

    // Live data
    val activeRides: List<LiveRideInfo> = emptyList(),
    val onlineDrivers: List<User> = emptyList(),
    val onlineDriverLocations: List<DriverLocation> = emptyList(),

    // System health
    val systemHealth: SystemHealth = SystemHealth.GOOD,
    val totalUsers: Int = 0,
    val systemUptime: Long = 0L
)

@HiltViewModel
class LiveMonitoringViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val driverRepository: DriverRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveMonitoringUiState())
    val uiState: StateFlow<LiveMonitoringUiState> = _uiState.asStateFlow()

    private var monitoringJob: Job? = null

    fun startLiveMonitoring() {
        if (monitoringJob?.isActive == true) {
            Log.d("LiveMonitoringVM", "Live monitoring already active")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isLiveMonitoring = true,
            errorMessage = null
        )

        monitoringJob = viewModelScope.launch {
            try {
                // Launch each monitoring function in its own coroutine so they run concurrently
                launch { monitorActiveRides() }
                launch { monitorOnlineDrivers() }
                launch { monitorDriverLocations() }


                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastUpdated = System.currentTimeMillis()
                )

                Log.d("LiveMonitoringVM", "Started live monitoring with real-time flows")
            } catch (e: Exception) {
                Log.e("LiveMonitoringVM", "Error starting live monitoring", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLiveMonitoring = false,
                    errorMessage = "Failed to start monitoring: ${e.message}"
                )
            }
        }
    }

    fun stopLiveMonitoring() {
        monitoringJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLiveMonitoring = false
        )
        Log.d("LiveMonitoringVM", "Stopped live monitoring")
    }

    private suspend fun monitorActiveRides() {
        bookingRepository.getActiveBookings().collect { result ->
            result.onSuccess { bookings ->
                Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Received ${bookings.size} bookings from Firebase")

                // Process user data asynchronously to avoid blocking the Flow
                viewModelScope.launch {
                    val activeRides = mutableListOf<LiveRideInfo>()

                    // Use coroutines to fetch user data concurrently
                    for (booking in bookings) {
                        // Get passenger name asynchronously
                        val passengerName = async {
                            userRepository.getUserByUid(booking.passengerId)
                                .getOrNull()?.displayName ?: "Unknown Passenger"
                        }

                        // Get driver name asynchronously if assigned
                        val driverName = async {
                            if (booking.driverId != null) {
                                userRepository.getUserByUid(booking.driverId!!)
                                    .getOrNull()?.displayName ?: "Unknown Driver"
                            } else {
                                "No driver assigned"
                            }
                        }

                        activeRides.add(
                            LiveRideInfo(
                                booking = booking,
                                passengerName = passengerName.await(),
                                driverName = driverName.await()
                            )
                        )
                    }

                    // Update state with new data and force UI refresh
                    val currentTime = System.currentTimeMillis()
                    val newState = _uiState.value.copy(
                        activeRides = activeRides,
                        lastUpdated = currentTime,
                        systemHealth = calculateSystemHealth(activeRides.size, _uiState.value.onlineDriverLocations.size)
                    )
                    _uiState.value = newState

                    Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Updated active rides: ${activeRides.size} at $currentTime")
                }
            }.onFailure { exception ->
                Log.e("LiveMonitoringVM", "Error monitoring active rides", exception)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error loading active rides: ${exception.message}",
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun monitorOnlineDrivers() {
        userRepository.getUsersByType(UserType.DRIVER).collect { result ->
            result.onSuccess { drivers ->
                Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Received ${drivers.size} drivers from Firebase")

                val onlineDrivers = drivers.filter { driver ->
                    driver.driverData?.online == true &&
                    driver.driverData?.verificationStatus == com.rj.islamove.data.models.VerificationStatus.APPROVED
                }

                val currentTime = System.currentTimeMillis()
                val newState = _uiState.value.copy(
                    onlineDrivers = onlineDrivers,
                    lastUpdated = currentTime,
                    systemHealth = calculateSystemHealth(_uiState.value.activeRides.size, onlineDrivers.size)
                )
                _uiState.value = newState

                Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Updated online drivers: ${onlineDrivers.size} at $currentTime")
            }.onFailure { exception ->
                Log.e("LiveMonitoringVM", "Error monitoring drivers", exception)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error loading drivers: ${exception.message}",
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun monitorDriverLocations() {
        driverRepository.observeOnlineDrivers().collect { driverLocations ->
            Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Received ${driverLocations.size} driver locations from Firebase")

            val currentTime = System.currentTimeMillis()
            val newState = _uiState.value.copy(
                onlineDriverLocations = driverLocations,
                lastUpdated = currentTime,
                systemHealth = calculateSystemHealth(_uiState.value.activeRides.size, driverLocations.size)
            )
            _uiState.value = newState

            Log.d("LiveMonitoringVM", "🔥 REAL-TIME: Updated driver locations: ${driverLocations.size} at $currentTime")
        }
    }


    private fun calculateSystemHealth(activeRides: Int, onlineDrivers: Int): SystemHealth {
        return when {
            onlineDrivers == 0 && activeRides > 0 -> SystemHealth.ERROR
            onlineDrivers > 0 && activeRides.toFloat() / onlineDrivers > 0.9f -> SystemHealth.WARNING
            else -> SystemHealth.GOOD
        }
    }



    fun emergencyStopRide(bookingId: String) {
        viewModelScope.launch {
            try {
                // This would typically:
                // 1. Update booking status to CANCELLED
                // 2. Send emergency notifications to both passenger and driver
                // 3. Alert local authorities if needed
                // 4. Log the incident
                
                bookingRepository.updateBookingStatus(bookingId, BookingStatus.CANCELLED)
                    .onSuccess {
                        Log.d("LiveMonitoringVM", "Emergency stop executed for booking $bookingId")
                    }
                    .onFailure { exception ->
                        Log.e("LiveMonitoringVM", "Failed to emergency stop ride", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to stop ride: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("LiveMonitoringVM", "Error in emergency stop", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Emergency stop failed: ${e.message}"
                )
            }
        }
    }


    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }


    override fun onCleared() {
        super.onCleared()
        stopLiveMonitoring()
    }
}