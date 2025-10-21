package com.rj.islamove.ui.screens.driver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.ui.theme.IslamoveSecondary
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DocumentImage
import com.rj.islamove.ui.viewmodels.DriverDocumentsViewModel

data class DocumentType(
    val id: String,
    val title: String,
    val description: String,
    val isRequired: Boolean,
    val isUploaded: Boolean = false,
    val status: DocumentStatus = DocumentStatus.PENDING_REVIEW,
    val imageUrl: String? = null,
    val rejectionReason: String? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDocumentsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    isPassengerMode: Boolean = false,
    viewModel: DriverDocumentsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var selectedDocument by remember { mutableStateOf<DocumentType?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()

    LaunchedEffect(driverId, isPassengerMode) {
        viewModel.loadDriverDocuments(driverId, isPassengerMode)
    }

    // Show error or success messages
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // You can show a snackbar or toast here
            // For now, the error will be displayed in the UI
        }
    }

    uiState.successMessage?.let { message ->
        LaunchedEffect(message) {
            // You can show a snackbar or toast here
            // For now, the success message will be displayed in the UI
        }
    }

    // Document types - different for passengers vs drivers
    val documentTypes = remember(documents, isPassengerMode) {
        if (isPassengerMode) {
            // For passengers, only show ID upload
            listOf(
                DocumentType(
                    id = "passenger_id",
                    title = "Valid ID",
                    description = when (documents["passenger_id"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Upload your ID for verification"
                    },
                    isRequired = true,
                    isUploaded = documents["passenger_id"]?.images?.isNotEmpty() == true,
                    status = documents["passenger_id"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["passenger_id"]?.rejectionReason,
                    icon = Icons.Default.Person
                )
            )
        } else {
            // For drivers, show all required documents
            listOf(
                DocumentType(
                    id = "license",
                    title = "Driver's License",
                    description = when (documents["license"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Pending"
                    },
                    isRequired = true,
                    isUploaded = documents["license"]?.images?.isNotEmpty() == true,
                    status = documents["license"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["license"]?.rejectionReason,
                    icon = Icons.Default.Person
                ),
                DocumentType(
                    id = "insurance",
                    title = "SJMODA Certification",
                    description = when (documents["insurance"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Pending"
                    },
                    isRequired = true,
                    isUploaded = documents["insurance"]?.images?.isNotEmpty() == true,
                    status = documents["insurance"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["insurance"]?.rejectionReason,
                    icon = Icons.Default.Warning
                ),
                DocumentType(
                    id = "vehicle_inspection",
                    title = "Official Receipt (OR)",
                    description = when (documents["vehicle_inspection"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Pending"
                    },
                    isRequired = true,
                    isUploaded = documents["vehicle_inspection"]?.images?.isNotEmpty() == true,
                    status = documents["vehicle_inspection"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["vehicle_inspection"]?.rejectionReason,
                    icon = Icons.Default.Check
                ),
                DocumentType(
                    id = "vehicle_registration",
                    title = "Certificate of Registration (CR)",
                    description = when (documents["vehicle_registration"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Pending"
                    },
                    isRequired = true,
                    isUploaded = documents["vehicle_registration"]?.images?.isNotEmpty() == true,
                    status = documents["vehicle_registration"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["vehicle_registration"]?.rejectionReason,
                    icon = Icons.Default.Check
                ),
                DocumentType(
                    id = "profile_photo",
                    title = "Profile Photo",
                    description = when (documents["profile_photo"]?.status) {
                        DocumentStatus.APPROVED -> "Approved"
                        DocumentStatus.REJECTED -> "Rejected"
                        DocumentStatus.PENDING_REVIEW -> "Under Review"
                        else -> "Pending"
                    },
                    isRequired = true,
                    isUploaded = documents["profile_photo"]?.images?.isNotEmpty() == true,
                    status = documents["profile_photo"]?.status ?: DocumentStatus.PENDING,
                    rejectionReason = documents["profile_photo"]?.rejectionReason,
                    icon = Icons.Default.Person
                )
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedDocument?.let { document ->
                // Add images to pending list instead of uploading immediately
                viewModel.addPendingImages(
                    documentType = document.id,
                    imageUris = uris
                )
            }
        }
        selectedDocument = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = if (isPassengerMode) "ID Verification" else "Upload Documents",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color.Black,
                navigationIconContentColor = Color.Black
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Show error message if any
            uiState.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.clearMessages() }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            // Show success message if any
            uiState.successMessage?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = IslamoveSecondary.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                color = IslamoveSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.clearMessages() }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = if (isPassengerMode) "ID Verification" else "Required Documents",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Show rejection messages if any documents are rejected
                val rejectedDocuments = documentTypes.filter { it.status == DocumentStatus.REJECTED }
                if (rejectedDocuments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Rejection Warning",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Document${if (rejectedDocuments.size > 1) "s" else ""} Rejected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF44336)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            rejectedDocuments.forEach { doc ->
                                Column(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "• ${doc.title}:",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFF44336)
                                    )
                                    Text(
                                        text = doc.rejectionReason ?: "No reason provided",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666),
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please upload new images for the rejected documents and resubmit for review.",
                                fontSize = 12.sp,
                                color = Color(0xFF666666),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(documentTypes) { document ->
                Column {
                    ModernDocumentItem(
                        document = document,
                        onUploadClick = {
                            selectedDocument = document
                            documentPickerLauncher.launch("image/*")
                        }
                    )

                    // Show pending images preview for this document
                    val docPendingImages = pendingImages[document.id] ?: emptyList()
                    if (docPendingImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(docPendingImages) { imageUri ->
                                PendingImagePreview(
                                    imageUri = imageUri,
                                    onRemove = { viewModel.removePendingImage(document.id, imageUri) }
                                )
                            }
                        }

                        Text(
                            text = "${docPendingImages.size} image${if (docPendingImages.size > 1) "s" else ""} selected (not uploaded yet)",
                            fontSize = 12.sp,
                            color = Color(0xFF2196F3),
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Instructions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2196F3).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isPassengerMode)
                            "Upload a clear photo of your ID document for verification. Make sure the ID is legible and shows your name and other relevant information. Your ID will be reviewed within 24-48 hours."
                        else
                            "Please ensure all documents are clear, legible, and valid. You can upload multiple images per document if needed. After uploading your images, click 'Submit for Review' to send them to admin. Documents will be reviewed within 24-48 hours.",
                        fontSize = 14.sp,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(16.dp),
                        lineHeight = 20.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))

                // Submit for review button
                val hasPendingDocs = documentTypes.any {
                    it.status == DocumentStatus.PENDING && it.isUploaded
                }
                val hasRejectedDocs = documentTypes.any {
                    it.status == DocumentStatus.REJECTED && it.isUploaded
                }
                val hasPendingImages = pendingImages.isNotEmpty()
                // Button is always enabled when not uploading

                // Always show the submit button
                if (true) { // Button always visible
                    Button(
                        onClick = {
                            viewModel.submitForReview(context, driverId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isUploading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IslamovePrimary
                        )
                    ) {
                        if (uiState.isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting...")
                        } else {
                            Text(
                                text = when {
                                    hasRejectedDocs -> "Resubmit for Review"
                                    hasPendingImages -> "Upload and Submit for Review"
                                    hasPendingDocs -> "Submit for Review"
                                    else -> "Submit Documents"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            hasRejectedDocs -> "After resubmitting, you won't be able to upload more images until admin reviews your updated documents."
                            hasPendingImages -> "Selected images will be uploaded when you submit. You can still add or remove images before submitting."
                            else -> "After submitting, you won't be able to upload more images until admin reviews your documents."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ModernDocumentItem(
    document: DocumentType,
    onUploadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Document icon with status indicator
        Box(
            modifier = Modifier.size(48.dp)
        ) {
            // Main icon background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = document.icon,
                    contentDescription = document.title,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Status indicator
            when {
                document.status == DocumentStatus.APPROVED -> {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Approved",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                document.status == DocumentStatus.REJECTED -> {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xFFF44336),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Rejected",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Document info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = document.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = document.description,
                fontSize = 14.sp,
                color = when {
                    document.status == DocumentStatus.APPROVED -> Color(0xFF4CAF50)
                    document.status == DocumentStatus.REJECTED -> Color(0xFFF44336)
                    else -> Color(0xFF757575)
                }
            )

            // Show rejection reason if document is rejected
            if (document.status == DocumentStatus.REJECTED && !document.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${document.rejectionReason}",
                    fontSize = 12.sp,
                    color = Color(0xFFF44336),
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp
                )
            }
        }

        // Action button
        when {
            document.status == DocumentStatus.APPROVED -> {
                // Show checkmark circle for approved documents, no button needed
            }
            document.status == DocumentStatus.PENDING_REVIEW -> {
                // Show disabled state for pending review documents
                Button(
                    onClick = { /* No action for pending review */ },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        disabledContainerColor = Color(0xFFE0E0E0)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Under Review",
                        fontSize = 12.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
            document.status == DocumentStatus.REJECTED -> {
                Button(
                    onClick = onUploadClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Re-upload",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
            else -> {
                Button(
                    onClick = onUploadClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Upload",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentImageItem(
    image: DocumentImage,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Box {
        Card(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = image.description,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (canRemove) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clickable { onRemove() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (image.description.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = image.description,
                    color = Color.White,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PendingImagePreview(
    imageUri: Uri,
    onRemove: () -> Unit
) {
    Box {
        Card(
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Pending image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .clickable { onRemove() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}