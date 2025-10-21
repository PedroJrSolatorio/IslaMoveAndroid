package com.rj.islamove.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Data models for Boundary Fare Management system
 * Manages fare rules based on passenger's boundary location and destination
 */

data class BoundaryFareRule(
    val id: String = "",
    val fromBoundary: String = "", // AURELIO, DON RUBEN, MAHAYAHAY, etc.
    val toLocation: String = "", // Municipal Hall, specific destinations
    val fare: Double = 0.0, // Single fare amount (no regular/discount distinction)
    @PropertyName("active")
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BoundaryFareBatch(
    val id: String = "",
    val name: String = "", // e.g., "San Jose Municipal Hall Boundary Fares"
    val description: String = "",
    val rules: List<BoundaryFareRule> = emptyList(),
    @PropertyName("active")
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BoundaryFareManagementUiState(
    val isLoading: Boolean = false,
    val fareBatches: List<BoundaryFareBatch> = emptyList(),
    val selectedBatch: BoundaryFareBatch? = null,
    val showBatchDialog: Boolean = false,
    val showRuleDialog: Boolean = false,
    val editingBatch: BoundaryFareBatch? = null,
    val editingRule: BoundaryFareRule? = null,
    val errorMessage: String? = null,
    val availableBoundaries: List<String> = emptyList(),
    val availableDestinations: List<String> = emptyList()
)