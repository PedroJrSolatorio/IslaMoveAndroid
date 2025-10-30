package com.rj.islamove.ui.screens.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rj.islamove.data.models.*
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.NumberFormat
import java.util.*
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    navController: NavController,
    bookingId: String,
    toUserId: String,
    toUserType: String, // "DRIVER" or "PASSENGER"
    viewModel: RatingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Determine if we're rating a driver or passenger
    val isRatingDriver = toUserType == "DRIVER"

    // Debug logging (can be removed in production)
    android.util.Log.d("RatingScreen", "Rating screen opened for $toUserType")
    
    LaunchedEffect(bookingId, toUserId) {
        viewModel.initializeRating(bookingId, toUserId, UserType.valueOf(toUserType))
    }

    // Handle rating submission result with timeout
    LaunchedEffect(uiState.isSubmitted) {
        android.util.Log.d("RatingScreen", "LaunchedEffect triggered with isSubmitted = ${uiState.isSubmitted}")
        println("DEBUG: LaunchedEffect triggered with isSubmitted = ${uiState.isSubmitted}")
        if (uiState.isSubmitted) {
            android.util.Log.d("RatingScreen", "Rating submission confirmed, starting navigation process...")
            println("DEBUG: Starting navigation process...")

            // Use a simpler navigation approach
            handleNavigationAfterRating(navController, bookingId)
        }
    }

    // Backup navigation trigger with timeout
    LaunchedEffect(uiState.isSubmitting) {
        if (uiState.isSubmitting) {
            android.util.Log.d("RatingScreen", "Rating submission started, setting up timeout...")
            // Wait for submission to complete (max 10 seconds)
            var attempts = 0
            while (attempts < 20 && uiState.isSubmitting && !uiState.isSubmitted) {
                kotlinx.coroutines.delay(500)
                attempts++
            }

            // If submission completed successfully
            if (uiState.isSubmitted && !uiState.isSubmitting) {
                android.util.Log.d("RatingScreen", "Submission completed via timeout check")
                handleNavigationAfterRating(navController, bookingId)
            } else if (attempts >= 20) {
                android.util.Log.w("RatingScreen", "Rating submission timed out")
                // Force navigation anyway after timeout
                handleNavigationAfterRating(navController, bookingId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Your trip",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Fare Summary
            item {
                FareSummarySection(uiState = uiState,
                    onToggleFareDetails = viewModel::toggleFareDetails)
            }

            // Rating section
            item {
                if (isRatingDriver) {
                    RateDriverSection(
                        uiState = uiState,
                        onRatingChange = viewModel::updateOverallRating,
                        onMessageToggle = viewModel::togglePersonalizedMessage
                    )
                } else {
                    RatePassengerSection(
                        uiState = uiState,
                        onRatingChange = viewModel::updateOverallRating,
                        onMessageToggle = viewModel::togglePersonalizedMessage
                    )
                }
            }

            // ** Comment section
            item {
                CommentSection(
                    review = uiState.review,
                    onReviewChange = viewModel::updateReview
                )
            }

            // Privacy options (Anonymous review switch)
            item {
                PrivacySection(
                    isAnonymous = uiState.isAnonymous,
                    onAnonymousChange = viewModel::updateAnonymous
                )
            }
            //

            // Submit button
            item {
                SubmitButton(
                    isEnabled = uiState.overallRating > 0 && !uiState.isSubmitting,
                    isLoading = uiState.isSubmitting,
                    onSubmit = {
                        android.util.Log.d("RatingScreen", "Submit button clicked, starting rating submission...")
                        println("DEBUG: Submit button clicked!")
                        viewModel.submitRating()
                    }
                )
            }



            // Debug: Manual navigation button (TEMPORARY)
            if (uiState.isSubmitted) {
                item {
                    Button(
                        onClick = {
                            println("DEBUG: Manual navigation button clicked")
                            navController.popBackStack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Blue
                        )
                    ) {
                        Text(
                            text = "Manual Go Back (DEBUG)",
                            color = Color.White
                        )
                    }
                }
            }

            // Debug: Show error message if it exists (TEMPORARY)
            val errorMessage = uiState.error
            if (errorMessage != null && errorMessage.contains("already")) {
                item {
                    Button(
                        onClick = {
                            println("DEBUG: Already rated - forcing navigation")
                            navController.popBackStack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Green
                        )
                    ) {
                        Text(
                            text = "Already Rated - Go Back (DEBUG)",
                            color = Color.White
                        )
                    }
                }
            }

            // Error display
            if (uiState.error != null) {
                item {
                    ErrorCard(error = uiState.error!!)
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    isAnonymous: Boolean,
    onAnonymousChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Anonymous review",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
        
        Switch(
            checked = isAnonymous,
            onCheckedChange = onAnonymousChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SubmitButton(
    isEnabled: Boolean,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Button(
        onClick = onSubmit,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IslamovePrimary,
            contentColor = Color.White,
            disabledContainerColor = IslamovePrimary.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Submit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}


// Add these new composables to your RatingScreen.kt file

// Personalized messages for drivers (when rating passengers)
private val DRIVER_PERSONALIZED_MESSAGES = listOf(
    "Respectful",
    "On time",
    "Friendly",
    "Polite conversation",
    "Quiet and peaceful"
)

// Personalized messages for passengers (when rating drivers)
private val PASSENGER_PERSONALIZED_MESSAGES = listOf(
    "Cool driver",
    "Hygienic",
    "Safe driving",
    "On time",
    "Professional"
)

@Composable
private fun PersonalizedMessagesSection(
    selectedMessages: List<String>,
    isRatingDriver: Boolean,
    onMessageToggle: (String) -> Unit
) {
    val messages = if (isRatingDriver) PASSENGER_PERSONALIZED_MESSAGES else DRIVER_PERSONALIZED_MESSAGES

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Quick feedback (optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Wrap chips in rows
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEach { message ->
                val isSelected = selectedMessages.contains(message)
                FilterChip(
                    selected = isSelected,
                    onClick = { onMessageToggle(message) },
                    label = {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IslamovePrimary.copy(alpha = 0.2f),
                        selectedLabelColor = IslamovePrimary,
                        selectedLeadingIconColor = IslamovePrimary,
                        containerColor = Color.White,
                        labelColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) IslamovePrimary else Color.Black.copy(alpha = 0.3f),
                        selectedBorderColor = IslamovePrimary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }
        }
    }
}


// Note: FlowRow is available in Compose 1.4.0+
// If you don't have it, use this alternative implementation:
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val sequences = mutableListOf<List<Placeable>>()
        var currentSequence = mutableListOf<Placeable>()
        var currentWidth = 0
        val spacing = 8.dp.roundToPx()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)
            if (currentWidth + placeable.width > constraints.maxWidth && currentSequence.isNotEmpty()) {
                sequences.add(currentSequence)
                currentSequence = mutableListOf()
                currentWidth = 0
            }
            currentSequence.add(placeable)
            currentWidth += placeable.width + spacing
        }
        if (currentSequence.isNotEmpty()) {
            sequences.add(currentSequence)
        }

        val height = sequences.sumOf { row ->
            row.maxOfOrNull { it.height } ?: 0
        } + (sequences.size - 1) * spacing

        layout(constraints.maxWidth, height) {
            var yPosition = 0
            sequences.forEach { row ->
                var xPosition = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x = xPosition, y = yPosition)
                    xPosition += placeable.width + spacing
                }
                yPosition += rowHeight + spacing
            }
        }
    }
}

@Composable
private fun FareSummarySection(
    uiState: RatingUiState,
    onToggleFareDetails: () -> Unit
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
        currency = Currency.getInstance("PHP")
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fare summary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            // Show "More details" for both drivers and passengers
            TextButton(
                onClick = onToggleFareDetails,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = IslamovePrimary
                )
            ) {
                Text(
                    text = if (uiState.isFareDetailsExpanded) "Less details" else "More details",
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    imageVector = if (uiState.isFareDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val booking = uiState.booking
        val fareEstimate = booking?.fareEstimate
        val actualFare = booking?.actualFare

        if (fareEstimate != null) {
            // Show expanded details if toggle is on
            if (uiState.isFareDetailsExpanded) {
                // Base fare
                FareSummaryItem("Base fare", fareEstimate.baseFare, currencyFormat)

                // Companions fare (if any)
                val companions = booking.companions
                if (!companions.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Companions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    companions.forEach { companion ->
                        val companionLabel = when (companion.type) {
                            CompanionType.REGULAR -> "Companion"
                            CompanionType.STUDENT -> "Student"
                            CompanionType.SENIOR -> "Senior"
                            CompanionType.CHILD -> "Child"
                        }

                        // Show discount if applicable
                        val label = if (companion.discountPercentage > 0) {
                            "  $companionLabel (${companion.discountPercentage}% off)"
                        } else {
                            "  $companionLabel"
                        }

                        FareSummaryItem(
                            label = label,
                            amount = companion.fare,
                            currencyFormat = currencyFormat
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Show discount if applicable
                val discountPercentage = booking.passengerDiscountPercentage
                if (discountPercentage != null && discountPercentage > 0) {
                    // Discount only applies to main passenger's base fare
                    val discountAmount = fareEstimate.baseFare * (discountPercentage / 100.0)
                    FareSummaryItem(
                        label = "Discount ($discountPercentage%)",
                        amount = -discountAmount,
                        currencyFormat = currencyFormat,
                        isDiscount = true
                    )
                }

                // Show surge if applicable
                if (fareEstimate.surgeFactor > 1.0) {
                    val surgePercentage = ((fareEstimate.surgeFactor - 1.0) * 100).toInt()
                    FareSummaryItem(
                        label = "Surge ($surgePercentage%)",
                        amount = fareEstimate.totalEstimate * (fareEstimate.surgeFactor - 1.0),
                        currencyFormat = currencyFormat
                    )
                }
            } else {
                // Collapsed view - just show base fare
                FareSummaryItem("Base fare", fareEstimate.baseFare, currencyFormat)

                // Show discount if applicable
                val discountPercentage = booking.passengerDiscountPercentage
                if (discountPercentage != null && discountPercentage > 0) {
                    val discountAmount = fareEstimate.baseFare * (discountPercentage / 100.0)
                    FareSummaryItem(
                        label = "Discount ($discountPercentage%)",
                        amount = -discountAmount,
                        currencyFormat = currencyFormat,
                        isDiscount = true
                    )
                }

                // Show surge if applicable
                if (fareEstimate.surgeFactor > 1.0) {
                    val surgeAmount = fareEstimate.totalEstimate * (fareEstimate.surgeFactor - 1.0)
                    FareSummaryItem("Surge", surgeAmount, currencyFormat)
                }
            }
        } else {
            // Fallback to mock data if no booking data available
            FareSummaryItem("Base fare", 1.50, currencyFormat)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = when {
                    actualFare != null -> currencyFormat.format(actualFare)
                    fareEstimate != null -> currencyFormat.format(fareEstimate.totalEstimate)
                    else -> "₱6" // Fallback - no decimals
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun FareSummaryItem(
    label: String,
    amount: Double,
    currencyFormat: NumberFormat,
    isDiscount: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDiscount) Color(0xFF198754) else Color.Black.copy(alpha = 0.7f)
        )
        Text(
            text = currencyFormat.format(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDiscount) Color(0xFF198754) else Color.Black.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RateDriverSection(
    uiState: RatingUiState,
    onRatingChange: (Int) -> Unit,
    onMessageToggle: (String) -> Unit
) {
    val driverUser = uiState.driverUser
    val driverStats = uiState.driverRatingStats
    
    Column {
        Text(
            text = "Rate your driver",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Driver avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = driverUser?.displayName?.takeIf { it.isNotEmpty() } ?: uiState.toUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                val vehicleInfo = driverUser?.driverData?.vehicleData
                val vehicleText = when {
                    vehicleInfo != null && vehicleInfo.make.isNotEmpty() && vehicleInfo.model.isNotEmpty() -> {
                        "${vehicleInfo.make} ${vehicleInfo.model}"
                    }
                    vehicleInfo?.make?.isNotEmpty() == true -> vehicleInfo.make
                    else -> "Bao-bao Driver"
                }
                
                Text(
                    text = vehicleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
        }
        
        // Overall rating display
        val overallRating = driverStats?.overallRating ?: driverUser?.driverData?.rating ?: 0.0
        val totalRatings = driverStats?.totalRatings ?: 0
        
        // Debug information
        println("DEBUG: Driver Stats - Overall: $overallRating, Total: $totalRatings")
        driverStats?.ratingBreakdown?.let { breakdown ->
            println("DEBUG: Rating Breakdown - 5:${breakdown.fiveStars}, 4:${breakdown.fourStars}, 3:${breakdown.threeStars}, 2:${breakdown.twoStars}, 1:${breakdown.oneStar}")
        }
        
        if (overallRating > 0.0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = String.format("%.1f", overallRating),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                StarRatingDisplay(rating = overallRating.toFloat())
            }
            
            if (totalRatings > 0) {
                Text(
                    text = "$totalRatings reviews",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Rating breakdown bars using real data
                val breakdown = driverStats?.ratingBreakdown
                if (breakdown != null && totalRatings > 0) {
                    val fiveStarPercent = (breakdown.fiveStars * 100) / totalRatings
                    val fourStarPercent = (breakdown.fourStars * 100) / totalRatings
                    val threeStarPercent = (breakdown.threeStars * 100) / totalRatings
                    val twoStarPercent = (breakdown.twoStars * 100) / totalRatings
                    val oneStarPercent = (breakdown.oneStar * 100) / totalRatings
                    
                    RatingBreakdownBar(5, fiveStarPercent)
                    RatingBreakdownBar(4, fourStarPercent)
                    RatingBreakdownBar(3, threeStarPercent)
                    RatingBreakdownBar(2, twoStarPercent)
                    RatingBreakdownBar(1, oneStarPercent)
                } else {
                    // Show sample data when no real data is available (for demo purposes)
                    Text(
                        text = "Loading rating distribution...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Fallback: Show some example data if driver has ratings but no breakdown stats
                    if (overallRating > 0.0) {
                        RatingBreakdownBar(5, 75)
                        RatingBreakdownBar(4, 15)
                        RatingBreakdownBar(3, 5)
                        RatingBreakdownBar(2, 3)
                        RatingBreakdownBar(1, 2)
                    } else {
                        RatingBreakdownBar(5, 0)
                        RatingBreakdownBar(4, 0)
                        RatingBreakdownBar(3, 0)
                        RatingBreakdownBar(2, 0)
                        RatingBreakdownBar(1, 0)
                    }
                }
            }
        } else {
            // New driver with no ratings yet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "New",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                StarRatingDisplay(rating = 0f)
            }
            
            Text(
                text = "No reviews yet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Interactive rating section for passenger to rate
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your Rating",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            InteractiveStarRating(
                rating = uiState.overallRating,
                onRatingChange = onRatingChange,
                size = 40.dp
            )
        }
        
        if (uiState.overallRating > 0) {
            Text(
                text = when (uiState.overallRating) {
                    5 -> "Excellent!"
                    4 -> "Good"
                    3 -> "Average"
                    2 -> "Below Average"
                    1 -> "Poor"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            // Add personalized messages section
            Spacer(modifier = Modifier.height(20.dp))
            PersonalizedMessagesSection(
                selectedMessages = uiState.personalizedMessages,
                isRatingDriver = true,
                onMessageToggle = onMessageToggle
            )
        }
    }
}

@Composable
private fun InteractiveStarRating(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) { index ->
            val starIndex = index + 1
            val isFilled = starIndex <= rating
            
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Star $starIndex",
                tint = if (isFilled) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(size)
                    .clickable { onRatingChange(starIndex) }
            )
        }
    }
}

@Composable
private fun StarRatingDisplay(rating: Float) {
    Row {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (index < rating) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RatingBreakdownBar(stars: Int, percentage: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stars.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.width(12.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .background(IslamovePrimary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.width(32.dp)
        )
    }
}

@Composable
private fun CommentSection(
    review: String,
    onReviewChange: (String) -> Unit
) {
    OutlinedTextField(
        value = review,
        onValueChange = onReviewChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        placeholder = {
            Text(
                "Add a comment",
                color = Color.Black.copy(alpha = 0.5f)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedBorderColor = Color.Black.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Black.copy(alpha = 0.3f),
            cursorColor = Color.Black
        ),
        maxLines = 5
    )
}

@Composable
private fun RatePassengerSection(
    uiState: RatingUiState,
    onRatingChange: (Int) -> Unit,
    onMessageToggle: (String) -> Unit
) {
    val passengerUser = uiState.passengerUser
    val passengerStats = uiState.passengerRatingStats

    Column {
        Text(
            text = "Rate your passenger",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Passenger avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = passengerUser?.displayName?.takeIf { it.isNotEmpty() } ?: uiState.toUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "Passenger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
        }

        // Display existing passenger ratings (similar to driver ratings)
        val overallRating = passengerStats?.overallRating ?: 0.0
        val totalRatings = passengerStats?.totalRatings ?: 0

        // Debug information (can be removed in production)
        android.util.Log.d("RatingScreen", "Passenger rating display: overallRating=$overallRating, totalRatings=$totalRatings")

        if (passengerStats != null) {
            // Show passenger rating info (even if they have 0 ratings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = String.format("%.1f", overallRating),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                StarRatingDisplay(rating = overallRating.toFloat())
            }

            Text(
                text = if (totalRatings == 0) "No reviews yet" else "$totalRatings review${if (totalRatings != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Rating distribution bars
            passengerStats?.ratingBreakdown?.let { breakdown ->
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    val fiveStarPercent = if (totalRatings > 0) (breakdown.fiveStars * 100) / totalRatings else 0
                    val fourStarPercent = if (totalRatings > 0) (breakdown.fourStars * 100) / totalRatings else 0
                    val threeStarPercent = if (totalRatings > 0) (breakdown.threeStars * 100) / totalRatings else 0
                    val twoStarPercent = if (totalRatings > 0) (breakdown.twoStars * 100) / totalRatings else 0
                    val oneStarPercent = if (totalRatings > 0) (breakdown.oneStar * 100) / totalRatings else 0

                    RatingBreakdownBar(5, fiveStarPercent)
                    RatingBreakdownBar(4, fourStarPercent)
                    RatingBreakdownBar(3, threeStarPercent)
                    RatingBreakdownBar(2, twoStarPercent)
                    RatingBreakdownBar(1, oneStarPercent)
                }
            }
        } else {
            // Show message when passenger stats are not loaded yet
            android.util.Log.d("RatingScreen", "passengerStats not loaded yet or failed to load")
            Text(
                text = "Loading passenger ratings...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Interactive rating section for driver to rate passenger
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your Rating",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            InteractiveStarRating(
                rating = uiState.overallRating,
                onRatingChange = onRatingChange,
                size = 40.dp
            )
        }

        if (uiState.overallRating > 0) {
            Text(
                text = when (uiState.overallRating) {
                    5 -> "Excellent!"
                    4 -> "Good"
                    3 -> "Average"
                    2 -> "Below Average"
                    1 -> "Poor"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
            // Add personalized messages section
            Spacer(modifier = Modifier.height(20.dp))
            PersonalizedMessagesSection(
                selectedMessages = uiState.personalizedMessages,
                isRatingDriver = false,
                onMessageToggle = onMessageToggle
            )
        }
    }
}

/**
 * Handle navigation after rating submission - separated for reusability and simpler debugging
 */
private suspend fun handleNavigationAfterRating(navController: androidx.navigation.NavController, bookingId: String) {
    try {
        android.util.Log.d("RatingScreen", "handleNavigationAfterRating called for booking: $bookingId")

        // Mark booking as rated in SharedPreferences
        val context = navController.context
        val sharedPreferences = context.getSharedPreferences("submitted_ratings", android.content.Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Get current user info
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        android.util.Log.d("RatingScreen", "Current user ID: $currentUserId")

        if (currentUserId != null) {
            try {
                android.util.Log.d("RatingScreen", "Fetching user document from Firestore...")
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .get()
                    .await()

                val currentUserType = userDoc.getString("userType") ?: "PASSENGER"
                android.util.Log.d("RatingScreen", "User type detected: $currentUserType")

                // Use user-type specific keys
                val userTypePrefix = currentUserType.lowercase()
                editor.putBoolean("${userTypePrefix}_rated_$bookingId", true)
                editor.putBoolean("${userTypePrefix}_rating_shown_$bookingId", true)
                editor.apply()
                android.util.Log.d("RatingScreen", "Marked booking $bookingId as rated by $currentUserType")

                // Navigate based on user type
                when (currentUserType) {
                    "DRIVER" -> {
                        android.util.Log.d("RatingScreen", "Driver completed rating, navigating back to driver home")

                        // Add small delay for UI stability
                        kotlinx.coroutines.delay(300)

                        // For drivers, simply navigate back so they can advance to the next queued ride
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate("driver_home")
                        }
                    }
                    "ADMIN" -> {
                        kotlinx.coroutines.delay(300)
                        navController.navigate("admin_home")
                    }
                    else -> {
                        kotlinx.coroutines.delay(300)
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate("passenger_home")
                        }
                    }
                }

                android.util.Log.d("RatingScreen", "Navigation completed successfully")

            } catch (e: Exception) {
                android.util.Log.e("RatingScreen", "Failed to get user type, using fallback", e)
                // Fallback: save as generic and navigate to passenger_home
                editor.putBoolean("rated_$bookingId", true)
                editor.putBoolean("rating_shown_$bookingId", true)
                editor.apply()

                kotlinx.coroutines.delay(300)
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    navController.navigate("passenger_home")
                }
            }
        } else {
            android.util.Log.w("RatingScreen", "No user found, using generic fallback")
            editor.putBoolean("rated_$bookingId", true)
            editor.putBoolean("rating_shown_$bookingId", true)
            editor.apply()

            kotlinx.coroutines.delay(300)
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                navController.navigate("passenger_home")
            }
        }

    } catch (e: Exception) {
        android.util.Log.e("RatingScreen", "Critical error in handleNavigationAfterRating", e)
        try {
            // Last resort - simple navigation
            navController.navigate("passenger_home")
        } catch (navEx: Exception) {
            android.util.Log.e("RatingScreen", "Even emergency navigation failed", navEx)
        }
    }
}
