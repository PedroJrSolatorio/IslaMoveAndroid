package com.rj.islamove.ui.screens.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HelpSupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Help & Support",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // FAQ Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Frequently Asked Questions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // FAQ Items
                    FAQItem(
                        question = "How do I book a ride?",
                        answer = "Simply select your destination location and confirm your booking."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FAQItem(
                        question = "How are fares calculated?",
                        answer = "Fares are calculated based on official Municipality of San Jose Fare Matrix. You'll see the estimated fare before confirming your ride."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FAQItem(
                        question = "How do I cancel a ride?",
                        answer = "You can cancel a ride from the active trips screen, but please be aware that you are limited to 3 cancellations per day."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Report Issue Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Report an Issue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Describe your issue or feedback below:",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = {
                            Text(
                                text = "Please describe your issue in detail...",
                                fontSize = 14.sp,
                                color = Color(0xFF999999)
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Submit button
                    Button(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.submitSupportTicket(commentText)
                                commentText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = commentText.isNotBlank() && !uiState.isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Submit",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Report")
                        }
                    }

                    // Success/Error messages
                    uiState.successMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    uiState.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            fontSize = 14.sp,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Delete Account Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE) // Light red background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Delete Account",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentUser?.isDeletionScheduled == true) {
                        // Show cancellation info if deletion is scheduled
                        val daysRemaining = currentUser?.deletionExecutionDate?.let {
                            ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                        } ?: 0

                        Text(
                            text = "Your account is scheduled for deletion in $daysRemaining days.",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You can cancel this deletion at any time before the scheduled date by continuing to use the app.",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.cancelAccountDeletion() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel Account Deletion")
                        }
                    } else {
                        // Show deletion request option
                        Text(
                            text = "Deleting your account will:",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        listOf(
//                            "• Remove all your personal data after 30 days",
                            "• Remove all your data after 30 days",
//                            "• Delete your trip history",
//                            "• Cancel any active bookings",
                            "• Remove your profile permanently"
                        ).forEach { item ->
                            Text(
                                text = item,
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
//
                        Spacer(modifier = Modifier.height(12.dp))

//                        Text(
//                            text = "You will have 30 days to cancel this request. If you log in during this period, the deletion will be cancelled automatically.",
//                            fontSize = 14.sp,
//                            color = Color(0xFF666666),
//                            fontWeight = FontWeight.Medium
//                        )

//                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFC62828)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFC62828)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Request Account Deletion")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF9F9F9)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "App Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Version: 1.0.0",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            // Contact Admin Section
            val context = LocalContext.current

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD) // light blue background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Need more help?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "islamoveapp@gmail.com",
                        fontSize = 14.sp,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:islamoveapp@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Islamove Support Request")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Delete Account Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deletePassword = ""
                },
                title = {
                    Text(
                        text = "Confirm Account Deletion",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "This action will schedule your account for deletion in 30 days. You can cancel anytime before then by logging in.",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Enter your password to confirm:",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828)
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFC62828),
                                focusedLabelColor = Color(0xFFC62828)
                            )
                        )

                        uiState.errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (deletePassword.isNotBlank()) {
                                viewModel.requestAccountDeletion(
                                    password = deletePassword,
                                    onSuccess = {
                                        showDeleteDialog = false
                                        deletePassword = ""
                                        // Logout after successful deletion request
                                        onLogout()
                                    }
                                )
                            }
                        },
                        enabled = deletePassword.isNotBlank() && !uiState.isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828)
                        )
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Confirm Deletion")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showDeleteDialog = false
                            deletePassword = ""
                        },
                        enabled = !uiState.isSubmitting
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun FAQItem(
    question: String,
    answer: String
) {
    Column {
        Text(
            text = "Q: $question",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "A: $answer",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            lineHeight = 18.sp
        )
    }
}