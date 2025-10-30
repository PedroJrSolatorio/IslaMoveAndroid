package com.rj.islamove.ui.screens.driver

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rj.islamove.utils.Point as UtilsPoint
import com.rj.islamove.data.models.*
import com.rj.islamove.data.repository.DriverRequest
import com.rj.islamove.ui.components.MapboxRideView
import com.mapbox.geojson.Point
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.ui.theme.IslamoveSecondary
import com.rj.islamove.utils.LocationUtils
import com.rj.islamove.utils.BoundaryFareUtils
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.*
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.rj.islamove.R
import com.rj.islamove.data.repository.RequestPhase

// Helper function to convert Utils Point to Mapbox Point
fun UtilsPoint.toMapboxPoint(): Point {
    return Point.fromLngLat(this.longitude(), this.latitude())
}

// Helper function to get boundary name or coordinates for pickup location
fun getPickupLocationBoundaryName(
    request: DriverRequest,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null
): String {
    val pickupCoordinates = GeoPoint(
        request.pickupLocation.latitude,
        request.pickupLocation.longitude
    )
    val boundaryName = BoundaryFareUtils.determineBoundary(pickupCoordinates, zoneBoundaryRepository)
    return boundaryName ?: "Lat: ${String.format("%.6f", request.pickupLocation.latitude)}, Lng: ${String.format("%.6f", request.pickupLocation.longitude)}"
}

// Helper function to get boundary name or coordinates for booking pickup location
fun getBookingPickupLocationBoundaryName(
    booking: Booking,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null
): String {
    val pickupCoordinates = GeoPoint(
        booking.pickupLocation.coordinates.latitude,
        booking.pickupLocation.coordinates.longitude
    )
    val boundaryName = BoundaryFareUtils.determineBoundary(pickupCoordinates, zoneBoundaryRepository)
    return boundaryName ?: "Lat: ${String.format("%.6f", booking.pickupLocation.coordinates.latitude)}, Lng: ${String.format("%.6f", booking.pickupLocation.coordinates.longitude)}"
}

// Helper function to get destination name for driver display
fun getDestinationBoundaryName(
    request: DriverRequest,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null
): String {
    val destCoordinates = GeoPoint(
        request.destination.latitude,
        request.destination.longitude
    )

    // Priority 1: Show the actual destination address/name if it's not just a generic address
    if (request.destination.address.isNotBlank() &&
        !request.destination.address.contains("Lat:") &&
        !request.destination.address.contains("Lng:")) {
        return request.destination.address
    }

    // Priority 2: For destinations without proper addresses, show the boundary name
    val boundaryName = BoundaryFareUtils.determineBoundary(destCoordinates, zoneBoundaryRepository)
    if (boundaryName != null) {
        return boundaryName
    }

    // Priority 3: Show coordinates as last resort
    return "Lat: ${String.format("%.6f", request.destination.latitude)}, Lng: ${String.format("%.6f", request.destination.longitude)}"
}

