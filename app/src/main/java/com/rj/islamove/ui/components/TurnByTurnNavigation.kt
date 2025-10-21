package com.rj.islamove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.data.models.NavigationInstruction
import com.rj.islamove.ui.theme.IslamovePrimary
import kotlin.math.roundToInt

/**
 * Main turn-by-turn navigation overlay component
 */
@Composable
fun TurnByTurnNavigationOverlay(
    currentInstruction: NavigationInstruction?,
    nextInstruction: NavigationInstruction?,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Current instruction
            if (currentInstruction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Direction icon
                    Icon(
                        imageVector = getDirectionIcon(currentInstruction.type, currentInstruction.modifier),
                        contentDescription = null,
                        tint = IslamovePrimary,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Instruction details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentInstruction.instruction,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDistance(currentInstruction.distance),
                                fontSize = 14.sp,
                                color = Color.Gray
                            )

                            Text(
                                text = formatDuration(currentInstruction.duration),
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // Stop navigation button
                    IconButton(
                        onClick = onStopNavigation
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop Navigation",
                            tint = Color.Red
                        )
                    }
                }

                // Next instruction preview
                if (nextInstruction != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.LightGray
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getDirectionIcon(nextInstruction.type, nextInstruction.modifier),
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Then: ${nextInstruction.instruction}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "in ${formatDistance(nextInstruction.distance)}",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            } else {
                // No instructions available
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = IslamovePrimary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Navigation active",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onStopNavigation
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop Navigation",
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact navigation instructions list
 */
@Composable
fun NavigationInstructionsList(
    instructions: List<NavigationInstruction>,
    currentInstructionIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(instructions.take(5)) { instruction ->
                val isCurrentInstruction = instructions.indexOf(instruction) == currentInstructionIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrentInstruction) IslamovePrimary.copy(alpha = 0.1f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getDirectionIcon(instruction.type, instruction.modifier),
                        contentDescription = null,
                        tint = if (isCurrentInstruction) IslamovePrimary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = instruction.instruction,
                            fontSize = if (isCurrentInstruction) 14.sp else 12.sp,
                            fontWeight = if (isCurrentInstruction) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrentInstruction) Color.Black else Color.Gray
                        )
                    }

                    Text(
                        text = formatDistance(instruction.distance),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Get appropriate icon for navigation instruction
 */
private fun getDirectionIcon(type: String, modifier: String): ImageVector {
    return when (type) {
        "turn" -> when (modifier) {
            "left", "slight left", "sharp left" -> Icons.Default.ArrowBack
            "right", "slight right", "sharp right" -> Icons.Default.ArrowForward
            else -> Icons.Default.ArrowForward
        }
        "merge" -> Icons.Default.ArrowForward
        "continue" -> Icons.Default.ArrowForward
        "arrive" -> Icons.Default.LocationOn
        "depart" -> Icons.Default.LocationOn
        "roundabout", "rotary" -> Icons.Default.ArrowForward
        "fork" -> Icons.Default.ArrowForward
        "ramp" -> Icons.Default.ArrowForward
        else -> Icons.Default.LocationOn
    }
}

/**
 * Format distance for display
 */
private fun formatDistance(distanceInMeters: Double): String {
    return when {
        distanceInMeters < 1000 -> "${distanceInMeters.roundToInt()}m"
        else -> "${"%.1f".format(distanceInMeters / 1000)}km"
    }
}

/**
 * Format duration for display
 */
private fun formatDuration(durationInSeconds: Double): String {
    val minutes = (durationInSeconds / 60).roundToInt()
    return when {
        minutes < 1 -> "${durationInSeconds.roundToInt()}s"
        minutes < 60 -> "${minutes}min"
        else -> "${minutes / 60}h ${minutes % 60}min"
    }
}