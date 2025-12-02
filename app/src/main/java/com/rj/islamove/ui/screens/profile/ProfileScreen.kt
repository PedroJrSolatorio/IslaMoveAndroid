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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import com.rj.islamove.R
import com.google.firebase.auth.EmailAuthProvider
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontStyle
import com.google.firebase.firestore.FirebaseFirestore

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
    // Add diagnostic logging on every recomposition
    LaunchedEffect(Unit) {
        android.util.Log.d("ProfileScreen", "🔄 LaunchedEffect(Unit) - Initial composition")
        viewModel.logCurrentState()
    }

    DisposableEffect(viewModel) {
        android.util.Log.d("ProfileScreen", "🔵 ViewModel attached: ${viewModel.hashCode()}")
        onDispose {
            android.util.Log.d("ProfileScreen", "🔴 ViewModel detached: ${viewModel.hashCode()}")
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    // Log state changes
    LaunchedEffect(uiState.user?.passengerRating, uiState.user?.passengerTotalTrips) {
        android.util.Log.d("ProfileScreen", "🔄 ========================================")
        android.util.Log.d("ProfileScreen", "🔄 UI State CHANGED")
        android.util.Log.d("ProfileScreen", "🔄 Current rating: ${uiState.user?.passengerRating}")
        android.util.Log.d("ProfileScreen", "🔄 Current trips: ${uiState.user?.passengerTotalTrips}")
        android.util.Log.d("ProfileScreen", "🔄 User: ${uiState.user?.displayName}")
        android.util.Log.d("ProfileScreen", "🔄 ========================================")
    }

    android.util.Log.d("ProfileScreen", "Current UI State - user: ${uiState.user?.displayName}, rating: ${uiState.user?.passengerRating}")
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
    var isUpdating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    // Camera and image states
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Upload image to Cloudinary using ViewModel
    fun uploadImageToCloudinary(uri: Uri) {
        isUploading = true
        android.util.Log.d("ProfileImage", "Starting Cloudinary upload, isUploading set to true")
        viewModel.uploadProfileImage(uri)
        // Don't set isUploading = false here!
    }

    fun saveImageLocally(uri: Uri) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.e("ProfileImage", "User not authenticated")
            isUploading = false
            return
        }
        uploadImageToCloudinary(uri)
    }

    // ADD: Observe upload completion from ViewModel state
    LaunchedEffect(uiState.user?.profileImageUrl, uiState.user?.updatedAt) {
        if (isUploading) {
            val newImageUrl = uiState.user?.profileImageUrl
            if (!newImageUrl.isNullOrEmpty() && newImageUrl != profileImageUri.toString()) {
                android.util.Log.d("ProfileImage", "Upload completed successfully, isUploading set to false")
                isUploading = false
                showImagePicker = false
            }
        }
    }

    // ADD: Timeout mechanism
    LaunchedEffect(isUploading) {
        if (isUploading) {
            delay(30000) // 30 second timeout
            if (isUploading) {
                android.util.Log.w("ProfileImage", "Upload timeout - resetting isUploading state")
                isUploading = false
            }
        }
    }

    // Create temporary file URI for camera
    fun createImageUri(): Uri {
        val imageFile = File(context.cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
        imageFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null && !isUploading) {
            profileImageUri = cameraImageUri
            android.util.Log.d("ProfileImage", "Camera photo taken successfully: $cameraImageUri")
            saveImageLocally(cameraImageUri!!)
        } else {
            android.util.Log.e("ProfileImage", "Camera photo failed or URI is null")
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !isUploading) {
            profileImageUri = uri
            android.util.Log.d("ProfileImage", "Gallery image selected: $uri")
            saveImageLocally(uri)
        } else if (isUploading) {
            android.util.Log.w("ProfileImage", "Upload already in progress, ignoring selection")
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !isUploading) {
            val uri = createImageUri()
            cameraImageUri = uri
            android.util.Log.d("ProfileImage", "Camera URI created: $uri")
            cameraLauncher.launch(uri)
        } else if (isUploading) {
            android.util.Log.w("ProfileImage", "Upload in progress, camera not launched")
        } else {
            android.util.Log.e("ProfileImage", "Camera permission denied")
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

    // Listen for when user returns after clicking verification link
    DisposableEffect(Unit) {
        var isActive = true

        val checkEmailVerification: () -> Unit = {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && isActive) {
                currentUser.reload().addOnCompleteListener { task ->
                    if (task.isSuccessful && isActive) {
                        val verifiedEmail = currentUser.email
                        val isVerified = currentUser.isEmailVerified

                        if (verifiedEmail != null) {
                            // Check if this email is different from what's in Firestore
                            val firestore = FirebaseFirestore.getInstance()
                            firestore.collection("users").document(currentUser.uid)
                                .get()
                                .addOnSuccessListener { document ->
                                    if (isActive) {
                                        val firestoreEmail = document.getString("email")
                                        val pendingEmail = document.getString("pendingEmail")

                                        // If Firebase Auth email differs from Firestore, update it
                                        if (verifiedEmail != firestoreEmail && verifiedEmail == pendingEmail) {
                                            android.util.Log.d("ProfileScreen", "Email verified! Updating Firestore: $verifiedEmail")

                                            firestore.collection("users").document(currentUser.uid)
                                                .update(mapOf(
                                                    "email" to verifiedEmail,
                                                    "emailVerified" to isVerified,
                                                    "pendingEmail" to null,
                                                    "pendingEmailTimestamp" to null,
                                                    "updatedAt" to System.currentTimeMillis()
                                                ))
                                                .addOnSuccessListener {
                                                    android.util.Log.d("ProfileScreen", "Firestore email updated successfully")
                                                    viewModel.loadUserProfile()
                                                }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        // Check immediately when screen loads
        checkEmailVerification()

        // Set up periodic checks (only while screen is active)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            var checkCount = 0
            override fun run() {
                if (isActive && checkCount < 60) { // Stop after 5 minutes
                    checkCount++
                    checkEmailVerification()
                    handler.postDelayed(this, 5000) // Check every 5 seconds
                }
            }
        }
        handler.postDelayed(runnable, 5000)

        onDispose {
            isActive = false
            handler.removeCallbacks(runnable)
            android.util.Log.d("ProfileScreen", "Email verification listener disposed")
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
            // Auto-close dialogs on successful update
            LaunchedEffect(uiState.updateSuccess, uiState.updateMessage) {
                if (uiState.updateSuccess && uiState.updateMessage?.contains("successfully") == true) {
                    delay(1500) // Show success message for 1.5 seconds
                    showPhoneDialog = false
                    showPasswordDialog = false
                    viewModel.clearUpdateSuccess()
                    viewModel.clearUpdateMessage()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentPadding = PaddingValues(top = 40.dp, bottom = 80.dp)
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
                            modifier = Modifier.clickable {
                                // Prevent opening image picker while an upload is in progress
                                if (!isUploading) {
                                    showImagePicker = true
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = if (profileImageUri != null) Color.Transparent else Color(0xFFE8B68C),
                                        shape = RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // The user's image or a placeholder
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(profileImageUri ?: uiState.user!!.profileImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .size(80.dp) // Apply size directly
                                        .clip(CircleShape), // Apply circular clip directly
                                    contentScale = ContentScale.Crop,
                                )

                                // --- UPLOADING INDICATOR OVERLAY ---
                                // This block adds a loading overlay when isUploading is true.
                                if (isUploading) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp) // Match the image size
                                            .clip(CircleShape) // Match the image shape
                                            .background(Color.Black.copy(alpha = 0.5f)), // Semi-transparent overlay
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = Color.White,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            }

                            // Edit Badge (conditionally shown)
                            // Hide the edit icon while uploading to prevent user confusion
                            if (!isUploading) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile Picture",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White, CircleShape)
                                        .padding(4.dp)
                                        .align(Alignment.BottomEnd),
                                    tint = IslamovePrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // User name (read-only)
                        Text(
                            text = uiState.user?.displayName ?: "No name set",
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
                                    Color(0xFF4CAF50),
                                    Icons.Default.CheckCircle
                                )
                                VerificationStatus.PENDING -> Triple(
                                    "⏳ Verification Pending",
                                    Color(0xFFFFA726),
                                    null
                                )
                                VerificationStatus.UNDER_REVIEW -> Triple(
                                    "👀 Under Review",
                                    Color(0xFF42A5F5),
                                    null
                                )
                                VerificationStatus.REJECTED -> Triple(
                                    "❌ Verification Rejected",
                                    Color(0xFFEF5350),
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

                        // Account Status (for passengers)
                        if (uiState.user!!.userType == UserType.PASSENGER) {
                            Spacer(modifier = Modifier.height(6.dp))

                            val (statusText, statusColor) = if (uiState.user!!.isActive) {
                                Pair("Active Account", Color(0xFF4CAF50))
                            } else {
                                Pair("Account Blocked", Color(0xFFEF5350))
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

                        // Rating and trip count
                        when (uiState.user!!.userType) {
                            UserType.DRIVER -> {
                                val driverData = uiState.user!!.driverData
                                if (driverData != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFFFA500)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (driverData.rating > 0.0) String.format("%.1f", driverData.rating) else "No ratings yet",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (driverData.totalTrips > 0) {
                                            Text(
                                                text = " • ${driverData.totalTrips} trip${if (driverData.totalTrips == 1) "" else "s"}",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            UserType.PASSENGER -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFFFA500)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.user!!.passengerRating > 0.0)
                                            String.format("%.1f", uiState.user!!.passengerRating) else "No ratings yet",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (uiState.user!!.passengerTotalTrips > 0) {
                                        Text(
                                            text = " • ${uiState.user!!.passengerTotalTrips} trip${if (uiState.user!!.passengerTotalTrips == 1) "" else "s"}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            UserType.ADMIN -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Personal Information section (Driver Profile Page)
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

                        // Email
                        ProfileMenuItem(
                            icon = Icons.Default.Email,
                            title = "Email",
                            subtitle = FirebaseAuth.getInstance().currentUser?.email ?: uiState.user?.email ?: "Not provided",
                            onClick = { showEmailDialog = true }
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
                            onClick = {
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                showPasswordDialog = true
                            }
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
            }
        }
    }

    // Image picker dialog
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = {
                if (!isUploading) {
                    showImagePicker = false
                }
            },
            title = {
                Text(
                    text = "Change Profile Picture",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Choose how you'd like to update your profile picture:")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUploading) {
                                showImagePicker = false
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Take a photo",
                            fontSize = 16.sp,
                            color = if (isUploading) Color.Gray else Color.Unspecified
                        )
                    }

                    // Gallery option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUploading) {
                                showImagePicker = false
                                galleryLauncher.launch("image/*")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = IslamovePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Choose from gallery",
                            fontSize = 16.sp,
                            color = if (isUploading) Color.Gray else Color.Unspecified
                        )
                    }

                    // Show uploading indicator
                    if (isUploading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Uploading...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!isUploading) {
                        showImagePicker = false
                    }
                },
                    enabled = !isUploading
                ) {
                    Text(if (isUploading) "Uploading..." else "Cancel")
                }
            },
            dismissButton = null
        )
    }

    // Name editing dialog removed - users cannot edit their name

    // Email editing dialog with re-authentication
    if (showEmailDialog) {
        var editingEmail by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email ?: "") }
        var currentPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showEmailDialog = false
                currentPassword = ""
                emailError = null
                updateMessage = null
            },
            title = {
                Text(
                    text = "Change Email Address",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "For security, please enter your current password to change your email address.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editingEmail,
                        onValueChange = {
                            editingEmail = it.trim()
                            emailError = null
                            updateMessage = null
                        },
                        label = { Text("New Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = emailError != null
                    )

                    if (emailError != null) {
                        Text(
                            text = emailError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            updateMessage = null
                        },
                        label = { Text("Current Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    updateMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = if (message.startsWith("Error") || message.contains("Incorrect") || message.contains("failed"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }

                    if (updateMessage?.contains("Verification email") == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check your inbox or spam emails and click the verification link.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Validate email format
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(editingEmail).matches()) {
                            emailError = "Please enter a valid email address"
                            return@Button
                        }

                        val currentEmail = FirebaseAuth.getInstance().currentUser?.email
                        if (editingEmail == currentEmail) {
                            emailError = "New email is the same as current email"
                            return@Button
                        }

                        if (currentPassword.isBlank()) {
                            updateMessage = "Please enter your current password"
                            return@Button
                        }

                        val user = FirebaseAuth.getInstance().currentUser
                        if (user != null && currentEmail != null) {
                            // Step 1: Re-authenticate user
                            val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)

                            user.reauthenticate(credential)
                                .addOnSuccessListener {
                                    // Step 2: Send verification email and update
                                    user.verifyBeforeUpdateEmail(editingEmail)
                                        .addOnSuccessListener {
                                            // Step 3: Update email in Firestore
                                            val firestore = FirebaseFirestore.getInstance()
                                            val userDoc = firestore.collection("users").document(user.uid)

                                            val updates = mapOf(
                                                "pendingEmail" to editingEmail,
                                                "pendingEmailTimestamp" to System.currentTimeMillis()
                                            )

                                            userDoc.update(updates)
                                                .addOnSuccessListener {
                                                    updateMessage = "Verification email sent to $editingEmail."
                                                    currentPassword = ""
                                                    android.util.Log.d("ProfileUpdate", "Email update initiated: $editingEmail")
                                                }
                                                .addOnFailureListener { e ->
                                                    updateMessage = "Error updating profile: ${e.message}"
                                                    android.util.Log.e("ProfileUpdate", "Firestore update failed", e)
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            updateMessage = when {
                                                e.message?.contains("email-already-in-use") == true ->
                                                    "This email is already in use by another account"
                                                e.message?.contains("invalid-email") == true ->
                                                    "Invalid email format"
                                                e.message?.contains("requires-recent-login") == true ->
                                                    "Please log out and log back in, then try again"
                                                else -> "Error updating email: ${e.message}"
                                            }
                                            android.util.Log.e("ProfileUpdate", "Email update failed", e)
                                        }
                                }
                                .addOnFailureListener { e ->
                                    updateMessage = when {
                                        e.message?.contains("wrong-password") == true ->
                                            "Incorrect password. Please try again."
                                        e.message?.contains("too-many-requests") == true ->
                                            "Too many failed attempts. Please try again later."
                                        else -> "Authentication failed: ${e.message}"
                                    }
                                    android.util.Log.e("ProfileUpdate", "Re-authentication failed", e)
                                }
                        } else {
                            updateMessage = "User not authenticated"
                        }
                    },
                    enabled = !uiState.isUpdating
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Send Verification")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmailDialog = false
                        currentPassword = ""
                        emailError = null
                        updateMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Phone editing dialog
    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = {
                Text(
                    text = "Edit Phone Number",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingPhone,
                        onValueChange = { input ->
                            // Allow only digits and optional leading "+"
                            val sanitized = input.filterIndexed { index, c ->
                                c.isDigit() || (index == 0 && c == '+')
                            }
                            editingPhone = sanitized

                            // Validate Philippine phone numbers
                            updateMessage = when {
                                editingPhone.isEmpty() -> null
                                editingPhone.matches(Regex("^09\\d{9}$")) -> null
                                editingPhone.matches(Regex("^\\+639\\d{9}$")) -> null
                                editingPhone.length < 11 -> "Phone number must be at least 11 digits"
                                editingPhone.length < 12 -> null // don't show error while typing
                                else -> "Invalid phone number. Use 09XXXXXXXXX or +639XXXXXXXXX"
                            }
                        },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    updateMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = if (message.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val phoneToSave = editingPhone.trim()

                        // Validation: Check if phone number meets minimum length requirement
                        if (phoneToSave.length < 11) {
                            updateMessage = "Phone number must be at least 11 digits"
                            return@Button
                        }

                        // Additional validation for Philippine phone formats
                        val isValidFormat = phoneToSave.matches(Regex("^09\\d{9}$")) ||
                                phoneToSave.matches(Regex("^\\+639\\d{9}$"))

                        if (!isValidFormat) {
                            updateMessage = "Invalid phone number. Use 09XXXXXXXXX or +639XXXXXXXXX"
                            return@Button
                        }

                        if (editingPhone.isNotBlank()) {
                            viewModel.updatePhoneNumber(phoneToSave)
                        } else {
                            updateMessage = "Please enter a phone number"
                        }
                    },
                    enabled = !uiState.isUpdating
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Update")
                    }
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
            onDismissRequest = {
                showPasswordDialog = false
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
                showCurrentPassword = false
                showNewPassword = false
                showConfirmPassword = false
                updateMessage = null
            },
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

                    // Show validation and update messages
                    updateMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = if (message.startsWith("Error") || message.contains("incorrect") || message.contains("do not match"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            currentPassword.isEmpty() -> {
                                updateMessage = "Please enter your current password"
                            }
                            newPassword.isEmpty() -> {
                                updateMessage = "Please enter a new password"
                            }
                            confirmPassword.isEmpty() -> {
                                updateMessage = "Please confirm your new password"
                            }
                            newPassword != confirmPassword -> {
                                updateMessage = "New passwords do not match"
                            }
                            newPassword.length < 6 -> {
                                updateMessage = "Password must be at least 6 characters long"
                            }
                            else -> {
                                viewModel.updatePassword(currentPassword, newPassword) { result ->
                                    updateMessage = result
                                    if (result == "Password updated successfully") {
                                        // Clear fields after short delay to show success message
                                        coroutineScope.launch {
                                            delay(1500)
                                            showPasswordDialog = false
                                            currentPassword = ""
                                            newPassword = ""
                                            confirmPassword = ""
                                            updateMessage = null
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !uiState.isUpdating
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        showCurrentPassword = false
                        showNewPassword = false
                        showConfirmPassword = false
                        updateMessage = null
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