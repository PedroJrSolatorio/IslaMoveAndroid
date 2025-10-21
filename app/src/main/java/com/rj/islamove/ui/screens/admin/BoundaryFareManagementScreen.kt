package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.BoundaryFareBatch
import com.rj.islamove.data.models.BoundaryFareRule
import com.rj.islamove.ui.theme.IslamovePrimary

@Composable
fun BoundaryFareManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BoundaryFareManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteBatchDialog by remember { mutableStateOf<BoundaryFareBatch?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<Pair<BoundaryFareBatch, BoundaryFareRule>?>(null) }

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
                    text = "Boundary Fare Management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage fare rules for passenger boundaries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Refresh button
            IconButton(onClick = { viewModel.loadFareBatches() }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = IslamovePrimary
                )
            }
        }

        // Main Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fare Batches",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                FloatingActionButton(
                                    onClick = { viewModel.showAddBatchDialog() },
                                    containerColor = IslamovePrimary
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Batch")
                                }
                            }
                        }

                        items(uiState.fareBatches) { batch ->
                            FareBatchCard(
                                batch = batch,
                                onEdit = { viewModel.showEditBatchDialog(it) },
                                onDelete = { showDeleteBatchDialog = it },
                                onAddRule = { viewModel.showAddRuleDialog(it) },
                                onEditRule = { rule -> viewModel.showEditRuleDialog(batch, rule) },
                                onDeleteRule = { rule -> showDeleteRuleDialog = batch to rule }
                            )
                        }

                        if (uiState.fareBatches.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "No batches",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No fare batches found",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Click + to create your first fare batch",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Batch Confirmation Dialog
    showDeleteBatchDialog?.let { batch ->
        AlertDialog(
            onDismissRequest = { showDeleteBatchDialog = null },
            title = { Text("Delete Fare Batch") },
            text = { Text("Are you sure you want to delete the fare batch '${batch.name}'? This will also delete all fare rules in this batch.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBatch(batch)
                    showDeleteBatchDialog = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBatchDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Rule Confirmation Dialog
    showDeleteRuleDialog?.let { (batch, rule) ->
        AlertDialog(
            onDismissRequest = { showDeleteRuleDialog = null },
            title = { Text("Delete Fare Rule") },
            text = { Text("Are you sure you want to delete the fare rule '${rule.fromBoundary} → ${rule.toLocation}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(batch, rule)
                    showDeleteRuleDialog = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRuleDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Dialog
    if (uiState.showBatchDialog) {
        BatchDialog(
            batch = uiState.editingBatch,
            onDismiss = { viewModel.hideBatchDialog() },
            onSave = { name, description ->
                viewModel.saveBatch(name, description)
            }
        )
    }

    // Rule Dialog
    if (uiState.showRuleDialog) {
        RuleDialog(
            rule = uiState.editingRule,
            availableBoundaries = uiState.availableBoundaries,
            availableDestinations = uiState.availableDestinations,
            onDismiss = { viewModel.hideRuleDialog() },
            onSave = { fromBoundary, toLocation, fare ->
                viewModel.saveRule(fromBoundary, toLocation, fare)
            }
        )
    }
}

@Composable
fun FareBatchCard(
    batch: BoundaryFareBatch,
    onEdit: (BoundaryFareBatch) -> Unit,
    onDelete: (BoundaryFareBatch) -> Unit,
    onAddRule: (BoundaryFareBatch) -> Unit,
    onEditRule: (BoundaryFareRule) -> Unit,
    onDeleteRule: (BoundaryFareRule) -> Unit
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
                        text = batch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (batch.description.isNotEmpty()) {
                        Text(
                            text = batch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${batch.rules.size} fare rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = IslamovePrimary
                    )
                }

                Row {
                    IconButton(onClick = { onEdit(batch) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = IslamovePrimary)
                    }
                    IconButton(onClick = { onDelete(batch) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }

            if (batch.rules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Column {
                    Text(
                        text = "Fare Rules",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    batch.rules.forEach { rule ->
                        FareRuleRow(
                            rule = rule,
                            onEdit = { onEditRule(rule) },
                            onDelete = { onDeleteRule(rule) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onAddRule(batch) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Fare Rule")
            }
        }
    }
}

@Composable
fun FareRuleRow(
    rule: BoundaryFareRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${rule.fromBoundary} → ${rule.toLocation}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "₱${kotlin.math.floor(rule.fare).toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = IslamovePrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = IslamovePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BatchDialog(
    batch: BoundaryFareBatch?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(batch?.name ?: "") }
    var description by remember { mutableStateOf(batch?.description ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (batch == null) "Add Fare Batch" else "Edit Fare Batch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Batch Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name, description) },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun RuleDialog(
    rule: BoundaryFareRule?,
    availableBoundaries: List<String>,
    availableDestinations: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit
) {
    var fromBoundary by remember { mutableStateOf(rule?.fromBoundary ?: "") }
    var toLocation by remember { mutableStateOf(rule?.toLocation ?: "") }
    var fareText by remember { mutableStateOf(rule?.fare?.toString() ?: "") }
    var boundaryExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (rule == null) "Add Fare Rule" else "Edit Fare Rule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    OutlinedTextField(
                        value = fromBoundary,
                        onValueChange = { fromBoundary = it },
                        label = { Text("From Boundary") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select boundary")
                        }
                    )

                    DropdownMenu(
                        expanded = boundaryExpanded,
                        onDismissRequest = { boundaryExpanded = false }
                    ) {
                        availableBoundaries.forEach { boundary ->
                            DropdownMenuItem(
                                text = { Text(boundary) },
                                onClick = {
                                    fromBoundary = boundary
                                    boundaryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    OutlinedTextField(
                        value = toLocation,
                        onValueChange = { toLocation = it },
                        label = { Text("To Destination") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select destination")
                        }
                    )

                    DropdownMenu(
                        expanded = destinationExpanded,
                        onDismissRequest = { destinationExpanded = false }
                    ) {
                        availableDestinations.forEach { destination ->
                            DropdownMenuItem(
                                text = { Text(destination) },
                                onClick = {
                                    toLocation = destination
                                    destinationExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fareText,
                    onValueChange = { fareText = it },
                    label = { Text("Fare (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val fare = fareText.toDoubleOrNull() ?: 0.0
                            onSave(fromBoundary, toLocation, fare)
                        },
                        enabled = fromBoundary.isNotBlank() && toLocation.isNotBlank() && fareText.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}