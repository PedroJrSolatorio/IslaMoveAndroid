package com.rj.islamove.ui.screens.passenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point as MapboxPoint
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.utils.Point
import com.rj.islamove.data.models.*
import kotlin.math.*
import com.rj.islamove.data.models.UserRatingStats
import com.rj.islamove.data.models.SanJoseLocationsData
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.DriverMatchingRepository
import com.rj.islamove.data.repository.DriverRepository
import com.rj.islamove.data.repository.DriverLocation
import com.rj.islamove.data.repository.MapboxRepository
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.ActiveBookingRepository
import com.rj.islamove.data.repository.LandmarkRepository
import com.rj.islamove.data.repository.MapboxPlacesRepository
import com.rj.islamove.data.repository.ServiceAreaManagementRepository
import com.rj.islamove.data.repository.BoundaryFareManagementRepository
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.rj.islamove.data.repository.DriverReportRepository
import com.rj.islamove.data.repository.SanJoseLocationRepository
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.data.models.DriverReport
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.services.PassengerLocationService
import com.rj.islamove.data.services.NotificationService
import com.rj.islamove.utils.LocationUtils
import com.rj.islamove.utils.BoundaryFareUtils
import com.rj.islamove.utils.ProximityAlertUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log
import android.content.Context
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import kotlin.text.Regex

data class PassengerHomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val pickupLocation: BookingLocation? = null,
    val destination: BookingLocation? = null,
    val fareEstimate: FareEstimate? = null,
    val currentBooking: Booking? = null,
    val locationSuggestions: List<BookingLocation> = emptyList(),
    val isSearching: Boolean = false,
    val showFareEstimate: Boolean = false,
    val selectedVehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
    val currentUserLocation: Point? = null,
    val hasLocationPermissions: Boolean = false,
    val onlineDrivers: List<DriverLocation> = emptyList(),
    val showOnlineDrivers: Boolean = true,
    val onlineDriverCount: Int = 0,
    // Fields for passenger dashboard
    val savedPlaces: List<BookingLocation> = emptyList(),
    val homeAddress: BookingLocation? = null,
    val recentTrips: List<Booking> = emptyList(),
    val customLandmarks: List<CustomLandmark> = emptyList(),
    // Driver tracking for navigation
    val assignedDriverLocation: Point? = null,
    val driverRoute: RouteInfo? = null,
    val showDriverNavigation: Boolean = false,
    val driverEta: Int = 0,
    // Driver information for display
    val assignedDriver: User? = null,
    // Trip completion and rating
    val showRatingScreen: Boolean = false,
    val completedBookingId: String? = null,
    // Passenger route preview (for showing Google route lines)
    val passengerRoute: RouteInfo? = null,
    // Home address dialog
    val showSetHomeDialog: Boolean = false,
    // Service boundary dialog
    val showServiceBoundaryDialog: Boolean = false,
    val serviceBoundaryMessage: String? = null,
    // Home location selection mode
    val isSelectingHomeLocation: Boolean = false,
    // Favorite location selection mode
    val isSelectingFavoriteLocation: Boolean = false,
    // Pickup location selection mode
    val isSelectingPickupLocation: Boolean = false,
    // Category-based POI landmarks from Mapbox Search
    val restaurants: List<PlaceDetails> = emptyList(),
    val hospitals: List<PlaceDetails> = emptyList(),
    val hotels: List<PlaceDetails> = emptyList(),
    val touristAttractions: List<PlaceDetails> = emptyList(),
    val shoppingMalls: List<PlaceDetails> = emptyList(),
    val transportationHubs: List<PlaceDetails> = emptyList(),
    // Persistent ride history
    val rideHistory: List<Booking> = emptyList(),
    val hasMoreRides: Boolean = false,
    val lastRideDocumentId: String? = null,
    // Passenger rating stats
    val passengerRatingStats: UserRatingStats? = null,
    // Dialog notifications
    val showDriverCancellationDialog: Boolean = false,
    // Cancellation timer and limits (timer is visual only - every cancellation counts)
    val cancellationTimeRemaining: Int = 0, // Seconds remaining (visual indicator only)
    val remainingCancellations: Int = 3, // Number of cancellations remaining (out of 3)
    val hasExceededCancellationLimit: Boolean = false, // Whether user has used all 3 cancellations
    val cancellationResetTimeMillis: Long? = null, // When the cancellation limit will reset
    // Current user data for profile display
    val currentUser: User? = null,
    // User rating statistics for reviews section
    val userRatingStats: UserRatingStats? = null,
    // Report driver modal
    val showReportDriverModal: Boolean = false,
    // Trip details dialog
    val showTripDetailsDialog: Boolean = false,
    val selectedTripForDetails: Booking? = null,
    val selectedTripDriver: User? = null
)

