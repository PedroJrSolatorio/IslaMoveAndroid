package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.User
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverVerificationScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDriverDetails: (String) -> Unit = {},
    onNavigateToStudentVerification: (String) -> Unit = {},
    viewModel: DriverVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPendingUsers()
    }

    // Date formatter for search
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Helper function to check if user matches search query (including date)
    fun matchesSearch(user: User, query: String): Boolean {
        if (query.isBlank()) return true

        val lowerQuery = query.lowercase()

        // Search by name
        if (user.displayName.contains(lowerQuery, ignoreCase = true)) return true

        // Search by phone
        if (user.phoneNumber.contains(lowerQuery, ignoreCase = true)) return true

        // Search by date format (2024-12-20, Dec 20, 2024, etc.)
        try {
            // Try different date formats
            val possibleDates = listOf(
                dateFormat.format(Date(user.createdAt)),
                dateTimeFormat.format(Date(user.createdAt)),
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(user.createdAt)),
                SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(user.createdAt)),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(user.createdAt))
                    .substringBefore("T")
            )

            if (possibleDates.any { it.contains(lowerQuery, ignoreCase = true) }) return true
        } catch (e: Exception) {
            // Date parsing failed, continue with other checks
        }

        return false
    }

    // Filter user types based on search query
    val filteredDrivers = uiState.pendingDrivers.filter { driver ->
        matchesSearch(driver, searchQuery)
    }

    val filteredStudentPassengers = uiState.passengersWithStudentDocuments.filter { passenger ->
        matchesSearch(passenger, searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Status bar spacing
                Spacer(modifier = Modifier.height(32.dp))

                // Header with back button and title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Verification Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search by name or date...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
        }

        // Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredDrivers.isEmpty() && filteredStudentPassengers.isEmpty()) {
            EmptyStateCard()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Driver Applications Section
                if (filteredDrivers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Driver Applications (${filteredDrivers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(filteredDrivers) { driver ->
                        PendingApplicationCard(
                            user = driver,
                            onClick = { onNavigateToDriverDetails(driver.uid) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Student Document Verification Section
                if (filteredStudentPassengers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Passenger ID Verification (${filteredStudentPassengers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(filteredStudentPassengers) { passenger ->
                        StudentDocumentCard(
                            user = passenger,
                            onClick = { onNavigateToStudentVerification(passenger.uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingApplicationCard(
    user: User,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile image placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Check if user has profile image, otherwise use placeholder
                if (user.profileImageUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder with initials or default icon
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.displayName.take(1).uppercase().ifEmpty { "?" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name and submission date
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName.ifEmpty { "Unknown User" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Submitted: ${dateFormat.format(Date(user.createdAt))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron arrow
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "View Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No pending verifications",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "User verifications including driver applications, student documents, and profile reviews will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StudentDocumentCard(
    user: User,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val studentDoc = user.studentDocument

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile image placeholder with School icon overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Check if user has profile image, otherwise use placeholder
                if (user.profileImageUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.displayName.take(1).uppercase().ifEmpty { "?" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Student indicator overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(
                            color = IslamovePrimary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Student",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName.ifEmpty { "Unknown Student" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                studentDoc?.let { doc ->
                    Text(
                        text = "${doc.school} • ID: ${doc.studentIdNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Uploaded: ${dateFormat.format(Date(doc.uploadedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (studentDoc?.status) {
                    com.rj.islamove.data.models.DocumentStatus.APPROVED -> Color(0xFFE8F5E8)
                    com.rj.islamove.data.models.DocumentStatus.REJECTED -> Color(0xFFFFEBEE)
                    else -> Color(0xFFFFF3CD)
                }
            ) {
                Text(
                    text = when (studentDoc?.status) {
                        com.rj.islamove.data.models.DocumentStatus.APPROVED -> "Approved"
                        com.rj.islamove.data.models.DocumentStatus.REJECTED -> "Rejected"
                        com.rj.islamove.data.models.DocumentStatus.PENDING_REVIEW -> "Review"
                        else -> "Pending"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = when (studentDoc?.status) {
                        com.rj.islamove.data.models.DocumentStatus.APPROVED -> Color(0xFF2E7D32)
                        com.rj.islamove.data.models.DocumentStatus.REJECTED -> Color(0xFFC62828)
                        else -> Color(0xFFE65100)
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

