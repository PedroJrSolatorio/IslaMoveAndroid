package com.rj.islamove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.rj.islamove.data.models.Ride
import com.rj.islamove.data.models.RideStatus
import com.rj.islamove.utils.BoundaryFareUtils
import java.text.SimpleDateFormat
import java.util.*
import com.rj.islamove.R

@Composable
fun TripDetailsModal(
    ride: Ride,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRIP DETAILS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        letterSpacing = 0.5.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Trip Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    val (statusText, statusColor) = when (ride.status) {
                        RideStatus.COMPLETED -> "Completed" to Color(0xFF4CAF50)
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.CANCELLED_BY_DRIVER -> "Cancelled" to Color(0xFFD32F2F)
                        else -> ride.status.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() } to Color(0xFF666666)
                    }

                    Text(
                        text = statusText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trip Route
                Column {
                    Text(
                        text = "Route:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF0F0F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = R.drawable.onlineicon,
                                contentDescription = "Vehicle",
                                modifier = Modifier.size(20.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatRideRouteWithBoundary(ride),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pickup Location Details
                DetailRow(
                    label = "Pickup:",
                    value = ride.pickupLocation.address
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Destination Details
                DetailRow(
                    label = "Destination:",
                    value = ride.destination.address
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Details
                ride.requestTime?.let { requestTime ->
                    DetailRow(
                        label = "Request Time:",
                        value = formatDateTime(requestTime)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ride.acceptTime?.let { acceptTime ->
                    DetailRow(
                        label = "Accept Time:",
                        value = formatDateTime(acceptTime)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ride.startTime?.let { startTime ->
                    DetailRow(
                        label = "Start Time:",
                        value = formatDateTime(startTime)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ride.endTime?.let { endTime ->
                    DetailRow(
                        label = "End Time:",
                        value = formatDateTime(endTime)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Driver Details
                ride.driverId?.let { driverId ->
                    DetailRow(
                        label = "Driver ID:",
                        value = driverId
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Passenger Details
                DetailRow(
                    label = "Passenger ID:",
                    value = ride.passengerId
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Total Fare
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Fare:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "₱${kotlin.math.floor(ride.fare.finalAmount).toInt()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Close",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Black
        )
    }
}


private fun formatRideRouteWithBoundary(ride: Ride): String {
    val pickupDisplay = BoundaryFareUtils.formatPickupLocationForHistory(
        ride.pickupLocation.latitude,
        ride.pickupLocation.longitude
    )
    val destinationShort = ride.destination.address.split(",").firstOrNull()?.trim() ?: ride.destination.address
    return "$pickupDisplay to $destinationShort"
}

private fun formatDateTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}