// Helper function to get destination name for booking display
fun getBookingDestinationBoundaryName(
    booking: Booking,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null
): String {
    val destCoordinates = booking.destination.coordinates

    // Priority 1: Show the actual destination address/name if it's not just a generic address
    if (booking.destination.address.isNotBlank() &&
        !booking.destination.address.contains("Lat:") &&
        !booking.destination.address.contains("Lng:")) {
        return booking.destination.address
    }

    // Priority 2: For destinations without proper addresses, show the boundary name
    val boundaryName = BoundaryFareUtils.determineBoundary(destCoordinates, zoneBoundaryRepository)
    if (boundaryName != null) {
        return boundaryName
    }

    // Priority 3: Show coordinates as last resort
    return "Lat: ${String.format("%.6f", booking.destination.coordinates.latitude)}, Lng: ${String.format("%.6f", booking.destination.coordinates.longitude)}"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToEarnings: (String) -> Unit,
    onNavigateToMissedRequests: () -> Unit,
    onNavigateToRating: (String, String) -> Unit = { _, _ -> },
    onNavigateToTripDetails: (String) -> Unit = { },
    viewModel: DriverHomeViewModel = hiltViewModel()
) {
    // Get ZoneBoundaryRepository from ViewModel's service area repository
    val zoneBoundaryRepository = viewModel.getZoneBoundaryRepository()

    // Local helper functions with repository access
    fun getRequestPickupBoundary(request: DriverRequest) = getPickupLocationBoundaryName(request, zoneBoundaryRepository)
    fun getPickupBoundary(booking: Booking) = getBookingPickupLocationBoundaryName(booking, zoneBoundaryRepository)
    fun getDestinationBoundary(booking: Booking) = getBookingDestinationBoundaryName(booking, zoneBoundaryRepository)
    fun getRequestDestinationBoundary(request: DriverRequest) = getDestinationBoundaryName(request, zoneBoundaryRepository)

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Map", "Rides")
    val uiState by viewModel.uiState.collectAsState()

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.onLocationPermissionsResult(granted)
    }

    // Request location permissions if not granted
    LaunchedEffect(Unit) {
        if (!uiState.hasLocationPermissions) {
            locationPermissionLauncher.launch(LocationUtils.LOCATION_PERMISSIONS)
        }
    }

    // Handle rating navigation trigger
    LaunchedEffect(uiState.shouldNavigateToRating) {
        if (uiState.shouldNavigateToRating) {
            val bookingId = uiState.completedBookingId

            if (!bookingId.isNullOrBlank()) {
                // Navigate to rating regardless of passengerId - let DriverRatingViewModel handle passenger lookup
                val passengerId = if (uiState.completedPassengerId.isNullOrBlank()) "LOOKUP_REQUIRED" else uiState.completedPassengerId!!
                android.util.Log.d("DriverHomeScreen", "Navigating to rating: bookingId=$bookingId, passengerId=$passengerId")
                onNavigateToRating(bookingId, passengerId)
                viewModel.resetRatingTrigger()
            } else {
                android.util.Log.w("DriverHomeScreen", "Cannot navigate to rating: missing bookingId")
                viewModel.resetRatingTrigger()
            }
        }
    }

    // Handle navigation to Map tab after accepting ride
    LaunchedEffect(uiState.shouldNavigateToMapTab) {
        if (uiState.shouldNavigateToMapTab) {
            android.util.Log.d("DriverHomeScreen", "Navigating to Map tab after accepting ride")
            selectedTab = 1 // Map tab is at index 1
            viewModel.resetMapTabNavigationTrigger()
        }
    }

    // Handle returning from rating screen with queued bookings
    LaunchedEffect(uiState.shouldNavigateToRating, uiState.currentBooking, uiState.queuedBookings) {
        android.util.Log.d("DriverHomeScreen", "Queue state check - shouldNavigateToRating: ${uiState.shouldNavigateToRating}, currentBooking: ${uiState.currentBooking?.id}, queuedBookings: ${uiState.queuedBookings.size}")

        // Check if driver has just returned from rating screen (shouldNavigateToRating just became false)
        if (!uiState.shouldNavigateToRating && uiState.currentBooking == null && uiState.queuedBookings.isNotEmpty()) {
            android.util.Log.d("DriverHomeScreen", "Driver returned from rating with ${uiState.queuedBookings.size} queued bookings, advancing to next")
            // Add a small delay to ensure UI state is stable before advancing
            kotlinx.coroutines.delay(100)
            viewModel.advanceToNextBookingAfterRating()
            // Navigate back to Map tab (60/40 view) since there are still passengers
            selectedTab = 1
        }
    }

    // Ensure Map tab is active when there are active passengers (current or queued)
    LaunchedEffect(uiState.currentBooking, uiState.queuedBookings.size) {
        val hasActivePassengers = uiState.currentBooking != null || uiState.queuedBookings.isNotEmpty()
        if (hasActivePassengers && selectedTab != 1) {
            android.util.Log.d("DriverHomeScreen", "Auto-switching to Map tab: currentBooking=${uiState.currentBooking?.id}, queued=${uiState.queuedBookings.size}")
            selectedTab = 1
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Clear error after showing it
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

  
    // Debug logging for currentBooking state
    LaunchedEffect(uiState.currentBooking?.id, selectedTab) {
        android.util.Log.d("DriverHomeScreen", "UI State Update - selectedTab: $selectedTab, currentBooking: ${uiState.currentBooking?.id}, online: ${uiState.online}")
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header white space

        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Islamove",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            actions = {
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Default.Person, contentDescription = "Profile")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Tab content based on selected tab
        Box(modifier = Modifier.weight(1f)) {
            android.util.Log.d("DriverHomeScreen", "Main screen: selectedTab = $selectedTab, currentBooking = ${uiState.currentBooking?.id ?: "null"}")
            when (selectedTab) {
                0 -> {
                    android.util.Log.d("DriverHomeScreen", "Showing DashboardContent")
                    DashboardContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToEarnings = onNavigateToEarnings,
                        onNavigateToMissedRequests = onNavigateToMissedRequests,
                        zoneBoundaryRepository = zoneBoundaryRepository
                    )
                }
                1 -> {
                    android.util.Log.d("DriverHomeScreen", "Showing MapContent")
                    MapContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        zoneBoundaryRepository = zoneBoundaryRepository
                    )
                }
                2 -> {
                    android.util.Log.d("DriverHomeScreen", "Showing RidesContent")
                    RidesContent(
                        uiState = uiState,
                        onNavigateToEarnings = onNavigateToEarnings,
                        onNavigateToTripDetails = onNavigateToTripDetails,
                        viewModel = viewModel,
                        zoneBoundaryRepository = zoneBoundaryRepository
                    )
                }
            }
        }

        // Bottom Tab Navigation
        NavigationBar(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            when (index) {
                                0 -> Icons.Default.Home
                                1 -> Icons.Default.LocationOn
                                2 -> Icons.Default.List
                                else -> Icons.Default.Home
                            },
                            contentDescription = tab
                        )
                    },
                    label = { Text(tab) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IslamovePrimary,
                        selectedTextColor = IslamovePrimary
                    )
                )
            }
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

    }

    // Passenger cancellation dialog
    if (uiState.showPassengerCancellationDialog) {
        com.rj.islamove.ui.components.PassengerCancellationNotificationDialog(
            onAcknowledge = { viewModel.hidePassengerCancellationDialog() },
            onDismiss = { viewModel.hidePassengerCancellationDialog() }
        )
    }

    // Service boundary dialog
    if (uiState.showServiceBoundaryDialog) {
        ServiceBoundaryDialog(
            message = uiState.serviceBoundaryMessage ?: "You must be within the service area to go online",
            onDismiss = { viewModel.hideServiceBoundaryDialog() }
        )
    }
}

