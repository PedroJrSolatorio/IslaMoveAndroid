package com.rj.islamove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rj.islamove.data.models.LocationType
import com.rj.islamove.data.models.SanJoseLocation
import com.rj.islamove.ui.theme.IslamovePrimary

data class POIInfo(
    val location: SanJoseLocation,
    val rating: Float? = null,
    val reviewCount: Int? = null,
    val description: String? = null,
    val hours: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
    val isOpen: Boolean? = null
)

@Composable
fun POIInfoCard(
    poiInfo: POIInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
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
            // Header with dismiss handle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Spacer(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // POI Image (if available)
            poiInfo.photoUrl?.let { photoUrl ->
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // POI Name and Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getLocationTypeIcon(poiInfo.location.type),
                    contentDescription = null,
                    tint = IslamovePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = poiInfo.location.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = getLocationTypeLabel(poiInfo.location.type),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Rating and Reviews (if available)
            if (poiInfo.rating != null || poiInfo.reviewCount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    poiInfo.rating?.let { rating ->
                        Text(
                            text = String.format("%.1f", rating),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        repeat(5) { index ->
                            Icon(
                                imageVector = if (index < rating.toInt()) Icons.Filled.Star else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index < rating.toInt()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    poiInfo.reviewCount?.let { count ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "($count reviews)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Address
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${poiInfo.location.barangay}, San Jose, Dinagat Islands",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Hours (if available)
            poiInfo.hours?.let { hours ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hours,
                        fontSize = 14.sp,
                        color = if (poiInfo.isOpen == true) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.error,
                        fontWeight = if (poiInfo.isOpen == true) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
            
            // Phone (if available)
            poiInfo.phone?.let { phone ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = phone,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Description (if available)
            poiInfo.description?.let { description ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Call Button (if phone available)
                if (poiInfo.phone != null) {
                    OutlinedButton(
                        onClick = { /* Handle call */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call")
                    }
                }
                
                // Removed Book Ride Button - Only GoogleMapsComponent.kt should handle ride booking
            }
        }
    }
}

private fun getLocationTypeIcon(type: LocationType): ImageVector {
    return when (type) {
        LocationType.POBLACION -> Icons.Default.LocationOn
        LocationType.BARANGAY -> Icons.Default.Home
        LocationType.LANDMARK -> Icons.Default.Place
        LocationType.MUNICIPAL_HALL -> Icons.Default.Home
        LocationType.BEACH -> Icons.Default.Place
        LocationType.SHRINE -> Icons.Default.Place
        LocationType.BOUNDARY -> Icons.Default.Place
    }
}

private fun getLocationTypeLabel(type: LocationType): String {
    return when (type) {
        LocationType.POBLACION -> "Poblacion"
        LocationType.BARANGAY -> "Barangay"
        LocationType.LANDMARK -> "Landmark"
        LocationType.MUNICIPAL_HALL -> "Government Building"
        LocationType.BEACH -> "Beach Resort"
        LocationType.SHRINE -> "Religious Site"
        LocationType.BOUNDARY -> "Boundary Area"
    }
}

// Preview sample data for Dinagat Islands locations
@Composable
private fun SamplePOIInfo(): POIInfo {
    return POIInfo(
        location = SanJoseLocation(
            name = "Islander's Castle",
            barangay = "San Jose",
            coordinates = com.google.firebase.firestore.GeoPoint(10.0050, 125.5708),
            type = LocationType.LANDMARK
        ),
        rating = 4.5f,
        reviewCount = 127,
        description = "Famous landmark castle owned by the Ecleo family, visible during island hopping trips. Located on top of the mountain with scenic views of Dinagat Islands.",
        hours = "Open 24 hours",
        isOpen = true,
        phone = "+63 xxx xxx xxxx"
    )
}