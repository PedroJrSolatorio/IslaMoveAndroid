package com.rj.islamove.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rj.islamove.data.models.ReportType

@Composable
fun ReportDriverModal(
    driverName: String,
    onDismiss: () -> Unit,
    onSubmitReport: (ReportType, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReportType by remember { mutableStateOf<ReportType?>(null) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val reportTypes = listOf(
        ReportType.UNSAFE_DRIVING to "Unsafe Driving",
        ReportType.INAPPROPRIATE_BEHAVIOR to "Inappropriate Behavior",
        ReportType.OVERCHARGING to "Overcharging",
        ReportType.OTHER to "Other"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header - simpler style matching PNG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REPORT DRIVER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        letterSpacing = 0.5.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Question text
                Text(
                    text = "Why are you reporting $driverName?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Report type selection - cleaner style
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    reportTypes.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedReportType == type,
                                    onClick = {
                                        selectedReportType = type
                                        // Clear description if not selecting "Other"
                                        if (type != ReportType.OTHER) {
                                            description = ""
                                        }
                                    }
                                )
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReportType == type,
                                onClick = {
                                    selectedReportType = type
                                    // Clear description if not selecting "Other"
                                    if (type != ReportType.OTHER) {
                                        description = ""
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF4CAF50), // Green color like in PNG
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                // Show comment box when "Other" is selected
                if (selectedReportType == ReportType.OTHER) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Please describe the issue:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        placeholder = {
                            Text(
                                text = "Describe the issue...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons - matching PNG style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Go back button (outlined)
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Go back",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Continue button (filled green)
                    Button(
                        onClick = {
                            selectedReportType?.let { type ->
                                isSubmitting = true
                                onSubmitReport(type, description)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedReportType != null && !isSubmitting &&
                                (selectedReportType != ReportType.OTHER || description.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50), // Green color like in PNG
                            contentColor = Color.White,
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Continue",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}