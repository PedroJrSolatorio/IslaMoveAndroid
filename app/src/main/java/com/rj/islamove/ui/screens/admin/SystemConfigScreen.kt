package com.rj.islamove.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rj.islamove.ui.theme.IslamovePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToServiceAreaManagement: () -> Unit = {}
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

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
                    text = "System Configuration",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage platform settings and parameters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Configuration sections
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configuration Sections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(getConfigSections()) { section ->
                ConfigSectionCard(
                    section = section,
                    onClick = {
                        when (section.id) {
                            "service_area_management" -> onNavigateToServiceAreaManagement()
                        }
                    }
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigSectionCard(
    section: ConfigSection,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = section.color,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (section.status.isNotEmpty()) {
                    Text(
                        text = section.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getConfigSections(): List<ConfigSection> {
    return listOf(
        ConfigSection(
            id = "service_area_management",
            title = "Service Area & Fare Management",
            description = "Manage areas, destinations, fares, and service boundaries",
            icon = Icons.Default.LocationOn,
            color = Color(0xFF4CAF50),
            status = "San Jose, Dinagat Islands"
        )
    )
}

data class ConfigSection(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val status: String
)

data class SystemConfig(
    val maintenanceMode: Boolean = false,
    val allowNewRegistrations: Boolean = true,
    val enableNotifications: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

