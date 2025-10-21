package com.rj.islamove.ui.screens.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.ui.components.MapboxRideView
import com.rj.islamove.ui.theme.IslamovePrimary
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceAreaScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ServiceAreaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<Point?>(null) }
    var landmarkToEdit by remember { mutableStateOf<CustomLandmark?>(null) }

    val density = LocalDensity.current

    // Bottom sheet drag state
    val maxHeight = with(density) { 500.dp.toPx() }
    val minHeight = with(density) { 120.dp.toPx() }
    val defaultHeight = with(density) { 250.dp.toPx() }
    var bottomSheetOffset by remember { mutableStateOf(defaultHeight) }
    var targetOffset by remember { mutableStateOf(defaultHeight) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) bottomSheetOffset else targetOffset,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 300),
        label = "bottomSheetOffset"
    )

    val currentOffset = if (isDragging) bottomSheetOffset else animatedOffset

    LaunchedEffect(Unit) {
        viewModel.loadLandmarks()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

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

            Column {
                Text(
                    text = "Service Area & Landmarks",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap on the map or search boundaries to add landmarks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Boundary Search Button
            IconButton(
                onClick = { viewModel.showBoundarySearch() }
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search Boundaries",
                    tint = IslamovePrimary
                )
            }
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Full-screen map with draggable bottom sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Map background
                MapboxRideView(
                    modifier = Modifier.fillMaxSize(),
                    initialLocation = Point.fromLngLat(121.0748, 15.7886), // San Jose, Nueva Ecija
                    showUserLocation = false,
                    showRoute = false,
                    showOnlineDrivers = false,
                    customLandmarks = uiState.landmarks,
                    onMapClick = { point ->
                        selectedLocation = point
                        showNameDialog = true
                    },
                    onLandmarkMarkerClick = { landmark ->
                        landmarkToEdit = landmark
                        showEditDialog = true
                    }
                )


                // Draggable Bottom Sheet
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(with(density) { currentOffset.toDp() })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                },
                                onDrag = { _, dragAmount ->
                                    val newOffset = bottomSheetOffset - dragAmount.y
                                    bottomSheetOffset = max(minHeight, min(maxHeight, newOffset))
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val snapTarget = when {
                                        bottomSheetOffset < (minHeight + defaultHeight) / 2 -> minHeight
                                        bottomSheetOffset < (defaultHeight + maxHeight) / 2 -> defaultHeight
                                        else -> maxHeight
                                    }
                                    targetOffset = snapTarget
                                }
                            )
                        },
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LandmarksBottomSheetContent(
                        landmarks = uiState.landmarks,
                        onDeleteLandmark = { viewModel.deleteLandmark(it.id) },
                        onClearAll = { viewModel.clearAllLandmarks() }
                    )
                }
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Destination Dialog
    if (showNameDialog && selectedLocation != null) {
        DestinationDialog(
            onDismiss = {
                showNameDialog = false
                selectedLocation = null
            },
            onConfirm = { name ->
                selectedLocation?.let { location ->
                    viewModel.addLandmark(location, name)
                }
                showNameDialog = false
                selectedLocation = null
            }
        )
    }

    // Edit Dialog
    if (showEditDialog && landmarkToEdit != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) { },
            contentAlignment = Alignment.Center
        ) {
            LandmarkEditDialog(
                landmark = landmarkToEdit!!,
                onDismiss = {
                    showEditDialog = false
                    landmarkToEdit = null
                },
                onDelete = { landmark ->
                    viewModel.deleteLandmark(landmark.id)
                    showEditDialog = false
                    landmarkToEdit = null
                },
                onEdit = { landmark, newName ->
                    viewModel.editLandmark(landmark.id, newName)
                    showEditDialog = false
                    landmarkToEdit = null
                }
            )
        }
    }

    // Boundary Search Dialog
    if (uiState.showBoundarySearch) {
        BoundarySearchDialog(
            searchQuery = uiState.boundarySearchQuery,
            searchResults = uiState.boundarySearchResults,
            isSearching = uiState.isBoundarySearching,
            onSearchQueryChanged = { viewModel.updateBoundarySearchQuery(it) },
            onResultSelected = { viewModel.selectBoundaryResult(it) },
            onDismiss = { viewModel.hideBoundarySearch() }
        )
    }
}

