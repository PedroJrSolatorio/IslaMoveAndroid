package com.rj.islamove.ui.screens.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.ui.components.ReportDriverModal
import com.rj.islamove.data.models.CompanionType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    bookingId: String,
    onNavigateBack: () -> Unit,
    viewModel: PassengerHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load booking by ID
    LaunchedEffect(bookingId) {
        viewModel.loadBookingById(bookingId)
    }

    val booking = uiState.selectedTripForDetails

    var showReportModal by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Trip Details",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (uiState.successMessage != null) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    contentColor = Color.White
                )
            }
        }
    ) { paddingValues ->
        if (booking == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Booking not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Trip Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (booking.status) {
                            BookingStatus.COMPLETED -> Color(0xFFE8F5E8)
                            BookingStatus.CANCELLED -> Color(0xFFFFE8E8)
                            BookingStatus.IN_PROGRESS -> Color(0xFFE8F0FF)
                            else -> Color(0xFFF5F5F5)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (booking.status) {
                                BookingStatus.COMPLETED -> Icons.Default.CheckCircle
                                BookingStatus.CANCELLED -> Icons.Default.Cancel
                                BookingStatus.IN_PROGRESS -> Icons.Default.DirectionsCar
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = when (booking.status) {
                                BookingStatus.COMPLETED -> Color(0xFF4CAF50)
                                BookingStatus.CANCELLED -> Color(0xFFF44336)
                                BookingStatus.IN_PROGRESS -> Color(0xFF2196F3)
                                else -> Color(0xFF757575)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = booking.status.name.replace("_", " ").uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (booking.status) {
                                BookingStatus.COMPLETED -> Color(0xFF4CAF50)
                                BookingStatus.CANCELLED -> Color(0xFFF44336)
                                BookingStatus.IN_PROGRESS -> Color(0xFF2196F3)
                                else -> Color(0xFF757575)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Route Information
                Column {
                    Text(
                        text = "Route",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Pickup
                            RouteDetailRow(
                                icon = Icons.Default.MyLocation,
                                label = "Pickup",
                                address = booking.pickupLocation.address,
                                coordinates = "📍 ${String.format("%.6f", booking.pickupLocation.coordinates.latitude)}, ${String.format("%.6f", booking.pickupLocation.coordinates.longitude)}",
                                tint = Color(0xFF4CAF50)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Destination
                            RouteDetailRow(
                                icon = Icons.Default.Place,
                                label = "Destination",
                                address = booking.destination.address,
                                coordinates = "📍 ${String.format("%.6f", booking.destination.coordinates.latitude)}, ${String.format("%.6f", booking.destination.coordinates.longitude)}",
                                tint = Color(0xFFF44336)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Driver Information (if available)
                if (uiState.selectedTripDriver != null) {
                    val driver = uiState.selectedTripDriver!!
                    Column {
                        Text(
                            text = "Driver Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Driver info row with report button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Driver profile image
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE0E0E0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (driver.profileImageUrl != null) {
                                            AsyncImage(
                                                model = driver.profileImageUrl,
                                                contentDescription = "Driver Photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = driver.displayName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black
                                        )

                                        if (driver.driverData?.rating != null && driver.driverData.rating > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = String.format("%.1f", driver.driverData.rating),
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF666666)
                                                )
                                                if (driver.driverData.totalTrips != null && driver.driverData.totalTrips > 0) {
                                                    Text(
                                                        text = " • ${driver.driverData.totalTrips} trips",
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF666666)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Report button
                                    OutlinedButton(
                                        onClick = { showReportModal = true },
                                        modifier = Modifier.height(36.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF44336)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = "Report Driver",
                                            tint = Color(0xFFF44336),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Report",
                                            fontSize = 12.sp,
                                            color = Color(0xFFF44336)
                                        )
                                    }
                                }

                                // Vehicle information (if available)
                                if (driver.driverData?.vehicleData != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val vehicle = driver.driverData.vehicleData

                                    // Only show year if it's a valid year (greater than 0)
                                    val vehicleInfo = buildString {
                                        if (vehicle.year > 0) {
                                            append("${vehicle.year} ")
                                        }
                                        append("${vehicle.make} ${vehicle.model}")
                                    }

                                    Text(
                                        text = vehicleInfo,
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )

                                    if (vehicle.plateNumber.isNotBlank()) {
                                        Text(
                                            text = vehicle.plateNumber,
                                            fontSize = 14.sp,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Trip Details
                Column {
                    Text(
                        text = "Trip Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Date and Time
                            TripDetailRow(
                                icon = Icons.Default.Schedule,
                                label = "Date & Time",
                                value = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                                    .format(Date(booking.requestTime))
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Distance
                            TripDetailRow(
                                icon = Icons.Default.Route,
                                label = "Distance",
                                value = "${String.format("%.1f", booking.fareEstimate.estimatedDistance)} mi"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Duration
                            TripDetailRow(
                                icon = Icons.Default.Schedule,
                                label = "Duration",
                                value = "${booking.fareEstimate.estimatedDuration} min"
                            )

                            // Completion Time
                            if (booking.completionTime != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TripDetailRow(
                                    icon = Icons.Default.CheckCircle,
                                    label = "Completed",
                                    value = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                                        .format(Date(booking.completionTime!!))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fare Details (New Section)
                Column {
                    Text(
                        text = "Fare & Payment",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Total Fare
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Payment,
                                    contentDescription = "Total Fare",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Total Fare",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Text(
                                        text = "₱${kotlin.math.floor(booking.actualFare ?: booking.fareEstimate.totalEstimate).toInt()}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            // Fare Breakdown (if available)
                            if (booking.fareEstimate.fareBreakdown.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fare Breakdown:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = booking.fareEstimate.fareBreakdown,
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }

                // Companion Information (if companions exist)
                if (booking.companions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Text(
                            text = "Companions (${booking.totalPassengers - 1} total)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                booking.companions.forEachIndexed { index, companion ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (companion.type) {
                                                CompanionType.STUDENT -> Icons.Default.School
                                                CompanionType.SENIOR -> Icons.Default.Elderly
                                                CompanionType.CHILD -> Icons.Default.ChildFriendly
                                                CompanionType.REGULAR -> Icons.Default.Person
                                            },
                                            contentDescription = companion.type.name,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = companion.type.name.lowercase().replaceFirstChar { it.uppercase() } +
                                                        (if (companion.discountPercentage > 0) " (${companion.discountPercentage}% Off)" else ""),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black
                                            )
                                        }
                                        Text(
                                            text = "₱${kotlin.math.floor(companion.fare).toInt()}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (companion.discountPercentage > 0) Color(0xFF4CAF50) else Color.Black
                                        )
                                    }
                                    if (index < booking.companions.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Special Instructions (moved to bottom, after main details)
                if (booking.specialInstructions.isNotBlank()) {
                    Column {
                        Text(
                            text = "Special Instructions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE0)), // Light yellow background for notice
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Note,
                                    contentDescription = "Instructions",
                                    tint = Color(0xFFFFA000), // Orange icon
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = booking.specialInstructions,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Report Driver Modal
    if (showReportModal) {
        ReportDriverModal(
            driverName = uiState.selectedTripDriver?.displayName ?: "Driver",
            onDismiss = { showReportModal = false },
            onSubmitReport = { reportType, description ->
                viewModel.submitDriverReport(reportType, description)
                showReportModal = false
            }
        )
    }
}

// Helper composable for consistent detail rows (Trip Information)
@Composable
private fun TripDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color(0xFF666666),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Black
            )
        }
    }
}

// Helper composable for consistent route detail rows
@Composable
private fun RouteDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, address: String, coordinates: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = address,
                fontSize = 14.sp,
                color = Color.Black
            )
            Text(
                text = coordinates,
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
        }
    }
}