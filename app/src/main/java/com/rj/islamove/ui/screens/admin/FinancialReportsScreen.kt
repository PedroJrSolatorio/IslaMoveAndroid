package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.repository.RevenueAnalytics
import com.rj.islamove.data.models.TimePeriod
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FinancialReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFinancialData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Header with back button and export options
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
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Financial Reports",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Revenue analysis and financial insights",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Period selector
            FilterChip(
                onClick = { viewModel.toggleTimePeriod() },
                label = { 
                    Text(
                        text = when(uiState.selectedPeriod) {
                            TimePeriod.TODAY -> "Today"
                            TimePeriod.WEEK -> "7 Days"
                            TimePeriod.MONTH -> "30 Days"
                            TimePeriod.YEAR -> "Year"
                        }
                    )
                },
                selected = true,
                leadingIcon = {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Export button
            OutlinedButton(
                onClick = { viewModel.exportFinancialReport() }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export")
            }
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
            uiState.revenueAnalytics?.let { analytics ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        RevenueOverviewSection(analytics)
                    }
                    
                    item {
                        RevenueBreakdownSection(analytics)
                    }
                    
                    item {
                        DailyRevenueSection(analytics)
                    }
                    
                    item {
                        CommissionAnalysisSection(analytics)
                    }
                    
                    item {
                        PerformanceMetricsSection(analytics)
                    }
                }
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RevenueOverviewSection(analytics: RevenueAnalytics) {
    val currency = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Revenue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = currency.format(analytics.totalRevenue),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Period: ${getPeriodDescription(analytics.period)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            
            Text(
                text = "Last updated: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(analytics.lastUpdated))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun RevenueBreakdownSection(analytics: RevenueAnalytics) {
    Column {
        Text(
            text = "Revenue Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                RevenueBreakdownItem(
                    title = "Platform Commission",
                    amount = analytics.platformCommission,
                    percentage = (analytics.platformCommission / analytics.totalRevenue) * 100,
                    icon = Icons.Default.Star,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                RevenueBreakdownItem(
                    title = "Driver Earnings",
                    amount = analytics.driverEarnings,
                    percentage = (analytics.driverEarnings / analytics.totalRevenue) * 100,
                    icon = Icons.Default.Person,
                    color = Color(0xFF2196F3)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Revenue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(analytics.totalRevenue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyRevenueSection(analytics: RevenueAnalytics) {
    Column {
        Text(
            text = "Daily Revenue Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (analytics.dailyRevenue.isEmpty()) {
                    Text(
                        text = "No daily revenue data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(analytics.dailyRevenue.toList().takeLast(7)) { (date, revenue) ->
                            DailyRevenueItem(date = date, revenue = revenue)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val avgDaily = analytics.dailyRevenue.values.average()
                    Text(
                        text = "Average daily revenue: ${NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(avgDaily)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommissionAnalysisSection(analytics: RevenueAnalytics) {
    Column {
        Text(
            text = "Commission Analysis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommissionCard(
                title = "Commission Rate",
                value = "${"%.1f".format((analytics.platformCommission / analytics.totalRevenue) * 100)}%",
                subtitle = "Of total revenue",
                icon = Icons.Default.Star,
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
            
            CommissionCard(
                title = "Avg Commission",
                value = "₱${"%.0f".format(analytics.platformCommission / maxOf(analytics.dailyRevenue.size, 1))}",
                subtitle = "Per ride",
                icon = Icons.Default.Info,
                color = Color(0xFF9C27B0),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PerformanceMetricsSection(analytics: RevenueAnalytics) {
    Column {
        Text(
            text = "Performance Indicators",
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
                    PerformanceIndicator(
                        title = "Revenue Growth",
                        value = "+12.5%",
                        subtitle = "vs last period",
                        color = Color(0xFF4CAF50)
                    )
                    
                    PerformanceIndicator(
                        title = "Commission Share",
                        value = "${"%.1f".format((analytics.platformCommission / analytics.totalRevenue) * 100)}%",
                        subtitle = "Platform take",
                        color = Color(0xFF2196F3)
                    )
                    
                    PerformanceIndicator(
                        title = "Driver Share",
                        value = "${"%.1f".format((analytics.driverEarnings / analytics.totalRevenue) * 100)}%",
                        subtitle = "Driver earnings",
                        color = Color(0xFF9C27B0)
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueBreakdownItem(
    title: String,
    amount: Double,
    percentage: Double,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${"%.1f".format(percentage)}% of total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Text(
            text = NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DailyRevenueItem(
    date: String,
    revenue: Double
) {
    Card(
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date) ?: Date()
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₱${"%.0f".format(revenue / 1000)}k",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CommissionCard(
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
                style = MaterialTheme.typography.headlineSmall,
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
private fun PerformanceIndicator(
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

private fun getPeriodDescription(period: TimePeriod): String {
    return when (period) {
        TimePeriod.TODAY -> "Today"
        TimePeriod.WEEK -> "Last 7 days"
        TimePeriod.MONTH -> "Last 30 days"
        TimePeriod.YEAR -> "Last 12 months"
    }
}