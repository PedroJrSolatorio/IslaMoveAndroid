package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.Booking
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.data.models.User
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMonitoringScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToRideDetails: (String) -> Unit = {},
    viewModel: LiveMonitoringViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Start live monitoring when screen loads
    LaunchedEffect(Unit) {
        Log.d("LiveMonitoringScreen", "🔥 Starting REAL-TIME monitoring")
        viewModel.startLiveMonitoring()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLiveMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 80.dp, bottom = 120.dp)
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Header with back button and refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = IslamovePrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = "Live Monitoring",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (uiState.isLiveMonitoring) Color.Green else Color.Gray,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.isLiveMonitoring) "🔥 REAL-TIME" else "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isLiveMonitoring) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (uiState.isLiveMonitoring) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Unified Map View with all information
        UnifiedMapView(
            uiState = uiState,
            onRideClick = onNavigateToRideDetails,
            onEmergencyStop = viewModel::emergencyStopRide
        )

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun UnifiedMapView(
    uiState: LiveMonitoringUiState,
    onRideClick: (String) -> Unit,
    onEmergencyStop: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
    ) {
        // Main Map
        MapboxRideView(
            modifier = Modifier.fillMaxSize(),
            initialLocation = Point.fromLngLat(125.576, 10.02), // San Jose, Dinagat Islands
            showUserLocation = false,
            showRoute = false,
            showOnlineDrivers = true,
            onlineDrivers = uiState.onlineDriverLocations,
            zoomLevel = 12.0
        )

        // System Health Status Bar (Top)
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active Rides
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.activeRides.size.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = IslamovePrimary
                    )
                    Text(
                        text = "Active Rides",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )

                // Online Drivers
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.onlineDriverLocations.size.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Online Drivers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )

                // System Health
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val (healthColor, healthText) = when (uiState.systemHealth) {
                        SystemHealth.GOOD -> Color(0xFF4CAF50) to "Good"
                        SystemHealth.WARNING -> Color(0xFFFF9800) to "Warning"
                        SystemHealth.ERROR -> Color(0xFFF44336) to "Error"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = healthColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = healthText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = healthColor
                        )
                    }
                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Rides List (Bottom)
        if (uiState.activeRides.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Active Rides",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.activeRides.take(3)) { rideInfo -> // Show max 3 rides
                            CompactRideCard(
                                rideInfo = rideInfo,
                                onClick = { onRideClick(rideInfo.booking.id) },
                                onEmergencyStop = { onEmergencyStop(rideInfo.booking.id) }
                            )
                        }

                        if (uiState.activeRides.size > 3) {
                            item {
                                Text(
                                    text = "+${uiState.activeRides.size - 3} more rides",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Last Updated (Bottom Left)
        uiState.lastUpdated?.let { lastUpdated ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Updated ${dateFormat.format(Date(lastUpdated))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Loading Indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactRideCard(
    rideInfo: LiveRideInfo,
    onClick: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val booking = rideInfo.booking

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ride #${booking.id.take(6)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${rideInfo.passengerName} → ${rideInfo.driverName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            BookingStatusBadge(status = booking.status)

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onEmergencyStop,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Emergency Stop",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveRideCard(
    rideInfo: LiveRideInfo,
    onClick: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val booking = rideInfo.booking
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ride #${booking.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rideInfo.passengerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Started: ${dateFormat.format(Date(booking.requestTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                BookingStatusBadge(status = booking.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Route info
            Column {
                Text(
                    text = "From: ${booking.pickupLocation.address}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = "To: ${booking.destination.address}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Driver info and emergency button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Driver: ${rideInfo.driverName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                
                OutlinedButton(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Emergency")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineDriverCard(
    driver: User,
    onClick: () -> Unit
) {
    val driverData = driver.driverData
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = driver.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                driverData?.let {
                    Text(
                        text = "${it.vehicleData.make} ${it.vehicleData.model} • ${it.vehicleData.plateNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(8.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
                
                driverData?.rating?.let { rating ->
                    Text(
                        text = "★ ${String.format("%.1f", rating)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Data classes
@Composable
private fun BookingStatusBadge(status: BookingStatus) {
    val (text, containerColor, contentColor) = when (status) {
        BookingStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.primary, Color.White)
        BookingStatus.LOOKING_FOR_DRIVER -> Triple("Looking", MaterialTheme.colorScheme.secondary, Color.White)
        BookingStatus.ACCEPTED -> Triple("Accepted", Color(0xFF2196F3), Color.White)
        BookingStatus.DRIVER_ARRIVING -> Triple("Arriving", Color(0xFFFF9800), Color.White)
        BookingStatus.DRIVER_ARRIVED -> Triple("Arrived", Color(0xFFFF5722), Color.White)
        BookingStatus.IN_PROGRESS -> Triple("In Progress", Color(0xFF4CAF50), Color.White)
        BookingStatus.COMPLETED -> Triple("Completed", Color(0xFF9C27B0), Color.White)
        BookingStatus.CANCELLED -> Triple("Cancelled", MaterialTheme.colorScheme.error, Color.White)
        BookingStatus.EXPIRED -> Triple("Expired", Color.Gray, Color.White)
        BookingStatus.SCHEDULED -> Triple("Scheduled", MaterialTheme.colorScheme.tertiary, Color.White)
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

data class LiveRideInfo(
    val booking: Booking,
    val passengerName: String,
    val driverName: String,
    val estimatedDuration: Int? = null
)