@Composable
private fun DashboardContent(
    uiState: DriverHomeUiState,
    viewModel: DriverHomeViewModel,
    onNavigateToEarnings: (String) -> Unit,
    onNavigateToMissedRequests: () -> Unit,
    zoneBoundaryRepository: ZoneBoundaryRepository
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Online/Offline Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        uiState.online -> IslamoveSecondary.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Driver Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                                    "Account verification required"
                                uiState.online -> "Online"
                                else -> "Offline"
                            },
                            fontSize = 14.sp,
                            color = when {
                                uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                                    MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Switch(
                        checked = uiState.online,
                        onCheckedChange = { viewModel.toggleOnlineStatus() },
                        enabled = !uiState.isLoading && (uiState.currentUser?.driverData?.verificationStatus == VerificationStatus.APPROVED),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IslamoveSecondary
                        )
                    )
                }

                // Show verification status information for unverified drivers
                if (uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val statusMessage = when (uiState.currentUser?.driverData?.verificationStatus) {
                        VerificationStatus.PENDING -> "Please upload the required documents in your account settings. Once submitted, kindly wait for admin approval."
                        VerificationStatus.UNDER_REVIEW -> "Your documents are currently being reviewed by an admin."
                        VerificationStatus.REJECTED -> "Your verification was rejected. Please resubmit your documents."
                        else -> "Please complete document verification to go online."
                    }
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }



        item {
            // Earnings Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Earnings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Today",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "₱${kotlin.math.floor(uiState.todayEarnings).toInt()}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "This Week",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "₱${kotlin.math.floor(uiState.weeklyEarnings).toInt()}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }


        // Show incoming requests (driver can accept multiple rides up to 5)
        if (uiState.incomingRequests.isNotEmpty()) {
            // Show each request as a separate card in the LazyColumn
            items(
                items = uiState.incomingRequests,
                key = { request -> request.requestId } // Preserve state across tab switches
            ) { request ->
                IncomingRequestsCardWithRating(
                    request = request,
                    isLoading = uiState.isLoading,
                    currentTimeMillis = uiState.currentTimeMillis,
                    zoneBoundaryRepository = zoneBoundaryRepository,
                    onAcceptRequest = { req -> viewModel.acceptRideRequest(req) },
                    onDeclineRequest = { req -> viewModel.declineRideRequest(req) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

    }
}

@Composable
private fun RideRequestBottomCardWithData(
    request: DriverRequest,
    isLoading: Boolean,
    currentTimeMillis: Long,
    zoneBoundaryRepository: ZoneBoundaryRepository?,
    currentDriverLocation: UtilsPoint?,
    onAcceptRequest: (DriverRequest) -> Unit,
    onDeclineRequest: (DriverRequest) -> Unit
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }
    var passengerData by remember(request.requestId) { mutableStateOf<User?>(null) }
    var passengerRating by remember(request.requestId) { mutableStateOf<Double?>(null) }
    var isRatingLoading by remember(request.requestId) { mutableStateOf(true) }

    // Fetch passenger data and rating
    LaunchedEffect(request.requestId) {
        // Reset loading state only when request changes
        isRatingLoading = true
        android.util.Log.e("RideRequestCard", "===== FETCHING RATING FOR PASSENGER: ${request.passengerId} (bookingId: ${request.bookingId}) =====")

        // First, try to get passenger ID from request, then fall back to booking lookup
        var passengerIdToUse = request.passengerId

        if (passengerIdToUse.isBlank() && request.bookingId.isNotBlank()) {
            android.util.Log.e("RideRequestCard", "Passenger ID is blank, trying to find it from booking: ${request.bookingId}")

            try {
                // Try to get passenger ID from booking document
                val bookingDoc = firestore.collection("bookings")
                    .document(request.bookingId)
                    .get()
                    .await()

                if (bookingDoc.exists()) {
                    passengerIdToUse = bookingDoc.getString("passengerId") ?: ""
                    android.util.Log.e("RideRequestCard", "Found passenger ID from booking: $passengerIdToUse")
                } else {
                    android.util.Log.e("RideRequestCard", "Booking document not found for ID: ${request.bookingId}")
                }
            } catch (e: Exception) {
                android.util.Log.e("RideRequestCard", "Error fetching booking to get passenger ID: ${e.message}", e)
            }
        }

        // Now fetch passenger data and rating using the found passenger ID
        if (passengerIdToUse.isNotBlank()) {
            try {
                // Fetch user data
                val userDoc = firestore.collection("users")
                    .document(passengerIdToUse)
                    .get()
                    .await()
                passengerData = userDoc.toObject(User::class.java)
                android.util.Log.e("RideRequestCard", "User document fetched successfully for passenger: $passengerIdToUse")

                // Fetch actual passenger rating from user_rating_stats
                android.util.Log.e("RideRequestCard", "Fetching from: user_rating_stats/$passengerIdToUse")
                val ratingDoc = firestore.collection("user_rating_stats")
                    .document(passengerIdToUse)
                    .get()
                    .await()

                android.util.Log.e("RideRequestCard", "Rating document exists: ${ratingDoc.exists()}")
                if (ratingDoc.exists()) {
                    val ratingStats = ratingDoc.toObject(UserRatingStats::class.java)
                    val fetchedRating = ratingStats?.overallRating
                    android.util.Log.e("RideRequestCard", "Raw rating from Firebase: $fetchedRating, totalRatings: ${ratingStats?.totalRatings}")
                    passengerRating = if (fetchedRating != null && fetchedRating > 0.0) {
                        fetchedRating
                    } else {
                        5.0 // New passenger with no ratings
                    }
                    android.util.Log.e("RideRequestCard", "FINAL RATING SET TO: $passengerRating")
                } else {
                    passengerRating = 5.0 // New passenger
                    android.util.Log.e("RideRequestCard", "No rating stats document for passenger $passengerIdToUse, using default 5.0")
                }
            } catch (e: Exception) {
                android.util.Log.e("RideRequestCard", "ERROR fetching passenger data: ${e.message}", e)
                e.printStackTrace()
                passengerRating = 5.0 // Fallback on error
            }
        } else {
            android.util.Log.e("RideRequestCard", "Could not determine passenger ID from request or booking!")
            passengerRating = 5.0
        }

        // Mark loading as complete
        isRatingLoading = false
    }

    RideRequestBottomCard(
        request = request,
        isLoading = isLoading,
        currentTimeMillis = currentTimeMillis,
        zoneBoundaryRepository = zoneBoundaryRepository,
        currentDriverLocation = currentDriverLocation,
        passengerData = passengerData,
        passengerRating = if (isRatingLoading) null else passengerRating, // Don't show rating while loading
        isRatingLoading = isRatingLoading,
        onAcceptRequest = onAcceptRequest,
        onDeclineRequest = onDeclineRequest
    )
}

@Composable
private fun RideRequestBottomCard(
    request: DriverRequest,
    isLoading: Boolean,
    currentTimeMillis: Long,
    zoneBoundaryRepository: ZoneBoundaryRepository?,
    currentDriverLocation: UtilsPoint?,
    passengerData: User?,
    passengerRating: Double?, // Will receive actual rating from Firebase, null while loading
    isRatingLoading: Boolean = false,
    onAcceptRequest: (DriverRequest) -> Unit,
    onDeclineRequest: (DriverRequest) -> Unit
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
        currency = Currency.getInstance("PHP")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    // Calculate distance to pickup
    val distanceToPickup = if (currentDriverLocation != null) {
        val driverLat = currentDriverLocation.latitude()
        val driverLng = currentDriverLocation.longitude()
        val pickupLat = request.pickupLocation.latitude
        val pickupLng = request.pickupLocation.longitude

        // Haversine formula
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(pickupLat - driverLat)
        val dLng = Math.toRadians(pickupLng - driverLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(driverLat)) * cos(Math.toRadians(pickupLat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        earthRadius * c
    } else {
        0.0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Passenger photo placeholder (circular)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Passenger",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Price and route info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatter.format(request.fareEstimate.totalEstimate),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Route: Pickup to Destination
                    Text(
                        text = "${getPickupLocationBoundaryName(request, zoneBoundaryRepository)} to ${getDestinationBoundaryName(request, zoneBoundaryRepository)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Distance to pickup
                    Text(
                        text = "${formatNavigationDistance(distanceToPickup)} away",
                        fontSize = 13.sp,
                        color = IslamovePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Passenger rating
                if (!isRatingLoading && passengerRating != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFC107), // Yellow
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", passengerRating),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (isRatingLoading) {
                    // Show loading indicator for rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Loading rating",
                            tint = Color(0xFFCCCCCC), // Gray color while loading
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Loading...",
                            fontSize = 14.sp,
                            color = Color(0xFFCCCCCC),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Passenger special instructions/notes
            if (request.specialInstructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Note",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Passenger Note:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = request.specialInstructions,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Decline button
                OutlinedButton(
                    onClick = { onDeclineRequest(request) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Decline",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Accept button
                Button(
                    onClick = { onAcceptRequest(request) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IslamovePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Accept",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapContent(
    uiState: DriverHomeUiState,
    viewModel: DriverHomeViewModel,
    zoneBoundaryRepository: ZoneBoundaryRepository
) {
    // Combine current booking and queued bookings for display
    val allAcceptedRides = buildList {
        uiState.currentBooking?.let { add(it) }
        addAll(uiState.queuedBookings)
    }

    // Check if we should show 60/40 split - keep it as long as there are ANY passengers (current or queued)
    // This prevents the layout from switching back to 100% when transitioning between rides (e.g., after rating)
    val hasActivePassengers = uiState.currentBooking != null || uiState.queuedBookings.isNotEmpty()

    // Log the state for debugging
    android.util.Log.e("MapContent", "🎨 RENDERING MapContent:")
    android.util.Log.e("MapContent", "   - currentBooking: ${uiState.currentBooking?.id}")
    android.util.Log.e("MapContent", "   - queuedBookings: ${uiState.queuedBookings.map { it.id }}")
    android.util.Log.e("MapContent", "   - allAcceptedRides count: ${allAcceptedRides.size}")
    android.util.Log.e("MapContent", "   - hasActivePassengers: $hasActivePassengers")
    android.util.Log.e("MapContent", "   - Will show 60/40 split: $hasActivePassengers")
    android.util.Log.e("MapContent", "   - 🗺️ routeInfo: ${uiState.routeInfo != null} (${uiState.routeInfo?.waypoints?.size} waypoints)")
    android.util.Log.e("MapContent", "   - 🛣️ showRoute will be: ${uiState.currentBooking != null && uiState.routeInfo != null}")

    // If there are active passengers (current or queued), show 60/40 split layout
    // Otherwise show full map with incoming requests at bottom
    if (hasActivePassengers) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // MAP SECTION - 60% of screen with rounded corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    MapboxRideView(
                        modifier = Modifier.fillMaxSize(),
                        currentUserLocation = uiState.currentUserLocation?.toMapboxPoint(),
                        pickupLocation = uiState.currentBooking?.pickupLocation,
                        destination = uiState.currentBooking?.destination,
                        driverLocation = null,
                        passengerLocation = uiState.passengerLocation?.toMapboxPoint(),
                        showRoute = uiState.currentBooking != null && uiState.routeInfo != null,
                        routeInfo = uiState.routeInfo,
                        currentBookingStatus = uiState.currentBooking?.status,
                        currentBooking = uiState.currentBooking,
                        isDriverView = true,
                        customLandmarks = uiState.customLandmarks,
                        queuedPassengerPickups = uiState.queuedBookings.map { it.pickupLocation },
                        queuedPassengerDestinations = uiState.queuedBookings.map { it.destination }
                    )
                }

                // Overlay new ride requests on top of the map
                if (uiState.incomingRequests.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                initialOffsetY = { -100 },
                                animationSpec = tween(500)
                            ) + fadeIn(animationSpec = tween(500)),
                            exit = slideOutVertically(
                                targetOffsetY = { -100 },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    // Alert header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🚨",
                                                fontSize = 20.sp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "NEW RIDE REQUEST${if (uiState.incomingRequests.size > 1) "S" else ""}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                text = "${uiState.incomingRequests.size}",
                                                color = MaterialTheme.colorScheme.onError,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Show first request details in the overlay
                                    uiState.incomingRequests.firstOrNull()?.let { request ->
                                        Text(
                                            text = "Pickup: ${request.pickupLocation?.address ?: "Loading address..."}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Destination: ${request.destination?.address ?: "Loading address..."}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Quick action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.acceptRideRequest(request) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Text("Accept", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.declineRideRequest(request) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Text("Decline", fontSize = 12.sp)
                                            }
                                        }

                                        if (uiState.incomingRequests.size > 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "+${uiState.incomingRequests.size - 1} more request${if (uiState.incomingRequests.size - 1 > 1) "s" else ""} in rides section below",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIDES SECTION - 40% of screen with scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // FIXED: Remember scroll state to prevent auto-scroll when switching passengers
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show ALL accepted rides with action buttons
                    items(
                        items = allAcceptedRides,
                        key = { it.id }
                    ) { booking ->
                        val isActive = booking.id == uiState.currentBooking?.id

                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(300)
                            ) + androidx.compose.animation.slideInHorizontally(
                                initialOffsetX = { if (isActive) -it else it },
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.8f,
                                animationSpec = androidx.compose.animation.core.tween(300)
                            ),
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(300)
                            ) + androidx.compose.animation.slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = androidx.compose.animation.core.tween(300)
                            )
                        ) {
                            RideCardWithActions(
                                booking = booking,
                                isActive = isActive,
                                isLoading = uiState.isLoading && booking.id == uiState.currentBooking?.id,
                                uiState = uiState,
                                onRideSelected = { viewModel.switchToRide(booking.id) },
                                onArrivedAtPickup = {
                                    if (booking.id == uiState.currentBooking?.id) {
                                        viewModel.arrivedAtPickup()
                                    }
                                },
                                onStartTrip = {
                                    if (booking.id == uiState.currentBooking?.id) {
                                        viewModel.startTrip()
                                    }
                                },
                                onCompleteTrip = {
                                    if (booking.id == uiState.currentBooking?.id) {
                                        viewModel.completeTrip()
                                    }
                                },
                                onCancelTrip = {
                                    if (booking.id == uiState.currentBooking?.id) {
                                        viewModel.cancelTrip()
                                    }
                                },
                                zoneBoundaryRepository = zoneBoundaryRepository
                            )
                        }
                    }

                    // Show additional incoming requests (excluding the first one shown in overlay)
                    if (uiState.incomingRequests.size > 1) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Additional Ride Requests",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(
                            items = uiState.incomingRequests.drop(1), // Skip first one (shown in overlay)
                            key = { it.requestId }
                        ) { request ->
                            RideRequestBottomCardWithData(
                                request = request,
                                isLoading = uiState.isLoading,
                                currentTimeMillis = uiState.currentTimeMillis,
                                zoneBoundaryRepository = zoneBoundaryRepository,
                                currentDriverLocation = uiState.currentUserLocation,
                                onAcceptRequest = { req -> viewModel.acceptRideRequest(req) },
                                onDeclineRequest = { req -> viewModel.declineRideRequest(req) }
                            )
                        }
                    }

                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    } else {
        // Original layout when no accepted rides
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MapboxRideView(
                    modifier = Modifier.fillMaxSize(),
                    currentUserLocation = uiState.currentUserLocation?.toMapboxPoint(),
                    pickupLocation = uiState.currentBooking?.pickupLocation,
                    destination = uiState.currentBooking?.destination,
                    driverLocation = null,
                    passengerLocation = uiState.passengerLocation?.toMapboxPoint(),
                    showRoute = uiState.currentBooking != null && uiState.routeInfo != null,
                    routeInfo = uiState.routeInfo,
                    currentBookingStatus = uiState.currentBooking?.status,
                    currentBooking = uiState.currentBooking,
                    isDriverView = true,
                    customLandmarks = uiState.customLandmarks,
                    queuedPassengerPickups = uiState.queuedBookings.map { it.pickupLocation },
                    queuedPassengerDestinations = uiState.queuedBookings.map { it.destination }
                )

                // Status overlay
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                                MaterialTheme.colorScheme.errorContainer
                            !uiState.online -> MaterialTheme.colorScheme.surfaceVariant
                            uiState.incomingRequests.isNotEmpty() -> IslamovePrimary
                            else -> IslamovePrimary.copy(alpha = 0.8f)
                        }
                    )
                ) {
                    Text(
                        text = when {
                            uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                                "Account verification required to receive requests"
                            !uiState.online -> "Go online to receive ride requests"
                            uiState.incomingRequests.isNotEmpty() -> "${uiState.incomingRequests.size} active request(s)"
                            else -> "Waiting for ride requests..."
                        },
                        modifier = Modifier.padding(12.dp),
                        color = when {
                            uiState.currentUser?.driverData?.verificationStatus != VerificationStatus.APPROVED ->
                                MaterialTheme.colorScheme.onErrorContainer
                            uiState.online -> Color.White
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 14.sp,
                        fontWeight = if (uiState.incomingRequests.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Incoming requests at bottom
            if (uiState.incomingRequests.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.incomingRequests,
                        key = { request -> request.requestId } // Preserve state across recompositions
                    ) { request ->
                        IncomingRequestsCardWithRating(
                            request = request,
                            isLoading = uiState.isLoading,
                            currentTimeMillis = uiState.currentTimeMillis,
                            zoneBoundaryRepository = zoneBoundaryRepository,
                            onAcceptRequest = { req -> viewModel.acceptRideRequest(req) },
                            onDeclineRequest = { req -> viewModel.declineRideRequest(req) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeTypeSelector(
    selectedType: DateRangeType,
    selectedDateRange: DateRange,
    onTypeSelected: (DateRangeType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Main selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedType.name.lowercase().replaceFirstChar { it.uppercase() }
                            .replace("_", " "),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select date range",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendar",
                        tint = Color(0xFF007AFF)
                    )
                    Text(
                        text = selectedDateRange.getFormattedDateRange(),
                        fontSize = 14.sp,
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .background(Color.White)
        ) {
            DropdownMenuItem(
                text = { Text("Today") },
                onClick = {
                    onTypeSelected(DateRangeType.TODAY)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This Week") },
                onClick = {
                    onTypeSelected(DateRangeType.THIS_WEEK)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This Month") },
                onClick = {
                    onTypeSelected(DateRangeType.THIS_MONTH)
                    expanded = false
                }
            )
        }
    }
}


@Composable
private fun RidesContent(
    uiState: DriverHomeUiState,
    onNavigateToEarnings: (String) -> Unit,
    onNavigateToTripDetails: (String) -> Unit,
    viewModel: DriverHomeViewModel,
    zoneBoundaryRepository: ZoneBoundaryRepository
) {
    val completedTrips = remember { mutableStateOf<List<Booking>>(emptyList()) }
    val isLoading = remember { mutableStateOf(false) }

    // Load completed trips when date range changes
    LaunchedEffect(uiState.selectedDateRange) {
        isLoading.value = true
        val result = viewModel.getDriverCompletedTrips(uiState.selectedDateRange)
        result.fold(
            onSuccess = { trips ->
                completedTrips.value = trips
            },
            onFailure = { error ->
                android.util.Log.e("RidesContent", "Failed to load completed trips", error)
            }
        )
        isLoading.value = false
    }

    // Calculate earnings for the selected date range
    val currentEarnings = remember(completedTrips.value) {
        viewModel.calculateEarningsForDateRange(completedTrips.value)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateRangeTypeSelector(
                    selectedType = uiState.selectedDateRangeType,
                    selectedDateRange = uiState.selectedDateRange,
                    onTypeSelected = { type ->
                        viewModel.selectDateRangeType(type)
                    }
                )
            }
        }


        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Total earnings",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "₱${kotlin.math.floor(currentEarnings).toInt()}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        item {
            Text(
                text = "Recent Trips",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (isLoading.value) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (completedTrips.value.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No completed trips yet",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(completedTrips.value.size) { index ->
                val booking = completedTrips.value[index]

                // Format trip details
                val destination = booking.destination.address
                val time = booking.completionTime?.let {
                    java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
                } ?: "Unknown time"
                val distance = "${String.format("%.1f", booking.fareEstimate.estimatedDistance)} mi"
                val fare = booking.fareEstimate.totalEstimate

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTripDetails(booking.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(0xFFE3F2FD),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.onlineicon),
                                contentDescription = "Trip",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = destination,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$time • $distance",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "₱${kotlin.math.floor(fare).toInt()}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestsCardWithRating(
    request: DriverRequest,
    isLoading: Boolean,
    currentTimeMillis: Long,
    requestPhase: String = "Active",
    zoneBoundaryRepository: ZoneBoundaryRepository? = null,
    onAcceptRequest: (DriverRequest) -> Unit,
    onDeclineRequest: (DriverRequest) -> Unit
) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    var passengerRating by remember(request.requestId) { mutableStateOf<Double?>(null) }
    var isRatingLoading by remember(request.requestId) { mutableStateOf(true) }

    // Fetch actual passenger rating from user_rating_stats
    LaunchedEffect(request.requestId) {
        // Reset loading state only when request changes
        isRatingLoading = true
        android.util.Log.d("IncomingRequestsCard", "===== FETCHING RATING FOR PASSENGER: ${request.passengerId} (bookingId: ${request.bookingId}) =====")

        // First, try to get passenger ID from request, then fall back to booking lookup
        var passengerIdToUse = request.passengerId

        if (passengerIdToUse.isBlank() && request.bookingId.isNotBlank()) {
            android.util.Log.d("IncomingRequestsCard", "Passenger ID is blank, trying to find it from booking: ${request.bookingId}")

            try {
                // Try to get passenger ID from booking document
                val bookingDoc = firestore.collection("bookings")
                    .document(request.bookingId)
                    .get()
                    .await()

                if (bookingDoc.exists()) {
                    passengerIdToUse = bookingDoc.getString("passengerId") ?: ""
                    android.util.Log.d("IncomingRequestsCard", "Found passenger ID from booking: $passengerIdToUse")
                } else {
                    android.util.Log.d("IncomingRequestsCard", "Booking document not found for ID: ${request.bookingId}")
                }
            } catch (e: Exception) {
                android.util.Log.e("IncomingRequestsCard", "Error fetching booking to get passenger ID: ${e.message}", e)
            }
        }

        // Now fetch passenger rating using the found passenger ID
        if (passengerIdToUse.isNotBlank()) {
            try {
                val ratingDoc = firestore.collection("user_rating_stats")
                    .document(passengerIdToUse)
                    .get()
                    .await()

                if (ratingDoc.exists()) {
                    val ratingStats = ratingDoc.toObject(UserRatingStats::class.java)
                    val fetchedRating = ratingStats?.overallRating
                    passengerRating = if (fetchedRating != null && fetchedRating > 0.0) {
                        fetchedRating
                    } else {
                        5.0 // New passenger with no ratings
                    }
                    android.util.Log.d("IncomingRequestsCard", "Passenger $passengerIdToUse rating: $passengerRating (raw: $fetchedRating, totalRatings: ${ratingStats?.totalRatings})")
                } else {
                    passengerRating = 5.0 // New passenger
                    android.util.Log.d("IncomingRequestsCard", "No rating stats for passenger $passengerIdToUse, using default 5.0")
                }
            } catch (e: Exception) {
                android.util.Log.e("IncomingRequestsCard", "Failed to fetch passenger rating: ${e.message}", e)
                passengerRating = 5.0 // Fallback on error
            }
        } else {
            android.util.Log.d("IncomingRequestsCard", "Could not determine passenger ID from request or booking!")
            passengerRating = 5.0
        }

        // Mark loading as complete
        isRatingLoading = false
    }

    IncomingRequestsCard(
        requests = listOf(request),
        isLoading = isLoading,
        currentTimeMillis = currentTimeMillis,
        zoneBoundaryRepository = zoneBoundaryRepository,
        passengerRating = if (isRatingLoading) null else passengerRating, // Don't show rating while loading
        isRatingLoading = isRatingLoading,
        onAcceptRequest = onAcceptRequest,
        onDeclineRequest = onDeclineRequest
    )
}

@Composable
private fun IncomingRequestsCard(
    requests: List<DriverRequest>,
    isLoading: Boolean,
    currentTimeMillis: Long,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null,
    passengerRating: Double? = null,
    isRatingLoading: Boolean = false,
    onAcceptRequest: (DriverRequest) -> Unit,
    onDeclineRequest: (DriverRequest) -> Unit
) {
    // Don't show card if no valid requests
    if (requests.isEmpty()) {
        return
    }

    // Filter out expired requests in real-time
    val validRequests = requests.filter { request ->
        !request.isExpired(currentTimeMillis)
    }

    if (validRequests.isEmpty()) {
        return
    }

    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
        currency = Currency.getInstance("PHP")
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    val request = validRequests.first()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Simple header - no phase text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ride Request",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IslamovePrimary
                )

                // Passenger Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isRatingLoading && passengerRating != null) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = String.format("%.1f", passengerRating),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (isRatingLoading) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Loading rating",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Loading...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCCCCCC)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Trip details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = IslamovePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PICKUP",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = getPickupLocationBoundaryName(request, zoneBoundaryRepository),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DESTINATION",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = getDestinationBoundaryName(request, zoneBoundaryRepository),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total Passengers Count
            if (request.totalPassengers > 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TOTAL PASSENGERS",
                            fontSize = 11.sp,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${request.totalPassengers}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Companion Breakdown
            if (request.companions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "COMPANIONS (${request.companions.size})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    request.companions.forEach { companion ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (companion.type) {
                                    CompanionType.STUDENT, CompanionType.SENIOR -> Color(0xFFE8F5E8)
                                    CompanionType.CHILD -> Color(0xFFE3F2FD)
                                    else -> Color(0xFFF5F5F5)
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = when (companion.type) {
                                            CompanionType.STUDENT, CompanionType.SENIOR -> Color(0xFF4CAF50)
                                            CompanionType.CHILD -> Color(0xFF2196F3)
                                            else -> Color.Gray
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = when (companion.type) {
                                                CompanionType.STUDENT -> "Student"
                                                CompanionType.SENIOR -> "Senior"
                                                CompanionType.CHILD -> "Child"
                                                else -> "Regular"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (companion.discountPercentage > 0) {
                                            Text(
                                                text = "${companion.discountPercentage}% discount",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "₱${kotlin.math.floor(companion.fare).toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (companion.type) {
                                        CompanionType.STUDENT, CompanionType.SENIOR -> Color(0xFF4CAF50)
                                        CompanionType.CHILD -> Color(0xFF2196F3)
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fare Breakdown Button (expandable)
            if (request.fareEstimate.fareBreakdown.isNotBlank()) {
                var showBreakdown by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showBreakdown = !showBreakdown },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (showBreakdown) "Hide Fare Breakdown" else "Show Fare Breakdown",
                        fontSize = 12.sp
                    )
                }

                if (showBreakdown) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF9C4)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = request.fareEstimate.fareBreakdown,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color.Black,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Discount Badge
            if (request.passengerDiscountPercentage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (request.passengerDiscountPercentage) {
                            20 -> Color(0xFFE8F5E8)
                            50 -> Color(0xFFE3F2FD)
                            else -> Color(0xFFF5F5F5)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = when (request.passengerDiscountPercentage) {
                                20 -> Color(0xFF4CAF50)
                                50 -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PASSENGER DISCOUNT",
                            fontSize = 11.sp,
                            color = when (request.passengerDiscountPercentage) {
                                20 -> Color(0xFF4CAF50)
                                50 -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${request.passengerDiscountPercentage}% OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (request.passengerDiscountPercentage) {
                                20 -> Color(0xFF4CAF50)
                                50 -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                            modifier = Modifier
                                .background(
                                    color = when (request.passengerDiscountPercentage) {
                                        20 -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        50 -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                        else -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Passenger Comment
            if (request.specialInstructions.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PASSENGER NOTE",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = request.specialInstructions,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fare information
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Estimated Fare",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = formatter.format(kotlin.math.floor(request.fareEstimate.totalEstimate)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "${formatNavigationDistance(request.fareEstimate.estimatedDistance)} • ${request.fareEstimate.estimatedDuration} min",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onDeclineRequest(request) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Decline")
                }

                Button(
                    onClick = { onAcceptRequest(request) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Accept")
                    }
                }
            }

            // Simple timer indicator (30 seconds only)
            val timeLeft = request.getTimeRemaining(currentTimeMillis)

            if (timeLeft > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (timeLeft / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = IslamovePrimary
                )
                Text(
                    text = "Expires in ${timeLeft}s",
                    fontSize = 12.sp,
                    color = IslamovePrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

private fun formatNavigationDistance(meters: Double): String {
    return when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> String.format("%.1f km", meters / 1000)
    }
}

@Composable
private fun ServiceBoundaryDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Service Area Required",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

/**
 * Ride Card with Actions - shows every accepted ride with its action buttons
 */
@Composable
private fun RideCardWithActions(
    booking: Booking,
    isActive: Boolean,
    isLoading: Boolean,
    uiState: DriverHomeUiState,
    onRideSelected: () -> Unit,
    onArrivedAtPickup: () -> Unit,
    onStartTrip: () -> Unit,
    onCompleteTrip: () -> Unit,
    onCancelTrip: () -> Unit,
    zoneBoundaryRepository: ZoneBoundaryRepository? = null
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    // State for passenger data
    var passengerName by remember { mutableStateOf<String?>(null) }
    var passengerProfileImage by remember { mutableStateOf<String?>(null) }
    var passengerPhoneNumber by remember { mutableStateOf<String?>(null) }

    // Fetch passenger data from Firestore
    LaunchedEffect(booking.passengerId) {
        try {
            val passengerDoc = firestore.collection("users")
                .document(booking.passengerId)
                .get()
                .await()

            if (passengerDoc.exists()) {
                passengerName = passengerDoc.getString("displayName")
                    ?: passengerDoc.getString("name")
                    ?: "Passenger"
                passengerProfileImage = passengerDoc.getString("profileImageUrl")
                passengerPhoneNumber = passengerDoc.getString("phoneNumber")
                    ?: passengerDoc.getString("phone")
            }
        } catch (e: Exception) {
            Log.e("RideCardWithActions", "Error fetching passenger data", e)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRideSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with avatar, name, call button, and fare
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Passenger profile image from Cloudinary
                    if (passengerProfileImage != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(passengerProfileImage)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Passenger Profile",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        )
                    } else {
                        // Fallback avatar if no profile image
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Passenger",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isActive) "Current Passenger:" else "Queued:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = passengerName ?: "Loading...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Call button
                    if (passengerPhoneNumber != null) {
                        IconButton(
                            onClick = {
                                try {
                                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:$passengerPhoneNumber")
                                    }
                                    context.startActivity(callIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to make call", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call Passenger",
                                tint = IslamovePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Fare with checkmark if active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "₱${String.format("%.2f", booking.fareEstimate.totalEstimate)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = IslamovePrimary
                    )
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pickup and Destination info
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TripOrigin,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Pickup: ${getBookingPickupLocationBoundaryName(booking, zoneBoundaryRepository)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    // Status badge beside pickup
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = getStatusColor(booking.status)
                    ) {
                        Text(
                            text = getStatusText(booking.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Destination: ${booking.destination.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                // Show ETA if active and navigating
                if (isActive && uiState.routeInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ETA: ${uiState.routeInfo.estimatedDuration} min",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Passenger special instructions/notes
            if (booking.specialInstructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Note",
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Passenger Note:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Text(
                                text = booking.specialInstructions,
                                fontSize = 13.sp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button based on status - ONLY show if this is the current booking
            if (booking.id == uiState.currentBooking?.id) {
                when (booking.status) {
                    BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelTrip,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onArrivedAtPickup,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Arrived at Pickup")
                            }
                        }
                    }
                    BookingStatus.DRIVER_ARRIVED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelTrip,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onStartTrip,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Start Trip")
                            }
                        }
                    }
                    BookingStatus.IN_PROGRESS -> {
                        Button(
                            onClick = onCompleteTrip,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Complete Trip")
                        }
                    }
                    else -> {}
                }
            } else {
                // For queued rides, show "Switch to this ride" button
                Button(
                    onClick = onRideSelected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Switch to this ride")
                }
            }
        }
    }
}

// Helper functions to get status color and text
private fun getStatusColor(status: com.rj.islamove.data.models.BookingStatus): Color {
    return when (status) {
        com.rj.islamove.data.models.BookingStatus.ACCEPTED -> Color(0xFF6C757D) // Muted Gray-Blue
        com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVING -> Color(0xFF6C757D) // Muted Gray-Blue
        com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVED -> Color(0xFF0D6EFD) // Professional Blue
        com.rj.islamove.data.models.BookingStatus.IN_PROGRESS -> Color(0xFF198754) // Professional Green
        else -> Color(0xFF6C757D)
    }
}

private fun getStatusText(status: com.rj.islamove.data.models.BookingStatus): String {
    return when (status) {
        com.rj.islamove.data.models.BookingStatus.ACCEPTED -> "GOING TO PICKUP"
        com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVING -> "GOING TO PICKUP"
        com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVED -> "AT PICKUP"
        com.rj.islamove.data.models.BookingStatus.IN_PROGRESS -> "IN TRIP"
        else -> "UNKNOWN"
    }
}
