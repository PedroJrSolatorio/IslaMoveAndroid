package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDriverVerification: () -> Unit = {},
    onNavigateToLiveMonitoring: () -> Unit = {},
    onNavigateToSystemConfig: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToManageUsers: () -> Unit = {},
    viewModel: AdminHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 36.dp)
    ) {
        // Header
        AdminHeader(onNavigateToProfile = onNavigateToProfile)

        Spacer(modifier = Modifier.height(8.dp))

        // Stats Grid (2x2 layout)
        AdminStatsGrid(
            pendingDrivers = uiState.pendingDriversCount,
            activeRides = uiState.activeRidesCount,
            onlineDrivers = uiState.onlineDriversCount,
            verifiedDrivers = uiState.verifiedDriversCount,
            totalUsers = uiState.totalUsersCount
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Admin Controls Section
        Text(
            text = "Admin Controls",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Scrollable Admin Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User Verification Button with hover effect
            val userVerificationInteractionSource = remember { MutableInteractionSource() }
            val isUserVerificationHovered by userVerificationInteractionSource.collectIsHoveredAsState()

            OutlinedButton(
                onClick = onNavigateToDriverVerification,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .hoverable(userVerificationInteractionSource),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isUserVerificationHovered) Color(0xFF007AFF) else Color.Transparent,
                    contentColor = if (isUserVerificationHovered) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUserVerificationHovered) Color(0xFF007AFF) else Color(0xFFE0E0E0)
                ),
                interactionSource = userVerificationInteractionSource
            ) {
                Text(
                    text = "User Verification",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Secondary Action Buttons with borders
            OutlinedButton(
                onClick = onNavigateToManageUsers,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Text(
                    text = "Manage Users",
                    fontSize = 16.sp
                )
            }

            OutlinedButton(
                onClick = onNavigateToLiveMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Text(
                    text = "Live Monitoring",
                    fontSize = 16.sp
                )
            }

            OutlinedButton(
                onClick = onNavigateToSystemConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Text(
                    text = "System Configuration",
                    fontSize = 16.sp
                )
            }

            OutlinedButton(
                onClick = onNavigateToReports,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Text(
                    text = "Reports & Analytics",
                    fontSize = 16.sp
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AdminHeader(
    onNavigateToProfile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Admin Dashboard",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        IconButton(
            onClick = onNavigateToProfile
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profile",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AdminStatsGrid(
    pendingDrivers: Int,
    activeRides: Int,
    onlineDrivers: Int,
    verifiedDrivers: Int,
    totalUsers: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // First row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Verified Drivers",
                value = verifiedDrivers.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pending Applications",
                value = pendingDrivers.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // Second row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Ongoing Rides",
                value = activeRides.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Total Users",
                value = totalUsers.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

