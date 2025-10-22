package com.rj.islamove.ui.screens.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.utils.Point
import com.rj.islamove.data.models.*
import java.util.*
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.ServiceAreaManagementRepository
import com.rj.islamove.data.repository.DriverMatchingRepository
import com.rj.islamove.data.repository.DriverRepository
import com.rj.islamove.data.repository.DriverRequest
import com.rj.islamove.data.repository.MapboxRepository
import com.rj.islamove.data.repository.RatingRepository
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import com.rj.islamove.data.services.DriverLocationService
import com.rj.islamove.data.services.PassengerLocationService
import com.rj.islamove.utils.LocationUtils
import com.rj.islamove.utils.ProximityAlertUtils
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import android.util.Log
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import javax.inject.Inject
import com.rj.islamove.R
import com.rj.islamove.data.repository.DriverRequestStatus
import com.rj.islamove.data.repository.RequestPhase
import kotlinx.coroutines.flow.update

data class DateRange(
    val startDate: Long,
    val endDate: Long,
    val label: String
) {
    fun getFormattedDateRange(): String {
        val calendar = Calendar.getInstance()
        val format = java.text.SimpleDateFormat("MMM dd", Locale.getDefault())

        // For Today, show only one date
        if (label == "Today") {
            calendar.timeInMillis = startDate
            return format.format(calendar.time)
        }

        // For Week and Month, show range
        calendar.timeInMillis = startDate
        val startStr = format.format(calendar.time)

        calendar.timeInMillis = endDate
        val endStr = format.format(calendar.time)

        return "$startStr - $endStr"
    }
}

enum class DateRangeType {
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}

// Helper functions for date ranges
fun getCurrentWeekRange(): DateRange {
    val calendar = Calendar.getInstance()
    // Set to first day of week (Sunday in US, Monday in some regions)
    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startDate = calendar.timeInMillis

    // Set to last day of week
    calendar.add(Calendar.DAY_OF_WEEK, 6)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    val endDate = calendar.timeInMillis

    return DateRange(startDate, endDate, "This Week")
}

fun getTodayRange(): DateRange {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startDate = calendar.timeInMillis

    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    val endDate = calendar.timeInMillis

    return DateRange(startDate, endDate, "Today")
}

fun getCurrentMonthRange(): DateRange {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startDate = calendar.timeInMillis

    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    val endDate = calendar.timeInMillis

    return DateRange(startDate, endDate, "This Month")
}

data class DriverHomeUiState(
    val online: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUserLocation: Point? = null,
    val hasLocationPermissions: Boolean = false,
    val totalEarnings: Double = 0.0,
    val todayEarnings: Double = 0.0,
    val weeklyEarnings: Double = 0.0,
    val todayTrips: Int = 0,
    val selectedVehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
    val incomingRequests: List<DriverRequest> = emptyList(), // Initial 30-second phase
    val secondChanceRequests: List<DriverRequest> = emptyList(), // 3-minute second chance phase
    val recentDeclinedRequests: List<DriverRequest> = emptyList(), // Fully expired/declined
    val currentBooking: Booking? = null,
    val queuedBookings: List<Booking> = emptyList(), // Queue of accepted bookings (max 5)
    val currentActiveBookingId: String? = null, // Which booking from queue is currently being navigated to
    val driverProfile: com.rj.islamove.data.repository.DriverProfile? = null,
    val passengerLocation: Point? = null, // Location of accepted passenger
    val showNavigationToPassenger: Boolean = false,
    val currentUser: User? = null,
    // Navigation trigger - will be handled by separate navigation screen
      // Route information for displaying polylines and navigation
    val routeInfo: RouteInfo? = null,
    // Timer for request countdown
    val currentTimeMillis: Long = System.currentTimeMillis(),
    // Rating navigation state for DriverHomeScreen trip completion
    val shouldNavigateToRating: Boolean = false,
    val completedBookingId: String? = null,
    val completedPassengerId: String? = null,
    // Map tab navigation after accepting ride
    val shouldNavigateToMapTab: Boolean = false,
    // Service area destinations visible to driver
    val customLandmarks: List<CustomLandmark> = emptyList(),
    // Dialog notifications
    val showPassengerCancellationDialog: Boolean = false,
    val showServiceBoundaryDialog: Boolean = false,
    val serviceBoundaryMessage: String? = null,
    // Date range for rides tab
    val selectedDateRangeType: DateRangeType = DateRangeType.THIS_WEEK,
    val selectedDateRange: DateRange = getCurrentWeekRange(),
    // Passenger rating information
    val passengerRatingStats: UserRatingStats? = null,
    // Trip details functionality
    val showTripDetailsDialog: Boolean = false,
    val selectedTripForDetails: Booking? = null,
    val selectedTripPassenger: User? = null,
    // Route caching for queued passengers
    val cachedRoutes: Map<String, RouteInfo> = emptyMap(), // bookingId -> routeInfo
)

