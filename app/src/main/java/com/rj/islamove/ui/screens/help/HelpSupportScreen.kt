package com.rj.islamove.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onNavigateBack: () -> Unit,
    viewModel: HelpSupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var commentText by remember { mutableStateOf("") }

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