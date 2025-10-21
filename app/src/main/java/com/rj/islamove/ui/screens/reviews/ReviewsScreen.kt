package com.rj.islamove.ui.screens.reviews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.Rating
import com.rj.islamove.data.models.RatingFilter
import com.rj.islamove.ui.components.UserRatingOverview
import com.rj.islamove.ui.components.StarDisplay
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReviewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadReviews()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Reviews") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.reviews.isNotEmpty()) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Filter")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Special handling for index building message
                    if (uiState.errorMessage!!.contains("indexes are still building")) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Setting up Reviews",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "We're optimizing the reviews system for better performance. This usually takes just a few minutes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(onClick = { viewModel.loadReviews() }) {
                            Text("Check Again")
                        }
                    } else {
                        // Regular error handling
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { viewModel.loadReviews() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.ratingStats != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Rating Overview
                    item {
                        UserRatingOverview(
                            ratingStats = uiState.ratingStats!!,
                            showDetails = true
                        )
                    }

                    // Filter Summary
                    if (uiState.currentFilter != RatingFilter.ALL) {
                        item {
                            FilterSummaryCard(
                                filter = uiState.currentFilter,
                                totalReviews = uiState.reviews.size,
                                onClearFilter = { viewModel.setFilter(RatingFilter.ALL) }
                            )
                        }
                    }

                    // Reviews List
                    if (uiState.reviews.isEmpty() && !uiState.isLoading) {
                        item {
                            NoReviewsCard()
                        }
                    } else {
                        items(uiState.reviews) { review ->
                            ReviewCard(review = review)
                        }
                    }
                }
            }
        }
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterDialog(
            currentFilter = uiState.currentFilter,
            onFilterSelected = { filter ->
                viewModel.setFilter(filter)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
private fun ReviewCard(review: Rating) {
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
            // Header with rating and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarDisplay(
                    rating = review.stars.toDouble(),
                    size = 20.dp
                )

                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(review.tripDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Review text if available
            if (review.review.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = review.review,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Category ratings if available
            if (hasCategoryRatings(review.categories)) {
                Spacer(modifier = Modifier.height(12.dp))
                CategoryRatingsSection(review = review)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reviewer info
            Text(
                text = if (review.isAnonymous) "- Anonymous" else "- ${review.fromUserId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
private fun CategoryRatingsSection(review: Rating) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Category Ratings",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Show relevant categories based on user type
            review.categories.apply {
                // Driver categories (when being rated by passengers)
                drivingSkill?.let { CategoryRatingRow("Driving Skill", it) }
                vehicleCondition?.let { CategoryRatingRow("Vehicle Condition", it) }
                punctuality?.let { CategoryRatingRow("Punctuality", it) }
                friendliness?.let { CategoryRatingRow("Friendliness", it) }
                routeKnowledge?.let { CategoryRatingRow("Route Knowledge", it) }

                // Passenger categories (when being rated by drivers)
                politeness?.let { CategoryRatingRow("Politeness", it) }
                cleanliness?.let { CategoryRatingRow("Cleanliness", it) }
                communication?.let { CategoryRatingRow("Communication", it) }
                respectfulness?.let { CategoryRatingRow("Respectfulness", it) }
                onTime?.let { CategoryRatingRow("On Time", it) }
            }
        }
    }
}

@Composable
private fun CategoryRatingRow(title: String, rating: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            StarDisplay(
                rating = rating.toDouble(),
                size = 14.dp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = rating.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoReviewsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No reviews yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Complete trips to start receiving reviews",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FilterSummaryCard(
    filter: RatingFilter,
    totalReviews: Int,
    onClearFilter: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = IslamovePrimary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Filtered by: ${getFilterDisplayName(filter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$totalReviews review${if (totalReviews == 1) "" else "s"} shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onClearFilter) {
                Text("Clear Filter")
            }
        }
    }
}

@Composable
private fun FilterDialog(
    currentFilter: RatingFilter,
    onFilterSelected: (RatingFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Reviews") },
        text = {
            LazyColumn {
                items(RatingFilter.values()) { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = filter == currentFilter,
                            onClick = { onFilterSelected(filter) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getFilterDisplayName(filter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun getFilterDisplayName(filter: RatingFilter): String {
    return when (filter) {
        RatingFilter.ALL -> "All Reviews"
        RatingFilter.FIVE_STARS -> "5 Stars"
        RatingFilter.FOUR_STARS -> "4 Stars"
        RatingFilter.THREE_STARS -> "3 Stars"
        RatingFilter.TWO_STARS -> "2 Stars"
        RatingFilter.ONE_STAR -> "1 Star"
        RatingFilter.WITH_REVIEW -> "With Comments"
        RatingFilter.WITHOUT_REVIEW -> "Without Comments"
        RatingFilter.RECENT -> "Most Recent"
        RatingFilter.OLDEST -> "Oldest First"
    }
}

private fun hasCategoryRatings(categories: com.rj.islamove.data.models.RatingCategories): Boolean {
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