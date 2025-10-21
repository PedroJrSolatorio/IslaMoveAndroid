package com.rj.islamove.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSelector(
    currentStyle: String = "Outdoors",
    onStyleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentStyle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Map Style") },
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = null)
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val styles = listOf(
                "Streets" to "Standard street map",
                "Outdoors" to "More landmarks and POIs",
                "Satellite" to "Satellite imagery",
                "Satellite Streets" to "Satellite with labels",
                "Light" to "Light theme with POIs",
                "Dark" to "Dark theme"
            )

            styles.forEach { (styleName, description) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(styleName)
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onStyleSelected(styleName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MapStyleRadioGroup(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val styles = listOf(
        "Streets" to "Standard street view",
        "Outdoors" to "Enhanced landmarks & POIs",
        "Satellite" to "Satellite imagery",
        "Light" to "Clean light theme"
    )

    Column(
        modifier = modifier.selectableGroup()
    ) {
        Text(
            "Map Style",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        styles.forEach { (styleName, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = (styleName == selectedStyle),
                        onClick = { onStyleSelected(styleName) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (styleName == selectedStyle),
                    onClick = null
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(
                        text = styleName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}