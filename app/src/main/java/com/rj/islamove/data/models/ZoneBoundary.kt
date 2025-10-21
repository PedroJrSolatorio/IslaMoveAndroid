package com.rj.islamove.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Data model for Zone Boundaries used in BoundaryFareUtils
 * These boundaries determine which zone a pickup location falls within
 */
data class ZoneBoundary(
    val id: String = "",
    val name: String = "", // e.g., "DON RUBEN", "MATINGBE", "SAN JOSE"
    val points: List<BoundaryPoint> = emptyList(), // Polygon vertices (closed polygon)
    val fillColor: String = "#FF9800", // Orange by default for zone boundaries
    val strokeColor: String = "#F57C00",
    val strokeWidth: Double = 2.0,
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var lastUpdated: Long = System.currentTimeMillis(),
    val boundaryFares: Map<String, Double> = emptyMap(), // Map of destination boundary name → fare (e.g., "DON RUBEN" → 50.0)
    val compatibleBoundaries: List<String> = emptyList() // List of boundary names that are compatible with this boundary for ride pooling
)

/**
 * UI State for Zone Boundary Management
 */
data class ZoneBoundaryUiState(
    val isLoading: Boolean = false,
    val zoneBoundaries: List<ZoneBoundary> = emptyList(),
    val selectedBoundary: ZoneBoundary? = null,
    val editingBoundary: ZoneBoundary? = null,
    val showBoundaryDialog: Boolean = false,
    val isDrawingBoundary: Boolean = false,
    val boundaryPoints: List<BoundaryPoint> = emptyList(),
    val draggingPointIndex: Int? = null, // Legacy field for backward compatibility
    val selectedPointIndex: Int? = null, // New field for select-then-move workflow
    val errorMessage: String? = null,
    val successMessage: String? = null
)