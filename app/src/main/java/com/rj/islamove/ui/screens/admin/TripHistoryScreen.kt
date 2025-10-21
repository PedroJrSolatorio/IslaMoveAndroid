package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rj.islamove.data.models.Ride
import com.rj.islamove.data.models.RideStatus
import com.rj.islamove.ui.components.TripDetailsModal
import com.rj.islamove.utils.BoundaryFareUtils
import java.text.SimpleDateFormat
import java.util.*
import com.rj.islamove.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    userId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: TripHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRide by remember { mutableStateOf<Ride?>(null) }

    LaunchedEffect(userId) {
        viewModel.loadTripHistory(userId)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        TripHistoryHeader(
            onNavigateBack = onNavigateBack
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Past Trips",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show loading state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Show error state
            uiState.errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading trips",
                            fontSize = 16.sp,
                            color = Color(0xFFD32F2F),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            fontSize = 14.sp,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@Column
            }

            if (uiState.rides.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No trips found",
                            fontSize = 16.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Trip history will appear here once rides are completed",
                            fontSize = 14.sp,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Trip list
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.rides.forEach { ride ->
                        TripHistoryItem(
                            ride = ride,
                            onClick = { selectedRide = ride }
                        )
                    }
                }
            }
        }
    }

    // Show trip details modal when a ride is selected
    selectedRide?.let { ride ->
        TripDetailsModal(
            ride = ride,
            onDismiss = { selectedRide = null }
        )
    }
}

@Composable
private fun TripHistoryHeader(
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Text(
            text = "Trip History",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun TripHistoryItem(
    ride: Ride,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle icon (using the onlineicon.png as requested)
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
                // Route
                Text(
                    text = formatRideRouteWithBoundary(ride),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time
                Text(
                    text = formatTripTime(ride),
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Price and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Price
                Text(
                    text = "₱${kotlin.math.floor(ride.fare.finalAmount).toInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status
                val (statusText, statusColor) = when (ride.status) {
                    RideStatus.COMPLETED -> "Completed" to Color(0xFF4CAF50)
                    RideStatus.CANCELLED_BY_PASSENGER,
                    RideStatus.CANCELLED_BY_DRIVER -> "Cancelled" to Color(0xFFD32F2F)
                    else -> ride.status.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() } to Color(0xFF666666)
                }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }
    }
}

private fun formatRideRouteWithBoundary(ride: Ride): String {
    // Format pickup location using boundary detection
    val pickupDisplay = BoundaryFareUtils.formatPickupLocationForHistory(
        ride.pickupLocation.latitude,
        ride.pickupLocation.longitude
    )

    // Format destination as before (just first part of address)
    val destinationShort = ride.destination.address.split(",").firstOrNull()?.trim() ?: ride.destination.address

    return "$pickupDisplay to $destinationShort"
}

private fun formatTripTime(ride: Ride): String {
    val calendar = Calendar.getInstance()
    val now = System.currentTimeMillis()
    val tripTime = ride.endTime ?: ride.startTime ?: ride.acceptTime ?: ride.requestTime

    val diffInDays = (now - tripTime) / (24 * 60 * 60 * 1000)

    return when {
        diffInDays == 0L -> {
            // Today
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today, ${timeFormat.format(Date(tripTime))}"
        }
        diffInDays == 1L -> {
            // Yesterday
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Yesterday, ${timeFormat.format(Date(tripTime))}"
        }
        diffInDays < 30L -> {
            // This month
            val dateFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            dateFormat.format(Date(tripTime))
        }
        else -> {
            // Older
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dateFormat.format(Date(tripTime))
        }
    }
}