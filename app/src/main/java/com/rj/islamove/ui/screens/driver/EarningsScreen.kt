package com.rj.islamove.ui.screens.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    viewModel: EarningsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(driverId) {
        viewModel.loadDriverEarnings(driverId)
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Top App Bar
        TopAppBar(
            title = { Text("Earnings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        when (val state = uiState) {
            is EarningsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is EarningsUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadDriverEarnings(driverId) }
                    ) {
                        Text("Retry")
                    }
                }
            }
            
            is EarningsUiState.Success -> {
                EarningsContent(
                    earnings = state.earnings,
                    transactions = state.transactions,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun EarningsContent(
    earnings: DriverEarnings,
    transactions: List<EarningsTransaction>,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
        currency = Currency.getInstance("PHP")
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total Earnings Summary
        item {
            EarningsSummaryCard(earnings = earnings, currencyFormat = currencyFormat)
        }
        
        // Period Earnings
        item {
            PeriodEarningsCard(earnings = earnings, currencyFormat = currencyFormat)
        }
        
        // Recent Transactions Header
        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Transaction List
        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(transactions) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    currencyFormat = currencyFormat
                )
            }
        }
    }
}

@Composable
private fun EarningsSummaryCard(
    earnings: DriverEarnings,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                        text = "Total Earnings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = currencyFormat.format(earnings.totalEarnings),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Trips Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = earnings.tripsCompleted.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Average per Trip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (earnings.tripsCompleted > 0) 
                            currencyFormat.format(earnings.totalEarnings / earnings.tripsCompleted)
                        else "₱0.00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodEarningsCard(
    earnings: DriverEarnings,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Earnings by Period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Today's earnings
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayEarnings = earnings.dailyEarnings[today] ?: 0.0
            
            EarningsPeriodItem(
                icon = Icons.Default.DateRange,
                period = "Today",
                amount = currencyFormat.format(todayEarnings)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // This week's earnings
            val thisWeek = SimpleDateFormat("yyyy-'W'ww", Locale.getDefault()).format(Date())
            val weekEarnings = earnings.weeklyEarnings[thisWeek] ?: 0.0
            
            EarningsPeriodItem(
                icon = Icons.Default.DateRange,
                period = "This Week",
                amount = currencyFormat.format(weekEarnings)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // This month's earnings
            val thisMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val monthEarnings = earnings.monthlyEarnings[thisMonth] ?: 0.0
            
            EarningsPeriodItem(
                icon = Icons.Default.DateRange,
                period = "This Month",
                amount = currencyFormat.format(monthEarnings)
            )
        }
    }
}

@Composable
private fun EarningsPeriodItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    period: String,
    amount: String
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
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = period,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: EarningsTransaction,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trip Earnings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                            .format(Date(transaction.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyFormat.format(transaction.netEarnings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                    Text(
                        text = transaction.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (transaction.status) {
                            EarningsStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                            EarningsStatus.PROCESSED -> Color.Blue
                            EarningsStatus.PAID_OUT -> Color.Green
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fare: ${currencyFormat.format(transaction.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Commission: ${currencyFormat.format(transaction.commission)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}