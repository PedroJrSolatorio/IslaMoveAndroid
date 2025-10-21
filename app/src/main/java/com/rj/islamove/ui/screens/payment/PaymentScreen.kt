package com.rj.islamove.ui.screens.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    bookingId: String,
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(bookingId) {
        viewModel.loadBookingForPayment(bookingId)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 80.dp, bottom = 120.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        when (val state = uiState) {
            is PaymentUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is PaymentUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            is PaymentUiState.Success -> {
                PaymentSuccessContent(
                    payment = state.payment,
                    onNavigateBack = onNavigateBack
                )
            }
            
            is PaymentUiState.Ready -> {
                PaymentFormContent(
                    booking = state.booking,
                    fareBreakdown = state.fareBreakdown,
                    isProcessing = state.isProcessing,
                    onProcessPayment = viewModel::processPayment
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PaymentFormContent(
    booking: Booking,
    fareBreakdown: FareBreakdown,
    isProcessing: Boolean,
    onProcessPayment: (PaymentMethod, String?) -> Unit
) {
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedDiscountType by remember { mutableStateOf<String?>(null) }
    
    // Trip Summary Card
    TripSummaryCard(booking = booking)
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Fare Breakdown Card
    FareBreakdownCard(fareBreakdown = fareBreakdown)
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Discount Selection
    DiscountSelectionCard(
        selectedDiscountType = selectedDiscountType,
        onDiscountSelected = { selectedDiscountType = it }
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Payment Method Selection
    PaymentMethodCard(
        selectedPaymentMethod = selectedPaymentMethod,
        onPaymentMethodSelected = { selectedPaymentMethod = it }
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Process Payment Button
    Button(
        onClick = { onProcessPayment(selectedPaymentMethod, selectedDiscountType) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isProcessing
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (isProcessing) "Processing..." else "Complete Payment"
        )
    }
}

@Composable
private fun TripSummaryCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Trip Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.Green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pickup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = booking.pickupLocation.address,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.Red
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Destination",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = booking.destination.address,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            booking.route?.let { route ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatNavigationDistance(route.totalDistance),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${route.estimatedDuration} min",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FareBreakdownCard(fareBreakdown: FareBreakdown) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
        currency = Currency.getInstance("PHP")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Fare Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            FareLineItem("Fare (San Jose Municipal Rate)", fareBreakdown.baseFare, currencyFormat)
            
            if (fareBreakdown.surgeFare > 0) {
                FareLineItem("Surge Fare", fareBreakdown.surgeFare, currencyFormat)
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            FareLineItem("Subtotal", fareBreakdown.subtotal, currencyFormat)
            
            if (fareBreakdown.discount > 0) {
                FareLineItem(
                    "Discount (${fareBreakdown.discountType})", 
                    -fareBreakdown.discount, 
                    currencyFormat,
                    color = Color.Green
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currencyFormat.format(fareBreakdown.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FareLineItem(
    label: String, 
    amount: Double, 
    currencyFormat: NumberFormat,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (amount != 0.0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
            Text(
                text = currencyFormat.format(amount),
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun DiscountSelectionCard(
    selectedDiscountType: String?,
    onDiscountSelected: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Discount (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val discountOptions = listOf(
                null to "No Discount",
                "SENIOR" to "Senior Citizen (20% off)",
                "PWD" to "PWD (20% off)",
                "STUDENT" to "Student (20% off)"
            )
            
            discountOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedDiscountType == value,
                            onClick = { onDiscountSelected(value) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDiscountType == value,
                        onClick = { onDiscountSelected(value) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label)
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    selectedPaymentMethod: PaymentMethod,
    onPaymentMethodSelected: (PaymentMethod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Only Cash payment available
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = true, // Always selected since it's the only option
                    onClick = { onPaymentMethodSelected(PaymentMethod.CASH) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Cash Payment")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Pay the driver directly with cash. A receipt will be generated for your records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentSuccessContent(
    payment: Payment,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Green
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Payment Successful!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
                    currency = Currency.getInstance("PHP")
                }
                
                Text(
                    text = "Amount: ${currencyFormat.format(payment.amount)}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "Method: ${payment.paymentMethod.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                payment.receipt?.let { receipt ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Receipt #${receipt.receiptNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

private fun formatNavigationDistance(meters: Double): String {
    return when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> String.format("%.1f km", meters / 1000)
    }
}