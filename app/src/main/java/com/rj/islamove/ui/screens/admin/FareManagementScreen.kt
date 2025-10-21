package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.FareMatrixEntry
import com.rj.islamove.ui.theme.IslamovePrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FareMatrixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<FareMatrixEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = { },
            navigationIcon = {
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
                        text = "Fare Management",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage fare matrix entries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FloatingActionButton(
                    onClick = {
                        viewModel.showAddDialog()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Fare Entry")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Main Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FareMatrixInfoCard(entriesCount = uiState.fareEntries.size)
                }

                if (uiState.fareEntries.isEmpty()) {
                    item {
                        EmptyStateCard(onAddClick = { viewModel.showAddDialog() })
                    }
                }

                items(uiState.fareEntries) { entry ->
                    FareEntryCard(
                        entry = entry,
                        onEdit = { viewModel.editEntry(entry) },
                        onDelete = { showDeleteDialog = entry }
                    )
                }
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Add/Edit Fare dialog
    if (uiState.showAddDialog) {
        AddEditFareDialog(
            entry = uiState.editingEntry,
            availableLocations = uiState.availableLocations,
            onDismiss = { viewModel.hideAddDialog() },
            onSave = { fromLocation, toLocation, regularFare, discountFare ->
                if (uiState.editingEntry != null) {
                    viewModel.updateFareEntry(
                        uiState.editingEntry!!.id,
                        fromLocation,
                        toLocation,
                        regularFare,
                        discountFare
                    )
                } else {
                    viewModel.addFareEntry(fromLocation, toLocation, regularFare, discountFare)
                }
            }
        )
    }

    // Delete fare entry dialog
    showDeleteDialog?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Fare Entry") },
            text = { Text("Are you sure you want to delete the fare entry from ${entry.fromLocation} to ${entry.toLocation}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFareEntry(entry.id)
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

    // Error message dialog
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

@Composable
private fun FareMatrixInfoCard(entriesCount: Int) {
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
                        text = "San Jose Fare Matrix",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Fixed fares between specific locations",
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
                FareStatItem("Total Routes", "$entriesCount")
                FareStatItem("System", "Municipal")
                FareStatItem("Discount", "20%")
                FareStatItem("Coverage", "San Jose")
            }
        }
    }
}

@Composable
private fun FareStatItem(label: String, value: String) {
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
private fun FareEntryCard(
    entry: FareMatrixEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                        text = "${entry.fromLocation} → ${entry.toLocation}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "Regular: ₱${entry.regularFare.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Discount: ₱${entry.discountFare.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last updated: ${SimpleDateFormat("MMM dd, yyyy").format(Date(entry.lastUpdated))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = IslamovePrimary
                        )
                    }
                    IconButton(onClick = onDelete) {
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
                text = "No fare entries found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add fare entries to define fixed prices between locations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Fare Entry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditFareDialog(
    entry: FareMatrixEntry?,
    availableLocations: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double) -> Unit
) {
    var fromLocation by remember(entry) { mutableStateOf(entry?.fromLocation ?: "") }
    var toLocation by remember(entry) { mutableStateOf(entry?.toLocation ?: "") }
    var regularFare by remember(entry) { mutableStateOf(entry?.regularFare?.toString() ?: "") }
    var discountFare by remember(entry) {
        mutableStateOf(
            entry?.discountFare?.toString() ?:
            (entry?.regularFare?.let { (it * 0.8).toString() } ?: "")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (entry != null) "Edit Fare Entry" else "Add Fare Entry")
        },
        text = {
            Column {
                // From Location
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { }
                ) {
                    OutlinedTextField(
                        value = fromLocation,
                        onValueChange = { fromLocation = it },
                        label = { Text("From Location") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // To Location
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { }
                ) {
                    OutlinedTextField(
                        value = toLocation,
                        onValueChange = { toLocation = it },
                        label = { Text("To Location") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = discountFare,
                    onValueChange = { discountFare = it },
                    label = { Text("Discount Fare (₱) - Auto 20%") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Available locations: ${availableLocations.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regular = regularFare.toDoubleOrNull()
                    val discount = discountFare.toDoubleOrNull()
                    if (fromLocation.isNotBlank() && toLocation.isNotBlank() && regular != null && discount != null) {
                        onSave(fromLocation, toLocation, regular, discount)
                    }
                },
                enabled = fromLocation.isNotBlank() && toLocation.isNotBlank() &&
                         regularFare.toDoubleOrNull() != null && discountFare.toDoubleOrNull() != null
            ) {
                Text(if (entry != null) "Update" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}