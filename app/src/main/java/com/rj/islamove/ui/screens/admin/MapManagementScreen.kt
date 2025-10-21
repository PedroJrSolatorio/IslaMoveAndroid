package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.ui.theme.IslamovePrimary
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Data models for zone compatibility
data class ZoneCompatibility(
    val zoneName: String,
    val compatibleZones: Set<String>
)

// UI State for Zone Compatibility
data class ZoneCompatibilityUiState(
    val isLoading: Boolean = false,
    val zones: List<String> = emptyList(),
    val compatibilities: Map<String, Set<String>> = emptyMap(),
    val errorMessage: String? = null
)

// ViewModel for Zone Compatibility
class ZoneCompatibilityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ZoneCompatibilityUiState())
    val uiState: StateFlow<ZoneCompatibilityUiState> = _uiState.asStateFlow()

    init {
        loadZoneCompatibilities()
    }

    private fun loadZoneCompatibilities() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // TODO: Load from Firestore /zoneCompatibility
        // For now, return known zones from BoundaryFareUtils
        val knownZones = listOf(
            "STA. CRUZ", "WILSON", "SAN JUAN", "POBLACION", "DON RUBEN",
            "JUSTINIANA", "AURELIO", "JACQUEZ", "MAHAYAHAY", "LUNA", "MATINGBE"
        )

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            zones = knownZones,
            compatibilities = emptyMap() // TODO: Load from Firestore
        )
    }

    fun updateCompatibility(zone: String, compatibleZones: Set<String>) {
        val updatedCompatibilities = _uiState.value.compatibilities.toMutableMap()
        updatedCompatibilities[zone] = compatibleZones
        _uiState.value = _uiState.value.copy(compatibilities = updatedCompatibilities)

        // TODO: Save to Firestore
        // For now, just update local state
        viewModelScope.launch {
            try {
                // TODO: Implement Firestore save
                // Save to /zoneCompatibility/{zone}
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ServiceAreaManagementViewModel = hiltViewModel(),
    zoneCompatibilityViewModel: ZoneCompatibilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoneCompatibilityUiState by zoneCompatibilityViewModel.uiState.collectAsState()
    var selectedArea by remember { mutableStateOf<ServiceArea?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Boundaries, 1 = Zone Compatibility

    // Auto-select the zone boundary area if it exists
    LaunchedEffect(uiState.serviceAreas) {
        val zoneArea = uiState.serviceAreas.find {
            it.name.contains("Zone Boundary", ignoreCase = true) ||
            it.name.contains("Boundary", ignoreCase = true)
        }
        if (zoneArea != null) {
            selectedArea = zoneArea
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = IslamovePrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Map Management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Draw and manage operational boundaries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tab Navigation
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Boundaries") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Zone Compatibility") }
            )
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                // Boundary content placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Boundary Management", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Boundary management functionality is available in Service Area Management")
                }
            }
            1 -> {
                // Zone Compatibility content placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Zone Compatibility", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Zone compatibility functionality is available in Service Area Management")
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && selectedArea != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedArea = null
            },
            title = { Text("Delete Boundary") },
            text = {
                Text("Are you sure you want to delete the boundary for \"${selectedArea!!.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedArea?.let { area ->
                            viewModel.deleteBoundary(area.id)
                        }
                        showDeleteDialog = false
                        selectedArea = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedArea = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error handling
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Handle error (you could show a snackbar here)
            viewModel.clearErrorMessage()
        }
    }
}

@Composable
private fun BoundaryCard(
    area: ServiceArea,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onView: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = area.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${area.boundary?.points?.size ?: 0} boundary points",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onView) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "View on Map",
                            tint = IslamovePrimary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}