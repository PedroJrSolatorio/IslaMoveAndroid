package com.rj.islamove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rj.islamove.data.models.*
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

@Composable
fun UserRatingOverview(
    ratingStats: UserRatingStats,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with overall rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format("%.1f", ratingStats.overallRating),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = IslamovePrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StarDisplay(
                            rating = ratingStats.overallRating,
                            size = 20.dp
                        )
                    }
                    Text(
                        text = "${ratingStats.totalRatings} reviews",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (ratingStats.totalRatings > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when {
                                ratingStats.overallRating >= 4.5 -> "Excellent"
                                ratingStats.overallRating >= 4.0 -> "Very Good"
                                ratingStats.overallRating >= 3.5 -> "Good"
                                ratingStats.overallRating >= 3.0 -> "Average"
                                else -> "Below Average"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = IslamovePrimary
                        )
                        Text(
                            text = "Rating",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showDetails && ratingStats.totalRatings > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Rating breakdown bars
                RatingBreakdownBars(ratingStats.ratingBreakdown, ratingStats.totalRatings)
                
                // Category averages if available
                if (hasAnyCategoryRatings(ratingStats.categoryAverages)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryAveragesDisplay(
                        categories = ratingStats.categoryAverages,
                        userType = ratingStats.userType
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingBreakdownBars(
    breakdown: RatingBreakdown,
    totalRatings: Int
) {
    Column {
        Text(
            text = "Rating Breakdown",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        listOf(
            5 to breakdown.fiveStars,
            4 to breakdown.fourStars,
            3 to breakdown.threeStars,
            2 to breakdown.twoStars,
            1 to breakdown.oneStar
        ).forEach { (stars, count) ->
            RatingBreakdownBar(
                stars = stars,
                count = count,
                totalRatings = totalRatings
            )
        }
    }
}

@Composable
private fun RatingBreakdownBar(
    stars: Int,
    count: Int,
    totalRatings: Int
) {
    val percentage = if (totalRatings > 0) count.toFloat() / totalRatings else 0f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$stars",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(16.dp)
        )
        
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage)
                    .background(IslamovePrimary)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun CategoryAveragesDisplay(
    categories: RatingCategories,
    userType: UserType
) {
    Column {
        Text(
            text = "Category Ratings",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (userType == UserType.DRIVER) {
            // Driver categories
            categories.drivingSkill?.let {
                CategoryRatingBar("Driving Skill", it, Icons.Default.Person)
            }
            categories.vehicleCondition?.let {
                CategoryRatingBar("Vehicle Condition", it, Icons.Default.Settings)
            }
            categories.punctuality?.let {
                CategoryRatingBar("Punctuality", it, Icons.Default.Check)
            }
            categories.friendliness?.let {
                CategoryRatingBar("Friendliness", it, Icons.Default.Face)
            }
            categories.routeKnowledge?.let {
                CategoryRatingBar("Route Knowledge", it, Icons.Default.LocationOn)
            }
        } else {
            // Passenger categories
            categories.politeness?.let {
                CategoryRatingBar("Politeness", it, Icons.Default.Face)
            }
            categories.cleanliness?.let {
                CategoryRatingBar("Cleanliness", it, Icons.Default.Star)
            }
            categories.communication?.let {
                CategoryRatingBar("Communication", it, Icons.Default.Phone)
            }
            categories.respectfulness?.let {
                CategoryRatingBar("Respectfulness", it, Icons.Default.Favorite)
            }
            categories.onTime?.let {
                CategoryRatingBar("On Time", it, Icons.Default.Check)
            }
        }
    }
}

@Composable
private fun CategoryRatingBar(
    title: String,
    rating: Int,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IslamovePrimary,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        
        StarDisplay(
            rating = rating.toDouble(),
            size = 14.dp
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = rating.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun RecentRatingsList(
    recentRatings: List<RecentRating>,
    modifier: Modifier = Modifier
) {
    if (recentRatings.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No reviews yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentRatings) { rating ->
                RecentRatingCard(rating = rating)
            }
        }
    }
}

@Composable
private fun RecentRatingCard(rating: RecentRating) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with stars and reviewer name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarDisplay(
                    rating = rating.stars.toDouble(),
                    size = 16.dp
                )
                
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(rating.tripDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (rating.review.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rating.review,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "- ${rating.fromUserName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun StarDisplay(
    rating: Double,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(5) { index ->
            val starIndex = index + 1
            val isFilled = starIndex <= round(rating)
            
            Icon(
                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.Star,
                contentDescription = null,
                tint = if (isFilled) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
fun RatingSummaryChip(
    rating: Double,
    totalReviews: Int,
    modifier: Modifier = Modifier
) {
    if (totalReviews > 0) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = IslamovePrimary.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IslamovePrimary
                )
                Text(
                    text = " ($totalReviews)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper function to check if any category ratings exist
private fun hasAnyCategoryRatings(categories: RatingCategories): Boolean {
    return listOfNotNull(
        categories.drivingSkill,
        categories.vehicleCondition,
        categories.punctuality,
        categories.friendliness,
        categories.routeKnowledge,
        categories.politeness,
        categories.cleanliness,
        categories.communication,
        categories.respectfulness,
        categories.onTime
    ).isNotEmpty()
}