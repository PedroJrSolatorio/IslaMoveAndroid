package com.rj.islamove.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.UserType
import com.rj.islamove.ui.theme.IslamovePrimary
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Period

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    onNavigateBack: () -> Unit,
    onAccountCreated: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedUserType by remember { mutableStateOf<String?>(null) }
    var dateOfBirth by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf("") }
    var idDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var showDocumentPicker by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    val allFieldsFilled = firstName.isNotEmpty() && lastName.isNotEmpty() &&
                         email.isNotEmpty() && phoneNumber.isNotEmpty() &&
                         password.isNotEmpty() && password.length >= 6 &&
                         selectedUserType != null &&
                         // For all users, require date of birth, gender, and address
                         dateOfBirth.isNotEmpty() && selectedGender != null && address.isNotEmpty() &&
                         // For passengers only, also require ID document
                         (selectedUserType != "PASSENGER" || idDocumentUri != null) &&
                         // Must be at least 12 years old
                         ageError == null
    
    // Handle navigation based on state
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && selectedUserType != null) {
            onAccountCreated(selectedUserType!!)
        }
    }

    // Helper function to create temporary file URI for camera
    fun createImageUri(): Uri {
        val imageFile = File(context.cacheDir, "id_document_${System.currentTimeMillis()}.jpg")
        imageFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    // Store camera image URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            idDocumentUri = uri
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            idDocumentUri = cameraImageUri
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri()
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
    ) {
        // Top App Bar with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Title
        Text(
            text = "Create your account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Form fields
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First name field
            OutlinedTextField(
                value = firstName,
                onValueChange = { 
                    firstName = it
                    viewModel.clearError()
                },
                placeholder = { 
                    Text(
                        "First name", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            // Last name field
            OutlinedTextField(
                value = lastName,
                onValueChange = { 
                    lastName = it
                    viewModel.clearError()
                },
                placeholder = { 
                    Text(
                        "Last name", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    viewModel.clearError()
                },
                placeholder = { 
                    Text(
                        "Email", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            // Phone number field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { 
                    phoneNumber = it
                    viewModel.clearError()
                },
                placeholder = { 
                    Text(
                        "Phone number", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                placeholder = {
                    Text(
                        "Password",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Common fields for all users - Show if any user type is selected
            if (selectedUserType != null) {
                // Date of Birth field - Clickable to open date picker
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { }, // Read-only field
                    readOnly = true,
                    placeholder = {
                        Text(
                            "Date of Birth",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Select date",
                            tint = IslamovePrimary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IslamovePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    enabled = false
                )

                // Age error message
                if (ageError != null) {
                    Text(
                        text = ageError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                // Gender selection
                Text(
                    text = "Gender",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Male
                    FilterChip(
                        selected = selectedGender == "Male",
                        onClick = {
                            selectedGender = "Male"
                            viewModel.clearError()
                        },
                        label = { Text("Male") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IslamovePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = IslamovePrimary
                        )
                    )

                    // Female
                    FilterChip(
                        selected = selectedGender == "Female",
                        onClick = {
                            selectedGender = "Female"
                            viewModel.clearError()
                        },
                        label = { Text("Female") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IslamovePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = IslamovePrimary
                        )
                    )

                    // Other
                    FilterChip(
                        selected = selectedGender == "Other",
                        onClick = {
                            selectedGender = "Other"
                            viewModel.clearError()
                        },
                        label = { Text("Other") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IslamovePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = IslamovePrimary
                        )
                    )
                }

                // Address field
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        viewModel.clearError()
                    },
                    placeholder = {
                        Text(
                            "Address",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IslamovePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // Valid ID Upload - Only for passengers
            if (selectedUserType == "PASSENGER") {
                // Valid ID Upload
                Text(
                    text = "Valid ID",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                Button(
                    onClick = { showDocumentPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (idDocumentUri != null) Color(0xFF4CAF50) else IslamovePrimary
                    )
                ) {
                    Icon(
                        if (idDocumentUri != null) Icons.Default.CheckCircle else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (idDocumentUri != null) "Valid ID Uploaded" else "Upload Valid ID"
                    )
                }

                // Show preview if document is selected
                if (idDocumentUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        AsyncImage(
                            model = idDocumentUri,
                            contentDescription = "Valid ID Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Type Selection
            Text(
                text = "Choose your role",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Passenger card
                UserTypeCard(
                    modifier = Modifier.weight(1f),
                    title = "Passenger",
                    icon = Icons.Default.Person,
                    isSelected = selectedUserType == "PASSENGER",
                    onClick = {
                        selectedUserType = "PASSENGER"
                        viewModel.clearError()
                    }
                )

                // Driver card
                UserTypeCard(
                    modifier = Modifier.weight(1f),
                    title = "Driver",
                    icon = Icons.Default.Person,
                    isSelected = selectedUserType == "DRIVER",
                    onClick = {
                        selectedUserType = "DRIVER"
                        viewModel.clearError()
                    }
                )
            }
        }
        
        // Error message
        uiState.errorMessage?.let { errorMessage ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Continue button (red like in the image)
        Button(
            onClick = {
                val displayName = "$firstName $lastName"
                // Set user type based on selection before creating account
                val userType = when (selectedUserType) {
                    "DRIVER" -> UserType.DRIVER
                    "PASSENGER" -> UserType.PASSENGER
                    else -> UserType.PASSENGER
                }
                viewModel.createAccountWithRole(
                    email = email,
                    password = password,
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    userType = userType,
                    dateOfBirth = dateOfBirth,
                    gender = selectedGender,
                    address = address,
                    idDocumentUri = if (selectedUserType == "PASSENGER") idDocumentUri else null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = allFieldsFilled && !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error, // Red color like in the image
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Creating Account...")
            } else {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Terms and conditions text (like in the image)
        Text(
            text = "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 16.sp
        )
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()

                            // Calculate age
                            val today = LocalDate.now()
                            val age = Period.between(selectedDate, today).years

                            // Check if user is at least 12 years old
                            if (age < 12) {
                                ageError = "You must be at least 12 years old to register"
                                dateOfBirth = ""
                            } else {
                                ageError = null
                                // Format date as YYYY-MM-DD
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                dateOfBirth = selectedDate.format(formatter)
                            }
                        }
                        showDatePicker = false
                        viewModel.clearError()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Select Date of Birth",
                        modifier = Modifier.padding(16.dp)
                    )
                },
                headline = {
                    Text(
                        text = datePickerState.selectedDateMillis?.let {
                            val date = Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                            date.format(formatter)
                        } ?: "Select a date",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }
    }

    // Document picker dialog
    if (showDocumentPicker) {
        AlertDialog(
            onDismissRequest = { showDocumentPicker = false },
            title = {
                Text(
                    text = "Upload Valid ID",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Choose how you'd like to upload your Valid ID:")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDocumentPicker = false
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
                            fontSize = 16.sp
                        )
                    }

                    // Gallery option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDocumentPicker = false
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
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDocumentPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserTypeCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) IslamovePrimary.copy(alpha = 0.1f)
                           else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(2.dp, IslamovePrimary)
                 else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) IslamovePrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) IslamovePrimary
                       else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}