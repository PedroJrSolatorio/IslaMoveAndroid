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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
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
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var selectedUserType by rememberSaveable { mutableStateOf<String?>(null) }
    var dateOfBirth by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var selectedGender by rememberSaveable { mutableStateOf<String?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var idDocumentUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var driverLicenseUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var sjmodaUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var orUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var crUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showDocumentPicker by rememberSaveable { mutableStateOf(false) }
    var showLicensePicker by rememberSaveable { mutableStateOf(false) }
    var showSjmodaPicker by rememberSaveable { mutableStateOf(false) }
    var showOrPicker by rememberSaveable { mutableStateOf(false) }
    var showCrPicker by rememberSaveable { mutableStateOf(false) }
    var cameraLicenseUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraSjmodaUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraOrUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraCrUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var ageError by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var phoneNumberError by rememberSaveable { mutableStateOf<String?>(null) }

    // New state for T&C and Privacy Policy
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }

    // Validation logic
    val allFieldsFilled = remember(
        firstName, lastName, email, phoneNumber, password,
        selectedUserType, dateOfBirth, selectedGender, address,
        idDocumentUri, driverLicenseUri, sjmodaUri, orUri, crUri,
        ageError, termsAccepted, privacyAccepted, phoneNumberError
    ) {
        firstName.isNotEmpty() &&
                lastName.isNotEmpty() &&
                email.isNotEmpty() &&
                phoneNumber.isNotEmpty() &&
                password.isNotEmpty() &&
                password.length >= 6 &&
                selectedUserType != null &&
                dateOfBirth.isNotEmpty() &&
                selectedGender != null &&
                address.isNotEmpty() &&
                (selectedUserType != "PASSENGER" || idDocumentUri != null) &&
                (selectedUserType != "DRIVER" || (driverLicenseUri != null && sjmodaUri != null && orUri != null && crUri != null)) &&
                ageError == null &&
                phoneNumberError == null &&
                termsAccepted &&
                privacyAccepted
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && selectedUserType != null) {
            onAccountCreated(selectedUserType!!)
        }
    }

    fun createImageUri(): Uri {
        val imageFile = File(context.cacheDir, "id_document_${System.currentTimeMillis()}.jpg")
        imageFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    var cameraImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            idDocumentUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            idDocumentUri = cameraImageUri
        }
    }

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

        Text(
            text = "Create your account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 32.dp)
        )

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
                onValueChange = { input ->
                    // Allow only digits and optional leading "+"
                    val sanitized = input.filterIndexed { index, c ->
                        c.isDigit() || (index == 0 && c == '+')
                    }

                    phoneNumber = sanitized
                    viewModel.clearError()

                    // Validate Philippine phone number
                    phoneNumberError = when {
                        phoneNumber.isEmpty() -> null
                        phoneNumber.matches(Regex("^09\\d{9}$")) -> null
                        phoneNumber.matches(Regex("^\\+639\\d{9}$")) -> null
                        phoneNumber.length < 11 -> "Phone number must be at least 11 digits"
                        else -> "Invalid phone number. Use 09XXXXXXXXX or +639XXXXXXXXX"
                    }
                },
                placeholder = {
                    Text(
                        "Phone number",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = phoneNumberError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (phoneNumberError == null) IslamovePrimary else MaterialTheme.colorScheme.error,
                    unfocusedBorderColor = if (phoneNumberError == null)
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            )

            // Show error text below
            if (phoneNumberError != null) {
                Text(
                    text = phoneNumberError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                placeholder = {
                    Text(
                        "Password (min. 6 characters)",
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

            // User Type Selection (moved up before common fields)
            Text(
                text = "Choose your role",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

            // Common fields - Show if any user type is selected
            if (selectedUserType != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Date of Birth field
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { },
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
                            "Complete Address",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IslamovePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // Valid ID Upload
            if (selectedUserType == "PASSENGER") {
                Text(
                    text = "Valid ID (Government-issued)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                Text(
                    text = "For students or senior citizens, upload your Student ID or Senior Citizen ID to avail discounts (requires admin verification within 24-48 hours)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                    lineHeight = 16.sp
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

            if (selectedUserType == "DRIVER") {
                // Driver's License Upload
                Text(
                    text = "Driver's License",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                val licenseLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        driverLicenseUri = uri
                    }
                }

                val cameraLicenseLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && cameraLicenseUri != null) {
                        driverLicenseUri = cameraLicenseUri
                    }
                }

                val cameraLicensePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        val uri = createImageUri()
                        cameraLicenseUri = uri
                        cameraLicenseLauncher.launch(uri)
                    }
                }

                Button(
                    onClick = { showLicensePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (driverLicenseUri != null) Color(0xFF4CAF50) else IslamovePrimary
                    )
                ) {
                    Icon(
                        if (driverLicenseUri != null) Icons.Default.CheckCircle else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (driverLicenseUri != null) "Driver's License Uploaded" else "Upload Driver's License"
                    )
                }

                if (driverLicenseUri != null) {
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
                            model = driverLicenseUri,
                            contentDescription = "Driver's License Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (showLicensePicker) {
                    AlertDialog(
                        onDismissRequest = { showLicensePicker = false },
                        title = {
                            Text(
                                text = "Upload Driver's License",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text("Choose how you'd like to upload your Driver's License:")
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showLicensePicker = false
                                            cameraLicensePermissionLauncher.launch(android.Manifest.permission.CAMERA)
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showLicensePicker = false
                                            licenseLauncher.launch("image/*")
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
                            TextButton(onClick = { showLicensePicker = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // SJMODA Certification Upload
                Text(
                    text = "Franchise Certificate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                val sjmodaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        sjmodaUri = uri
                    }
                }

                val cameraSjmodaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && cameraSjmodaUri != null) {
                        sjmodaUri = cameraSjmodaUri
                    }
                }

                val cameraSjmodaPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        val uri = createImageUri()
                        cameraSjmodaUri = uri
                        cameraSjmodaLauncher.launch(uri)
                    }
                }

                Button(
                    onClick = { showSjmodaPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sjmodaUri != null) Color(0xFF4CAF50) else IslamovePrimary
                    )
                ) {
                    Icon(
                        if (sjmodaUri != null) Icons.Default.CheckCircle else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (sjmodaUri != null) "Franchise Certificate Uploaded" else "Upload Franchise Certificate"
                    )
                }

                if (sjmodaUri != null) {
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
                            model = sjmodaUri,
                            contentDescription = "Franchise Certificate Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (showSjmodaPicker) {
                    AlertDialog(
                        onDismissRequest = { showSjmodaPicker = false },
                        title = {
                            Text(
                                text = "Upload Franchise Certificate",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text("Choose how you'd like to upload your Franchise Certificate:")
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSjmodaPicker = false
                                            cameraSjmodaPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSjmodaPicker = false
                                            sjmodaLauncher.launch("image/*")
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
                            TextButton(onClick = { showSjmodaPicker = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Official Receipt (OR) Upload
                Text(
                    text = "Official Receipt (OR)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                val orLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        orUri = uri
                    }
                }

                val cameraOrLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && cameraOrUri != null) {
                        orUri = cameraOrUri
                    }
                }

                val cameraOrPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        val uri = createImageUri()
                        cameraOrUri = uri
                        cameraOrLauncher.launch(uri)
                    }
                }

                Button(
                    onClick = { showOrPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orUri != null) Color(0xFF4CAF50) else IslamovePrimary
                    )
                ) {
                    Icon(
                        if (orUri != null) Icons.Default.CheckCircle else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (orUri != null) "Official Receipt Uploaded" else "Upload Official Receipt"
                    )
                }

                if (orUri != null) {
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
                            model = orUri,
                            contentDescription = "Official Receipt Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (showOrPicker) {
                    AlertDialog(
                        onDismissRequest = { showOrPicker = false },
                        title = {
                            Text(
                                text = "Upload Official Receipt",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text("Choose how you'd like to upload your Official Receipt:")
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showOrPicker = false
                                            cameraOrPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showOrPicker = false
                                            orLauncher.launch("image/*")
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
                            TextButton(onClick = { showOrPicker = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Certificate of Registration (CR) Upload
                Text(
                    text = "Certificate of Registration (CR)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                val crLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        crUri = uri
                    }
                }

                val cameraCrLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && cameraCrUri != null) {
                        crUri = cameraCrUri
                    }
                }

                val cameraCrPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        val uri = createImageUri()
                        cameraCrUri = uri
                        cameraCrLauncher.launch(uri)
                    }
                }

                Button(
                    onClick = { showCrPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (crUri != null) Color(0xFF4CAF50) else IslamovePrimary
                    )
                ) {
                    Icon(
                        if (crUri != null) Icons.Default.CheckCircle else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (crUri != null) "Certificate of Registration Uploaded" else "Upload Certificate of Registration"
                    )
                }

                if (crUri != null) {
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
                            model = crUri,
                            contentDescription = "Certificate of Registration Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (showCrPicker) {
                    AlertDialog(
                        onDismissRequest = { showCrPicker = false },
                        title = {
                            Text(
                                text = "Upload Certificate of Registration",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text("Choose how you'd like to upload your Certificate of Registration:")
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCrPicker = false
                                            cameraCrPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCrPicker = false
                                            crLauncher.launch("image/*")
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
                            TextButton(onClick = { showCrPicker = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Terms and Conditions Checkbox
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (termsAccepted)
                        IslamovePrimary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (termsAccepted) IslamovePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { termsAccepted = !termsAccepted }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = IslamovePrimary
                        )
                    )

                    val annotatedText = buildAnnotatedString {
                        append("I agree to the ")
                        pushStringAnnotation(tag = "TERMS", annotation = "terms")
                        withStyle(
                            style = SpanStyle(
                                color = IslamovePrimary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append("Terms and Conditions")
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations(
                                tag = "TERMS",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let {
                                onNavigateToTerms()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy Policy Checkbox
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (privacyAccepted)
                        IslamovePrimary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (privacyAccepted) IslamovePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { privacyAccepted = !privacyAccepted }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = privacyAccepted,
                        onCheckedChange = { privacyAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = IslamovePrimary
                        )
                    )

                    val annotatedText = buildAnnotatedString {
                        append("I agree to the ")
                        pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                        withStyle(
                            style = SpanStyle(
                                color = IslamovePrimary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append("Privacy Policy")
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations(
                                tag = "PRIVACY",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let {
                                onNavigateToPrivacy()
                            }
                        }
                    )
                }
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

        Spacer(modifier = Modifier.height(24.dp))

        // Debug info - Remove this in production
        if (!allFieldsFilled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Please complete:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (firstName.isEmpty()) Text("• First name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastName.isEmpty()) Text("• Last name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (email.isEmpty()) Text("• Email", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (phoneNumber.isEmpty()) Text("• Phone number", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (password.isEmpty() || password.length < 6) Text("• Password (min 6 chars)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == null) Text("• Select your role", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (dateOfBirth.isEmpty()) Text("• Date of birth", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedGender == null) Text("• Gender", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (address.isEmpty()) Text("• Address", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == "PASSENGER" && idDocumentUri == null) Text("• Valid ID upload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == "DRIVER" && driverLicenseUri == null) Text("• Driver's License upload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == "DRIVER" && sjmodaUri == null) Text("• Franchise Certificate upload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == "DRIVER" && orUri == null) Text("• Official Receipt upload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedUserType == "DRIVER" && crUri == null) Text("• Certificate of Registration upload", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ageError != null) Text("• Must be 12+ years old", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!termsAccepted) Text("• Accept Terms and Conditions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!privacyAccepted) Text("• Accept Privacy Policy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Continue button
        Button(
            onClick = {
                val displayName = "$firstName $lastName"
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
                    idDocumentUri = if (selectedUserType == "PASSENGER") idDocumentUri else null,
                    driverLicenseUri = if (selectedUserType == "DRIVER") driverLicenseUri else null,
                    sjmodaUri = if (selectedUserType == "DRIVER") sjmodaUri else null,
                    orUri = if (selectedUserType == "DRIVER") orUri else null,
                    crUri = if (selectedUserType == "DRIVER") crUri else null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = allFieldsFilled && !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
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
                    text = "Create Account",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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

                            val today = LocalDate.now()
                            val age = Period.between(selectedDate, today).years

                            if (age < 12) {
                                ageError = "You must be at least 12 years old to register"
                                dateOfBirth = ""
                            } else {
                                ageError = null
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