@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val matchingRepository: DriverMatchingRepository,
    private val bookingRepository: BookingRepository,
    private val mapboxRepository: MapboxRepository,
    private val driverLocationService: DriverLocationService,
    private val passengerLocationService: PassengerLocationService,
    private val locationUtils: LocationUtils,
    private val auth: FirebaseAuth,
    private val notificationService: com.rj.islamove.data.services.NotificationService,
    private val serviceAreaManagementRepository: ServiceAreaManagementRepository,
    private val userRepository: com.rj.islamove.data.repository.UserRepository,
    private val paymentRepository: com.rj.islamove.data.repository.PaymentRepository,
    private val ratingRepository: RatingRepository,
    private val zoneBoundaryRepository: ZoneBoundaryRepository,
    private val firestore: FirebaseFirestore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverHomeUiState())
    val uiState: StateFlow<DriverHomeUiState> = _uiState.asStateFlow()

    private var locationUpdatesStopCallback: (() -> Unit)? = null
    private var staleDriverMonitorJob: kotlinx.coroutines.Job? = null

    // Optimized location and route management
    private val optimizedLocationManager = com.rj.islamove.utils.OptimizedLocationUpdateManager(
        context = context,
        driverRepository = driverRepository,
        coroutineScope = viewModelScope
    )
    private val optimizedRouteManager = com.rj.islamove.utils.OptimizedRouteManager(
        mapboxRepository = mapboxRepository,
        coroutineScope = viewModelScope
    )
    private val enhancedDriverLocationService = com.rj.islamove.services.EnhancedDriverLocationService(
        context = context,
        coroutineScope = viewModelScope
    )

    // Route recalculation throttling (reduced frequency for API optimization)
    private var lastRouteRecalculationLocation: Point? = null
    private var lastRouteRecalculationTime: Long = 0
    private val ROUTE_RECALC_DISTANCE_THRESHOLD = 100.0 // increased to 100 meters
    private val ROUTE_RECALC_TIME_THRESHOLD = 10000L // reduced to 10 seconds

    // Track if route has been calculated (to avoid recalculating on every location update)
    private var isRouteCalculated = false
    private var lastCalculatedDestination: BookingLocation? = null

    // SharedPreferences for tracking rated bookings
    private val sharedPreferences = context.getSharedPreferences("submitted_ratings", android.content.Context.MODE_PRIVATE)

    // Track previous request count to detect new ride requests
    private var previousRequestCount: Int = 0

    /**
     * Vibrate device 5 times when there's a new active ride request
     */
    private fun vibrateForNewRideRequest() {
        try {
            val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
            if (vibrator?.hasVibrator() == true) {
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    // Create vibration pattern: 5 bursts with 200ms on, 200ms off
                    val timings = LongArray(10) // 5 on + 5 off = 10 elements
                    val amplitudes = IntArray(10)

                    for (i in timings.indices) {
                        timings[i] = 200L // 200ms for each segment
                        amplitudes[i] = if (i % 2 == 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 // On for even indices, off for odd
                    }

                    val vibrationEffect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    val pattern = longArrayOf(200, 200, 200, 200, 200, 200, 200, 200, 200, 200)
                    vibrator.vibrate(pattern, -1)
                }
                Log.d("DriverViewModel", "📳 Vibrating device for new ride request")
            }
        } catch (e: Exception) {
            Log.e("DriverViewModel", "❌ Failed to vibrate device", e)
        }
    }

    /**
     * Play notification sound 5 times when there's a new active ride request
     */
    private fun playRideRequestSound() {
        try {
            Log.d("DriverViewModel", "🔊 Playing ride request notification sound 5 times...")
            playRideRequestSoundRecursive(1, 5)
        } catch (e: Exception) {
            Log.e("DriverViewModel", "❌ Failed to play ride request sound", e)
        }
    }

    /**
     * Recursively play the ride request sound a specified number of times
     */
    private fun playRideRequestSoundRecursive(currentPlay: Int, totalPlays: Int) {
        if (currentPlay > totalPlays) {
            Log.d("DriverViewModel", "✅ Completed playing ride request sound $totalPlays times")
            return
        }

        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.ride_request_notification)
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener { mp ->
                    mp.release()
                    Log.d("DriverViewModel", "🔊 Ride request sound #$currentPlay completed and released")

                    // Play the next iteration after a short delay
                    if (currentPlay < totalPlays) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            playRideRequestSoundRecursive(currentPlay + 1, totalPlays)
                        }, 200) // 200ms delay between plays
                    }
                }
                mediaPlayer.start()
                Log.d("DriverViewModel", "🔊 Playing ride request sound #$currentPlay")
            } else {
                Log.e("DriverViewModel", "❌ MediaPlayer.create returned null for ride request sound #$currentPlay")
            }
        } catch (e: Exception) {
            Log.e("DriverViewModel", "❌ Failed to play ride request sound #$currentPlay", e)
        }
    }
    private val ratedBookings = mutableSetOf<String>()

    // Proximity alert utility
    private val proximityAlertUtils = ProximityAlertUtils(context)

    init {
        checkLocationPermissions()
        loadCurrentLocation()
        cleanupStaleDrivers() // Clean up any stale online drivers (3+ minute timeout)
        startStaleDriverMonitoring() // Start continuous monitoring for stale drivers
        restoreDriverOnlineStatus() // Restore driver online status from Firebase
        restoreActiveBooking() // Restore any active booking when app restarts
        observeIncomingRequests()
        // monitorBookingCancellations() // DISABLED: Duplicate observer - observeBookingStatus() already handles cancellations for accepted bookings
        loadDriverProfile()
        loadEarnings()
        loadCurrentUser()
        loadServiceAreaDestinations()
        startTimerUpdates()
        loadRatedBookings()
        observeAuthStateChanges()
    }

    private fun loadCurrentUser() {
        auth.currentUser?.let { firebaseUser ->
            // Use real-time flow to automatically sync with Firebase changes
            viewModelScope.launch {
                userRepository.getUserFlow(firebaseUser.uid).collect { user ->
                    if (user != null) {
                        _uiState.value = _uiState.value.copy(currentUser = user)
                    } else {
                        // Fallback to basic Firebase Auth data if Firestore fails
                        val basicUser = User(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            displayName = firebaseUser.displayName ?: "",
                            userType = UserType.DRIVER
                        )
                        _uiState.value = _uiState.value.copy(currentUser = basicUser)
                    }
                }
            }
        }
    }

    /**
     * Clean up stale online drivers when app starts
     * Sets drivers offline if they haven't been active for 3+ minutes
     */
    private fun cleanupStaleDrivers() {
        viewModelScope.launch {
            try {
                android.util.Log.d("DriverViewModel", "🧹 Starting cleanup of stale online drivers...")
                driverRepository.cleanupStaleOnlineDrivers().onSuccess {
                    android.util.Log.d("DriverViewModel", "✅ Stale driver cleanup completed")
                }.onFailure { exception ->
                    android.util.Log.w("DriverViewModel", "⚠️ Stale driver cleanup failed", exception)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverViewModel", "❌ Error during stale driver cleanup", e)
            }
        }
    }

    /**
     * Start continuous monitoring for stale drivers
     * Runs every 2 minutes to check for drivers that should be offline
     */
    private fun startStaleDriverMonitoring() {
        // Cancel any existing monitoring
        stopStaleDriverMonitoring()

        staleDriverMonitorJob = viewModelScope.launch {
            android.util.Log.d("DriverViewModel", "🔄 Starting continuous stale driver monitoring...")

            try {
                while (true) {
                    // Wait 2 minutes between checks
                    kotlinx.coroutines.delay(120000L) // 2 minutes

                    android.util.Log.d("DriverViewModel", "⏰ Running periodic stale driver cleanup...")
                    driverRepository.cleanupStaleOnlineDrivers().onSuccess {
                        android.util.Log.d("DriverViewModel", "✅ Periodic cleanup completed successfully")
                    }.onFailure { exception ->
                        android.util.Log.w("DriverViewModel", "⚠️ Periodic cleanup failed", exception)
                    }

                    // ENHANCEMENT: Also check for pending requests during monitoring
                    if (_uiState.value.online) {
                        auth.currentUser?.uid?.let { driverId ->
                            android.util.Log.d("DriverViewModel", "🔍 Periodic check for pending requests...")
                            matchingRepository.processQueuedBookingsForNewDriver(driverId).onSuccess { count ->
                                if (count > 0) {
                                    android.util.Log.i("DriverViewModel", "📬 Found and processed $count pending requests during monitoring")
                                }
                            }.onFailure { exception ->
                                android.util.Log.w("DriverViewModel", "⚠️ Failed periodic pending request check", exception)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("DriverViewModel", "🛑 Stale driver monitoring cancelled")
            } catch (e: Exception) {
                android.util.Log.e("DriverViewModel", "❌ Error in stale driver monitoring", e)
            }
        }
    }

    /**
     * Stop the continuous stale driver monitoring
     */
    private fun stopStaleDriverMonitoring() {
        staleDriverMonitorJob?.cancel()
        staleDriverMonitorJob = null
        android.util.Log.d("DriverViewModel", "🛑 Stopped stale driver monitoring")
    }

    /**
     * Restore driver online status from Firebase when app restarts
     * This synchronizes the UI toggle with the actual Firebase status
     */
    private fun restoreDriverOnlineStatus() {
        viewModelScope.launch {
            val driverId = auth.currentUser?.uid ?: return@launch

            android.util.Log.d("DriverViewModel", "🔄 Restoring driver online status for: $driverId")

            try {
                // Check the driver's online status in Firebase
                driverRepository.getDriverOnlineStatus(driverId).onSuccess { isOnline ->
                    android.util.Log.d("DriverViewModel", "✅ Restored online status: $isOnline")

                    _uiState.value = _uiState.value.copy(online = isOnline)

                    // If driver was online, restart location tracking AND process pending requests
                    if (isOnline) {
                        startLocationUpdates()

                        // CRITICAL FIX: Also process pending requests when restoring online status
                        launch {
                            android.util.Log.w("DriverViewModel", "🔄🔄🔄 RESTORE: Driver $driverId was online - processing pending requests...")
                            matchingRepository.processQueuedBookingsForNewDriver(driverId).onSuccess { count ->
                                android.util.Log.w("DriverViewModel", "✅✅✅ RESTORE: Successfully processed $count requests for restored online driver $driverId")
                            }.onFailure { exception ->
                                android.util.Log.e("DriverViewModel", "❌❌❌ RESTORE: Failed to process pending requests for driver $driverId", exception)
                            }
                        }
                    }
                }.onFailure { exception ->
                    android.util.Log.w("DriverViewModel", "⚠️ Failed to restore online status, defaulting to offline", exception)
                    _uiState.value = _uiState.value.copy(online = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverViewModel", "❌ Error restoring driver online status", e)
                _uiState.value = _uiState.value.copy(online = false)
            }
        }
    }

    /**
     * Restore any active bookings when the app restarts
     * This ensures both current booking and queued bookings persist when app is terminated/restarted
     */
    private fun restoreActiveBooking() {
        viewModelScope.launch {
            val driverId = auth.currentUser?.uid ?: run {
                android.util.Log.w("DriverViewModel", "⚠️ Cannot restore bookings - no authenticated user")
                return@launch
            }

            android.util.Log.d("DriverViewModel", "🔄 Checking for active bookings to restore for driver: $driverId")

            try {
                // Query for each status separately to avoid composite index requirement
                val acceptedBookings = firestore.collection("bookings")
                    .whereEqualTo("driverId", driverId)
                    .whereEqualTo("status", BookingStatus.ACCEPTED.name)
                    .get()
                    .await()

                val arrivingBookings = firestore.collection("bookings")
                    .whereEqualTo("driverId", driverId)
                    .whereEqualTo("status", BookingStatus.DRIVER_ARRIVING.name)
                    .get()
                    .await()

                val arrivedBookings = firestore.collection("bookings")
                    .whereEqualTo("driverId", driverId)
                    .whereEqualTo("status", BookingStatus.DRIVER_ARRIVED.name)
                    .get()
                    .await()

                val inProgressBookings = firestore.collection("bookings")
                    .whereEqualTo("driverId", driverId)
                    .whereEqualTo("status", BookingStatus.IN_PROGRESS.name)
                    .get()
                    .await()

                // Combine all results
                val allDocs = acceptedBookings.documents + arrivingBookings.documents + arrivedBookings.documents + inProgressBookings.documents

                if (allDocs.isNotEmpty()) {
                    val allBookings = allDocs.mapNotNull { doc ->
                        try {
                            doc.toObject(Booking::class.java)?.also {
                                android.util.Log.d("DriverViewModel", "📦 Found booking: ${it.id}, status: ${it.status}, passengerId: ${it.passengerId}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DriverViewModel", "❌ Failed to parse booking ${doc.id}", e)
                            null
                        }
                    }.sortedBy { it.requestTime } // Sort by request time

                    android.util.Log.i("DriverViewModel", "✅ Found ${allBookings.size} active bookings to restore")

                    // Determine which booking is the current one (priority: IN_PROGRESS > DRIVER_ARRIVED > DRIVER_ARRIVING > ACCEPTED)
                    val currentBooking = allBookings.find { it.status == BookingStatus.IN_PROGRESS }
                        ?: allBookings.find { it.status == BookingStatus.DRIVER_ARRIVED }
                        ?: allBookings.find { it.status == BookingStatus.DRIVER_ARRIVING }
                        ?: allBookings.firstOrNull { it.status == BookingStatus.ACCEPTED }

                    // Create queued bookings list (all other accepted bookings)
                    val queuedBookings = allBookings.filter { it != currentBooking }

                    android.util.Log.d("DriverViewModel", "📊 Current booking: ${currentBooking?.id}, Queued: ${queuedBookings.size}")

                    // Restore the booking state
                    _uiState.value = _uiState.value.copy(
                        currentBooking = currentBooking,
                        queuedBookings = queuedBookings,
                        showNavigationToPassenger = currentBooking != null,
                        currentActiveBookingId = currentBooking?.id
                    )

                    // Start observing all bookings for updates
                    allBookings.forEach { booking ->
                        observeBookingStatus(booking.id)
                        android.util.Log.d("DriverViewModel", "📝 Restored booking: ${booking.id} with status: ${booking.status}")
                    }

                    // Restore passenger location monitoring for current booking if needed
                    currentBooking?.let { booking ->
                        when (booking.status) {
                            BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING, BookingStatus.DRIVER_ARRIVED -> {
                                // Start monitoring passenger location during pickup phase
                                viewModelScope.launch {
                                    observePassengerLocation(booking)

                                    // Recalculate route to pickup (only if not already arrived)
                                    if (booking.status != BookingStatus.DRIVER_ARRIVED) {
                                        _uiState.value.currentUserLocation?.let { driverLoc ->
                                            val driverLocation = BookingLocation(
                                                address = "Current Location",
                                                coordinates = com.google.firebase.firestore.GeoPoint(
                                                    driverLoc.latitude(),
                                                    driverLoc.longitude()
                                                )
                                            )
                                            mapboxRepository.getRoute(driverLocation, booking.pickupLocation, forceRealRoute = true)
                                                .onSuccess { route ->
                                                    _uiState.value = _uiState.value.copy(routeInfo = route)
                                                    android.util.Log.d("DriverViewModel", "✅ Restored route to pickup: ${route.totalDistance}km")
                                                }
                                        }
                                    } else {
                                        android.util.Log.d("DriverViewModel", "ℹ️ Driver already arrived, skipping route calculation")
                                    }
                                }
                            }
                            BookingStatus.IN_PROGRESS -> {
                                // Trip is in progress, fetch route for navigation
                                fetchRouteForTrip(booking)
                            }
                            else -> { /* No additional action needed */ }
                        }
                    }

                    android.util.Log.i("DriverViewModel", "✅ Restored booking state - Current: ${currentBooking?.id}, Queued: ${queuedBookings.size} rides")

                } else {
                    android.util.Log.d("DriverViewModel", "ℹ️ No active bookings found to restore")
                }

            } catch (e: Exception) {
                android.util.Log.e("DriverViewModel", "❌ Failed to restore active bookings for driver $driverId", e)
                android.util.Log.e("DriverViewModel", "Error details: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        try {
            android.util.Log.d("DriverHomeViewModel", "🧹 onCleared() called - starting cleanup")

            // Remove auth state listener
            authStateListener?.let {
                auth.removeAuthStateListener(it)
                android.util.Log.d("DriverHomeViewModel", "✅ Auth state listener removed")
            }

            // Set driver offline when ViewModel is destroyed (backup to MainActivity.onDestroy)
            val currentUser = auth.currentUser
            if (currentUser != null && _uiState.value.online) {
                android.util.Log.d("DriverHomeViewModel", "ViewModel cleared - setting driver offline for: ${currentUser.uid}")

                // Use GlobalScope instead of runBlocking to avoid blocking main thread
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        driverRepository.updateDriverStatus(online = false).onSuccess {
                            android.util.Log.d("DriverHomeViewModel", "✅ Successfully set driver offline on ViewModel clear")
                        }.onFailure { exception ->
                            android.util.Log.e("DriverHomeViewModel", "❌ Failed to set driver offline on ViewModel clear", exception)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        android.util.Log.w("DriverHomeViewModel", "⚠️ Coroutine cancelled while setting driver offline", e)
                    } catch (e: Exception) {
                        android.util.Log.e("DriverHomeViewModel", "❌ Error setting driver offline on ViewModel clear", e)
                    }
                }
            }

            stopLocationUpdates()
            stopStaleDriverMonitoring()
            proximityAlertUtils.release()

            // Additional cleanup for optimized managers
            optimizedLocationManager.stopLocationUpdates()
            optimizedRouteManager.stopRouteFollowing()
            enhancedDriverLocationService.stopLocationUpdates()
            enhancedDriverLocationService.cleanupDriverLocation()

            android.util.Log.d("DriverHomeViewModel", "✅ Enhanced cleanup completed in onCleared")
        } catch (e: Exception) {
            android.util.Log.e("DriverHomeViewModel", "💥 Error in onCleared", e)
        } finally {
            super.onCleared()
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun checkLocationPermissions() {
        val hasPermissions = locationUtils.hasLocationPermissions()
        _uiState.value = _uiState.value.copy(hasLocationPermissions = hasPermissions)
    }

    /**
     * Load current user location
     */
    fun loadCurrentLocation() {
        if (!locationUtils.hasLocationPermissions()) {
            _uiState.value = _uiState.value.copy(hasLocationPermissions = false)
            return
        }

        viewModelScope.launch {
            try {
                val currentLocation = locationUtils.getCurrentLocation()
                _uiState.value = _uiState.value.copy(
                    currentUserLocation = currentLocation,
                    hasLocationPermissions = true,
                    errorMessage = null // Clear any previous errors
                )
            } catch (e: Exception) {
                Log.e("DriverHomeVM", "Error loading current location", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = locationUtils.getLocationErrorMessage()
                )
            }
        }
    }

    /**
     * Start location updates for live tracking - OPTIMIZED VERSION
     */
    fun startLocationUpdates() {
        if (!locationUtils.hasLocationPermissions()) {
            _uiState.value = _uiState.value.copy(hasLocationPermissions = false)
            return
        }

        stopLocationUpdates() // Stop any existing updates

        val isActiveRide = _uiState.value.currentBooking != null
        android.util.Log.d("DriverHomeVM", "🚀 Starting optimized location updates. Active ride: $isActiveRide")

        // Use optimized location manager for throttled updates
        optimizedLocationManager.startLocationUpdates(
            onLocationUpdate = { point ->
                // Get smoothed location for route calculation
                val smoothedLocation = optimizedLocationManager.getSmoothedLocation(point)

                _uiState.value = _uiState.value.copy(
                    currentUserLocation = smoothedLocation,
                    errorMessage = null // Clear any previous location errors
                )
                android.util.Log.d("DriverHomeVM", "📍 Optimized location updated: ${smoothedLocation.latitude()}, ${smoothedLocation.longitude()}")

                // Update driver location in route manager for deviation detection
                optimizedRouteManager.updateDriverLocation(smoothedLocation)

                // Start optimized route tracking if there's an active booking
                _uiState.value.currentBooking?.let { booking ->
                    startOptimizedRouteTracking(booking, smoothedLocation)
                }

                // Check proximity to pickup location if there's an active booking
                checkProximityToPickup(smoothedLocation)
            },
            onError = { exception ->
                android.util.Log.w("DriverHomeVM", "⚠️ Optimized location error: ${exception.message}")
                // Handle errors with user-friendly messages
                val userFriendlyMessage = when {
                    exception.message?.contains("GPS signal unavailable") == true ->
                        "GPS signal weak. Please ensure location services are on and try moving to an open area."
                    exception.message?.contains("permission denied") == true ->
                        "Location permission denied. Please enable location permissions in settings."
                    else -> "Location update temporarily unavailable. Checking GPS signal..."
                }
                _uiState.value = _uiState.value.copy(errorMessage = userFriendlyMessage)
            },
            isActiveRide = isActiveRide
        )

        // Also start enhanced driver location service for Firebase updates
        enhancedDriverLocationService.startLocationUpdates(
            onLocationUpdate = { point ->
                // Firebase updates are handled by the enhanced service
                android.util.Log.d("DriverHomeVM", "📡 Firebase location updated: ${point.latitude()}, ${point.longitude()}")
            },
            onError = { exception ->
                android.util.Log.w("DriverHomeVM", "⚠️ Enhanced location service error: ${exception.message}")
            },
            isActiveRide = isActiveRide
        )
    }

    /**
     * Calculate route ONCE - like Google Maps, then follow it - ONLY CALCULATE ONCE per destination
     * Like Google Maps: Route is calculated once when ride starts, polyline persists as driver moves
     */
    private fun startOptimizedRouteTracking(booking: Booking, driverLocation: Point) {
        _uiState.value.currentUserLocation?.let { currentLocation ->
            val origin = BookingLocation(
                address = "Current Location",
                coordinates = com.google.firebase.firestore.GeoPoint(
                    currentLocation.latitude(),
                    currentLocation.longitude()
                )
            )
            val destination = if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.DRIVER_ARRIVING) {
                booking.pickupLocation
            } else {
                booking.destination
            }

            // Check if we need to calculate a new route (destination changed or first time)
            val needsNewRoute = !isRouteCalculated ||
                                lastCalculatedDestination == null ||
                                lastCalculatedDestination != destination

            if (needsNewRoute) {
                android.util.Log.d("DriverHomeVM", "🗺️ Calculating route for NEW destination: ${destination.address}")

                // Calculate route ONCE (Google Maps approach)
                viewModelScope.launch {
                    optimizedRouteManager.calculateRouteOnce(origin, destination) { route ->
                        _uiState.value = _uiState.value.copy(routeInfo = route)
                        android.util.Log.d("DriverHomeVM", "✅ Route calculated: ${route?.totalDistance}m - Route will persist as driver moves")

                        // Mark route as calculated
                        isRouteCalculated = true
                        lastCalculatedDestination = destination

                        // Start following the pre-calculated route (deviation detection only)
                        if (route != null) {
                            optimizedRouteManager.startRouteFollowing(driverLocation) { newRoute ->
                                _uiState.value = _uiState.value.copy(routeInfo = newRoute)
                                android.util.Log.w("DriverHomeVM", "🔄 Route recalculated due to major deviation")
                            }
                        }
                    }
                }
            } else {
                // Route already calculated - just update driver position, polyline stays visible
                android.util.Log.d("DriverHomeVM", "📍 Driver location updated - Route polyline persists (no API call)")
            }
        }
    }

    private fun observeIncomingRequests() {
        viewModelScope.launch {
            val driverId = auth.currentUser?.uid ?: return@launch

            matchingRepository.observeDriverRequests(driverId).collect { requests ->
                val currentTime = System.currentTimeMillis()

                android.util.Log.d("DriverHomeVM", "Received ${requests.size} requests from repository")

                // Filter and verify each request's booking is still active
                val validRequests = mutableListOf<DriverRequest>()

                for (request in requests) {
                    // Verify the request is in INITIAL phase with PENDING status
                    if (!request.isInInitialPhase(currentTime) ||
                        request.status != DriverRequestStatus.PENDING) {
                        android.util.Log.d("DriverHomeVM", "Filtering out non-initial request: ${request.requestId}")
                        continue
                    }

                    // Check if the booking associated with this request has been cancelled
                    if (request.bookingId.isNotBlank()) {
                        val bookingResult = bookingRepository.getBooking(request.bookingId)
                        bookingResult.fold(
                            onSuccess = { booking ->
                                if (booking?.status == BookingStatus.CANCELLED) {
                                    android.util.Log.w("DriverHomeVM", "Booking ${request.bookingId} is cancelled - removing request ${request.requestId}")
                                    // Don't add this request
                                } else {
                                    validRequests.add(request)
                                }
                            },
                            onFailure = {
                                // If we can't verify, include it (better to show than hide)
                                validRequests.add(request)
                            }
                        )
                    } else {
                        validRequests.add(request)
                    }
                }

                android.util.Log.d("DriverHomeVM", "Filtered to ${validRequests.size} valid INITIAL requests")

                // Check for new requests and trigger sound/vibration
                if (validRequests.size > previousRequestCount && validRequests.isNotEmpty()) {
                    val newRequestCount = validRequests.size - previousRequestCount
                    android.util.Log.d("DriverHomeVM", "🔔 Detected $newRequestCount new ride request(s)")
                    vibrateForNewRideRequest()
                    playRideRequestSound()
                }

                // Update the previous count for next comparison
                previousRequestCount = validRequests.size

                _uiState.update { it.copy(incomingRequests = validRequests) }
            }
        }
    }

    /**
     * Stop location updates - OPTIMIZED VERSION
     */
    fun stopLocationUpdates() {
        locationUpdatesStopCallback?.invoke()
        locationUpdatesStopCallback = null

        // Stop optimized managers
        optimizedLocationManager.stopLocationUpdates()
        optimizedRouteManager.stopRouteFollowing()
        enhancedDriverLocationService.stopLocationUpdates()

        android.util.Log.d("DriverHomeVM", "🛑 All optimized location updates stopped")
    }

    /**
     * Handle location permissions result
     */
    fun onLocationPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasLocationPermissions = granted)
        if (granted) {
            // Add small delay to allow system to process permission change
            viewModelScope.launch {
                delay(500) // 500ms delay
                loadCurrentLocation()
                startLocationUpdates() // Restart location updates
            }
        }
    }

    /**
     * Toggle driver online/offline status with integrated location tracking
     */
    fun toggleOnlineStatus() {
        val currentState = _uiState.value
        val newOnlineStatus = !currentState.online

        // OPTIMISTIC UPDATE: Update UI immediately for better responsiveness
        _uiState.value = currentState.copy(
            online = newOnlineStatus,
            isLoading = true,
            errorMessage = null,
            // Clear requests when going offline
            incomingRequests = if (newOnlineStatus) currentState.incomingRequests else emptyList()
        )

        viewModelScope.launch {
            if (newOnlineStatus) {
                // Going online - check for verification first
                val driverId = auth.currentUser?.uid
                if (driverId == null) {
                    _uiState.value = currentState.copy(
                        online = false,
                        isLoading = false,
                        errorMessage = "Authentication error. Please log in again."
                    )
                    return@launch
                }

                userRepository.getUserByUid(driverId).onSuccess { user ->
                    if (user.driverData?.verificationStatus == VerificationStatus.APPROVED) {
                        // Driver is verified, proceed with going online
                        viewModelScope.launch {
                            try {
                                val locationJob = async { loadCurrentLocation() }
                                val statusJob = async { driverLocationService.setDriverOnlineStatus(true) }

                                locationJob.await()
                                statusJob.await().onSuccess {
                                    driverRepository.updateDriverStatus(
                                        online = true,
                                        vehicleCategory = currentState.selectedVehicleCategory
                                    ).onSuccess {
                                        _uiState.value = _uiState.value.copy(isLoading = false)
                                        startLocationUpdates()
                                        auth.currentUser?.let { user ->
                                            launch {
                                                // AUTO-CLEANUP: Remove stuck bookings before processing queued bookings
                                                android.util.Log.d("DriverViewModel", "🧹 Running auto-cleanup for stuck bookings...")
                                                matchingRepository.cleanupStuckBookings(user.uid).onSuccess { cleanedCount ->
                                                    if (cleanedCount > 0) {
                                                        android.util.Log.w("DriverViewModel", "✅ Auto-cleanup: Cancelled $cleanedCount stuck booking(s)")
                                                    } else {
                                                        android.util.Log.d("DriverViewModel", "✅ Auto-cleanup: No stuck bookings found")
                                                    }
                                                }.onFailure { exception ->
                                                    android.util.Log.e("DriverViewModel", "⚠️ Auto-cleanup failed (non-critical): ${exception.message}")
                                                }

                                                android.util.Log.w("DriverViewModel", "🚀🚀🚀 CRITICAL: Driver ${user.uid} went online - processing queued bookings...")
                                                matchingRepository.processQueuedBookingsForNewDriver(user.uid).onSuccess { count ->
                                                    android.util.Log.w("DriverViewModel", "✅✅✅ CRITICAL: Successfully processed $count requests for newly online driver ${user.uid}")
                                                }.onFailure { exception ->
                                                    android.util.Log.e("DriverViewModel", "❌❌❌ CRITICAL: Failed to process queued bookings for driver ${user.uid}", exception)
                                                }
                                            }
                                        }
                                        val currentLocation = _uiState.value.currentUserLocation
                                        if (currentLocation != null) {
                                            launch {
                                                android.util.Log.d("DriverViewModel", "📍 Updating initial driver location to Firebase: ${currentLocation.latitude()}, ${currentLocation.longitude()}")
                                                driverRepository.updateDriverLocation(
                                                    latitude = currentLocation.latitude(),
                                                    longitude = currentLocation.longitude()
                                                ).onSuccess {
                                                    android.util.Log.d("DriverViewModel", "✅ Initial driver location updated in Firebase successfully")
                                                }.onFailure { exception ->
                                                    android.util.Log.w("DriverViewModel", "⚠️ Failed to update initial driver location: ${exception.message}")
                                                }
                                            }
                                        } else {
                                            android.util.Log.w("DriverViewModel", "⚠️ No current location available when going online")
                                        }
                                    }.onFailure { exception ->
                                        _uiState.value = _uiState.value.copy(
                                            online = false,
                                            isLoading = false,
                                            errorMessage = exception.message ?: "Failed to go online"
                                        )
                                    }
                                }.onFailure { exception ->
                                    android.util.Log.e("DriverViewModel", "Failed to set online status: ${exception.message}")
                                    // Check if it's a service boundary error
                                    val errorMsg = exception.message ?: "Failed to start location tracking"
                                    if (errorMsg.contains("You must be within", ignoreCase = true)) {
                                        _uiState.value = _uiState.value.copy(
                                            online = false,
                                            isLoading = false,
                                            showServiceBoundaryDialog = true,
                                            serviceBoundaryMessage = errorMsg
                                        )
                                    } else {
                                        _uiState.value = _uiState.value.copy(
                                            online = false,
                                            isLoading = false,
                                            errorMessage = errorMsg
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                _uiState.value = _uiState.value.copy(
                                    online = false,
                                    isLoading = false,
                                    errorMessage = e.message ?: "Failed to go online"
                                )
                            }
                        }
                    } else {
                        // Driver is not verified
                        _uiState.value = currentState.copy(
                            online = false,
                            isLoading = false,
                            errorMessage = "Your account is not verified. Please complete your document submission and wait for admin approval."
                        )
                    }
                }.onFailure { exception ->
                    _uiState.value = currentState.copy(
                        online = false,
                        isLoading = false,
                        errorMessage = "Failed to check verification status: ${exception.message}"
                    )
                }
            } else {
                // Going offline - handle background operations
                viewModelScope.launch {
                    try {
                        driverLocationService.setDriverOnlineStatus(false)
                            .onSuccess {
                                driverRepository.updateDriverStatus(online = false)
                                    .onSuccess {
                                        _uiState.value = _uiState.value.copy(isLoading = false)
                                        stopLocationUpdates()
                                    }
                                    .onFailure { exception ->
                                        _uiState.value = _uiState.value.copy(
                                            online = true,
                                            isLoading = false,
                                            errorMessage = exception.message ?: "Failed to go offline"
                                        )
                                    }
                            }
                            .onFailure { exception ->
                                _uiState.value = _uiState.value.copy(
                                    online = true,
                                    isLoading = false,
                                    errorMessage = exception.message ?: "Failed to stop location tracking"
                                )
                            }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            online = true,
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to go offline"
                        )
                    }
                }
            }
        }
    }

    /**
     * Accept an incoming ride request
     */
    fun acceptRideRequest(request: DriverRequest) {
        // Debug the request object to see what's in it
        android.util.Log.d("DriverViewModel", "Accepting ride request:")
        android.util.Log.d("DriverViewModel", "  requestId: '${request.requestId}'")
        android.util.Log.d("DriverViewModel", "  bookingId: '${request.bookingId}'")
        android.util.Log.d("DriverViewModel", "  driverId: '${request.driverId}'")
        android.util.Log.d("DriverViewModel", "  passengerId: '${request.passengerId}'")

        // Check if driver already has 5 queued bookings (max limit)
        val totalAcceptedRides = _uiState.value.queuedBookings.size + (if (_uiState.value.currentBooking != null) 1 else 0)
        if (totalAcceptedRides >= 5) {
            android.util.Log.w("DriverViewModel", "❌ Cannot accept new ride request - driver already has 5 accepted rides")
            android.util.Log.w("DriverViewModel", "   Current booking: ${_uiState.value.currentBooking?.id}")
            android.util.Log.w("DriverViewModel", "   Queued bookings: ${_uiState.value.queuedBookings.size}")

            _uiState.value = _uiState.value.copy(
                errorMessage = "Cannot accept new ride request. You already have 5 rides queued.",
                isLoading = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // RACE CONDITION PREVENTION: Check if booking is still active before accepting
            if (request.bookingId.isNotBlank()) {
                val bookingCheck = bookingRepository.getBooking(request.bookingId)
                bookingCheck.fold(
                    onSuccess = { booking ->
                        if (booking?.status == BookingStatus.CANCELLED) {
                            android.util.Log.w("DriverViewModel", "❌ Cannot accept ride - booking ${request.bookingId} was already cancelled by passenger")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                showPassengerCancellationDialog = true,
                                errorMessage = "This ride was cancelled by the passenger."
                            )
                            // Remove this request from UI immediately
                            removeRequestFromUi(request)
                            return@launch
                        }
                    },
                    onFailure = {
                        android.util.Log.w("DriverViewModel", "⚠️ Could not verify booking status, proceeding with acceptance")
                        // Continue with acceptance if we can't check
                    }
                )
            }

            matchingRepository.acceptRideRequest(request.requestId, request.driverId)
                .onSuccess {
                    // Check if driver will reach 5 rides after accepting this one
                    val newTotalRides = _uiState.value.queuedBookings.size + (if (_uiState.value.currentBooking != null) 1 else 0) + 1
                    val shouldStayOnline = newTotalRides < 5

                    // Only mark driver offline if they've reached 5 accepted rides
                    launch {
                        val currentLocation = _uiState.value.currentUserLocation
                        if (currentLocation != null) {
                            driverRepository.updateDriverLocation(
                                latitude = currentLocation.latitude(),
                                longitude = currentLocation.longitude(),
                                online = shouldStayOnline // Stay online if under 5 rides
                            )
                        }
                    }

                    // Remove the accepted request from incoming requests and keep others visible (unless at limit)
                    val updatedRequests = if (shouldStayOnline) {
                        _uiState.value.incomingRequests.filter { it.requestId != request.requestId }
                    } else {
                        emptyList()
                    }

                    // Reset proximity alerts for new booking
                    resetProximityAlerts()

                    // Create booking object from request
                    android.util.Log.d("DriverViewModel", "Processing request - requestId: '${request.requestId}', bookingId: '${request.bookingId}'")

                    val bookingId = if (request.bookingId.isNotBlank()) {
                        android.util.Log.d("DriverViewModel", "Using bookingId from request: '${request.bookingId}'")
                        request.bookingId
                    } else {
                        // Extract booking ID from requestId format: "bookingId_driverId_timestamp"
                        // Handle case where bookingId might start with underscore or be malformed
                        val requestIdParts = request.requestId.split("_")
                        android.util.Log.d("DriverViewModel", "requestId parts: $requestIdParts")

                        val extractedId = if (requestIdParts.size >= 3) {
                            // Reconstruct bookingId by taking all parts except the last 2 (driverId and timestamp)
                            val result = requestIdParts.dropLast(2).joinToString("_")
                            android.util.Log.d("DriverViewModel", "Reconstructed bookingId: '$result'")

                            // Check if result is empty (malformed requestId that started with underscore)
                            if (result.isBlank()) {
                                android.util.Log.w("DriverViewModel", "Malformed requestId - booking ID is empty, creating fallback")
                                "fallback_${request.driverId}_${System.currentTimeMillis()}"
                            } else {
                                result
                            }
                        } else {
                            // Fallback: use the requestId as-is if format doesn't match expected pattern
                            android.util.Log.w("DriverViewModel", "Unexpected requestId format, using as-is")
                            request.requestId
                        }
                        android.util.Log.w("DriverViewModel", "bookingId is empty, extracted from requestId: '$extractedId' (from '${request.requestId}')")
                        extractedId
                    }

                    android.util.Log.d("DriverViewModel", "Final bookingId to use: '$bookingId'")

                    val booking = Booking(
                        id = bookingId,
                        passengerId = request.passengerId,
                        driverId = request.driverId,
                        pickupLocation = request.pickupLocation.toBookingLocation(),
                        destination = request.destination.toBookingLocation(),
                        fareEstimate = request.fareEstimate.toFareEstimate(),
                        status = BookingStatus.ACCEPTED,
                        vehicleCategory = _uiState.value.selectedVehicleCategory,
                        specialInstructions = request.specialInstructions
                    )

                    android.util.Log.d("DriverViewModel", "Created booking object with ID: '${booking.id}'")

                    // Validation check - ensure the booking object has the correct ID
                    if (booking.id != bookingId) {
                        android.util.Log.e("DriverViewModel", "CRITICAL: Booking object ID mismatch! Expected: '$bookingId', Got: '${booking.id}'")
                    }
                    if (booking.id.isBlank()) {
                        android.util.Log.e("DriverViewModel", "CRITICAL: Booking object has empty ID after creation!")
                    }

                    // Get passenger's live location (not pickup location)
                    val passengerPoint = try {
                        // Try to get passenger's current live location
                        val liveLocation = passengerLocationService.getPassengerLocation(
                            booking.id,
                            booking.passengerId
                        ).getOrNull()

                        liveLocation ?: run {
                            // Fallback to pickup location if live location not available yet
                            Log.w("DriverHomeViewModel", "Passenger live location not available yet, using pickup location as fallback")
                            Point.fromLngLat(
                                booking.pickupLocation.coordinates.longitude,
                                booking.pickupLocation.coordinates.latitude
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("DriverHomeViewModel", "Failed to get passenger location, using pickup as fallback", e)
                        Point.fromLngLat(
                            booking.pickupLocation.coordinates.longitude,
                            booking.pickupLocation.coordinates.latitude
                        )
                    }

                    // Add booking to queue or set as current booking
                    android.util.Log.e("DriverViewModel", "📋 QUEUE LOGIC - Before adding new booking:")
                    android.util.Log.e("DriverViewModel", "   - Current booking: ${_uiState.value.currentBooking?.id}")
                    android.util.Log.e("DriverViewModel", "   - Queued bookings: ${_uiState.value.queuedBookings.map { it.id }}")
                    android.util.Log.e("DriverViewModel", "   - New booking to add: ${booking.id}")

                    val newQueuedBookings = if (_uiState.value.currentBooking != null) {
                        // Already has a current booking, add to queue
                        android.util.Log.e("DriverViewModel", "   ✅ Adding ${booking.id} to QUEUE (current exists)")
                        _uiState.value.queuedBookings + booking
                    } else {
                        // No current booking, queue stays the same
                        android.util.Log.e("DriverViewModel", "   ✅ No current booking, queue unchanged")
                        _uiState.value.queuedBookings
                    }

                    val newCurrentBooking = if (_uiState.value.currentBooking != null) {
                        // Keep existing current booking
                        android.util.Log.e("DriverViewModel", "   ✅ Keeping existing current: ${_uiState.value.currentBooking?.id}")
                        _uiState.value.currentBooking
                    } else {
                        // Set this as current booking
                        android.util.Log.e("DriverViewModel", "   ✅ Setting ${booking.id} as CURRENT (no existing current)")
                        booking
                    }

                    android.util.Log.e("DriverViewModel", "📋 QUEUE LOGIC - After processing:")
                    android.util.Log.e("DriverViewModel", "   - NEW current booking: ${newCurrentBooking?.id}")
                    android.util.Log.e("DriverViewModel", "   - NEW queued bookings: ${newQueuedBookings.map { it.id }}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        incomingRequests = updatedRequests,
                        currentBooking = newCurrentBooking,
                        queuedBookings = newQueuedBookings,
                        currentActiveBookingId = newCurrentBooking?.id, // Set active booking ID
                        passengerLocation = if (_uiState.value.currentBooking == null) passengerPoint else _uiState.value.passengerLocation,
                        showNavigationToPassenger = if (_uiState.value.currentBooking == null) true else _uiState.value.showNavigationToPassenger,
                        // Keep UI showing as online if under 5 rides
                        online = shouldStayOnline,
                        // Navigate to Map tab after accepting ride
                        shouldNavigateToMapTab = true
                    )

                    // Pre-calculate routes for all queued bookings (including this one if queued)
                    launch {
                        preCacheRoutesForQueuedBookings()
                    }

                    // Only fetch route and observe passenger for current booking (not queued ones)
                    if (_uiState.value.currentBooking == booking) {
                        // Fetch route to pickup location using Google Maps Directions API
                        launch {
                            val currentLocation = _uiState.value.currentUserLocation
                            if (currentLocation != null) {
                                val driverLocation = BookingLocation(
                                    address = "Driver Location",
                                    coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                                )

                                // Calculate route from driver to pickup for navigation - FORCE REAL ROUTE for active ride
                                mapboxRepository.getRoute(driverLocation, booking.pickupLocation, forceRealRoute = true)
                                    .onSuccess { route ->
                                        _uiState.value = _uiState.value.copy(routeInfo = route)
                                        Log.d("DriverHomeViewModel", "ACTIVE RIDE: Route to pickup fetched successfully with ${route.waypoints.size} waypoints, distance: ${route.totalDistance}km, Route ID: ${route.routeId}")
                                        if (route.routeId.startsWith("simple_direct")) {
                                            Log.w("DriverHomeViewModel", "⚠️ WARNING: Got simple direct route instead of real road routing for active ride!")
                                        } else {
                                            Log.i("DriverHomeViewModel", "✅ SUCCESS: Using real Mapbox Directions API for active ride navigation")
                                        }
                                    }
                                    .onFailure { exception ->
                                        Log.e("DriverHomeViewModel", "Failed to fetch route to pickup for active ride, trying fallback route", exception)
                                        // Try fallback route without forcing real route
                                        mapboxRepository.getRoute(driverLocation, booking.pickupLocation, forceRealRoute = false)
                                            .onSuccess { fallbackRoute ->
                                                _uiState.value = _uiState.value.copy(routeInfo = fallbackRoute)
                                                Log.w("DriverHomeViewModel", "USING FALLBACK: Route to pickup calculated with fallback method, Route ID: ${fallbackRoute.routeId}")
                                            }
                                            .onFailure { fallbackException ->
                                                Log.e("DriverHomeViewModel", "Both primary and fallback route calculation failed for active ride", fallbackException)
                                                // Create a simple direct route as last resort
                                                val directRoute = mapboxRepository.createSimpleDirectRoute(driverLocation, booking.pickupLocation)
                                                _uiState.value = _uiState.value.copy(routeInfo = directRoute)
                                                Log.w("DriverHomeViewModel", "USING DIRECT ROUTE: Created simple direct route as last resort")
                                            }
                                    }

                                // If fare estimate has 0 distance, calculate actual trip distance (pickup to destination)
                                if (booking.fareEstimate.estimatedDistance == 0.0) {
                                    Log.d("DriverHomeViewModel", "Fare estimate has 0 distance, calculating actual trip route")
                                    // Force real route for active ride trip distance calculation
                                    mapboxRepository.getRoute(booking.pickupLocation, booking.destination, forceRealRoute = true)
                                        .onSuccess { tripRoute ->
                                            Log.d("DriverHomeViewModel", "ACTIVE RIDE: Calculated actual trip distance: ${tripRoute.totalDistance}km, duration: ${tripRoute.estimatedDuration}min, Route ID: ${tripRoute.routeId}")
                                            if (tripRoute.routeId.startsWith("simple_direct")) {
                                                Log.w("DriverHomeViewModel", "⚠️ WARNING: Trip distance calculation used simple direct route instead of real roads!")
                                            } else {
                                                Log.i("DriverHomeViewModel", "✅ SUCCESS: Trip distance calculated using real Mapbox Directions API")
                                            }

                                            // Update the current booking with correct distance
                                            val updatedFareEstimate = booking.fareEstimate.copy(
                                                estimatedDistance = tripRoute.totalDistance,
                                                estimatedDuration = tripRoute.estimatedDuration
                                            )
                                            val updatedBooking = booking.copy(fareEstimate = updatedFareEstimate)

                                            _uiState.value = _uiState.value.copy(currentBooking = updatedBooking)
                                            Log.d("DriverHomeViewModel", "Updated booking fare estimate with actual distance: ${tripRoute.totalDistance}km")
                                        }
                                        .onFailure { exception ->
                                            Log.w("DriverHomeViewModel", "Failed to calculate trip route distance", exception)
                                        }
                                }
                            }
                        }

                        // Start observing passenger's live location for real-time updates
                        launch {
                            observePassengerLocation(booking)
                        }

                        // Start observing booking status for real-time cancellation updates
                        launch {
                            observeBookingStatus(booking.id)
                        }
                    } else {
                        // For queued bookings, just observe the booking status for cancellations
                        launch {
                            observeBookingStatus(booking.id)
                        }
                    }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to accept request"
                    )
                }
        }
    }

    /**
     * Decline an incoming ride request
     */
    fun declineRideRequest(request: DriverRequest) {
        viewModelScope.launch {
            matchingRepository.declineRequest(request.requestId, request.driverId)
                .onSuccess {
                    // Request will be auto-removed by the observer
                    android.util.Log.d("DriverHomeVM", "Successfully declined request: ${request.requestId}")
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to decline request: ${exception.message}"
                    )
                }
        }
    }

    /**
     * Switch to a different ride from the queue
     * Allows driver to navigate to a different accepted booking
     */
    fun switchToRide(bookingId: String) {
        android.util.Log.d("DriverViewModel", "Switching to ride: $bookingId")

        val currentBooking = _uiState.value.currentBooking
        val queuedBookings = _uiState.value.queuedBookings

        // Check if already viewing this booking
        if (currentBooking?.id == bookingId) {
            android.util.Log.d("DriverViewModel", "Already viewing this booking")
            return
        }

        // Find the booking in the queue
        val bookingToActivate = queuedBookings.find { it.id == bookingId }

        if (bookingToActivate == null) {
            android.util.Log.w("DriverViewModel", "Booking $bookingId not found in queue")
            return
        }

        // Build new queue: add current booking back to queue, remove the activated booking
        val updatedQueue = buildList {
            // Add current booking back to queue if it exists
            if (currentBooking != null) {
                add(currentBooking)
            }
            // Add all other queued bookings except the one being activated
            addAll(queuedBookings.filter { it.id != bookingId })
        }

        // Update passenger location to the new active booking's pickup location
        val newPassengerLocation = try {
            Point.fromLngLat(
                bookingToActivate.pickupLocation.coordinates.longitude,
                bookingToActivate.pickupLocation.coordinates.latitude
            )
        } catch (e: Exception) {
            android.util.Log.e("DriverViewModel", "Failed to create passenger location point", e)
            _uiState.value.passengerLocation // Keep existing location if conversion fails
        }

        android.util.Log.d("DriverViewModel", "🎯 MARKER FIX: Updating passenger location for booking $bookingId")
        android.util.Log.d("DriverViewModel", "   Old passenger location: ${_uiState.value.passengerLocation}")
        android.util.Log.d("DriverViewModel", "   New passenger location: $newPassengerLocation")

        android.util.Log.d("DriverViewModel", "🎯 MARKER DEBUG: Before state update:")
        android.util.Log.d("DriverViewModel", "   - New current booking: ${bookingToActivate.id}")
        android.util.Log.d("DriverViewModel", "   - Updated queue (${updatedQueue.size} bookings): ${updatedQueue.map { it.id }}")
        android.util.Log.d("DriverViewModel", "   - Current active booking ID: $bookingId")

        // Update UI state with new active booking
        _uiState.value = _uiState.value.copy(
            currentBooking = bookingToActivate,
            queuedBookings = updatedQueue,
            currentActiveBookingId = bookingId,
            passengerLocation = newPassengerLocation
        )

        android.util.Log.d("DriverViewModel", "🎯 MARKER DEBUG: After state update:")
        android.util.Log.d("DriverViewModel", "   - UI state current booking: ${_uiState.value.currentBooking?.id}")
        android.util.Log.d("DriverViewModel", "   - UI state queued bookings: ${_uiState.value.queuedBookings.map { it.id }}")
        android.util.Log.d("DriverViewModel", "   - UI state active booking ID: ${_uiState.value.currentActiveBookingId}")

        android.util.Log.d("DriverViewModel", "Switched to booking: $bookingId, Queue size: ${updatedQueue.size}")

        // Fetch route and observe passenger for the newly activated booking
        viewModelScope.launch {
            val currentLocation = _uiState.value.currentUserLocation
            if (currentLocation != null) {
                // Determine destination based on booking status
                val targetLocation = when (bookingToActivate.status) {
                    BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING -> {
                        // Navigate to pickup location
                        bookingToActivate.pickupLocation
                    }
                    BookingStatus.DRIVER_ARRIVED, BookingStatus.IN_PROGRESS -> {
                        // Navigate to destination
                        bookingToActivate.destination
                    }
                    else -> {
                        android.util.Log.w("DriverViewModel", "Unexpected booking status: ${bookingToActivate.status}")
                        bookingToActivate.pickupLocation
                    }
                }

                val driverLocation = BookingLocation(
                    address = "Driver Location",
                    coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                )

                // Fetch route to target location
                val routeResult = mapboxRepository.getRoute(
                    origin = driverLocation,
                    destination = targetLocation,
                    forceRealRoute = true
                )

                routeResult.onSuccess { route ->
                    _uiState.value = _uiState.value.copy(routeInfo = route)
                    android.util.Log.d("DriverViewModel", "Route fetched for booking $bookingId")
                }.onFailure { error ->
                    android.util.Log.e("DriverViewModel", "Failed to fetch route: ${error.message}")
                }
            }

            // Start observing passenger location for the new active booking
            launch { observePassengerLocation(bookingToActivate) }
        }
    }

    /**
     * Complete current trip
     */
    /**
     * Mark driver as arrived at pickup location
     */
    fun arrivedAtPickup() {
        val currentBooking = _uiState.value.currentBooking ?: return

        // Enhanced debugging for booking validation
        android.util.Log.d("DriverViewModel", "Current booking object: id='${currentBooking.id}', status=${currentBooking.status}")
        android.util.Log.d("DriverViewModel", "Booking ID length: ${currentBooking.id.length}")
        android.util.Log.d("DriverViewModel", "Booking ID characters: ${currentBooking.id.toCharArray().contentToString()}")

        // Validate booking ID
        if (currentBooking.id.isBlank()) {
            android.util.Log.e("DriverViewModel", "Cannot mark arrival - booking ID is blank")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Booking ID is empty. Please try accepting a new ride."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            android.util.Log.d("DriverViewModel", "Marking arrival for booking: ${currentBooking.id}")

            try {
                // Update booking status in repository to DRIVER_ARRIVED
                bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.DRIVER_ARRIVED)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            currentBooking = currentBooking.copy(status = BookingStatus.DRIVER_ARRIVED),
                            isLoading = false
                        )
                        android.util.Log.i("DriverViewModel", "Driver marked as arrived at pickup for booking ${currentBooking.id}")

                        // Navigation to destination will be handled manually after passenger enters vehicle
                    }
                    .onFailure { exception ->
                        android.util.Log.e("DriverViewModel", "Failed to mark arrival for booking ${currentBooking.id}", exception)

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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to mark arrival: ${e.message}"
                )
            }
        }
    }

    /**
     * Start the trip (passenger has entered the vehicle)
     */
    fun startTrip() {
        val currentBooking = _uiState.value.currentBooking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Update booking status in repository to IN_PROGRESS
                bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.IN_PROGRESS)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            currentBooking = currentBooking.copy(status = BookingStatus.IN_PROGRESS),
                            isLoading = false
                        )
                        android.util.Log.i("DriverViewModel", "Trip started for booking ${currentBooking.id}")

                        // Reset route flag when status changes to IN_PROGRESS (new destination: pickup -> dropoff)
                        isRouteCalculated = false
                        lastCalculatedDestination = null
                        android.util.Log.d("DriverHomeVM", "🔄 Route flag reset - Status changed to IN_PROGRESS, will calculate route to destination")

                        // Fetch route from pickup to destination for navigation
                        fetchRouteForTrip(currentBooking)
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to start trip: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start trip: ${e.message}"
                )
            }
        }
    }

    /**
     * Cancel the current trip with default reason
     */
    fun cancelTrip() {
        cancelTripWithReason("Driver cancelled the trip")
    }

    /**
     * Cancel the current trip with a specific reason - notifies passenger and updates booking status
     */
    fun cancelTripWithReason(reason: String) {
        val currentBooking = _uiState.value.currentBooking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // STEP 1: Cancel the booking in repository (this will update status to CANCELLED)
                val cancellationResult = bookingRepository.cancelBooking(
                    bookingId = currentBooking.id,
                    reason = reason,
                    cancelledBy = "driver"  // Specify that driver cancelled
                )

                cancellationResult.onSuccess {
                    android.util.Log.d("DriverViewModel", "Booking cancelled successfully, now sending passenger notification")

                    // STEP 2: Send notification to passenger about driver cancellation
                    val notificationResult = notificationService.sendRideUpdateToPassenger(
                        booking = currentBooking,
                        status = BookingStatus.CANCELLED,
                        additionalData = mapOf(
                            "cancelled_by" to "driver",
                            "reason" to reason
                        )
                    )

                    notificationResult.onSuccess {
                        android.util.Log.i("DriverViewModel", "✅ Successfully queued passenger notification for trip cancellation")
                    }.onFailure { exception ->
                        android.util.Log.e("DriverViewModel", "❌ Failed to queue passenger notification: ${exception.message}", exception)
                        // Continue with cancellation even if notification fails
                    }

                    // STEP 2.5: Also send a general notification as backup (for testing)
                    try {
                        launch {
                            val backupResult = notificationService.sendGeneralNotification(
                                userId = currentBooking.passengerId,
                                title = "Driver Cancelled Trip",
                                body = "Your driver cancelled: $reason. We'll help you find another driver.",
                                data = mapOf(
                                    "type" to "ride_cancelled",
                                    "booking_id" to currentBooking.id,
                                    "cancelled_by" to "driver",
                                    "reason" to reason
                                )
                            )

                            backupResult.onSuccess {
                                android.util.Log.i("DriverViewModel", "Backup notification sent successfully")
                            }.onFailure { exception ->
                                android.util.Log.w("DriverViewModel", "Backup notification also failed: ${exception.message}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("DriverViewModel", "Failed to send backup notification", e)
                    }

                    // STEP 3: Just set loading to false
                    // NOTE: Queue management is now handled by observeBookingStatus -> handleSpecificBookingCancellation
                    // which will automatically promote the next booking and maintain the 60/40 view
                    _uiState.value = _uiState.value.copy(isLoading = false)

                    // Reset route calculation flag for next booking
                    isRouteCalculated = false
                    lastCalculatedDestination = null
                    optimizedRouteManager.clearRoute()
                    android.util.Log.d("DriverHomeVM", "🗑️ Route calculation flag reset after cancellation")

                    android.util.Log.i("DriverViewModel", "✅ Cancellation initiated for booking ${currentBooking.id}. Queue management handled by observer.")

                }.onFailure { exception ->
                    // Failed to cancel booking
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to cancel trip: ${exception.message}"
                    )
                    android.util.Log.e("DriverViewModel", "Failed to cancel booking ${currentBooking.id}", exception)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to cancel trip: ${e.message}"
                )
                android.util.Log.e("DriverViewModel", "Exception during trip cancellation", e)
            }
        }
    }

    fun completeTrip() {
        val currentBooking = _uiState.value.currentBooking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Update booking status to completed in repository
                val bookingUpdateResult = bookingRepository.updateBookingStatus(currentBooking.id, BookingStatus.COMPLETED)

                bookingUpdateResult.onSuccess {
                    // Save earnings to driver profile in Firebase
                    val currentUserId = auth.currentUser?.uid
                    if (currentUserId != null) {
                        // Calculate actual fare after discount
                        val actualFare = if (currentBooking.passengerDiscountPercentage != null && currentBooking.passengerDiscountPercentage > 0) {
                            val discountMultiplier = (100 - currentBooking.passengerDiscountPercentage) / 100.0
                            currentBooking.fareEstimate.totalEstimate * discountMultiplier
                        } else {
                            currentBooking.fareEstimate.totalEstimate
                        }

                        android.util.Log.d("DriverViewModel", "💰 Fare calculation: Original=₱${currentBooking.fareEstimate.totalEstimate}, Discount=${currentBooking.passengerDiscountPercentage ?: 0}%, Actual=₱$actualFare")

                        // Update User document with total earnings (using discounted amount)
                        userRepository.updateDriverEarnings(
                            uid = currentUserId,
                            additionalEarnings = actualFare,
                            incrementTrips = true
                        ).onFailure { e ->
                            android.util.Log.e("DriverViewModel", "Failed to save earnings to Firebase", e)
                        }

                        // Process payment to update weekly earnings
                        try {
                            paymentRepository.processPayment(
                                booking = currentBooking,
                                paymentMethod = com.rj.islamove.data.models.PaymentMethod.CASH
                            )
                            android.util.Log.d("DriverViewModel", "Payment processed successfully: ₱$actualFare")
                        } catch (e: Exception) {
                            android.util.Log.e("DriverViewModel", "Failed to process payment for weekly earnings", e)
                        }
                    }

                    // IMPORTANT: Restore driver to online status and available for new rides
                    val statusUpdateJob = async {
                        driverRepository.updateDriverStatus(
                            online = true,
                            vehicleCategory = _uiState.value.selectedVehicleCategory
                        )
                    }

                    val locationServiceJob = async {
                        driverLocationService.setDriverOnlineStatus(true)
                    }

                    // Wait for both to complete
                    statusUpdateJob.await().onSuccess {
                        locationServiceJob.await().onSuccess {
                            // Check if driver has already rated this booking
                            val hasAlreadyRated = hasDriverAlreadyRated(currentBooking.id)
                            val shouldTriggerRating = !hasAlreadyRated

                            // Reload earnings data to get updated weekly earnings
                            loadDriverProfile()

                            // Advance to next booking in queue, if any
                            // Ensure current booking is removed from queued bookings to prevent duplicates
                            val queuedBookingsWithoutCurrent = _uiState.value.queuedBookings.filter { it.id != currentBooking.id }
                            val nextBooking = queuedBookingsWithoutCurrent.firstOrNull()
                            val remainingQueue = queuedBookingsWithoutCurrent.drop(1)

                            android.util.Log.d("DriverHomeViewModel", "Queue filtering: original=${_uiState.value.queuedBookings.size}, without current=${queuedBookingsWithoutCurrent.size}, next booking: ${nextBooking?.id}")

                            // Show rating screen after each completed ride (regardless of queued rides)
                            val shouldNavigateToRatingNow = shouldTriggerRating

                            if (hasAlreadyRated) {
                                android.util.Log.d("DriverHomeViewModel", "Driver has already rated booking ${currentBooking.id}, skipping rating screen")
                            }

                            android.util.Log.d("DriverHomeViewModel", "Trip completed, triggering rating navigation: bookingId=${currentBooking.id}, passengerId=${currentBooking.passengerId}")

                            // Reset route calculation flag for next booking
                            isRouteCalculated = false
                            lastCalculatedDestination = null
                            optimizedRouteManager.clearRoute()
                            android.util.Log.d("DriverHomeVM", "🗑️ Route calculation flag reset after ride completion")

                            // Set up state for rating navigation or advance to next booking
                            if (shouldNavigateToRatingNow) {
                                // Ensure queued bookings are preserved (without current booking to prevent duplicates)
                                android.util.Log.d("DriverHomeViewModel", "Before rating navigation: current queue size = ${_uiState.value.queuedBookings.size}, filtered queue size = ${queuedBookingsWithoutCurrent.size}, will preserve all ${queuedBookingsWithoutCurrent.size} bookings")

                                // Trigger rating navigation - don't advance to next booking yet
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    currentBooking = null, // Clear current booking during rating
                                    currentActiveBookingId = null,
                                    todayTrips = _uiState.value.todayTrips + 1,
                                    // Reset trip-related state but KEEP driver online
                                    passengerLocation = null,
                                    showNavigationToPassenger = false,
                                    online = true, // Ensure driver stays online
                                    // Trigger rating navigation
                                    shouldNavigateToRating = true,
                                    completedBookingId = currentBooking.id,
                                    completedPassengerId = currentBooking.passengerId,
                                    // IMPORTANT: Preserve ALL queued bookings (not remainingQueue which drops the first one)
                                    queuedBookings = queuedBookingsWithoutCurrent
                                )

                                android.util.Log.d("DriverHomeViewModel", "Rating navigation triggered, preserved ${queuedBookingsWithoutCurrent.size} bookings in queue")
                            } else {
                                // No rating needed, advance directly to next booking
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    currentBooking = nextBooking,
                                    queuedBookings = remainingQueue, // Using filtered queue without current booking
                                    currentActiveBookingId = nextBooking?.id,
                                    todayTrips = _uiState.value.todayTrips + 1,
                                    // Reset trip-related state but KEEP driver online
                                    passengerLocation = null,
                                    showNavigationToPassenger = nextBooking != null,
                                    online = true, // Ensure driver stays online
                                    // No rating navigation
                                    shouldNavigateToRating = false,
                                    completedBookingId = null,
                                    completedPassengerId = null,
                                    // Clear previous route to prevent showing old route
                                    routeInfo = null
                                )

                                // If we have a next booking, set it up for navigation
                                if (nextBooking != null) {
                                    android.util.Log.d("DriverHomeViewModel", "Advancing to next queued booking: ${nextBooking.id}")

                                    // Get passenger location for the next booking
                                    launch {
                                        // Set loading state while calculating route
                                        _uiState.value = _uiState.value.copy(isLoading = true)

                                        val passengerPoint = try {
                                            val liveLocation = passengerLocationService.getPassengerLocation(
                                                nextBooking.id,
                                                nextBooking.passengerId
                                            ).getOrNull()

                                            liveLocation ?: Point.fromLngLat(
                                                nextBooking.pickupLocation.coordinates.longitude,
                                                nextBooking.pickupLocation.coordinates.latitude
                                            )
                                        } catch (e: Exception) {
                                            Point.fromLngLat(
                                                nextBooking.pickupLocation.coordinates.longitude,
                                                nextBooking.pickupLocation.coordinates.latitude
                                            )
                                        }

                                        _uiState.value = _uiState.value.copy(
                                            passengerLocation = passengerPoint,
                                            isLoading = false // Clear loading after passenger location is set
                                        )

                                        // Check for cached route first, then fetch if needed
                                        val cachedRoute = getCachedRoute(nextBooking.id)
                                        if (cachedRoute != null) {
                                            // Use cached route instantly
                                            _uiState.value = _uiState.value.copy(routeInfo = cachedRoute)
                                            android.util.Log.d("DriverHomeViewModel", "⚡ Using cached route for next passenger: ${nextBooking.id}")
                                        } else {
                                            // No cached route, calculate it
                                            android.util.Log.d("DriverHomeViewModel", "No cached route for ${nextBooking.id}, calculating now...")

                                            val currentLocation = _uiState.value.currentUserLocation
                                            if (currentLocation != null) {
                                                val driverLocation = BookingLocation(
                                                    address = "Driver Location",
                                                    coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                                                )

                                                mapboxRepository.getRoute(driverLocation, nextBooking.pickupLocation, forceRealRoute = true)
                                                    .onSuccess { route ->
                                                        _uiState.value = _uiState.value.copy(routeInfo = route)
                                                        android.util.Log.d("DriverHomeViewModel", "Route calculated for next passenger: ${nextBooking.id}")
                                                    }
                                                    .onFailure { error ->
                                                        android.util.Log.e("DriverHomeViewModel", "Failed to calculate route for next passenger: ${nextBooking.id} - no straight line fallback", error)
                                                        // Keep routeInfo as null - better to show no route than a straight line
                                                    }
                                            }
                                        }

                                        // Start observing the next booking
                                        observePassengerLocation(nextBooking)
                                        observeBookingStatus(nextBooking.id)
                                    }
                                }
                            }

                            // Update driver location to show as available again
                            val currentLocation = _uiState.value.currentUserLocation
                            if (currentLocation != null) {
                                launch {
                                    driverRepository.updateDriverLocation(
                                        latitude = currentLocation.latitude(),
                                        longitude = currentLocation.longitude()
                                    )
                                }
                            }

                            android.util.Log.d("DriverHomeViewModel", "Trip completed, triggering rating navigation: bookingId=${currentBooking.id}, passengerId=${currentBooking.passengerId}")

                        }.onFailure { exception ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "Failed to restore online status: ${exception.message}"
                            )
                        }
                    }.onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to restore online status: ${exception.message}"
                        )
                    }
                }.onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to complete trip: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                // Reload earnings data to get updated weekly earnings
                loadDriverProfile()

                // Advance to next booking even in error case
                // Ensure current booking is removed from queued bookings to prevent duplicates
                val queuedBookingsWithoutCurrent = _uiState.value.queuedBookings.filter { it.id != currentBooking.id }
                val nextBooking = queuedBookingsWithoutCurrent.firstOrNull()
                val remainingQueue = queuedBookingsWithoutCurrent.drop(1)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentBooking = nextBooking,
                    queuedBookings = remainingQueue,
                    todayTrips = _uiState.value.todayTrips + 1,
                    // Reset trip-related state and ensure driver stays online
                    passengerLocation = null,
                    showNavigationToPassenger = nextBooking != null,
                    online = true,
                    errorMessage = "Trip completed but failed to restore online status: ${e.message}"
                )
            }
        }
    }

    /**
     * Load driver profile information and earnings from Firebase
     */
    private fun loadDriverProfile() {
        viewModelScope.launch {
            val driverId = auth.currentUser?.uid
            if (driverId == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Authentication required. Please log in as a driver."
                )
                return@launch
            }

            // Load driver profile
            driverRepository.getDriverProfile(driverId)
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(driverProfile = profile)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to load profile: ${exception.message}"
                    )
                }

            // Load driver earnings from User document
            userRepository.getUserByUid(driverId)
                .onSuccess { user ->
                    val earnings = user.driverData?.totalEarnings ?: 0.0
                    val trips = user.driverData?.totalTrips ?: 0
                    android.util.Log.d("DriverViewModel", "Loaded persistent earnings: ₱$earnings, trips: $trips")

                    // Also load weekly earnings from PaymentRepository
                    viewModelScope.launch {
                        try {
                            paymentRepository.getDriverEarnings(driverId).collect { driverEarnings ->
                                val thisWeek = java.text.SimpleDateFormat("yyyy-'W'ww", java.util.Locale.getDefault()).format(java.util.Date())
                                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

                                val weeklyEarnings = driverEarnings?.weeklyEarnings?.get(thisWeek) ?: 0.0
                                val todayEarnings = driverEarnings?.dailyEarnings?.get(today) ?: 0.0

                                _uiState.value = _uiState.value.copy(
                                    totalEarnings = earnings,
                                    todayEarnings = todayEarnings,
                                    weeklyEarnings = weeklyEarnings,
                                    todayTrips = trips  // This shows total trips, not just today - can be improved later
                                )
                                android.util.Log.d("DriverViewModel", "Loaded weekly earnings: ₱$weeklyEarnings")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DriverViewModel", "Failed to load weekly earnings", e)
                            // Still set the basic earnings even if weekly fails
                            _uiState.value = _uiState.value.copy(
                                totalEarnings = earnings,
                                todayEarnings = 0.0,
                                weeklyEarnings = 0.0,
                                todayTrips = trips
                            )
                        }
                    }
                }
                .onFailure { exception ->
                    android.util.Log.e("DriverViewModel", "Failed to load driver earnings from Firebase", exception)
                }
        }
    }

    /**
     * Load driver earnings - now relies on persistent Firebase data
     */
    private fun loadEarnings() {
        // Don't calculate earnings from ride requests here - this was overriding Firebase earnings
        // The persistent earnings from loadDriverProfile() are the authoritative source
        android.util.Log.d("DriverViewModel", "loadEarnings: Using persistent Firebase earnings, not recalculating from requests")
    }


    /**
     * Load service area destinations as landmarks visible to driver
     */
    private fun loadServiceAreaDestinations() {
        viewModelScope.launch {
            try {
                val serviceAreaResult = serviceAreaManagementRepository.getAllServiceAreas()
                val serviceAreaLandmarks = if (serviceAreaResult.isSuccess) {
                    serviceAreaResult.getOrNull()?.flatMap { area ->
                        area.destinations.map { destination ->
                            CustomLandmark(
                                id = destination.id,
                                name = "${destination.name} - ₱${destination.regularFare}",
                                latitude = destination.latitude,
                                longitude = destination.longitude,
                                color = destination.markerColor,
                                createdAt = destination.createdAt
                            )
                        }
                    } ?: emptyList()
                } else {
                    Log.w("DriverHomeViewModel", "Failed to load service areas: ${serviceAreaResult.exceptionOrNull()}")
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(customLandmarks = serviceAreaLandmarks)
                Log.d("DriverHomeViewModel", "Loaded ${serviceAreaLandmarks.size} service area destinations for driver")
            } catch (e: Exception) {
                Log.e("DriverHomeViewModel", "Error loading service area destinations", e)
            }
        }
    }

    private fun getTodayStartTime(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Get the ZoneBoundaryRepository for use in boundary determination
     */
    fun getZoneBoundaryRepository(): ZoneBoundaryRepository {
        return zoneBoundaryRepository
    }


    /**
     * Hide passenger cancellation dialog notification
     */
    fun hidePassengerCancellationDialog() {
        _uiState.value = _uiState.value.copy(
            showPassengerCancellationDialog = false,
            errorMessage = null
        )
        android.util.Log.d("DriverHomeVM", "Dismissed passenger cancellation dialog")
    }

    /**
     * Hide service boundary dialog notification
     */
    fun hideServiceBoundaryDialog() {
        _uiState.value = _uiState.value.copy(
            showServiceBoundaryDialog = false,
            serviceBoundaryMessage = null
        )
        android.util.Log.d("DriverHomeVM", "Dismissed service boundary dialog")
    }

  
  
    /**
     * Start timer updates to keep countdown current - simplified since repository handles categorization
     */
    private fun startTimerUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second
                val currentTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(currentTimeMillis = currentTime)
            }
        }
    }

    /**
     * Observe passenger's live location during active trip
     */
    private suspend fun observePassengerLocation(booking: Booking) {
        try {
            passengerLocationService.getPassengerLocationFlow(booking.id).collect { passengerPoint ->
                passengerPoint?.let { point ->
                    // Only update if we still have the same booking
                    if (_uiState.value.currentBooking?.id == booking.id) {
                        _uiState.value = _uiState.value.copy(
                            passengerLocation = point
                        )
                        Log.d("DriverHomeViewModel", "Updated passenger location: ${point.latitude()}, ${point.longitude()}")

                        // Only recalculate route for driver's current location changes, NOT passenger GPS changes
                        // Reason: During pickup phase, driver routes to STATIC pickup address
                        // During trip, driver routes to STATIC destination address
                        // Passenger GPS tracking is just for display, not routing!
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DriverHomeViewModel", "Failed to observe passenger location", e)
        }
    }

    /**
     * Observe booking status for real-time updates (especially cancellations)
     */
    private suspend fun observeBookingStatus(bookingId: String) {
        try {
            Log.d("DriverHomeViewModel", "👀 Starting to observe booking status for: $bookingId")
            bookingRepository.observeBooking(bookingId).collect { result ->
                result.onSuccess { updatedBooking ->
                    if (updatedBooking != null) {
                        val currentState = _uiState.value
                        val currentBooking = currentState.currentBooking

                        // Check if this booking is the current one OR in the queue
                        val isCurrentBooking = currentBooking?.id == bookingId
                        val isInQueue = currentState.queuedBookings.any { it.id == bookingId }
                        val queuedBooking = currentState.queuedBookings.find { it.id == bookingId }

                        Log.d("DriverHomeViewModel", "📊 Observing booking $bookingId: isCurrent=$isCurrentBooking, isQueued=$isInQueue, status=${updatedBooking.status}")

                        // Only process if this booking is either current or queued
                        if (isCurrentBooking || isInQueue) {
                            // Handle cancellation - immediate cleanup regardless of who cancelled
                            if (updatedBooking.status == BookingStatus.CANCELLED) {
                                val previousStatus = if (isCurrentBooking) currentBooking?.status else queuedBooking?.status

                                // Only process if status actually changed to CANCELLED
                                if (previousStatus != BookingStatus.CANCELLED) {
                                    Log.e("DriverHomeViewModel", "🚫 CANCELLATION DETECTED for booking $bookingId")
                                    Log.e("DriverHomeViewModel", "   - Is current booking: $isCurrentBooking")
                                    Log.e("DriverHomeViewModel", "   - Is in queue: $isInQueue")
                                    Log.e("DriverHomeViewModel", "   - Cancelled by: ${updatedBooking.cancelledBy}")
                                    Log.e("DriverHomeViewModel", "🧹 Starting cleanup for specific booking...")

                                    // Remove only this specific cancelled booking, keep others
                                    handleSpecificBookingCancellation(bookingId, updatedBooking.cancelledBy ?: "unknown")

                                    Log.i("DriverHomeViewModel", "✅ Cleanup completed for booking: $bookingId")
                                    return@collect // Exit the observer since booking is cancelled
                                }
                            } else if (isCurrentBooking) {
                                // Update current booking with new data (other status changes)
                                _uiState.value = currentState.copy(currentBooking = updatedBooking)
                                Log.d("DriverHomeViewModel", "🔄 Updated current booking with new status: ${updatedBooking.status}")

                                // IMPORTANT: Only trigger route-related side effects for CURRENT booking
                                if (updatedBooking.status == BookingStatus.IN_PROGRESS) {
                                    // Reset route flag when status changes to IN_PROGRESS (new destination: pickup -> dropoff)
                                    isRouteCalculated = false
                                    lastCalculatedDestination = null
                                    android.util.Log.d("DriverHomeVM", "🔄 Route flag reset - Current booking status changed to IN_PROGRESS, will calculate route to destination")

                                    // Fetch route from pickup to destination for navigation (ONLY for current booking)
                                    fetchRouteForTrip(updatedBooking)
                                }
                            } else if (isInQueue) {
                                // Update queued booking with new data
                                val updatedQueue = currentState.queuedBookings.map { booking ->
                                    if (booking.id == bookingId) updatedBooking else booking
                                }
                                _uiState.value = currentState.copy(queuedBookings = updatedQueue)
                                Log.d("DriverHomeViewModel", "🔄 Updated queued booking with new status: ${updatedBooking.status}")

                                // IMPORTANT: DO NOT trigger route calculations for queued bookings
                                if (updatedBooking.status == BookingStatus.IN_PROGRESS) {
                                    Log.w("DriverHomeViewModel", "⚠️ Queued booking ${bookingId} status changed to IN_PROGRESS, but NOT triggering route calculation (only current booking gets routes)")
                                }
                            }
                        } else {
                            Log.w("DriverHomeViewModel", "⚠️ Received update for booking not in current/queue. BookingId: $bookingId")
                        }
                    } else {
                        // Booking was deleted, treat as cancellation
                        Log.w("DriverHomeViewModel", "🗑️ Booking $bookingId document was deleted - treating as emergency cancellation")
                        handleSpecificBookingCancellation(bookingId, "system_deleted")
                    }
                }.onFailure { exception ->
                    Log.e("DriverHomeViewModel", "❌ Failed to observe booking status for $bookingId", exception)
                    // If observation fails, we might need to check manually
                    scheduleManualCancellationCheck(bookingId)
                }
            }
        } catch (e: Exception) {
            Log.e("DriverHomeViewModel", "💥 CRITICAL: Failed to set up booking status observer for $bookingId", e)
            // Schedule manual check as backup
            scheduleManualCancellationCheck(bookingId)
        }
    }

    /**
     * Handle cancellation of a specific booking (current or queued)
     * This keeps other bookings intact
     */
    private suspend fun handleSpecificBookingCancellation(cancelledBookingId: String, cancelledBy: String) {
        try {
            Log.e("DriverHomeViewModel", "🧹 handleSpecificBookingCancellation for booking: $cancelledBookingId (by: $cancelledBy)")

            val currentState = _uiState.value
            val isCurrentBooking = currentState.currentBooking?.id == cancelledBookingId
            val isInQueue = currentState.queuedBookings.any { it.id == cancelledBookingId }

            Log.e("DriverHomeViewModel", "📋 BEFORE CANCELLATION:")
            Log.e("DriverHomeViewModel", "   - currentBooking: ${currentState.currentBooking?.id}")
            Log.e("DriverHomeViewModel", "   - queuedBookings: ${currentState.queuedBookings.map { it.id }}")

            // Remove cancelled booking from queue
            val filteredQueue = currentState.queuedBookings.filter { it.id != cancelledBookingId }

            // Determine new current booking
            val newCurrentBooking = if (isCurrentBooking) {
                // Current booking cancelled, promote first from queue
                filteredQueue.firstOrNull()
            } else {
                // Keep current booking (wasn't cancelled)
                currentState.currentBooking
            }

            // Update remaining queue
            val newQueue = if (isCurrentBooking && filteredQueue.isNotEmpty()) {
                filteredQueue.drop(1) // Remove first since it became current
            } else {
                filteredQueue
            }

            val hasRemainingRides = newCurrentBooking != null || newQueue.isNotEmpty()

            Log.e("DriverHomeViewModel", "📝 AFTER CANCELLATION:")
            Log.e("DriverHomeViewModel", "   - NEW currentBooking: ${newCurrentBooking?.id}")
            Log.e("DriverHomeViewModel", "   - NEW queuedBookings: ${newQueue.map { it.id }}")
            Log.e("DriverHomeViewModel", "   - Has remaining rides: $hasRemainingRides")
            Log.e("DriverHomeViewModel", "   - Should keep 60/40 view: $hasRemainingRides")

            // Update state
            _uiState.value = currentState.copy(
                currentBooking = newCurrentBooking,
                queuedBookings = newQueue,
                passengerLocation = if (isCurrentBooking) null else currentState.passengerLocation,
                showNavigationToPassenger = hasRemainingRides,
                routeInfo = if (isCurrentBooking) null else currentState.routeInfo,
                incomingRequests = currentState.incomingRequests.filter { it.bookingId != cancelledBookingId },
                secondChanceRequests = currentState.secondChanceRequests.filter { it.bookingId != cancelledBookingId },
                recentDeclinedRequests = currentState.recentDeclinedRequests.filter { it.bookingId != cancelledBookingId },
                currentActiveBookingId = newCurrentBooking?.id,
                showPassengerCancellationDialog = (cancelledBy == "passenger" && (isCurrentBooking || isInQueue)),
                errorMessage = if (cancelledBy == "passenger" && (isCurrentBooking || isInQueue))
                    "A passenger cancelled their ride request." else null
            )

            Log.e("DriverHomeViewModel", "✅ State updated - UI should ${if (hasRemainingRides) "STAY in 60/40" else "go to 100%"} view")

            // Only stop navigation if no remaining rides
            if (!hasRemainingRides) {
                stopNavigation()
            }

            // If we promoted a new current booking, start observing it and fetch route
            if (isCurrentBooking && newCurrentBooking != null) {
                Log.d("DriverHomeViewModel", "🔄 Promoted booking ${newCurrentBooking.id} to current, starting observers and fetching route")

                // Get passenger location
                viewModelScope.launch {
                    val passengerPoint = try {
                        val liveLocation = passengerLocationService.getPassengerLocation(
                            newCurrentBooking.id,
                            newCurrentBooking.passengerId
                        ).getOrNull()

                        liveLocation ?: Point.fromLngLat(
                            newCurrentBooking.pickupLocation.coordinates.longitude,
                            newCurrentBooking.pickupLocation.coordinates.latitude
                        )
                    } catch (e: Exception) {
                        Point.fromLngLat(
                            newCurrentBooking.pickupLocation.coordinates.longitude,
                            newCurrentBooking.pickupLocation.coordinates.latitude
                        )
                    }

                    _uiState.value = _uiState.value.copy(passengerLocation = passengerPoint)

                    // Fetch route to pickup location
                    val currentLocation = _uiState.value.currentUserLocation
                    if (currentLocation != null) {
                        val driverLocation = BookingLocation(
                            address = "Driver Location",
                            coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                        )

                        mapboxRepository.getRoute(driverLocation, newCurrentBooking.pickupLocation)
                            .onSuccess { route ->
                                _uiState.value = _uiState.value.copy(routeInfo = route)
                                Log.d("DriverHomeViewModel", "✅ Route fetched for promoted booking: ${route.totalDistance}km")
                            }
                            .onFailure { e ->
                                Log.e("DriverHomeViewModel", "❌ Failed to fetch route for promoted booking", e)
                            }
                    }

                    // Start observing passenger location for updates
                    observePassengerLocation(newCurrentBooking)
                }
            }

        } catch (e: Exception) {
            Log.e("DriverHomeViewModel", "💥 CRITICAL: handleSpecificBookingCancellation failed", e)
        }
    }

    /**
     * Perform comprehensive cleanup when a booking is cancelled
     * This ensures no "ghost rides" remain in the driver's UI
     * Only removes requests related to the cancelled booking, keeping other valid requests
     */
    private suspend fun performGhostRideCleanup(cancelledBy: String) {
        try {
            Log.i("DriverHomeViewModel", "🧹 Starting ghost ride cleanup (cancelled by: $cancelledBy)")

            val currentState = _uiState.value
            val hadAcceptedBooking = currentState.currentBooking != null
            val cancelledBookingId = currentState.currentBooking?.id

            // Remove the cancelled booking from queue if it exists there
            val filteredQueuedBookings = if (cancelledBookingId != null) {
                currentState.queuedBookings.filter { it.id != cancelledBookingId }
            } else {
                currentState.queuedBookings
            }

            // Advance to the next booking in queue if there are any remaining
            val nextBooking = filteredQueuedBookings.firstOrNull()
            val remainingQueue = filteredQueuedBookings.drop(1)

            // Check if we have remaining rides to maintain 60/40 split view
            val hasRemainingRides = nextBooking != null || remainingQueue.isNotEmpty()

            // Clear booking-related state and advance to next booking if available
            _uiState.value = currentState.copy(
                currentBooking = nextBooking, // Set to next booking if available, otherwise null
                passengerLocation = null,
                showNavigationToPassenger = hasRemainingRides,
                routeInfo = null,
                // Only remove requests related to the cancelled booking, keep other valid requests
                incomingRequests = if (cancelledBookingId != null) {
                    currentState.incomingRequests.filter { it.bookingId != cancelledBookingId }
                } else {
                    currentState.incomingRequests
                },
                secondChanceRequests = if (cancelledBookingId != null) {
                    currentState.secondChanceRequests.filter { it.bookingId != cancelledBookingId }
                } else {
                    currentState.secondChanceRequests
                },
                recentDeclinedRequests = if (cancelledBookingId != null) {
                    currentState.recentDeclinedRequests.filter { it.bookingId != cancelledBookingId }
                } else {
                    currentState.recentDeclinedRequests
                },
                // Set queued bookings to remaining queue
                queuedBookings = remainingQueue,
                currentActiveBookingId = nextBooking?.id, // Set to next booking ID if available
                // Show dialog only if passenger cancelled AND driver had accepted the ride
                showPassengerCancellationDialog = (cancelledBy == "passenger" && hadAcceptedBooking),
                errorMessage = if (cancelledBy == "passenger" && hadAcceptedBooking) "A passenger cancelled their ride request." else null
            )

            Log.d("DriverHomeViewModel", "🧹 Ghost ride cleanup completed - removed requests for booking: $cancelledBookingId")

            // Stop navigation to reset map view to normal mode
            // Only call stopNavigation if we don't have remaining rides to avoid interfering with 60/40 split view
            if (!hasRemainingRides) {
                stopNavigation()
            }

            // Restore driver to online status for new rides
            val newState = _uiState.value
            val currentLocation = newState.currentUserLocation

            // Update both location service and repository
            driverLocationService.setDriverOnlineStatus(true)

            if (currentLocation != null) {
                driverRepository.updateDriverLocation(
                    latitude = currentLocation.latitude(),
                    longitude = currentLocation.longitude(),
                    online = true // Critical: ensure driver is marked as online
                ).onSuccess {
                    Log.d("DriverHomeViewModel", "✅ Driver location updated: online=true")
                }.onFailure { e ->
                    Log.e("DriverHomeViewModel", "❌ Failed to update driver location", e)
                }
            }

            // Also update driver status in repository
            driverRepository.updateDriverStatus(
                online = true,
                vehicleCategory = currentState.selectedVehicleCategory
            ).onSuccess {
                Log.d("DriverHomeViewModel", "✅ Driver status updated: online=true")
            }.onFailure { e ->
                Log.e("DriverHomeViewModel", "❌ Failed to update driver status", e)
            }

            // Reset proximity alerts
            resetProximityAlerts()

            Log.i("DriverHomeViewModel", "✅ Ghost ride cleanup completed successfully")

        } catch (e: Exception) {
            Log.e("DriverHomeViewModel", "💥 CRITICAL: Ghost ride cleanup failed", e)
            // Even if cleanup fails, try to at least clear the current booking
            _uiState.value = _uiState.value.copy(
                currentBooking = null,
                errorMessage = "Error: Ride cancelled but cleanup failed. Please restart app."
            )
        }
    }

    /**
     * Schedule manual cancellation check as backup when real-time observation fails
     */
    private fun scheduleManualCancellationCheck(bookingId: String) {
        viewModelScope.launch {
            Log.w("DriverHomeViewModel", "⏰ Scheduling manual cancellation check for booking: $bookingId")

            // Check every 5 seconds for up to 1 minute
            repeat(12) { attempt ->
                delay(5000) // 5 seconds

                try {
                    val result = bookingRepository.getBooking(bookingId)
                    result.fold(
                        onSuccess = { booking ->
                            if (booking?.status == BookingStatus.CANCELLED) {
                                Log.i("DriverHomeViewModel", "🔍 Manual check detected cancellation for booking: $bookingId")
                                performGhostRideCleanup(booking.cancelledBy ?: "manual_check")
                                return@launch // Stop checking
                            } else if (_uiState.value.currentBooking?.id == bookingId) {
                                Log.d("DriverHomeViewModel", "🔍 Manual check: booking still active (attempt ${attempt + 1}/12)")
                            }
                        },
                        onFailure = {
                            Log.w("DriverHomeViewModel", "🔍 Manual check failed (attempt ${attempt + 1}/12)")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("DriverHomeViewModel", "🔍 Manual check error (attempt ${attempt + 1}/12)", e)
                }
            }

            Log.w("DriverHomeViewModel", "⏰ Manual cancellation check completed for booking: $bookingId")
        }
    }

    /**
     * Start external navigation app (Google Maps, Waze, etc.)
     */
    private fun startExternalNavigation(destination: BookingLocation) {
        // This would typically open Google Maps or another navigation app
        // with turn-by-turn directions
        val lat = destination.coordinates.latitude
        val lng = destination.coordinates.longitude
        Log.d("DriverHomeViewModel", "Opening external navigation to: $lat, $lng (${destination.address})")
        // Intent to open navigation app would go here in actual implementation
    }

    fun stopNavigation() {
        Log.d("DriverHomeViewModel", "Navigation stopped")
        // Stop any ongoing navigation tracking
        _uiState.value = _uiState.value.copy(routeInfo = null)
    }

    /**
     * Fetch route information for trip navigation
     */
    private fun fetchRouteForTrip(booking: Booking) {
        Log.d("DriverHomeViewModel", "fetchRouteForTrip: Starting ACTIVE RIDE route calculation from current location to destination")

        // Get driver's current location
        val currentLocation = _uiState.value.currentUserLocation
        if (currentLocation == null) {
            Log.w("DriverHomeViewModel", "Cannot fetch route: Current location is null")
            return
        }

        val driverCurrentLocation = BookingLocation(
            address = "Current Location",
            coordinates = com.google.firebase.firestore.GeoPoint(
                currentLocation.latitude(),
                currentLocation.longitude()
            )
        )

        Log.d("DriverHomeViewModel", "  Current Location: (${currentLocation.latitude()}, ${currentLocation.longitude()})")
        Log.d("DriverHomeViewModel", "  Destination: ${booking.destination.address} (${booking.destination.coordinates.latitude}, ${booking.destination.coordinates.longitude})")

        viewModelScope.launch {
            try {
                // Force real Mapbox Directions API for active ride navigation - from CURRENT location to destination
                mapboxRepository.getRoute(driverCurrentLocation, booking.destination, forceRealRoute = true)
                    .onSuccess { route ->
                        Log.d("DriverHomeViewModel", "fetchRouteForTrip: ACTIVE RIDE route calculation SUCCESS!")
                        Log.d("DriverHomeViewModel", "  Route ID: ${route.routeId}")
                        Log.d("DriverHomeViewModel", "  Waypoints: ${route.waypoints.size}")
                        Log.d("DriverHomeViewModel", "  Distance: ${route.totalDistance}km")
                        Log.d("DriverHomeViewModel", "  Duration: ${route.estimatedDuration}min")

                        if (route.routeId.startsWith("simple_direct")) {
                            Log.w("DriverHomeViewModel", "⚠️ WARNING: fetchRouteForTrip got simple direct route instead of real roads for active ride!")
                        } else {
                            Log.i("DriverHomeViewModel", "✅ SUCCESS: fetchRouteForTrip using real Mapbox Directions API for active ride")
                        }

                        _uiState.value = _uiState.value.copy(routeInfo = route)

                        Log.d("DriverHomeViewModel", "fetchRouteForTrip: UI State updated with routeInfo")
                        Log.d("DriverHomeViewModel", "  Current booking status: ${_uiState.value.currentBooking?.status}")
                        Log.d("DriverHomeViewModel", "  showRoute will be: ${_uiState.value.currentBooking != null}")

                        if (route.routeId.startsWith("fallback_route_")) {
                            Log.i("DriverHomeViewModel", "Using fallback route - ${"%.1f".format(route.totalDistance)}km estimated")
                            // Show notification about fallback route
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "ℹ️ Showing estimated route. For precise navigation, use Google Maps or Waze.",
                            )
                        } else {
                            Log.d("DriverHomeViewModel", "Route fetched successfully with ${route.waypoints.size} waypoints")
                        }
                    }
                    .onFailure { exception ->
                        Log.e("DriverHomeViewModel", "Failed to fetch route for trip", exception)
                        // Still allow trip to continue without route visualization
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Navigation temporarily unavailable. Please use your preferred maps app."
                        )
                    }
            } catch (e: Exception) {
                Log.e("DriverHomeViewModel", "Error fetching route for trip", e)
            }
        }
    }

    /**
     * Pre-calculate routes for all queued bookings to enable instant switching
     */
    private suspend fun preCacheRoutesForQueuedBookings() {
        val currentLocation = _uiState.value.currentUserLocation ?: return
        val queuedBookings = _uiState.value.queuedBookings

        android.util.Log.d("DriverHomeViewModel", "🚀 Pre-caching routes for ${queuedBookings.size} queued bookings")

        val updatedCachedRoutes = _uiState.value.cachedRoutes.toMutableMap()

        queuedBookings.forEach { booking ->
            // Skip if route is already cached
            if (updatedCachedRoutes.containsKey(booking.id)) {
                android.util.Log.d("DriverHomeViewModel", "   ✅ Route already cached for booking ${booking.id}")
                return@forEach
            }

            try {
                val driverLocation = BookingLocation(
                    address = "Driver Location",
                    coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                )

                android.util.Log.d("DriverHomeViewModel", "   📍 Calculating route to booking ${booking.id} pickup")

                mapboxRepository.getRoute(driverLocation, booking.pickupLocation, forceRealRoute = true)
                    .onSuccess { route ->
                        updatedCachedRoutes[booking.id] = route
                        android.util.Log.d("DriverHomeViewModel", "   ✅ Route cached for booking ${booking.id}: ${route.totalDistance}km, ${route.estimatedDuration}min")

                        // Update UI with new cached routes
                        _uiState.value = _uiState.value.copy(cachedRoutes = updatedCachedRoutes)
                    }
                    .onFailure { error ->
                        android.util.Log.e("DriverHomeViewModel", "   ❌ Failed to cache route for booking ${booking.id} - no straight line fallback", error)
                        // Don't cache anything - better to show no route than a straight line
                    }

            } catch (e: Exception) {
                android.util.Log.e("DriverHomeViewModel", "   ❌ Exception caching route for booking ${booking.id}", e)
            }
        }
    }

    /**
     * Get cached route for a booking, if available
     */
    private fun getCachedRoute(bookingId: String): RouteInfo? {
        return _uiState.value.cachedRoutes[bookingId]
    }

    /**
     * Calculate distance between two coordinates in meters using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Load already-rated bookings from SharedPreferences (driver-specific)
     */
    private fun loadRatedBookings() {
        try {
            // Load from driver-specific key formats
            val allKeys = sharedPreferences.all
            for ((key, value) in allKeys) {
                if (value == true && key.startsWith("driver_rated_")) {
                    val bookingId = key.removePrefix("driver_rated_")
                    ratedBookings.add(bookingId)
                }
            }

            Log.d("DriverHomeViewModel", "Loaded ${ratedBookings.size} already-rated bookings from SharedPreferences")
        } catch (e: Exception) {
            Log.e("DriverHomeViewModel", "Error loading rated bookings", e)
        }
    }

    /**
     * Check if driver has already rated a booking
     */
    private fun hasDriverAlreadyRated(bookingId: String): Boolean {
        return ratedBookings.contains(bookingId)
    }

    /**
     * Reset rating navigation trigger after navigating to rating screen
     */
    fun resetRatingTrigger() {
        android.util.Log.d("DriverHomeViewModel", "resetRatingTrigger called - current queued bookings: ${_uiState.value.queuedBookings.size}")
        _uiState.value = _uiState.value.copy(
            shouldNavigateToRating = false,
            completedBookingId = null,
            completedPassengerId = null
        )
        android.util.Log.d("DriverHomeViewModel", "resetRatingTrigger completed - queued bookings preserved: ${_uiState.value.queuedBookings.size}")
    }

    /**
     * Reset map tab navigation trigger after navigating to map tab
     */
    fun resetMapTabNavigationTrigger() {
        _uiState.value = _uiState.value.copy(
            shouldNavigateToMapTab = false
        )
    }

    /**
     * Advance to next booking after rating is completed
     */
    fun advanceToNextBookingAfterRating() {
        val currentQueuedBookings = _uiState.value.queuedBookings
        android.util.Log.d("DriverHomeViewModel", "advanceToNextBookingAfterRating called with ${currentQueuedBookings.size} queued bookings")

        if (currentQueuedBookings.isEmpty()) {
            android.util.Log.w("DriverHomeViewModel", "No queued bookings to advance to")
            return
        }

        val nextBooking = currentQueuedBookings.firstOrNull()
        val remainingQueue = currentQueuedBookings.drop(1)

        if (nextBooking != null) {
            android.util.Log.d("DriverHomeViewModel", "Advancing to next booking after rating: ${nextBooking.id}, remaining queue size: ${remainingQueue.size}")

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentBooking = nextBooking,
                queuedBookings = remainingQueue,
                currentActiveBookingId = nextBooking.id,
                // Reset trip-related state but KEEP driver online
                passengerLocation = null,
                showNavigationToPassenger = true,
                online = true, // Ensure driver stays online
                // Clear rating navigation state
                shouldNavigateToRating = false,
                completedBookingId = null,
                completedPassengerId = null,
                // Clear previous route to prevent showing old route
                routeInfo = null
            )

            // Set up the next booking in a separate coroutine
            viewModelScope.launch {
                // Set loading state while calculating route
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Get passenger location for the next booking
                val passengerPoint = try {
                    val liveLocation = passengerLocationService.getPassengerLocation(
                        nextBooking.id,
                        nextBooking.passengerId
                    ).getOrNull()

                    liveLocation ?: Point.fromLngLat(
                        nextBooking.pickupLocation.coordinates.longitude,
                        nextBooking.pickupLocation.coordinates.latitude
                    )
                } catch (e: Exception) {
                    Point.fromLngLat(
                        nextBooking.pickupLocation.coordinates.longitude,
                        nextBooking.pickupLocation.coordinates.latitude
                    )
                }

                _uiState.value = _uiState.value.copy(
                    passengerLocation = passengerPoint,
                    isLoading = false // Clear loading after passenger location is set
                )

                // Check for cached route first, then fetch if needed
                val cachedRoute = getCachedRoute(nextBooking.id)
                if (cachedRoute != null) {
                    // Use cached route instantly
                    _uiState.value = _uiState.value.copy(routeInfo = cachedRoute)
                    android.util.Log.d("DriverHomeViewModel", "⚡ Using cached route for next passenger: ${nextBooking.id}")
                } else {
                    // No cached route, calculate it
                    android.util.Log.d("DriverHomeViewModel", "No cached route for ${nextBooking.id}, calculating now...")

                    val currentLocation = _uiState.value.currentUserLocation
                    if (currentLocation != null) {
                        val driverLocation = BookingLocation(
                            address = "Driver Location",
                            coordinates = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
                        )

                        mapboxRepository.getRoute(driverLocation, nextBooking.pickupLocation, forceRealRoute = true)
                            .onSuccess { route ->
                                _uiState.value = _uiState.value.copy(routeInfo = route)
                                android.util.Log.d("DriverHomeViewModel", "Route calculated for next passenger: ${nextBooking.id}")
                            }
                            .onFailure { error ->
                                android.util.Log.e("DriverHomeViewModel", "Failed to calculate route for next passenger: ${nextBooking.id} - no straight line fallback", error)
                                // Keep routeInfo as null - better to show no route than a straight line
                            }
                    }
                }

                // Start observing the next booking
                observePassengerLocation(nextBooking)
                observeBookingStatus(nextBooking.id)
            }
        } else {
            // No more bookings, just clear rating state and stay online
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentBooking = null,
                currentActiveBookingId = null,
                passengerLocation = null,
                showNavigationToPassenger = false,
                online = true,
                shouldNavigateToRating = false,
                completedBookingId = null,
                completedPassengerId = null,
                queuedBookings = emptyList()
            )

            android.util.Log.d("DriverHomeViewModel", "No more queued bookings, staying online and available")
        }
    }

    /**
     * Check proximity to pickup location and trigger alerts if driver is approaching
     */
    private fun checkProximityToPickup(driverLocation: Point) {
        val currentBooking = _uiState.value.currentBooking

        android.util.Log.d("DriverHomeVM", "🔍 Checking proximity to pickup...")
        android.util.Log.d("DriverHomeVM", "   📋 Current booking: ${currentBooking?.id}")
        android.util.Log.d("DriverHomeVM", "   📊 Booking status: ${currentBooking?.status}")

        // Only check proximity for active bookings where driver is heading to pickup
        if (currentBooking != null &&
            currentBooking.status in listOf(
                BookingStatus.ACCEPTED,
                BookingStatus.DRIVER_ARRIVING
            )) {

            val pickupLocation = com.google.firebase.firestore.GeoPoint(
                currentBooking.pickupLocation.coordinates.latitude,
                currentBooking.pickupLocation.coordinates.longitude
            )

            val driverGeoPoint = com.google.firebase.firestore.GeoPoint(
                driverLocation.latitude(),
                driverLocation.longitude()
            )

            android.util.Log.i("DriverHomeVM", "✅ PROXIMITY CHECK ACTIVE - Driver heading to pickup")

            // Check proximity and trigger alerts if close to pickup
            proximityAlertUtils.checkProximityAndAlert(driverGeoPoint, pickupLocation)
        } else {
            android.util.Log.d("DriverHomeVM", "❌ No proximity check - No active booking or not heading to pickup")
        }
    }

    /**
     * Reset proximity alerts when accepting a new ride request
     */
    private fun resetProximityAlerts() {
        proximityAlertUtils.resetAlerts()
        android.util.Log.d("DriverHomeVM", "Proximity alerts reset for new booking")
    }

    /**
     * Remove a specific request from the UI state
     * Used when a request is cancelled or invalid
     */
    private fun removeRequestFromUi(request: DriverRequest) {
        val currentState = _uiState.value
        val filteredIncomingRequests = currentState.incomingRequests.filter { it.requestId != request.requestId }
        val filteredSecondChanceRequests = currentState.secondChanceRequests.filter { it.requestId != request.requestId }
        val filteredRecentDeclinedRequests = currentState.recentDeclinedRequests.filter { it.requestId != request.requestId }

        _uiState.value = currentState.copy(
            incomingRequests = filteredIncomingRequests,
            secondChanceRequests = filteredSecondChanceRequests,
            recentDeclinedRequests = filteredRecentDeclinedRequests
        )

        android.util.Log.d("DriverHomeVM", "Removed request ${request.requestId} from UI. Remaining: ${filteredIncomingRequests.size} incoming, ${filteredSecondChanceRequests.size} second chance")
    }


    /**
     * Check if a request's booking has been cancelled
     * This helps remove ride requests immediately when passenger cancels
     */
    private suspend fun isRequestBookingCancelled(bookingId: String): Boolean {
        return try {
            val result = bookingRepository.getBooking(bookingId)
            result.fold(
                onSuccess = { booking ->
                    val isCancelled = booking?.status == BookingStatus.CANCELLED
                    if (isCancelled) {
                        android.util.Log.d("DriverHomeVM", "Booking $bookingId is cancelled - removing associated requests")
                    }
                    isCancelled
                },
                onFailure = {
                    false // If we can't check, assume it's not cancelled
                }
            )
        } catch (e: Exception) {
            android.util.Log.w("DriverHomeVM", "Error checking booking status for $bookingId", e)
            false // Default to not cancelled on error
        }
    }

    /**
     * Get driver's completed trips from Firestore within date range
     */
    suspend fun getDriverCompletedTrips(dateRange: DateRange? = null): Result<List<Booking>> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            // Use simple query pattern like admin activity history (no indexes needed)
            val querySnapshot = firestore.collection("bookings")
                .whereEqualTo("driverId", currentUser.uid)
                .whereEqualTo("status", BookingStatus.COMPLETED.name)
                .limit(100) // Get more trips to filter and sort locally
                .get()
                .await()

            val allBookings = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(Booking::class.java)?.copy(id = doc.id)
            }

            // Apply client-side filtering and sorting (same pattern as admin activity history)
            val filteredAndSortedBookings = allBookings
                // First, apply date range filtering if provided
                .let { bookings ->
                    if (dateRange != null) {
                        bookings.filter { booking ->
                            val completionTime = booking.completionTime ?: 0L
                            completionTime >= dateRange.startDate && completionTime <= dateRange.endDate
                        }
                    } else {
                        bookings
                    }
                }
                // Then sort by completion time (most recent first)
                .sortedByDescending { it.completionTime ?: it.requestTime }
                // Limit results
                .take(50)

            Result.success(filteredAndSortedBookings)
        } catch (e: Exception) {
            android.util.Log.e("DriverHomeVM", "Error getting completed trips", e)
            Result.failure(e)
        }
    }

    /**
     * Select date range type
     */
    fun selectDateRangeType(type: DateRangeType) {
        val newDateRange = when (type) {
            DateRangeType.TODAY -> getTodayRange()
            DateRangeType.THIS_WEEK -> getCurrentWeekRange()
            DateRangeType.THIS_MONTH -> getCurrentMonthRange()
        }

        _uiState.value = _uiState.value.copy(
            selectedDateRangeType = type,
            selectedDateRange = newDateRange
        )
    }

    /**
     * Calculate earnings for selected date range from completed trips
     */
    fun calculateEarningsForDateRange(trips: List<Booking>): Double {
        return trips.sumOf { it.fareEstimate.totalEstimate }
    }

    fun loadBookingById(bookingId: String) {
        viewModelScope.launch {
            try {
                val result = bookingRepository.getBooking(bookingId)
                if (result.isSuccess) {
                    val booking = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        selectedTripForDetails = booking
                    )
                    // Load passenger if available
                    booking?.passengerId?.let { passengerId ->
                        loadPassengerForTripDetails(passengerId)
                    }
                }
            } catch (e: Exception) {
                Log.e("DriverHomeViewModel", "Error loading booking", e)
            }
        }
    }

    private fun loadPassengerForTripDetails(passengerId: String) {
        viewModelScope.launch {
            try {
                val passengerResult = userRepository.getUserByUid(passengerId)
                if (passengerResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        selectedTripPassenger = passengerResult.getOrNull()
                    )
                }
            } catch (e: Exception) {
                Log.e("DriverHomeViewModel", "Error loading passenger for trip details", e)
            }
        }
    }

    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    private fun observeAuthStateChanges() {
        authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                // User signed out - immediately stop all driver services
                android.util.Log.w("DriverHomeViewModel", "🚪 AUTH STATE CHANGED: User signed out - stopping ALL driver services")

                viewModelScope.launch {
                    try {
                        // Stop location updates immediately
                        stopLocationUpdates()
                        android.util.Log.d("DriverHomeViewModel", "✅ Location updates stopped on logout")

                        // Stop session monitoring
                        stopStaleDriverMonitoring()
                        android.util.Log.d("DriverHomeViewModel", "✅ Session monitoring stopped on logout")

                        // Set driver offline (just in case it wasn't done already)
                        driverRepository.updateDriverStatus(online = false).onSuccess {
                            android.util.Log.d("DriverHomeViewModel", "✅ Driver status set offline on logout")
                        }

                        // Update UI state to offline
                        _uiState.value = _uiState.value.copy(
                            online = false,
                            currentBooking = null,
                            queuedBookings = emptyList(),
                            incomingRequests = emptyList()
                        )

                        android.util.Log.d("DriverHomeViewModel", "✅ All driver services stopped successfully on logout")
                    } catch (e: Exception) {
                        android.util.Log.e("DriverHomeViewModel", "❌ Error stopping services on logout", e)
                    }
                }
            } else {
                android.util.Log.d("DriverHomeViewModel", "ℹ️ AUTH STATE: User logged in: ${firebaseAuth.currentUser?.uid}")
            }
        }

        // Add the listener
        authStateListener?.let { auth.addAuthStateListener(it) }

        android.util.Log.d("DriverHomeViewModel", "🔍 Auth state observer registered")
    }
}