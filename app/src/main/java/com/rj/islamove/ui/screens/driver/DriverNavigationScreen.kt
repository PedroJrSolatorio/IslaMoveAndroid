package com.rj.islamove.ui.screens.driver

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.components.TurnByTurnNavigationOverlay
import com.rj.islamove.data.models.BookingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverNavigationScreen(
    bookingId: String,
    onNavigateBack: () -> Unit,
    onNavigateToRating: (String, String, String) -> Unit = { _, _, _ -> },
    viewModel: DriverNavigationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load booking details when screen opens
    LaunchedEffect(bookingId) {
        viewModel.initialize(bookingId)
    }

    // Handle rating navigation trigger
    LaunchedEffect(uiState.shouldNavigateToRating) {
        if (uiState.shouldNavigateToRating) {
            val booking = uiState.currentBooking
            if (booking != null && !booking.passengerId.isNullOrBlank()) {
                // Navigate to rating screen to rate the passenger
                android.util.Log.d("DriverNavigation", "Navigating to rating: bookingId=${booking.id}, passengerId=${booking.passengerId}")
                onNavigateToRating(booking.id, booking.passengerId, "PASSENGER")
                // Reset the trigger
                viewModel.resetRatingTrigger()
            } else {
                // Invalid booking data - just reset the trigger without navigation
                android.util.Log.w("DriverNavigation", "Cannot navigate to rating: booking=${booking?.id}, passengerId=${booking?.passengerId}")
                viewModel.resetRatingTrigger()
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Top App Bar
        TopAppBar(
            title = { Text("Navigation") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        
        // Map content with overlays
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
                driverLocation = uiState.currentUserLocation?.toMapboxPoint(),
                showUserLocation = true, // Enable built-in location component
                showRoute = true,
                routeInfo = uiState.currentRoute, // Pass the actual route data for turn-by-turn directions
                isNavigationMode = true,
                currentBookingStatus = uiState.currentBooking?.status
            )

            // Turn-by-turn navigation overlay - top
            val currentRoute = uiState.currentRoute
            if (currentRoute != null && currentRoute.turnByTurnInstructions.isNotEmpty()) {
                val instructions = currentRoute.turnByTurnInstructions
                val currentInstruction = instructions.firstOrNull()
                val nextInstruction = instructions.getOrNull(1)

                TurnByTurnNavigationOverlay(
                    currentInstruction = currentInstruction,
                    nextInstruction = nextInstruction,
                    onStopNavigation = { onNavigateBack() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            } else if (uiState.currentRoute != null) {
                // Fallback to simple navigation info if no turn-by-turn instructions
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${formatNavigationDistance(uiState.distanceMeters)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "ETA: ${uiState.etaMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Debug info
                        if (uiState.distanceMeters == 0.0 && uiState.etaMinutes == 0) {
                            Text(
                                text = "Debug: Route not calculated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Bottom navigation panel - positioned as overlay at bottom
            uiState.currentBooking?.let { booking ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Destination",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = booking.destination.address,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Show different buttons based on trip status
                        when {
                            uiState.shouldShowCompleteTripButton -> {
                                Button(
                                    onClick = { viewModel.completeTrip() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50) // Green
                                    )
                                ) {
                                    Text("Complete Trip")
                                }
                            }
                            uiState.shouldShowStartTripButton -> {
                                Button(
                                    onClick = { viewModel.startTrip() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3) // Blue
                                    )
                                ) {
                                    Text("Start Trip")
                                }
                            }
                            uiState.shouldShowArrivedButton -> {
                                Button(
                                    onClick = { viewModel.arrivedAtPickup() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9800) // Orange
                                    )
                                ) {
                                    Text("Arrived at Pickup")
                                }
                            }
                            uiState.currentBooking?.status == BookingStatus.IN_PROGRESS -> {
                                // Manual complete trip option for testing
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.completeTrip() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50) // Green
                                        )
                                    ) {
                                        Text("Complete Trip")
                                    }
                                    Text(
                                        text = "Distance to destination: ${formatNavigationDistance(uiState.distanceMeters)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("End Navigation")
                                }
                            }
                        }
                    }
                }
            }

            // Error message overlay
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
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