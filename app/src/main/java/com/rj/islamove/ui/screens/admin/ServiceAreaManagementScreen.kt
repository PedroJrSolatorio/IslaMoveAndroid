package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import android.util.Log
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.data.models.ServiceDestination
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.theme.IslamovePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceAreaManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ServiceAreaManagementViewModel = hiltViewModel(),
    zoneBoundaryViewModel: ZoneBoundaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoneBoundaryUiState by zoneBoundaryViewModel.uiState.collectAsState()
    var showDeleteDestinationDialog by remember { mutableStateOf<ServiceDestination?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Auto-switch to Destinations tab when drawing zone boundary
    LaunchedEffect(zoneBoundaryUiState.isDrawingBoundary) {
        if (zoneBoundaryUiState.isDrawingBoundary) {
            selectedTabIndex = 0 // Switch to Destinations tab
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
                    text = "Manage destinations and zone boundaries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Refresh button
            IconButton(onClick = {
                viewModel.loadServiceAreas()
                zoneBoundaryViewModel.loadZoneBoundaries()
            }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = IslamovePrimary
                )
            }
        }

        // Tab Layout
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Destinations") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Zone Boundaries") }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text("Service Boundary") }
            )
            Tab(
                selected = selectedTabIndex == 3,
                onClick = { selectedTabIndex = 3 },
                text = { Text("Compatibility") }
            )
        }

        // Content based on selected tab
        if (uiState.isLoading || zoneBoundaryUiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (selectedTabIndex) {
                0 -> ServiceAreaMapTab(
                    serviceAreas = uiState.serviceAreas,
                    uiState = uiState,
                    viewModel = viewModel,
                    zoneBoundaryViewModel = zoneBoundaryViewModel,
                    zoneBoundaryUiState = zoneBoundaryUiState,
                    onSwitchToZoneBoundariesTab = { selectedTabIndex = 1 },
                    onAddDestination = { name, lat, lng, color ->
                        // Find or create a destinations area, but don't use the boundary area
                        val destinationsArea = uiState.serviceAreas.find { it.name == "Destinations Area" }
                        val targetAreaId = destinationsArea?.id ?: if (uiState.serviceAreas.isNotEmpty()) {
                            uiState.serviceAreas.first { it.name != "Zone Boundary Area" }.id
                        } else {
                            "default_area"
                        }
                        viewModel.addDestination(targetAreaId, name, lat, lng, 0.0, 0.0, color)
                    },
                    onEditDestination = { destination ->
                        // Find the service area that contains this destination
                        val areaWithDestination = uiState.serviceAreas.find { area ->
                            area.destinations.any { it.id == destination.id }
                        }
                        val targetAreaId = areaWithDestination?.id ?: "default_area"
                        viewModel.updateDestination(
                            targetAreaId, destination.id, destination.name,
                            destination.latitude, destination.longitude,
                            0.0, 0.0
                        )
                    },
                    onDeleteDestination = { destinationId ->
                        // Find the service area that contains this destination
                        val areaWithDestination = uiState.serviceAreas.find { area ->
                            area.destinations.any { it.id == destinationId }
                        }
                        val targetAreaId = areaWithDestination?.id ?: "default_area"
                        viewModel.deleteDestination(targetAreaId, destinationId)
                    }
                )
                1 -> ZoneBoundaryTab(
                    zoneBoundaryViewModel = zoneBoundaryViewModel,
                    uiState = zoneBoundaryUiState
                )
                2 -> com.rj.islamove.ui.screens.admin.ServiceBoundaryTab(
                    viewModel = viewModel,
                    uiState = uiState
                )
                3 -> BoundaryCompatibilityTab(
                    zoneBoundaryViewModel = zoneBoundaryViewModel,
                    uiState = zoneBoundaryUiState
                )
            }
        }
    }

    // Delete Destination Dialog
    showDeleteDestinationDialog?.let { destination ->
        AlertDialog(
            onDismissRequest = { showDeleteDestinationDialog = null },
            title = { Text("Delete Destination") },
            text = {
                Text("Are you sure you want to delete '${destination.name}'?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDestination("default_area", destination.id) // Using default area
                        showDeleteDestinationDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDestinationDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error Dialog
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceAreaMapTab(
    serviceAreas: List<ServiceArea>,
    uiState: com.rj.islamove.data.models.ServiceAreaUiState,
    viewModel: ServiceAreaManagementViewModel,
    zoneBoundaryViewModel: ZoneBoundaryViewModel,
    zoneBoundaryUiState: com.rj.islamove.data.models.ZoneBoundaryUiState,
    onSwitchToZoneBoundariesTab: () -> Unit,
    onAddDestination: (String, Double, Double, String) -> Unit,
    onEditDestination: (ServiceDestination) -> Unit,
    onDeleteDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDestinationEditor by remember { mutableStateOf<Pair<ServiceDestination?, Point>?>(null) }

    val centerLat = 10.0195507
    val centerLng = 125.5760264

    val landmarks: List<CustomLandmark> = serviceAreas.flatMap { area ->
        area.destinations.map { destination ->
            CustomLandmark(
                id = destination.id,
                name = destination.name,
                latitude = destination.latitude,
                longitude = destination.longitude,
                color = destination.markerColor,
                createdAt = destination.createdAt
            )
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Service Area Map",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = if (zoneBoundaryUiState.isDrawingBoundary)
                "Drawing Zone Boundary - Tap on map to add points"
            else
                "Tap on the map to add destinations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Zone Boundary Drawing Controls
        var showBoundaryNameDialog by remember { mutableStateOf(false) }
        var boundaryNameInput by remember { mutableStateOf("") }

        // Pre-fill boundary name when editing starts
        LaunchedEffect(zoneBoundaryUiState.editingBoundary) {
            if (zoneBoundaryUiState.editingBoundary != null) {
                boundaryNameInput = zoneBoundaryUiState.editingBoundary.name
                Log.d("ServiceAreaMapTab", "Pre-filled boundary name to: '${zoneBoundaryUiState.editingBoundary.name}'")
            }
        }

        if (zoneBoundaryUiState.isDrawingBoundary) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)) // Orange for zone boundaries
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (zoneBoundaryUiState.editingBoundary != null)
                                "Editing: ${zoneBoundaryUiState.editingBoundary.name}"
                            else
                                "Drawing Zone Boundary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${zoneBoundaryUiState.boundaryPoints.size} points",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (zoneBoundaryUiState.selectedPointIndex != null)
                            "Point ${zoneBoundaryUiState.selectedPointIndex?.plus(1)} selected. Tap on map to move it."
                        else
                            "Tap on points to select them, then tap on map to move. Or tap empty area to add new points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Normal
                    )

                    // Show instruction when a point is selected
                    if (zoneBoundaryUiState.selectedPointIndex != null) {
                        Text(
                            text = "Point ${zoneBoundaryUiState.selectedPointIndex!! + 1} selected. Click anywhere on the map to move it, or use 'Deselect' to stop moving.",
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
                            onClick = { zoneBoundaryViewModel.cancelDrawingBoundary() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { zoneBoundaryViewModel.clearBoundaryPoints() },
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
                            onClick = { zoneBoundaryViewModel.deselectBoundaryPoint() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            enabled = zoneBoundaryUiState.selectedPointIndex != null
                        ) {
                            Text("Deselect", maxLines = 1)
                        }

                        Button(
                            onClick = { showBoundaryNameDialog = true },
                            enabled = zoneBoundaryUiState.boundaryPoints.size >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", color = Color.White, maxLines = 1)
                        }
                    }
                }
            }

            // Boundary name dialog
            if (showBoundaryNameDialog) {
                AlertDialog(
                    onDismissRequest = { showBoundaryNameDialog = false },
                    title = {
                        Text(if (zoneBoundaryUiState.editingBoundary != null) "Update Boundary Name" else "Enter Boundary Name")
                    },
                    text = {
                        Column {
                            Text("Enter a name for this zone boundary:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = boundaryNameInput,
                                onValueChange = { boundaryNameInput = it.uppercase() },
                                label = { Text("Boundary Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (zoneBoundaryUiState.editingBoundary != null) {
                                    zoneBoundaryViewModel.updateBoundary(
                                        zoneBoundaryUiState.editingBoundary.id,
                                        boundaryNameInput
                                    )
                                } else {
                                    zoneBoundaryViewModel.finishDrawingBoundary(boundaryNameInput)
                                }
                                showBoundaryNameDialog = false
                                boundaryNameInput = ""
                                onSwitchToZoneBoundariesTab()
                            },
                            enabled = boundaryNameInput.isNotBlank()
                        ) {
                            Text(if (zoneBoundaryUiState.editingBoundary != null) "Update" else "Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showBoundaryNameDialog = false
                            boundaryNameInput = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MapboxRideView(
                modifier = Modifier.fillMaxSize(),
                initialLocation = Point.fromLngLat(centerLng, centerLat),
                showUserLocation = false,
                showRoute = false,
                showOnlineDrivers = false,
                customLandmarks = landmarks,
                serviceAreas = serviceAreas,
                showServiceAreaBoundaries = false,
                showBarangayBoundaries = true,
                isDrawingBoundaryMode = zoneBoundaryUiState.isDrawingBoundary,
                boundaryDrawingPoints = zoneBoundaryUiState.boundaryPoints,
                selectedBoundaryPointIndex = zoneBoundaryUiState.selectedPointIndex,
                zoneBoundaries = zoneBoundaryUiState.zoneBoundaries,
                showZoneBoundaries = zoneBoundaryUiState.isDrawingBoundary,
                mapStyle = "Streets",
                onMapClick = { point ->
                    if (!zoneBoundaryUiState.isDrawingBoundary) {
                        showDestinationEditor = null to point
                    }
                },
                onBoundaryPointAdded = { lat, lng ->
                    if (zoneBoundaryUiState.isDrawingBoundary) {
                        // If a point is selected, move it instead of adding a new point
                        if (zoneBoundaryUiState.selectedPointIndex != null) {
                            zoneBoundaryViewModel.updateBoundaryPoint(zoneBoundaryUiState.selectedPointIndex!!, lat, lng)
                        } else {
                            // No point selected, add a new point
                            zoneBoundaryViewModel.addBoundaryPoint(lat, lng)
                        }
                    }
                },
                onBoundaryPointDragStart = { index ->
                    // Zone boundary point drag start
                },
                onBoundaryPointDrag = { index, lat, lng ->
                    if (zoneBoundaryUiState.isDrawingBoundary) {
                        // Direct update for zone boundary points
                        zoneBoundaryViewModel.updateBoundaryPoint(index, lat, lng)
                    }
                },
                onBoundaryPointDragEnd = {
                    if (zoneBoundaryUiState.isDrawingBoundary) {
                        // Don't deselect here - let user manually deselect using the button
                        Log.d("ServiceAreaManagementScreen", "Drag ended for zone boundary point")
                    }
                },
                onBoundaryPointSelected = { index ->
                    if (zoneBoundaryUiState.isDrawingBoundary) {
                        if (index != null) {
                            zoneBoundaryViewModel.selectBoundaryPoint(index)
                        } else {
                            zoneBoundaryViewModel.deselectBoundaryPoint()
                        }
                    }
                },
                onLandmarkMarkerClick = { landmark ->
                    val destination = serviceAreas.flatMap { area ->
                        area.destinations.map { dest -> area.id to dest }
                    }.find { (_, dest) ->
                        dest.latitude == landmark.latitude &&
                        dest.longitude == landmark.longitude
                    }?.second

                    destination?.let {
                        val point = Point.fromLngLat(landmark.longitude, landmark.latitude)
                        showDestinationEditor = it to point
                    }
                }
            )
        }
    }

    // Show destination editor dialog
    showDestinationEditor?.let { (destination, point) ->
        DestinationEditorDialog(
            destination = destination,
            point = point,
            zoneBoundaries = zoneBoundaryUiState.zoneBoundaries,
            viewModel = viewModel,
            onSave = { name, color, boundaryFares ->
                if (destination != null) {
                    // Update existing destination with boundary fares only
                    val updatedDestination = destination.copy(
                        name = name,
                        markerColor = color,
                        latitude = point.latitude(),
                        longitude = point.longitude(),
                        regularFare = 0.0,
                        discountFare = 0.0
                    )
                    onEditDestination(updatedDestination)

                    // Update boundary fares for this destination
                    viewModel.updateBoundaryFaresForDestination(updatedDestination.name, boundaryFares)
                } else {
                    // Add new destination with boundary fares only
                    onAddDestination(
                        name,
                        point.latitude(),
                        point.longitude(),
                        color
                    )

                    // Set boundary fares for this destination
                    viewModel.setBoundaryFaresForDestination(name, boundaryFares)
                }
                showDestinationEditor = null
            },
            onDelete = if (destination != null) {
                {
                    onDeleteDestination(destination.id)
                    showDestinationEditor = null
                }
            } else null,
            onDismiss = {
                showDestinationEditor = null
            }
        )
    }
}

@Composable
fun ServiceAreasViewerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ServiceAreaManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadServiceAreas()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
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

            Column {
                Text(
                    text = "Service Areas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "View available destinations and service areas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Read-only service areas map
            ServiceAreasReadOnlyMapTab(
                serviceAreas = uiState.serviceAreas,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ServiceAreasReadOnlyMapTab(
    serviceAreas: List<ServiceArea>,
    viewModel: ServiceAreaManagementViewModel,
    modifier: Modifier = Modifier
) {
    val centerLat = 10.0195507
    val centerLng = 125.5760264

    // State to hold boundary fare display data
    var boundaryFareDisplayMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Load boundary fares for all destinations
    LaunchedEffect(serviceAreas) {
        val displayMap = mutableMapOf<String, String>()
        serviceAreas.forEach { area ->
            area.destinations.forEach { destination ->
                try {
                    val boundaryFareDisplay = viewModel.getBoundaryFareDisplayString(destination.name)
                    displayMap[destination.name] = boundaryFareDisplay
                } catch (e: Exception) {
                    Log.e("ServiceAreasReadOnlyMapTab", "Error loading boundary fares for ${destination.name}", e)
                    displayMap[destination.name] = "No boundary fares set"
                }
            }
        }
        boundaryFareDisplayMap = displayMap
    }

    val landmarks: List<CustomLandmark> = serviceAreas.flatMap { area ->
        area.destinations.map { destination ->
            val boundaryFareDisplay = boundaryFareDisplayMap[destination.name] ?: "Loading boundary fares..."
            val fullDisplay = if (boundaryFareDisplay != "No boundary fares set" && boundaryFareDisplay != "Loading boundary fares...") {
                "$boundaryFareDisplay"
            } else {
                "No boundary fares set"
            }
            CustomLandmark(
                id = destination.id,
                name = "${destination.name} - $fullDisplay",
                latitude = destination.latitude,
                longitude = destination.longitude,
                color = destination.markerColor,
                createdAt = destination.createdAt
            )
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Available Destinations",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "View all service areas and destinations with current fares",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MapboxRideView(
                modifier = Modifier.fillMaxSize(),
                initialLocation = Point.fromLngLat(centerLng, centerLat),
                showUserLocation = true,
                showRoute = false,
                showOnlineDrivers = false,
                customLandmarks = landmarks,
                mapStyle = "Satellite Streets",
                onMapClick = { /* Read-only, no interaction */ }
            )
        }

        // Show service areas summary
        if (serviceAreas.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Service Areas Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    serviceAreas.forEach { area ->
                        Text(
                            text = "• ${area.name}: ${area.destinations.size} destinations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationEditorDialog(
    destination: ServiceDestination?,
    point: Point,
    zoneBoundaries: List<com.rj.islamove.data.models.ZoneBoundary>,
    onDismiss: () -> Unit,
    onSave: (String, String, Map<String, Double>) -> Unit,
    onDelete: (() -> Unit)?,
    viewModel: ServiceAreaManagementViewModel = hiltViewModel()
) {
    var name by remember(destination) { mutableStateOf(destination?.name ?: "") }
    var selectedColor by remember { mutableStateOf("red") }

    // Get boundary names dynamically from created zone boundaries
    val boundaryNames = remember(zoneBoundaries) {
        zoneBoundaries
            .filter { it.isActive }
            .map { it.name }
            .sorted()
    }

    // Boundary fare states - store as strings to handle empty input properly
    val boundaryFareTexts = remember {
        mutableStateMapOf<String, String>().apply {
            // Initialize with empty strings - will be populated by LaunchedEffect
            boundaryNames.forEach { boundaryName ->
                put(boundaryName, "")
            }
        }
    }

    // Load existing boundary fares when destination is set
    LaunchedEffect(destination) {
        if (destination != null) {
            try {
                val existingFares = viewModel.getBoundaryFaresForDestination(destination.name)
                boundaryNames.forEach { boundaryName ->
                    val existingFare = existingFares[boundaryName]
                    boundaryFareTexts[boundaryName] = existingFare?.toString() ?: ""
                }
                Log.d("DestinationEditor", "Loaded ${existingFares.size} boundary fares for ${destination.name}")
            } catch (e: Exception) {
                Log.e("DestinationEditor", "Error loading boundary fares for ${destination.name}", e)
            }
        }
    }

    val markerColors = listOf(
        "red" to Color.Red,
        "blue" to Color.Blue,
        "green" to Color.Green,
        "orange" to Color(0xFFFF9800),
        "purple" to Color(0xFF9C27B0),
        "yellow" to Color.Yellow
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (destination != null) "Edit Destination" else "Add Destination")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Location: ${String.format("%.4f", point.latitude())}, ${String.format("%.4f", point.longitude())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Destination Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )


                // Color Selector
                Text("Marker Color:", style = MaterialTheme.typography.bodyMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(markerColors) { (colorName, color) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = color,
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (selectedColor == colorName) 3.dp else 1.dp,
                                    color = if (selectedColor == colorName) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorName }
                        )
                    }
                }

                // Boundary Fare Setup
                Spacer(modifier = Modifier.height(8.dp))
                Text("Boundary Fares (₱):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Special fares when traveling FROM these boundaries TO this destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(boundaryNames) { boundaryName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = boundaryName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = boundaryFareTexts[boundaryName] ?: "",
                                onValueChange = { newValue ->
                                    // Allow empty input or valid numbers
                                    if (newValue.isEmpty() || newValue.toDoubleOrNull() != null) {
                                        boundaryFareTexts[boundaryName] = newValue
                                    }
                                },
                                label = { Text("Fare") },
                                modifier = Modifier.width(100.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        // Convert string fare values to doubles, filtering out empty values
                        val boundaryFaresMap = boundaryFareTexts
                            .filter { it.value.isNotEmpty() }
                            .mapValues { it.value.toDouble() }
                        onSave(name.trim(), selectedColor, boundaryFaresMap)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (destination != null) "Update" else "Add")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

// Zone Boundary Tab
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneBoundaryTab(
    zoneBoundaryViewModel: ZoneBoundaryViewModel,
    uiState: com.rj.islamove.data.models.ZoneBoundaryUiState,
    modifier: Modifier = Modifier
) {
    var showBoundaryNameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<com.rj.islamove.data.models.ZoneBoundary?>(null) }
    var boundaryNameInput by remember { mutableStateOf("") }

    // Pre-fill boundary name when editing starts
    LaunchedEffect(uiState.editingBoundary) {
        if (uiState.editingBoundary != null) {
            boundaryNameInput = uiState.editingBoundary.name
            Log.d("ZoneBoundaryTab", "Pre-filled boundary name to: '${uiState.editingBoundary.name}'")
        }
        // Don't clear boundaryNameInput when editingBoundary is null
        // It should only be cleared when dialog is dismissed/confirmed
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Zone Boundaries for Fare Calculation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "These boundaries are used to determine zone-based fares",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { zoneBoundaryViewModel.startDrawingBoundary() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isDrawingBoundary
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Boundary")
                }

                // Migration button (only show if no boundaries exist)
                if (uiState.zoneBoundaries.isEmpty()) {
                    OutlinedButton(
                        onClick = { zoneBoundaryViewModel.migrateBoundaries() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Migrate Data")
                    }
                }
            }

            // Drawing mode controls
            if (uiState.isDrawingBoundary) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (uiState.editingBoundary != null)
                                "Editing: ${uiState.editingBoundary.name}"
                            else
                                "Drawing New Boundary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (uiState.selectedPointIndex != null)
                                "Point ${uiState.selectedPointIndex?.plus(1)} selected. Tap on map to move it."
                            else
                                "Tap on points to select them, then tap on map to move. Or tap empty area to add new points.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    Text(
                            text = "Points: ${uiState.boundaryPoints.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // First row of buttons
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { zoneBoundaryViewModel.cancelDrawingBoundary() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Cancel", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { zoneBoundaryViewModel.clearBoundaryPoints() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    )
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
                                    onClick = { zoneBoundaryViewModel.deselectBoundaryPoint() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    enabled = uiState.selectedPointIndex != null
                                ) {
                                    Text("Deselect", maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        Log.d("ZoneBoundaryTab", "Save button clicked! editingBoundary=${uiState.editingBoundary?.name}, boundaryNameInput='$boundaryNameInput'")
                                        showBoundaryNameDialog = true
                                        Log.d("ZoneBoundaryTab", "Set showBoundaryNameDialog=true")
                                    },
                                    enabled = uiState.boundaryPoints.size >= 3,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save", maxLines = 1)
                                }
                            }
                        }
                    }
                }

            // List of existing boundaries
            if (uiState.zoneBoundaries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Zone Boundaries",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add boundaries to enable zone-based fares",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.zoneBoundaries) { boundary ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { zoneBoundaryViewModel.selectBoundary(boundary) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = boundary.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${boundary.points.size - 1} points",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (boundary.boundaryFares.isNotEmpty()) {
                                        Text(
                                            text = "${boundary.boundaryFares.size} fares configured",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = IslamovePrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    zoneBoundaryViewModel.startEditingBoundary(boundary)
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(onClick = { showDeleteDialog = boundary }) {
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
            }
        }
    }

    // Boundary name dialog
    Log.d("ZoneBoundaryTab", "showBoundaryNameDialog=$showBoundaryNameDialog, boundaryNameInput='$boundaryNameInput'")

    if (showBoundaryNameDialog) {
        // Log the current value when dialog shows
        LaunchedEffect(Unit) {
            Log.d("ZoneBoundaryTab", "Dialog opened with boundaryNameInput: '$boundaryNameInput', editingBoundary: ${uiState.editingBoundary?.name}")
        }

        AlertDialog(
            onDismissRequest = { showBoundaryNameDialog = false },
            title = {
                Text(if (uiState.editingBoundary != null) "Update Boundary Name" else "Enter Boundary Name")
            },
            text = {
                Column {
                    Text("Enter a name for this zone boundary:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = boundaryNameInput,
                        onValueChange = { boundaryNameInput = it.uppercase() },
                        label = { Text("Boundary Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uiState.editingBoundary != null) {
                            zoneBoundaryViewModel.updateBoundary(
                                uiState.editingBoundary.id,
                                boundaryNameInput
                            )
                        } else {
                            zoneBoundaryViewModel.finishDrawingBoundary(boundaryNameInput)
                        }
                        showBoundaryNameDialog = false
                        boundaryNameInput = ""
                    },
                    enabled = boundaryNameInput.isNotBlank()
                ) {
                    Text(if (uiState.editingBoundary != null) "Update" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBoundaryNameDialog = false
                    boundaryNameInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { boundary ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Zone Boundary") },
            text = {
                Text("Are you sure you want to delete '${boundary.name}'? This will affect fare calculations.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        zoneBoundaryViewModel.deleteBoundary(boundary.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error dialog
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { zoneBoundaryViewModel.clearError() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { zoneBoundaryViewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Success dialog
    uiState.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { zoneBoundaryViewModel.clearSuccessMessage() },
            title = { Text("Success") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { zoneBoundaryViewModel.clearSuccessMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // Boundary Detail Dialog with Fare Management
    if (uiState.showBoundaryDialog && uiState.selectedBoundary != null) {
        BoundaryFareDialog(
            boundary = uiState.selectedBoundary!!,
            allBoundaries = uiState.zoneBoundaries,
            onDismiss = { zoneBoundaryViewModel.clearSelection() },
            onSave = { boundaryFares ->
                zoneBoundaryViewModel.updateBoundaryFares(
                    uiState.selectedBoundary!!.id,
                    boundaryFares
                )
                zoneBoundaryViewModel.clearSelection()
            },
            onEdit = {
                zoneBoundaryViewModel.startEditingBoundary(uiState.selectedBoundary!!)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoundaryFareDialog(
    boundary: com.rj.islamove.data.models.ZoneBoundary,
    allBoundaries: List<com.rj.islamove.data.models.ZoneBoundary>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit,
    onEdit: () -> Unit
) {
    // Get all boundaries INCLUDING current boundary (for within-boundary pricing)
    val otherBoundaries = allBoundaries.filter { it.isActive }

    // State for fare inputs - using strings to handle empty input
    val fareInputs = remember {
        mutableStateMapOf<String, String>().apply {
            otherBoundaries.forEach { otherBoundary ->
                val existingFare = boundary.boundaryFares[otherBoundary.name]
                put(otherBoundary.name, existingFare?.toString() ?: "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(boundary.name)
                    Text(
                        text = "${boundary.points.size - 1} points",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Boundary",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Boundary-to-Boundary Fares",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Set fares for trips FROM this boundary TO other boundaries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (otherBoundaries.isEmpty()) {
                    Text(
                        text = "No other boundaries available. Create more boundaries to set fares.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(otherBoundaries) { otherBoundary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "→ ${otherBoundary.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = fareInputs[otherBoundary.name] ?: "",
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.toDoubleOrNull() != null) {
                                            fareInputs[otherBoundary.name] = newValue
                                        }
                                    },
                                    label = { Text("₱") },
                                    modifier = Modifier.width(100.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Convert string inputs to map, filtering out empty values
                    val boundaryFaresMap = fareInputs
                        .filter { it.value.isNotEmpty() }
                        .mapValues { it.value.toDouble() }
                    onSave(boundaryFaresMap)
                }
            ) {
                Text("Save Fares")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}




// Boundary Compatibility Tab - For managing ride pooling compatibility
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoundaryCompatibilityTab(
    zoneBoundaryViewModel: ZoneBoundaryViewModel,
    uiState: com.rj.islamove.data.models.ZoneBoundaryUiState,
    modifier: Modifier = Modifier
) {
    var selectedBoundaryForEdit by remember { mutableStateOf<com.rj.islamove.data.models.ZoneBoundary?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Boundary Compatibility for Ride Pooling",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure which destination boundaries are compatible for ride pooling. Drivers with an active passenger can only receive requests for compatible destinations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // List of boundaries with compatibility settings
        if (uiState.zoneBoundaries.isEmpty()) {
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
                        contentDescription = "No boundaries",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Zone Boundaries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create zone boundaries first to configure compatibility",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            uiState.zoneBoundaries.forEach { boundary ->
                BoundaryCompatibilityCard(
                    boundary = boundary,
                    allBoundaries = uiState.zoneBoundaries,
                    onEditCompatibility = { selectedBoundaryForEdit = it },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    // Compatibility edit dialog
    selectedBoundaryForEdit?.let { boundary ->
        BoundaryCompatibilityDialog(
            boundary = boundary,
            allBoundaries = uiState.zoneBoundaries,
            onDismiss = { selectedBoundaryForEdit = null },
            onSave = { compatibleBoundaries ->
                zoneBoundaryViewModel.updateCompatibleBoundaries(
                    boundary.id,
                    compatibleBoundaries
                )
                selectedBoundaryForEdit = null
            }
        )
    }
}

@Composable
private fun BoundaryCompatibilityCard(
    boundary: com.rj.islamove.data.models.ZoneBoundary,
    allBoundaries: List<com.rj.islamove.data.models.ZoneBoundary>,
    onEditCompatibility: (com.rj.islamove.data.models.ZoneBoundary) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onEditCompatibility(boundary) }
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
                        text = boundary.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (boundary.compatibleBoundaries.isEmpty()) {
                            "No compatible boundaries set"
                        } else {
                            "Compatible with: ${boundary.compatibleBoundaries.joinToString(", ")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (boundary.compatibleBoundaries.isEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Compatibility",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoundaryCompatibilityDialog(
    boundary: com.rj.islamove.data.models.ZoneBoundary,
    allBoundaries: List<com.rj.islamove.data.models.ZoneBoundary>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    // Get other boundaries (excluding current one)
    val otherBoundaries = allBoundaries.filter { it.id != boundary.id && it.isActive }

    // State for checked boundaries
    val checkedBoundaries = remember {
        mutableStateMapOf<String, Boolean>().apply {
            otherBoundaries.forEach { otherBoundary ->
                put(otherBoundary.name, boundary.compatibleBoundaries.contains(otherBoundary.name))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configure Compatibility for ${boundary.name}")
        },
        text = {
            Column {
                Text(
                    text = "Select which boundaries are compatible with ${boundary.name} for ride pooling:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (otherBoundaries.isEmpty()) {
                    Text(
                        text = "No other boundaries available. Create more boundaries to configure compatibility.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(otherBoundaries) { otherBoundary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checkedBoundaries[otherBoundary.name] = !(checkedBoundaries[otherBoundary.name] ?: false)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checkedBoundaries[otherBoundary.name] ?: false,
                                    onCheckedChange = { isChecked ->
                                        checkedBoundaries[otherBoundary.name] = isChecked
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = otherBoundary.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedBoundaries = checkedBoundaries
                        .filter { it.value }
                        .keys
                        .toList()
                    onSave(selectedBoundaries)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
