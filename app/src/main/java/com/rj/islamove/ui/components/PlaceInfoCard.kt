package com.rj.islamove.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.ui.theme.IslamovePrimary

data class PlaceInfo(
    val id: String,
    val name: String,
    val category: String,
    val rating: Double? = null,
    val totalRatings: Int? = null,
    val address: String,
    val isOpen: Boolean? = null,
    val openingHours: String? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String? = null
)

/**
 * Google Maps-style place information card
 */
@Composable
fun PlaceInfoCard(
    place: PlaceInfo,
    onDirectionsClick: () -> Unit,
    onCallClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Place name and details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = place.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    // Rating and category
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (place.rating != null) {
                            Text(
                                text = String.format("%.1f", place.rating),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            
                            // Star rating
                            repeat(5) { index ->
                                Icon(
                                    if (index < place.rating.toInt()) Icons.Default.Star else Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB400),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            place.totalRatings?.let {
                                Text(
                                    text = "($it)",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        
                        Text(
                            text = "• ${place.category}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    
                    // Address
                    Text(
                        text = place.address,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    // Opening hours
                    place.openingHours?.let { hours ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = if (place.isOpen == true) Color.Green else Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = hours,
                                fontSize = 14.sp,
                                color = if (place.isOpen == true) Color.Green else Color.Red,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Save button
                IconButton(
                    onClick = { /* TODO: Implement save functionality */ }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Save",
                        tint = Color.Gray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Removed Book Ride Button - Only GoogleMapsComponent.kt should handle ride booking
                
                // Call button (if phone number available)
                if (onCallClick != null && !place.phoneNumber.isNullOrEmpty()) {
                    OutlinedButton(
                        onClick = onCallClick,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call")
                    }
                } else {
                    // Save button if no phone number
                    OutlinedButton(
                        onClick = { /* TODO: Implement save functionality */ },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Category filter chips (like Google Maps)
 */
@Composable
fun CategoryFilterRow(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        CategoryFilter("Restaurants", Icons.Default.Place),
        CategoryFilter("Hotels", Icons.Default.Home),
        CategoryFilter("Gas", Icons.Default.Place),
        CategoryFilter("Attractions", Icons.Default.Place),
        CategoryFilter("Shopping", Icons.Default.ShoppingCart),
        CategoryFilter("Food", Icons.Default.Place)
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category.name,
                onClick = { 
                    onCategorySelected(
                        if (selectedCategory == category.name) null else category.name
                    )
                },
                label = { 
                    Text(
                        text = category.name,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IslamovePrimary.copy(alpha = 0.2f),
                    selectedLabelColor = IslamovePrimary
                )
            )
        }
    }
}

data class CategoryFilter(
    val name: String,
    val icon: ImageVector
)

/**
 * Search bar for places (Google Maps style)
 */
@Composable
fun PlaceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search here",
                        color = Color.Gray
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = { onSearch(query) }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Search",
                    tint = IslamovePrimary
                )
            }
        }
    }
}