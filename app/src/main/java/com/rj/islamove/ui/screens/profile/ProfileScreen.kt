package com.rj.islamove.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.ui.screens.driver.DriverHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToRideHistory: (String) -> Unit = {},
    onNavigateToDriverDocuments: (String) -> Unit = {},
    onNavigateToReviews: () -> Unit = {},
    onNavigateToHelpSupport: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Dialog states for popup editing
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf("") }
    var editingPhone by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }

    // Password change dialog states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileImageUri = uri
        uri?.let {
            viewModel.updateProfile(context, editingName, "", editingPhone, it)
        }
    }

    // Get DriverHomeViewModel if user is a driver
    val driverHomeViewModel: DriverHomeViewModel? = if (uiState.user?.userType == UserType.DRIVER) {
        hiltViewModel()
    } else {
        null
    }

    // Listen for auth state changes and cleanup driver services
    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                // User signed out - stop driver location services
                android.util.Log.d("ProfileScreen", "🧹 User signed out - stopping driver location services")
                driverHomeViewModel?.stopLocationUpdates()
            }
        }

        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)

        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        }
    }

    // Initialize edited values when user data loads
    LaunchedEffect(uiState.user) {
        uiState.user?.let { user ->
            editingName = user.displayName
            editingPhone = user.phoneNumber
        }
    }

    // Handle update success
    LaunchedEffect(uiState.updateSuccess) {
        if (uiState.updateSuccess) {
            viewModel.clearUpdateSuccess()
            // Close dialogs after successful save
            showPhoneDialog = false
            showEmailDialog = false
        }
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { viewModel.loadUserProfile() }) {
                    Text("Retry")
                }
            }
        }

        uiState.user != null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                item {

                    // User profile section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile picture with edit badge (clickable)
                        Box(
                            modifier = Modifier.clickable { showImagePicker = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = if (profileImageUri != null || !uiState.user!!.profileImageUrl.isNullOrEmpty()) Color.Transparent else Color(
                                            0xFFE8B68C
                                        ),
                                        shape = RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileImageUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(profileImageUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray.copy(alpha = 0.1f)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (!uiState.user!!.profileImageUrl.isNullOrEmpty()) {
                                    // Log the URL for debugging
                                    android.util.Log.d("ProfileScreen", "Loading profile image: ${uiState.user!!.profileImageUrl}")

                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(uiState.user!!.profileImageUrl)
                                                .crossfade(true)
                                                .listener(
                                                    onError = { _, result ->
                                                        android.util.Log.e("ProfileScreen", "Error loading profile image: ${result.throwable.message}")
                                                        android.util.Log.e("ProfileScreen", "URL was: ${uiState.user!!.profileImageUrl}")
                                                    },
                                                    onSuccess = { _, _ ->
                                                        android.util.Log.d("ProfileScreen", "Profile image loaded successfully")
                                                    }
                                                )
                                                .build(),
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            placeholder = androidx.compose.ui.res.painterResource(com.rj.islamove.R.drawable.ic_launcher_foreground),
                                            error = androidx.compose.ui.res.painterResource(com.rj.islamove.R.drawable.ic_launcher_foreground)
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            // Edit badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .background(
                                        color = IslamovePrimary,
                                        shape = RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // User name (read-only)
                        Text(
                            text = editingName.ifEmpty { "No name set" },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Driver verification status (for drivers only)
                        if (uiState.user!!.userType == UserType.DRIVER && uiState.user!!.driverData != null) {
                            Spacer(modifier = Modifier.height(6.dp))

                            val verificationStatus = uiState.user!!.driverData!!.verificationStatus
                            val (statusText, statusColor, statusIcon) = when (verificationStatus) {
                                VerificationStatus.APPROVED -> Triple(
                                    "Verified Driver",
                                    Color(0xFF4CAF50), // Green
                                    Icons.Default.CheckCircle
                                )
                                VerificationStatus.PENDING -> Triple(
                                    "⏳ Verification Pending",
                                    Color(0xFFFFA726), // Orange
                                    null
                                )
                                VerificationStatus.UNDER_REVIEW -> Triple(
                                    "👀 Under Review",
                                    Color(0xFF42A5F5), // Blue
                                    null
                                )
                                VerificationStatus.REJECTED -> Triple(
                                    "❌ Verification Rejected",
                                    Color(0xFFEF5350), // Red
                                    null
                                )
                            }

                            Card(
                                modifier = Modifier.wrapContentWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = statusColor.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (statusIcon != null) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = statusColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        // Account Status (for passengers and all users)
                        if (uiState.user!!.userType == UserType.PASSENGER) {
                            Spacer(modifier = Modifier.height(6.dp))

                            val (statusText, statusColor) = if (uiState.user!!.isActive) {
                                Pair("Active Account", Color(0xFF4CAF50)) // Green
                            } else {
                                Pair("Account Blocked", Color(0xFFEF5350)) // Red
                            }

                            Card(
                                modifier = Modifier.wrapContentWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = statusColor.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (uiState.user!!.isActive) Icons.Default.CheckCircle else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = statusColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Rating and trip count (show for both drivers and passengers)
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (uiState.user!!.userType) {
                                UserType.DRIVER -> {
                                    // Driver ratings from driverData
                                    val driverData = uiState.user!!.driverData
                                    if (driverData != null) {
                                        val rating = driverData.rating.takeIf { it > 0.0 } ?: 0.0
                                        val totalTrips = driverData.totalTrips.takeIf { it > 0 } ?: 0

                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (rating > 0.0) Color(0xFFFFA500) else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (rating > 0.0) String.format("%.2f", rating) else "No ratings yet",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (totalTrips > 0) {
                                            Text(
                                                text = " • $totalTrips trip${if (totalTrips == 1) "" else "s"}",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        // No driver data found
                                        Text(
                                            text = "Driver profile incomplete",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                UserType.PASSENGER -> {
                                    // Passenger ratings from top-level fields (with null safety)
                                    val rating = uiState.user!!.passengerRating?.takeIf { it > 0.0 } ?: 0.0
                                    val totalTrips = uiState.user!!.passengerTotalTrips?.takeIf { it > 0 } ?: 0

                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (rating > 0.0) Color(0xFFFFA500) else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (rating > 0.0) String.format("%.2f", rating) else "No ratings yet",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (totalTrips > 0) {
                                        Text(
                                            text = " • $totalTrips trip${if (totalTrips == 1) "" else "s"}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                UserType.ADMIN -> {
                                    // Admin - show admin badge
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Administrator",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Personal Information section
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Personal Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Email (Read-only for authentication security)
                        ProfileMenuItem(
                            icon = Icons.Default.Email,
                            title = "Email",
                            subtitle = uiState.user?.email ?: "Not provided",
                            onClick = {
                                showEmailDialog = true
                            }
                        )

                        // Phone Number
                        ProfileMenuItem(
                            icon = Icons.Default.Phone,
                            title = "Phone Number",
                            subtitle = uiState.user?.phoneNumber?.ifEmpty { "Not provided" } ?: "Not provided",
                            onClick = {
                                editingPhone = uiState.user?.phoneNumber ?: ""
                                showPhoneDialog = true
                            }
                        )

                        // Reviews Section
                        uiState.userRatingStats?.let { ratingStats ->
                            if (ratingStats.totalRatings > 0) {
                                Spacer(modifier = Modifier.height(16.dp))

                                ProfileMenuItem(
                                    icon = Icons.Default.Star,
                                    title = "My Reviews",
                                    subtitle = "${ratingStats.totalRatings} reviews • ${String.format("%.1f", ratingStats.overallRating)} stars",
                                    onClick = { onNavigateToReviews() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Settings section
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Privacy & Security
                        ProfileMenuItem(
                            icon = Icons.Default.Lock,
                            title = "Privacy & Security",
                            subtitle = "Password",
                            onClick = { showPasswordDialog = true }
                        )

                        // Documents section (for drivers only)
                        if (uiState.user!!.userType == UserType.DRIVER) {
                            val verificationStatus = uiState.user!!.driverData?.verificationStatus
                            val documentSubtitle = when (verificationStatus) {
                                VerificationStatus.APPROVED -> "Documents verified ✓"
                                VerificationStatus.PENDING -> "Verification pending - documents under review"
                                VerificationStatus.UNDER_REVIEW -> "Documents are being reviewed"
                                VerificationStatus.REJECTED -> "Documents rejected - please resubmit"
                                else -> "Upload and manage your documents"
                            }

                            ProfileMenuItem(
                                icon = Icons.Default.AccountBox,
                                title = "Documents",
                                subtitle = documentSubtitle,
                                onClick = { onNavigateToDriverDocuments(uiState.user!!.uid) }
                            )
                        }

                        // Help & Support
                        ProfileMenuItem(
                            icon = Icons.Default.Info,
                            title = "Help & Support",
                            subtitle = "FAQ, Contact us, Report issues",
                            onClick = { onNavigateToHelpSupport() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Logout button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogoutDialog = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Logout",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // THIS IS WHERE THE EXTRA '}' WAS REMOVED
            }
        }
    }

    // Image picker dialog
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Change Profile Picture") },
            text = {
                Column {
                    Text("Choose how you'd like to update your profile picture:")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImagePicker = false
                                imagePickerLauncher.launch("image/*")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBox,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Choose from gallery",
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Name editing dialog removed - users cannot edit their name

    // Email view dialog (Read-only)
    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Email Address") },
            text = {
                Column {
                    Text(
                        text = FirebaseAuth.getInstance().currentUser?.email ?: uiState.user?.email ?: "Not provided",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Email address cannot be changed for authentication security.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Phone editing dialog
    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text("Edit Phone Number") },
            text = {
                OutlinedTextField(
                    value = editingPhone,
                    onValueChange = { editingPhone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingPhone.isNotBlank()) {
                            viewModel.updateProfile(context, editingName, "", editingPhone, null)
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Password change dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column {
                    // Current Password Field
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                Icon(
                                    imageVector = if (showCurrentPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showCurrentPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // New Password Field
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Password Field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Password Requirements
                    Text(
                        text = "Password must be at least 6 characters long",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            currentPassword.isEmpty() -> {
                                // Show error for current password
                            }
                            newPassword.isEmpty() -> {
                                // Show error for new password
                            }
                            newPassword != confirmPassword -> {
                                // Show error for password mismatch
                            }
                            newPassword.length < 6 -> {
                                // Show error for password length
                            }
                            else -> {
                                viewModel.updatePassword(currentPassword, newPassword)
                                showPasswordDialog = false
                            }
                        }
                    }
                ) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut()
                        onSignOut()
                    }
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}