@Composable
private fun LandmarkItem(
    landmark: CustomLandmark,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = IslamovePrimary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = landmark.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Lat: ${String.format("%.4f", landmark.latitude)}, " +
                          "Lng: ${String.format("%.4f", landmark.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun DestinationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Destination")
        },
        text = {
            Column {
                Text("Enter destination details:")
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Destination Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
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
private fun LandmarkEditDialog(
    landmark: CustomLandmark,
    onDismiss: () -> Unit,
    onDelete: (CustomLandmark) -> Unit,
    onEdit: (CustomLandmark, String) -> Unit
) {
    var name by remember { mutableStateOf(landmark.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Landmark",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Landmark Info Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Landmark",
                    tint = IslamovePrimary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Current Name",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        text = landmark.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = "Coordinates",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Coordinates",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Lat: ${String.format("%.4f", landmark.latitude)}, Lng: ${String.format("%.4f", landmark.longitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Edit Name Field
            Text(
                text = "Edit Name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Landmark Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IslamovePrimary,
                    focusedLabelColor = IslamovePrimary
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button
                Button(
                    onClick = { onDelete(landmark) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F) // Red color for deletion
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                // Save button
                Button(
                    onClick = { if (name.isNotBlank()) onEdit(landmark, name.trim()) },
                    enabled = name.isNotBlank() && name.trim() != landmark.name,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IslamovePrimary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LandmarksBottomSheetContent(
    landmarks: List<CustomLandmark>,
    onDeleteLandmark: (CustomLandmark) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(
                    Color.Gray.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Manage Landmarks",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Clear all button
            if (landmarks.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Landmarks count
        Text(
            text = "${landmarks.size} custom landmarks",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (landmarks.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No landmarks yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
                Text(
                    text = "Tap anywhere on the map to create a landmark",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        } else {
            // Landmarks list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(landmarks) { landmark ->
                    LandmarkItem(
                        landmark = landmark,
                        onDelete = { onDeleteLandmark(landmark) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoundarySearchDialog(
    searchQuery: String,
    searchResults: List<BoundarySearchResult>,
    isSearching: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onResultSelected: (BoundarySearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Administrative Boundaries",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = IslamovePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Free Tier - Limited Boundary Data",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = IslamovePrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search cities, provinces, and regions in the Philippines. For detailed boundary polygons, upgrade to a paid Mapbox plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    label = { Text("Search places (e.g., San Jose, Nueva Ecija)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IslamovePrimary,
                        focusedLabelColor = IslamovePrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Results
                if (searchResults.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { result ->
                            BoundaryResultItem(
                                result = result,
                                onClick = { onResultSelected(result) }
                            )
                        }
                    }
                } else if (searchQuery.isNotEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No boundaries found",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                            )
                            Text(
                                text = "Try searching for cities or provinces",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoundaryResultItem(
    result: BoundarySearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = IslamovePrimary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${result.adminLevel} • ${result.country}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Center: ${String.format("%.4f", result.centerLat)}, ${String.format("%.4f", result.centerLng)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Icon(
                Icons.Default.Add,
                contentDescription = "Add Boundary",
                tint = IslamovePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class ServiceAreaUiState(
    val isLoading: Boolean = false,
    val landmarks: List<CustomLandmark> = emptyList(),
    val errorMessage: String? = null,
    val isAddingMode: Boolean = false,
    val showBoundarySearch: Boolean = false,
    val boundarySearchQuery: String = "",
    val boundarySearchResults: List<BoundarySearchResult> = emptyList(),
    val isBoundarySearching: Boolean = false
)

data class BoundarySearchResult(
    val id: String,
    val name: String,
    val adminLevel: String,
    val country: String,
    val centerLat: Double,
    val centerLng: Double,
    val bbox: List<Double>? = null
)