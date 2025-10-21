package com.rj.islamove.ui.screens.profile

import androidx.compose.foundation.background
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
import com.rj.islamove.data.models.*
import com.rj.islamove.utils.BoundaryFareUtils
import java.text.SimpleDateFormat
import java.util.*
import com.rj.islamove.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onRideSelected: (String) -> Unit = {},
    viewModel: RideHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(userId) {
        viewModel.loadRideHistory(userId)
    }

    // Load more items when reaching the end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleItemIndex ->
                if (lastVisibleItemIndex != null &&
                    lastVisibleItemIndex >= uiState.rides.size - 2 &&
                    !uiState.isLoadingMore &&
                    uiState.hasMore) {
                    viewModel.loadMoreHistory()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp)
    ) {
        when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading ride history...")
                        }
                    }
                }

                uiState.error != null -> {
                    val errorMsg = uiState.error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMsg ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadRideHistory(userId) }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                uiState.rides.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No rides yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your completed rides will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(uiState.rides) { booking ->
                            TripHistoryItem(
                                booking = booking,
                                onClick = { onRideSelected(booking.id) }
                            )
                        }

                        // Loading indicator for pagination
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun TripHistoryItem(
    booking: Booking,
    onClick: () -> Unit
) {
    // Format date and time like in the PNG
    val dateFormat = when {
        isToday(booking.requestTime) -> SimpleDateFormat("'Today', h:mm a", Locale.getDefault())
        isYesterday(booking.requestTime) -> SimpleDateFormat("'Yesterday', h:mm a", Locale.getDefault())
        else -> SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        onClick = onClick,
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
                    text = formatRouteWithBoundary(booking),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date and time
                Text(
                    text = dateFormat.format(Date(booking.requestTime)),
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
                    text = "$${kotlin.math.floor(booking.actualFare ?: booking.fareEstimate.totalEstimate).toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status
                val (statusColor, statusText) = when (booking.status) {
                    BookingStatus.COMPLETED -> Color(0xFF4CAF50) to "Completed"
                    BookingStatus.CANCELLED -> Color(0xFFD32F2F) to "Cancelled"
                    BookingStatus.IN_PROGRESS -> Color(0xFF2196F3) to "In Progress"
                    BookingStatus.EXPIRED -> Color(0xFF666666) to "Expired"
                    else -> Color(0xFF666666) to booking.status.name
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

// Helper functions
private fun formatRoute(pickup: String, destination: String): String {
    val pickupShort = pickup.split(",").firstOrNull()?.trim() ?: pickup
    val destinationShort = destination.split(",").firstOrNull()?.trim() ?: destination

    return "$pickupShort to $destinationShort"
}

private fun formatRouteWithBoundary(booking: Booking): String {
    // Format pickup location using boundary detection
    val pickupDisplay = BoundaryFareUtils.formatPickupLocationForHistory(booking.pickupLocation.coordinates)

    // Format destination as before (just first part of address)
    val destinationShort = booking.destination.address.split(",").firstOrNull()?.trim() ?: booking.destination.address

    return "$pickupDisplay to $destinationShort"
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