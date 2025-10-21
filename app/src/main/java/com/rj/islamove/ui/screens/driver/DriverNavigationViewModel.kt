package com.rj.islamove.ui.screens.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.utils.Point
import com.rj.islamove.data.models.*
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.MapboxRepository
import com.rj.islamove.data.services.DriverLocationService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class DriverNavigationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentBooking: Booking? = null,
    val currentUserLocation: Point? = null,
    val currentRoute: RouteInfo? = null,
    val etaMinutes: Int = 0,
    val distanceMeters: Double = 0.0,
    val currentInstruction: String = "",
    val nextInstruction: String = "",
    val isNavigating: Boolean = false,
    val shouldShowArrivedButton: Boolean = false,
    val shouldShowStartTripButton: Boolean = false,
    val shouldShowCompleteTripButton: Boolean = false,
    val showRatingScreen: Boolean = false,
    val shouldNavigateToRating: Boolean = false
)

@HiltViewModel
class DriverNavigationViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val mapboxRepository: MapboxRepository,
    private val driverLocationService: DriverLocationService,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverNavigationUiState())
    val uiState: StateFlow<DriverNavigationUiState> = _uiState.asStateFlow()

    // Route manager to handle intelligent route updates
    private val routeManager = com.rj.islamove.utils.OptimizedRouteManager(
        mapboxRepository = mapboxRepository,
        coroutineScope = viewModelScope
    )
    
    fun initialize(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Load booking details
            loadBooking(bookingId)
            
            // Start location tracking
            driverLocationService.startLocationTracking()
            
            // Try to get current location immediately with timeout
            try {
                val currentLocation = withTimeoutOrNull(5000) { // 5 second timeout
                    driverLocationService.getCurrentLocationFlow().firstOrNull()
                }
                currentLocation?.let { location ->
                    _uiState.value = _uiState.value.copy(
                        currentUserLocation = location,
                        isLoading = false
                    )
                    
                    // Calculate initial route with this location
                    _uiState.value.currentBooking?.let { booking ->
                        calculateRoute(booking)
                    }
                    
                    android.util.Log.d("DriverNavVM", "Got immediate location: ${location.latitude()}, ${location.longitude()}")
                } ?: run {
                    android.util.Log.w("DriverNavVM", "Could not get location within 5 seconds")
                }
            } catch (e: Exception) {
                android.util.Log.w("DriverNavVM", "Failed to get immediate location: ${e.message}")
            }
        }
        
        // Collect ongoing location updates in separate coroutine (non-blocking)
        viewModelScope.launch {
            driverLocationService.getCurrentLocationFlow().collect { location ->
                _uiState.value = _uiState.value.copy(
                    currentUserLocation = location
                )

                // Update location in route manager for deviation detection
                // It will automatically recalculate ONLY if driver deviates significantly
                routeManager.updateDriverLocation(location)

                android.util.Log.d("DriverNavVM", "📍 Location updated: ${location.latitude()}, ${location.longitude()}")
            }
        }
    }
    
    private suspend fun loadBooking(bookingId: String) {
        // Start observing real-time booking changes instead of just loading once
        viewModelScope.launch {
            bookingRepository.observeBooking(bookingId).collect { result ->
                result.onSuccess { booking ->
                    if (booking != null) {
                        val previousBooking = _uiState.value.currentBooking
                        val statusChanged = previousBooking?.status != booking.status
                        
                        _uiState.value = _uiState.value.copy(
                            currentBooking = booking,
                            isLoading = false
                        )
                        
                        // Recalculate route when booking status changes
                        if (statusChanged || previousBooking == null) {
                            calculateRoute(booking)
                        }
                    }
                }.onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to observe booking: ${exception.message}"
                    )
                }
            }
        }
    }
    
    private suspend fun calculateRoute(booking: Booking) {
        val currentLocation = _uiState.value.currentUserLocation
        android.util.Log.d("DriverNavVM", "calculateRoute called with location: $currentLocation")

        if (currentLocation == null) {
            android.util.Log.w("DriverNavVM", "Cannot calculate route - no current location available")
            return
        }

        // Determine destination based on booking status
        val destination = when (booking.status) {
            BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING -> {
                android.util.Log.d("DriverNavVM", "Calculating route to pickup: ${booking.pickupLocation.address}")
                booking.pickupLocation
            }
            BookingStatus.DRIVER_ARRIVED -> {
                // At pickup, waiting for passenger
                android.util.Log.d("DriverNavVM", "Driver arrived - not calculating route")
                // Stop route following when arrived
                routeManager.stopRouteFollowing()
                return
            }
            BookingStatus.IN_PROGRESS -> {
                android.util.Log.d("DriverNavVM", "Calculating route to destination: ${booking.destination.address}")
                booking.destination
            }
            else -> {
                android.util.Log.w("DriverNavVM", "Cannot calculate route for booking status: ${booking.status}")
                return
            }
        }

        // Create BookingLocation from current location
        val driverLocation = BookingLocation(
            address = "Current Location",
            coordinates = com.google.firebase.firestore.GeoPoint(
                currentLocation.latitude(),
                currentLocation.longitude()
            )
        )

        // Use OptimizedRouteManager to calculate route ONCE
        // It will only recalculate if driver deviates significantly (100m+)
        android.util.Log.d("DriverNavVM", "🗺️ Using OptimizedRouteManager for intelligent routing")

        routeManager.calculateRouteOnce(driverLocation, destination) { route ->
            if (route != null) {
                android.util.Log.d("DriverNavVM", "ACTIVE RIDE: Route calculation successful: distance=${route.totalDistance}m, duration=${route.estimatedDuration}min, Route ID: ${route.routeId}")
                if (route.routeId.startsWith("simple_direct")) {
                    android.util.Log.w("DriverNavVM", "⚠️ WARNING: DriverNavigation got simple direct route instead of real roads for active ride!")
                } else {
                    android.util.Log.i("DriverNavVM", "✅ SUCCESS: DriverNavigation using real Mapbox Directions API")
                }

                val isToPickup = booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.DRIVER_ARRIVING
                val instruction = if (isToPickup) "Navigate to pickup location" else "Navigate to destination"

                _uiState.value = _uiState.value.copy(
                    currentRoute = route,
                    etaMinutes = route.estimatedDuration,
                    distanceMeters = route.totalDistance,
                    currentInstruction = instruction,
                    isNavigating = true,
                    shouldShowArrivedButton = isToPickup && route.totalDistance < 100, // Within 100m
                    shouldShowStartTripButton = booking.status == BookingStatus.DRIVER_ARRIVED,
                    shouldShowCompleteTripButton = booking.status == BookingStatus.IN_PROGRESS && route.totalDistance < 500 // Within 500m
                )

                android.util.Log.d("DriverNavVM", "UI state updated with route: ETA=${route.estimatedDuration}min, Distance=${String.format("%.0f", route.totalDistance)}m")

                // Start monitoring for route deviations
                routeManager.startRouteFollowing(currentLocation) { updatedRoute ->
                    // This callback is called ONLY when driver deviates significantly
                    updatedRoute?.let { newRoute ->
                        android.util.Log.i("DriverNavVM", "🔄 Route recalculated due to deviation: ${newRoute.totalDistance}m")
                        _uiState.value = _uiState.value.copy(
                            currentRoute = newRoute,
                            etaMinutes = newRoute.estimatedDuration,
                            distanceMeters = newRoute.totalDistance
                        )
                    }
                }
            } else {
                android.util.Log.e("DriverNavVM", "Route calculation failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to calculate route"
                )
            }
        }
    }
    
    /**
     * Driver arrived at pickup location
     */
    fun arrivedAtPickup() {
        viewModelScope.launch {
            val currentBooking = _uiState.value.currentBooking ?: return@launch
            
            // Enhanced debugging for booking validation
            android.util.Log.d("DriverNavigation", "Current booking object: id='${currentBooking.id}', status=${currentBooking.status}")
            android.util.Log.d("DriverNavigation", "Booking ID length: ${currentBooking.id.length}")
            android.util.Log.d("DriverNavigation", "Booking ID characters: ${currentBooking.id.toCharArray().contentToString()}")
            
            // Validate booking ID
            if (currentBooking.id.isBlank()) {
                android.util.Log.e("DriverNavigation", "Cannot mark arrival - booking ID is blank")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Booking ID is empty. Please try accepting a new ride."
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            android.util.Log.d("DriverNavigation", "Marking arrival for booking: ${currentBooking.id}")
            
            bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.DRIVER_ARRIVED)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        currentBooking = currentBooking.copy(status = BookingStatus.DRIVER_ARRIVED),
                        isLoading = false,
                        currentInstruction = "Waiting for passenger",
                        isNavigating = false,
                        shouldShowArrivedButton = false,
                        shouldShowStartTripButton = true
                    )
                    android.util.Log.i("DriverNavigation", "Driver marked as arrived - passenger will be notified")
                }
                .onFailure { exception ->
                    android.util.Log.e("DriverNavigation", "Failed to mark arrival for booking ${currentBooking.id}", exception)
                    
                    val errorMessage = when {
                        exception.message?.contains("invalid document reference") == true -> 
                            "Failed to mark arrival: Invalid booking reference. Please restart the app."
                        exception.message?.contains("even number of segments") == true -> 
                            "Failed to mark arrival: Document path error. Please contact support."
                        else -> 
                            "Failed to mark arrival: ${exception.message ?: "Unknown error"}"
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMessage
                    )
                }
        }
    }
    
    /**
     * Start trip after passenger gets in
     */
    fun startTrip() {
        viewModelScope.launch {
            val currentBooking = _uiState.value.currentBooking ?: return@launch
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.IN_PROGRESS)
                .onSuccess {
                    val updatedBooking = currentBooking.copy(status = BookingStatus.IN_PROGRESS)
                    _uiState.value = _uiState.value.copy(
                        currentBooking = updatedBooking,
                        isLoading = false,
                        shouldShowStartTripButton = false,
                        currentInstruction = "Starting trip to destination"
                    )
                    // Recalculate route to destination and start navigation
                    calculateRoute(updatedBooking)
                    android.util.Log.i("DriverNavigation", "Trip started - navigating to destination")
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to start trip: ${exception.message}"
                    )
                }
        }
    }
    
    /**
     * Complete the trip
     */
    fun completeTrip() {
        viewModelScope.launch {
            val currentBooking = _uiState.value.currentBooking ?: return@launch
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.COMPLETED)
                .onSuccess {
                    // Re-fetch the complete booking data to ensure we have all fields including passengerId
                    bookingRepository.getBooking(currentBooking.id)
                        .onSuccess { updatedBooking ->
                            _uiState.value = _uiState.value.copy(
                                currentBooking = updatedBooking,
                                isLoading = false,
                                currentInstruction = "Trip completed!",
                                isNavigating = false,
                                shouldShowCompleteTripButton = false,
                                showRatingScreen = true,
                                shouldNavigateToRating = true
                            )
                            android.util.Log.i("DriverNavigation", "Trip completed successfully - booking refreshed, passengerId: ${updatedBooking.passengerId}")
                        }
                        .onFailure { exception ->
                            android.util.Log.e("DriverNavigation", "Failed to refresh booking after completion", exception)
                            // Fallback to old behavior but with warning
                            _uiState.value = _uiState.value.copy(
                                currentBooking = currentBooking.copy(status = BookingStatus.COMPLETED),
                                isLoading = false,
                                currentInstruction = "Trip completed!",
                                isNavigating = false,
                                shouldShowCompleteTripButton = false,
                                shouldNavigateToRating = false  // Don't navigate if data is incomplete
                            )
                        }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to complete trip: ${exception.message}"
                    )
                }
        }
    }
    
    /**
     * Cancel the current trip
     */
    fun cancelTrip() {
        viewModelScope.launch {
            val currentBooking = _uiState.value.currentBooking ?: return@launch
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.CANCELLED)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        currentBooking = currentBooking.copy(status = BookingStatus.CANCELLED),
                        isLoading = false,
                        currentRoute = null, // Clear route to exit navigation mode
                        isNavigating = false, // Stop navigation mode
                        etaMinutes = 0, // Reset ETA
                        distanceMeters = 0.0, // Reset distance
                        currentInstruction = "", // Clear instructions
                        nextInstruction = "", // Clear next instruction
                        shouldShowArrivedButton = false, // Hide arrived button
                        shouldShowStartTripButton = false, // Hide start trip button
                        shouldShowCompleteTripButton = false // Hide complete trip button
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to cancel trip: ${exception.message}"
                    )
                }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Reset rating navigation trigger
     */
    fun resetRatingTrigger() {
        _uiState.value = _uiState.value.copy(
            showRatingScreen = false,
            shouldNavigateToRating = false
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        // Stop location tracking when ViewModel is destroyed
        driverLocationService.stopLocationTracking()
        // Stop route following and clear route
        routeManager.clearRoute()
    }
}