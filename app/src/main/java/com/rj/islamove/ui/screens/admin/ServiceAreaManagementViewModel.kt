package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.data.models.ServiceAreaUiState
import com.rj.islamove.data.models.ServiceDestination
import com.rj.islamove.data.models.ServiceAreaBoundary
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.data.models.BoundarySearchResult
import com.rj.islamove.data.repository.ServiceAreaManagementRepository
import com.rj.islamove.data.repository.BoundaryFareManagementRepository
import com.rj.islamove.data.api.MapboxBoundariesService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceAreaManagementViewModel @Inject constructor(
    private val repository: ServiceAreaManagementRepository,
    private val boundariesService: MapboxBoundariesService,
    private val boundaryFareRepository: BoundaryFareManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceAreaUiState())
    val uiState: StateFlow<ServiceAreaUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ServiceAreaManagementVM"
    }

    init {
        loadServiceAreas()
    }

    fun loadServiceAreas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.getAllServiceAreas()
                    .onSuccess { areas ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            serviceAreas = areas,
                            errorMessage = null
                        )
                        Log.d(TAG, "Loaded ${areas.size} service areas")

                        // If no service areas exist, create a default one
                        if (areas.isEmpty()) {
                            Log.d(TAG, "No service areas found, creating default area")
                            createDefaultServiceArea()
                        }
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error loading service areas", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load service areas: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadServiceAreas", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading service areas: ${e.message}"
                )
            }
        }
    }

    private fun createDefaultServiceArea() {
        viewModelScope.launch {
            try {
                val defaultArea = ServiceArea(name = "Default Area")
                repository.addServiceArea(defaultArea)
                    .onSuccess {
                        Log.d(TAG, "Created default service area")
                        // Reload the service areas to include the new default area
                        loadServiceAreas()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to create default service area", exception)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating default service area", e)
            }
        }
    }

    fun showAddAreaDialog() {
        _uiState.value = _uiState.value.copy(showAreaDialog = true, editingArea = null)
    }

    fun showEditAreaDialog(area: ServiceArea) {
        _uiState.value = _uiState.value.copy(showAreaDialog = true, editingArea = area)
    }

    fun hideAreaDialog() {
        _uiState.value = _uiState.value.copy(showAreaDialog = false, editingArea = null)
    }

    fun showAddDestinationDialog(area: ServiceArea) {
        _uiState.value = _uiState.value.copy(
            showDestinationDialog = true,
            selectedArea = area,
            editingDestination = null
        )
    }

    fun showEditDestinationDialog(area: ServiceArea, destination: ServiceDestination) {
        _uiState.value = _uiState.value.copy(
            showDestinationDialog = true,
            selectedArea = area,
            editingDestination = destination
        )
    }

    fun hideDestinationDialog() {
        _uiState.value = _uiState.value.copy(
            showDestinationDialog = false,
            selectedArea = null,
            editingDestination = null
        )
    }

    fun showMapForArea(area: ServiceArea) {
        _uiState.value = _uiState.value.copy(
            showMap = true,
            selectedArea = area
        )
    }

    fun hideMap() {
        _uiState.value = _uiState.value.copy(
            showMap = false,
            selectedArea = null
        )
    }

    fun addServiceArea(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val newArea = ServiceArea(name = name.trim())
                repository.addServiceArea(newArea)
                    .onSuccess {
                        loadServiceAreas()
                        hideAreaDialog()
                        Log.d(TAG, "Added new service area: $name")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to add service area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to add service area: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding service area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error adding service area: ${e.message}"
                )
            }
        }
    }

    fun updateServiceArea(areaId: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val existingArea = _uiState.value.serviceAreas.find { it.id == areaId }
                    ?: return@launch

                val updatedArea = existingArea.copy(name = name.trim())
                repository.updateServiceArea(updatedArea)
                    .onSuccess {
                        loadServiceAreas()
                        hideAreaDialog()
                        Log.d(TAG, "Updated service area: $name")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to update service area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update service area: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating service area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating service area: ${e.message}"
                )
            }
        }
    }

    fun deleteServiceArea(areaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                Log.d(TAG, "Deleting service area: $areaId")
                repository.deleteServiceArea(areaId)
                    .onSuccess {
                        Log.d(TAG, "Successfully deleted service area: $areaId")
                        // Remove from local state immediately
                        _uiState.value = _uiState.value.copy(
                            serviceAreas = _uiState.value.serviceAreas.filter { it.id != areaId },
                            isLoading = false
                        )
                        // Reload from server to verify
                        loadServiceAreas()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to delete service area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete service area: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting service area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting service area: ${e.message}"
                )
            }
        }
    }

    fun addDestination(
        areaId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        regularFare: Double,
        discountFare: Double,
        markerColor: String = "red"
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val newDestination = ServiceDestination(
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    regularFare = regularFare,
                    discountFare = discountFare,
                    markerColor = markerColor
                )

                repository.addDestinationToArea(areaId, newDestination)
                    .onSuccess {
                        loadServiceAreas()
                        hideDestinationDialog()
                        Log.d(TAG, "Added destination $name to area $areaId")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to add destination", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to add destination: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding destination", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error adding destination: ${e.message}"
                )
            }
        }
    }

    fun updateDestination(
        areaId: String,
        destinationId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        regularFare: Double,
        discountFare: Double
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val updatedDestination = ServiceDestination(
                    id = destinationId,
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    regularFare = regularFare,
                    discountFare = discountFare
                )

                repository.updateDestinationInArea(areaId, updatedDestination)
                    .onSuccess {
                        loadServiceAreas()
                        hideDestinationDialog()
                        Log.d(TAG, "Updated destination $name in area $areaId")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to update destination", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update destination: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating destination", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating destination: ${e.message}"
                )
            }
        }
    }

    fun deleteDestination(areaId: String, destinationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                repository.removeDestinationFromArea(areaId, destinationId)
                    .onSuccess {
                        loadServiceAreas()
                        Log.d(TAG, "Deleted destination $destinationId from area $areaId")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to delete destination", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete destination: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting destination", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting destination: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // Zone Boundary Management Functions
    fun startDrawingBoundary(areaId: String) {
        viewModelScope.launch {
            try {
                // Determine the appropriate area name based on context
                val boundaryAreaName = when (areaId) {
                    "service_area" -> {
                        // Create a unique operational service area name
                        val existingServiceAreas = _uiState.value.serviceAreas.filter {
                            !it.name.contains("Zone Boundary", ignoreCase = true)
                        }
                        val newAreaNumber = existingServiceAreas.size + 1
                        "Service Area $newAreaNumber"
                    }
                    else -> {
                        // Default zone boundary area for fare calculations
                        "Zone Boundary Area"
                    }
                }

                var targetArea = _uiState.value.serviceAreas.find { it.name == boundaryAreaName }

                if (targetArea == null) {
                    // Create the appropriate area if it doesn't exist
                    val newArea = ServiceArea(name = boundaryAreaName)

                    repository.addServiceArea(newArea)
                        .onSuccess {
                            Log.d(TAG, "Created area: $boundaryAreaName")
                            // Reload service areas to get the area with proper ID
                            repository.getAllServiceAreas()
                                .onSuccess { areas ->
                                    _uiState.value = _uiState.value.copy(
                                        serviceAreas = areas
                                    )

                                    // Find the newly created area with proper ID
                                    targetArea = areas.find { it.name == boundaryAreaName }

                                    if (targetArea != null) {
                                        // Start drawing with the area that has proper ID
                                        _uiState.value = _uiState.value.copy(
                                            isDrawingBoundary = true,
                                            selectedArea = targetArea,
                                            boundaryPoints = emptyList()
                                        )
                                        Log.d(TAG, "Started boundary drawing for area: ${targetArea!!.name} (ID: ${targetArea!!.id})")
                                    } else {
                                        Log.e(TAG, "Could not find newly created area")
                                        _uiState.value = _uiState.value.copy(
                                            errorMessage = "Failed to initialize drawing area"
                                        )
                                    }
                                }
                                .onFailure { exception ->
                                    Log.e(TAG, "Failed to reload service areas", exception)
                                    _uiState.value = _uiState.value.copy(
                                        errorMessage = "Failed to reload areas: ${exception.message}"
                                    )
                                }
                        }
                        .onFailure { exception ->
                            Log.e(TAG, "Failed to create area", exception)
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Failed to create area: ${exception.message}"
                            )
                        }
                } else {
                    // Area already exists, start drawing
                    _uiState.value = _uiState.value.copy(
                        isDrawingBoundary = true,
                        selectedArea = targetArea,
                        boundaryPoints = emptyList() // Start fresh
                    )
                    Log.d(TAG, "Started boundary drawing for existing area: ${targetArea.name} (ID: ${targetArea.id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in startDrawingBoundary", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error starting boundary drawing: ${e.message}"
                )
            }
        }
    }

    fun stopDrawingBoundary() {
        _uiState.value = _uiState.value.copy(
            isDrawingBoundary = false,
            boundaryPoints = emptyList()
        )
    }

    fun addBoundaryPoint(latitude: Double, longitude: Double) {
        val newPoint = BoundaryPoint(latitude, longitude)
        val currentPoints = _uiState.value.boundaryPoints

        val updatedPoints = if (currentPoints.isEmpty()) {
            // First point, just add it
            listOf(newPoint)
        } else if (currentPoints.size == 1) {
            // Second point, add it to create a line
            currentPoints + newPoint
        } else {
            // For 3 or more points, use intelligent ordering to create proper polygon
            createOptimalPolygonPoints(currentPoints, newPoint)
        }

        _uiState.value = _uiState.value.copy(boundaryPoints = updatedPoints)
        Log.d(TAG, "Added boundary point at ($latitude, $longitude). Total points: ${updatedPoints.size}")
    }

    /**
     * Create optimal polygon points by finding the best insertion point
     */
    private fun createOptimalPolygonPoints(existingPoints: List<BoundaryPoint>, newPoint: BoundaryPoint): List<BoundaryPoint> {
        // For polygons, we want to maintain a logical order around the perimeter
        // Find the two adjacent points where inserting the new point creates the smallest perimeter increase

        var bestInsertionIndex = 0
        var minPerimeterIncrease = Double.MAX_VALUE

        for (i in 0..existingPoints.size) {
            val testPoints = existingPoints.toMutableList()
            testPoints.add(i, newPoint)

            // Calculate perimeter increase
            val perimeterIncrease = calculatePerimeterIncrease(existingPoints, testPoints, i)

            if (perimeterIncrease < minPerimeterIncrease) {
                minPerimeterIncrease = perimeterIncrease
                bestInsertionIndex = i
            }
        }

        val result = existingPoints.toMutableList()
        result.add(bestInsertionIndex, newPoint)
        return result
    }

    /**
     * Calculate the increase in perimeter when inserting a point
     */
    private fun calculatePerimeterIncrease(originalPoints: List<BoundaryPoint>, newPoints: List<BoundaryPoint>, insertionIndex: Int): Double {
        if (originalPoints.size < 2) return 0.0

        val newPoint = newPoints[insertionIndex]
        var increase = 0.0

        // Remove distance between adjacent original points
        if (originalPoints.size > 1) {
            val prevIndex = if (insertionIndex > 0) insertionIndex - 1 else originalPoints.size - 1
            val nextIndex = if (insertionIndex < originalPoints.size) insertionIndex else 0

            if (prevIndex != nextIndex) {
                val originalDistance = calculateDistanceBetweenPoints(originalPoints[prevIndex], originalPoints[nextIndex])
                val newDistance1 = calculateDistanceBetweenPoints(originalPoints[prevIndex], newPoint)
                val newDistance2 = calculateDistanceBetweenPoints(newPoint, originalPoints[nextIndex])
                increase = (newDistance1 + newDistance2) - originalDistance
            }
        }

        return increase
    }

    /**
     * Find the nearest boundary point to a given new point
     */
    private fun findNearestBoundaryPoint(newPoint: BoundaryPoint, existingPoints: List<BoundaryPoint>): BoundaryPoint {
        if (existingPoints.isEmpty()) {
            throw IllegalArgumentException("Existing points list cannot be empty")
        }

        var nearestPoint = existingPoints[0]
        var minDistance = calculateDistanceBetweenPoints(newPoint, nearestPoint)

        for (i in 1 until existingPoints.size) {
            val currentPoint = existingPoints[i]
            val distance = calculateDistanceBetweenPoints(newPoint, currentPoint)

            if (distance < minDistance) {
                minDistance = distance
                nearestPoint = currentPoint
            }
        }

        return nearestPoint
    }

    /**
     * Calculate distance between two boundary points using Haversine formula
     */
    private fun calculateDistanceBetweenPoints(point1: BoundaryPoint, point2: BoundaryPoint): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    fun removeBoundaryPoint(index: Int) {
        val updatedPoints = _uiState.value.boundaryPoints.toMutableList()
        if (index in updatedPoints.indices) {
            updatedPoints.removeAt(index)
            _uiState.value = _uiState.value.copy(boundaryPoints = updatedPoints)
            Log.d(TAG, "Removed boundary point at index: $index, remaining points: ${updatedPoints.size}")
        }
    }

    fun removeBoundaryPointAtCoordinates(latitude: Double, longitude: Double): Boolean {
        // Find and remove the boundary point closest to the given coordinates
        if (_uiState.value.boundaryPoints.isEmpty()) {
            return false
        }

        val touchPoint = BoundaryPoint(latitude, longitude)
        var nearestIndex = -1
        var minDistance = Double.MAX_VALUE

        _uiState.value.boundaryPoints.forEachIndexed { index, point ->
            val deltaLat = latitude - point.latitude
            val deltaLon = longitude - point.longitude
            val distance = kotlin.math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon)

            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = index
            }
        }

        // Use a threshold for deletion (approximately 50 meters in degrees)
        val threshold = 0.0005
        if (nearestIndex >= 0 && minDistance < threshold) {
            removeBoundaryPoint(nearestIndex)
            return true
        }

        return false
    }

    fun saveBoundary() {
        val selectedArea = _uiState.value.selectedArea ?: return
        val boundaryPoints = _uiState.value.boundaryPoints

        if (boundaryPoints.size < 3) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "A zone boundary needs at least 3 points"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Preserve existing boundary properties or use defaults
                val existingBoundary = selectedArea.boundary
                val boundary = ServiceAreaBoundary(
                    points = boundaryPoints,
                    fillColor = existingBoundary?.fillColor ?: "#4CAF5080",
                    strokeColor = existingBoundary?.strokeColor ?: "#4CAF50",
                    strokeWidth = existingBoundary?.strokeWidth ?: 3.0
                )

                val updatedArea = selectedArea.copy(boundary = boundary)
                repository.updateServiceArea(updatedArea)
                    .onSuccess {
                        loadServiceAreas()
                        stopDrawingBoundary()
                        Log.d(TAG, "Saved boundary for area: ${selectedArea.name}")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to save boundary", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to save boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving boundary", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error saving boundary: ${e.message}"
                )
            }
        }
    }

    fun deleteBoundary(areaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val area = _uiState.value.serviceAreas.find { it.id == areaId }
                if (area == null) {
                    Log.e(TAG, "Cannot find boundary area with ID: $areaId")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Boundary not found"
                    )
                    return@launch
                }

                Log.d(TAG, "Attempting to delete boundary area: ${area.name} (ID: $areaId)")

                // Delete the service area document from Firebase
                repository.deleteServiceArea(areaId)
                    .onSuccess {
                        Log.d(TAG, "Successfully deleted boundary area: ${area.name} (ID: $areaId)")
                        // Remove from local state immediately
                        _uiState.value = _uiState.value.copy(
                            serviceAreas = _uiState.value.serviceAreas.filter { it.id != areaId },
                            isLoading = false
                        )
                        // Reload from server to verify
                        loadServiceAreas()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to delete boundary area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting boundary area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting boundary: ${e.message}"
                )
            }
        }
    }

    // Unified boundary drawing/editing functions
    fun updateBoundaryPoint(index: Int, latitude: Double, longitude: Double) {
        val updatedPoints = _uiState.value.boundaryPoints.toMutableList()
        if (index in updatedPoints.indices) {
            val oldPoint = updatedPoints[index]
            updatedPoints[index] = BoundaryPoint(latitude, longitude)
            _uiState.value = _uiState.value.copy(boundaryPoints = updatedPoints)
            Log.d(TAG, "Updated boundary point $index from (${oldPoint.latitude}, ${oldPoint.longitude}) to ($latitude, $longitude)")
        } else {
            Log.w(TAG, "Cannot update boundary point: index $index out of bounds (${updatedPoints.size} points)")
        }
    }

    fun startDraggingPoint(index: Int) {
        // Only allow dragging if we're in boundary drawing mode and have points
        val currentState = _uiState.value
        Log.d(TAG, "startDraggingPoint called with index $index, isDrawingBoundary=${currentState.isDrawingBoundary}, points count=${currentState.boundaryPoints.size}")

        if (currentState.isDrawingBoundary && currentState.boundaryPoints.isNotEmpty() && index in currentState.boundaryPoints.indices) {
            val point = currentState.boundaryPoints[index]
            _uiState.value = currentState.copy(draggingPointIndex = index)
            Log.d(TAG, "Started dragging point at index: $index, coordinates: (${point.latitude}, ${point.longitude})")
        } else {
            Log.w(TAG, "Rejected drag start - isDrawingBoundary=${currentState.isDrawingBoundary}, points=${currentState.boundaryPoints.size}, index=$index")
        }
    }

    fun startDraggingPointAtCoordinates(latitude: Double, longitude: Double): Boolean {
        // Find the nearest boundary point to the given coordinates
        if (!_uiState.value.isDrawingBoundary || _uiState.value.boundaryPoints.isEmpty()) {
            return false
        }

        val touchPoint = BoundaryPoint(latitude, longitude)
        var nearestIndex = -1
        var minDistance = Double.MAX_VALUE

        _uiState.value.boundaryPoints.forEachIndexed { index, point ->
            // Calculate distance using Haversine formula (approximation for short distances)
            val deltaLat = latitude - point.latitude
            val deltaLon = longitude - point.longitude
            val distance = kotlin.math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon)

            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = index
            }
        }

        // Use a threshold for touch detection (approximately 50 meters in degrees)
        val threshold = 0.0005 // roughly 50 meters
        if (nearestIndex >= 0 && minDistance < threshold) {
            _uiState.value = _uiState.value.copy(draggingPointIndex = nearestIndex)
            return true
        }

        return false
    }

    fun stopDraggingPoint() {
        val currentDragIndex = _uiState.value.draggingPointIndex
        _uiState.value = _uiState.value.copy(draggingPointIndex = null)
        Log.d(TAG, "Stopped dragging point (was index: $currentDragIndex)")
    }

    /**
     * Select a boundary point for moving
     */
    fun selectBoundaryPoint(index: Int) {
        _uiState.value = _uiState.value.copy(selectedPointIndex = index)
        Log.d(TAG, "Selected boundary point $index for moving")
    }

    /**
     * Deselect the currently selected boundary point
     */
    fun deselectBoundaryPoint() {
        _uiState.value = _uiState.value.copy(selectedPointIndex = null)
        Log.d(TAG, "Deselected boundary point")
    }

    /**
     * Clear all boundary points
     */
    fun clearBoundaryPoints() {
        _uiState.value = _uiState.value.copy(
            boundaryPoints = emptyList(),
            selectedPointIndex = null,
            draggingPointIndex = null
        )
        Log.d(TAG, "Cleared all boundary points")
    }

    fun startEditingBoundary(area: ServiceArea) {
        val boundaryPoints = area.boundary?.points ?: emptyList()
        _uiState.value = _uiState.value.copy(
            isDrawingBoundary = true,
            selectedArea = area,
            boundaryPoints = boundaryPoints
        )
        Log.d(TAG, "Started editing boundary for area: ${area.name} with ${boundaryPoints.size} points")
    }

    /**
     * Update the name of a service area (used for renaming boundaries)
     */
    fun renameBoundary(areaId: String, newName: String) {
        Log.d(TAG, "renameBoundary called with areaId: $areaId, newName: '$newName'")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val existingArea = _uiState.value.serviceAreas.find { it.id == areaId }
                if (existingArea == null) {
                    Log.e(TAG, "Area not found with id: $areaId")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Area not found"
                    )
                    return@launch
                }

                Log.d(TAG, "Found area: '${existingArea.name}' (id: ${existingArea.id})")

                val updatedArea = existingArea.copy(name = newName.trim())
                Log.d(TAG, "Updating area to new name: '${updatedArea.name}'")

                repository.updateServiceArea(updatedArea)
                    .onSuccess {
                        Log.d(TAG, "Successfully renamed boundary area to: $newName")
                        loadServiceAreas()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to rename boundary area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to rename boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming boundary area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error renaming boundary: ${e.message}"
                )
            }
        }
    }

    // Mapbox Boundaries Explorer v4 Integration Methods

    fun showBoundarySearch() {
        Log.d(TAG, "showBoundarySearch() called")
        _uiState.value = _uiState.value.copy(showBoundarySearch = true)
        Log.d(TAG, "showBoundarySearch state updated: ${_uiState.value.showBoundarySearch}")
    }

    fun hideBoundarySearch() {
        _uiState.value = _uiState.value.copy(
            showBoundarySearch = false,
            boundarySearchQuery = "",
            boundarySearchResults = emptyList()
        )
    }

    fun updateBoundarySearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(boundarySearchQuery = query)

        if (query.length >= 2) {
            searchBoundaries(query)
        } else {
            _uiState.value = _uiState.value.copy(boundarySearchResults = emptyList())
        }
    }

    private fun searchBoundaries(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBoundarySearching = true)

            try {
                // Search within Nueva Ecija / Philippines area
                val bbox = listOf(120.5, 15.3, 121.5, 16.1) // Nueva Ecija approximate bounds

                boundariesService.searchBoundaries(
                    query = query,
                    boundingBox = bbox,
                    countryCode = "PH"
                ).onSuccess { boundaryFeatures ->
                    val searchResults = boundaryFeatures.map { feature ->
                        BoundarySearchResult(
                            id = feature.id,
                            name = feature.name,
                            adminLevel = when (feature.adminLevel) {
                                0 -> "Country"
                                1 -> "Province"
                                2 -> "City/Municipality"
                                3 -> "District"
                                else -> "Local Area"
                            },
                            country = feature.isoCountryCode,
                            centerLat = feature.centroid?.second ?: 15.7886,
                            centerLng = feature.centroid?.first ?: 121.0748,
                            bbox = feature.bbox
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        boundarySearchResults = searchResults,
                        isBoundarySearching = false
                    )
                }.onFailure { exception ->
                    Log.e(TAG, "Boundary search failed", exception)
                    _uiState.value = _uiState.value.copy(
                        isBoundarySearching = false,
                        errorMessage = "Failed to search boundaries: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in boundary search", e)
                _uiState.value = _uiState.value.copy(
                    isBoundarySearching = false,
                    errorMessage = "Boundary search error: ${e.message}"
                )
            }
        }
    }

    fun importBoundaryAsTemplate(result: BoundarySearchResult) {
        viewModelScope.launch {
            try {
                // Create a rectangular boundary around the search result's center
                // Since we're using the free tier, we create an approximate boundary
                val centerLat = result.centerLat
                val centerLng = result.centerLng

                // Create approximate boundary points based on bounding box or default size
                val latOffset = 0.01 // About 1km
                val lngOffset = 0.01 // About 1km

                val boundaryPoints = if (result.bbox != null && result.bbox.size == 4) {
                    // Use actual bounding box if available
                    listOf(
                        BoundaryPoint(result.bbox[1], result.bbox[0]), // SW
                        BoundaryPoint(result.bbox[1], result.bbox[2]), // SE
                        BoundaryPoint(result.bbox[3], result.bbox[2]), // NE
                        BoundaryPoint(result.bbox[3], result.bbox[0]), // NW
                        BoundaryPoint(result.bbox[1], result.bbox[0])  // Close polygon
                    )
                } else {
                    // Create default rectangular boundary around center
                    listOf(
                        BoundaryPoint(centerLat - latOffset, centerLng - lngOffset),
                        BoundaryPoint(centerLat - latOffset, centerLng + lngOffset),
                        BoundaryPoint(centerLat + latOffset, centerLng + lngOffset),
                        BoundaryPoint(centerLat + latOffset, centerLng - lngOffset),
                        BoundaryPoint(centerLat - latOffset, centerLng - lngOffset)
                    )
                }

                val boundary = ServiceAreaBoundary(
                    points = boundaryPoints,
                    fillColor = "#4CAF5080",
                    strokeColor = "#4CAF50",
                    strokeWidth = 2.0
                )

                val newArea = ServiceArea(
                    id = generateAreaId(),
                    name = "${result.name} Zone Template",
                    destinations = emptyList(),
                    boundary = boundary,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis()
                )

                repository.addServiceArea(newArea)
                    .onSuccess {
                        Log.d(TAG, "Successfully imported boundary template: ${result.name}")
                        loadServiceAreas()
                        hideBoundarySearch()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to import boundary template", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to import boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing boundary template", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error importing boundary: ${e.message}"
                )
            }
        }
    }

    private fun generateAreaId(): String {
        return "area_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Set boundary fares for a new destination
     */
    fun setBoundaryFaresForDestination(destinationName: String, boundaryFares: Map<String, Double>) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Setting boundary fares for destination: $destinationName")

                // Create a new fare batch for this destination
                val fareBatch = com.rj.islamove.data.models.BoundaryFareBatch(
                    name = "$destinationName Fares",
                    description = "Boundary fares for $destinationName",
                    isActive = true,
                    rules = boundaryFares.map { (boundary, fare) ->
                        com.rj.islamove.data.models.BoundaryFareRule(
                            fromBoundary = boundary,
                            toLocation = destinationName,
                            fare = fare,
                            isActive = true
                        )
                    }
                )

                boundaryFareRepository.addFareBatch(fareBatch)
                    .onSuccess {
                        Log.d(TAG, "Successfully set boundary fares for $destinationName")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to set boundary fares for $destinationName", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to set boundary fares: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting boundary fares for $destinationName", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error setting boundary fares: ${e.message}"
                )
            }
        }
    }

    /**
     * Update boundary fares for an existing destination
     */
    fun updateBoundaryFaresForDestination(destinationName: String, boundaryFares: Map<String, Double>) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating boundary fares for destination: $destinationName")

                // Get existing fare batches for this destination
                val existingBatchesResult = boundaryFareRepository.getAllFareBatches()
                if (existingBatchesResult.isFailure) {
                    val exception = existingBatchesResult.exceptionOrNull()
                    Log.e(TAG, "Failed to get existing fare batches", exception)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to get existing fares: ${exception?.message}"
                    )
                    return@launch
                }

                val existingBatches = existingBatchesResult.getOrThrow()
                val destinationBatches = existingBatches.filter {
                    it.name.contains(destinationName, ignoreCase = true)
                }

                // Delete existing batches for this destination
                destinationBatches.forEach { batch ->
                    boundaryFareRepository.deleteFareBatch(batch.id)
                        .onFailure { exception ->
                            Log.e(TAG, "Failed to delete fare batch ${batch.id}", exception)
                        }
                }

                // Create new fare batch with updated fares
                val fareBatch = com.rj.islamove.data.models.BoundaryFareBatch(
                    name = "$destinationName Fares",
                    description = "Boundary fares for $destinationName",
                    isActive = true,
                    rules = boundaryFares.map { (boundary, fare) ->
                        com.rj.islamove.data.models.BoundaryFareRule(
                            fromBoundary = boundary,
                            toLocation = destinationName,
                            fare = fare,
                            isActive = true
                        )
                    }
                )

                boundaryFareRepository.addFareBatch(fareBatch)
                    .onSuccess {
                        Log.d(TAG, "Successfully updated boundary fares for $destinationName")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to update boundary fares for $destinationName", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to update boundary fares: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating boundary fares for $destinationName", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error updating boundary fares: ${e.message}"
                )
            }
        }
    }

    /**
     * Get all boundary fares for a destination for display (suspend function)
     */
    suspend fun getBoundaryFaresForDestination(destinationName: String): Map<String, Double> {
        val destinationFares = mutableMapOf<String, Double>()

        try {
            val faresResult = boundaryFareRepository.getAllFareBatches()
            if (faresResult.isSuccess) {
                val batches = faresResult.getOrThrow()

                batches.forEach { batch ->
                    batch.rules.forEach { rule ->
                        if (rule.toLocation.equals(destinationName, ignoreCase = true) && rule.isActive) {
                            destinationFares[rule.fromBoundary] = rule.fare
                        }
                    }
                }

                Log.d(TAG, "Retrieved ${destinationFares.size} boundary fares for $destinationName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting boundary fares for $destinationName", e)
        }

        return destinationFares
    }

    /**
     * Get a display string for boundary fares (shows all fares or default message)
     */
    suspend fun getBoundaryFareDisplayString(destinationName: String): String {
        val fares = getBoundaryFaresForDestination(destinationName)
        return if (fares.isNotEmpty()) {
            fares.map { (boundary, fare) -> "$boundary: ₱$fare" }.joinToString(", ")
        } else {
            "No boundary fares set"
        }
    }

    /**
     * Add service area with full ServiceArea object (for service boundary creation)
     */
    fun addServiceAreaWithBoundary(serviceArea: ServiceArea) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.addServiceArea(serviceArea)
                    .onSuccess {
                        loadServiceAreas()
                        Log.d(TAG, "Added new service area with boundary: ${serviceArea.name}")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to add service area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to add service area: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding service area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error adding service area: ${e.message}"
                )
            }
        }
    }

    /**
     * Update service area with full ServiceArea object (for service boundary updates)
     */
    fun updateServiceAreaWithBoundary(serviceArea: ServiceArea) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.updateServiceArea(serviceArea)
                    .onSuccess {
                        loadServiceAreas()
                        Log.d(TAG, "Updated service area: ${serviceArea.name}")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to update service area", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update service area: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating service area", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating service area: ${e.message}"
                )
            }
        }
    }
}