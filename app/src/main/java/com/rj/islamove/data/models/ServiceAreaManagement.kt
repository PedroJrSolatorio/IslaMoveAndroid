package com.rj.islamove.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Data models for the combined Service Area Management feature
 * Combines fare management with service area mapping
 */

data class ServiceArea(
    val id: String = "",
    val name: String = "", // AURELIO, DON RUBEN, MAHAYAHAY, etc.
    val destinations: List<ServiceDestination> = emptyList(),
    val boundary: ServiceAreaBoundary? = null, // Polygon boundary for zone highlighting
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class ServiceDestination(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val regularFare: Double = 0.0,
    val discountFare: Double = 0.0,
    val markerColor: String = "red",
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class ServiceAreaBoundary(
    val points: List<BoundaryPoint> = emptyList(), // Polygon vertices
    val fillColor: String = "#4CAF5080", // Semi-transparent green by default
    val strokeColor: String = "#4CAF50", // Green stroke
    val strokeWidth: Double = 3.0
)


data class ServiceAreaUiState(
    val isLoading: Boolean = false,
    val serviceAreas: List<ServiceArea> = emptyList(),
    val selectedArea: ServiceArea? = null,
    val showAreaDialog: Boolean = false,
    val showDestinationDialog: Boolean = false,
    val showMap: Boolean = false,
    val editingArea: ServiceArea? = null,
    val editingDestination: ServiceDestination? = null,
    val errorMessage: String? = null,
    val isDrawingBoundary: Boolean = false,
    val boundaryPoints: List<BoundaryPoint> = emptyList(),
    val draggingPointIndex: Int? = null, // Legacy field for backward compatibility
    val selectedPointIndex: Int? = null, // New field for select-then-move workflow
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