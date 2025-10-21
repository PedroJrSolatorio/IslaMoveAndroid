package com.rj.islamove.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.models.DriverReport
import com.rj.islamove.data.repository.UserRepository
import com.rj.islamove.data.repository.UserStatusRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.rj.islamove.data.repository.DriverReportRepository
import com.rj.islamove.data.models.SupportComment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageUsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userStatusRepository: UserStatusRepository,
    private val supportCommentRepository: SupportCommentRepository,
    private val driverReportRepository: DriverReportRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageUsersUiState())
    val uiState: StateFlow<ManageUsersUiState> = _uiState.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    private val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    init {
        loadUsers()
        loadDriverReportCounts()
        loadUserCommentCounts()
        // Clean up any stale pending operations on app start
        cleanupStalePendingOperations()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            userRepository.getAllUsers()
                .collect { result ->
                    result.fold(
                        onSuccess = { users ->
                            // Apply user status overrides to Firebase data
                            val statusOverrides = userStatusRepository.userStatusOverrides.value
                            val pendingOps = userStatusRepository.pendingFirebaseOps.value

                            // Debug logging
                            android.util.Log.d("ManageUsersVM", "Firebase update received: ${users.size} users")
                            android.util.Log.d("ManageUsersVM", "Status overrides: $statusOverrides")
                            android.util.Log.d("ManageUsersVM", "Pending ops: $pendingOps")

                            val mergedUsers = users.map { firebaseUser ->
                                // Apply status override if it exists
                                val overrideStatus = statusOverrides[firebaseUser.uid]
                                if (overrideStatus != null) {
                                    android.util.Log.d("ManageUsersVM", "Applying override for ${firebaseUser.uid}: isActive=$overrideStatus")
                                    firebaseUser.copy(isActive = overrideStatus)
                                } else {
                                    firebaseUser
                                }
                            }

                            _allUsers.value = mergedUsers
                            updateFilteredUsers()
                            loadUserCommentCounts() // Load comment counts after users are loaded
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = null
                                )
                            }

                            // Remove overrides for users whose Firebase updates have completed
                            // and match the override (meaning Firebase is now in sync)
                            val outdatedOverrides = statusOverrides.filter { (uid, overrideStatus) ->
                                val firebaseUser = users.find { it.uid == uid }
                                firebaseUser != null &&
                                firebaseUser.isActive == overrideStatus &&
                                !pendingOps.contains(uid)
                            }

                            if (outdatedOverrides.isNotEmpty()) {
                                outdatedOverrides.keys.forEach { uid ->
                                    userStatusRepository.removeStatusOverride(uid)
                                }
                                android.util.Log.d("ManageUsersVM", "Removed outdated overrides: ${outdatedOverrides.keys}")
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message
                                )
                            }
                        }
                    )
                }
        }
    }

    fun refreshDriverReportCounts() {
        loadDriverReportCounts()
    }

    fun refreshUserCommentCounts() {
        loadUserCommentCounts()
    }

    private fun loadDriverReportCounts() {
        viewModelScope.launch {
            try {
                val result = driverReportRepository.getDriverReportCounts()
                result.fold(
                    onSuccess = { reportCounts ->
                        _uiState.update { it.copy(driverReportCounts = reportCounts) }
                        android.util.Log.d("ManageUsersVM", "Loaded driver report counts: $reportCounts")
                    },
                    onFailure = { exception ->
                        android.util.Log.e("ManageUsersVM", "Failed to load driver report counts: ${exception.message}")
                        _uiState.update { it.copy(driverReportCounts = emptyMap()) }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ManageUsersVM", "Error loading driver report counts", e)
                _uiState.update { it.copy(driverReportCounts = emptyMap()) }
            }
        }
    }

    private fun loadUserCommentCounts() {
        viewModelScope.launch {
            try {
                val users = _allUsers.value
                val commentCounts = mutableMapOf<String, Int>()

                users.forEach { user ->
                    val comments = getUserComments(user.uid)
                    if (comments.isNotEmpty()) {
                        commentCounts[user.uid] = comments.size
                    }
                }

                _uiState.update { it.copy(userCommentCounts = commentCounts) }
                android.util.Log.d("ManageUsersVM", "Loaded user comment counts: $commentCounts")
            } catch (e: Exception) {
                android.util.Log.e("ManageUsersVM", "Error loading user comment counts", e)
                _uiState.update { it.copy(userCommentCounts = emptyMap()) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateFilteredUsers()
    }

    fun updateSelectedFilter(filter: UserFilter) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                selectedStatusFilter = StatusFilter.ALL // Reset status filter when changing user type
            )
        }
        updateFilteredUsers()
    }

    fun updateSelectedStatusFilter(statusFilter: StatusFilter) {
        _uiState.update { it.copy(selectedStatusFilter = statusFilter) }
        updateFilteredUsers()
    }

    fun toggleStatusDropdown() {
        _uiState.update { it.copy(isStatusDropdownExpanded = !it.isStatusDropdownExpanded) }
    }

    fun closeStatusDropdown() {
        _uiState.update { it.copy(isStatusDropdownExpanded = false) }
    }

    private fun updateFilteredUsers() {
        val currentState = _uiState.value

        // Users already have overrides applied in loadUsers(), so just use _allUsers.value directly
        val usersWithOverrides = _allUsers.value

        val filteredUsers = usersWithOverrides
            .filter { user ->
                // Apply search filter
                val matchesSearch = if (currentState.searchQuery.isBlank()) {
                    true
                } else {
                    user.displayName.contains(currentState.searchQuery, ignoreCase = true) ||
                    user.email?.contains(currentState.searchQuery, ignoreCase = true) == true ||
                    user.phoneNumber.contains(currentState.searchQuery, ignoreCase = true)
                }

                // Apply type filter
                val matchesType = when (currentState.selectedFilter) {
                    UserFilter.ALL -> true
                    UserFilter.PASSENGER -> user.userType == UserType.PASSENGER
                    UserFilter.DRIVER -> user.userType == UserType.DRIVER
                }

                // Apply status filter based on user type
                val matchesStatus = when (currentState.selectedStatusFilter) {
                    StatusFilter.ALL -> true
                    StatusFilter.ACTIVE -> user.isActive
                    StatusFilter.BLOCKED -> !user.isActive
                    StatusFilter.VERIFIED -> user.userType == UserType.DRIVER && user.driverData?.verificationStatus == VerificationStatus.APPROVED
                    StatusFilter.PENDING -> user.userType == UserType.DRIVER && (user.driverData?.verificationStatus == VerificationStatus.PENDING || user.driverData?.verificationStatus == null)
                    StatusFilter.REJECTED -> user.userType == UserType.DRIVER && user.driverData?.verificationStatus == VerificationStatus.REJECTED
                    StatusFilter.UNDER_REVIEW -> user.userType == UserType.DRIVER && user.driverData?.verificationStatus == VerificationStatus.UNDER_REVIEW
                }

                matchesSearch && matchesType && matchesStatus
            }
            .sortedBy { user ->
                // Keep consistent sorting by display name only to prevent position swapping
                user.displayName.lowercase()
            }

        _uiState.update { it.copy(filteredUsers = filteredUsers) }
    }

    fun updateUserStatus(user: User, newActiveStatus: Boolean) {
        viewModelScope.launch {
            // Debug logging
            android.util.Log.d("ManageUsersVM", "Updating user ${user.uid} to isActive=$newActiveStatus")

            // Set status override immediately for instant UI feedback
            userStatusRepository.setStatusOverride(user.uid, newActiveStatus)
            android.util.Log.d("ManageUsersVM", "Added status override. Total overrides: ${userStatusRepository.userStatusOverrides.value.keys}")

            // Mark this operation as pending
            userStatusRepository.addPendingOperation(user.uid)

            // Trigger UI update immediately
            updateFilteredUsers()

            // Update in Firebase
            userRepository.updateUserStatus(
                uid = user.uid,
                isActive = newActiveStatus
            ).fold(
                onSuccess = {
                    android.util.Log.d("ManageUsersVM", "Firebase update successful for ${user.uid}")
                    // Remove from pending operations
                    userStatusRepository.removePendingOperation(user.uid)
                    // Note: Status override will be removed automatically in loadUsers()
                    // when Firebase data matches the override
                },
                onFailure = { error ->
                    android.util.Log.e("ManageUsersVM", "Firebase update failed for ${user.uid}: ${error.message}")
                    // Remove pending operation and status override
                    userStatusRepository.clearOverride(user.uid)

                    _uiState.update {
                        it.copy(error = "Failed to update user status: ${error.message}")
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * One-time cleanup function to fix any legacy "active" field issues
     * Call this if you're still experiencing UI sync issues
     */
    fun cleanupLegacyActiveFields() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            userRepository.cleanupLegacyActiveFields().fold(
                onSuccess = {
                    // Clear all overrides and pending operations after cleanup
                    userStatusRepository.clearAllOverrides()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Cleanup completed successfully. The active field issue should now be resolved."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Cleanup failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    private fun cleanupStalePendingOperations() {
        viewModelScope.launch {
            // Clear all pending operations on app start since they may be stale
            // Firebase operations should complete quickly, so any pending ops from a previous session
            // are likely stale
            val stalePendingOps = userStatusRepository.pendingFirebaseOps.value
            stalePendingOps.forEach { uid ->
                userStatusRepository.removePendingOperation(uid)
            }
            if (stalePendingOps.isNotEmpty()) {
                android.util.Log.d("ManageUsersVM", "Cleaned up ${stalePendingOps.size} stale pending operations")
            }
        }
    }

    /**
     * Get comments for a specific user
     */
    suspend fun getUserComments(userId: String): List<SupportComment> {
        return try {
            val result = supportCommentRepository.getUserComments(userId)
            result.getOrElse { emptyList() }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            android.util.Log.e("ManageUsersViewModel", "Error getting user comments", e)
            emptyList()
        }
    }

    /**
     * Update user discount percentage
     */
    fun updateUserDiscount(userId: String, discountPercentage: Int?) {
        viewModelScope.launch {
            userRepository.updateUserDiscount(userId, discountPercentage)
                .onSuccess {
                    android.util.Log.d("ManageUsersViewModel", "Successfully updated discount for user $userId")
                }
                .onFailure { exception ->
                    android.util.Log.e("ManageUsersViewModel", "Failed to update discount for user $userId: ${exception.message}")
                }
        }
    }

    /**
     * Update passenger verification status
     */
    fun updatePassengerVerification(userId: String, isVerified: Boolean) {
        viewModelScope.launch {
            val status = if (isVerified) DocumentStatus.APPROVED else DocumentStatus.REJECTED
            userRepository.updatePassengerDocumentStatus(userId, status)
                .onSuccess {
                    android.util.Log.d("ManageUsersViewModel", "Successfully updated verification for user $userId to $status")
                }
                .onFailure { exception ->
                    android.util.Log.e("ManageUsersViewModel", "Failed to update verification for user $userId: ${exception.message}")
                }
        }
    }

    /**
     * Update passenger verification status
     */

    /**
     * Update driver verification status with automatic discount reset
     */
    fun updateDriverVerification(userId: String, verificationStatus: VerificationStatus) {
        viewModelScope.launch {
            android.util.Log.d("ManageUsersViewModel", "Updating driver $userId verification status to $verificationStatus")

            // CRITICAL: If verification status is not APPROVED, automatically reset discount to null
            if (verificationStatus != VerificationStatus.APPROVED) {
                updateUserDiscount(userId, null)
                android.util.Log.d("ManageUsersViewModel", "Reset discount for driver $userId due to verification status change")
            }

            // Update driver verification status
            val updates = mapOf<String, Any>(
                "driverData.verificationStatus" to verificationStatus.name,
                "driverData.verificationUpdatedAt" to System.currentTimeMillis()
            )
            updateUserPersonalInfo(userId, updates)
        }
    }

    /**
     * Update user personal information
     */
    fun updateUserPersonalInfo(userId: String, updates: Map<String, Any>) {
        viewModelScope.launch {
            userRepository.updateUserPersonalInfo(userId, updates)
                .onSuccess {
                    android.util.Log.d("ManageUsersViewModel", "Successfully updated personal info for user $userId")
                }
                .onFailure { exception ->
                    android.util.Log.e("ManageUsersViewModel", "Failed to update personal info for user $userId: ${exception.message}")
                }
        }
    }

    /**
     * Update user password
     */
    fun updateUserPassword(userId: String, newPassword: String) {
        viewModelScope.launch {
            userRepository.updateUserPassword(userId, newPassword)
                .onSuccess {
                    android.util.Log.d("ManageUsersViewModel", "Successfully updated password for user $userId")
                }
                .onFailure { exception ->
                    android.util.Log.e("ManageUsersViewModel", "Failed to update password for user $userId: ${exception.message}")
                }
        }
    }

    /**
     * Load driver reports for a specific driver
     */
    fun loadDriverReports(driverId: String) {
        viewModelScope.launch {
            try {
                val result = driverReportRepository.getReportsForDriver(driverId)
                result.fold(
                    onSuccess = { reports ->
                        _uiState.update { it.copy(driverReports = reports.sortedByDescending { it.timestamp }) }
                        android.util.Log.d("ManageUsersViewModel", "Loaded ${reports.size} reports for driver $driverId")
                    },
                    onFailure = { exception ->
                        android.util.Log.e("ManageUsersViewModel", "Failed to load driver reports for $driverId: ${exception.message}")
                        _uiState.update { it.copy(driverReports = emptyList()) }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ManageUsersViewModel", "Error loading driver reports for $driverId", e)
                _uiState.update { it.copy(driverReports = emptyList()) }
            }
        }
    }

    /**
     * Update the status of a driver report
     */
    fun updateReportStatus(reportId: String, newStatus: com.rj.islamove.data.models.ReportStatus) {
        viewModelScope.launch {
            try {
                val result = driverReportRepository.updateReportStatus(reportId, newStatus)
                result.fold(
                    onSuccess = {
                        android.util.Log.d("ManageUsersViewModel", "Successfully updated report $reportId status to $newStatus")
                        // Refresh the reports list to show the updated status
                        val currentReports = _uiState.value.driverReports
                        val updatedReports = currentReports.map { report ->
                            if (report.id == reportId) {
                                report.copy(status = newStatus)
                            } else {
                                report
                            }
                        }
                        _uiState.update { it.copy(driverReports = updatedReports) }

                        // Refresh driver report counts after status update
                        refreshDriverReportCounts()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("ManageUsersViewModel", "Failed to update report $reportId status: ${exception.message}")
                        _uiState.update { it.copy(error = "Failed to update report status: ${exception.message}") }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ManageUsersViewModel", "Error updating report $reportId status", e)
                _uiState.update { it.copy(error = "Error updating report status: ${e.message}") }
            }
        }
    }

    /**
     * Soft delete user (block + mark as deleted)
     */
    fun deleteUser(user: User) {
        viewModelScope.launch {
            // Prevent deletion of admin users
            if (user.userType == UserType.ADMIN) {
                android.util.Log.w("ManageUsersViewModel", "Attempted to delete admin user ${user.uid} - action blocked")
                _uiState.update {
                    it.copy(error = "Cannot delete admin users")
                }
                return@launch
            }

            val currentAdminId = firebaseAuth.currentUser?.uid ?: ""
            userRepository.deleteUser(user.uid, currentAdminId)
                .onSuccess {
                    android.util.Log.d("ManageUsersViewModel", "Successfully deleted user ${user.uid}")
                    // Update filtered users to remove the deleted user from the list
                    updateFilteredUsers()
                }
                .onFailure { exception ->
                    android.util.Log.e("ManageUsersViewModel", "Failed to delete user ${user.uid}: ${exception.message}")
                    _uiState.update {
                        it.copy(error = "Failed to delete user: ${exception.message}")
                    }
                }
        }
    }
}

data class ManageUsersUiState(
    val searchQuery: String = "",
    val selectedFilter: UserFilter = UserFilter.ALL,
    val selectedStatusFilter: StatusFilter = StatusFilter.ALL,
    val isStatusDropdownExpanded: Boolean = false,
    val filteredUsers: List<User> = emptyList(),
    val driverReports: List<DriverReport> = emptyList(),
    val driverReportCounts: Map<String, Int> = emptyMap(),
    val userCommentCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class StatusFilter {
    ALL, ACTIVE, BLOCKED, VERIFIED, PENDING, REJECTED, UNDER_REVIEW
}