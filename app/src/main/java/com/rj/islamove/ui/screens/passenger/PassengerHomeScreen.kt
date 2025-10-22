package com.rj.islamove.ui.screens.passenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import android.content.Intent.ACTION_CALL
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import android.util.Log
import java.io.FileOutputStream
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlin.math.max
import kotlin.math.min
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.utils.Point
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.data.models.Booking
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.RouteInfo
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.components.ReportDriverModal
import com.mapbox.geojson.Point as MapboxPoint
import com.rj.islamove.data.models.PlaceDetails as MapboxPlaceDetails
import com.rj.islamove.ui.components.toMapboxPlaceDetails
// Removed Google Maps LatLng import - using Mapbox Point instead
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.ui.theme.IslamoveSecondary
import com.rj.islamove.utils.LocationUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.*
import com.rj.islamove.R

// Extension function to find Activity from Context
fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

// Calculate distance between two points using Haversine formula
fun calculateDistance(point1: MapboxPoint, point2: MapboxPoint): Double {
    val R = 6371000.0 // Earth's radius in meters
    val lat1 = Math.toRadians(point1.latitude())
    val lat2 = Math.toRadians(point2.latitude())
    val deltaLat = Math.toRadians(point2.latitude() - point1.latitude())
    val deltaLon = Math.toRadians(point2.longitude() - point1.longitude())

    val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return R * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerHomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToRating: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToReviews: () -> Unit = { },
    onNavigateToDriverDocuments: (String) -> Unit = { },
    onNavigateToTripDetails: (String) -> Unit = { },
    onNavigateToHelpSupport: () -> Unit = { },
    onSignOut: () -> Unit = { },
    viewModel: PassengerHomeViewModel = hiltViewModel()
) {
    // Get ZoneBoundaryRepository from ViewModel
    val zoneBoundaryRepository = viewModel.getZoneBoundaryRepository()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Maps", "Rides", "Profile")
    val uiState by viewModel.uiState.collectAsState()

    // Search state to control MapsContent positioning
    var isSearchActive by remember { mutableStateOf(false) }

    // POI selection state - moved from MapsContent to parent scope
    var selectedPlaceForBooking by remember { mutableStateOf<MapboxPlaceDetails?>(null) }

    // Debug logging for currentBooking state
    LaunchedEffect(uiState.currentBooking?.id, selectedTab) {
        android.util.Log.d("PassengerHomeScreen", "UI State - selectedTab: $selectedTab, currentBooking: ${uiState.currentBooking?.id}, status: ${uiState.currentBooking?.status}")
    }

    // Reset search active state when changing tabs
    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) { // Only reset when not on Maps tab (index 0)
            android.util.Log.d("PassengerHomeScreen", "Tab changed to $selectedTab, resetting isSearchActive")
            isSearchActive = false
        }
    }

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

    // Load saved places when screen starts
    LaunchedEffect(Unit) {
        viewModel.loadSavedPlaces()
    }

    // Handle rating screen navigation
    LaunchedEffect(uiState.showRatingScreen) {
        if (uiState.showRatingScreen &&
            uiState.completedBookingId != null &&
            uiState.currentBooking?.driverId != null) {
            onNavigateToRating(
                uiState.completedBookingId!!,
                uiState.currentBooking!!.driverId!!,
                "DRIVER"
            )
            viewModel.resetRatingTrigger()
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Minimal status bar spacing
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color.White)
        )

        // Tab content
        Box(modifier = Modifier.weight(1f)) {
            android.util.Log.d("PassengerHomeScreen", "Rendering tab content for selectedTab: $selectedTab")
            when (selectedTab) {
                0 -> {
                    android.util.Log.d("PassengerHomeScreen", "Rendering MapsContent")
                    MapsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        selectedPlaceForBooking = selectedPlaceForBooking,
                        onSelectedPlaceForBookingChange = { selectedPlaceForBooking = it },
                        modifier = if (isSearchActive) Modifier.align(Alignment.TopCenter) else Modifier,
                        isSearchActive = isSearchActive,
                        onSearchActiveChange = { isSearchActive = it },
                        zoneBoundaryRepository = zoneBoundaryRepository
                    )
                }
                1 -> {
                    android.util.Log.d("PassengerHomeScreen", "Rendering RidesContent")
                    RidesContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToTripDetails = onNavigateToTripDetails
                    )
                }
                2 -> {
                    android.util.Log.d("PassengerHomeScreen", "Rendering ProfileContent")
                    ProfileContent(
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToReviews = onNavigateToReviews,
                        onNavigateToDriverDocuments = onNavigateToDriverDocuments,
                        onNavigateToHelpSupport = onNavigateToHelpSupport,
                        onSignOut = onSignOut,
                        uiState = uiState,
                        viewModel = viewModel
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
                                0 -> Icons.Default.LocationOn
                                1 -> Icons.Default.List
                                2 -> Icons.Default.Person
                                else -> Icons.Default.LocationOn
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

    // Driver cancellation dialog
    if (uiState.showDriverCancellationDialog) {
        com.rj.islamove.ui.components.DriverCancellationNotificationDialog(
            onAcknowledge = { viewModel.hideDriverCancellationDialog() },
            onDismiss = { viewModel.hideDriverCancellationDialog() }
        )
    }

    // Service boundary dialog
    if (uiState.showServiceBoundaryDialog) {
        ServiceBoundaryDialog(
            message = uiState.serviceBoundaryMessage ?: "Destination must be within the service area",
            onDismiss = { viewModel.hideServiceBoundaryDialog() }
        )
    }

    // Report Driver Modal for active trips (DriverFoundCard)
    if (uiState.showReportDriverModal) {
        ReportDriverModal(
            driverName = uiState.assignedDriver?.displayName ?: "Driver",
            onDismiss = { viewModel.hideReportDriverModal() },
            onSubmitReport = { reportType, description ->
                viewModel.submitDriverReport(reportType, description)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhereToCard(
    modifier: Modifier = Modifier,
    currentLocation: Point?,
    onDestinationSearch: (String) -> Unit,
    onBookRide: (String) -> Unit,
    fareEstimate: String? = null,
    discountPercentage: Int? = null,
    isLoading: Boolean = false,
    pickupLocation: BookingLocation? = null,
    onPickupClick: (() -> Unit)? = null,
    onSearchActiveChange: (Boolean) -> Unit = {}
) {
    var destinationText by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val canBook = destinationText.isNotBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(max = if (isFocused) 500.dp else 350.dp)
        ) {
            Text(
                text = "Where to?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Pickup location display (clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .then(
                        if (onPickupClick != null) {
                            Modifier.clickable { onPickupClick() }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = IslamovePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Pickup",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pickupLocation?.address ?: "Current Location",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onPickupClick != null) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change pickup location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Destination input
            OutlinedTextField(
                value = destinationText,
                onValueChange = {
                    destinationText = it
                    android.util.Log.d("WhereToCard", "Text changed: '$it'")
                    if (it.isNotBlank()) {
                        onDestinationSearch(it)
                    }
                    // Reset search active state when text is cleared
                    if (it.isEmpty()) {
                        onSearchActiveChange(false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        android.util.Log.d("WhereToCard", "Focus changed: isFocused = $isFocused")
                        onSearchActiveChange(focusState.isFocused)
                    },
                placeholder = { Text("Where would you like to go?") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (destinationText.isNotBlank()) {
                            onDestinationSearch(destinationText)
                        }
                    }
                )
            )

            // Fare estimate display (when available)
            if (fareEstimate != null && canBook) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = IslamovePrimary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Estimated Fare",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = fareEstimate,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = IslamovePrimary
                            )
                        }

                        // Show discount information if user has a discount
                        if (discountPercentage != null && discountPercentage > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = when (discountPercentage) {
                                        20 -> Color(0xFF4CAF50)
                                        50 -> Color(0xFF2196F3)
                                        else -> Color(0xFF9E9E9E)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$discountPercentage% discount applied",
                                    fontSize = 12.sp,
                                    color = when (discountPercentage) {
                                        20 -> Color(0xFF4CAF50)
                                        50 -> Color(0xFF2196F3)
                                        else -> Color(0xFF9E9E9E)
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Book Ride button
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (canBook) {
                        onBookRide(destinationText)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canBook && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IslamovePrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Booking...")
                } else {
                    Text(
                        text = if (canBook) "Book Ride" else "Enter destination",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPlacesSection(
    places: List<BookingLocation>,
    onPlaceSelected: (BookingLocation) -> Unit,
    onPlaceRemove: (BookingLocation) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Places",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quick Book",
                    fontSize = 12.sp,
                    color = IslamovePrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(places) { place ->
                    SavedPlaceCard(
                        place = place,
                        onPlaceSelected = { onPlaceSelected(place) },
                        onPlaceRemove = { onPlaceRemove(place) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultSavedPlacesSection(
    onAddPlace: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick Destinations",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DefaultPlaceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Home,
                    title = "Add Home",
                    onClick = { onAddPlace("Home") }
                )
                DefaultPlaceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocationOn,
                    title = "Add Work",
                    onClick = { onAddPlace("Work") }
                )
                DefaultPlaceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    title = "Add Favorite",
                    onClick = { onAddPlace("Favorite") }
                )
            }
        }
    }
}

@Composable
private fun SavedPlaceCard(
    place: BookingLocation,
    onPlaceSelected: () -> Unit,
    onPlaceRemove: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBookingDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = IslamovePrimary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main clickable area with long press support
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showBookingDialog = true
                            },
                            onLongPress = {
                                showDeleteDialog = true
                            }
                        )
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    when (place.placeType) {
                        "Home" -> Icons.Default.Home
                        "Work" -> Icons.Default.LocationOn
                        "Favorite" -> Icons.Default.Star
                        else -> Icons.Default.Star
                    },
                    contentDescription = null,
                    tint = IslamovePrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = place.address,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to book",
                    fontSize = 10.sp,
                    color = IslamovePrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove ${place.address}?") },
            text = { Text("This saved place will be removed from your list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPlaceRemove()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Booking confirmation dialog
    if (showBookingDialog) {
        AlertDialog(
            onDismissRequest = { showBookingDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (place.placeType) {
                            "Home" -> Icons.Default.Home
                            "Work" -> Icons.Default.LocationOn
                            "Favorite" -> Icons.Default.Star
                            else -> Icons.Default.Star
                        },
                        contentDescription = null,
                        tint = IslamovePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Book Ride")
                }
            },
            text = {
                Column {
                    Text("Do you want to book a ride to this location?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Destination:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = place.address,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPlaceSelected()
                        showBookingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IslamovePrimary
                    )
                ) {
                    Text("Book Ride")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBookingDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DefaultPlaceCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoriteTripsSection(
    trips: List<Booking>,
    onRebookTrip: (Booking) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Favorite Trips",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            trips.take(3).forEach { trip ->
                FavoriteTripItem(
                    trip = trip,
                    onRebook = { onRebookTrip(trip) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteTripItem(
    trip: Booking,
    onRebook: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRebook() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trip.destination.address,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = formatTripDate(System.currentTimeMillis()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRebook) {
            Text("Book Again")
        }
    }
}

@Composable
private fun DriverFoundCard(
    modifier: Modifier = Modifier,
    booking: Booking,
    driver: User?,
    eta: Int,
    route: RouteInfo?,
    uiState: PassengerHomeUiState,
    onCallDriver: () -> Unit,
    onCancelBooking: (() -> Unit)? = null,
    onReportDriver: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Driver info section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Driver profile photo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = Color(0xFFE8B68C),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (driver?.profileImageUrl != null) {
                        AsyncImage(
                            model = driver.profileImageUrl,
                            contentDescription = "Driver Photo",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Driver Photo",
                            modifier = Modifier.size(30.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Driver name and car details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver?.displayName ?: "Driver",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    val vehicleInfo = driver?.driverData?.vehicleData?.let { vehicle ->
                        "${vehicle.make} ${vehicle.model} ${vehicle.color}"
                    } ?: "Vehicle Info"

                    Text(
                        text = vehicleInfo,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    // Rating with multiple stars
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val rating = driver?.driverData?.rating ?: 0.0
                        val tripCount = driver?.driverData?.totalTrips ?: 0
                        val fullStars = rating.toInt()
                        val hasHalfStar = (rating - fullStars) >= 0.5

                        // Display up to 5 stars
                        repeat(5) { index ->
                            val starTint = when {
                                index < fullStars -> Color(0xFFFFA500) // Full star
                                index == fullStars && hasHalfStar -> Color(0xFFFFA500) // Half star (show as full for simplicity)
                                else -> Color(0xFFE0E0E0) // Empty star
                            }

                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = starTint
                            )
                            if (index < 4) Spacer(modifier = Modifier.width(2.dp))
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "${String.format("%.1f", rating)} ($tripCount)",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }

                // Report button - only show when trip is in progress
                if (booking.status == BookingStatus.IN_PROGRESS) {
                    IconButton(
                        onClick = onReportDriver,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.Red.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = "Report Driver",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ETA and distance - adapt labels based on booking status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val etaLabel = when (booking.status) {
                        BookingStatus.ACCEPTED -> "ETA"
                        BookingStatus.DRIVER_ARRIVED -> "Driver"
                        BookingStatus.IN_PROGRESS -> "Arriving"
                        else -> "ETA"
                    }

                    Text(
                        text = etaLabel,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    val etaText = when (booking.status) {
                        BookingStatus.DRIVER_ARRIVED -> "Here!"
                        else -> {
                            if (eta > 0) {
                                val hours = eta / 60
                                val minutes = eta % 60
                                if (hours > 0) {
                                    "${hours}h ${minutes}m"
                                } else {
                                    "${minutes} min"
                                }
                            } else {
                                "Calculating..."
                            }
                        }
                    }

                    Text(
                        text = etaText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Trip distance",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    val distanceText = route?.let {
                        formatNavigationDistance(it.totalDistance)
                    } ?: booking.fareEstimate.estimatedDistance.takeIf { it > 0 }?.let {
                        formatNavigationDistance(it)
                    } ?: "Calculating..."

                    Text(
                        text = distanceText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Destination
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Destination: ",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = booking.destination.address,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            // Cancel button - only show for specific statuses and within 20-second window for ACCEPTED
            if (onCancelBooking != null && booking.status in listOf(
                BookingStatus.ACCEPTED,
                BookingStatus.DRIVER_ARRIVING
            ) && (booking.status != BookingStatus.ACCEPTED || uiState.cancellationTimeRemaining > 0)) {
                Spacer(modifier = Modifier.height(16.dp))

                // Cancel button with countdown text only
                OutlinedButton(
                    onClick = onCancelBooking,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.hasExceededCancellationLimit,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (uiState.hasExceededCancellationLimit)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.hasExceededCancellationLimit)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = when {
                            uiState.hasExceededCancellationLimit -> "Cancellation Limit Reached (3/3)"
                            booking.status == BookingStatus.ACCEPTED && uiState.cancellationTimeRemaining > 0 ->
                                "Cancel Ride - ${uiState.cancellationTimeRemaining}s"
                            booking.status == BookingStatus.ACCEPTED -> "Cancel Ride"
                            else -> "Cancel Booking"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveBookingCard(
    booking: Booking,
    uiState: PassengerHomeUiState,
    onCancelBooking: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = IslamovePrimary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (booking.status) {
                        BookingStatus.PENDING, BookingStatus.LOOKING_FOR_DRIVER -> "Looking for driver..."
                        BookingStatus.ACCEPTED -> "Driver found!"
                        BookingStatus.DRIVER_ARRIVED -> "Driver has arrived"
                        BookingStatus.IN_PROGRESS -> "Trip in progress"
                        else -> "Active Booking"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IslamovePrimary
                )
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
                Text(
                    text = booking.destination.address,
                    fontSize = 14.sp,
                    maxLines = 2
                )
            }

            // Show cancel button for PENDING, ACCEPTED, DRIVER_ARRIVING
            if (booking.status in listOf(
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
                BookingStatus.DRIVER_ARRIVING
            )) {
                Spacer(modifier = Modifier.height(12.dp))

                // Show cancellation timer for ACCEPTED status
                if (booking.status == BookingStatus.ACCEPTED && uiState.cancellationTimeRemaining > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Cancellation timer",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${uiState.cancellationTimeRemaining}s remaining",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Cancellations left: ${uiState.remainingCancellations}/3",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress bar showing time remaining
                            val progress = uiState.cancellationTimeRemaining / 20f
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF4CAF50),
                                trackColor = Color(0xFFE0E0E0)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Show remaining cancellations info for non-ACCEPTED status or when timer expired
                if (booking.status != BookingStatus.ACCEPTED || uiState.cancellationTimeRemaining == 0) {
                    Text(
                        text = "Cancellations remaining: ${uiState.remainingCancellations}/3",
                        fontSize = 12.sp,
                        color = if (uiState.remainingCancellations > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cancel button - disabled if exceeded limit
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Progress background for ACCEPTED status with active timer
                    if (booking.status == BookingStatus.ACCEPTED && uiState.cancellationTimeRemaining > 0) {
                        val progress = uiState.cancellationTimeRemaining / 20f
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                            trackColor = Color.Transparent
                        )
                    }

                    OutlinedButton(
                        onClick = onCancelBooking,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.hasExceededCancellationLimit,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = if (uiState.hasExceededCancellationLimit) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (uiState.hasExceededCancellationLimit) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    uiState.hasExceededCancellationLimit -> "Cancellation Limit Reached"
                                    booking.status == BookingStatus.PENDING -> "Cancel Booking"
                                    booking.status == BookingStatus.ACCEPTED && uiState.cancellationTimeRemaining > 0 -> "Cancel Ride - ${uiState.cancellationTimeRemaining}s"
                                    booking.status == BookingStatus.ACCEPTED -> "Cancel Ride"
                                    booking.status == BookingStatus.DRIVER_ARRIVING -> "Cancel Ride"
                                    else -> "Cancel"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapsContent(
    uiState: PassengerHomeUiState,
    viewModel: PassengerHomeViewModel,
    selectedPlaceForBooking: MapboxPlaceDetails?,
    onSelectedPlaceForBookingChange: (MapboxPlaceDetails?) -> Unit,
    modifier: Modifier = Modifier,
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    zoneBoundaryRepository: com.rj.islamove.data.repository.ZoneBoundaryRepository
) {
    val context = LocalContext.current

    // Store pending phone number for after permission is granted
    var pendingPhoneNumber by remember { mutableStateOf<String?>(null) }

    // Permission launcher for CALL_PHONE
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingPhoneNumber != null) {
            android.util.Log.d("DriverCall", "CALL_PHONE permission granted, attempting call to $pendingPhoneNumber")
            try {
                val intent = Intent(ACTION_CALL).apply {
                    data = Uri.parse("tel:$pendingPhoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                android.util.Log.d("DriverCall", "Call initiated successfully")
            } catch (e: Exception) {
                android.util.Log.e("DriverCall", "Failed to initiate call after permission granted", e)
            }
            pendingPhoneNumber = null
        } else {
            android.util.Log.w("DriverCall", "CALL_PHONE permission denied or no pending number")
            pendingPhoneNumber = null
        }
    }

    var placeSelectionTrigger by remember { mutableStateOf(0L) }

    // Clear the place booking dialog when entering favorite selection mode (but not home selection)
    LaunchedEffect(uiState.isSelectingFavoriteLocation) {
        Log.d("PassengerHomeScreen", "Favorite selection mode state changed - isSelectingFavorite: ${uiState.isSelectingFavoriteLocation}")
        if (uiState.isSelectingFavoriteLocation) {
            Log.d("PassengerHomeScreen", "Clearing selectedPlaceForBooking due to favorite selection mode")
            onSelectedPlaceForBookingChange(null)
        }
    }

    // Bottom sheet drag state (slide down only)
    val density = LocalDensity.current
    val maxHeight = with(density) {
        val height = if (isSearchActive) 600.dp.toPx() else 250.dp.toPx()
        val heightDp = if (isSearchActive) 600 else 250
        android.util.Log.d("MapsHeight", "isSearchActive: $isSearchActive, maxHeight: ${heightDp}dp")
        height
    }
    val minHeight = with(density) { 150.dp.toPx() } // Minimum height when collapsed
    var bottomSheetOffset by remember { mutableStateOf(maxHeight) }
    var targetOffset by remember { mutableStateOf(maxHeight) }

    var isDragging by remember { mutableStateOf(false) }

    // Update targetOffset when isSearchActive changes
    LaunchedEffect(isSearchActive) {
        android.util.Log.d("MapsHeight", "LaunchedEffect triggered - isSearchActive: $isSearchActive, isDragging: $isDragging")
        if (!isDragging) {
            val heightDp = if (isSearchActive) 600 else 250
            android.util.Log.d("MapsHeight", "Updating offsets - new maxHeight: ${heightDp}dp")
            targetOffset = maxHeight
            bottomSheetOffset = maxHeight
        }
    }

    // Only animate when not dragging
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) bottomSheetOffset else targetOffset,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 200),
        label = "bottomSheetOffset"
    )

    // Use animated offset when not dragging, direct offset when dragging
    val currentOffset = if (isDragging) bottomSheetOffset else animatedOffset

    // Outer Box to ensure dialog appears on top
    Box(modifier = modifier.fillMaxSize()) {

    // Container for map and bottom sheet
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full-screen map with user location as pulsating blue dot
        MapboxRideView(
            modifier = Modifier.fillMaxSize(),
            currentUserLocation = uiState.currentUserLocation?.let {
                MapboxPoint.fromLngLat(it.longitude(), it.latitude())
            },
            pickupLocation = uiState.currentBooking?.pickupLocation ?: uiState.pickupLocation,
            destination = uiState.currentBooking?.destination ?: uiState.destination,
            driverLocation = uiState.assignedDriverLocation?.let { driverLoc ->
                MapboxPoint.fromLngLat(driverLoc.longitude(), driverLoc.latitude())
            }, // Track driver location for navigation
            onlineDrivers = uiState.onlineDrivers,
            showOnlineDrivers = uiState.showOnlineDrivers,
            showUserLocation = true, // Enable built-in My Location button and blue dot
            homeLocation = uiState.homeAddress,
            favoriteLocations = uiState.savedPlaces.filter { it.placeType != "Home" }.also { favorites ->
                Log.d("PassengerHomeScreen", "Passing ${favorites.size} favorite locations to Mapbox: ${favorites.joinToString { "${it.address} (${it.placeType})" }}")
            },
            customLandmarks = uiState.customLandmarks.also {
    android.util.Log.d("PassengerHomeScreen", "Passing ${it.size} custom landmarks to Mapbox: ${it.joinToString { "${it.name}" }}")
},
            selectedPlaceDetails = selectedPlaceForBooking,
            onLandmarkMarkerClick = { landmark ->
                // Extract clean destination name (remove fare part for display)
                val cleanName = landmark.name.split(" - ₱").firstOrNull() ?: landmark.name

                // Show landmark in place details dialog for booking
                // Use original landmark.name for address to preserve fare information for calculation
                onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                    id = landmark.id,
                    name = cleanName,
                    point = MapboxPoint.fromLngLat(landmark.longitude, landmark.latitude),
                    address = landmark.name, // Keep original name with fare for fare calculation
                    rating = null,
                    userRatingsTotal = null,
                    types = listOf("custom_landmark"),
                    phoneNumber = null,
                    websiteUri = null,
                    isOpen = null,
                    openingHours = null
                ))
            },
            onHomeMarkerClick = {
                // Show home location in place details dialog with remove option
                uiState.homeAddress?.let { homeLocation ->
                    onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                        id = "home_location",
                        name = "Home",
                        point = MapboxPoint.fromLngLat(homeLocation.coordinates.longitude, homeLocation.coordinates.latitude),
                        address = homeLocation.address,
                        rating = null,
                        userRatingsTotal = null,
                        types = emptyList(),
                        phoneNumber = null,
                        websiteUri = null,
                        isOpen = null,
                        openingHours = null
                    ))
                }
            },
            onPlaceDialogDismiss = {
                onSelectedPlaceForBookingChange(null)
            },
            onPOIClickForSelection = if (uiState.isSelectingHomeLocation || uiState.isSelectingFavoriteLocation || uiState.isSelectingPickupLocation) {
                { latLng ->
                    Log.d("PassengerHomeScreen", "Setting location from POI selection: $latLng")
                    if (uiState.isSelectingHomeLocation) {
                        Log.d("PassengerHomeScreen", "Setting home location from POI")
                        onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                            id = "home_poi_selection_${latLng.latitude()}_${latLng.longitude()}",
                            name = "Selected POI",
                            point = latLng,
                            address = "POI at ${String.format("%.6f", latLng.latitude())}, ${String.format("%.6f", latLng.longitude())}",
                            rating = null,
                            userRatingsTotal = null,
                            types = emptyList(),
                            phoneNumber = null,
                            websiteUri = null,
                            isOpen = null,
                            openingHours = null
                        ))
                    } else if (uiState.isSelectingFavoriteLocation) {
                        Log.d("PassengerHomeScreen", "Setting favorite location")
                        viewModel.setFavoriteLocationFromMap(latLng)
                    } else if (uiState.isSelectingPickupLocation) {
                        Log.d("PassengerHomeScreen", "Setting pickup location")
                        viewModel.setPickupLocationFromMap(latLng)
                    }
                }
            } else null,
            onFavoriteMarkerClick = { favoriteLocation ->
                // When favorite marker is clicked, show the POI dialog with remove option
                Log.d("PassengerHomeScreen", "Favorite marker clicked: ${favoriteLocation.address}")
                onSelectedPlaceForBookingChange(favoriteLocation.toMapboxPlaceDetails())
                // Note: The actual removal will happen when user clicks "Remove Favorite" button in the dialog
            },
            onRemoveFavoriteLocation = { favoriteLocation ->
                // Actually remove the favorite location
                Log.d("PassengerHomeScreen", "Removing favorite location: ${favoriteLocation.address}, placeType: '${favoriteLocation.placeType}', Firebase key: '${favoriteLocation.placeId}'")
                // Use the original Firebase key stored in placeId, fallback to placeType, then address
                when {
                    !favoriteLocation.placeId.isNullOrEmpty() -> {
                        // Use the original Firebase key for removal
                        Log.d("PassengerHomeScreen", "Using placeId '${favoriteLocation.placeId}' for removal")
                        viewModel.removePlace(favoriteLocation.placeId)
                    }
                    !favoriteLocation.placeType.isNullOrEmpty() -> {
                        // Fallback to placeType
                        Log.d("PassengerHomeScreen", "Using placeType '${favoriteLocation.placeType}' for removal")
                        viewModel.removePlace(favoriteLocation.placeType)
                    }
                    else -> {
                        // Final fallback: remove by address
                        Log.d("PassengerHomeScreen", "Using address '${favoriteLocation.address}' for removal")
                        viewModel.removePlaceByAddress(favoriteLocation.address)
                    }
                }
            },
            onRemoveHomeLocation = { homeLocation ->
                // Remove the home location
                Log.d("PassengerHomeScreen", "Removing home location: ${homeLocation.address}")
                viewModel.removePlace("Home")
            },
            disableAutoPOISelection = (uiState.isSelectingHomeLocation || uiState.isSelectingFavoriteLocation || uiState.isSelectingPickupLocation).also { disable ->
                Log.d("PassengerHomeScreen", "disableAutoPOISelection set to: $disable (isSelectingHome: ${uiState.isSelectingHomeLocation}, isSelectingFavorite: ${uiState.isSelectingFavoriteLocation}, isSelectingPickup: ${uiState.isSelectingPickupLocation})")
            },
            isInSelectionMode = uiState.isSelectingHomeLocation || uiState.isSelectingFavoriteLocation || uiState.isSelectingPickupLocation,
            isHomeSelectionMode = uiState.isSelectingHomeLocation,
            onSetHomeLocation = { homeLocation ->
                Log.d("PassengerHomeScreen", "Setting home location: ${homeLocation.address}")
                viewModel.setHomeLocationFromMap(MapboxPoint.fromLngLat(homeLocation.coordinates.longitude, homeLocation.coordinates.latitude))
            },
            routeInfo = when {
                uiState.currentBooking?.status == BookingStatus.IN_PROGRESS -> uiState.driverRoute
                uiState.currentBooking?.status == BookingStatus.ACCEPTED -> uiState.driverRoute
                uiState.currentBooking?.status == BookingStatus.DRIVER_ARRIVED -> uiState.driverRoute
                uiState.currentBooking == null -> uiState.passengerRoute
                else -> null // Don't show route for other booking states (PENDING, FINDING_DRIVER, etc.)
            },
            onMapClick = { latLng ->
                Log.d("PassengerHomeScreen", "Map click handler - isSelectingFavorite: ${uiState.isSelectingFavoriteLocation}, isSelectingHome: ${uiState.isSelectingHomeLocation}")

                // First check if click is near a custom landmark (within 200 meters)
                val clickedLandmark = uiState.customLandmarks.firstOrNull { landmark ->
                    val landmarkPoint = com.mapbox.geojson.Point.fromLngLat(landmark.longitude, landmark.latitude)
                    val distance = calculateDistance(latLng, landmarkPoint)
                    Log.d("PassengerHomeScreen", "Landmark '${landmark.name}' at ${landmark.latitude}, ${landmark.longitude} is ${distance}m away")
                    distance < 200.0 // 200 meters threshold
                }

                when {
                    // Handle landmark clicks first (highest priority)
                    clickedLandmark != null -> {
                        Log.d("PassengerHomeScreen", "Landmark clicked: ${clickedLandmark.name}")
                        val cleanName = clickedLandmark.name.split(" - ₱").firstOrNull() ?: clickedLandmark.name
                        onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                            id = clickedLandmark.id,
                            name = cleanName,
                            point = com.mapbox.geojson.Point.fromLngLat(clickedLandmark.longitude, clickedLandmark.latitude),
                            address = clickedLandmark.name, // Keep original name with fare for fare calculation
                            rating = null,
                            userRatingsTotal = null,
                            types = listOf("custom_landmark"),
                            phoneNumber = null,
                            websiteUri = null,
                            isOpen = null,
                            openingHours = null
                        ))
                    }
                    uiState.isSelectingHomeLocation -> {
                        // Handle home location selection - show popup with "Set as Home" button
                        Log.d("PassengerHomeScreen", "Processing home location selection")
                        onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                            id = "home_selection_${latLng.latitude()}_${latLng.longitude()}",
                            name = "Selected Location",
                            point = latLng,
                            address = "Lat: ${String.format("%.6f", latLng.latitude())}, Lng: ${String.format("%.6f", latLng.longitude())}",
                            rating = null,
                            userRatingsTotal = null,
                            types = emptyList(),
                            phoneNumber = null,
                            websiteUri = null,
                            isOpen = null,
                            openingHours = null
                        ))
                    }
                    uiState.isSelectingFavoriteLocation -> {
                        // Handle favorite location selection
                        Log.d("PassengerHomeScreen", "Processing favorite location selection")
                        viewModel.setFavoriteLocationFromMap(latLng)
                    }
                    uiState.isSelectingPickupLocation -> {
                        // Handle pickup location selection
                        Log.d("PassengerHomeScreen", "Processing pickup location selection")
                        viewModel.setPickupLocationFromMap(latLng)
                    }
                    else -> {
                        // Handle general map clicks - set destination (keep current pickup)
                        val destGeoPoint = com.google.firebase.firestore.GeoPoint(latLng.latitude(), latLng.longitude())

                        // PRIORITY 1: Check if clicking near an admin destination (within 500m)
                        val nearbyDestination = uiState.customLandmarks.minByOrNull { landmark ->
                            val landmarkLat = landmark.latitude
                            val landmarkLng = landmark.longitude
                            val R = 6371000.0 // Earth radius in meters
                            val dLat = Math.toRadians(latLng.latitude() - landmarkLat)
                            val dLon = Math.toRadians(latLng.longitude() - landmarkLng)
                            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                    Math.cos(Math.toRadians(landmarkLat)) *
                                    Math.cos(Math.toRadians(latLng.latitude())) *
                                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                            R * c // Distance in meters
                        }?.takeIf { landmark ->
                            val landmarkLat = landmark.latitude
                            val landmarkLng = landmark.longitude
                            val R = 6371000.0
                            val dLat = Math.toRadians(latLng.latitude() - landmarkLat)
                            val dLon = Math.toRadians(latLng.longitude() - landmarkLng)
                            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                    Math.cos(Math.toRadians(landmarkLat)) *
                                    Math.cos(Math.toRadians(latLng.latitude())) *
                                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                            val distance = R * c
                            Log.d("PassengerHomeScreen", "Distance to ${landmark.name}: ${distance}m")
                            distance < 500.0 // 500 meters threshold for admin destinations
                        }

                        // Determine destination address and display name
                        val destinationAddress: String
                        val destinationDisplayName: String

                        if (nearbyDestination != null) {
                            // Use admin destination name (with fare if present)
                            Log.d("PassengerHomeScreen", "Using nearby admin destination: ${nearbyDestination.name}")
                            destinationAddress = nearbyDestination.name
                            // Extract clean name for display (remove fare)
                            destinationDisplayName = nearbyDestination.name.split(" - ₱").firstOrNull() ?: nearbyDestination.name
                        } else {
                            // PRIORITY 2: Check if destination is in a boundary (as fallback)
                            val destBoundaryName = com.rj.islamove.utils.BoundaryFareUtils.determineBoundary(
                                destGeoPoint,
                                zoneBoundaryRepository // Use the repository to get proper boundary detection
                            )
                            destinationAddress = destBoundaryName ?: "Selected Location (${String.format("%.6f", latLng.latitude())}, ${String.format("%.6f", latLng.longitude())})"
                            destinationDisplayName = destBoundaryName ?: "Selected Location"
                        }

                        // FIXED: Don't set destination here - only show dialog
                        // Route calculation will happen when user clicks "Book Ride" button
                        // This prevents unwanted route triggering on every map click

                        // Show booking dialog for the selected location
                        onSelectedPlaceForBookingChange(MapboxPlaceDetails(
                            id = "map_click_${latLng.latitude()}_${latLng.longitude()}",
                            name = destinationDisplayName,
                            point = latLng,
                            address = destinationAddress,
                            rating = null,
                            userRatingsTotal = null,
                            types = emptyList(),
                            phoneNumber = null,
                            websiteUri = null,
                            isOpen = null,
                            openingHours = null
                        ))
                    }
                }
            },
            onPlaceSelected = { placeDetails ->
                // Handle different selection modes
                when {
                    uiState.isSelectingPickupLocation -> {
                        // Handle pickup location selection
                        Log.d("PassengerHomeScreen", "Setting pickup location from POI: ${placeDetails.address}")
                        viewModel.setPickupLocationFromMap(placeDetails.point)
                    }
                    uiState.isSelectingHomeLocation || uiState.isSelectingFavoriteLocation -> {
                        // Already handled in onPOIClickForSelection
                    }
                    else -> {
                        // Normal POI selection
                        // FIXED: Don't set destination here - only show dialog
                        // Route calculation will happen when user clicks "Book Ride" button
                        // This prevents unwanted route triggering on every POI selection

                        // Show the booking dialog for the selected POI
                        onSelectedPlaceForBookingChange(placeDetails)
                    }
                }
            },
            onBookRide = { bookingLocation, passengerComment ->
                // Only handle booking if not in special selection modes
                if (!uiState.isSelectingHomeLocation && !uiState.isSelectingFavoriteLocation && !uiState.isSelectingPickupLocation) {
                    // Set destination and directly book the ride (show "Looking for driver..." state)
                    val destination = BookingLocation(
                        address = bookingLocation.address,
                        coordinates = com.google.firebase.firestore.GeoPoint(bookingLocation.coordinates.latitude, bookingLocation.coordinates.longitude),
                        placeId = bookingLocation.placeId
                    )

                    // Directly book the ride with comment - this will show "Looking for driver..." state
                    viewModel.bookRideToLocationWithComment(destination, passengerComment)
                }
            },
            onCalculateFare = { destinationLocation ->
                // Calculate fare using ViewModel's dedicated POI fare calculation method
                viewModel.calculateFareForPOI(destinationLocation)
            },
            passengerDiscountPercentage = uiState.currentUser?.discountPercentage,
            centerOnLocationTrigger = 0L,
            currentBookingStatus = uiState.currentBooking?.status,
            currentBooking = uiState.currentBooking,
            centerOnPassenger = false // Keep following driver/route, not passenger
        )

        // Pickup selection banner
        if (uiState.isSelectingPickupLocation) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = IslamovePrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap on map to select pickup location",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.cancelPickupLocationSelection()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Top-Right Corner: Quick access buttons and Driver count indicator
        if (uiState.currentBooking == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Home address quick access button (if home is saved)
                val homePlace = uiState.savedPlaces.find { it.placeType == "Home" }
                if (homePlace != null) {
                    FloatingActionButton(
                        onClick = {
                            // Show booking dialog for home location
                            onSelectedPlaceForBookingChange(homePlace.toMapboxPlaceDetails())
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = IslamoveSecondary,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Go Home",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }



        // Driver count indicator positioned in top-left
        if (uiState.currentBooking == null || uiState.currentBooking?.status == BookingStatus.COMPLETED) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.onlineDriverCount > 0)
                        IslamovePrimary
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${uiState.onlineDriverCount}",
                        fontSize = 16.sp,
                        color = if (uiState.onlineDriverCount > 0) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.onlineicon),
                        contentDescription = "Online drivers",
                        modifier = Modifier.size(16.dp),
                        tint = if (uiState.onlineDriverCount > 0) Color.White else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Draggable Bottom Sheet (slide down only)
        if ((uiState.currentBooking == null || uiState.currentBooking?.status == BookingStatus.COMPLETED) && selectedPlaceForBooking == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { currentOffset.toDp() })
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDrag = { _, dragAmount ->
                                val newOffset = bottomSheetOffset - dragAmount.y
                                // Only allow sliding down (decreasing height)
                                bottomSheetOffset = max(minHeight, min(maxHeight, newOffset))
                            },
                            onDragEnd = {
                                isDragging = false
                                // Snap to nearest position
                                val snapTarget = when {
                                    bottomSheetOffset < (minHeight + maxHeight) / 2 -> minHeight
                                    else -> maxHeight
                                }
                                targetOffset = snapTarget
                            }
                        )
                    },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(5.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(2.5.dp)
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxHeight()
                    ) {
                    // Greeting with time-based message and actual user name
                    val currentTime = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val greeting = when (currentTime) {
                        in 0..11 -> "Good morning"
                        in 12..17 -> "Good afternoon"
                        else -> "Good evening"
                    }
                    val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"

                    Text(
                        text = "$greeting, $userName!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Main search bar with magnifying glass as per spec
                    var searchText by remember { mutableStateOf("") }
                    android.util.Log.d("MapsHeight", "Search field rendered in bottom sheet")
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            val isActive = it.isNotEmpty()
                            android.util.Log.d("MapsHeight", "Search text changed: '$it', setting isSearchActive to: $isActive")
                            onSearchActiveChange(isActive)
                            if (it.isNotBlank()) {
                                viewModel.searchLocations(it)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        placeholder = { Text("Where to?") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (searchText.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        searchText = ""
                                        onSearchActiveChange(false)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IslamovePrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    // Location suggestions
                    if ((uiState.locationSuggestions.isNotEmpty() && searchText.isNotEmpty()) ||
                        (uiState.homeAddress != null && searchText.isNotEmpty())) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Show home address suggestion first if available and search matches
                            if (uiState.homeAddress != null &&
                                searchText.isNotEmpty() &&
                                (uiState.homeAddress.address.contains(searchText, ignoreCase = true) ||
                                 "home".contains(searchText, ignoreCase = true))) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Show booking dialog for home location
                                                onSelectedPlaceForBookingChange(uiState.homeAddress.toMapboxPlaceDetails())
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = null,
                                            tint = IslamovePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Home",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = uiState.homeAddress.address,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            items(uiState.locationSuggestions.take(5)) { location ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setDestination(location)
                                            // Convert to MapboxPlaceDetails for POI selection
                                            val placeDetails = MapboxPlaceDetails(
                                                id = "suggestion_${System.currentTimeMillis()}",
                                                name = location.address,
                                                point = MapboxPoint.fromLngLat(location.coordinates.longitude, location.coordinates.latitude),
                                                address = location.address,
                                                rating = null,
                                                userRatingsTotal = null,
                                                types = listOf("destination"),
                                                phoneNumber = null,
                                                websiteUri = null,
                                                isOpen = null,
                                                openingHours = null
                                            )
                                            onSelectedPlaceForBookingChange(placeDetails)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = IslamovePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = location.address,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Quick action buttons as per design spec
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Home button (🏠)
                            OutlinedButton(
                                onClick = {
                                    if (uiState.homeAddress != null) {
                                        // Show booking dialog for home location
                                        onSelectedPlaceForBookingChange(uiState.homeAddress.toMapboxPlaceDetails())
                                    } else {
                                        // Start home location selection mode to use GoogleMapsComponent style
                                        viewModel.startHomeLocationSelection()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = when {
                                    uiState.isSelectingHomeLocation -> BorderStroke(2.dp, IslamovePrimary) // Active selection mode
                                    uiState.homeAddress != null -> BorderStroke(2.dp, IslamovePrimary) // Has saved home
                                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) // Default
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (uiState.isSelectingHomeLocation) {
                                        IslamovePrimary.copy(alpha = 0.1f) // Light background when selecting
                                    } else {
                                        Color.Transparent
                                    }
                                )
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = when {
                                        uiState.isSelectingHomeLocation -> IslamovePrimary // Active selection
                                        uiState.homeAddress != null -> IslamovePrimary // Has saved home
                                        else -> MaterialTheme.colorScheme.onSurface // Default
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        uiState.isSelectingHomeLocation -> "Selecting..." // Active selection
                                        uiState.homeAddress != null -> "Home" // Has saved home
                                        else -> "Add Home" // Default
                                    },
                                    color = when {
                                        uiState.isSelectingHomeLocation -> IslamovePrimary // Active selection
                                        uiState.homeAddress != null -> IslamovePrimary // Has saved home
                                        else -> MaterialTheme.colorScheme.onSurface // Default
                                    }
                                )
                            }

                            // Favorites button
                            OutlinedButton(
                                onClick = {
                                    viewModel.startFavoriteLocationSelection()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = if (uiState.isSelectingFavoriteLocation) {
                                    BorderStroke(2.dp, IslamovePrimary) // Active selection mode
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) // Default
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (uiState.isSelectingFavoriteLocation) {
                                        IslamovePrimary.copy(alpha = 0.1f) // Light background when selecting
                                    } else {
                                        Color.Transparent
                                    }
                                )
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (uiState.isSelectingFavoriteLocation) {
                                        IslamovePrimary // Active selection
                                    } else {
                                        MaterialTheme.colorScheme.onSurface // Default
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.isSelectingFavoriteLocation) {
                                        "Selecting..." // Active selection
                                    } else {
                                        "Favorites" // Default
                                    },
                                    color = if (uiState.isSelectingFavoriteLocation) {
                                        IslamovePrimary // Active selection
                                    } else {
                                        MaterialTheme.colorScheme.onSurface // Default
                                    }
                                )
                            }
                        }


                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Book ride button (when destination is selected)
                    if (searchText.isNotEmpty()) {
                        Button(
                            onClick = {
                                // Try to find the exact location from suggestions first
                                val matchingSuggestion = uiState.locationSuggestions.find {
                                    it.address.contains(searchText, ignoreCase = true)
                                }

                                val searchDestination = if (matchingSuggestion != null) {
                                    // Use the actual coordinates from the matching suggestion
                                    MapboxPlaceDetails(
                                        id = "suggestion_destination_${System.currentTimeMillis()}",
                                        name = searchText,
                                        point = MapboxPoint.fromLngLat(
                                            matchingSuggestion.coordinates.longitude,
                                            matchingSuggestion.coordinates.latitude
                                        ),
                                        address = matchingSuggestion.address,
                                        rating = null,
                                        userRatingsTotal = null,
                                        types = listOf("destination"),
                                        phoneNumber = null,
                                        websiteUri = null,
                                        isOpen = null,
                                        openingHours = null
                                    )
                                } else {
                                    // Fallback to default coordinates
                                    MapboxPlaceDetails(
                                        id = "search_destination_${System.currentTimeMillis()}",
                                        name = searchText,
                                        point = MapboxPoint.fromLngLat(125.5760264, 10.0195507), // Default to San Jose center
                                        address = searchText,
                                        rating = null,
                                        userRatingsTotal = null,
                                        types = listOf("destination"),
                                        phoneNumber = null,
                                        websiteUri = null,
                                        isOpen = null,
                                        openingHours = null
                                    )
                                }
                                onSelectedPlaceForBookingChange(searchDestination)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IslamovePrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Book Ride",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    } // Close inner Column
                } // Close outer Column
            }
        }

        // No top banner needed - detailed driver card shows all info

        // Bottom cards for specific booking states
        android.util.Log.d("PassengerHomeScreen", "MapsContent - currentBooking: ${uiState.currentBooking?.id}, status: ${uiState.currentBooking?.status}")
        if (uiState.currentBooking != null && uiState.currentBooking?.status != BookingStatus.COMPLETED) {
            android.util.Log.d("PassengerHomeScreen", "Showing booking UI for status: ${uiState.currentBooking?.status}")
            when (uiState.currentBooking?.status) {
                BookingStatus.PENDING, BookingStatus.LOOKING_FOR_DRIVER -> {
                    android.util.Log.d("PassengerHomeScreen", "Rendering 'Looking for driver' card")
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Looking for driver...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = IslamovePrimary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            Text(
                                text = "Going to: ${uiState.currentBooking?.destination?.address}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedButton(
                                onClick = {
                                    viewModel.cancelBooking("Cancelled by passenger")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Cancel Booking")
                            }
                        }
                    }
                }
                BookingStatus.ACCEPTED, BookingStatus.DRIVER_ARRIVED, BookingStatus.IN_PROGRESS -> {
                    DriverFoundCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        booking = uiState.currentBooking!!,
                        driver = uiState.assignedDriver,
                        eta = uiState.driverEta,
                        route = uiState.driverRoute,
                        uiState = uiState,
                        onCallDriver = {
                            val driverPhoneNumber = uiState.assignedDriver?.phoneNumber
                            if (!driverPhoneNumber.isNullOrEmpty()) {
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CALL_PHONE
                                    ) == PackageManager.PERMISSION_GRANTED -> {
                                        // Permission already granted, make the call
                                        try {
                                            val intent = Intent(ACTION_CALL).apply {
                                                data = Uri.parse("tel:$driverPhoneNumber")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                            android.util.Log.d("DriverCall", "Initiating call to driver: $driverPhoneNumber")
                                        } catch (e: Exception) {
                                            android.util.Log.e("DriverCall", "Failed to initiate call", e)
                                            // Try with ACTION_DIAL as fallback
                                            try {
                                                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:$driverPhoneNumber")
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(dialIntent)
                                                android.util.Log.d("DriverCall", "Opened dialer instead")
                                            } catch (dialException: Exception) {
                                                android.util.Log.e("DriverCall", "Failed to open dialer as well", dialException)
                                            }
                                        }
                                    }
                                    else -> {
                                        // Store number and request permission
                                        android.util.Log.d("DriverCall", "Requesting CALL_PHONE permission for $driverPhoneNumber")
                                        pendingPhoneNumber = driverPhoneNumber
                                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                    }
                                }
                            } else {
                                android.util.Log.w("DriverCall", "Driver phone number not available")
                            }
                        },
                        onCancelBooking = {
                            viewModel.cancelBooking("Cancelled by passenger")
                        },
                        onReportDriver = {
                            viewModel.showReportDriverModal()
                        }
                    )
                }
                else -> {
                    // Other states handled by top banner only
                }
            }
        }
    } // Close inner Box (Container for map and bottom sheet)

    // Home Location Selection - Small floating instruction
    if (uiState.isSelectingHomeLocation) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentSize(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = IslamovePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tap to set home",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { viewModel.cancelHomeLocationSelection() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Favorite Location Selection - Small floating instruction
    if (uiState.isSelectingFavoriteLocation) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentSize(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = IslamovePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tap to add favorite",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { viewModel.cancelFavoriteLocationSelection() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }

    } // Close outer Box

}

@Composable
private fun RidesContent(
    uiState: PassengerHomeUiState,
    viewModel: PassengerHomeViewModel,
    onNavigateToTripDetails: (String) -> Unit
) {
    android.util.Log.d("PassengerHomeScreen", "RidesContent - currentBooking: ${uiState.currentBooking?.id}, status: ${uiState.currentBooking?.status}")
    // Load persistent ride history when this composable is first displayed
    LaunchedEffect(Unit) {
        viewModel.loadRideHistory()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Rides",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        val currentTrips = buildList {
            // Always include currentBooking if it exists and is active
            uiState.currentBooking?.let { currentBooking ->
                android.util.Log.d("PassengerHomeScreen", "RidesContent - Found currentBooking with status: ${currentBooking.status}")
                if (currentBooking.status in listOf(
                    BookingStatus.PENDING,
                    BookingStatus.LOOKING_FOR_DRIVER,
                    BookingStatus.SCHEDULED,
                    BookingStatus.ACCEPTED,
                    BookingStatus.DRIVER_ARRIVING,
                    BookingStatus.DRIVER_ARRIVED,
                    BookingStatus.IN_PROGRESS
                )) {
                    android.util.Log.d("PassengerHomeScreen", "RidesContent - Adding currentBooking to active trips list")
                    add(currentBooking)
                }
            }

            // Add other active trips from rideHistory (avoid duplicates)
            uiState.rideHistory.filter { trip ->
                trip.status in listOf(
                    BookingStatus.PENDING,
                    BookingStatus.LOOKING_FOR_DRIVER,
                    BookingStatus.SCHEDULED,
                    BookingStatus.ACCEPTED,
                    BookingStatus.DRIVER_ARRIVING,
                    BookingStatus.DRIVER_ARRIVED,
                    BookingStatus.IN_PROGRESS
                ) && trip.id != uiState.currentBooking?.id // Avoid duplicates
            }.forEach { trip ->
                add(trip)
            }
        }.sortedWith(compareByDescending<Booking> { trip ->
            when (trip.status) {
                BookingStatus.IN_PROGRESS -> 5
                BookingStatus.DRIVER_ARRIVED -> 4
                BookingStatus.DRIVER_ARRIVING -> 3
                BookingStatus.ACCEPTED -> 2
                BookingStatus.PENDING, BookingStatus.LOOKING_FOR_DRIVER, BookingStatus.SCHEDULED -> 1
                else -> 0
            }
        })
        
        val completedTrips = uiState.rideHistory.filter { trip ->
            trip.status == BookingStatus.COMPLETED
        }.sortedByDescending { it.completionTime ?: it.requestTime }

        // Debug logging for completed trips
        android.util.Log.d("PassengerHomeScreen", "RidesContent - completedTrips: ${completedTrips.size}")
        completedTrips.forEach { trip ->
            android.util.Log.d("PassengerHomeScreen", "Completed trip: ${trip.id} - ${trip.status}")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (currentTrips.isEmpty() && completedTrips.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No rides yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Your rides will appear here",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Current/Active trips section
            if (currentTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Current Trip",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(currentTrips) { ride ->
                    CurrentTripCard(ride = ride, onClick = { onNavigateToTripDetails(ride.id) })
                }
                
                if (completedTrips.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            // Completed trips section
            if (completedTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Trip History",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(completedTrips) { ride ->
                    RideCard(ride = ride, onClick = { onNavigateToTripDetails(ride.id) })
                }
            }
        }
    }
}

@Composable
private fun RideCard(ride: Booking, onClick: () -> Unit = {}) {
    // Format date and time like in the PNG
    val dateFormat = when {
        isToday(ride.requestTime) -> SimpleDateFormat("'Today', h:mm a", Locale.getDefault())
        isYesterday(ride.requestTime) -> SimpleDateFormat("'Yesterday', h:mm a", Locale.getDefault())
        else -> SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular vehicle icon (using onlineicon.png)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = R.drawable.onlineicon,
                    contentDescription = "Vehicle",
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Trip details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Route summary
                Text(
                    text = formatRoute(ride.pickupLocation.address, ride.destination.address),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date and time
                Text(
                    text = dateFormat.format(Date(ride.requestTime)),
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }

            // Price and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Price
                Text(
                    text = "₱${kotlin.math.floor(ride.actualFare ?: ride.fareEstimate.totalEstimate).toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status
                val (statusColor, statusText) = when (ride.status) {
                    BookingStatus.COMPLETED -> Color(0xFF4CAF50) to "Completed"
                    BookingStatus.CANCELLED -> Color(0xFFD32F2F) to "Cancelled"
                    BookingStatus.IN_PROGRESS -> Color(0xFF2196F3) to "In Progress"
                    BookingStatus.EXPIRED -> Color(0xFF666666) to "Expired"
                    else -> Color(0xFF666666) to ride.status.name
                }

                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun CurrentTripCard(ride: Booking, onClick: () -> Unit = {}) {
    // Format date and time like in the PNG
    val dateFormat = when {
        isToday(ride.requestTime) -> SimpleDateFormat("'Today', h:mm a", Locale.getDefault())
        isYesterday(ride.requestTime) -> SimpleDateFormat("'Yesterday', h:mm a", Locale.getDefault())
        else -> SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)), // Light blue background for active trip
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular vehicle icon (using onlineicon.png) with animated background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF)), // Blue background for active trip
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = R.drawable.onlineicon,
                    contentDescription = "Vehicle",
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Trip details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Route summary
                Text(
                    text = formatRoute(ride.pickupLocation.address, ride.destination.address),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date and time
                Text(
                    text = dateFormat.format(Date(ride.requestTime)),
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }

            // Price and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Price
                Text(
                    text = "₱${kotlin.math.floor(ride.fareEstimate.totalEstimate).toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status with active color
                val (statusColor, statusText) = when (ride.status) {
                    BookingStatus.IN_PROGRESS -> Color(0xFF007AFF) to "In Progress"
                    BookingStatus.DRIVER_ARRIVED -> Color(0xFF34C759) to "Driver Arrived"
                    BookingStatus.DRIVER_ARRIVING -> Color(0xFFFF9500) to "Driver Coming"
                    BookingStatus.ACCEPTED -> Color(0xFF007AFF) to "Driver Assigned"
                    BookingStatus.PENDING -> Color(0xFFFF9500) to "Finding Driver"
                    BookingStatus.LOOKING_FOR_DRIVER -> Color(0xFFFF9500) to "Looking for Driver"
                    BookingStatus.SCHEDULED -> Color(0xFF5856D6) to "Scheduled"
                    else -> Color(0xFF007AFF) to "Active"
                }

                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    onNavigateToProfile: () -> Unit,
    onNavigateToReviews: () -> Unit,
    onNavigateToDriverDocuments: (String) -> Unit,
    onNavigateToHelpSupport: () -> Unit,
    onSignOut: () -> Unit,
    uiState: PassengerHomeUiState = PassengerHomeUiState(),
    viewModel: PassengerHomeViewModel
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Debug logging for ProfileContent
    android.util.Log.d("ProfileContent", "ProfileContent recomposing, currentUser: ${uiState.currentUser?.uid}, isActive: ${uiState.currentUser?.isActive}")

    // Force refresh user data when ProfileContent is first composed
    LaunchedEffect(Unit) {
        android.util.Log.d("ProfileContent", "ProfileContent LaunchedEffect - calling refreshUserData")
        viewModel.refreshUserData()
    }

    // Inline editing states
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf("") }
    var editingEmail by remember { mutableStateOf("") }
    var editingPhone by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var profileRefreshTrigger by remember { mutableStateOf(0) }
    var currentDisplayName by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.displayName ?: "User") }
    var currentPhoneNumber by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.phoneNumber ?: "") }


    
    // Student ID dialog state
    var showStudentIdDialog by remember { mutableStateOf(false) }
    var studentIdNumber by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var studentIdUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingStudentId by remember { mutableStateOf(false) }
    var showStudentIdImagePicker by remember { mutableStateOf(false) }

    
    // Store camera image URI separately
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Upload image to Cloudinary using ViewModel
    fun uploadImageToCloudinary(uri: Uri) {
        try {
            isUploading = true
            android.util.Log.d("ProfileImage", "Starting Cloudinary upload")
            viewModel.uploadProfileImage(uri)
            isUploading = false
        } catch (e: Exception) {
            android.util.Log.e("ProfileImage", "Upload failed", e)
            isUploading = false
        }
    }

    // Function to upload image using ViewModel
    fun saveImageLocally(uri: Uri) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.e("ProfileImage", "User not authenticated")
            return
        }

        // Upload to Cloudinary using ViewModel
        uploadImageToCloudinary(uri)
    }

    // Load saved profile image on startup and refresh user data
    LaunchedEffect(Unit) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val sharedPrefs = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            val savedImagePath = sharedPrefs.getString("profile_image_path", null)
            if (savedImagePath != null) {
                val savedFile = File(savedImagePath)
                if (savedFile.exists()) {
                    profileImageUri = Uri.fromFile(savedFile)
                    android.util.Log.d("ProfileImage", "Loaded saved profile image: $savedImagePath")
                }
            }
        }
    }

    // Load phone number from Firestore
    LaunchedEffect(Unit, profileRefreshTrigger) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val phoneNumber = document.getString("phoneNumber")
                        if (phoneNumber != null) {
                            currentPhoneNumber = phoneNumber
                            android.util.Log.d("ProfileUpdate", "Phone number loaded from Firestore: $phoneNumber")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ProfileUpdate", "Failed to load phone number from Firestore", e)
                }
        }
    }

    // Refresh user data when profileRefreshTrigger changes
    LaunchedEffect(profileRefreshTrigger) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        currentUser?.reload()?.addOnSuccessListener {
            android.util.Log.d("ProfileUpdate", "User data refreshed")
        }?.addOnFailureListener { e ->
            android.util.Log.e("ProfileUpdate", "Failed to refresh user data", e)
        }
    }
    
    // Create a function to create temporary file URI for camera
    fun createImageUri(): Uri {
        val imageFile = File(context.cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
        // Make sure the parent directories exist
        imageFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            // Photo was taken successfully, use the camera URI
            profileImageUri = cameraImageUri
            android.util.Log.d("ProfileImage", "Camera photo taken successfully: $cameraImageUri")
            // Save locally
            saveImageLocally(cameraImageUri!!)
        } else {
            android.util.Log.e("ProfileImage", "Camera photo failed or URI is null")
        }
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            profileImageUri = uri
            android.util.Log.d("ProfileImage", "Gallery image selected: $uri")
            // Save locally
            saveImageLocally(uri)
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri()
            cameraImageUri = uri // Store the URI for later use
            android.util.Log.d("ProfileImage", "Camera URI created: $uri")
            cameraLauncher.launch(uri)
        } else {
            android.util.Log.e("ProfileImage", "Camera permission denied")
        }
    }

    // Student ID gallery launcher
    val studentIdGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            studentIdUri = uri
            android.util.Log.d("StudentId", "Student ID image selected: $uri")
        }
    }

    // Student ID camera launcher
    var studentIdCameraUri by remember { mutableStateOf<Uri?>(null) }
    val studentIdCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && studentIdCameraUri != null) {
            studentIdUri = studentIdCameraUri
            android.util.Log.d("StudentId", "Student ID photo taken successfully: $studentIdCameraUri")
        } else {
            android.util.Log.e("StudentId", "Student ID photo failed or URI is null")
        }
    }

    // Student ID camera permission launcher
    val studentIdCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri()
            studentIdCameraUri = uri
            android.util.Log.d("StudentId", "Student ID camera URI created: $uri")
            studentIdCameraLauncher.launch(uri)
        } else {
            android.util.Log.e("StudentId", "Student ID camera permission denied")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // User profile section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture with edit badge - clickable
            Box(
                modifier = Modifier.clickable { showImagePicker = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = if (profileImageUri != null) Color.Transparent else Color(0xFFE8B68C),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Check for Cloudinary URL first, then fall back to local URI
                    if (!uiState.currentUser?.profileImageUrl.isNullOrEmpty()) {
                        // Show Cloudinary image from Firestore
                        android.util.Log.d("ProfileContent", "Loading profile image from Cloudinary: ${uiState.currentUser?.profileImageUrl}")
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(uiState.currentUser?.profileImageUrl)
                                .crossfade(true)
                                .listener(
                                    onError = { _, result ->
                                        android.util.Log.e("ProfileContent", "Error loading profile image: ${result.throwable.message}")
                                        android.util.Log.e("ProfileContent", "URL was: ${uiState.currentUser?.profileImageUrl}")
                                    },
                                    onSuccess = { _, _ ->
                                        android.util.Log.d("ProfileContent", "Profile image loaded successfully from Cloudinary")
                                    }
                                )
                                .build(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.1f)),
                            contentScale = ContentScale.Crop
                        )
                    } else if (profileImageUri != null) {
                        // Show selected image using AsyncImage (local file)
                        AsyncImage(
                            model = profileImageUri,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.1f)),
                            contentScale = ContentScale.Crop,
                            onError = {
                                // Log error for debugging
                                android.util.Log.e("ProfileImage", "Error loading image: ${it.result.throwable}")
                            },
                            placeholder = painterResource(android.R.drawable.ic_menu_camera)
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                }
                // Edit badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .background(
                            color = IslamovePrimary,
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User name (read-only)
            Text(
                text = currentDisplayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rating and trip count
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFA500)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = uiState.passengerRatingStats?.let { stats ->
                        if (stats.totalRatings > 0) {
                            String.format("%.2f", stats.overallRating)
                        } else {
                            "New"
                        }
                    } ?: "New",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = uiState.passengerRatingStats?.let { stats ->
                        " • ${if (stats.totalRatings == 0) "No" else "${stats.totalRatings}"} trip${if (stats.totalRatings == 1) "" else "s"}"
                    } ?: " • No trips",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account status indicator - Force recomposition with key
            uiState.currentUser?.let { user ->
                // Debug logging
                android.util.Log.d("ProfileContent", "Rendering user status: active=${user.isActive}, uid=${user.uid}, updatedAt=${user.updatedAt}")

                // Using key to force recomposition when user status changes
                key(user.isActive, user.updatedAt, user.discountPercentage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (user.isActive) "Active" else "Blocked",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (user.isActive) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                            modifier = Modifier
                                .background(
                                    color = if (user.isActive) Color(0xFFE8F5E8) else Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        Text(
                            text = " • ",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = when (user.discountPercentage) {
                                null -> "None"
                                20 -> "20%"
                                50 -> "50%"
                                else -> "${user.discountPercentage}%"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (user.discountPercentage) {
                                20 -> Color(0xFF4CAF50)
                                50 -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                            modifier = Modifier
                                .background(
                                    color = when (user.discountPercentage) {
                                        20 -> Color(0xFFE8F5E8)
                                        50 -> Color(0xFFE3F2FD)
                                        else -> Color(0xFFF5F5F5)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Personal Information section
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Personal Information",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Email
            ProfileMenuItem(
                icon = Icons.Default.Email,
                title = "Email",
                subtitle = FirebaseAuth.getInstance().currentUser?.email ?: "Not provided",
                onClick = {
                    showEmailDialog = true
                }
            )

            // Phone Number
            ProfileMenuItem(
                icon = Icons.Default.Phone,
                title = "Phone Number",
                subtitle = currentPhoneNumber.ifEmpty { "Not provided" },
                onClick = {
                    editingPhone = currentPhoneNumber
                    showPhoneDialog = true
                }
            )

            // Reviews Section
            uiState.passengerRatingStats?.let { ratingStats ->
                if (ratingStats.totalRatings > 0) {
                    Spacer(modifier = Modifier.height(16.dp))

                    ProfileMenuItem(
                        icon = Icons.Default.Star,
                        title = "My Reviews",
                        subtitle = "${ratingStats.totalRatings} reviews • ${String.format("%.1f", ratingStats.overallRating)} stars",
                        onClick = { onNavigateToReviews() }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings section
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Privacy & Security
            ProfileMenuItem(
                icon = Icons.Default.Lock,
                title = "Privacy & Security",
                subtitle = "Password",
                onClick = {
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    showPasswordDialog = true
                }
            )

            // ID Verification
            ProfileMenuItem(
                icon = Icons.Default.Star,
                title = "ID Verification",
                subtitle = uiState.currentUser?.studentDocument?.let { doc ->
                    when (doc.status) {
                        com.rj.islamove.data.models.DocumentStatus.APPROVED -> "Verified • ID approved"
                        com.rj.islamove.data.models.DocumentStatus.PENDING_REVIEW -> "Pending verification"
                        com.rj.islamove.data.models.DocumentStatus.REJECTED -> "Rejected • Resubmit required"
                        else -> "Upload ID for verification"
                    }
                } ?: "Upload ID for verification",
                onClick = {
                    uiState.currentUser?.uid?.let { userId ->
                        onNavigateToDriverDocuments(userId)
                    }
                }
            )

            // Help & Support
            ProfileMenuItem(
                icon = Icons.Default.Info,
                title = "Help & Support",
                subtitle = "FAQ, Report issues",
                onClick = { onNavigateToHelpSupport() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Logout button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Logout",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
    
    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Logout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to logout?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        showLogoutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Image picker dialog
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = {
                Text(
                    text = "Change Profile Picture",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Choose how you'd like to update your profile picture:")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Camera option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                showImagePicker = false
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Take a photo",
                            fontSize = 16.sp
                        )
                    }
                    
                    // Gallery option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                showImagePicker = false
                                galleryLauncher.launch("image/*")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Choose from gallery",
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Cancel")
                }
            },
            dismissButton = null
        )
    }

    // Name editing dialog
    // Name editing dialog removed - users cannot edit their name

    // Email view dialog (Read-only)
    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Email Address") },
            text = {
                Column {
                    Text(
                        text = FirebaseAuth.getInstance().currentUser?.email ?: "Not provided",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Email address cannot be changed for authentication security.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Phone number editing dialog
    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = {
                Text(
                    text = "Edit Phone Number",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingPhone,
                        onValueChange = { editingPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter your phone number in any format",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    updateMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = if (message.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingPhone.isNotBlank()) {
                            // Save the phone number exactly as entered by the user (no auto-formatting)
                            val phoneToSave = editingPhone.trim()
                            isUpdating = true
                            updateMessage = null

                            val user = FirebaseAuth.getInstance().currentUser
                            if (user != null) {
                                // Update Firestore directly (Firebase Auth phone number requires verification)
                                val firestore = FirebaseFirestore.getInstance()
                                val userDoc = firestore.collection("users").document(user.uid)

                                val updates = mapOf(
                                    "phoneNumber" to phoneToSave,
                                    "updatedAt" to System.currentTimeMillis()
                                )

                                userDoc.update(updates)
                                    .addOnSuccessListener {
                                        updateMessage = "Phone number updated successfully"
                                        isUpdating = false
                                        showPhoneDialog = false
                                        // Update local state immediately
                                        currentPhoneNumber = phoneToSave
                                        // Trigger UI refresh
                                        profileRefreshTrigger++
                                        android.util.Log.d("ProfileUpdate", "Phone number saved to Firestore (as entered): $phoneToSave")
                                    }
                                    .addOnFailureListener { e ->
                                        updateMessage = "Error saving phone number: ${e.message}"
                                        isUpdating = false
                                        android.util.Log.e("ProfileUpdate", "Phone number save failed", e)
                                    }
                            } else {
                                updateMessage = "User not authenticated"
                                isUpdating = false
                            }
                        } else {
                            updateMessage = "Please enter a phone number"
                        }
                    },
                    enabled = !isUpdating
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Password change dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                showCurrentPassword = false
                showNewPassword = false
                showConfirmPassword = false
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            },
            title = {
                Text(
                    text = "Change Password",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                Icon(
                                    imageVector = if (showCurrentPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showCurrentPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showCurrentPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showNewPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showConfirmPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Password must be at least 6 characters long",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    updateMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            currentPassword.isEmpty() -> {
                                updateMessage = "Please enter your current password"
                            }
                            newPassword.isEmpty() -> {
                                updateMessage = "Please enter a new password"
                            }
                            confirmPassword.isEmpty() -> {
                                updateMessage = "Please confirm your new password"
                            }
                            newPassword != confirmPassword -> {
                                updateMessage = "New passwords do not match"
                            }
                            newPassword.length < 6 -> {
                                updateMessage = "Password must be at least 6 characters long"
                            }
                            else -> {
                                isUpdating = true
                                updateMessage = null

                                val user = FirebaseAuth.getInstance().currentUser
                                val email = user?.email
                                if (email != null) {
                                    // Re-authenticate user first
                                    val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
                                    user.reauthenticate(credential)
                                        .addOnSuccessListener {
                                            // Update password
                                            user.updatePassword(newPassword)
                                                .addOnSuccessListener {
                                                    updateMessage = "Password updated successfully"
                                                    isUpdating = false
                                                    currentPassword = ""
                                                    newPassword = ""
                                                    confirmPassword = ""
                                                    android.util.Log.d("ProfileUpdate", "Password updated successfully")
                                                }
                                                .addOnFailureListener { e ->
                                                    updateMessage = "Error updating password: ${e.message}"
                                                    isUpdating = false
                                                    android.util.Log.e("ProfileUpdate", "Password update failed", e)
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            updateMessage = "Current password is incorrect"
                                            isUpdating = false
                                            android.util.Log.e("ProfileUpdate", "Re-authentication failed", e)
                                        }
                                } else {
                                    updateMessage = "User email not found"
                                    isUpdating = false
                                }
                            }
                        }
                    },
                    enabled = !isUpdating
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    showCurrentPassword = false
                    showNewPassword = false
                    showConfirmPassword = false
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }



    
    // Student ID Upload Dialog
    if (showStudentIdDialog) {
        AlertDialog(
            onDismissRequest = {
                showStudentIdDialog = false
                studentIdNumber = ""
                schoolName = ""
                studentIdUri = null
            },
            title = {
                Text(
                    text = "ID Verification",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Upload your ID document for verification.")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Student ID Number
                    OutlinedTextField(
                        value = studentIdNumber,
                        onValueChange = { studentIdNumber = it },
                        label = { Text("Student ID Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // School Name
                    OutlinedTextField(
                        value = schoolName,
                        onValueChange = { schoolName = it },
                        label = { Text("School/University Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Student ID Image Upload
                    Button(
                        onClick = { showStudentIdImagePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (studentIdUri != null) Color(0xFF4CAF50) else IslamovePrimary
                        )
                    ) {
                        Icon(
                            if (studentIdUri != null) Icons.Default.CheckCircle else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (studentIdUri != null) "Student ID Uploaded" else "Upload Student ID Photo"
                        )
                    }

                    if (isUploadingStudentId) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (studentIdUri != null && studentIdNumber.isNotBlank() && schoolName.isNotBlank()) {
                            isUploadingStudentId = true
                            viewModel.uploadStudentDocument(
                                imageUri = studentIdUri!!,
                                studentIdNumber = studentIdNumber,
                                school = schoolName
                            )
                            showStudentIdDialog = false
                            isUploadingStudentId = false
                            studentIdNumber = ""
                            schoolName = ""
                            studentIdUri = null
                        }
                    },
                    enabled = studentIdUri != null && studentIdNumber.isNotBlank() && schoolName.isNotBlank() && !isUploadingStudentId
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStudentIdDialog = false
                        studentIdNumber = ""
                        schoolName = ""
                        studentIdUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Student ID Image Picker Dialog
    if (showStudentIdImagePicker) {
        AlertDialog(
            onDismissRequest = { showStudentIdImagePicker = false },
            title = {
                Text(
                    text = "Select Student ID Photo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Choose how you'd like to upload your student ID:")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStudentIdImagePicker = false
                                studentIdCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Take a photo",
                            fontSize = 16.sp
                        )
                    }

                    // Gallery option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStudentIdImagePicker = false
                                studentIdGalleryLauncher.launch("image/*")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Choose from gallery",
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStudentIdImagePicker = false }) {
                    Text("Cancel")
                }
            },
            dismissButton = null
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTripDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// Helper functions for ride card formatting
private fun formatRoute(pickup: String, destination: String): String {
    val pickupShort = pickup.split(",").firstOrNull()?.trim() ?: pickup
    val destinationShort = destination.split(",").firstOrNull()?.trim() ?: destination

    return "$pickupShort to $destinationShort"
}

private fun isToday(timestamp: Long): Boolean {
    val today = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
           today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(timestamp: Long): Boolean {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    return yesterday.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
           yesterday.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
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
                verticalAlignment = Alignment.CenterVertically,
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

private fun formatNavigationDistance(meters: Double): String {
    return when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> String.format("%.1f km", meters / 1000)
    }
}

