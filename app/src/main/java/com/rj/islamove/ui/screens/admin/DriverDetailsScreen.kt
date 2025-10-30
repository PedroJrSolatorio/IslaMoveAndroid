package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDetailsScreen(
    driverUid: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToDocumentDetails: (String, String, String, com.rj.islamove.data.models.DriverDocument) -> Unit = { _, _, _, _ -> },
    isStudentVerification: Boolean = false,
    viewModel: DriverDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(driverUid) {
        viewModel.loadDriverDetails(driverUid)
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
                            text = if (isStudentVerification) "ID Verification" else "Driver Application",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                uiState.driver?.let { driver ->
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Profile Section
                        item {
                            ProfileSection(driver = driver)
                        }

                        // Discount Settings Section (only for verified passengers)
                        if (driver.userType == com.rj.islamove.data.models.UserType.PASSENGER &&
                            driver.studentDocument?.status == com.rj.islamove.data.models.DocumentStatus.APPROVED) {
                            item {
                                DiscountSettingsSection(
                                    user = driver,
                                    onUpdateDiscount = { discountPercentage ->
                                        viewModel.updateUserDiscount(driver.uid, discountPercentage)
                                    }
                                )
                            }
                        }

                        if (isStudentVerification) {
                            // ID Information Section
                            item {
                                IDInformationSection(driver = driver)
                            }

                            // Valid ID Section (only Valid ID)
                            item {
                                IDDocumentSection(
                                    driver = driver,
                                    onDocumentClick = { docType, docTitle, document ->
                                        onNavigateToDocumentDetails(driverUid, docType, docTitle, document)
                                    }
                                )
                            }
                        } else {
                            // Personal Information
                            item {
                                PersonalInformationSection(driver = driver)
                            }

                            // Documents Section
                            item {
                                DocumentsSection(
                                    driver = driver,
                                    onDocumentClick = { docType, docTitle, document ->
                                        onNavigateToDocumentDetails(driverUid, docType, docTitle, document)
                                    }
                                )
                            }
                        }
                    }

                    // Driver Status Display at Bottom
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .navigationBarsPadding()
                        ) {
                            if (isStudentVerification) {
                                IDStatusDisplay(
                                    driver = driver,
                                    onApproveID = {
                                        viewModel.approveStudentDocument(driverUid)
                                    },
                                    onRejectID = { rejectionReason ->
                                        viewModel.rejectStudentDocument(driverUid, rejectionReason)
                                    }
                                )
                            } else {
                                DriverStatusDisplay(
                                    driver = driver,
                                    onApproveDriver = {
                                        viewModel.approveDriver(driverUid)
                                    },
                                    onRejectDriver = {
                                        // For now, use a default rejection reason
                                        // In a more complete implementation, you'd show a dialog for input
                                        viewModel.rejectDriver(driverUid, "Application rejected by admin")
                                    }
                                )
                            }
                        }
                    }
                } ?: run {
                    // Error state
                    uiState.errorMessage?.let { error ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(driver: User) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Image
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (driver.profileImageUrl?.isNotEmpty() == true) {
                AsyncImage(
                    model = driver.profileImageUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder with initials
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = driver.displayName.take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Driver Name
        Text(
            text = driver.displayName.ifEmpty { "Unknown Driver" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Application ID
        Text(
            text = "Application ID: ${driver.uid.take(6).uppercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PersonalInformationSection(driver: User) {
    Column {
        SectionTitle("Personal Information")
        Spacer(modifier = Modifier.height(16.dp))

        CleanInfoRow("Full Name", driver.displayName.ifEmpty { "Not provided" })
        CleanInfoRow("Email", driver.email ?: "Not provided")
        CleanInfoRow("Phone", driver.phoneNumber)
    }
}


@Composable
private fun DocumentsSection(
    driver: User,
    onDocumentClick: (String, String, com.rj.islamove.data.models.DriverDocument) -> Unit
) {
    val driverData = driver.driverData

    Column {
        SectionTitle("Documents")
        Spacer(modifier = Modifier.height(16.dp))

        if (driverData?.documents?.isNotEmpty() == true) {
            val documentTypes = mapOf(
                "license" to "Driver's License",
                "vehicle_registration" to "Certificate of Registration (CR)",
                "insurance" to "SJMODA Certification",
                "vehicle_inspection" to "Official Receipt (OR)"
            )

            documentTypes.forEach { (docType, title) ->
                val document = driverData.documents[docType]
                DocumentItem(
                    title = title,
                    hasDocument = document != null && document.images.isNotEmpty(),
                    status = document?.status,
                    onClick = {
                        document?.let { onDocumentClick(docType, title, it) }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            val defaultDocs = listOf(
                "Driver's License",
                "Certificate of Registration (CR)",
                "SJMODA Certification",
                "Official Receipt (OR)"
            )

            defaultDocs.forEach { title ->
                DocumentItem(
                    title = title,
                    hasDocument = false,
                    status = null,
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DriverStatusDisplay(
    driver: User,
    onApproveDriver: () -> Unit = {},
    onRejectDriver: () -> Unit = {}
) {
    val driverData = driver.driverData

    // Check if all required documents are approved
    val allDocumentsApproved = driverData?.documents?.let { docs ->
        val requiredDocTypes = listOf("license", "vehicle_registration", "insurance", "vehicle_inspection")
        // Check if all required documents exist and are approved
        val hasAllDocs = requiredDocTypes.all { docType ->
            docs.containsKey(docType) && docs[docType]?.images?.isNotEmpty() == true
        }

        val allApproved = requiredDocTypes.all { docType ->
            docs[docType]?.status == DocumentStatus.APPROVED
        }

        hasAllDocs && allApproved
    } ?: false

    when (driverData?.verificationStatus) {
        VerificationStatus.APPROVED -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = "✓ Driver Approved",
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4CAF50),
                    textAlign = TextAlign.Center
                )
            }
        }
        VerificationStatus.REJECTED -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "✗ Driver Application Rejected",
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
        VerificationStatus.UNDER_REVIEW -> {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRejectDriver,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Reject",
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onApproveDriver,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        enabled = allDocumentsApproved
                    ) {
                        Text(
                            text = "Approve",
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
                if (!allDocumentsApproved) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show which documents are missing or not approved
                    val requiredDocTypes = listOf("license", "vehicle_registration", "insurance", "vehicle_inspection")
                    val documentNames = mapOf(
                        "license" to "Driver's License",
                        "vehicle_registration" to "Certificate of Registration",
                        "insurance" to "SJMODA Certification",
                        "vehicle_inspection" to "Official Receipt"
                    )

                    val missingOrPending = requiredDocTypes.filter { docType ->
                        val doc = driverData?.documents?.get(docType)
                        doc == null || doc.images.isEmpty() || doc.status != DocumentStatus.APPROVED
                    }.mapNotNull { documentNames[it] }

                    Text(
                        text = if (missingOrPending.isNotEmpty()) {
                            "⚠️ Missing or pending: ${missingOrPending.joinToString(", ")}"
                        } else {
                            "⚠️ All documents must be approved before approving the driver application"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        else -> {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRejectDriver,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Reject",
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onApproveDriver,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        enabled = allDocumentsApproved
                    ) {
                        Text(
                            text = "Approve",
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                if (!allDocumentsApproved) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show which documents are missing or not approved
                    val requiredDocTypes = listOf("license", "vehicle_registration", "insurance", "vehicle_inspection")
                    val documentNames = mapOf(
                        "license" to "Driver's License",
                        "vehicle_registration" to "Certificate of Registration",
                        "insurance" to "SJMODA Certification",
                        "vehicle_inspection" to "Official Receipt"
                    )

                    val missingOrPending = requiredDocTypes.filter { docType ->
                        val doc = driverData?.documents?.get(docType)
                        doc == null || doc.images.isEmpty() || doc.status != DocumentStatus.APPROVED
                    }.mapNotNull { documentNames[it] }

                    Text(
                        text = if (missingOrPending.isNotEmpty()) {
                            "⚠️ Missing or pending: ${missingOrPending.joinToString(", ")}"
                        } else {
                            "⚠️ All documents must be approved before approving the driver application"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CleanInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DocumentItem(
    title: String,
    hasDocument: Boolean,
    status: DocumentStatus?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = title,
                tint = if (hasDocument) {
                    IslamovePrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            when (status) {
                DocumentStatus.APPROVED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Approved",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
                DocumentStatus.REJECTED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Rejected",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                DocumentStatus.PENDING_REVIEW -> {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Under Review",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⏳",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DocumentStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⌛",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                null -> {
                    // No status indicator for missing documents
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun IDInformationSection(driver: User) {
    Column {
        SectionTitle("ID Information")
        Spacer(modifier = Modifier.height(16.dp))

        // ID details - simplified for generic ID verification
        driver.studentDocument?.let { idDoc ->
            CleanInfoRow("Uploaded", SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(idDoc.uploadedAt)))
            CleanInfoRow("Status", when (idDoc.status) {
                DocumentStatus.APPROVED -> "Approved"
                DocumentStatus.REJECTED -> "Rejected"
                DocumentStatus.PENDING_REVIEW -> "Under Review"
                else -> "Pending"
            })
        } ?: run {
            CleanInfoRow("Status", "No ID document uploaded")
        }
    }
}

@Composable
private fun IDDocumentSection(
    driver: User,
    onDocumentClick: (String, String, com.rj.islamove.data.models.DriverDocument) -> Unit
) {
    Column {
        SectionTitle("Documents")
        Spacer(modifier = Modifier.height(16.dp))

        val idDoc = driver.studentDocument
        DocumentItem(
            title = "Valid ID",
            hasDocument = idDoc != null && idDoc.studentIdUrl.isNotEmpty(),
            status = idDoc?.status,
            onClick = {
                if (idDoc != null && idDoc.studentIdUrl.isNotEmpty()) {
                    // Create a fake DriverDocument for compatibility with existing navigation
                    val fakeDriverDocument = com.rj.islamove.data.models.DriverDocument(
                        images = listOf(
                            com.rj.islamove.data.models.DocumentImage(
                                url = idDoc.studentIdUrl,
                                description = "Valid ID",
                                uploadedAt = idDoc.uploadedAt
                            )
                        ),
                        status = idDoc.status,
                        rejectionReason = idDoc.rejectionReason
                    )
                    onDocumentClick("passenger_id", "Valid ID", fakeDriverDocument)
                }
            }
        )
    }
}

@Composable
private fun IDStatusDisplay(
    driver: User,
    onApproveID: () -> Unit,
    onRejectID: (String) -> Unit
) {
    val idDoc = driver.studentDocument
    val currentStatus = idDoc?.status

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Current Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Verification Status:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            StatusChip(
                status = when (currentStatus) {
                    DocumentStatus.APPROVED -> "Approved"
                    DocumentStatus.REJECTED -> "Rejected"
                    DocumentStatus.PENDING_REVIEW -> "Under Review"
                    else -> "Pending"
                },
                color = when (currentStatus) {
                    DocumentStatus.APPROVED -> Color(0xFF4CAF50)
                    DocumentStatus.REJECTED -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFFF9800)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rejection reason display (read-only)
        if (currentStatus == DocumentStatus.REJECTED && !idDoc?.rejectionReason.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Rejection Reason:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = idDoc?.rejectionReason ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action buttons
        if (currentStatus != DocumentStatus.APPROVED) {
            if (currentStatus != DocumentStatus.REJECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To approve or reject the ID, please click on the document above and review it individually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Approved state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = "✓ ID Approved",
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


@Composable
private fun StatusChip(
    status: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun DiscountSettingsSection(
    user: User,
    onUpdateDiscount: (Int?) -> Unit
) {
    var selectedDiscount by remember { mutableStateOf(user.discountPercentage) }
    var isUpdating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Discount Settings")
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Current discount display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Discount:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = when (selectedDiscount) {
                            null -> "No Discount"
                            20 -> "20% Discount"
                            50 -> "50% Discount"
                            else -> "${selectedDiscount}% Discount"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when (selectedDiscount) {
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                            20 -> Color(0xFF4CAF50)
                            50 -> Color(0xFF2196F3)
                            else -> IslamovePrimary
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Discount options
                Text(
                    text = "Select Discount:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // No Discount option
                    OutlinedButton(
                        onClick = {
                            selectedDiscount = null
                            isUpdating = true
                            onUpdateDiscount(null)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedDiscount == null)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (selectedDiscount == null)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (selectedDiscount == null)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        enabled = !isUpdating
                    ) {
                        Text(
                            text = "No Discount",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 20% Discount option
                    OutlinedButton(
                        onClick = {
                            selectedDiscount = 20
                            isUpdating = true
                            onUpdateDiscount(20)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedDiscount == 20)
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (selectedDiscount == 20)
                                Color(0xFF4CAF50)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (selectedDiscount == 20)
                            BorderStroke(1.dp, Color(0xFF4CAF50))
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        enabled = !isUpdating
                    ) {
                        Text(
                            text = "20%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 50% Discount option
                    OutlinedButton(
                        onClick = {
                            selectedDiscount = 50
                            isUpdating = true
                            onUpdateDiscount(50)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedDiscount == 50)
                                Color(0xFF2196F3).copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.surface,
                            contentColor = if (selectedDiscount == 50)
                                Color(0xFF2196F3)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (selectedDiscount == 50)
                            BorderStroke(1.dp, Color(0xFF2196F3))
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        enabled = !isUpdating
                    ) {
                        Text(
                            text = "50%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Update status indicator
                if (isUpdating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Updating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Helper text
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Discount will be applied to user's ride fares. Only available for verified passengers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

