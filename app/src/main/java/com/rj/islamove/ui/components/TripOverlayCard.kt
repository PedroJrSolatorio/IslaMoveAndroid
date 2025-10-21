package com.rj.islamove.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.data.models.Booking
import com.rj.islamove.data.models.BookingStatus

@Composable
fun TripOverlayCard(
    booking: Booking,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    text = booking.destination?.address ?: "Unknown destination",
                    fontSize = 14.sp,
                    maxLines = 2
                )
            }
        }
    }
}