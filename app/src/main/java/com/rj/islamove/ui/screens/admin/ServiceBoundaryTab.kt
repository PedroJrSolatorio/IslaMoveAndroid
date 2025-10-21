package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.theme.IslamovePrimary

// Service Boundary Tab - For driver filtering boundary
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceBoundaryTab(
    viewModel: ServiceAreaManagementViewModel,
    uiState: com.rj.islamove.data.models.ServiceAreaUiState,
    modifier: Modifier = Modifier
) {
    var isDrawingBoundary by remember { mutableStateOf(false) }
    var boundaryPoints by remember { mutableStateOf<List<BoundaryPoint>>(emptyList()) }
    var selectedPointIndex by remember { mutableIntStateOf(-1) }
    var serviceBoundaryName by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    var editingBoundary by remember { mutableStateOf<com.rj.islamove.data.models.ServiceArea?>(null) }

    // Find existing service boundary
    // Look for the most recent boundary that is NOT a zone boundary (excluding zone boundaries tab)
    val serviceBoundary = uiState.serviceAreas
        .filter {
            it.boundary != null &&
            it.boundary.points.isNotEmpty() &&
            !it.name.uppercase().contains("ZONE")
        }
        .maxByOrNull { it.lastUpdated }

    // Debug logging
    LaunchedEffect(uiState.serviceAreas) {
        android.util.Log.d("ServiceBoundaryTab", "Total service areas: ${uiState.serviceAreas.size}")
        uiState.serviceAreas.forEach { area ->
            android.util.Log.d("ServiceBoundaryTab", "Area: ${area.name}, hasBoundary: ${area.boundary != null}, points: ${area.boundary?.points?.size ?: 0}")
        }
        if (serviceBoundary != null) {
            android.util.Log.d("ServiceBoundaryTab", "Found service boundary: ${serviceBoundary.name} with ${serviceBoundary.boundary?.points?.size} points")
        } else {
            android.util.Log.d("ServiceBoundaryTab", "No service boundary found")
        }
    }

    // Load existing boundary if available
    LaunchedEffect(serviceBoundary) {
        if (serviceBoundary?.boundary != null && boundaryPoints.isEmpty() && !isDrawingBoundary) {
            boundaryPoints = serviceBoundary.boundary.points
            android.util.Log.d("ServiceBoundaryTab", "Loaded boundary points: ${boundaryPoints.size}")
        }
    }

    // Helper functions for boundary point management (same as ZoneBoundaryViewModel)
    fun addBoundaryPoint(lat: Double, lng: Double) {
        if (selectedPointIndex >= 0 && selectedPointIndex < boundaryPoints.size) {
            // Move selected point
            boundaryPoints = boundaryPoints.mapIndexed { index, point ->
                if (index == selectedPointIndex) BoundaryPoint(lat, lng) else point
            }
            selectedPointIndex = -1
        } else {
            // Add new point
            boundaryPoints = boundaryPoints + BoundaryPoint(lat, lng)
        }
    }

    fun selectBoundaryPoint(index: Int) {
        selectedPointIndex = index
    }

    fun deselectBoundaryPoint() {
        selectedPointIndex = -1
    }

    fun clearBoundaryPoints() {
        boundaryPoints = emptyList()
        selectedPointIndex = -1
    }

    fun cancelDrawing() {
        isDrawingBoundary = false
        boundaryPoints = editingBoundary?.boundary?.points ?: serviceBoundary?.boundary?.points ?: emptyList()
        selectedPointIndex = -1
        editingBoundary = null
    }

    if (isDrawingBoundary) {
        // Drawing Mode - Full screen map with controls (same as Zone Boundary)
        Column(modifier = Modifier.fillMaxSize()) {
            // Drawing Controls Card (matching Zone Boundary style)
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3)) // Blue for service boundaries
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingBoundary != null)
                                "Editing: ${editingBoundary!!.name}"
                            else
                                "Drawing Service Boundary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${boundaryPoints.size} points",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (selectedPointIndex >= 0)
                            "Point ${selectedPointIndex + 1} selected. Tap on map to move it."
                        else
                            "Tap on points to select them, then tap on map to move. Or tap empty area to add new points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Normal
                    )

                    // Show instruction when a point is selected
                    if (selectedPointIndex >= 0) {
                        Text(
                            text = "Point ${selectedPointIndex + 1} selected. Click anywhere on the map to move it, or use 'Deselect' to stop moving.",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // First row of buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { cancelDrawing() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { clearBoundaryPoints() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Clear", maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Second row of buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { deselectBoundaryPoint() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            enabled = selectedPointIndex >= 0
                        ) {
                            Text("Deselect", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                // Pre-fill name when editing before showing dialog
                                if (editingBoundary != null) {
                                    serviceBoundaryName = editingBoundary!!.name
                                }
                                showNameDialog = true
                            },
                            enabled = boundaryPoints.size >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", color = Color.White, maxLines = 1)
                        }
                    }
                }
            }

            // Map
            val centerLat = if (boundaryPoints.isNotEmpty()) {
                boundaryPoints.map { it.latitude }.average()
            } else 10.0080
            val centerLng = if (boundaryPoints.isNotEmpty()) {
                boundaryPoints.map { it.longitude }.average()
            } else 125.5718

            MapboxRideView(
                modifier = Modifier.fillMaxSize(),
                initialLocation = Point.fromLngLat(centerLng, centerLat),
                showUserLocation = true,
                showRoute = false,
                showOnlineDrivers = false,
                showServiceAreaBoundaries = false,
                showZoneBoundaries = false,
                isDrawingBoundaryMode = true,
                boundaryDrawingPoints = boundaryPoints,
                selectedBoundaryPointIndex = if (selectedPointIndex >= 0) selectedPointIndex else null,
                mapStyle = "Streets",
                onBoundaryPointAdded = { lat, lng ->
                    addBoundaryPoint(lat, lng)
                },
                onBoundaryPointSelected = { index ->
                    if (index != null) {
                        selectBoundaryPoint(index)
                    } else {
                        deselectBoundaryPoint()
                    }
                },
                onBoundaryPointDrag = { index, lat, lng ->
                    // Move the point
                    boundaryPoints = boundaryPoints.mapIndexed { i, point ->
                        if (i == index) BoundaryPoint(lat, lng) else point
                    }
                },
                onBoundaryPointDragEnd = {
                    deselectBoundaryPoint()
                }
            )
        }

        // Name Dialog (show outside the Column)
        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = {
                    Text(if (editingBoundary != null) "Update Boundary Name" else "Enter Boundary Name")
                },
                text = {
                    Column {
                        Text("Enter a name for this service boundary:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serviceBoundaryName,
                            onValueChange = { serviceBoundaryName = it },
                            label = { Text("Boundary Name") },
                            placeholder = { Text("e.g., 'Main Service Area'") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editingBoundary != null) {
                                // Update existing
                                viewModel.updateServiceAreaWithBoundary(
                                    editingBoundary!!.copy(
                                        name = serviceBoundaryName,
                                        boundary = com.rj.islamove.data.models.ServiceAreaBoundary(
                                            points = boundaryPoints
                                        ),
                                        isActive = true
                                    )
                                )
                            } else {
                                // Create new
                                viewModel.addServiceAreaWithBoundary(
                                    com.rj.islamove.data.models.ServiceArea(
                                        name = serviceBoundaryName,
                                        boundary = com.rj.islamove.data.models.ServiceAreaBoundary(
                                            points = boundaryPoints
                                        ),
                                        isActive = true
                                    )
                                )
                            }
                            showNameDialog = false
                            isDrawingBoundary = false
                            serviceBoundaryName = ""
                            editingBoundary = null
                        },
                        enabled = serviceBoundaryName.isNotBlank()
                    ) {
                        Text(if (editingBoundary != null) "Update" else "Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNameDialog = false
                        serviceBoundaryName = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    } else {
        // Management Mode
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service Boundary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Define where your app works. Only drivers inside this boundary will be able to accept rides. This is separate from zone boundaries used for fare management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Boundary Card
            if (serviceBoundary != null && serviceBoundary.boundary != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                                    text = serviceBoundary.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${serviceBoundary.boundary.points.size} boundary points",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Active - Filtering drivers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        editingBoundary = serviceBoundary
                                        boundaryPoints = serviceBoundary.boundary.points
                                        serviceBoundaryName = serviceBoundary.name
                                        isDrawingBoundary = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = IslamovePrimary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteServiceArea(serviceBoundary.id)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // No boundary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "No boundary",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Service Boundary Set",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Without a service boundary, the app will use the default San Jose, Dinagat Islands area",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = {
                    isDrawingBoundary = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (serviceBoundary == null) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = if (serviceBoundary == null) "Create" else "Edit"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (serviceBoundary == null) "Draw Service Boundary" else "Edit Service Boundary")
            }
        }
    }
}
