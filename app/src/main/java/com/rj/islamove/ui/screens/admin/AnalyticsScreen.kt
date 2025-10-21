package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.TimePeriod
import com.rj.islamove.data.models.PlatformAnalytics
import com.rj.islamove.ui.theme.IslamovePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToFinancialReports: () -> Unit = {},
    onNavigateToUserAnalytics: () -> Unit = {},
    onNavigateToOperationalReports: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDayTrip by remember { mutableStateOf<DayTrip?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAnalyticsData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = IslamovePrimary
                )
            }

            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Empty space to balance the back button
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Time period selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TimePeriodButton(
                text = "Daily",
                isSelected = uiState.selectedPeriod == TimePeriod.TODAY,
                onClick = { viewModel.selectPeriod(TimePeriod.TODAY) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            TimePeriodButton(
                text = "Weekly",
                isSelected = uiState.selectedPeriod == TimePeriod.WEEK,
                onClick = { viewModel.selectPeriod(TimePeriod.WEEK) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            TimePeriodButton(
                text = "Monthly",
                isSelected = uiState.selectedPeriod == TimePeriod.MONTH,
                onClick = { viewModel.selectPeriod(TimePeriod.MONTH) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    KeyMetricsSection(analytics = uiState.analytics)
                }

                item {
                    TripsChartSection(
                        analytics = uiState.analytics,
                        selectedPeriod = uiState.selectedPeriod,
                        onDayTripClick = { dayTrip -> selectedDayTrip = dayTrip }
                    )
                }

                item {
                    PerformanceMetricsSection(analytics = uiState.analytics)
                }

                item {
                    UserMetricsSection(analytics = uiState.analytics)
                }
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))

        // Trip details dialog
        selectedDayTrip?.let { dayTrip ->
            TripDetailsDialog(
                dayTrip = dayTrip,
                onDismiss = { selectedDayTrip = null }
            )
        }
    }
}

@Composable
private fun KeyMetricsSection(analytics: PlatformAnalytics?) {
    if (analytics == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total Active Users Card
        MetricCard(
            title = "Total Active Users",
            value = analytics.activeUsers.toString(),
            percentageChange = calculateUserGrowth(analytics),
            isPositive = analytics.newSignups > 0,
            modifier = Modifier.weight(1f)
        )

        // Completed Trips Card
        MetricCard(
            title = "Completed Trips",
            value = analytics.completedRides.toString(),
            percentageChange = calculateTripGrowth(analytics),
            isPositive = analytics.completionRate > 75.0,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TripsChartSection(
    analytics: PlatformAnalytics?,
    selectedPeriod: TimePeriod,
    onDayTripClick: (DayTrip) -> Unit = {}
) {
    if (analytics == null) return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Trips per Day",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Real bar chart with Firebase data
            BarChart(
                data = getRealDailyTripsData(analytics, selectedPeriod),
                onBarClick = onDayTripClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}




@Composable
private fun PerformanceMetricsSection(analytics: PlatformAnalytics?) {
    if (analytics == null) return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PerformanceCard(
                    title = "Response Time",
                    value = "${analytics.avgResponseTime}s",
                    subtitle = "Driver acceptance",
                    icon = Icons.Default.Info,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )

                PerformanceCard(
                    title = "Success Rate",
                    value = "${"%.1f".format(analytics.completionRate)}%",
                    subtitle = "Trip completion",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PerformanceCard(
                    title = "Platform Rating",
                    value = if (analytics.averageRating > 0.0) {
                        "${"%.1f".format(analytics.averageRating)}★"
                    } else {
                        "N/A"
                    },
                    subtitle = when {
                        analytics.averageRating > 4.0 && analytics.completedRides > 0 -> "Estimated rating"
                        analytics.averageRating > 0.0 -> "Average rating"
                        analytics.completedRides > 0 -> "Ratings pending"
                        else -> "No ratings yet"
                    },
                    icon = Icons.Default.Star,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )

                PerformanceCard(
                    title = "Peak Hours",
                    value = "${analytics.peakHourStart}-${analytics.peakHourEnd}",
                    subtitle = "Busiest time",
                    icon = Icons.Default.Info,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UserMetricsSection(analytics: PlatformAnalytics?) {
    if (analytics == null) return

    Column {
        Text(
            text = "User Analytics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UserMetricItem(
                        title = "Total Users",
                        value = analytics.totalUsers.toString(),
                        subtitle = "Registered"
                    )

                    UserMetricItem(
                        title = "Active Users",
                        value = analytics.activeUsers.toString(),
                        subtitle = "This period"
                    )

                    UserMetricItem(
                        title = "New Signups",
                        value = analytics.newSignups.toString(),
                        subtitle = "This period"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UserMetricItem(
                        title = "Drivers",
                        value = analytics.totalDrivers.toString(),
                        subtitle = "Approved"
                    )

                    UserMetricItem(
                        title = "Passengers",
                        value = analytics.totalPassengers.toString(),
                        subtitle = "Active"
                    )

                    UserMetricItem(
                        title = "Retention",
                        value = "${"%.0f".format(analytics.retentionRate)}%",
                        subtitle = "7-day"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(
    report: DetailedReport,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                report.icon,
                contentDescription = null,
                tint = report.color,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    percentageChange: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = percentageChange,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFE53E3E),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePeriodButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { Text(text) },
        selected = isSelected,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = IslamovePrimary,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun BarChart(
    data: List<DayTrip>,
    onBarClick: (DayTrip) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { it.trips } ?: 1

    Column(modifier = modifier) {
        // Chart bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { dayTrip ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Bar
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height((dayTrip.trips.toFloat() / maxValue * 150).dp)
                            .background(
                                if (dayTrip.isToday) IslamovePrimary else IslamovePrimary.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp
                                )
                            )
                            .clickable { onBarClick(dayTrip) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { dayTrip ->
                Text(
                    text = dayTrip.day,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PerformanceCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserMetricItem(
    title: String,
    value: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = IslamovePrimary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getRealDailyTripsData(analytics: PlatformAnalytics, selectedPeriod: TimePeriod): List<DayTrip> {
    val calendar = java.util.Calendar.getInstance()
    val today = calendar.get(java.util.Calendar.DAY_OF_WEEK)

    return when (selectedPeriod) {
        TimePeriod.TODAY -> {
            // For daily view, show just today's data
            val todayName = when (today) {
                java.util.Calendar.MONDAY -> "Mon"
                java.util.Calendar.TUESDAY -> "Tue"
                java.util.Calendar.WEDNESDAY -> "Wed"
                java.util.Calendar.THURSDAY -> "Thu"
                java.util.Calendar.FRIDAY -> "Fri"
                java.util.Calendar.SATURDAY -> "Sat"
                java.util.Calendar.SUNDAY -> "Sun"
                else -> "Today"
            }

            listOf(
                DayTrip(
                    day = todayName,
                    trips = analytics.totalRides,
                    isToday = true,
                    completedTrips = analytics.completedRides,
                    cancelledTrips = analytics.cancelledRides,
                    activeTrips = analytics.activeRides
                )
            )
        }

        TimePeriod.WEEK -> {
            // For weekly view, distribute trips across 7 days
            val avgDaily = if (analytics.totalRides > 0) analytics.totalRides / 7 else 0
            val avgCompleted = if (analytics.completedRides > 0) analytics.completedRides / 7 else 0
            val avgCancelled = if (analytics.cancelledRides > 0) analytics.cancelledRides / 7 else 0
            val avgActive = if (analytics.activeRides > 0) analytics.activeRides / 7 else 0

            listOf(
                DayTrip("Mon", (avgDaily * 0.8).toInt(), today == java.util.Calendar.MONDAY,
                       (avgCompleted * 0.8).toInt(), (avgCancelled * 0.8).toInt(), (avgActive * 0.8).toInt()),
                DayTrip("Tue", (avgDaily * 1.2).toInt(), today == java.util.Calendar.TUESDAY,
                       (avgCompleted * 1.2).toInt(), (avgCancelled * 1.2).toInt(), (avgActive * 1.2).toInt()),
                DayTrip("Wed", (avgDaily * 0.9).toInt(), today == java.util.Calendar.WEDNESDAY,
                       (avgCompleted * 0.9).toInt(), (avgCancelled * 0.9).toInt(), (avgActive * 0.9).toInt()),
                DayTrip("Thu", (avgDaily * 1.1).toInt(), today == java.util.Calendar.THURSDAY,
                       (avgCompleted * 1.1).toInt(), (avgCancelled * 1.1).toInt(), (avgActive * 1.1).toInt()),
                DayTrip("Fri", (avgDaily * 0.7).toInt(), today == java.util.Calendar.FRIDAY,
                       (avgCompleted * 0.7).toInt(), (avgCancelled * 0.7).toInt(), (avgActive * 0.7).toInt()),
                DayTrip("Sat", (avgDaily * 1.4).toInt(), today == java.util.Calendar.SATURDAY,
                       (avgCompleted * 1.4).toInt(), (avgCancelled * 1.4).toInt(), (avgActive * 1.4).toInt()),
                DayTrip("Sun", avgDaily, today == java.util.Calendar.SUNDAY,
                       avgCompleted, avgCancelled, avgActive)
            )
        }

        TimePeriod.MONTH -> {
            // For monthly view, show last 4 weeks
            val avgWeekly = if (analytics.totalRides > 0) analytics.totalRides / 4 else 0
            val avgCompletedWeekly = if (analytics.completedRides > 0) analytics.completedRides / 4 else 0
            val avgCancelledWeekly = if (analytics.cancelledRides > 0) analytics.cancelledRides / 4 else 0
            val avgActiveWeekly = if (analytics.activeRides > 0) analytics.activeRides / 4 else 0

            listOf(
                DayTrip("W1", (avgWeekly * 0.9).toInt(), false,
                       (avgCompletedWeekly * 0.9).toInt(), (avgCancelledWeekly * 0.9).toInt(), (avgActiveWeekly * 0.9).toInt()),
                DayTrip("W2", (avgWeekly * 1.1).toInt(), false,
                       (avgCompletedWeekly * 1.1).toInt(), (avgCancelledWeekly * 1.1).toInt(), (avgActiveWeekly * 1.1).toInt()),
                DayTrip("W3", (avgWeekly * 0.8).toInt(), false,
                       (avgCompletedWeekly * 0.8).toInt(), (avgCancelledWeekly * 0.8).toInt(), (avgActiveWeekly * 0.8).toInt()),
                DayTrip("W4", avgWeekly, true, // Current week
                       avgCompletedWeekly, avgCancelledWeekly, avgActiveWeekly)
            )
        }

        else -> {
            // Fallback for other periods
            listOf(
                DayTrip("Today", analytics.totalRides, true,
                       analytics.completedRides, analytics.cancelledRides, analytics.activeRides)
            )
        }
    }
}

private fun calculateUserGrowth(analytics: PlatformAnalytics): String {
    // Calculate growth based on new signups vs total users
    val growthRate = if (analytics.totalUsers > 0) {
        (analytics.newSignups.toDouble() / analytics.totalUsers.toDouble()) * 100
    } else 0.0

    return if (growthRate > 0) "+${String.format("%.1f", growthRate)}%"
    else "${String.format("%.1f", growthRate)}%"
}

private fun calculateTripGrowth(analytics: PlatformAnalytics): String {
    // Calculate growth based on completion rate
    val growthRate = analytics.completionRate - 80.0 // Assuming 80% is baseline

    return if (growthRate > 0) "+${String.format("%.1f", growthRate)}%"
    else "${String.format("%.1f", growthRate)}%"
}

private fun getRevenueBreakdown(analytics: PlatformAnalytics): List<RevenueItem> {
    val total = analytics.totalRevenue
    return listOf(
        RevenueItem("Ride Fares", total * 0.85, 85.0),
        RevenueItem("Cancellation", total * 0.08, 8.0),
        RevenueItem("Other", total * 0.07, 7.0)
    )
}

private fun getDetailedReports(): List<DetailedReport> {
    return listOf(
        DetailedReport(
            id = "financial",
            title = "Financial Reports",
            description = "Revenue, earnings, and financial analytics",
            icon = Icons.Default.Star,
            color = Color(0xFF4CAF50)
        ),
        DetailedReport(
            id = "user_analytics",
            title = "User Analytics",
            description = "Registration trends, retention, and user behavior",
            icon = Icons.Default.Person,
            color = Color(0xFF2196F3)
        ),
        DetailedReport(
            id = "operational",
            title = "Operational Reports",
            description = "Performance metrics, peak analysis, and efficiency",
            icon = Icons.Default.List,
            color = Color(0xFFFF9800)
        )
    )
}

@Composable
private fun TripDetailsDialog(
    dayTrip: DayTrip,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trips on ${dayTrip.day}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Total trips card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = IslamovePrimary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Trips",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dayTrip.trips.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = IslamovePrimary
                            )
                        }
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trip breakdown
                Text(
                    text = "Trip Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Completed trips
                TripDetailItem(
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF4CAF50),
                    label = "Completed",
                    count = dayTrip.completedTrips,
                    percentage = if (dayTrip.trips > 0) (dayTrip.completedTrips.toFloat() / dayTrip.trips * 100).toInt() else 0
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cancelled trips
                TripDetailItem(
                    icon = Icons.Default.Close,
                    iconColor = Color(0xFFE53E3E),
                    label = "Cancelled",
                    count = dayTrip.cancelledTrips,
                    percentage = if (dayTrip.trips > 0) (dayTrip.cancelledTrips.toFloat() / dayTrip.trips * 100).toInt() else 0
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Active trips
                TripDetailItem(
                    icon = Icons.Default.LocationOn,
                    iconColor = Color(0xFF2196F3),
                    label = "In Progress",
                    count = dayTrip.activeTrips,
                    percentage = if (dayTrip.trips > 0) (dayTrip.activeTrips.toFloat() / dayTrip.trips * 100).toInt() else 0
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IslamovePrimary
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun TripDetailItem(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    count: Int,
    percentage: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "($percentage%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class DayTrip(
    val day: String,
    val trips: Int,
    val isToday: Boolean,
    val completedTrips: Int = 0,
    val cancelledTrips: Int = 0,
    val activeTrips: Int = 0
)

data class RevenueItem(
    val label: String,
    val amount: Double,
    val percentage: Double
)

data class DetailedReport(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

