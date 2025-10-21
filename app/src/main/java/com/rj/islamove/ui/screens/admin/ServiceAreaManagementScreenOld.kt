package com.rj.islamove.ui.screens.admin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.data.models.ServiceDestination
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.theme.IslamovePrimary

@Composable
private fun ServiceAreaInfoCard(
    areaCount: Int,
    totalDestinations: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                Column {
                    Text(
                        text = "San Jose Service Areas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Nested area and destination management",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoStatItem("Areas", "$areaCount")
                InfoStatItem("Destinations", "$totalDestinations")
                InfoStatItem("System", "Municipal")
                InfoStatItem("Discount", "20%")
            }
        }
    }
}

@Composable
private fun InfoStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ServiceAreaCard(
    area: ServiceArea,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onEditArea: () -> Unit,
    onDeleteArea: () -> Unit,
    onShowMap: () -> Unit,
    onEditDestination: (ServiceDestination) -> Unit,
    onDeleteDestination: (ServiceDestination) -> Unit,
    onDrawBoundary: () -> Unit = {},
    onDeleteBoundary: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Area Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = area.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${area.destinations.size} destinations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onShowMap) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Show Map",
                            tint = IslamovePrimary
                        )
                    }
                    IconButton(onClick = onEditArea) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Area",
                            tint = IslamovePrimary
                        )
                    }
                    IconButton(onClick = onDeleteArea) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Area",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.clickable { onExpandClick() }
                    )
                }
            }

            // Expanded Content - Destinations List
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Destinations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Tap map to add destinations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Zone Boundary Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zone Boundary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDrawBoundary,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), // Orange
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                if (area.boundary != null) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = if (area.boundary != null) "Edit Boundary" else "Draw Boundary",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (area.boundary != null) "Edit" else "Draw", fontSize = 12.sp)
                        }

                        if (area.boundary != null) {
                            Button(
                                onClick = onDeleteBoundary,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Boundary",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Boundary Status
                if (area.boundary != null) {
                    Text(
                        text = "✓ Zone boundary defined (${area.boundary.points.size} points)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = "No zone boundary set - tap 'Draw' to define area coverage. Tap points to select them, then tap map to move.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (area.destinations.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "No destinations yet. Click the map icon and tap locations to add destinations.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        area.destinations.forEach { destination ->
                            DestinationItem(
                                destination = destination,
                                onEdit = { onEditDestination(destination) },
                                onDelete = { onDeleteDestination(destination) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationItem(
    destination: ServiceDestination,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Regular: ₱${destination.regularFare.toInt()} | Discount: ₱${destination.discountFare.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Lat: ${String.format("%.4f", destination.latitude)}, Lng: ${String.format("%.4f", destination.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = IslamovePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No service areas found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create service areas and add destinations with fares and locations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Service Area")
            }
        }
    }
}

@Composable
private fun AddEditAreaDialog(
    area: ServiceArea?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(area) { mutableStateOf(area?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (area != null) "Edit Service Area" else "Add Service Area")
        },
        text = {
            Column {
                Text("Enter the name for this service area:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Area Name (e.g., AURELIO)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Examples: AURELIO, DON RUBEN, MAHAYAHAY, MATINGBE, JACQUEZ, POBLACION, STA. CRUZ, CUARINTA, LUNA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(if (area != null) "Update" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
private fun InteractiveServiceAreaMapDialog(
    area: ServiceArea,
    onDismiss: () -> Unit,
    onAddDestination: (String, Double, Double, Double, Double, String) -> Unit,
    onUpdateDestination: (ServiceDestination, String, Double, Double, String) -> Unit,
    onDeleteDestination: (ServiceDestination) -> Unit
) {
    var showDestinationEditor by remember { mutableStateOf<Pair<ServiceDestination?, Point>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${area.name} - Tap to Add Destinations")
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "• Tap empty space on map to add new destination\n• Tap existing marker to edit or delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Convert destinations to custom landmarks for map display
                val landmarks = area.destinations.map { dest ->
                    com.rj.islamove.data.models.CustomLandmark(
                        id = dest.id,
                        name = dest.name,
                        latitude = dest.latitude,
                        longitude = dest.longitude
                    )
                }

                // Calculate center point - default to San Jose if no destinations
                val centerLat = if (landmarks.isNotEmpty()) {
                    landmarks.map { it.latitude }.average()
                } else {
                    10.0080 // San Jose, Dinagat Islands default
                }
                val centerLng = if (landmarks.isNotEmpty()) {
                    landmarks.map { it.longitude }.average()
                } else {
                    125.5718 // San Jose, Dinagat Islands default
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    MapboxRideView(
                        modifier = Modifier.fillMaxSize(),
                        initialLocation = Point.fromLngLat(centerLng, centerLat),
                        showUserLocation = false,
                        showRoute = false,
                        showOnlineDrivers = false,
                        customLandmarks = landmarks,
                        onMapClick = { point ->
                            // When user taps empty space, show destination editor for new destination
                            showDestinationEditor = null to point
                        },
                        onLandmarkMarkerClick = { landmark ->
                            // When user taps existing marker, show destination editor for that destination
                            val destination = area.destinations.find { it.id == landmark.id }
                            if (destination != null) {
                                val point = Point.fromLngLat(destination.longitude, destination.latitude)
                                showDestinationEditor = destination to point
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { }
    )

    // Destination Editor Dialog
    showDestinationEditor?.let { (destination, point) ->
        DestinationEditorDialog(
            destination = destination,
            point = point,
            onDismiss = { showDestinationEditor = null },
            onSave = { name, regularFare, discountFare, color ->
                if (destination != null) {
                    // Update existing destination
                    onUpdateDestination(destination, name, regularFare, discountFare, color)
                } else {
                    // Add new destination
                    onAddDestination(name, point.latitude(), point.longitude(), regularFare, discountFare, color)
                }
                showDestinationEditor = null
            },
            onDelete = if (destination != null) {
                { onDeleteDestination(destination); showDestinationEditor = null }
            } else null
        )
    }
}

@Composable
private fun DestinationEditorDialog(
    destination: ServiceDestination?,
    point: Point,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(destination) { mutableStateOf(destination?.name ?: "") }
    var regularFare by remember(destination) { mutableStateOf(destination?.regularFare?.toString() ?: "") }
    var discountFare by remember(destination) {
        mutableStateOf(
            destination?.discountFare?.toString() ?:
            (destination?.regularFare?.let { (it * 0.8).toString() } ?: "")
        )
    }
    var selectedColor by remember { mutableStateOf("red") }

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

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = regularFare,
                        onValueChange = {
                            regularFare = it
                            // Auto-calculate discount fare (20% discount)
                            val regularValue = it.toDoubleOrNull()
                            if (regularValue != null) {
                                discountFare = (regularValue * 0.8).toString()
                            }
                        },
                        label = { Text("Regular Fare (₱)") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = discountFare,
                        onValueChange = { discountFare = it },
                        label = { Text("Discount Fare (₱) - Auto 20%") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = false,
                        colors = TextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regular = regularFare.toDoubleOrNull()
                    val discount = discountFare.toDoubleOrNull()
                    if (name.isNotBlank() && regular != null && discount != null) {
                        onSave(name.trim(), regular, discount, selectedColor)
                    }
                },
                enabled = name.isNotBlank() &&
                         regularFare.toDoubleOrNull() != null &&
                         discountFare.toDoubleOrNull() != null
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

@Composable
private fun ServiceAreasListTab(
    serviceAreas: List<ServiceArea>,
    expandedAreas: Set<String>,
    onToggleArea: (String) -> Unit,
    onAddDestination: (String) -> Unit,
    onEditDestination: (String, ServiceDestination) -> Unit,
    onDeleteDestination: (String, String) -> Unit,
    onAddServiceArea: () -> Unit,
    onShowMap: () -> Unit,
    onEditArea: (ServiceArea) -> Unit,
    onDeleteArea: (ServiceArea) -> Unit,
    onDrawBoundary: (String) -> Unit = {},
    onDeleteBoundary: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Service Areas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FloatingActionButton(
                    onClick = onAddServiceArea,
                    modifier = Modifier.size(40.dp),
                    containerColor = IslamovePrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Service Area",
                        tint = Color.White
                    )
                }
            }
        }

        items(serviceAreas, key = { it.id }) { serviceArea ->
            ServiceAreaCard(
                area = serviceArea,
                isExpanded = expandedAreas.contains(serviceArea.id),
                onExpandClick = { onToggleArea(serviceArea.id) },
                onEditArea = { onEditArea(serviceArea) },
                onDeleteArea = { onDeleteArea(serviceArea) },
                onShowMap = onShowMap,
                onEditDestination = { destination -> onEditDestination(serviceArea.id, destination) },
                onDeleteDestination = { destination -> onDeleteDestination(serviceArea.id, destination.id) },
                onDrawBoundary = { onDrawBoundary(serviceArea.id) },
                onDeleteBoundary = { onDeleteBoundary(serviceArea.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceAreaMapTab(
    serviceAreas: List<ServiceArea>,
    uiState: com.rj.islamove.data.models.ServiceAreaUiState,
    viewModel: ServiceAreaManagementViewModel,
    onAddDestination: (String, String, Double, Double, Double, Double, String) -> Unit,
    onEditDestination: (String, ServiceDestination) -> Unit,
    onDeleteDestination: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDestinationEditor by remember { mutableStateOf<Pair<ServiceDestination?, Point>?>(null) }
    var selectedAreaForNewDestination by remember { mutableStateOf(serviceAreas.firstOrNull()?.id) }

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
            text = "Tap on the map to add destinations to service areas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Service Area Selector
        if (serviceAreas.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = serviceAreas.find { it.id == selectedAreaForNewDestination }?.name ?: "Select Service Area",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Add destinations to:") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    serviceAreas.forEach { area ->
                        DropdownMenuItem(
                            text = { Text(area.name) },
                            onClick = {
                                selectedAreaForNewDestination = area.id
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Boundary Drawing Controls (Unified)
        if (uiState.isDrawingBoundary) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)) // Orange
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Boundary for ${uiState.selectedArea?.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.boundaryPoints.size} points",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "🟠 Tap points to select, tap map to move them • Long press to deselect",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.stopDrawingBoundary() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White)
                        }


                        Button(
                            onClick = { viewModel.saveBoundary() },
                            enabled = uiState.boundaryPoints.size >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(if (uiState.draggingPointIndex != null) 1f else 1f)
                        ) {
                            Text("Save Boundary", color = Color.White)
                        }
                    }
                }
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
                showServiceAreaBoundaries = true,
                isDrawingBoundaryMode = uiState.isDrawingBoundary,
                boundaryDrawingPoints = uiState.boundaryPoints,
                                onMapClick = { point ->
                    if (selectedAreaForNewDestination != null && !uiState.isDrawingBoundary) {
                        showDestinationEditor = null to point
                    }
                },
                onBoundaryPointAdded = { lat, lng ->
                    if (uiState.isDrawingBoundary) {
                        if (uiState.draggingPointIndex != null) {
                            // Update the dragging point and stop dragging
                            viewModel.updateBoundaryPoint(uiState.draggingPointIndex, lat, lng)
                            viewModel.stopDraggingPoint()
                        } else {
                            // Add new point
                            viewModel.addBoundaryPoint(lat, lng)
                        }
                    }
                },
                onBoundaryPointDragStart = { index ->
                    if (uiState.isDrawingBoundary) {
                        viewModel.startDraggingPoint(index)
                    }
                },
                onBoundaryPointDrag = { index, lat, lng ->
                    if (uiState.isDrawingBoundary) {
                        viewModel.updateBoundaryPoint(index, lat, lng)
                    }
                },
                onBoundaryPointDragEnd = {
                    if (uiState.isDrawingBoundary) {
                        viewModel.stopDraggingPoint()
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
        if (selectedAreaForNewDestination != null || destination != null) {
            DestinationEditorDialog(
                destination = destination,
                point = point,
                onSave = { name, regularFare, discountFare, color ->
                    if (destination != null) {
                        // Update existing destination
                        val areaId = serviceAreas.find { area ->
                            area.destinations.any { it.id == destination.id }
                        }?.id

                        if (areaId != null) {
                            onEditDestination(
                                areaId,
                                destination.copy(
                                    name = name,
                                    regularFare = regularFare,
                                    discountFare = discountFare,
                                    markerColor = color,
                                    latitude = point.latitude(),
                                    longitude = point.longitude()
                                )
                            )
                        }
                    } else if (selectedAreaForNewDestination != null) {
                        // Add new destination
                        onAddDestination(
                            selectedAreaForNewDestination!!,
                            name,
                            point.latitude(),
                            point.longitude(),
                            regularFare,
                            discountFare,
                            color
                        )
                    }
                    showDestinationEditor = null
                    selectedAreaForNewDestination = null
                },
                onDelete = if (destination != null) {
                    {
                        val areaId = serviceAreas.find { area ->
                            area.destinations.any { it.id == destination.id }
                        }?.id

                        if (areaId != null) {
                            onDeleteDestination(areaId, destination.id)
                        }
                        showDestinationEditor = null
                    }
                } else null,
                onDismiss = {
                    showDestinationEditor = null
                    selectedAreaForNewDestination = null
                }
            )
        }
    }
}

// @Composable
// fun ServiceAreasViewerScreen(
//     onNavigateBack: () -> Unit = {},
//     viewModel: ServiceAreaManagementViewModel = hiltViewModel()
// ) {
   
// Removed duplicate function - implementation is in the main ServiceAreaManagementScreen.kt file
