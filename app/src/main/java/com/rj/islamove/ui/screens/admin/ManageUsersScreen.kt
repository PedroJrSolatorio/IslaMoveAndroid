package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.VerificationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToUserDetail: (User) -> Unit = {},
    viewModel: ManageUsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Refresh report counts when screen becomes visible/returns from navigation
    LaunchedEffect(Unit) {
        viewModel.refreshDriverReportCounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        ManageUsersHeader(onNavigateBack = onNavigateBack)

        // Search Bar
        SearchBar(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery
        )

        // Filter Tabs
        FilterTabs(
            selectedFilter = uiState.selectedFilter,
            selectedStatusFilter = uiState.selectedStatusFilter,
            isStatusDropdownExpanded = uiState.isStatusDropdownExpanded,
            onFilterSelected = viewModel::updateSelectedFilter,
            onStatusFilterSelected = viewModel::updateSelectedStatusFilter,
            onToggleStatusDropdown = viewModel::toggleStatusDropdown,
            onCloseStatusDropdown = viewModel::closeStatusDropdown
        )

        // Users List
        UsersList(
            users = uiState.filteredUsers,
            driverReportCounts = uiState.driverReportCounts,
            userCommentCounts = uiState.userCommentCounts,
            isLoading = uiState.isLoading,
            onUserClick = onNavigateToUserDetail
        )
    }
}

@Composable
private fun ManageUsersHeader(
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Text(
            text = "Manage Users",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                text = "Search users by name, email...",
                color = Color(0xFF9E9E9E),
                fontSize = 14.sp
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF9E9E9E)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFE0E0E0),
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
private fun FilterTabs(
    selectedFilter: UserFilter,
    selectedStatusFilter: StatusFilter,
    isStatusDropdownExpanded: Boolean,
    onFilterSelected: (UserFilter) -> Unit,
    onStatusFilterSelected: (StatusFilter) -> Unit,
    onToggleStatusDropdown: () -> Unit,
    onCloseStatusDropdown: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTab(
                text = "All",
                isSelected = selectedFilter == UserFilter.ALL,
                onClick = { onFilterSelected(UserFilter.ALL) }
            )
            FilterTab(
                text = "Passenger",
                isSelected = selectedFilter == UserFilter.PASSENGER,
                onClick = { onFilterSelected(UserFilter.PASSENGER) }
            )
            FilterTab(
                text = "Driver",
                isSelected = selectedFilter == UserFilter.DRIVER,
                onClick = { onFilterSelected(UserFilter.DRIVER) }
            )

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onToggleStatusDropdown() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = getStatusFilterText(selectedStatusFilter),
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dropdown",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isStatusDropdownExpanded,
                    onDismissRequest = onCloseStatusDropdown
                ) {
                    val statusOptions = getStatusFilterOptions(selectedFilter)
                    statusOptions.forEach { statusFilter ->
                        DropdownMenuItem(
                            text = { Text(getStatusFilterText(statusFilter)) },
                            onClick = {
                                onStatusFilterSelected(statusFilter)
                                onCloseStatusDropdown()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        color = if (isSelected) Color(0xFF007AFF) else Color.Black,
        modifier = Modifier
            .clickable { onClick() }
            .background(
                color = if (isSelected) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun UsersList(
    users: List<User>,
    driverReportCounts: Map<String, Int>,
    userCommentCounts: Map<String, Int>,
    isLoading: Boolean,
    onUserClick: (User) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFF007AFF)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = users,
                key = { user -> user.uid }
            ) { user ->
                UserItem(
                    user = user,
                    reportCount = driverReportCounts[user.uid] ?: 0,
                    commentCount = userCommentCounts[user.uid] ?: 0,
                    onClick = { onUserClick(user) }
                )
            }
        }
    }
}

@Composable
private fun UserItem(
    user: User,
    reportCount: Int,
    commentCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F0))
                    .border(1.dp, Color(0xFFE0E0E0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl.isNullOrEmpty()) {
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                } else {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = "Profile Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_camera),
                        placeholder = painterResource(android.R.drawable.ic_menu_camera),
                        onError = {
                            // Log error for debugging
                            android.util.Log.e("ManageUsers", "Failed to load profile image: ${user.profileImageUrl}")
                        }
                    )
                }
            }

            // User Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = user.displayName.ifEmpty { "No Name" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = when (user.userType) {
                        UserType.PASSENGER -> "Passenger"
                        UserType.DRIVER -> "Driver"
                        UserType.ADMIN -> "Admin"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }

            // Status Badge with Report Icon
            StatusBadgeWithReport(
                user = user,
                reportCount = reportCount,
                commentCount = commentCount
            )
        }
    }
}

@Composable
private fun StatusBadgeWithReport(
    user: User,
    reportCount: Int,
    commentCount: Int
) {
    val (backgroundColor, textColor, statusText) = when (user.userType) {
        UserType.DRIVER -> {
            // For drivers, show verification status
            when (user.driverData?.verificationStatus) {
                VerificationStatus.APPROVED -> Triple(Color(0xFFE8F5E8), Color(0xFF4CAF50), "Verified")
                VerificationStatus.PENDING -> Triple(Color(0xFFFFF3E0), Color(0xFFF57C00), "Pending")
                VerificationStatus.REJECTED -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "Rejected")
                VerificationStatus.UNDER_REVIEW -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), "Under Review")
                null -> Triple(Color(0xFFF5F5F5), Color(0xFF757575), "Pending")
            }
        }
        else -> {
            // For passengers and admins, show active status
            when {
                !user.isActive -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "Blocked")
                else -> Triple(Color(0xFFE8F5E8), Color(0xFF4CAF50), "Active")
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show red flag icon and count for drivers with pending reports (left side)
        if (user.userType == UserType.DRIVER && reportCount > 0) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Report",
                tint = Color(0xFFFF5722),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reportCount.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5722)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Show comment icon for users with comments (left side, after report icon if present)
        if (commentCount > 0) {
            Icon(
                imageVector = Icons.Default.Comment,
                contentDescription = "Comment",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = commentCount.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Status Badge
        Text(
            text = statusText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

enum class UserFilter {
    ALL, PASSENGER, DRIVER
}

private fun getStatusFilterOptions(userFilter: UserFilter): List<StatusFilter> {
    return when (userFilter) {
        UserFilter.ALL -> listOf(StatusFilter.ALL)
        UserFilter.PASSENGER -> listOf(StatusFilter.ALL, StatusFilter.ACTIVE, StatusFilter.BLOCKED)
        UserFilter.DRIVER -> listOf(StatusFilter.ALL, StatusFilter.VERIFIED, StatusFilter.PENDING, StatusFilter.REJECTED, StatusFilter.UNDER_REVIEW)
    }
}

private fun getStatusFilterText(statusFilter: StatusFilter): String {
    return when (statusFilter) {
        StatusFilter.ALL -> "Status"
        StatusFilter.ACTIVE -> "Active"
        StatusFilter.BLOCKED -> "Blocked"
        StatusFilter.VERIFIED -> "Verified"
        StatusFilter.PENDING -> "Pending"
        StatusFilter.REJECTED -> "Rejected"
        StatusFilter.UNDER_REVIEW -> "Under Review"
    }
}