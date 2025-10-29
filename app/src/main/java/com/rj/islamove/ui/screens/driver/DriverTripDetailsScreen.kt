package com.rj.islamove.ui.screens.driver

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
import com.rj.islamove.ui.components.ReportPassengerModal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripDetailsScreen(
    bookingId: String,
    onNavigateBack: () -> Unit,
    viewModel: DriverHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoneBoundaryRepository = viewModel.getZoneBoundaryRepository()

    // Load booking by ID
    LaunchedEffect(bookingId) {
        viewModel.loadBookingById(bookingId)
    }

    val booking = uiState.selectedTripForDetails

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Pickup",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Pickup",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = getBookingPickupLocationBoundaryName(booking, zoneBoundaryRepository),
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "📍 ${String.format("%.6f", booking.pickupLocation.coordinates.latitude)}, ${String.format("%.6f", booking.pickupLocation.coordinates.longitude)}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Destination
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = "Destination",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Destination",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = booking.destination.address.ifBlank {
                                            "${String.format("%.6f", booking.destination.coordinates.latitude)}, ${String.format("%.6f", booking.destination.coordinates.longitude)}"
                                        },
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "📍 ${String.format("%.6f", booking.destination.coordinates.latitude)}, ${String.format("%.6f", booking.destination.coordinates.longitude)}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Date/Time",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Date & Time",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                                            .format(Date(booking.requestTime)),
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Distance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Route,
                                    contentDescription = "Distance",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Distance",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Text(
                                        text = "${String.format("%.1f", booking.fareEstimate.estimatedDistance)} mi",
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Duration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Duration",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Duration",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Text(
                                        text = "${booking.fareEstimate.estimatedDuration} min",
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fare
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Payment,
                                    contentDescription = "Fare",
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
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Completion Time
                            if (booking.completionTime != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF666666),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Completed",
                                            fontSize = 12.sp,
                                            color = Color(0xFF666666)
                                        )
                                        Text(
                                            text = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                                                .format(Date(booking.completionTime!!)),
                                            fontSize = 14.sp,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Passenger Information (if available)
                if (uiState.selectedTripPassenger != null) {
                    val passenger = uiState.selectedTripPassenger!!
                    var showReportPassengerModal by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Text(
                            text = "Passenger Information",
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
                                // Passenger info row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Passenger profile image
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE0E0E0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (passenger.profileImageUrl != null) {
                                            AsyncImage(
                                                model = passenger.profileImageUrl,
                                                contentDescription = "Passenger Photo",
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
                                            text = passenger.displayName ?: "Unknown Passenger",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black
                                        )
                                    }

                                    // Report button
                                    OutlinedButton(
                                        onClick = { showReportPassengerModal = true },
                                        modifier = Modifier.height(36.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF44336)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = "Report Passenger",
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
                            }
                        }
                    }

                    // Report Passenger Modal
                    if (showReportPassengerModal) {
                        ReportPassengerModal(
                            passengerName = passenger.displayName ?: "Passenger",
                            onDismiss = { showReportPassengerModal = false },
                            onSubmitReport = { reportType, description ->
                                viewModel.submitPassengerReport(reportType, description)
                                showReportPassengerModal = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