@HiltViewModel
class PassengerHomeViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val driverMatchingRepository: DriverMatchingRepository,
    private val driverRepository: DriverRepository,
    private val mapboxRepository: MapboxRepository,
    private val passengerLocationService: PassengerLocationService,
    private val locationUtils: LocationUtils,
    private val userRepository: UserRepository,
    private val profileRepository: com.rj.islamove.data.repository.ProfileRepository,
    private val firebaseAuth: FirebaseAuth,
    private val activeBookingRepository: ActiveBookingRepository,
    private val landmarkRepository: LandmarkRepository,
    private val mapboxPlacesRepository: MapboxPlacesRepository,
    private val serviceAreaManagementRepository: ServiceAreaManagementRepository,
    private val boundaryFareManagementRepository: BoundaryFareManagementRepository,
    private val zoneBoundaryRepository: ZoneBoundaryRepository,
    private val ratingRepository: com.rj.islamove.data.repository.RatingRepository,
    private val sanJoseLocationRepository: SanJoseLocationRepository,
    private val notificationService: NotificationService,
    private val supportCommentRepository: SupportCommentRepository,
    private val driverReportRepository: DriverReportRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerHomeUiState())
    val uiState: StateFlow<PassengerHomeUiState> = _uiState.asStateFlow()

    private var locationUpdatesStopCallback: (() -> Unit)? = null
    private var driverObserverJob: Job? = null
    private var userDataObserverJob: Job? = null

    // Enhanced location and route management for passengers
    private val enhancedPassengerListener = com.rj.islamove.services.EnhancedPassengerLocationListener(
        coroutineScope = viewModelScope
    )
    private val optimizedRouteManager = com.rj.islamove.utils.OptimizedRouteManager(
        mapboxRepository = mapboxRepository,
        coroutineScope = viewModelScope
    )

    // Track bookings that have already shown rating screen to prevent duplicates
    private val ratedBookings = mutableSetOf<String>()
    private val sharedPreferences = context.getSharedPreferences("submitted_ratings", Context.MODE_PRIVATE)
    // Track bookings that were cancelled locally to prevent race condition UI flicker
    private val locallyCancelledBookings = mutableSetOf<String>()
    // Track if a cancellation is currently in progress to prevent double-clicking
    private var cancellationInProgress = false

    // Track which bookings are currently being monitored to prevent duplicate listeners
    private val monitoredBookings = mutableSetOf<String>()

    // Track if driver route has been calculated (to avoid recalculating on every location update)
    private var isDriverRouteCalculated = false
    private var lastCalculatedDestination: BookingLocation? = null

    // Cancellation timer management (visual indicator only - every cancellation counts toward limit)
    private var cancellationTimerJob: Job? = null
    private val CANCELLATION_WINDOW_SECONDS = 20

    // Proximity alert utility for driver approaching passenger
    private val proximityAlertUtils = ProximityAlertUtils(context)

    // Track last sent driver location for real-time updates (reduced threshold for immediate updates)
    private var lastSentDriverLocation: com.rj.islamove.data.repository.DriverLocation? = null
    private val DRIVER_LOCATION_UPDATE_THRESHOLD_METERS = 2.0 // Reduced from 20m to 2m for near real-time updates
    private var lastUpdateTimeMillis = 0L
    private var lastDriverLocationTime: Long? = null
    private val TIME_UPDATE_THRESHOLD_MS = 1500L // Update at least every 1.5 seconds for smooth real-time feel

    init {
        Log.d("PassengerHomeViewModel", "ViewModel instance created/initialized")

        // Load already-rated bookings to prevent duplicate rating screens
        loadRatedBookings()

        checkLocationPermissions()
        loadCurrentLocation()
        startLocationUpdates() // Start continuous location updates for live pickup location
        observeOnlineDrivers()
        loadSavedPlaces() // Load saved places if user is authenticated
        loadCustomLandmarks() // Load admin-created landmarks for passenger booking
        loadCategoryLandmarks() // Load category-based POI landmarks from Mapbox Search
        loadPassengerRatingStats() // Load passenger's rating statistics
        loadRideHistory() // Load ride history to show completed rides
        loadCancellationLimit() // Load user's current cancellation count

        // Debug: Check if user is authenticated before loading user data
        val currentUserId = firebaseAuth.currentUser?.uid
        Log.d("PassengerViewModel", "Init: Current user ID = $currentUserId")
        loadCurrentUserData() // Load current user data for profile display

        // Check for existing active bookings and restart monitoring
        checkAndRestoreActiveBooking()

        // Observe active bookings in real-time to detect when booking is expired/removed
        observeActiveBookingChanges()

        // Pre-load zone boundaries to ensure they're cached before booking
        preloadZoneBoundaries()
    }

    override fun onCleared() {
        Log.d("PassengerHomeViewModel", "ViewModel being cleared/destroyed")
        super.onCleared()
        stopLocationUpdates()
        driverObserverJob?.cancel()
        cancellationTimerJob?.cancel()
        userDataObserverJob?.cancel()
        proximityAlertUtils.release()
        locallyCancelledBookings.clear()
        cancellationInProgress = false

        // Enhanced cleanup for optimized managers
        enhancedPassengerListener.stopListening()
        optimizedRouteManager.stopRouteFollowing()
        driverObserverJob?.cancel()

        Log.d("PassengerHomeViewModel", "🧹 Enhanced cleanup completed in onCleared")
    }

    /**
     * Check if location permissions are granted
     */
    private fun checkLocationPermissions() {
        val hasPermissions = locationUtils.hasLocationPermissions()
        _uiState.value = _uiState.value.copy(hasLocationPermissions = hasPermissions)
    }

    /**
     * Helper function to get address from coordinates with fallback
     */
    private suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): String {
        return try {
            val geoPoint = GeoPoint(latitude, longitude)
            val addressResult = mapboxRepository.reverseGeocode(geoPoint)
            addressResult.getOrNull()?.fullAddress ?: "Lat: ${
                String.format(
                    "%.6f",
                    latitude
                )
            }, Lng: ${String.format("%.6f", longitude)}"
        } catch (e: Exception) {
            "Lat: ${String.format("%.6f", latitude)}, Lng: ${String.format("%.6f", longitude)}"
        }
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

            // If we have location and no pickup is set, set current location as pickup
            if (currentLocation != null && _uiState.value.pickupLocation == null) {
                val geoPoint = GeoPoint(currentLocation.latitude(), currentLocation.longitude())

                // Check if current location is in a boundary first
                val boundaryName = BoundaryFareUtils.determineBoundary(geoPoint, zoneBoundaryRepository)
                val address = boundaryName ?: "Lat: ${String.format("%.6f", currentLocation.latitude())}, Lng: ${String.format("%.6f", currentLocation.longitude())}"

                val currentLocationBooking = BookingLocation(
                    address = address,
                    coordinates = geoPoint
                )
                setPickupLocation(currentLocationBooking)
            }

                // IMPORTANT: Restart driver observation with location-based query when location becomes available
                if (currentLocation != null) {
                    println("DEBUG PASSENGER: Location became available, refreshing driver observations")
                    refreshDriversForLocation()
                }
            } catch (e: Exception) {
                Log.e("PassengerHomeVM", "Error loading current location", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to get current location. Please check location settings."
                )
            }
        }
    }

    /**
     * Start location updates for live tracking
     */
    fun startLocationUpdates() {
        if (!locationUtils.hasLocationPermissions()) {
            _uiState.value = _uiState.value.copy(hasLocationPermissions = false)
            return
        }

        stopLocationUpdates() // Stop any existing updates

        locationUpdatesStopCallback = locationUtils.startLocationUpdates(
            onLocationUpdate = { point ->
                _uiState.value = _uiState.value.copy(currentUserLocation = point)

                // Update pickup location with reverse geocoded address
                viewModelScope.launch {
                    var address = getAddressFromCoordinates(point.latitude(), point.longitude())

                    // If address is in lat/lng format, try to get zone boundary name instead
                    if (address.startsWith("Lat:")) {
                        val pickupBoundary = BoundaryFareUtils.determineBoundary(
                            GeoPoint(point.latitude(), point.longitude()),
                            zoneBoundaryRepository
                        )
                        if (pickupBoundary != null) {
                            address = pickupBoundary
                        }
                    }

                    val currentLocationBooking = BookingLocation(
                        address = address,
                        coordinates = GeoPoint(point.latitude(), point.longitude())
                    )
                    _uiState.value = _uiState.value.copy(pickupLocation = currentLocationBooking)
                }
            },
            onError = { exception ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Location update failed: ${exception.message}"
                )
            }
        )
    }

    /**
     * Stop location updates
     */
    fun stopLocationUpdates() {
        locationUpdatesStopCallback?.invoke()
        locationUpdatesStopCallback = null
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
     * Search for locations based on query - prioritize local San Jose locations
     */
    fun searchLocations(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(locationSuggestions = emptyList())
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)

            // First priority: Admin-configured destinations
            val adminDestinations = getAdminDestinationSuggestions(query)

            if (adminDestinations.isNotEmpty()) {
                // If we have admin destination matches, use them exclusively
                _uiState.value = _uiState.value.copy(
                    locationSuggestions = adminDestinations,
                    isSearching = false
                )
            } else {
                // Second priority: search for local San Jose locations
                val localSuggestions = getLocalLocationSuggestions(query)

                if (localSuggestions.isNotEmpty()) {
                    // If we have local matches, use them exclusively
                    _uiState.value = _uiState.value.copy(
                        locationSuggestions = localSuggestions,
                        isSearching = false
                    )
                } else {
                    // Only fall back to external search if no local matches found
                    // But constrain results to San Jose area
                    val constrainedQuery = "$query, San Jose, Dinagat Islands"

                    bookingRepository.searchLocations(constrainedQuery)
                        .onSuccess { locations ->
                            // Filter results to only include locations within Dinagat Islands
                            val validLocations = locations.filter { location ->
                                isWithinDinagatIslands(location.coordinates)
                            }

                            _uiState.value = _uiState.value.copy(
                                locationSuggestions = validLocations,
                                isSearching = false
                            )
                        }
                        .onFailure { exception ->
                            _uiState.value = _uiState.value.copy(
                                locationSuggestions = emptyList(),
                                isSearching = false,
                                errorMessage = "No locations found in San Jose, Dinagat Islands"
                            )
                        }
                }
            }
        }
    }

    /**
     * Set pickup location
     */
    fun setPickupLocation(location: BookingLocation) {
        _uiState.value = _uiState.value.copy(
            pickupLocation = location,
            locationSuggestions = emptyList()
        )
        calculateFareIfReady()
    }

    /**
     * Set destination location
     */
    fun setDestination(location: BookingLocation) {
        // Ensure we have current location as pickup when setting destination
        if (_uiState.value.pickupLocation == null && _uiState.value.currentUserLocation != null) {
            val currentLoc = _uiState.value.currentUserLocation!!
            val geoPoint = GeoPoint(currentLoc.latitude(), currentLoc.longitude())

            // Check if current location is in a boundary
            val boundaryName = BoundaryFareUtils.determineBoundary(geoPoint, zoneBoundaryRepository)
            val pickupAddress = boundaryName ?: "Lat: ${String.format("%.6f", currentLoc.latitude())}, Lng: ${String.format("%.6f", currentLoc.longitude())}"

            val currentLocationBooking = BookingLocation(
                address = pickupAddress,
                coordinates = geoPoint
            )
            setPickupLocation(currentLocationBooking)
        }

        _uiState.value = _uiState.value.copy(
            destination = location,
            locationSuggestions = emptyList()
        )
        calculateFareIfReady()
    }


    /**
     * Calculate fare estimate and route if both locations are set
     * IMPORTANT: Only calculate fare for NEW bookings, not existing ones
     */
    private fun calculateFareIfReady() {
        val state = _uiState.value

        // IMPORTANT: Don't recalculate fare if passenger already has an active booking
        if (state.currentBooking != null && state.currentBooking.status != BookingStatus.COMPLETED) {
            Log.d("PassengerHomeViewModel", "⏭️ Skipping fare calculation - passenger has active booking: ${state.currentBooking.id}")
            return
        }

        val pickupNullable = state.pickupLocation
        val destination = state.destination

        if (pickupNullable != null && destination != null) {
            viewModelScope.launch {
                try {
                    // Create local non-null variable for pickup
                    var pickup = pickupNullable

                    // Check if pickup location uses lat/lng format and can be updated with boundary name
                    if (pickup.address.startsWith("Lat:")) {
                        val pickupBoundary = BoundaryFareUtils.determineBoundary(pickup.coordinates, zoneBoundaryRepository)
                        if (pickupBoundary != null) {
                            Log.i("PassengerHomeViewModel", "📍 Updating pickup from '${pickup.address}' to '$pickupBoundary'")
                            pickup = pickup.copy(address = pickupBoundary)
                            _uiState.value = _uiState.value.copy(pickupLocation = pickup)
                        }
                    }

                    // Check for boundary-based fare first
                    Log.i("PassengerHomeViewModel", "🚖 FARE CALCULATION START")
                    Log.i("PassengerHomeViewModel", "📍 Pickup: ${pickup.address}")
                    Log.i("PassengerHomeViewModel", "🎯 Destination: ${destination.address}")

                    val boundaryFare = BoundaryFareUtils.calculateBoundaryBasedFare(
                        pickupCoordinates = pickup.coordinates,
                        destinationAddress = destination.address,
                        destinationCoordinates = destination.coordinates,
                        repository = boundaryFareManagementRepository,
                        zoneBoundaryRepository = zoneBoundaryRepository
                    )

                    // Check if destination has admin-set fare second
                    val adminFare = extractAdminFare(destination.address)

                    val fareEstimate = if (boundaryFare != null) {
                        // Use boundary-based fare
                        Log.i("PassengerHomeViewModel", "🎉 FINAL FARE: BOUNDARY-BASED - ₱$boundaryFare")
                        Log.i("PassengerHomeViewModel", "=========================================")
                        createFareEstimateFromAdminFare(boundaryFare, state.selectedVehicleCategory)
                    } else if (adminFare != null) {
                        // Create fare estimate with admin-set fare
                        Log.i("PassengerHomeViewModel", "🏛️  FINAL FARE: ADMIN-SET - ₱$adminFare")
                        Log.i("PassengerHomeViewModel", "=========================================")
                        createFareEstimateFromAdminFare(adminFare, state.selectedVehicleCategory)
                    } else {
                        // No admin fare configured - hide error message from user
                        Log.w("PassengerHomeViewModel", "❌ NO ADMIN FARE CONFIGURED")
                        Log.w("PassengerHomeViewModel", "=========================================")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = null,
                            fareEstimate = null,
                            showFareEstimate = false
                        )
                        return@launch
                    }

                    // Calculate route for distance and time display
                    val route = try {
                        mapboxRepository.getRoute(pickup, destination).getOrNull()
                    } catch (e: Exception) {
                        Log.e("PassengerHomeViewModel", "Failed to calculate route: ${e.message}")
                        null
                    }

                    // Show fare estimate with calculated route
                    _uiState.value = _uiState.value.copy(
                        fareEstimate = fareEstimate,
                        showFareEstimate = true,
                        passengerRoute = route
                    )

                } catch (e: Exception) {
                    // Handle error - could use the existing error handling pattern
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to calculate fare estimate: ${e.message}"
                    )
                }
            }
        } else {
            // Clear route when locations are incomplete
            // Clear route when locations are incomplete
            _uiState.value = _uiState.value.copy(passengerRoute = null)
        }
    }

    private fun createBookingWithCompanions(
        passengerComment: String,
        companions: List<CompanionType>,
        baseFareEstimate: FareEstimate
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = firebaseAuth.currentUser?.uid ?: return@launch
                val pickupLocation = _uiState.value.pickupLocation ?: return@launch
                val destination = _uiState.value.destination ?: return@launch

                val onlineDriversCount = _uiState.value.onlineDriverCount
                if (onlineDriversCount == 0) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passengerRoute = null,
                        errorMessage = "No drivers are currently online. Please try again later."
                    )
                    Log.w("PassengerHomeViewModel", "Booking creation blocked - no drivers online")
                    return@launch
                }

                // Calculate fares for each companion
                val companionFaresList = mutableListOf<CompanionFare>()
                val companionsList = mutableListOf<Companion>()

                companions.forEach { companionType ->
                    val discountPercentage = when (companionType) {
                        CompanionType.STUDENT, CompanionType.SENIOR -> 20
                        CompanionType.CHILD -> 50
                        else -> 0
                    }

                    val companionBaseFare = baseFareEstimate.baseFare
                    val discountAmount = companionBaseFare * (discountPercentage / 100.0)
                    val finalFare = companionBaseFare - discountAmount

                    companionFaresList.add(
                        CompanionFare(
                            companionType = companionType,
                            baseFare = companionBaseFare,
                            discountPercentage = discountPercentage,
                            discountAmount = discountAmount,
                            finalFare = finalFare
                        )
                    )

                    companionsList.add(
                        Companion(
                            type = companionType,
                            discountPercentage = discountPercentage,
                            fare = finalFare
                        )
                    )
                }

                // Calculate main passenger fare with their discount
                val passengerDiscountPercentage = _uiState.value.currentUser?.discountPercentage ?: 0
                val passengerBaseFare = baseFareEstimate.baseFare
                val passengerDiscountAmount = passengerBaseFare * (passengerDiscountPercentage / 100.0)
                val passengerFinalFare = passengerBaseFare - passengerDiscountAmount

                // Calculate total fare
                val totalFare = passengerFinalFare + companionsList.sumOf { it.fare }

                // Create fare breakdown string
                val breakdown = buildString {
                    appendLine("Main Passenger: ₱${kotlin.math.floor(passengerBaseFare).toInt()}")
                    if (passengerDiscountPercentage > 0) {
                        appendLine("  Discount ($passengerDiscountPercentage%): -₱${kotlin.math.floor(passengerDiscountAmount).toInt()}")
                        appendLine("  Subtotal: ₱${kotlin.math.floor(passengerFinalFare).toInt()}")
                    }

                    companionFaresList.forEachIndexed { index, fare ->
                        val typeName = when (fare.companionType) {
                            CompanionType.STUDENT -> "Student"
                            CompanionType.SENIOR -> "Senior"
                            CompanionType.CHILD -> "Child"
                            else -> "Companion"
                        }
                        appendLine("${typeName} ${index + 1}: ₱${kotlin.math.floor(fare.baseFare).toInt()}")
                        if (fare.discountPercentage > 0) {
                            appendLine("  Discount (${fare.discountPercentage}%): -₱${kotlin.math.floor(fare.discountAmount).toInt()}")
                            appendLine("  Subtotal: ₱${kotlin.math.floor(fare.finalFare).toInt()}")
                        }
                    }

                    appendLine("─────────────")
                    append("TOTAL: ₱${kotlin.math.floor(totalFare).toInt()}")
                }

                // Create updated fare estimate with breakdown
                val updatedFareEstimate = baseFareEstimate.copy(
                    totalEstimate = totalFare,
                    passengerFare = passengerFinalFare,
                    companionFares = companionFaresList,
                    fareBreakdown = breakdown
                )

                // Create booking
                val bookingId = UUID.randomUUID().toString()
                val booking = Booking(
                    id = bookingId,
                    passengerId = currentUserId,
                    pickupLocation = pickupLocation,
                    destination = destination,
                    fareEstimate = updatedFareEstimate,
                    status = BookingStatus.PENDING,
                    requestTime = System.currentTimeMillis(),
                    specialInstructions = passengerComment,
                    passengerDiscountPercentage = passengerDiscountPercentage,
                    companions = companionsList,
                    totalPassengers = 1 + companions.size
                )

                // Save to Firestore
                bookingRepository.createBooking(booking)

                // Update UI state
                _uiState.value = _uiState.value.copy(
                    currentBooking = booking,
                    showFareEstimate = false,
                    pickupLocation = null,
                    destination = null,
                    fareEstimate = null
                )

                // Find available drivers using matching repository
                viewModelScope.launch {
                    try {
                        driverMatchingRepository.findAndNotifyDrivers(
                            booking = booking,
                            maxDrivers = 10 // Notify up to 10 nearby drivers
                        )
                        Log.d("PassengerHomeViewModel", "Successfully notified drivers for booking ${booking.id}")
                    } catch (e: Exception) {
                        Log.e("PassengerHomeViewModel", "Error finding drivers", e)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Could not find nearby drivers. Please try again."
                        )
                    }
                }
                // Start monitoring booking status for rating screen
                monitorBookingStatus(booking.id)
                Log.d("PassengerHomeViewModel", "Started monitoring booking ${booking.id} for status changes")
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error creating booking with companions", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to create booking: ${e.message}"
                )
            }
        }
    }

    /**
     * Cancel current booking with cancellation limit enforcement
     */
    fun cancelBooking(reason: String = "Cancelled by passenger") {
        val currentBooking = _uiState.value.currentBooking ?: return

        // Prevent double-clicking while cancellation is in progress
        if (cancellationInProgress) {
            Log.d("PassengerHomeViewModel", "Cancellation already in progress, ignoring duplicate request")
            return
        }

        Log.d("PassengerHomeViewModel", "cancelBooking() called - instantly clearing UI")
        cancellationInProgress = true

        // Mark this booking as locally cancelled to prevent race condition
        locallyCancelledBookings.add(currentBooking.id)

        // Safety mechanism: Remove from locally cancelled bookings after 30 seconds to prevent permanent blocking
        viewModelScope.launch {
            delay(30000) // 30 seconds
            if (locallyCancelledBookings.remove(currentBooking.id)) {
                Log.d("PassengerViewModel", "Safety timeout: Removed booking ${currentBooking.id} from locally cancelled bookings after 30s")
            }
        }

        // Instantly clear the UI state for immediate user feedback
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentBooking = null,
            fareEstimate = null,
            showFareEstimate = false,
            cancellationTimeRemaining = 0,
            showOnlineDrivers = true  // Re-enable showing online drivers after cancellation
        )

        // Reset cancellation flag immediately so user can book again
        cancellationInProgress = false
        Log.d("PassengerViewModel", "Cancellation flag reset immediately - user can book again")

        // Reset route calculation flag for next booking
        isDriverRouteCalculated = false
        lastCalculatedDestination = null
        optimizedRouteManager.clearRoute()
        android.util.Log.d("PassengerHomeVM", "🗑️ Route calculation flag reset after cancellation")

        // Fire-and-forget background cancellation - don't block the UI
        viewModelScope.launch {
            try {
                // Only check cancellation limit if booking has been accepted by driver
                val hasExceededLimit = if (shouldCountCancellation(currentBooking.status)) {
                    checkCancellationLimit()
                } else {
                    false
                }

                if (hasExceededLimit) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "You have reached your cancellation limit (3 cancellations maximum) for accepted rides. Please contact support if needed."
                    )
                    return@launch
                }

                // Stop the cancellation timer when booking is cancelled
                stopCancellationTimer()

                // Only increment cancellation count if booking has been accepted by driver
                if (shouldCountCancellation(currentBooking.status)) {
                    incrementCancellationCount()
                }

                // Fire-and-forget Firebase cancellation in background
                bookingRepository.cancelBooking(
                    bookingId = currentBooking.id,
                    reason = reason,
                    cancelledBy = "passenger"
                ).onSuccess {
                    Log.d("PassengerViewModel", "✅ Background cancellation successful for booking: ${currentBooking.id}")
                }.onFailure { exception ->
                    Log.w("PassengerViewModel", "Background cancellation failed for booking: ${currentBooking.id}: ${exception.message}")
                    // Remove from locally cancelled bookings so Firebase listener can restore the booking if needed
                    locallyCancelledBookings.remove(currentBooking.id)
                }
            } catch (e: Exception) {
                Log.e("PassengerViewModel", "Exception during background cancellation", e)
            }
        }
    }

    /**
     * Start the 20-second visual timer after ride acceptance
     */
    private fun startCancellationTimer() {
        cancellationTimerJob?.cancel()

        cancellationTimerJob = viewModelScope.launch {
            var timeRemaining = CANCELLATION_WINDOW_SECONDS

            // Update UI to show timer
            _uiState.value = _uiState.value.copy(
                cancellationTimeRemaining = timeRemaining
            )

            while (timeRemaining > 0) {
                delay(1000) // Wait 1 second
                timeRemaining--
                _uiState.value = _uiState.value.copy(
                    cancellationTimeRemaining = timeRemaining
                )
            }

            // Timer expired - reset to 0
            _uiState.value = _uiState.value.copy(
                cancellationTimeRemaining = 0
            )
        }
    }

    /**
     * Stop the cancellation timer (called when booking is cancelled or changes status)
     */
    private fun stopCancellationTimer() {
        cancellationTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            cancellationTimeRemaining = 0
        )
    }

    /**
     * Check if passenger has exceeded cancellation limit (resets after 12 hours)
     */
    private suspend fun checkCancellationLimit(): Boolean {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return false

        return try {
            val userResult = userRepository.getUserByUid(currentUserId)
            userResult.fold(
                onSuccess = { user ->
                    val currentTimeMillis = System.currentTimeMillis()
                    val lastCancellationTimestamp = user.preferences.lastCancellationTimestamp
                    val twelveHoursInMillis = 12 * 60 * 60 * 1000L

                    // Check if 12 hours have passed since last cancellation
                    val cancellationCount = if (lastCancellationTimestamp == null ||
                        (currentTimeMillis - lastCancellationTimestamp) >= twelveHoursInMillis) {
                        // 12 hours have passed, reset the counter
                        Log.d("PassengerViewModel", "12 hours elapsed! Resetting cancellation count from ${user.preferences.cancellationCount} to 0")
                        userRepository.updateUserProfile(
                            uid = currentUserId,
                            preferences = mapOf(
                                "cancellationCount" to 0,
                                "lastCancellationTimestamp" to com.google.firebase.firestore.FieldValue.delete()
                            )
                        )
                        0
                    } else {
                        user.preferences.cancellationCount
                    }

                    val remainingCancellations = maxOf(0, 3 - cancellationCount)
                    val hasExceededLimit = cancellationCount >= 3

                    // Calculate reset time if limit is exceeded
                    val resetTimeMillis = if (hasExceededLimit && lastCancellationTimestamp != null) {
                        lastCancellationTimestamp + twelveHoursInMillis
                    } else {
                        null
                    }

                    _uiState.value = _uiState.value.copy(
                        remainingCancellations = remainingCancellations,
                        hasExceededCancellationLimit = hasExceededLimit,
                        cancellationResetTimeMillis = resetTimeMillis
                    )

                    Log.d("PassengerViewModel", "Cancellation limit check - Count: $cancellationCount, Remaining: $remainingCancellations")

                    cancellationCount >= 3
                },
                onFailure = { false }
            )
        } catch (e: Exception) {
            Log.w("PassengerViewModel", "Error checking cancellation limit", e)
            false
        }
    }

    /**
     * Increment user's cancellation count (resets after 12 hours)
     */
    private suspend fun incrementCancellationCount() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        try {
            val userResult = userRepository.getUserByUid(currentUserId)
            userResult.onSuccess { user ->
                val currentTimeMillis = System.currentTimeMillis()
                val lastCancellationTimestamp = user.preferences.lastCancellationTimestamp
                val twelveHoursInMillis = 12 * 60 * 60 * 1000L

                // Check if 12 hours have passed since last cancellation
                val newCancellationCount = if (lastCancellationTimestamp == null ||
                    (currentTimeMillis - lastCancellationTimestamp) >= twelveHoursInMillis) {
                    // 12 hours have passed, start fresh
                    1
                } else {
                    user.preferences.cancellationCount + 1
                }

                userRepository.updateUserProfile(
                    uid = currentUserId,
                    preferences = mapOf(
                        "cancellationCount" to newCancellationCount,
                        "lastCancellationTimestamp" to currentTimeMillis
                    )
                )

                // Update UI with new cancellation status
                val remainingCancellations = maxOf(0, 3 - newCancellationCount)
                val hasExceededLimit = newCancellationCount >= 3

                // Calculate reset time if limit is exceeded
                val resetTimeMillis = if (hasExceededLimit) {
                    currentTimeMillis + twelveHoursInMillis
                } else {
                    null
                }

                _uiState.value = _uiState.value.copy(
                    remainingCancellations = remainingCancellations,
                    hasExceededCancellationLimit = hasExceededLimit,
                    cancellationResetTimeMillis = resetTimeMillis
                )

                Log.d("PassengerViewModel", "Updated cancellation count to: $newCancellationCount, reset time: $resetTimeMillis")
            }
        } catch (e: Exception) {
            Log.w("PassengerViewModel", "Error incrementing cancellation count", e)
        }
    }

    /**
     * Determine if a cancellation should count toward the 3-attempt limit
     * Only counts cancellations after a driver has accepted the ride
     */
    private fun shouldCountCancellation(status: BookingStatus): Boolean {
        return when (status) {
            BookingStatus.ACCEPTED,
            BookingStatus.DRIVER_ARRIVING,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.IN_PROGRESS -> true
            else -> false
        }
    }

    /**
     * Monitor booking status changes using real Firebase listeners
     */
    private fun monitorBookingStatus(bookingId: String) {
        // Prevent duplicate monitoring of the same booking
        if (monitoredBookings.contains(bookingId)) {
            android.util.Log.d(
                "PassengerViewModel",
                "Booking $bookingId is already being monitored, skipping duplicate monitor setup"
            )
            return
        }

        monitoredBookings.add(bookingId)
        android.util.Log.d(
            "PassengerViewModel",
            "Starting to monitor booking status for bookingId: $bookingId"
        )
        viewModelScope.launch {
            bookingRepository.observeBooking(bookingId).collect { result ->
                result.onSuccess { updatedBooking ->
                    if (updatedBooking != null) {
                        android.util.Log.d(
                            "PassengerViewModel",
                            "Booking status updated: ${updatedBooking.status}"
                        )
                        val previousBooking = _uiState.value.currentBooking
                        val statusChanged = previousBooking?.status != updatedBooking.status

                        // Don't update UI if this booking was cancelled locally
                        // This prevents the cancelled booking from briefly reappearing due to race conditions
                        if (locallyCancelledBookings.contains(updatedBooking.id)) {
                            Log.d("PassengerViewModel", "Ignoring booking update for locally cancelled booking: ${updatedBooking.id}, status: ${updatedBooking.status}")
                            // Remove from tracking once we've confirmed the cancellation OR if booking gets terminal state
                            if (updatedBooking.status == BookingStatus.CANCELLED ||
                                updatedBooking.status == BookingStatus.COMPLETED ||
                                updatedBooking.status == BookingStatus.EXPIRED) {
                                locallyCancelledBookings.remove(updatedBooking.id)
                                Log.d("PassengerViewModel", "Removed locally cancelled booking ${updatedBooking.id} from tracking due to terminal status: ${updatedBooking.status}")
                            }
                            return@collect
                        }

                        // Handle EXPIRED status - clear booking and show message
                        if (updatedBooking.status == BookingStatus.EXPIRED) {
                            android.util.Log.w(
                                "PassengerViewModel",
                                "Booking expired (no drivers available) - clearing UI"
                            )
                            _uiState.value = _uiState.value.copy(
                                currentBooking = null,
                                isLoading = false,
                                showOnlineDrivers = true,  // Re-enable showing online drivers
                                errorMessage = "No drivers available. Please try again."
                            )
                            // Stop monitoring this expired booking
                            monitoredBookings.remove(updatedBooking.id)
                            return@collect
                        }

                        _uiState.value = _uiState.value.copy(
                            currentBooking = updatedBooking,
                            isLoading = false
                        )

                        // Handle passenger location tracking based on booking status changes
                        if (statusChanged) {
                            handleBookingStatusChange(updatedBooking)
                            // Start driver tracking and cancellation timer when driver is assigned
                            if (updatedBooking.status == BookingStatus.ACCEPTED && updatedBooking.driverId != null) {
                                startDriverTracking(updatedBooking.driverId!!)
                                fetchDriverInfo(updatedBooking.driverId!!)
                                startCancellationTimer()
                                // Hide online drivers to prevent duplicate driver icon
                                _uiState.value = _uiState.value.copy(showOnlineDrivers = false)
                                android.util.Log.d("PassengerHomeVM", "🚗 Hiding online drivers to prevent duplication")
                            }

                            // Ensure driver tracking continues for all active statuses (not just ACCEPTED)
                            if (updatedBooking.driverId != null && updatedBooking.status in listOf(
                                    BookingStatus.DRIVER_ARRIVING,
                                    BookingStatus.DRIVER_ARRIVED,
                                    BookingStatus.IN_PROGRESS
                                )) {
                                // Restart driver tracking to ensure it's active
                                startDriverTracking(updatedBooking.driverId!!)
                                android.util.Log.i("PassengerHomeVM", "🔄 Restarted driver tracking for status: ${updatedBooking.status}")
                            }

                            // Ensure passenger route is available when trip starts or driver arrives
                            if (updatedBooking.status == BookingStatus.IN_PROGRESS ||
                                updatedBooking.status == BookingStatus.DRIVER_ARRIVED
                            ) {
                                ensurePassengerRouteAvailable(updatedBooking)

                                // Reset route flag when status changes to IN_PROGRESS (new destination: passenger -> dropoff)
                                if (updatedBooking.status == BookingStatus.IN_PROGRESS) {
                                    isDriverRouteCalculated = false
                                    lastCalculatedDestination = null
                                    android.util.Log.d("PassengerHomeVM", "🔄 Route flag reset - Status changed to IN_PROGRESS, will calculate route to destination")
                                }
                            }

                            // Handle trip completion
                            if (updatedBooking.status == BookingStatus.COMPLETED) {
                                // Prevent showing rating screen multiple times for the same booking
                                val currentState = _uiState.value
                                val isAlreadyCompleted = currentState.completedBookingId == updatedBooking.id
                                val isRatingScreenAlreadyShown = currentState.showRatingScreen
                                val wasAlreadyRated = ratedBookings.contains(updatedBooking.id)

                                android.util.Log.d(
                                    "PassengerViewModel",
                                    "Completion check: alreadyCompleted=$isAlreadyCompleted, ratingShown=$isRatingScreenAlreadyShown, alreadyRated=$wasAlreadyRated"
                                )

                                if (!isAlreadyCompleted && !isRatingScreenAlreadyShown && !wasAlreadyRated) {

                                    // Additional global check to prevent rating screen from multiple sources
                                    // Use passenger-specific key to avoid conflicts with driver ratings
                                    val globalKey = "passenger_rating_shown_${updatedBooking.id}"
                                    val isGloballyShown = sharedPreferences.getBoolean(globalKey, false)

                                    if (isGloballyShown) {
                                        android.util.Log.d(
                                            "PassengerViewModel",
                                            "Rating screen already shown for passenger for booking: ${updatedBooking.id}, skipping"
                                        )
                                        return@collect
                                    }

                                    // Mark as shown for passenger immediately
                                    sharedPreferences.edit().putBoolean(globalKey, true).apply()

                                    android.util.Log.d(
                                        "PassengerViewModel",
                                        "Booking completed! Showing rating screen for booking: ${updatedBooking.id}"
                                    )
                                    // Small delay to ensure the booking status is fully processed
                                    kotlinx.coroutines.delay(1000)

                                    // Refresh trips list to include the completed trip
                                    loadRecentTrips()
                                    // Also refresh ride history for the rides tab
                                    loadRideHistory()

                                    _uiState.value = _uiState.value.copy(
                                        showRatingScreen = true,
                                        completedBookingId = updatedBooking.id,
                                        // Keep the booking data for rating navigation, but mark it as completed
                                        currentBooking = updatedBooking
                                    )

                                    // Reset route calculation flag for next booking
                                    isDriverRouteCalculated = false
                                    lastCalculatedDestination = null
                                    optimizedRouteManager.clearRoute()
                                    android.util.Log.d("PassengerHomeVM", "🗑️ Route calculation flag reset after ride completion")
                                } else {
                                    android.util.Log.d(
                                        "PassengerViewModel",
                                        "Not showing rating screen for booking ${updatedBooking.id} - already handled"
                                    )

                                    // Still refresh ride history and clear current booking if it's completed
                                    loadRideHistory()

                                    // Clear current booking since it's completed (if it matches)
                                    if (_uiState.value.currentBooking?.id == updatedBooking.id) {
                                        _uiState.value = _uiState.value.copy(
                                            currentBooking = null
                                        )
                                    }

                                    // If rating screen is shown but this booking completed again,
                                    // it might be a duplicate completion event - ignore it
                                    if (isRatingScreenAlreadyShown && isAlreadyCompleted) {
                                        android.util.Log.d(
                                            "PassengerViewModel",
                                            "Ignoring duplicate completion event for booking ${updatedBooking.id}"
                                        )
                                    }
                                }

                                // Additional safety: Check if this booking might have been rated by checking Firebase
                                viewModelScope.launch {
                                    try {
                                        val ratingResult = ratingRepository.getRatingByBooking(updatedBooking.id, firebaseAuth.currentUser?.uid ?: "")
                                        if (ratingResult.isSuccess && ratingResult.getOrNull() != null) {
                                            // This booking was already rated, add to ratedBookings set
                                            ratedBookings.add(updatedBooking.id)
                                            android.util.Log.d(
                                                "PassengerViewModel",
                                                "Found existing rating for booking ${updatedBooking.id}, added to ratedBookings set"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("PassengerViewModel", "Error checking rating status for booking ${updatedBooking.id}", e)
                                    }
                                }

                                // Stop monitoring this booking since it's completed
                                monitoredBookings.remove(updatedBooking.id)
                                android.util.Log.d("PassengerViewModel", "Stopped monitoring completed booking: ${updatedBooking.id}")
                            }
                            // Handle trip cancellation (by driver or system)
                            else if (updatedBooking.status == BookingStatus.CANCELLED) {
                                android.util.Log.d(
                                    "PassengerViewModel",
                                    "Booking cancelled! Restoring passenger to normal state for booking: ${updatedBooking.id}"
                                )
                                handleDriverCancellation(updatedBooking)

                                // Stop monitoring this booking since it's cancelled
                                monitoredBookings.remove(updatedBooking.id)
                                android.util.Log.d("PassengerViewModel", "Stopped monitoring cancelled booking: ${updatedBooking.id}")
                            } else {
                                android.util.Log.d(
                                    "PassengerViewModel",
                                    "Booking status updated to: ${updatedBooking.status} for booking: ${updatedBooking.id}"
                                )
                            }
                        }
                    }
                }.onFailure { exception ->
                    android.util.Log.e(
                        "PassengerViewModel",
                        "Error monitoring booking: ${exception.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Error monitoring booking: ${exception.message}",
                        isLoading = false
                    )
                }
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
     * Hide driver cancellation dialog notification
     */
    fun hideDriverCancellationDialog() {
        _uiState.value = _uiState.value.copy(
            showDriverCancellationDialog = false
        )
    }

    /**
     * Observe ALL online drivers worldwide for map display
     * Passengers can see drivers everywhere but can only book in San Jose, Dinagat Islands
     */
    private fun observeOnlineDrivers() {
        println("DEBUG PASSENGER: Starting to observe ALL online drivers worldwide")

        // Cancel any existing driver observer to prevent duplicates
        driverObserverJob?.cancel()

        driverObserverJob = viewModelScope.launch {
            println("DEBUG PASSENGER: Starting driver observation with service boundary filtering")

            // Get service boundary for filtering
            val serviceBoundaryResult = serviceAreaManagementRepository.getActiveServiceBoundary()
            val serviceBoundary = serviceBoundaryResult.getOrNull()

            // Show ALL online drivers initially, then filter by boundary
            driverRepository.observeOnlineDrivers().collect { drivers ->
                println("DEBUG PASSENGER: Received ${drivers.size} drivers from query")

                val currentBooking = _uiState.value.currentBooking
                val assignedDriverId = currentBooking?.driverId

                val filteredDrivers = if (serviceBoundary != null && serviceBoundary.boundary != null) {
                    println("DEBUG PASSENGER: Filtering drivers by service boundary '${serviceBoundary.name}'")
                    val boundaryPoints = serviceBoundary.boundary.points.map {
                        com.rj.islamove.data.models.BoundaryPointData(it.latitude, it.longitude)
                    }

                    drivers.filter { driver ->
                        val isWithin = sanJoseLocationRepository.isWithinBoundary(
                            driver.latitude,
                            driver.longitude,
                            boundaryPoints
                        )
                        val isNotAssigned = assignedDriverId == null || driver.driverId != assignedDriverId

                        if (!isWithin) {
                            println("DEBUG PASSENGER: Driver ${driver.driverId} filtered out - outside boundary (${driver.latitude}, ${driver.longitude})")
                        }
                        if (!isNotAssigned) {
                            println("DEBUG PASSENGER: Driver ${driver.driverId} filtered out - assigned to current booking (${driver.latitude}, ${driver.longitude})")
                        }

                        isWithin && isNotAssigned
                    }
                } else {
                    println("DEBUG PASSENGER: No service boundary found, using default San Jose bounds")
                    drivers.filter { driver ->
                        val isWithin = sanJoseLocationRepository.isWithinSanJose(driver.latitude, driver.longitude)
                        val isNotAssigned = assignedDriverId == null || driver.driverId != assignedDriverId

                        if (!isWithin) {
                            println("DEBUG PASSENGER: Driver ${driver.driverId} filtered out - outside San Jose (${driver.latitude}, ${driver.longitude})")
                        }
                        if (!isNotAssigned) {
                            println("DEBUG PASSENGER: Driver ${driver.driverId} filtered out - assigned to current booking (${driver.latitude}, ${driver.longitude})")
                        }

                        isWithin && isNotAssigned
                    }
                }

                println("DEBUG PASSENGER: After filtering: ${filteredDrivers.size} drivers in service area")
                filteredDrivers.forEach { driver ->
                    println("DEBUG PASSENGER: Showing driver ${driver.driverId} at ${driver.latitude}, ${driver.longitude}, Vehicle: ${driver.vehicleCategory}")
                }

                _uiState.value = _uiState.value.copy(
                    onlineDrivers = filteredDrivers,
                    onlineDriverCount = filteredDrivers.size
                )
                println("DEBUG PASSENGER: Updated UI state - onlineDriverCount: ${filteredDrivers.size}")
            }
        }
    }

    /**
     * Refresh online drivers when location changes
     */
    fun refreshDriversForLocation() {
        println("DEBUG PASSENGER: Manual refresh requested")
        observeOnlineDrivers()
    }

    /**
     * Book a ride to the specified destination with passenger comment using BookingLocation
     */
    fun bookRideToLocationWithComment(destination: BookingLocation, passengerComment: String = "", companions: List<CompanionType> = emptyList()) {
        // Check if current user is blocked/inactive using real-time data
        val currentUser = _uiState.value.currentUser
        val currentUserId = firebaseAuth.currentUser?.uid

        if (currentUserId == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please log in to book a ride."
            )
            return
        }

        if (currentUser == null || !currentUser.isActive) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Your account has been blocked. Please contact support for assistance."
            )
            return
        }

        // Continue with existing booking logic
        performBookingToLocationWithComment(destination, passengerComment, companions)
    }

    private fun performBookingToLocationWithComment(destination: BookingLocation, passengerComment: String = "", companions: List<CompanionType> = emptyList()) {
        // Check if passenger already has an active trip (not completed)
        if (_uiState.value.currentBooking != null && _uiState.value.currentBooking!!.status != BookingStatus.COMPLETED) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "You already have an active trip. Please complete or cancel your current trip before booking a new one."
            )
            return
        }

        // Additional check: prevent rebooking if rating screen was just shown for a completed trip
        if (_uiState.value.completedBookingId != null || _uiState.value.showRatingScreen) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please complete the rating for your completed trip before booking a new one."
            )
            return
        }

        viewModelScope.launch {
            // Ensure we have current location
            if (_uiState.value.currentUserLocation == null) {
                loadCurrentLocation()
                // Wait a moment for location to load
                delay(1000)
            }

            val currentLocation = _uiState.value.currentUserLocation
            if (currentLocation == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = locationUtils.getLocationErrorMessage()
                )
                return@launch
            }

            // Check if passenger is within operational boundaries
            val isWithinBoundaries = isPointWithinOperationalBoundaries(
                currentLocation.latitude(),
                currentLocation.longitude()
            )
            if (!isWithinBoundaries) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Your current location is outside our operational area. Please move within the service area to book a ride."
                )
                return@launch
            }

            // Check if destination is within operational boundaries
            val isDestinationWithinBoundaries = isPointWithinOperationalBoundaries(
                destination.coordinates.latitude,
                destination.coordinates.longitude
            )
            if (!isDestinationWithinBoundaries) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "The destination is outside our operational area. Please select a destination within the service area."
                )
                return@launch
            }

            // Set current location as pickup
            val pickupGeoPoint = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
            val boundaryName = BoundaryFareUtils.determineBoundary(pickupGeoPoint, zoneBoundaryRepository)
            val pickupLocation = BookingLocation(
                address = boundaryName ?: "Lat: ${String.format("%.6f", currentLocation.latitude())}, Lng: ${String.format("%.6f", currentLocation.longitude())}",
                coordinates = pickupGeoPoint
            )

            // FIXED: Set pickup and destination WITHOUT triggering route calculation
            // This prevents the route from flashing on screen during booking
            _uiState.value = _uiState.value.copy(
                pickupLocation = pickupLocation,
                destination = destination
            )

            // Calculate fare WITHOUT showing route (no visual flash)
            val boundaryFare = BoundaryFareUtils.calculateBoundaryBasedFare(
                pickupCoordinates = pickupGeoPoint,
                destinationAddress = destination.address,
                destinationCoordinates = destination.coordinates,
                repository = boundaryFareManagementRepository,
                zoneBoundaryRepository = zoneBoundaryRepository
            )

            val adminFare = extractAdminFare(destination.address)

            val fareEstimate = if (boundaryFare != null) {
                createFareEstimateFromAdminFare(boundaryFare, _uiState.value.selectedVehicleCategory)
            } else if (adminFare != null) {
                createFareEstimateFromAdminFare(adminFare, _uiState.value.selectedVehicleCategory)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No fare configured for this route. Please contact support."
                )
                return@launch
            }

            // Set fare estimate WITHOUT route (prevents visual flash)
            _uiState.value = _uiState.value.copy(
                fareEstimate = fareEstimate,
                showFareEstimate = true,
                passengerRoute = null // Explicitly clear route
            )

            // Create the booking with comment and companions immediately (no delay needed)
            createBookingWithCompanions(passengerComment, companions, fareEstimate)
        }
    }

    /**
     * Handle booking status changes to automatically start/stop passenger location tracking
     */
    private fun handleBookingStatusChange(booking: Booking) {
        viewModelScope.launch {
            try {
                val result = passengerLocationService.handleBookingStatusChange(
                    booking.id,
                    booking.status
                )

                // Stop cancellation timer when booking status changes away from ACCEPTED
                if (booking.status != BookingStatus.ACCEPTED) {
                    stopCancellationTimer()
                }

                result.onFailure { exception ->
                    // Log error but don't show to user as it's not critical
                    Log.e(
                        "PassengerHomeViewModel",
                        "Failed to handle location tracking for booking status: ${booking.status}",
                        exception
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "PassengerHomeViewModel",
                    "Error handling booking status change for location tracking", e
                )
            }
        }
    }

    /**
     * Handle when driver cancels the trip - restore passenger to normal state
     * while preserving greeting, user info, and maps content
     */
    private fun handleDriverCancellation(cancelledBooking: Booking) {
        viewModelScope.launch {
            try {
                android.util.Log.d(
                    "PassengerViewModel",
                    "Handling driver cancellation, restoring passenger UI"
                )

                // STEP 1: Stop any ongoing driver tracking
                driverObserverJob?.cancel()

                // STEP 2: Stop passenger location tracking for this booking
                passengerLocationService.handleBookingStatusChange(null, null)

                // Only show dialog if DRIVER cancelled (not if passenger cancelled their own ride)
                Log.i("PassengerViewModel", "🚫 Booking was cancelled - checking who cancelled")
                Log.d("PassengerViewModel", "cancelledBy: '${cancelledBooking.cancelledBy}'")

                if (cancelledBooking.cancelledBy == "driver") {
                    Log.i("PassengerViewModel", "✅ Driver cancelled - showing dialog to passenger")

                    // STEP 3: Show cancellation dialog and restore passenger to booking-ready state
                    _uiState.value = _uiState.value.copy(
                        // Clear trip-related state
                        currentBooking = null,
                        assignedDriverLocation = null,
                        driverRoute = null,
                        assignedDriver = null,
                        showDriverNavigation = false,
                        driverEta = 0,
                        isLoading = false,

                        // Clear fare estimate and route from cancelled trip
                        fareEstimate = null,
                        showFareEstimate = false,
                        passengerRoute = null,

                        // Show dialog notification instead of error message
                        showDriverCancellationDialog = true,

                        // PRESERVE essential UI elements:
                        // - currentUserLocation (for maps)
                        // - hasLocationPermissions (for location services)
                        // - pickupLocation and destination (user might want to rebook)
                        // - onlineDrivers (show available drivers)
                        // - savedPlaces (user's saved locations)
                        // - recentTrips (user's history)

                        // Clear any previous errors
                        errorMessage = null
                    )
                } else {
                    Log.i("PassengerViewModel", "❌ Passenger cancelled their own ride - not showing dialog")

                    // Just restore to booking-ready state without dialog
                    _uiState.value = _uiState.value.copy(
                        // Clear trip-related state
                        currentBooking = null,
                        assignedDriverLocation = null,
                        driverRoute = null,
                        assignedDriver = null,
                        showDriverNavigation = false,
                        driverEta = 0,
                        isLoading = false,

                        // Clear fare estimate and route from cancelled trip
                        fareEstimate = null,
                        showFareEstimate = false,
                        passengerRoute = null,

                        // PRESERVE essential UI elements but no dialog
                        errorMessage = null
                    )
                }

                // STEP 4: Restart essential services
                // Restart location updates to ensure maps work properly
                startLocationUpdates()

                // Restart driver observation to show available drivers
                observeOnlineDrivers()

                android.util.Log.d(
                    "PassengerViewModel",
                    "Passenger UI restored after driver cancellation"
                )

            } catch (e: Exception) {
                android.util.Log.e("PassengerViewModel", "Error handling driver cancellation", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error handling trip cancellation: ${e.message}",
                    isLoading = false,
                    currentBooking = null // Still clear the booking even if restoration fails
                )
            }
        }
    }

    /**
     * Apply passenger discount to fare amount
     */
    private fun applyDiscountToFare(baseFare: Double): Double {
        val discountPercentage = _uiState.value.currentUser?.discountPercentage
        return if (discountPercentage != null && discountPercentage > 0) {
            val discountMultiplier = (100 - discountPercentage) / 100.0
            baseFare * discountMultiplier
        } else {
            baseFare
        }
    }

    /**
     * Calculate fare for map point-of-interest selection (immediate calculation)
     */
    fun calculateFareForPOI(destinationLocation: BookingLocation): String {
        // Use selected pickup location or fallback to current location for regular destinations
        val pickupLocation = _uiState.value.pickupLocation ?: run {
        // For POI calculation, we need a simple current location booking
        val currentLocation = _uiState.value.currentUserLocation
        if (currentLocation != null) {
            val pickupGeoPoint = GeoPoint(currentLocation.latitude(), currentLocation.longitude())
            val boundaryName = BoundaryFareUtils.determineBoundary(pickupGeoPoint, zoneBoundaryRepository)
            BookingLocation(
                address = boundaryName ?: "Lat: ${String.format("%.6f", currentLocation.latitude())}, Lng: ${String.format("%.6f", currentLocation.longitude())}",
                coordinates = pickupGeoPoint
            )
        } else null
    }

        if (pickupLocation != null) {
            // Check for boundary-based fare first (includes boundary-to-boundary)
            val boundaryFare = BoundaryFareUtils.calculateBoundaryBasedFare(
                pickupCoordinates = pickupLocation.coordinates,
                destinationAddress = destinationLocation.address,
                destinationCoordinates = destinationLocation.coordinates,
                repository = boundaryFareManagementRepository,
                zoneBoundaryRepository = zoneBoundaryRepository
            )

            if (boundaryFare != null) {
                // Use boundary-based fare with discount applied
                val discountedFare = applyDiscountToFare(boundaryFare)
                Log.d("PassengerHomeViewModel", "Using boundary-based fare for POI destination: ₱$boundaryFare -> ₱$discountedFare (after discount)")
                return "₱${kotlin.math.floor(discountedFare).toInt()}"
            }

            // Check if destination has admin-set fare (boundary fares have priority)
            val adminFare = extractAdminFare(destinationLocation.address)

            // Only use admin fare if no boundary fare
            if (adminFare != null) {
                // Use admin-set fare for admin destinations with discount applied
                val discountedFare = applyDiscountToFare(adminFare)
                Log.d("PassengerHomeViewModel", "Using admin fare for POI destination: ₱$adminFare -> ₱$discountedFare (after discount)")
                return "₱${kotlin.math.floor(discountedFare).toInt()}"
            }

            // No admin fare configured
            Log.w("PassengerHomeViewModel", "No admin fare configured for ${destinationLocation.address}")
            return "No fare set"
        }

        return "Select pickup"
    }

    /**
     * Start tracking assigned driver's live location and calculate route
     */
    private fun fetchDriverInfo(driverId: String) {
        viewModelScope.launch {
            try {
                val result = userRepository.getUserByUid(driverId)
                result.onSuccess { driver ->
                    _uiState.value = _uiState.value.copy(
                        assignedDriver = driver
                    )
                }.onFailure { e ->
                    Log.e("PassengerViewModel", "Error fetching driver info: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("PassengerViewModel", "Error fetching driver info: ${e.message}")
            }
        }
    }

    private fun startDriverTracking(driverId: String) {
        android.util.Log.i("PassengerHomeVM", "🚀 Starting driver tracking for: $driverId")

        // Cancel any existing driver tracking
        driverObserverJob?.cancel()
        lastSentDriverLocation = null // Reset location threshold tracker
        android.util.Log.d("PassengerHomeVM", "Cancelled previous driver tracking job")

        // Use the existing DriverRepository observeDriverLocation method
        driverObserverJob = viewModelScope.launch {
            android.util.Log.i("PassengerHomeVM", "🔄 Driver location observer launched for $driverId")

            // Start a timer to periodically force refresh if location seems stale
            val forceRefreshJob = launch {
                while (isActive) {
                    delay(10000) // Check every 10 seconds
                    val lastLocationAge = System.currentTimeMillis() - (lastDriverLocationTime ?: 0L)
                    if (lastLocationAge > 15000) { // If no location updates for 15 seconds
                        android.util.Log.w("PassengerHomeVM", "⚠️ Driver location seems stale (${lastLocationAge}ms old), forcing refresh")
                        driverRepository.forceRefreshOnlineDrivers()
                    }
                }
            }

            try {
                driverRepository.observeDriverLocation(driverId).collect { driverLocation ->
                    driverLocation?.let { location ->
                        val driverPoint = Point.fromLngLat(
                            location.longitude,
                            location.latitude
                        )

                        // Enhanced debugging
                        val locationAge = System.currentTimeMillis() - location.lastUpdate
                        android.util.Log.d("PassengerHomeVM", "🚗 Driver location received: lat=${location.latitude}, lng=${location.longitude}, age=${locationAge}ms, lastUpdate=${location.lastUpdate}")

                        val driverIdMatch = location.driverId == driverId
                        android.util.Log.d("PassengerHomeVM", "🆔 Driver ID match: expected=$driverId, received=${location.driverId}, match=$driverIdMatch")

                        // Check if we should update UI (distance OR time-based for real-time updates)
                        val currentTimeMillis = System.currentTimeMillis()
                        val shouldUpdateUI = lastSentDriverLocation?.let { lastLoc ->
                            val distance = calculateDistance(
                                lastLoc.latitude,
                                lastLoc.longitude,
                                location.latitude,
                                location.longitude
                            )
                            val timeSinceLastUpdate = currentTimeMillis - lastUpdateTimeMillis

                            android.util.Log.d("PassengerHomeVM", "📏 Distance from last: ${String.format("%.2f", distance)}m, time since last: ${timeSinceLastUpdate}ms")

                            // Update if moved enough OR enough time has passed (for real-time feel)
                            val shouldUpdate = distance >= DRIVER_LOCATION_UPDATE_THRESHOLD_METERS || timeSinceLastUpdate >= TIME_UPDATE_THRESHOLD_MS
                            android.util.Log.d("PassengerHomeVM", "🔄 Should update UI: $shouldUpdate (distance threshold: ${DRIVER_LOCATION_UPDATE_THRESHOLD_METERS}m, time threshold: ${TIME_UPDATE_THRESHOLD_MS}ms)")
                            shouldUpdate
                        } ?: true // Always update on first location

                        if (shouldUpdateUI) {
                            // Calculate ETA based on actual remaining distance
                            val eta = calculateActualRemainingETA(driverPoint)

                            // Update driverEta in UI state so passengers can see the ETA
                            _uiState.value = _uiState.value.copy(driverEta = eta)

                            android.util.Log.i("PassengerHomeVM", "📍 DRIVER LOCATION UPDATED: (${location.latitude}, ${location.longitude}) - ETA: ${eta}min - UI updated!")

                            // Update driver location in route manager for deviation detection
                            optimizedRouteManager.updateDriverLocation(driverPoint)

                            // Check proximity for driver approaching passenger pickup
                            checkDriverProximityToPickup(location)

                            // Update route to driver using optimized route manager
                            updateRouteToDriver(location)

                            // Remember this location and time as last sent
                            lastSentDriverLocation = location
                            lastUpdateTimeMillis = currentTimeMillis
                            lastDriverLocationTime = System.currentTimeMillis()
                        } else {
                            android.util.Log.d("PassengerHomeVM", "📍 Driver moved but < 2m and < 1.5s, skipping UI update for performance")
                        }
                    } ?: run {
                        // Driver went offline
                        _uiState.value = _uiState.value.copy(
                            assignedDriverLocation = null,
                            showDriverNavigation = false,
                            errorMessage = "Driver went offline."
                        )
                        android.util.Log.w("PassengerHomeVM", "⚠️ Driver location became null - driver offline")
                    }
                }
                android.util.Log.w("PassengerHomeVM", "⚠️ Driver location observer completed (stream ended)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when job is cancelled - don't log as error or show error message
                android.util.Log.d("PassengerHomeVM", "✅ Driver tracking cancelled (expected when restarting tracking)")
                forceRefreshJob.cancel() // Cancel the force refresh job
                throw e // Re-throw to properly handle coroutine cancellation
            } catch (e: Exception) {
                android.util.Log.e("PassengerHomeVM", "❌ Error in driver location tracking: ${e.message}", e)
                forceRefreshJob.cancel() // Cancel the force refresh job
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Location tracking error. Please try again."
                )
            }
        }

        android.util.Log.i("PassengerHomeVM", "✅ Driver tracking observer job created and active")
    }

    /**
     * Calculate ACTUAL remaining ETA based on driver's current position and remaining distance
     * This updates dynamically as the driver moves closer
     */
    private fun calculateActualRemainingETA(driverLocation: Point): Int {
        val currentBooking = _uiState.value.currentBooking

        // Determine destination based on booking status
        val destinationCoords = when (currentBooking?.status) {
            BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING, BookingStatus.DRIVER_ARRIVED -> {
                // Driver is heading to pickup passenger
                _uiState.value.pickupLocation?.coordinates
            }
            BookingStatus.IN_PROGRESS -> {
                // Driver is heading to final destination (passenger is in vehicle)
                currentBooking.destination.coordinates
            }
            else -> _uiState.value.pickupLocation?.coordinates
        }

        return if (destinationCoords != null) {
            // Calculate straight-line distance from current driver location to destination
            val straightLineDistance = calculateDistance(
                driverLocation.latitude(),
                driverLocation.longitude(),
                destinationCoords.latitude,
                destinationCoords.longitude
            )

            // IMPORTANT: Roads are NOT straight lines! Use a more realistic multiplier
            // City roads with turns, traffic lights, etc. are typically 1.5-2x longer than straight-line
            val roadMultiplier = 1.8  // Roads are ~80% longer than straight-line
            val estimatedRoadDistance = straightLineDistance * roadMultiplier

            // Average city speed: 25-30 km/h = ~0.45 km per minute
            // So ~2.2 minutes per km (more realistic for city with traffic)
            val minutesPerKm = 2.2
            val estimatedMinutes = (estimatedRoadDistance * minutesPerKm).toInt()

            android.util.Log.d("PassengerHomeVM", "ETA calc: straightLine=${String.format("%.2f", straightLineDistance)}km, road=${String.format("%.2f", estimatedRoadDistance)}km, ETA=${estimatedMinutes}min")

            // Minimum 1 minute to avoid showing 0
            maxOf(1, estimatedMinutes)
        } else {
            5 // Default 5 minutes if no destination
        }
    }

    /**
     * Calculate distance between two points in kilometers
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLngRad = Math.toRadians(lng2 - lng1)

        val a = Math.sin(deltaLatRad / 2).pow(2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2).pow(2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Update route to driver using optimized route manager - ONLY CALCULATE ONCE per destination
     * Like Google Maps: Route is calculated once when ride starts, polyline persists as driver moves
     */
    private fun updateRouteToDriver(driverLocation: com.rj.islamove.data.repository.DriverLocation) {
        val currentBooking = _uiState.value.currentBooking

        val destination = when (currentBooking?.status) {
            BookingStatus.ACCEPTED -> _uiState.value.pickupLocation
            BookingStatus.IN_PROGRESS -> currentBooking.destination
            else -> _uiState.value.pickupLocation
        }

        destination?.let { dest ->
            // Check if we need to calculate a new route (destination changed or first time)
            val needsNewRoute = !isDriverRouteCalculated ||
                                lastCalculatedDestination == null ||
                                lastCalculatedDestination != dest

            if (needsNewRoute) {
                android.util.Log.d("PassengerHomeVM", "🗺️ Calculating driver route for NEW destination: ${dest.address}")

                val driverBookingLocation = BookingLocation(
                    address = "Driver Location",
                    coordinates = com.google.firebase.firestore.GeoPoint(
                        driverLocation.latitude,
                        driverLocation.longitude
                    )
                )

                // Calculate route ONCE (Google Maps approach)
                viewModelScope.launch {
                    optimizedRouteManager.calculateRouteOnce(driverBookingLocation, dest) { route ->
                        _uiState.value = _uiState.value.copy(
                            driverRoute = route,
                            assignedDriverLocation = com.rj.islamove.utils.Point.fromLngLat(driverLocation.longitude, driverLocation.latitude),
                            driverEta = route?.estimatedDuration ?: 0
                        )
                        android.util.Log.d("PassengerHomeVM", "✅ Driver route calculated: ${route?.totalDistance}m - Route will persist as driver moves")

                        // Mark route as calculated
                        isDriverRouteCalculated = true
                        lastCalculatedDestination = dest

                        // Start following the pre-calculated route (deviation detection only)
                        if (route != null) {
                            val driverPoint = com.rj.islamove.utils.Point.fromLngLat(
                                driverLocation.longitude,
                                driverLocation.latitude
                            )
                            optimizedRouteManager.startRouteFollowing(driverPoint) { newRoute ->
                                _uiState.value = _uiState.value.copy(
                                    driverRoute = newRoute,
                                    driverEta = newRoute?.estimatedDuration ?: _uiState.value.driverEta
                                )
                                android.util.Log.w("PassengerHomeVM", "🔄 Driver route recalculated due to major deviation")
                            }
                        }
                    }
                }
        } else {
            // Route already calculated - just update driver position, polyline stays visible
            _uiState.value = _uiState.value.copy(assignedDriverLocation = com.rj.islamove.utils.Point.fromLngLat(driverLocation.longitude, driverLocation.latitude))
            Log.d("PassengerHomeVM", "📍 Driver location updated - Route polyline persists (no API call)")
        }
        }
    }

    /**
     * Reset rating screen trigger after navigating to rating
     */
    fun resetRatingTrigger() {
        _uiState.value = _uiState.value.copy(
            showRatingScreen = false,
            completedBookingId = null,
            // Now clear currentBooking after rating navigation
            currentBooking = null,
            fareEstimate = null,
            showFareEstimate = false,
            passengerRoute = null,
            driverRoute = null,
            showDriverNavigation = false,
            assignedDriverLocation = null
        )
    }

    /**
     * Check if coordinates are within Dinagat Islands bounds
     */
    private fun isWithinDinagatIslands(coordinates: GeoPoint): Boolean {
        // Dinagat Islands approximate bounds
        val minLat = 9.8  // South bound
        val maxLat = 10.3 // North bound  
        val minLng = 125.3 // West bound
        val maxLng = 125.8 // East bound

        val isWithin = coordinates.latitude in minLat..maxLat &&
                coordinates.longitude in minLng..maxLng

        // Debug logging to help identify location issues
        if (!isWithin) {
            android.util.Log.d(
                "PassengerGeoFence",
                "Location outside bounds - Lat: ${coordinates.latitude}, Lng: ${coordinates.longitude}"
            )
            android.util.Log.d(
                "PassengerGeoFence",
                "Expected bounds: Lat[$minLat-$maxLat], Lng[$minLng-$maxLng]"
            )
            android.util.Log.d(
                "PassengerGeoFence",
                "Lat check: ${coordinates.latitude in minLat..maxLat}, Lng check: ${coordinates.longitude in minLng..maxLng}"
            )
        } else {
            android.util.Log.d(
                "PassengerGeoFence",
                "Location within bounds - Lat: ${coordinates.latitude}, Lng: ${coordinates.longitude}"
            )
        }

        return isWithin
    }

    /**
     * Ensure passenger route is available for active booking
     */
    private fun ensurePassengerRouteAvailable(booking: Booking) {
        // Only recalculate if passenger route is missing
        if (_uiState.value.passengerRoute == null) {
            viewModelScope.launch {
                try {
                    Log.d(
                        "PassengerHomeViewModel",
                        "Recalculating ACTIVE RIDE passenger route for trip in progress"
                    )
                    // Force real route for active ride passenger navigation
                    mapboxRepository.getRoute(booking.pickupLocation, booking.destination, forceRealRoute = true)
                        .onSuccess { route ->
                            _uiState.value = _uiState.value.copy(passengerRoute = route)
                            Log.d(
                                "PassengerHomeViewModel",
                                "ACTIVE RIDE: Passenger route recalculated successfully with ${route.waypoints.size} waypoints, Route ID: ${route.routeId}"
                            )
                            if (route.routeId.startsWith("simple_direct")) {
                                Log.w("PassengerHomeViewModel", "⚠️ WARNING: Passenger route is simple direct instead of real roads for active ride!")
                            } else {
                                Log.i("PassengerHomeViewModel", "✅ SUCCESS: Passenger route using real Mapbox Directions API")
                            }
                        }
                        .onFailure { exception ->
                            Log.e(
                                "PassengerHomeViewModel",
                                "Failed to recalculate passenger route for active ride",
                                exception
                            )
                        }
                } catch (e: Exception) {
                    Log.e(
                        "PassengerHomeViewModel",
                        "Error ensuring passenger route availability",
                        e
                    )
                }
            }
        }
    }

    /**
     * Get admin-configured destination suggestions based on search query
     */
    private suspend fun getAdminDestinationSuggestions(query: String): List<BookingLocation> {
        return try {
            val serviceAreasResult = serviceAreaManagementRepository.getAllServiceAreas()
            serviceAreasResult.onSuccess { serviceAreas ->
                // Get all destinations from all service areas
                val allDestinations = serviceAreas.flatMap { area ->
                    area.destinations.filter { destination ->
                        destination.isActive && destination.name.contains(query, ignoreCase = true)
                    }
                }

                // Convert ServiceDestination to BookingLocation
                return allDestinations.map { destination ->
                    BookingLocation(
                        address = destination.name,
                        coordinates = com.google.firebase.firestore.GeoPoint(
                            destination.latitude,
                            destination.longitude
                        )
                    )
                }.take(5) // Limit to 5 suggestions
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error fetching admin destinations", e)
            emptyList()
        }
    }

    /**
     * Get local location suggestions based on search query using official San Jose locations
     */
    private fun getLocalLocationSuggestions(query: String): List<BookingLocation> {
        // Filter locations based on query
        val sanJoseLocations = SanJoseLocationsData.locations.filter { location ->
            location.name.contains(query, ignoreCase = true) ||
            location.barangay.contains(query, ignoreCase = true)
        }

        // Convert SanJoseLocation to BookingLocation
        return sanJoseLocations.map { sanJoseLocation ->
            BookingLocation(
                address = "${sanJoseLocation.name}, ${sanJoseLocation.barangay}, San Jose, Dinagat Islands",
                coordinates = sanJoseLocation.coordinates
            )
        }.take(5) // Limit to 5 suggestions
    }

    /**
     * Save a place (home, work, favorite) for the current user
     */
    fun savePlace(placeType: String, location: BookingLocation) {
        val currentUser = firebaseAuth.currentUser ?: return

        // Update the location with the correct place type
        val locationWithType = location.copy(placeType = placeType)

        viewModelScope.launch {
            userRepository.saveUserPlace(currentUser.uid, placeType, locationWithType)
                .onSuccess {
                    loadSavedPlaces() // Refresh saved places
                    _uiState.value = _uiState.value.copy(
                        errorMessage = null
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to save $placeType address: ${exception.message}"
                    )
                }
        }
    }

    /**
     * Save home address for the current user
     */
    fun saveHomeAddress(location: BookingLocation) {
        savePlace("Home", location)
    }

    /**
     * Save favorite place for the current user
     */
    fun saveFavoritePlace(location: BookingLocation) {
        // Generate a unique key for this favorite location
        val favoriteKey = "Favorite_${System.currentTimeMillis()}"
        Log.d(
            "PassengerHomeViewModel",
            "Saving favorite place '${location.address}' with key '$favoriteKey'"
        )
        savePlace(favoriteKey, location)
    }

    /**
     * Load saved places for the current user
     */
    fun loadSavedPlaces() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d("PassengerHomeViewModel", "No authenticated user, cannot load saved places")
            _uiState.value = _uiState.value.copy(savedPlaces = emptyList())
            return
        }

        Log.d("PassengerHomeViewModel", "Loading saved places for user: ${currentUser.uid}")
        viewModelScope.launch {
            userRepository.getUserSavedPlaces(currentUser.uid)
                .onSuccess { savedPlacesMap ->
                    // Log the raw saved places data for debugging
                    Log.d("PassengerHomeViewModel", "Raw saved places map: $savedPlacesMap")

                    // Exclude home address from the general saved places list since it's handled separately
                    // BUT preserve the original key information by updating the placeType field
                    val savedPlacesList =
                        savedPlacesMap.filterKeys { it != "Home" }.map { (key, place) ->
                            // Normalize the place type for UI display (all Favorite_* keys become "Favorite")
                            val normalizedPlaceType = when {
                                key.startsWith("Favorite_") -> "Favorite"
                                else -> key
                            }
                            // Store the original Firebase key in the placeId field for removal
                            place.copy(
                                placeType = normalizedPlaceType,
                                placeId = key // Store original Firebase key here for removal
                            )
                        }
                    val homeAddress = savedPlacesMap["Home"]?.copy(placeType = "Home")

                    // Log place types for debugging
                    savedPlacesList.forEach { place ->
                        Log.d(
                            "PassengerHomeViewModel",
                            "Loaded place: ${place.address}, placeType: '${place.placeType}', placeName: '${place.placeName}', placeId: '${place.placeId}'"
                        )
                    }

                    Log.d(
                        "PassengerHomeViewModel",
                        "Loaded ${savedPlacesList.size} saved places (excluding home)"
                    )
                    _uiState.value = _uiState.value.copy(
                        savedPlaces = savedPlacesList,
                        homeAddress = homeAddress
                    )
                }
                .onFailure { exception ->
                    Log.e("PassengerHomeViewModel", "Failed to load saved places", exception)
                    _uiState.value = _uiState.value.copy(savedPlaces = emptyList())
                }
        }
    }

    /**
     * Remove a saved place
     */
    fun removePlace(placeType: String) {
        val currentUser = firebaseAuth.currentUser ?: return

        Log.d("PassengerHomeViewModel", "Attempting to remove place with type: '$placeType'")

        viewModelScope.launch {
            userRepository.removeUserPlace(currentUser.uid, placeType)
                .onSuccess {
                    Log.d("PassengerHomeViewModel", "Successfully removed place type: '$placeType'")
                    loadSavedPlaces() // Refresh saved places
                }
                .onFailure { exception ->
                    Log.e(
                        "PassengerHomeViewModel",
                        "Failed to remove place type: '$placeType'",
                        exception
                    )
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to remove $placeType: ${exception.message}"
                    )
                }
        }
    }

    /**
     * Remove a saved place by finding it in the saved places map (fallback method)
     */
    fun removePlaceByAddress(address: String) {
        val currentUser = firebaseAuth.currentUser ?: return

        Log.d("PassengerHomeViewModel", "Attempting to remove place by address: '$address'")

        viewModelScope.launch {
            // First get all saved places to find the correct key
            userRepository.getUserSavedPlaces(currentUser.uid)
                .onSuccess { savedPlacesMap ->
                    Log.d(
                        "PassengerHomeViewModel",
                        "Available places for removal: ${savedPlacesMap.keys}"
                    )

                    // Find ALL keys that match the address (in case of duplicates)
                    val matchingKeys = savedPlacesMap.entries.filter { (_, place) ->
                        place.address == address
                    }.map { it.key }

                    Log.d(
                        "PassengerHomeViewModel",
                        "Found ${matchingKeys.size} places matching address '$address': $matchingKeys"
                    )

                    if (matchingKeys.isNotEmpty()) {
                        // If multiple matches, prefer the most recent favorite (highest timestamp)
                        val keyToRemove = if (matchingKeys.size > 1) {
                            val favoriteKeys = matchingKeys.filter { it.startsWith("Favorite_") }
                            if (favoriteKeys.isNotEmpty()) {
                                // Get the most recent favorite (highest timestamp)
                                favoriteKeys.maxByOrNull { key ->
                                    key.removePrefix("Favorite_").toLongOrNull() ?: 0L
                                } ?: matchingKeys.first()
                            } else {
                                matchingKeys.first()
                            }
                        } else {
                            matchingKeys.first()
                        }

                        Log.d("PassengerHomeViewModel", "Selected key '$keyToRemove' for removal")
                        userRepository.removeUserPlace(currentUser.uid, keyToRemove)
                            .onSuccess {
                                Log.d(
                                    "PassengerHomeViewModel",
                                    "Successfully removed place by address"
                                )
                                loadSavedPlaces()
                            }
                            .onFailure { exception ->
                                Log.e(
                                    "PassengerHomeViewModel",
                                    "Failed to remove place by address",
                                    exception
                                )
                                _uiState.value = _uiState.value.copy(
                                    errorMessage = "Failed to remove place: ${exception.message}"
                                )
                            }
                    } else {
                        Log.w(
                            "PassengerHomeViewModel",
                            "Could not find place with address '$address'"
                        )
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Could not find place to remove"
                        )
                    }
                }
                .onFailure { exception ->
                    Log.e("PassengerHomeViewModel", "Failed to load places for removal", exception)
                }
        }
    }

    /**
     * Hide service boundary dialog
     */
    fun hideServiceBoundaryDialog() {
        _uiState.value = _uiState.value.copy(
            showServiceBoundaryDialog = false,
            serviceBoundaryMessage = null
        )
    }

    /**
     * Start home location selection mode
     */
    fun startHomeLocationSelection() {
        _uiState.value = _uiState.value.copy(
            isSelectingHomeLocation = true,
            isSelectingFavoriteLocation = false, // Cancel favorite selection if active
            isSelectingPickupLocation = false, // Cancel pickup selection if active
            showSetHomeDialog = false
        )
    }

    /**
     * Cancel home location selection mode
     */
    fun cancelHomeLocationSelection() {
        _uiState.value = _uiState.value.copy(isSelectingHomeLocation = false)
    }

    /**
     * Set home location from map selection
     */
    fun setHomeLocationFromMap(latLng: MapboxPoint) {
        viewModelScope.launch {
            // Try to get a more detailed address using reverse geocoding
            val locationResult = try {
                val geoPoint = GeoPoint(latLng.latitude(), latLng.longitude())
                mapboxRepository.reverseGeocode(geoPoint)
                    .getOrNull()
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Failed to reverse geocode location", e)
                null
            }

            val homeAddress = BookingLocation(
                address = locationResult?.fullAddress ?: "Selected Home Location",
                coordinates = GeoPoint(latLng.latitude(), latLng.longitude())
            )

            saveHomeAddress(homeAddress)
            _uiState.value = _uiState.value.copy(isSelectingHomeLocation = false)
        }
    }


    /**
     * Start favorite location selection mode
     */
    fun startFavoriteLocationSelection() {
        _uiState.value = _uiState.value.copy(
            isSelectingFavoriteLocation = true,
            isSelectingHomeLocation = false, // Cancel home selection if active
            isSelectingPickupLocation = false // Cancel pickup selection if active
        )
    }

    /**
     * Cancel favorite location selection mode
     */
    fun cancelFavoriteLocationSelection() {
        _uiState.value = _uiState.value.copy(isSelectingFavoriteLocation = false)
    }

    /**
     * Start pickup location selection mode
     */
    fun startPickupLocationSelection() {
        _uiState.value = _uiState.value.copy(
            isSelectingPickupLocation = true,
            isSelectingHomeLocation = false, // Cancel home selection if active
            isSelectingFavoriteLocation = false // Cancel favorite selection if active
        )
    }

    /**
     * Cancel pickup location selection mode
     */
    fun cancelPickupLocationSelection() {
        _uiState.value = _uiState.value.copy(isSelectingPickupLocation = false)
    }

    /**
     * Set favorite location from map selection
     */
    fun setFavoriteLocationFromMap(latLng: MapboxPoint) {
        viewModelScope.launch {
            // Try to get a more detailed address using reverse geocoding
            val locationResult = try {
                val geoPoint = GeoPoint(latLng.latitude(), latLng.longitude())
                mapboxRepository.reverseGeocode(geoPoint)
                    .getOrNull()
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Failed to reverse geocode location", e)
                null
            }

            val favoriteLocation = BookingLocation(
                address = locationResult?.fullAddress ?: "Favorite Location",
                coordinates = GeoPoint(latLng.latitude(), latLng.longitude())
            )

            // Add to saved places
            saveFavoritePlace(favoriteLocation)
            _uiState.value = _uiState.value.copy(isSelectingFavoriteLocation = false)
        }
    }

    /**
     * Set pickup location from map selection
     */
    fun setPickupLocationFromMap(latLng: MapboxPoint) {
        viewModelScope.launch {
            val geoPoint = GeoPoint(latLng.latitude(), latLng.longitude())

            // Check if pickup is in a boundary first
            val boundaryName = BoundaryFareUtils.determineBoundary(geoPoint, zoneBoundaryRepository)

            val address = if (boundaryName != null) {
                // Use boundary name as address
                boundaryName
            } else {
                // Try reverse geocoding as fallback
                try {
                    val locationResult = mapboxRepository.reverseGeocode(geoPoint).getOrNull()
                    locationResult?.fullAddress ?: "Selected Pickup Location"
                } catch (e: Exception) {
                    Log.e("PassengerHomeViewModel", "Failed to reverse geocode location", e)
                    "Selected Pickup Location"
                }
            }

            val pickupLocation = BookingLocation(
                address = address,
                coordinates = geoPoint
            )

            // Set as pickup location
            setPickupLocation(pickupLocation)
            _uiState.value = _uiState.value.copy(isSelectingPickupLocation = false)
        }
    }

    /**
     * Get the ZoneBoundaryRepository for use in boundary determination
     */
    fun getZoneBoundaryRepository(): ZoneBoundaryRepository {
        return zoneBoundaryRepository
    }

    /**
     * Pre-load zone boundaries to ensure they're cached before user books a ride
     */
    private fun preloadZoneBoundaries() {
        viewModelScope.launch {
            try {
                // This will load and cache boundaries for future use
                val dummyPoint = com.google.firebase.firestore.GeoPoint(0.0, 0.0)
                BoundaryFareUtils.determineBoundary(dummyPoint, zoneBoundaryRepository)
                Log.d("PassengerHomeViewModel", "✅ Zone boundaries pre-loaded successfully")
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Failed to pre-load zone boundaries", e)
            }
        }
    }

    /**
     * Load recent trips for the rides tab
     */
    fun loadRecentTrips() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                Log.d("PassengerHomeViewModel", "Loading recent trips for user: $userId")

                // Load active bookings first
                val activeTrips = mutableListOf<Booking>()
                try {
                    activeBookingRepository.getPassengerActiveBookings(userId)
                        .first().let { activeBookings ->
                            Log.d("PassengerHomeViewModel", "Found ${activeBookings.size} active bookings")

                            // Convert ActiveBookings to Booking objects for display - only for genuinely active bookings
                            activeBookings.mapNotNullTo(activeTrips) { activeBooking ->
                                try {
                                    // Skip completed or cancelled bookings - they shouldn't be in active trips
                                    if (activeBooking.status in listOf(
                                        com.rj.islamove.data.repository.ActiveBookingStatus.COMPLETED,
                                        com.rj.islamove.data.repository.ActiveBookingStatus.CANCELLED,
                                        com.rj.islamove.data.repository.ActiveBookingStatus.EXPIRED
                                    )) {
                                        Log.d("PassengerHomeViewModel", "Filtering out inactive booking: ${activeBooking.bookingId} with status: ${activeBooking.status}")
                                        // Remove this stale booking from active bookings
                                        viewModelScope.launch {
                                            activeBookingRepository.removeActiveBooking(activeBooking.bookingId)
                                        }
                                        return@mapNotNullTo null
                                    }

                                    // Additional validation: Check if this booking exists as completed in ride history
                                    val userId = firebaseAuth.currentUser?.uid ?: return@mapNotNullTo null
                                    val rideHistory = profileRepository.getRideHistory(userId, 50).getOrNull()
                                    val isCompletedInHistory = rideHistory?.rides?.any {
                                        it.id == activeBooking.bookingId && it.status == BookingStatus.COMPLETED
                                    } ?: false

                                    if (isCompletedInHistory) {
                                        Log.w("PassengerHomeViewModel", "Found stale active booking ${activeBooking.bookingId} in loadRecentTrips that is completed in ride history - removing it")
                                        viewModelScope.launch {
                                            activeBookingRepository.removeActiveBooking(activeBooking.bookingId)
                                        }
                                        return@mapNotNullTo null
                                    }

                                    Booking(
                                        id = activeBooking.bookingId,
                                        passengerId = activeBooking.passengerId,
                                        pickupLocation = activeBooking.pickupLocation.toBookingLocation(),
                                        destination = activeBooking.destination.toBookingLocation(),
                                        fareEstimate = activeBooking.fareEstimate.toFareEstimate(),
                                        status = when (activeBooking.status) {
                                            com.rj.islamove.data.repository.ActiveBookingStatus.SEARCHING_DRIVER -> BookingStatus.PENDING
                                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ASSIGNED -> BookingStatus.ACCEPTED
                                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVING -> BookingStatus.DRIVER_ARRIVING
                                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVED -> BookingStatus.DRIVER_ARRIVED
                                            com.rj.islamove.data.repository.ActiveBookingStatus.IN_PROGRESS -> BookingStatus.IN_PROGRESS
                                            else -> {
                                                Log.w("PassengerHomeViewModel", "Unexpected active booking status: ${activeBooking.status}, treating as PENDING")
                                                BookingStatus.PENDING
                                            }
                                        },
                                        requestTime = activeBooking.requestTime,
                                        vehicleCategory = try {
                                            VehicleCategory.valueOf(activeBooking.vehicleCategory)
                                        } catch (e: Exception) {
                                            VehicleCategory.STANDARD
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.w("PassengerHomeViewModel", "Failed to convert active booking to trip", e)
                                    null
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.e("PassengerHomeViewModel", "Failed to load active bookings", e)
                }

                // Load completed trips from Firestore
                val completedTrips = mutableListOf<Booking>()
                try {
                    val result = bookingRepository.getUserBookingHistory(50)
                    result.onSuccess { bookings ->
                        Log.d("PassengerHomeViewModel", "Found ${bookings.size} total bookings from history")

                        val filtered = bookings.filter {
                            it.status == BookingStatus.COMPLETED || it.status == BookingStatus.CANCELLED
                        }

                        Log.d("PassengerHomeViewModel", "Found ${filtered.size} completed/cancelled trips")
                        filtered.forEach { trip ->
                            Log.d("PassengerHomeViewModel", "Trip ${trip.id}: ${trip.status} - ${trip.pickupLocation.address} to ${trip.destination.address}")
                        }

                        completedTrips.addAll(filtered.sortedByDescending { it.completionTime ?: it.requestTime })
                    }.onFailure { exception ->
                        Log.e("PassengerHomeViewModel", "Failed to load completed trips", exception)
                    }
                } catch (e: Exception) {
                    Log.e("PassengerHomeViewModel", "Error loading booking history", e)
                }

                // Combine all trips
                val allTrips = activeTrips + completedTrips
                Log.d("PassengerHomeViewModel", "Total trips loaded: ${allTrips.size} (${activeTrips.size} active, ${completedTrips.size} completed)")

                // Only update current booking if we don't already have one being monitored
                val existingCurrentBooking = _uiState.value.currentBooking
                val newCurrentBooking = if (existingCurrentBooking != null &&
                    existingCurrentBooking.status !in listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
                    // Keep the existing current booking that's being monitored in real-time (only if not completed)
                    Log.d("PassengerHomeViewModel", "Keeping existing current booking ${existingCurrentBooking.id} with real-time status: ${existingCurrentBooking.status}")
                    existingCurrentBooking
                } else {
                    // No current booking or existing booking is completed, use the first truly active one from the query
                    val queryCurrentBooking = activeTrips.firstOrNull { booking ->
                        // Double-check: Only set as current booking if it's truly active (not completed)
                        booking.status !in listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)
                    }
                    Log.d("PassengerHomeViewModel", "Setting current booking from query: ${queryCurrentBooking?.id} with status: ${queryCurrentBooking?.status}")
                    queryCurrentBooking
                }

                _uiState.value = _uiState.value.copy(
                    recentTrips = allTrips,
                    currentBooking = newCurrentBooking
                )

            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error loading recent trips", e)
            }
        }
    }

    /**
     * Check for existing active bookings and restart monitoring
     * This ensures trip completion works even if ViewModel is recreated
     */
    private fun checkAndRestoreActiveBooking() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Log.w("PassengerHomeViewModel", "Cannot restore active booking - user not authenticated")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(
                    "PassengerHomeViewModel",
                    "ViewModel initialized - checking for existing active bookings for user: $userId"
                )

                // Check active bookings repository first
                activeBookingRepository.getPassengerActiveBookings(userId)
                    .first().let { activeBookings ->
                        Log.d("PassengerHomeViewModel", "Active bookings query returned ${activeBookings.size} bookings")
                        if (activeBookings.isNotEmpty()) {
                            val activeBooking = activeBookings.first()
                            Log.d(
                                "PassengerHomeViewModel",
                                "Found active booking: ${activeBooking.bookingId} with status: ${activeBooking.status}"
                            )

                            // Convert to Booking object - only for genuinely active bookings
                            try {
                                // Skip completed or cancelled bookings - they shouldn't be restored as active
                                if (activeBooking.status in listOf(
                                    com.rj.islamove.data.repository.ActiveBookingStatus.COMPLETED,
                                    com.rj.islamove.data.repository.ActiveBookingStatus.CANCELLED,
                                    com.rj.islamove.data.repository.ActiveBookingStatus.EXPIRED
                                )) {
                                    Log.d("PassengerHomeViewModel", "Skipping inactive booking: ${activeBooking.bookingId} with status: ${activeBooking.status}")
                                    // Remove this stale booking from active bookings
                                    activeBookingRepository.removeActiveBooking(activeBooking.bookingId)
                                    return@let
                                }

                                // Additional validation: Check if this booking exists as completed in ride history
                                val rideHistory = profileRepository.getRideHistory(userId, 50).getOrNull()
                                val isCompletedInHistory = rideHistory?.rides?.any {
                                    it.id == activeBooking.bookingId && it.status == BookingStatus.COMPLETED
                                } ?: false

                                if (isCompletedInHistory) {
                                    Log.w("PassengerHomeViewModel", "Found stale active booking ${activeBooking.bookingId} that is completed in ride history - removing it")
                                    activeBookingRepository.removeActiveBooking(activeBooking.bookingId)
                                    return@let
                                }

                                // Double-check by querying the main bookings collection directly
                                try {
                                    val bookingDoc = bookingRepository.getBooking(activeBooking.bookingId)
                                    bookingDoc.onSuccess { mainBooking ->
                                        if (mainBooking?.status in listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
                                            Log.w("PassengerHomeViewModel", "Found stale active booking ${activeBooking.bookingId} that is ${mainBooking?.status} in main collection - removing it")
                                            activeBookingRepository.removeActiveBooking(activeBooking.bookingId)
                                            return@let
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("PassengerHomeViewModel", "Could not verify booking status from main collection", e)
                                }

                                val booking = Booking(
                                    id = activeBooking.bookingId,
                                    passengerId = activeBooking.passengerId,
                                    pickupLocation = activeBooking.pickupLocation.toBookingLocation(),
                                    destination = activeBooking.destination.toBookingLocation(),
                                    fareEstimate = activeBooking.fareEstimate.toFareEstimate(),
                                    status = when (activeBooking.status) {
                                        com.rj.islamove.data.repository.ActiveBookingStatus.SEARCHING_DRIVER -> BookingStatus.PENDING
                                        com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ASSIGNED -> BookingStatus.ACCEPTED
                                        com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVING -> BookingStatus.DRIVER_ARRIVING
                                        com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVED -> BookingStatus.DRIVER_ARRIVED
                                        com.rj.islamove.data.repository.ActiveBookingStatus.IN_PROGRESS -> BookingStatus.IN_PROGRESS
                                        else -> {
                                            Log.w("PassengerHomeViewModel", "Unexpected active booking status: ${activeBooking.status}, treating as PENDING")
                                            BookingStatus.PENDING
                                        }
                                    },
                                    requestTime = activeBooking.requestTime,
                                    vehicleCategory = try {
                                        VehicleCategory.valueOf(activeBooking.vehicleCategory)
                                    } catch (e: Exception) {
                                        VehicleCategory.STANDARD
                                    },
                                    driverId = activeBooking.assignedDriverId
                                )

                                // Set the current booking and start monitoring
                                _uiState.value = _uiState.value.copy(currentBooking = booking)
                                Log.d(
                                    "PassengerHomeViewModel",
                                    "Successfully restored active booking: ${booking.id} with status: ${booking.status}. UI State currentBooking is now: ${_uiState.value.currentBooking?.id}. Starting monitoring..."
                                )
                                monitorBookingStatus(booking.id)

                                // Start driver tracking if driver is assigned
                                if (booking.driverId != null && booking.status in listOf(
                                        BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING,
                                        BookingStatus.DRIVER_ARRIVED, BookingStatus.IN_PROGRESS
                                    )
                                ) {
                                    startDriverTracking(booking.driverId!!)
                                    fetchDriverInfo(booking.driverId!!)
                                }

                            } catch (e: Exception) {
                                Log.e("PassengerHomeViewModel", "Error restoring active booking", e)
                            }
                        } else {
                            Log.w("PassengerHomeViewModel", "No active bookings found for user: $userId")
                        }
                    }

            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error checking for active bookings", e)
            }
        }
    }

    /**
     * Observe active booking changes in real-time
     * This ensures UI is updated when booking is expired/removed (e.g., when driver declines)
     */
    private fun observeActiveBookingChanges() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Log.w("PassengerHomeViewModel", "Cannot observe active bookings - user not authenticated")
            return
        }

        viewModelScope.launch {
            activeBookingRepository.getPassengerActiveBookings(userId).collect { activeBookings ->
                Log.d("PassengerHomeViewModel", "🔄 Active booking observer triggered - found ${activeBookings.size} active bookings")

                val currentBookingId = _uiState.value.currentBooking?.id

                if (activeBookings.isNotEmpty()) {
                    val latestActiveBooking = activeBookings.first()

                    // If we have a current booking, check if status changed
                    if (currentBookingId != null && currentBookingId == latestActiveBooking.bookingId) {
                        // Status might have changed - convert and update
                        val updatedStatus = when (latestActiveBooking.status) {
                            com.rj.islamove.data.repository.ActiveBookingStatus.SEARCHING_DRIVER -> BookingStatus.PENDING
                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ASSIGNED -> BookingStatus.ACCEPTED
                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVING -> BookingStatus.DRIVER_ARRIVING
                            com.rj.islamove.data.repository.ActiveBookingStatus.DRIVER_ARRIVED -> BookingStatus.DRIVER_ARRIVED
                            com.rj.islamove.data.repository.ActiveBookingStatus.IN_PROGRESS -> BookingStatus.IN_PROGRESS
                            else -> BookingStatus.PENDING
                        }

                        // Update current booking status if changed
                        if (_uiState.value.currentBooking?.status != updatedStatus) {
                            Log.d("PassengerHomeViewModel", "📊 Booking status changed from ${_uiState.value.currentBooking?.status} to $updatedStatus")
                            _uiState.value = _uiState.value.copy(
                                currentBooking = _uiState.value.currentBooking?.copy(
                                    status = updatedStatus,
                                    driverId = latestActiveBooking.assignedDriverId
                                )
                            )

                            // Start driver tracking if just accepted
                            if (updatedStatus == BookingStatus.ACCEPTED && latestActiveBooking.assignedDriverId != null) {
                                startDriverTracking(latestActiveBooking.assignedDriverId!!)
                                fetchDriverInfo(latestActiveBooking.assignedDriverId!!)
                            }
                        }
                    }
                } else if (currentBookingId != null) {
                    // No active bookings but we have a current booking - it was removed
                    Log.w("PassengerHomeViewModel", "❌ Current booking $currentBookingId was removed from active bookings")

                    // Check if UI is already showing rating screen - don't interfere
                    if (_uiState.value.showRatingScreen) {
                        Log.d("PassengerHomeViewModel", "⭐ Rating screen already showing - ignoring active booking removal")
                        return@collect
                    }

                    // Check if booking is already marked as completed in UI state
                    if (_uiState.value.currentBooking?.status == BookingStatus.COMPLETED) {
                        Log.d("PassengerHomeViewModel", "✅ Current booking already COMPLETED in UI - ignoring active booking removal")
                        return@collect
                    }

                    // CRITICAL FIX: Check booking status BEFORE clearing UI
                    try {
                        val bookingResult = bookingRepository.getBooking(currentBookingId)
                        val actualBooking = bookingResult.getOrNull()
                        val bookingStatus = actualBooking?.status
                        Log.d("PassengerHomeViewModel", "Booking $currentBookingId status in Firestore: $bookingStatus")

                        when (bookingStatus) {
                            BookingStatus.COMPLETED -> {
                                Log.d("PassengerHomeViewModel", "✅ Booking is COMPLETED - updating UI to COMPLETED and waiting for monitorBookingStatus")
                                // Update current booking to COMPLETED status but DON'T clear it
                                // monitorBookingStatus will handle showing the rating screen
                                _uiState.value = _uiState.value.copy(
                                    currentBooking = actualBooking,
                                    isLoading = false
                                )
                                // Don't clear - let monitorBookingStatus show rating screen
                                return@collect
                            }
                            BookingStatus.EXPIRED -> {
                                Log.d("PassengerHomeViewModel", "⏱️ Booking EXPIRED - clearing UI")
                                _uiState.value = _uiState.value.copy(
                                    currentBooking = null,
                                    isLoading = false,
                                    showOnlineDrivers = true,
                                    errorMessage = "No drivers available. Please try again."
                                )
                            }
                            BookingStatus.CANCELLED -> {
                                Log.d("PassengerHomeViewModel", "Booking CANCELLED - clearing UI")
                                _uiState.value = _uiState.value.copy(
                                    currentBooking = null,
                                    isLoading = false,
                                    showOnlineDrivers = true
                                )
                            }
                            null -> {
                                Log.d("PassengerHomeViewModel", "Booking not found in Firestore - clearing UI")
                                _uiState.value = _uiState.value.copy(
                                    currentBooking = null,
                                    isLoading = false,
                                    showOnlineDrivers = true
                                )
                            }
                            else -> {
                                Log.d("PassengerHomeViewModel", "Unknown status ($bookingStatus) - clearing UI")
                                _uiState.value = _uiState.value.copy(
                                    currentBooking = null,
                                    isLoading = false,
                                    showOnlineDrivers = true
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PassengerHomeViewModel", "Error checking booking status", e)
                        // On error, don't clear if rating screen is showing or booking is completed
                        if (!_uiState.value.showRatingScreen &&
                            _uiState.value.currentBooking?.status != BookingStatus.COMPLETED) {
                            _uiState.value = _uiState.value.copy(
                                currentBooking = null,
                                isLoading = false,
                                showOnlineDrivers = true
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Load custom landmarks created by admin for passenger booking
     * This includes both custom landmarks and service area destinations
     */
    private fun loadCustomLandmarks() {
        viewModelScope.launch {
            try {
                // Load custom landmarks from admin
                val customLandmarks = landmarkRepository.getAllLandmarks()

                // Load service area destinations and convert to CustomLandmarks
                val serviceAreaResult = serviceAreaManagementRepository.getAllServiceAreas()
                val serviceAreaLandmarks = if (serviceAreaResult.isSuccess) {
                    serviceAreaResult.getOrNull()?.flatMap { area ->
                        area.destinations.map { destination ->
                            // Include fare in the landmark name so it can be used for fare calculation
                            val landmarkName = if (destination.regularFare > 0) {
                                "${destination.name} - ₱${destination.regularFare.toInt()}"
                            } else {
                                destination.name
                            }
                            CustomLandmark(
                                id = destination.id,
                                name = landmarkName,
                                latitude = destination.latitude,
                                longitude = destination.longitude,
                                color = destination.markerColor,
                                createdAt = destination.createdAt
                            )
                        }
                    } ?: emptyList()
                } else {
                    Log.w("PassengerHomeViewModel", "Failed to load service areas: ${serviceAreaResult.exceptionOrNull()}")
                    emptyList()
                }

                // Combine both types of landmarks
                val allLandmarks = customLandmarks + serviceAreaLandmarks
                _uiState.value = _uiState.value.copy(customLandmarks = allLandmarks)
                Log.d("PassengerHomeViewModel", "Loaded ${customLandmarks.size} custom landmarks and ${serviceAreaLandmarks.size} service area destinations")
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error loading custom landmarks", e)
            }
        }
    }

    /**
     * Load category-based POI landmarks from Mapbox Search
     */
    fun loadCategoryLandmarks() {
        viewModelScope.launch {
            try {
                val center = com.mapbox.geojson.Point.fromLngLat(125.5800815, 10.0097818) // San Jose, Dinagat Islands

                // Load tourist attractions
                val attractionsResult = mapboxPlacesRepository.searchTouristAttractions(center)
                if (attractionsResult.isSuccess) {
                    val attractions = attractionsResult.getOrNull() ?: emptyList()
                    _uiState.value = _uiState.value.copy(touristAttractions = attractions)
                    Log.d("PassengerHomeViewModel", "Loaded ${attractions.size} tourist attractions")
                }

                // Search for specific categories
                loadRestaurantLandmarks(center)
                loadHospitalLandmarks(center)
                loadHotelLandmarks(center)
                loadShoppingMallLandmarks(center)
                loadTransportationHubLandmarks(center)

            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error loading category landmarks", e)
            }
        }
    }

    /**
     * Load restaurant landmarks
     */
    private suspend fun loadRestaurantLandmarks(center: com.mapbox.geojson.Point) {
        try {
            val result = mapboxPlacesRepository.searchPlaces("restaurant", center, 15)
            if (result.isSuccess) {
                val restaurants = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(restaurants = restaurants)
                Log.d("PassengerHomeViewModel", "Loaded ${restaurants.size} restaurants")
            }
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading restaurants", e)
        }
    }

    /**
     * Load hospital landmarks
     */
    private suspend fun loadHospitalLandmarks(center: com.mapbox.geojson.Point) {
        try {
            val result = mapboxPlacesRepository.searchPlaces("hospital", center, 10)
            if (result.isSuccess) {
                val hospitals = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(hospitals = hospitals)
                Log.d("PassengerHomeViewModel", "Loaded ${hospitals.size} hospitals")
            }
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading hospitals", e)
        }
    }

    /**
     * Load hotel landmarks
     */
    private suspend fun loadHotelLandmarks(center: com.mapbox.geojson.Point) {
        try {
            val result = mapboxPlacesRepository.searchPlaces("hotel", center, 10)
            if (result.isSuccess) {
                val hotels = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(hotels = hotels)
                Log.d("PassengerHomeViewModel", "Loaded ${hotels.size} hotels")
            }
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading hotels", e)
        }
    }

    /**
     * Load shopping mall landmarks
     */
    private suspend fun loadShoppingMallLandmarks(center: com.mapbox.geojson.Point) {
        try {
            val result = mapboxPlacesRepository.searchPlaces("shopping mall", center, 8)
            if (result.isSuccess) {
                val malls = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(shoppingMalls = malls)
                Log.d("PassengerHomeViewModel", "Loaded ${malls.size} shopping malls")
            }
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading shopping malls", e)
        }
    }

    /**
     * Load transportation hub landmarks
     */
    private suspend fun loadTransportationHubLandmarks(center: com.mapbox.geojson.Point) {
        try {
            val result = mapboxPlacesRepository.searchPlaces("bus terminal", center, 8)
            if (result.isSuccess) {
                val hubs = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(transportationHubs = hubs)
                Log.d("PassengerHomeViewModel", "Loaded ${hubs.size} transportation hubs")
            }
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading transportation hubs", e)
        }
    }

    /**
     * Load already-rated bookings from SharedPreferences
     */
    private fun loadRatedBookings() {
        try {
            // Load from old comma-separated format (for backward compatibility)
            val ratedBookingsString = sharedPreferences.getString("rated_bookings", "") ?: ""
            if (ratedBookingsString.isNotEmpty()) {
                val bookingIds = ratedBookingsString.split(",").filter { it.isNotBlank() }
                ratedBookings.addAll(bookingIds)
            }

            // Load from individual key formats - check both old generic keys and new passenger-specific keys
            val allKeys = sharedPreferences.all
            for ((key, value) in allKeys) {
                if (value == true) {
                    when {
                        // New passenger-specific key format
                        key.startsWith("passenger_rated_") -> {
                            val bookingId = key.removePrefix("passenger_rated_")
                            ratedBookings.add(bookingId)
                        }
                        // Old generic key format (for backward compatibility)
                        key.startsWith("rated_") && !key.startsWith("driver_rated_") -> {
                            val bookingId = key.removePrefix("rated_")
                            ratedBookings.add(bookingId)
                        }
                    }
                }
            }

            Log.d("PassengerHomeViewModel", "Loaded ${ratedBookings.size} already-rated bookings from SharedPreferences")
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error loading rated bookings", e)
        }
    }

    /**
     * Extract admin-set fare from landmark name (format: "Destination Name - ₱50")
     */
    private fun extractAdminFare(landmarkName: String): Double? {
        return try {
            val farePattern = Regex("₱(\\d+(?:\\.\\d{2})?)")
            val matchResult = farePattern.find(landmarkName)
            matchResult?.groups?.get(1)?.value?.toDouble()
        } catch (e: Exception) {
            Log.e("PassengerHomeViewModel", "Error extracting admin fare from: $landmarkName", e)
            null
        }
    }

    /**
     * Create fare estimate from admin-set fare
     */
    private suspend fun createFareEstimateFromAdminFare(adminFare: Double, vehicleCategory: VehicleCategory): FareEstimate {
        val state = _uiState.value
        val pickup = state.pickupLocation
        val destination = state.destination

        // Calculate distance and duration even for admin fares since drivers need this info
        var estimatedDistance = 0.0
        var estimatedDuration = 0

        if (pickup != null && destination != null) {
            try {
                val routeResult = mapboxRepository.getRoute(pickup, destination)
                routeResult.onSuccess { route ->
                    estimatedDistance = route.totalDistance
                    estimatedDuration = route.estimatedDuration
                    Log.d("PassengerHomeViewModel", "✅ Route SUCCESS - Admin fare with calculated distance: ${String.format("%.0f", estimatedDistance)}m, duration: ${estimatedDuration}min, routeId: ${route.routeId}")
                }.onFailure { exception ->
                    Log.w("PassengerHomeViewModel", "❌ Route FAILED - using fallback calculation", exception)
                    // Use fallback calculation
                    estimatedDistance = calculateStraightLineDistance(pickup, destination)
                    estimatedDuration = (estimatedDistance / 40000.0 * 60).toInt() // Estimate 40km/h average speed (40000m/h)
                    Log.d("PassengerHomeViewModel", "🔄 FALLBACK - Admin fare with straight-line distance: ${String.format("%.0f", estimatedDistance)}m, duration: ${estimatedDuration}min")
                }
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error calculating route for admin fare", e)
            }
        }

        // Apply passenger discount to the final fare and round down
        val discountedFare = applyDiscountToFare(adminFare)
        val roundedDownFare = kotlin.math.floor(discountedFare)
        Log.d("PassengerHomeViewModel", "Admin fare: ₱$adminFare, Discounted fare: ₱$discountedFare, Rounded down: ₱$roundedDownFare")

        return FareEstimate(
            baseFare = kotlin.math.floor(adminFare),
            distanceFare = 0.0, // No distance-based fare component for admin fares
            timeFare = 0.0,   // No time-based fare component for admin fares
            surgeFactor = 1.0,
            totalEstimate = roundedDownFare, // Use discounted fare (rounded down) so driver sees what passenger pays
            currency = "PHP",
            estimatedDuration = estimatedDuration,
            estimatedDistance = estimatedDistance
        )
    }

    private fun calculateStraightLineDistance(pickup: BookingLocation, destination: BookingLocation): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1 = Math.toRadians(pickup.coordinates.latitude)
        val lat2 = Math.toRadians(destination.coordinates.latitude)
        val dLat = Math.toRadians(destination.coordinates.latitude - pickup.coordinates.latitude)
        val dLon = Math.toRadians(destination.coordinates.longitude - pickup.coordinates.longitude)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return R * c
    }

    /**
     * Upload profile image to Cloudinary and update user profile
     */
    fun uploadProfileImage(imageUri: android.net.Uri): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser == null) {
                    android.util.Log.e("ProfileImage", "User not authenticated")
                    return@launch
                }

                android.util.Log.d("ProfileImage", "Starting Cloudinary upload for user: ${currentUser.uid}")

                // Upload to Cloudinary
                val result = profileRepository.uploadProfileImage(context, currentUser.uid, imageUri)

                if (result.isSuccess) {
                    val cloudinaryUrl = result.getOrThrow()
                    android.util.Log.d("ProfileImage", "Cloudinary upload successful: $cloudinaryUrl")

                    // Update user profile with Cloudinary URL
                    val updateResult = profileRepository.updateUserProfile(
                        uid = currentUser.uid,
                        profileImageUrl = cloudinaryUrl
                    )

                    if (updateResult.isSuccess) {
                        android.util.Log.d("ProfileImage", "Profile updated successfully with Cloudinary URL")
                        // Image upload and profile update completed successfully
                    } else {
                        android.util.Log.e("ProfileImage", "Failed to update profile: ${updateResult.exceptionOrNull()}")
                    }
                } else {
                    android.util.Log.e("ProfileImage", "Cloudinary upload failed: ${result.exceptionOrNull()}")
                }

            } catch (e: Exception) {
                android.util.Log.e("ProfileImage", "Upload process failed", e)
            }
        }
    }

    fun uploadStudentDocument(
        imageUri: android.net.Uri,
        studentIdNumber: String,
        school: String
    ): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser == null) {
                    android.util.Log.e("StudentDocument", "User not authenticated")
                    return@launch
                }

                android.util.Log.d("StudentDocument", "Starting student document upload for user: ${currentUser.uid}")

                // Upload to Cloudinary
                val result = profileRepository.uploadStudentDocument(
                    context = context,
                    uid = currentUser.uid,
                    imageUri = imageUri,
                    studentIdNumber = studentIdNumber,
                    school = school
                )

                if (result.isSuccess) {
                    val cloudinaryUrl = result.getOrThrow()
                    android.util.Log.d("StudentDocument", "Student document upload successful: $cloudinaryUrl")
                    refreshUserData()
                } else {
                    android.util.Log.e("StudentDocument", "Student document upload failed: ${result.exceptionOrNull()}")
                }

            } catch (e: Exception) {
                android.util.Log.e("StudentDocument", "Student document upload process failed", e)
            }
        }
    }

    /**
     * Get persistent ride history for the current user
     */
    fun loadRideHistory(limit: Int = 20): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser == null) {
                    android.util.Log.e("RideHistory", "User not authenticated")
                    return@launch
                }

                android.util.Log.d("RideHistory", "Loading ride history for user: ${currentUser.uid}")

                // Get ride history using ProfileRepository
                val result = profileRepository.getRideHistory(currentUser.uid, limit)

                if (result.isSuccess) {
                    val rideHistoryResult = result.getOrThrow()
                    android.util.Log.d("RideHistory", "Loaded ${rideHistoryResult.rides.size} rides")

                    // Update UI state with ride history
                    _uiState.value = _uiState.value.copy(
                        rideHistory = rideHistoryResult.rides,
                        hasMoreRides = rideHistoryResult.hasMore,
                        lastRideDocumentId = rideHistoryResult.lastDocumentId
                    )
                } else {
                    android.util.Log.e("RideHistory", "Failed to load ride history: ${result.exceptionOrNull()}")
                }

            } catch (e: Exception) {
                android.util.Log.e("RideHistory", "Failed to load ride history", e)
            }
        }
    }

    /**
     * Load passenger's rating statistics
     */
    private fun loadPassengerRatingStats() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                Log.d("PassengerHomeViewModel", "Loading passenger rating stats for user: $currentUserId")

                ratingRepository.getUserRatingStats(currentUserId).fold(
                    onSuccess = { stats ->
                        Log.d("PassengerHomeViewModel", "Passenger rating stats loaded successfully:")
                        Log.d("PassengerHomeViewModel", "  - Overall rating: ${stats.overallRating}")
                        Log.d("PassengerHomeViewModel", "  - Total ratings: ${stats.totalRatings}")

                        _uiState.value = _uiState.value.copy(passengerRatingStats = stats)
                    },
                    onFailure = { error ->
                        Log.w("PassengerHomeViewModel", "Could not load passenger rating stats for user $currentUserId: ${error.message}", error)
                        // Not critical, continue without stats
                    }
                )
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Unexpected error loading passenger rating stats", e)
            }
        }
    }

    /**
     * Load user's current cancellation count and limits (resets daily)
     */
    private fun loadCancellationLimit() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val userResult = userRepository.getUserByUid(currentUserId)
                userResult.onSuccess { user ->
                    val currentTimeMillis = System.currentTimeMillis()
                    val lastCancellationTimestamp = user.preferences.lastCancellationTimestamp
                    val twelveHoursInMillis = 12 * 60 * 60 * 1000L // 12 hours in milliseconds

                    // Check if 12 hours have passed since last cancellation
                    val cancellationCount = if (lastCancellationTimestamp == null ||
                        (currentTimeMillis - lastCancellationTimestamp) >= twelveHoursInMillis) {
                        // 12 hours have passed or no previous cancellation, reset the counter
                        if (user.preferences.cancellationCount > 0) {
                            Log.d("PassengerViewModel", "12 hours elapsed! Resetting cancellation count from ${user.preferences.cancellationCount} to 0")
                            userRepository.updateUserProfile(
                                uid = currentUserId,
                                preferences = mapOf(
                                    "cancellationCount" to 0,
                                    "lastCancellationTimestamp" to com.google.firebase.firestore.FieldValue.delete()
                                )
                            )
                        }
                        0
                    } else {
                        user.preferences.cancellationCount
                    }

                    val remainingCancellations = maxOf(0, 3 - cancellationCount)
                    val hasExceededLimit = cancellationCount >= 3

                    // Calculate reset time if limit is exceeded
                    val resetTimeMillis = if (hasExceededLimit && lastCancellationTimestamp != null) {
                        lastCancellationTimestamp + twelveHoursInMillis
                    } else {
                        null
                    }

                    _uiState.value = _uiState.value.copy(
                        remainingCancellations = remainingCancellations,
                        hasExceededCancellationLimit = hasExceededLimit,
                        cancellationResetTimeMillis = resetTimeMillis
                    )

                    Log.d("PassengerViewModel", "Loaded cancellation count: $cancellationCount, remaining: $remainingCancellations")
                }.onFailure { error ->
                    Log.w("PassengerViewModel", "Could not load cancellation count: ${error.message}", error)
                }
            } catch (e: Exception) {
                Log.e("PassengerViewModel", "Unexpected error loading cancellation limit", e)
            }
        }
    }

    /**
     * Load current user data for profile display with real-time updates
     */
    private fun loadCurrentUserData() {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            Log.w("PassengerViewModel", "No authenticated user found, cannot load user data")
            return
        }

        Log.d("PassengerViewModel", "Setting up Firebase listener for user: $currentUserId")

        // Cancel any existing user data observer
        userDataObserverJob?.cancel()

        userDataObserverJob = viewModelScope.launch {
            try {
                Log.d("PassengerViewModel", "Starting to collect user flow for: $currentUserId")
                userRepository.getUserFlow(currentUserId).collect { user ->
                    val previousUser = _uiState.value.currentUser

                    // Detailed debug logging
                    Log.d("PassengerViewModel", "Firebase listener received user data:")
                    Log.d("PassengerViewModel", "  - uid: ${user?.uid}")
                    Log.d("PassengerViewModel", "  - isActive: ${user?.isActive}")
                    Log.d("PassengerViewModel", "  - updatedAt: ${user?.updatedAt}")
                    Log.d("PassengerViewModel", "  - userType: ${user?.userType}")
                    Log.d("PassengerViewModel", "  - displayName: ${user?.displayName}")

                    _uiState.value = _uiState.value.copy(
                        currentUser = user
                    )

                    // Check if user was just blocked
                    if (previousUser?.isActive == true && user?.isActive == false) {
                        Log.w("PassengerViewModel", "User account has been blocked by admin")
                        // Show error message if user is blocked
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Your account has been blocked by an administrator. Please contact support for assistance."
                        )
                        // Cancel any active booking if user gets blocked
                        val currentBooking = _uiState.value.currentBooking
                        if (currentBooking != null && currentBooking.status != BookingStatus.COMPLETED) {
                            Log.d("PassengerViewModel", "Cancelling active booking due to account being blocked")
                            cancelBooking()
                        }
                    }

                    Log.d("PassengerViewModel", "Updated current user data: uid=${user?.uid}, active=${user?.isActive}, updatedAt=${user?.updatedAt}")
                }
            } catch (e: Exception) {
                Log.e("PassengerViewModel", "Error observing current user data", e)
            }
        }
    }

    /**
     * Manually refresh user data - useful for debugging
     */
    fun refreshUserData() {
        Log.d("PassengerViewModel", "Manually refreshing user data")
        loadCurrentUserData()
    }

    /**
     * Check proximity when driver is approaching passenger pickup location
     */
    private fun checkDriverProximityToPickup(driverLocation: DriverLocation) {
        val currentBooking = _uiState.value.currentBooking
        val pickupLocation = _uiState.value.pickupLocation

        Log.d("PassengerHomeViewModel", "🔍 Passenger checking driver proximity...")
        Log.d("PassengerHomeViewModel", "   📋 Current booking: ${currentBooking?.id}")
        Log.d("PassengerHomeViewModel", "   📊 Booking status: ${currentBooking?.status}")
        Log.d("PassengerHomeViewModel", "   📍 Pickup location: ${pickupLocation?.address}")

        // Only check proximity when driver is heading to pickup (not destination)
        if (currentBooking != null && pickupLocation != null &&
            currentBooking.status in listOf(
                BookingStatus.ACCEPTED,
                BookingStatus.DRIVER_ARRIVING
            )) {

            val driverGeoPoint = GeoPoint(
                driverLocation.latitude,
                driverLocation.longitude
            )

            val pickupGeoPoint = GeoPoint(
                pickupLocation.coordinates.latitude,
                pickupLocation.coordinates.longitude
            )

            Log.i("PassengerHomeViewModel", "✅ PASSENGER PROXIMITY CHECK ACTIVE - Driver approaching pickup")

            // Check proximity and trigger alerts if driver is close to pickup
            proximityAlertUtils.checkProximityAndAlert(driverGeoPoint, pickupGeoPoint)
        } else {
            Log.d("PassengerHomeViewModel", "❌ No passenger proximity check - No active booking or not pickup phase")
        }
    }

    /**
     * Reset proximity alerts when booking status changes or new driver is assigned
     */
    private fun resetProximityAlerts() {
        proximityAlertUtils.resetAlerts()
        Log.d("PassengerHomeViewModel", "Proximity alerts reset for passenger")
    }

    // Report driver functionality
    fun showReportDriverModal() {
        _uiState.value = _uiState.value.copy(showReportDriverModal = true)
    }

    fun hideReportDriverModal() {
        _uiState.value = _uiState.value.copy(showReportDriverModal = false)
    }

    fun submitDriverReport(reportType: ReportType, description: String) {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser

                // Check if this is called from trip details or active trip
                val selectedTripDriver = _uiState.value.selectedTripDriver
                val selectedTripForDetails = _uiState.value.selectedTripForDetails
                val assignedDriver = _uiState.value.assignedDriver
                val currentBooking = _uiState.value.currentBooking

                // Use trip details data if available, otherwise use active trip data
                val driver = selectedTripDriver ?: assignedDriver
                val booking = selectedTripForDetails ?: currentBooking

                if (currentUser != null && driver != null && booking != null) {
                    val report = DriverReport(
                        passengerId = currentUser.uid,
                        passengerName = _uiState.value.currentUser?.displayName ?: "Unknown Passenger",
                        driverId = driver.uid,
                        driverName = driver.displayName ?: "Unknown Driver",
                        rideId = booking.id,
                        reportType = reportType,
                        description = description
                    )

                    val result = driverReportRepository.submitReport(report)
                    if (result.isSuccess) {
                        Log.d("PassengerHomeViewModel", "Driver report submitted successfully")
                        _uiState.update { it.copy(
                            showReportDriverModal = false,
                            successMessage = "Report submitted successfully"
                        ) }

                        // Clear message after 3 seconds
                        delay(3000)
                        _uiState.update { it.copy(successMessage = null) }
                    } else {
                        Log.e("PassengerHomeViewModel", "Failed to submit driver report", result.exceptionOrNull())
                        _uiState.value = _uiState.value.copy(
                            showReportDriverModal = false,
                            errorMessage = "Failed to submit report: ${result.exceptionOrNull()?.message}"
                        )
                    }
                } else {
                    Log.e("PassengerHomeViewModel", "Cannot submit report: missing user, driver, or booking information")
                    _uiState.value = _uiState.value.copy(
                        showReportDriverModal = false,
                        errorMessage = "Cannot submit report: missing information"
                    )
                }
            } catch (e: Exception) {
                Log.e("PassengerHomeViewModel", "Error submitting driver report", e)
                _uiState.value = _uiState.value.copy(
                    showReportDriverModal = false,
                    errorMessage = "Error submitting report: ${e.message}"
                )
            }
        }
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
                    // Load driver if available
                    booking?.driverId?.let { driverId ->
                        loadDriverForTripDetails(driverId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerHomeViewModel", "Error loading booking", e)
            }
        }
    }

    private fun loadDriverForTripDetails(driverId: String) {
        viewModelScope.launch {
            try {
                val result = userRepository.getUserByUid(driverId)
                if (result.isSuccess) {
                    val driver = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        selectedTripDriver = driver,
                        assignedDriver = driver // Also set assignedDriver for report modal
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerHomeViewModel", "Error loading driver info: ${e.message}")
            }
        }
    }

    /**
     * Check if a point is within any operational boundary
     */
    private suspend fun isPointWithinOperationalBoundaries(latitude: Double, longitude: Double): Boolean {
        return try {
            // Get all service areas with boundaries from Firestore
            val serviceAreasResult = serviceAreaManagementRepository.getAllServiceAreas()
            if (serviceAreasResult.isFailure) {
                android.util.Log.e("PassengerHomeViewModel", "Failed to get service areas for boundary check", serviceAreasResult.exceptionOrNull())
                return true // Default to allowing if boundary check fails
            }

            val serviceAreas = serviceAreasResult.getOrNull() ?: emptyList()
            for (area in serviceAreas.filter { it.isActive }) {
                area.boundary?.let { boundary ->
                    if (boundary.points.isNotEmpty() && isPointInPolygon(
                            latitude, longitude,
                            boundary.points.map { com.mapbox.geojson.Point.fromLngLat(it.longitude, it.latitude) }
                        )) {
                        return true
                    }
                }
            }

            false // No boundaries found or point not in any boundary
        } catch (e: Exception) {
            android.util.Log.e("PassengerHomeViewModel", "Error checking boundaries", e)
            true // Default to allowing if boundary check fails
        }
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     */
    private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<com.mapbox.geojson.Point>): Boolean {
        if (polygon.size < 3) return false

        var intersections = 0
        val n = polygon.size

        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]

            // Check if the point is on an edge
            if (isPointOnLineSegment(lat, lng, p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude())) {
                return true
            }

            // Check for ray casting intersection
            if (((p1.latitude() <= lat && lat < p2.latitude()) || (p2.latitude() <= lat && lat < p1.latitude())) &&
                (lng < (p2.longitude() - p1.longitude()) * (lat - p1.latitude()) / (p2.latitude() - p1.latitude()) + p1.longitude())
            ) {
                intersections++
            }
        }

        return intersections % 2 == 1
    }

    /**
     * Check if a point is on a line segment (with small tolerance)
     */
    private fun isPointOnLineSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val tolerance = 0.000001 // Very small tolerance for floating point comparison
        val crossProduct = (py - y1) * (x2 - x1) - (px - x1) * (y2 - y1)

        if (abs(crossProduct) > tolerance) {
            return false
        }

        val dotProduct = (px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)
        val segmentLengthSquared = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)

        if (dotProduct < -tolerance || dotProduct > segmentLengthSquared + tolerance) {
            return false
        }

        return true
    }

}
