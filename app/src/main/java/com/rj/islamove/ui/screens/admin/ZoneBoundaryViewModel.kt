package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.data.models.ZoneBoundary
import com.rj.islamove.data.models.ZoneBoundaryUiState
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import com.rj.islamove.utils.ZoneBoundaryMigrationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class ZoneBoundaryViewModel @Inject constructor(
    private val repository: ZoneBoundaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneBoundaryUiState())
    val uiState: StateFlow<ZoneBoundaryUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ZoneBoundaryViewModel"
    }

    init {
        loadZoneBoundaries()
    }

    /**
     * Load all zone boundaries from Firestore
     */
    fun loadZoneBoundaries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.getAllZoneBoundaries()
                    .onSuccess { boundaries ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            zoneBoundaries = boundaries,
                            errorMessage = null
                        )
                        Log.d(TAG, "Loaded ${boundaries.size} zone boundaries")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error loading zone boundaries", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load zone boundaries: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadZoneBoundaries", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading zone boundaries: ${e.message}"
                )
            }
        }
    }

    /**
     * Start drawing a new boundary
     */
    fun startDrawingBoundary() {
        _uiState.value = _uiState.value.copy(
            isDrawingBoundary = true,
            boundaryPoints = emptyList(),
            editingBoundary = null,
            selectedPointIndex = null
        )
        Log.d(TAG, "Started drawing new boundary")
    }

    /**
     * Add a point to the current boundary being drawn
     */
    fun addBoundaryPoint(latitude: Double, longitude: Double) {
        val currentPoints = _uiState.value.boundaryPoints
        val newPoint = BoundaryPoint(latitude, longitude)
        _uiState.value = _uiState.value.copy(
            boundaryPoints = currentPoints + newPoint
        )
        Log.d(TAG, "Added boundary point: $latitude, $longitude (total: ${currentPoints.size + 1})")
        Log.d(TAG, "Editing mode: ${_uiState.value.editingBoundary?.name ?: "New boundary"}")
    }

    /**
     * Update a specific boundary point (for dragging/editing)
     */
    fun updateBoundaryPoint(index: Int, latitude: Double, longitude: Double) {
        val currentPoints = _uiState.value.boundaryPoints.toMutableList()
        if (index in currentPoints.indices) {
            currentPoints[index] = BoundaryPoint(latitude, longitude)
            _uiState.value = _uiState.value.copy(boundaryPoints = currentPoints)
            Log.d(TAG, "Updated boundary point $index: $latitude, $longitude")
            Log.d(TAG, "Total points after update: ${currentPoints.size}")
        } else {
            Log.w(TAG, "Invalid point index $index, total points: ${currentPoints.size}")
        }
    }

    /**
     * Remove a boundary point
     */
    fun removeBoundaryPoint(index: Int) {
        val currentPoints = _uiState.value.boundaryPoints.toMutableList()
        if (index in currentPoints.indices) {
            currentPoints.removeAt(index)
            _uiState.value = _uiState.value.copy(boundaryPoints = currentPoints)
            Log.d(TAG, "Removed boundary point $index (remaining: ${currentPoints.size})")
        }
    }

    /**
     * Clear all boundary points
     */
    fun clearBoundaryPoints() {
        _uiState.value = _uiState.value.copy(boundaryPoints = emptyList())
        Log.d(TAG, "Cleared all boundary points")
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
     * Move the selected boundary point to a new location
     */
    fun moveSelectedPoint(latitude: Double, longitude: Double) {
        val selectedIndex = _uiState.value.selectedPointIndex
        if (selectedIndex != null) {
            val currentPoints = _uiState.value.boundaryPoints.toMutableList()
            if (selectedIndex in currentPoints.indices) {
                currentPoints[selectedIndex] = BoundaryPoint(latitude, longitude)
                _uiState.value = _uiState.value.copy(boundaryPoints = currentPoints)
                Log.d(TAG, "Moved selected point $selectedIndex to: $latitude, $longitude")
            } else {
                Log.w(TAG, "Invalid selected point index $selectedIndex, total points: ${currentPoints.size}")
            }
        } else {
            Log.w(TAG, "No point selected for moving")
        }
    }

    /**
     * Start dragging a boundary point (legacy method for backward compatibility)
     */
    fun startDraggingPoint(index: Int) {
        selectBoundaryPoint(index)
    }

    /**
     * Stop dragging a boundary point (legacy method for backward compatibility)
     */
    fun stopDraggingPoint() {
        deselectBoundaryPoint()
    }

    /**
     * Finish drawing and save the boundary
     */
    fun finishDrawingBoundary(name: String) {
        viewModelScope.launch {
            val points = _uiState.value.boundaryPoints

            if (points.size < 3) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "A boundary must have at least 3 points"
                )
                return@launch
            }

            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Please enter a boundary name"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Check if name already exists
                val nameExists = repository.boundaryNameExists(name).getOrDefault(false)
                if (nameExists) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "A boundary with the name '$name' already exists"
                    )
                    return@launch
                }

                // Ensure polygon is closed
                val closedPoints = if (points.first() != points.last()) {
                    points + points.first()
                } else {
                    points
                }

                val newBoundary = ZoneBoundary(
                    name = name.uppercase(),
                    points = closedPoints,
                    fillColor = "#FF9800",
                    strokeColor = "#F57C00",
                    strokeWidth = 2.0,
                    isActive = true
                )

                repository.addZoneBoundary(newBoundary)
                    .onSuccess {
                        Log.d(TAG, "Successfully saved boundary: $name")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isDrawingBoundary = false,
                            boundaryPoints = emptyList(),
                            selectedPointIndex = null,
                            errorMessage = null
                        )
                        loadZoneBoundaries()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error saving boundary", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to save boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in finishDrawingBoundary", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error saving boundary: ${e.message}"
                )
            }
        }
    }

    /**
     * Cancel drawing a boundary
     */
    fun cancelDrawingBoundary() {
        _uiState.value = _uiState.value.copy(
            isDrawingBoundary = false,
            boundaryPoints = emptyList(),
            editingBoundary = null,
            selectedPointIndex = null
        )
        Log.d(TAG, "Cancelled drawing boundary")
    }

    /**
     * Start editing an existing boundary
     */
    fun startEditingBoundary(boundary: ZoneBoundary) {
        // Remove the duplicate closing point if it exists (polygon is closed by having first point at end)
        val pointsForEditing = if (boundary.points.size > 1 && boundary.points.first() == boundary.points.last()) {
            boundary.points.dropLast(1)
        } else {
            boundary.points
        }

        _uiState.value = _uiState.value.copy(
            isDrawingBoundary = true,
            editingBoundary = boundary,
            boundaryPoints = pointsForEditing,
            showBoundaryDialog = false,
            selectedPointIndex = null
        )
        Log.d(TAG, "Started editing boundary: ${boundary.name} with ${pointsForEditing.size} unique points")
    }

    /**
     * Update an existing boundary
     */
    fun updateBoundary(boundaryId: String, name: String) {
        viewModelScope.launch {
            val points = _uiState.value.boundaryPoints

            if (points.size < 3) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "A boundary must have at least 3 points"
                )
                return@launch
            }

            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Please enter a boundary name"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Check if name already exists (excluding current boundary)
                val nameExists = repository.boundaryNameExists(name, boundaryId).getOrDefault(false)
                if (nameExists) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "A boundary with the name '$name' already exists"
                    )
                    return@launch
                }

                // Ensure polygon is closed
                val closedPoints = if (points.first() != points.last()) {
                    points + points.first()
                } else {
                    points
                }

                val updatedBoundary = _uiState.value.editingBoundary?.copy(
                    id = boundaryId,
                    name = name.uppercase(),
                    points = closedPoints
                ) ?: return@launch

                repository.updateZoneBoundary(updatedBoundary)
                    .onSuccess {
                        Log.d(TAG, "Successfully updated boundary: $name")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isDrawingBoundary = false,
                            boundaryPoints = emptyList(),
                            editingBoundary = null,
                            selectedPointIndex = null,
                            errorMessage = null
                        )
                        loadZoneBoundaries()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error updating boundary", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateBoundary", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating boundary: ${e.message}"
                )
            }
        }
    }

    /**
     * Update boundary fares for a specific boundary
     */
    fun updateBoundaryFares(boundaryId: String, boundaryFares: Map<String, Double>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Find the boundary to update
                val boundary = _uiState.value.zoneBoundaries.find { it.id == boundaryId }
                if (boundary == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Boundary not found"
                    )
                    return@launch
                }

                // Create updated boundary with new fares
                val updatedBoundary = boundary.copy(
                    boundaryFares = boundaryFares,
                    lastUpdated = System.currentTimeMillis()
                )

                repository.updateZoneBoundary(updatedBoundary)
                    .onSuccess {
                        Log.d(TAG, "Successfully updated boundary fares for: ${boundary.name}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Boundary fares updated successfully"
                        )
                        loadZoneBoundaries()

                        // Clear success message after 2 seconds
                        delay(2000)
                        _uiState.value = _uiState.value.copy(successMessage = null)
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error updating boundary fares", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update boundary fares: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateBoundaryFares", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating boundary fares: ${e.message}"
                )
            }
        }
    }

    /**
     * Update compatible boundaries for ride pooling
     */
    fun updateCompatibleBoundaries(boundaryId: String, compatibleBoundaries: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Find the boundary to update
                val boundary = _uiState.value.zoneBoundaries.find { it.id == boundaryId }
                if (boundary == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Boundary not found"
                    )
                    return@launch
                }

                // Create updated boundary with new compatible boundaries
                val updatedBoundary = boundary.copy(
                    compatibleBoundaries = compatibleBoundaries,
                    lastUpdated = System.currentTimeMillis()
                )

                repository.updateZoneBoundary(updatedBoundary)
                    .onSuccess {
                        Log.d(TAG, "Successfully updated compatible boundaries for: ${boundary.name}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Compatibility settings updated successfully"
                        )
                        loadZoneBoundaries()

                        // Clear success message after 2 seconds
                        delay(2000)
                        _uiState.value = _uiState.value.copy(successMessage = null)
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error updating compatible boundaries", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update compatibility settings: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateCompatibleBoundaries", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating compatibility settings: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a zone boundary
     */
    fun deleteBoundary(boundaryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.deleteZoneBoundary(boundaryId)
                    .onSuccess {
                        Log.d(TAG, "Successfully deleted boundary: $boundaryId")
                        // Add delay to ensure Firestore consistency before refreshing
                        delay(1000)
                        loadZoneBoundaries()

                        // Add success message and clear loading state
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Boundary deleted successfully"
                        )

                        // Clear success message after 3 seconds
                        delay(3000)
                        _uiState.value = _uiState.value.copy(successMessage = null)
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error deleting boundary", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteBoundary", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting boundary: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a boundary to view details
     */
    fun selectBoundary(boundary: ZoneBoundary) {
        _uiState.value = _uiState.value.copy(
            selectedBoundary = boundary,
            showBoundaryDialog = true
        )
        Log.d(TAG, "Selected boundary: ${boundary.name}")
    }

    /**
     * Clear selected boundary
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedBoundary = null,
            showBoundaryDialog = false
        )
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    /**
     * Load all boundaries including inactive ones
     */
    fun loadAllBoundariesIncludingInactive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.getAllZoneBoundariesIncludingInactive()
                    .onSuccess { boundaries ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            zoneBoundaries = boundaries,
                            errorMessage = null
                        )
                        Log.d(TAG, "Loaded ${boundaries.size} total boundaries (including inactive)")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error loading all boundaries", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load boundaries: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadAllBoundariesIncludingInactive", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading boundaries: ${e.message}"
                )
            }
        }
    }

    /**
     * Reactivate a boundary
     */
    fun reactivateBoundary(boundaryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.reactivateZoneBoundary(boundaryId)
                    .onSuccess {
                        Log.d(TAG, "Successfully reactivated boundary: $boundaryId")
                        // Add delay to ensure Firestore consistency before refreshing
                        delay(1000)
                        loadZoneBoundaries()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Boundary reactivated successfully"
                        )
                        // Clear success message after 3 seconds
                        delay(3000)
                        _uiState.value = _uiState.value.copy(successMessage = null)
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error reactivating boundary", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to reactivate boundary: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reactivateBoundary", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error reactivating boundary: ${e.message}"
                )
            }
        }
    }

    /**
     * Migrate hardcoded boundaries from BoundaryFareUtils to Firestore
     * This should only be called once by admin
     */
    fun migrateBoundaries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Check if already migrated
                val isMigrated = ZoneBoundaryMigrationUtil.isMigrated(repository)
                if (isMigrated) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Boundaries have already been migrated"
                    )
                    return@launch
                }

                // Perform migration
                ZoneBoundaryMigrationUtil.migrateToFirestore(repository)
                    .onSuccess { count ->
                        Log.i(TAG, "Successfully migrated $count boundaries")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null
                        )
                        loadZoneBoundaries()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error migrating boundaries", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Migration failed: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in migrateBoundaries", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Migration error: ${e.message}"
                )
            }
        }
    }
}