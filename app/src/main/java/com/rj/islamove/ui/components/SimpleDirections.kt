package com.rj.islamove.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.ui.theme.IslamoveSecondary

/**
 * Simple directions button - like Google Maps "Directions" button
 */
@Composable
fun SimpleDirectionsButton(
    onStartDirections: () -> Unit,
    modifier: Modifier = Modifier,
    isNavigating: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isNavigating) Color.Red else IslamoveSecondary
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isNavigating) Icons.Default.Close else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isNavigating) "Stop Directions" else "Start Directions",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Button(
                onClick = onStartDirections,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = if (isNavigating) Color.Red else IslamoveSecondary
                )
            ) {
                Text(if (isNavigating) "STOP" else "START")
            }
        }
    }
}

/**
 * Simple navigation overlay for when directions are active
 */
@Composable
fun SimpleNavigationOverlay(
    currentInstruction: String,
    distance: String,
    onStopDirections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction icon
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = IslamovePrimary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Instruction text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentInstruction,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                if (distance.isNotEmpty()) {
                    Text(
                        text = distance,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // Stop button
            IconButton(
                onClick = onStopDirections
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Stop Directions",
                    tint = Color.Red
                )
            }
        }
    }
}