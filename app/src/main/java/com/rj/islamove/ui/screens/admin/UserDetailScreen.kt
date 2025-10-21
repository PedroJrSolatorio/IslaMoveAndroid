package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DriverDocument
import com.rj.islamove.data.models.SupportComment
import com.rj.islamove.data.models.DriverReport
import com.rj.islamove.data.models.ReportType
import com.rj.islamove.data.models.ReportStatus
import com.rj.islamove.data.models.VerificationStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    user: User,
    userComments: List<SupportComment> = emptyList(),
    driverReports: List<DriverReport> = emptyList(),
    onNavigateBack: () -> Unit = {},
    onViewTripHistory: () -> Unit = {},
    onNavigateToDocumentDetails: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateDiscount: (String, Int?) -> Unit = { _, _ -> },
    onUpdateVerification: (String, Boolean) -> Unit = { _, _ -> },
    onUpdatePersonalInfo: (String, Map<String, Any>) -> Unit = { _, _ -> },
    onResetPassword: (String) -> Unit = { _ -> },
    onUpdateActiveStatus: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateReportStatus: (String, ReportStatus) -> Unit = { _, _ -> },
    onUpdateDriverVerification: (String, VerificationStatus) -> Unit = { _, _ -> },
    onUpdatePassword: (String, String) -> Unit = { _, _ -> },
    onDeleteUser: (User) -> Unit = { _ -> },
) {
    var selectedDiscount by remember { mutableStateOf(user.discountPercentage) }
    var isVerified by remember { mutableStateOf(user.studentDocument?.status == DocumentStatus.APPROVED) }
    var isActive by remember { mutableStateOf(user.isActive) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStatusUpdateDialog by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<DriverReport?>(null) }
    var selectedNewStatus by remember { mutableStateOf<ReportStatus?>(null) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var selectedVerificationStatus by remember { mutableStateOf(VerificationStatus.PENDING) }
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
        
    // Debug logging to check discount field
    androidx.compose.runtime.LaunchedEffect(user) {
        android.util.Log.d("UserDetailScreen", "User data: ${user.displayName}")
        android.util.Log.d("UserDetailScreen", "Discount percentage: ${user.discountPercentage}")
        android.util.Log.d("UserDetailScreen", "User type: ${user.userType}")
    }

    // Handle messages
    LaunchedEffect(actionMessage, errorMessage) {
        if (actionMessage != null || errorMessage != null) {
            // Auto-clear messages after 3 seconds
            kotlinx.coroutines.delay(3000)
            actionMessage = null
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        UserDetailHeader(onNavigateBack = onNavigateBack)

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Profile Section
            ProfileSection(
                user = user,
                onDeleteUser = onDeleteUser,
                onShowDeleteConfirmation = { showDeleteConfirmationDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Personal Information Section
            PersonalInformationSection(
                user = user,
                onEditField = { field, value ->
                    editingField = field
                    editValue = value
                    showEditDialog = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Account Status Section (for all users)
            AccountStatusSection(
                user = user,
                selectedDiscount = selectedDiscount,
                isVerified = isVerified,
                isActive = isActive,
                onDiscountChange = { discount ->
                    selectedDiscount = discount
                    onUpdateDiscount(user.uid, discount)
                    actionMessage = if (discount != null) "Discount set to $discount%" else "Discount removed"
                },
                onVerificationChange = { verified ->
                    isVerified = verified
                    // CRITICAL: If verification is removed, automatically reset discount to null
                    if (!verified && selectedDiscount != null) {
                        selectedDiscount = null
                        onUpdateDiscount(user.uid, null)
                        actionMessage = "Passenger verification removed and discount reset to None"
                    } else {
                        onUpdateVerification(user.uid, verified)
                        actionMessage = if (verified) "Passenger verified" else "Passenger verification removed"
                    }
                },
                onActiveStatusChange = { active ->
                    isActive = active
                    onUpdateActiveStatus(user.uid, active)
                    actionMessage = if (active) "User activated" else "User blocked"
                },
                onEditDriverVerification = if (user.userType == UserType.DRIVER) {
                    {
                        showVerificationDialog = true
                        selectedVerificationStatus = user.driverData?.verificationStatus ?: VerificationStatus.PENDING
                    }
                } else {
                    null
                }
            )

            // Driver Documents Section (for drivers only)
            if (user.userType == UserType.DRIVER && user.driverData != null) {
                Spacer(modifier = Modifier.height(24.dp))
                DriverDocumentsSection(
                    user = user,
                    onNavigateToDocumentDetails = onNavigateToDocumentDetails
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Activity History Section
            ActivityHistorySection(onViewTripHistory = onViewTripHistory)

            Spacer(modifier = Modifier.height(24.dp))

            // Support Comments Section
            SupportCommentsSection(comments = userComments)

            Spacer(modifier = Modifier.height(24.dp))

            // Driver Reports Section - Only show for drivers
            if (user.userType == UserType.DRIVER) {
                DriverReportsSection(
                    reports = driverReports,
                    onReportClick = { report ->
                        selectedReport = report
                        showStatusUpdateDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Show action/error messages
            if (actionMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(actionMessage ?: "")
                }
            }

            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(errorMessage ?: "")
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        var newValue by remember { mutableStateOf(editValue) }

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                newValue = editValue
            },
            title = {
                Text(
                    text = when (editingField) {
                        "plainTextPassword" -> "Edit Password"
                        "resetPassword" -> "Reset Password"
                        else -> "Edit ${editingField.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                if (editingField == "resetPassword") {
                    Column {
                        Text(
                            text = "Send a password reset email to this user?",
                            fontSize = 14.sp,
                            color = Color(0xFF333333)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Email: $newValue",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFF8F9FA),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    var showPassword by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = {
                            Text(
                                when (editingField) {
                                    "plainTextPassword" -> "Password"
                                    else -> editingField.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (editingField == "plainTextPassword") {
                            if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            if (editingField == "plainTextPassword") {
                                val image = if (showPassword)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff

                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(imageVector = image, "")
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingField == "resetPassword") {
                            onResetPassword(newValue)
                            actionMessage = "Password reset email sent to user"
                        } else if (newValue != editValue) {
                            if (editingField == "plainTextPassword") {
                                onUpdatePassword(user.uid, newValue)
                                actionMessage = "Password updated successfully"
                            } else {
                                onUpdatePersonalInfo(user.uid, mapOf(editingField to newValue))
                                actionMessage = "Personal information updated"
                            }
                        }
                        showEditDialog = false
                        newValue = editValue
                    }
                ) {
                    Text(if (editingField == "resetPassword") "Send Reset Email" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        newValue = editValue
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Status Update Dialog
    if (showStatusUpdateDialog && selectedReport != null) {
        var selectedStatus by remember { mutableStateOf(selectedReport!!.status) }

        AlertDialog(
            onDismissRequest = {
                showStatusUpdateDialog = false
                selectedReport = null
            },
            title = {
                Text(
                    text = "Update Report Status",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Report ID: ${selectedReport!!.id.take(8)}",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "Report Type: ${selectedReport!!.reportType.name.replace("_", " ")}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select new status:",
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status selection radio buttons
                    val statusOptions = listOf(
                        ReportStatus.PENDING to "Pending Review",
                        ReportStatus.UNDER_REVIEW to "Under Review",
                        ReportStatus.RESOLVED to "Resolved",
                        ReportStatus.DISMISSED to "Dismissed"
                    )

                    statusOptions.forEach { (status, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStatus = status },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = when (status) {
                                        ReportStatus.PENDING -> Color(0xFFF57C00)
                                        ReportStatus.UNDER_REVIEW -> Color(0xFF2196F3)
                                        ReportStatus.RESOLVED -> Color(0xFF4CAF50)
                                        ReportStatus.DISMISSED -> Color(0xFF9E9E9E)
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = Color(0xFF333333)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedStatus != selectedReport!!.status) {
                            onUpdateReportStatus(selectedReport!!.id, selectedStatus)
                            actionMessage = "Report status updated to ${when (selectedStatus) {
                                ReportStatus.PENDING -> "Pending Review"
                                ReportStatus.UNDER_REVIEW -> "Under Review"
                                ReportStatus.RESOLVED -> "Resolved"
                                ReportStatus.DISMISSED -> "Dismissed"
                            }}"
                        }
                        showStatusUpdateDialog = false
                        selectedReport = null
                    },
                    enabled = selectedStatus != selectedReport!!.status
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStatusUpdateDialog = false
                        selectedReport = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Verification Status Update Dialog
    if (showVerificationDialog && user.userType == UserType.DRIVER) {
        val currentStatus = user.driverData?.verificationStatus ?: VerificationStatus.PENDING
        val currentDiscount = user.discountPercentage

        AlertDialog(
            onDismissRequest = {
                showVerificationDialog = false
            },
            title = {
                Text("Update Verification Status")
            },
            text = {
                Column {
                    Text("Current Status: ${currentStatus.name}")
                    if (currentDiscount != null) {
                        Text("Current Discount: $currentDiscount%")
                        Text(
                            text = "⚠️  Changing verification status from APPROVED will reset discount to None",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Select new status:")
                    Spacer(modifier = Modifier.height(8.dp))

                    com.rj.islamove.data.models.VerificationStatus.entries.forEach { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedVerificationStatus == status,
                                onClick = { selectedVerificationStatus = status }
                            )
                            Text(
                                text = status.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateDriverVerification(user.uid, selectedVerificationStatus)
                        showVerificationDialog = false
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showVerificationDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmationDialog = false
            },
            title = {
                Text(
                    text = "Confirm Permanent Deletion",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFD32F2F)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to permanently delete this user?",
                        fontSize = 16.sp,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone.",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "User: ${user.displayName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "Email: ${user.email ?: "Not provided"}",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "User ID: ${user.uid.take(8)}",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showDeleteConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SupportCommentsSection(comments: List<SupportComment>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Support Comments",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (comments.isEmpty()) {
                Text(
                    text = "No support comments submitted",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                comments.forEach { comment ->
                    CommentItem(comment = comment)
                    if (comment != comments.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: SupportComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Date
            Text(
                text = formatDate(comment.timestamp),
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Comment message
            Text(
                text = comment.message,
                fontSize = 14.sp,
                color = Color(0xFF333333),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun DriverReportsSection(reports: List<DriverReport>, onReportClick: (DriverReport) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Driver Reports",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (reports.isEmpty()) {
                Text(
                    text = "No reports submitted",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                reports.forEach { report ->
                    ReportItem(report = report, onReportClick = onReportClick)
                    if (report != reports.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportItem(report: DriverReport, onReportClick: (DriverReport) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onReportClick(report)
            },
        colors = CardDefaults.cardColors(
            containerColor = when (report.reportType) {
                ReportType.UNSAFE_DRIVING -> Color(0xFFFFEBEE)
                ReportType.INAPPROPRIATE_BEHAVIOR -> Color(0xFFFFF3E0)
                else -> Color(0xFFF8F9FA)
            }
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with report type and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (report.reportType) {
                        ReportType.UNSAFE_DRIVING -> "Unsafe Driving"
                        ReportType.INAPPROPRIATE_BEHAVIOR -> "Inappropriate Behavior"
                        ReportType.VEHICLE_CONDITION -> "Vehicle Condition"
                        ReportType.ROUTE_ISSUES -> "Route Issues"
                        ReportType.HYGIENE_CONCERNS -> "Hygiene Concerns"
                        ReportType.OVERCHARGING -> "Overcharging"
                        ReportType.CANCELLATION_ABUSE -> "Cancellation Abuse"
                        ReportType.OTHER -> "Other"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (report.reportType) {
                        ReportType.UNSAFE_DRIVING -> Color(0xFFD32F2F)
                        ReportType.INAPPROPRIATE_BEHAVIOR -> Color(0xFFF57C00)
                        else -> Color(0xFF333333)
                    }
                )

                Text(
                    text = formatDate(report.timestamp),
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reporter info
            Text(
                text = "Reported by: ${report.passengerName}",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )

            if (report.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = report.description,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status badge
            Text(
                text = when (report.status) {
                    com.rj.islamove.data.models.ReportStatus.PENDING -> "Pending Review"
                    com.rj.islamove.data.models.ReportStatus.UNDER_REVIEW -> "Under Review"
                    com.rj.islamove.data.models.ReportStatus.RESOLVED -> "Resolved"
                    com.rj.islamove.data.models.ReportStatus.DISMISSED -> "Dismissed"
                },
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(
                        color = when (report.status) {
                            com.rj.islamove.data.models.ReportStatus.PENDING -> Color(0xFFF57C00)
                            com.rj.islamove.data.models.ReportStatus.UNDER_REVIEW -> Color(0xFF2196F3)
                            com.rj.islamove.data.models.ReportStatus.RESOLVED -> Color(0xFF4CAF50)
                            com.rj.islamove.data.models.ReportStatus.DISMISSED -> Color(0xFF9E9E9E)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun UserDetailHeader(
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Text(
            text = "User Details",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ProfileSection(
    user: User,
    onDeleteUser: (User) -> Unit = { _ -> },
    onShowDeleteConfirmation: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Image with Online Status
        Box(
            modifier = Modifier.size(100.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F0))
                    .border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl.isNullOrEmpty()) {
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                } else {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = "Profile Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Online Status Indicator (for drivers)
            if (user.userType == UserType.DRIVER) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (user.driverData?.online == true) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        .border(3.dp, Color.White, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User Name
        Text(
            text = user.displayName.ifEmpty { "No Name" },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // User ID
        Text(
            text = "User ID: ${user.uid.take(8)}",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Joined Date
        Text(
            text = "Joined: ${formatDate(user.createdAt)}",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Permanently Delete User Button - Only show for non-admin users
        if (user.userType != UserType.ADMIN) {
            Button(
                onClick = onShowDeleteConfirmation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F), // Red color for delete
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete User",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PersonalInformationSection(
    user: User,
    onEditField: (String, String) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Personal Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full Name
            EditableInfoRow(
                label = "Full Name",
                value = user.displayName.ifEmpty { "Not provided" },
                fieldName = "Full Name",
                onEdit = onEditField
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Email
            InfoRow(
                label = "Email",
                value = user.email ?: "Not provided"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phone Number
            EditableInfoRow(
                label = "Phone Number",
                value = user.phoneNumber.ifEmpty { "Not provided" },
                fieldName = "Phone Number",
                onEdit = onEditField
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Password",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (showPassword) (user.plainTextPassword ?: "Not available") else "●●●●●●●●",
                        fontSize = 14.sp,
                        color = Color.Black,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )

                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onEditField("plainTextPassword", user.plainTextPassword ?: "") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Edit Password",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User Type
            InfoRow(
                label = "User Type",
                value = when (user.userType) {
                    UserType.PASSENGER -> "Passenger"
                    UserType.DRIVER -> "Driver"
                    UserType.ADMIN -> "Admin"
                }
            )

            // Common fields for all users
            // Date of Birth
            if (user.dateOfBirth != null) {
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(
                    label = "Date of Birth",
                    value = user.dateOfBirth
                )
            }

            // Gender
            if (user.gender != null) {
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(
                    label = "Gender",
                    value = user.gender
                )
            }

            // Address
            if (user.address != null) {
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(
                    label = "Address",
                    value = user.address
                )
            }

            // Passenger-specific fields (Valid ID only)
            if (user.userType == UserType.PASSENGER) {

                // Valid ID Status
                if (user.studentDocument != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Valid ID",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )

                        Text(
                            text = when (user.studentDocument.status) {
                                DocumentStatus.APPROVED -> "Verified"
                                DocumentStatus.PENDING_REVIEW -> "Pending Review"
                                DocumentStatus.REJECTED -> "Rejected"
                                DocumentStatus.PENDING -> "Uploaded"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (user.studentDocument.status) {
                                DocumentStatus.APPROVED -> Color(0xFF4CAF50)
                                DocumentStatus.PENDING_REVIEW -> Color(0xFFF57C00)
                                DocumentStatus.REJECTED -> Color(0xFFD32F2F)
                                DocumentStatus.PENDING -> Color(0xFF9E9E9E)
                            },
                            modifier = Modifier
                                .background(
                                    color = when (user.studentDocument.status) {
                                        DocumentStatus.APPROVED -> Color(0xFFE8F5E8)
                                        DocumentStatus.PENDING_REVIEW -> Color(0xFFFFF3E0)
                                        DocumentStatus.REJECTED -> Color(0xFFFFEBEE)
                                        DocumentStatus.PENDING -> Color(0xFFF5F5F5)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Show Valid ID image if available
                    if (user.studentDocument.studentIdUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = user.studentDocument.studentIdUrl,
                            contentDescription = "Valid ID",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStatusSection(
    user: User,
    selectedDiscount: Int?,
    isVerified: Boolean,
    isActive: Boolean,
    onDiscountChange: (Int?) -> Unit,
    onVerificationChange: (Boolean) -> Unit,
    onActiveStatusChange: (Boolean) -> Unit,
    onEditDriverVerification: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Account Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Account Status Toggle - Only show for passenger users
            if (user.userType == UserType.PASSENGER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isActive) "Active" else "Blocked",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                        )

                        Switch(
                            checked = isActive,
                            onCheckedChange = onActiveStatusChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFFE8F5E8),
                                uncheckedThumbColor = Color(0xFFD32F2F),
                                uncheckedTrackColor = Color(0xFFFFEBEE)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Driver specific status
            if (user.userType == UserType.DRIVER && user.driverData != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verification",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = user.driverData.verificationStatus.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (user.driverData.verificationStatus.name) {
                                "APPROVED" -> Color(0xFF4CAF50)
                                "REJECTED" -> Color(0xFFD32F2F)
                                "PENDING" -> Color(0xFFF57C00)
                                "UNDER_REVIEW" -> Color(0xFF1976D2)
                                else -> Color(0xFF9E9E9E)
                            },
                            modifier = Modifier
                                .background(
                                    color = when (user.driverData.verificationStatus.name) {
                                        "APPROVED" -> Color(0xFFE8F5E8)
                                        "REJECTED" -> Color(0xFFFFEBEE)
                                        "PENDING" -> Color(0xFFFFF3E0)
                                        "UNDER_REVIEW" -> Color(0xFFE3F2FD)
                                        else -> Color(0xFFF5F5F5)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Verification Status",
                            tint = Color(0xFF007AFF),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    onEditDriverVerification?.invoke()
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Online Status",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )

                    Text(
                        text = if (user.driverData.online) "Online" else "Offline",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (user.driverData.online) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                        modifier = Modifier
                            .background(
                                color = if (user.driverData.online) Color(0xFFE8F5E8) else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Passenger verification and discount controls
            if (user.userType == UserType.PASSENGER) {
                // Verification status toggle for passengers
                if (user.studentDocument != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verification",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isVerified) "Verified" else "Not Verified",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isVerified) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                modifier = Modifier
                                    .background(
                                        color = if (isVerified) Color(0xFFE8F5E8) else Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            Switch(
                                checked = isVerified,
                                onCheckedChange = onVerificationChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF4CAF50),
                                    checkedTrackColor = Color(0xFFE8F5E8),
                                    uncheckedThumbColor = Color(0xFF9E9E9E),
                                    uncheckedTrackColor = Color(0xFFF5F5F5)
                                )
                            )
                        }
                    }
                }

                // Discount controls for passengers
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discount",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // No Discount
                        FilterChip(
                            selected = selectedDiscount == null,
                            onClick = { onDiscountChange(null) },
                            label = { Text("None") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF5F5F5),
                                selectedLabelColor = Color(0xFF666666)
                            )
                        )

                        // 20% Discount - Only enabled if verified
                        FilterChip(
                            selected = selectedDiscount == 20,
                            onClick = { onDiscountChange(20) },
                            label = { Text("20%") },
                            enabled = isVerified,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE8F5E8),
                                selectedLabelColor = Color(0xFF4CAF50)
                            )
                        )

                        // 50% Discount - Only enabled if verified
                        FilterChip(
                            selected = selectedDiscount == 50,
                            onClick = { onDiscountChange(50) },
                            label = { Text("50%") },
                            enabled = isVerified,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE3F2FD),
                                selectedLabelColor = Color(0xFF2196F3)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHistorySection(onViewTripHistory: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Activity History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewTripHistory() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Trip History",
                    fontSize = 16.sp,
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "View Trip History",
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF666666),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EditableInfoRow(
    label: String,
    value: String,
    fieldName: String,
    onEdit: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF666666)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )

            IconButton(
                onClick = { onEdit(fieldName, value) },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Edit $label",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DriverDocumentsSection(
    user: User,
    onNavigateToDocumentDetails: (String, String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Driver Documents",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            val documents = user.driverData?.documents ?: emptyMap()

            if (documents.isEmpty()) {
                Text(
                    text = "No documents uploaded",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                documents.forEach { (documentType, document) ->
                    DocumentItem(
                        documentType = documentType,
                        document = document,
                        onClick = {
                            val documentTitle = getDocumentDisplayName(documentType)
                            onNavigateToDocumentDetails(user.uid, documentType, documentTitle)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Show verification notes if available
            user.driverData?.verificationNotes?.let { notes ->
                if (notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verification Notes:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notes,
                        fontSize = 14.sp,
                        color = Color(0xFF333333),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF8F9FA),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentItem(
    documentType: String,
    document: DriverDocument,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFF8F9FA),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF666666)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = getDocumentDisplayName(documentType),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                val uniqueImageCount = document.images.distinctBy { it.url }.size
                Text(
                    text = "${uniqueImageCount} image${if (uniqueImageCount != 1) "s" else ""} • Uploaded ${formatDate(document.uploadedAt)}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )

                // Show rejection reason if document was rejected
                if (document.status == DocumentStatus.REJECTED && !document.rejectionReason.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rejected: ${document.rejectionReason}",
                        fontSize = 11.sp,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusIcon, statusColor, statusBgColor) = when (document.status) {
                DocumentStatus.APPROVED -> Triple(
                    Icons.Default.Check,
                    Color(0xFF4CAF50),
                    Color(0xFFE8F5E8)
                )
                DocumentStatus.REJECTED -> Triple(
                    Icons.Default.Close,
                    Color(0xFFD32F2F),
                    Color(0xFFFFEBEE)
                )
                DocumentStatus.PENDING_REVIEW -> Triple(
                    Icons.Default.Info,
                    Color(0xFFF57C00),
                    Color(0xFFFFF3E0)
                )
                DocumentStatus.PENDING -> Triple(
                    Icons.Default.Info,
                    Color(0xFF9E9E9E),
                    Color(0xFFF5F5F5)
                )
            }

            Icon(
                statusIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = statusBgColor,
                        shape = CircleShape
                    )
                    .padding(6.dp),
                tint = statusColor
            )
        }
    }

  }

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(date)
}

private fun getDocumentDisplayName(documentType: String): String {
    return when (documentType) {
        "license" -> "Driver's License"
        "insurance" -> "SJMODA Certification"
        "vehicle_inspection" -> "Official Receipt (OR)"
        "vehicle_registration" -> "Certificate of Registration (CR)"
        "profile_photo" -> "Profile Photo"
        else -> documentType.replace("_", " ").split(" ").joinToString(" ") {
            it.replaceFirstChar { char -> char.uppercase() }
        }
    }
}