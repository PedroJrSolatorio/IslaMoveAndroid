package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DriverDocument
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailsScreen(
    driverUid: String,
    documentType: String,
    documentTitle: String,
    onNavigateBack: () -> Unit,
    viewModel: DriverDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var comments by remember { mutableStateOf("") }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Load driver details if not already loaded
    LaunchedEffect(driverUid) {
        if (uiState.driver == null) {
            viewModel.loadDriverDetails(driverUid)
        }
    }

    // Get the document from the appropriate source
    val document = when (documentType) {
        "passenger_id" -> {
            // For passenger ID, get from studentDocument and convert to DriverDocument format
            uiState.driver?.studentDocument?.let { studentDoc ->
                if (studentDoc.studentIdUrl.isNotEmpty()) {
                    DriverDocument(
                        images = listOf(
                            com.rj.islamove.data.models.DocumentImage(
                                url = studentDoc.studentIdUrl,
                                description = "Valid ID",
                                uploadedAt = studentDoc.uploadedAt
                            )
                        ),
                        status = studentDoc.status,
                        rejectionReason = studentDoc.rejectionReason
                    )
                } else null
            }
        }
        else -> {
            // For driver documents, get from driverData
            uiState.driver?.driverData?.documents?.get(documentType)
        }
    }

    // Update comments when document changes
    LaunchedEffect(document) {
        comments = document?.rejectionReason ?: ""
    }

    if (document == null) {
        // Show loading or error state
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Document not found",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column {
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Document Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Document Images Section
                Column {
                    // Deduplicate images by URL to avoid showing duplicates
                    val uniqueImages = document.images.distinctBy { it.url }

                    Text(
                        text = "Document Images (${uniqueImages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (uniqueImages.isNotEmpty()) {
                        if (uniqueImages.size == 1) {
                            // Single image - show larger
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                AsyncImage(
                                    model = uniqueImages.first().url,
                                    contentDescription = documentTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            // Multiple images - show in scrollable row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uniqueImages) { image ->
                                    Card(
                                        modifier = Modifier
                                            .width(300.dp)
                                            .height(400.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        AsyncImage(
                                            model = image.url,
                                            contentDescription = "${documentTitle} - ${image.description}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No image available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Document Information Section
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Document Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Document Type
                    InfoRow(
                        label = "Document Type",
                        value = documentTitle
                    )

                    // Upload Date
                    InfoRow(
                        label = "Upload Date",
                        value = dateFormat.format(Date(document.uploadedAt))
                    )

                    // Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val (statusText, statusColor) = when (document.status) {
                            DocumentStatus.APPROVED -> "Approved" to Color(0xFF4CAF50)
                            DocumentStatus.REJECTED -> "Rejected" to MaterialTheme.colorScheme.error
                            DocumentStatus.PENDING_REVIEW -> "Under Review" to MaterialTheme.colorScheme.primary
                            DocumentStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
                            DocumentStatus.EXPIRED -> "Expired" to MaterialTheme.colorScheme.outline
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }

                    // Comments Section
                    Column {
                        Text(
                            text = "Rejection Reason / Comments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = comments,
                            onValueChange = { comments = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            placeholder = {
                                Text(
                                    text = if (document.status == DocumentStatus.REJECTED) {
                                        "Edit rejection reason..."
                                    } else {
                                        "Enter rejection reason (required to reject document)..."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            maxLines = 8,
                            enabled = document.status != DocumentStatus.APPROVED,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IslamovePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )

                        if (document.status != DocumentStatus.REJECTED && comments.isBlank()) {
                            Text(
                                text = "* Rejection reason is required to reject this document",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // White footer spacer
                Spacer(modifier = Modifier.height(80.dp))
            }

            // Action Buttons
            if (document.status != DocumentStatus.APPROVED) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (comments.isNotBlank()) {
                                    viewModel.rejectDocument(driverUid, documentType, comments)
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            enabled = document.status != DocumentStatus.REJECTED && comments.isNotBlank()
                        ) {
                            Text(
                                text = "Reject Document",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.approveDocument(driverUid, documentType)
                                onNavigateBack()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IslamovePrimary
                            ),
                            enabled = document.status != DocumentStatus.APPROVED
                        ) {
                            Text(
                                text = "Approve Document",
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Approved state message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Card(
                        modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "✓ Document Approved",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}