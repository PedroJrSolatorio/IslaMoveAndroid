package com.rj.islamove.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiCostMonitor(
    usageStats: Map<String, Any>,
    modifier: Modifier = Modifier,
    showDetailed: Boolean = false
) {
    val safetyStatus = usageStats["safetyStatus"] as? String ?: "SAFE"
    val dailyCount = usageStats["dailyRequestCount"] as? Int ?: 0
    val dailyLimit = usageStats["dailyLimit"] as? Int ?: 100
    val hourlyCount = usageStats["hourlyRequestCount"] as? Int ?: 0
    val hourlyLimit = usageStats["hourlyLimit"] as? Int ?: 10
    val estimatedCost = usageStats["estimatedDailyCost"] as? Double ?: 0.0
    val emergencyThrottle = usageStats["emergencyThrottleActive"] as? Boolean ?: false

    val (statusColor, statusIcon, statusText) = when (safetyStatus) {
        "EMERGENCY_THROTTLED" -> Triple(Color.Red, Icons.Default.Close, "EMERGENCY STOP")
        "APPROACHING_LIMIT" -> Triple(Color(0xFFFF9800), Icons.Default.Warning, "APPROACHING LIMIT")
        "MODERATE_USAGE" -> Triple(Color(0xFFFFEB3B), Icons.Default.Warning, "MODERATE USAGE")
        else -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "SAFE")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emergencyThrottle) Color.Red.copy(alpha = 0.1f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "API Cost Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor.copy(alpha = 0.2f),
                        labelColor = statusColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    title = "Daily",
                    value = "$dailyCount/$dailyLimit",
                    subtitle = "requests",
                    progress = dailyCount.toFloat() / dailyLimit.toFloat(),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                StatCard(
                    title = "Hourly",
                    value = "$hourlyCount/$hourlyLimit",
                    subtitle = "requests",
                    progress = hourlyCount.toFloat() / hourlyLimit.toFloat(),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                StatCard(
                    title = "Est. Cost",
                    value = "$${"%.2f".format(estimatedCost)}",
                    subtitle = "today",
                    progress = estimatedCost.toFloat() / 50.0f, // Assume $50 daily budget
                    modifier = Modifier.weight(1f)
                )
            }

            if (showDetailed) {
                Spacer(modifier = Modifier.height(16.dp))

                // Detailed Stats
                DetailedStats(usageStats)
            }

            // Emergency Warning
            if (emergencyThrottle) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🚨 Emergency throttle activated! API calls disabled to prevent cost overrun.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    progress >= 0.8f -> Color.Red
                    progress >= 0.5f -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }
            )
        }
    }
}

@Composable
private fun DetailedStats(usageStats: Map<String, Any>) {
    val cacheSize = usageStats["cacheSize"] as? Int ?: 0
    val cacheHitRate = usageStats["cacheHitRate"] as? Double ?: 0.0
    val hoursUntilReset = usageStats["hoursUntilDailyReset"] as? Long ?: 0
    val shortDistanceThreshold = usageStats["shortDistanceThreshold"] as? Double ?: 1.0

    Column {
        Text(
            text = "Detailed Statistics",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cache Hit Rate:",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${"%.1f".format(cacheHitRate)}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cache Size:",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "$cacheSize routes",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reset in:",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${hoursUntilReset}h",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Free routes under:",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${shortDistanceThreshold}km",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}