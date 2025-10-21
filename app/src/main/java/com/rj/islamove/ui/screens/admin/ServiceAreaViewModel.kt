package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.data.models.ServiceDestination
import com.rj.islamove.data.repository.LandmarkRepository
import com.rj.islamove.data.repository.ServiceAreaManagementRepository
import com.rj.islamove.data.api.MapboxBoundariesService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.text.Regex
import javax.inject.Inject

@HiltViewModel
class ServiceAreaViewModel @Inject constructor(
    private val landmarkRepository: LandmarkRepository,
    private val serviceAreaRepository: ServiceAreaManagementRepository,
    private val boundariesService: MapboxBoundariesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceAreaUiState())
    val uiState: StateFlow<ServiceAreaUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ServiceAreaVM"
    }

    fun loadLandmarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Try to load landmarks from service area management system first
                val serviceAreasResult = serviceAreaRepository.getAllServiceAreas()
                serviceAreasResult.onSuccess { serviceAreas ->
                    // Convert service area destinations to custom landmarks with name and color only
                    val serviceLandmarks = serviceAreas.flatMap { area ->
                        area.destinations.map { destination ->
                            CustomLandmark(
                                id = destination.id,
                                name = destination.name,
                                latitude = destination.latitude,
                                longitude = destination.longitude,
                                color = destination.markerColor
                            )
                        }
                    }

                    // Also load legacy landmarks
                    val legacyLandmarks = try {
                        landmarkRepository.getAllLandmarks()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load legacy landmarks", e)
                        emptyList()
                    }

                    // Combine both lists, prioritizing service area landmarks
                    val allLandmarks = serviceLandmarks + legacyLandmarks.filter { legacy ->
                        !serviceLandmarks.any { it.name.startsWith(legacy.name.split(" - ")[0]) }
                    }

                    Log.d(TAG, "Loaded ${allLandmarks.size} landmarks: ${serviceLandmarks.size} from service areas, ${legacyLandmarks.size} legacy")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        landmarks = allLandmarks
                    )
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load service areas, falling back to legacy landmarks", exception)
                    loadLegacyLandmarks()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadLandmarks", e)
                loadLegacyLandmarks()
            }
        }
    }

    private suspend fun loadLegacyLandmarks() {
        try {
            val landmarks = landmarkRepository.getAllLandmarks()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                landmarks = landmarks
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to load landmarks: ${e.message}"
            )
        }
    }

    fun addLandmark(location: Point, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // First try to add to service area management system
                val serviceDestination = ServiceDestination(
                    name = name.trim(),
                    latitude = location.latitude(),
                    longitude = location.longitude(),
                    regularFare = 0.0, // Not used in display
                    discountFare = 0.0, // Not used in display
                    markerColor = "red"
                )

                // Add to the first service area (or create a default one)
                val areasResult = serviceAreaRepository.getAllServiceAreas()
                areasResult.onSuccess { areas ->
                    val targetArea = areas.firstOrNull()
                    if (targetArea != null) {
                        serviceAreaRepository.addDestinationToArea(targetArea.id, serviceDestination)
                            .onSuccess {
                                Log.d(TAG, "Added destination '$name' to service area '${targetArea.name}' with fare ₱${serviceDestination.regularFare}")
                                // Reload landmarks to include the new destination
                                loadLandmarks()
                            }
                            .onFailure { exception ->
                                Log.e(TAG, "Failed to add destination to service area", exception)
                                // Fallback to old landmark system
                                addLegacyLandmark(location, name)
                            }
                    } else {
                        Log.w(TAG, "No service areas found, using legacy landmark system")
                        addLegacyLandmark(location, name)
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load service areas", exception)
                    // Fallback to old landmark system
                    addLegacyLandmark(location, name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding landmark", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to add landmark: ${e.message}"
                )
            }
        }
    }

    private suspend fun addLegacyLandmark(location: Point, name: String) {
        try {
            val landmark = CustomLandmark(
                id = generateLandmarkId(),
                name = name,
                latitude = location.latitude(),
                longitude = location.longitude()
            )

            landmarkRepository.saveLandmark(landmark)

            // Update local state
            val currentLandmarks = _uiState.value.landmarks.toMutableList()
            currentLandmarks.add(landmark)
            _uiState.value = _uiState.value.copy(
                landmarks = currentLandmarks,
                isLoading = false,
                isAddingMode = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to add landmark: ${e.message}"
            )
        }
    }

    fun deleteLandmark(landmarkId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Try to delete from service area system first
                val serviceAreasResult = serviceAreaRepository.getAllServiceAreas()
                serviceAreasResult.onSuccess { serviceAreas ->
                    var deletedFromServiceArea = false

                    for (area in serviceAreas) {
                        val destinationExists = area.destinations.any { it.id == landmarkId }
                        if (destinationExists) {
                            serviceAreaRepository.removeDestinationFromArea(area.id, landmarkId)
                                .onSuccess {
                                    Log.d(TAG, "Deleted destination from service area '${area.name}'")
                                    deletedFromServiceArea = true
                                    // Reload landmarks to get updated data
                                    loadLandmarks()
                                    return@onSuccess
                                }
                                .onFailure { exception ->
                                    Log.e(TAG, "Failed to delete destination from service area", exception)
                                }
                        }
                    }

                    if (!deletedFromServiceArea) {
                        // Fallback to legacy landmark system
                        deleteLegacyLandmark(landmarkId)
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load service areas for deletion", exception)
                    // Fallback to legacy landmark system
                    deleteLegacyLandmark(landmarkId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting landmark", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete landmark: ${e.message}"
                )
            }
        }
    }

    private suspend fun deleteLegacyLandmark(landmarkId: String) {
        try {
            landmarkRepository.deleteLandmark(landmarkId)

            // Update local state
            val currentLandmarks = _uiState.value.landmarks.toMutableList()
            currentLandmarks.removeIf { it.id == landmarkId }
            _uiState.value = _uiState.value.copy(
                landmarks = currentLandmarks,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to delete landmark: ${e.message}"
            )
        }
    }

    fun editLandmark(landmarkId: String, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val currentLandmarks = _uiState.value.landmarks.toMutableList()
                val landmarkIndex = currentLandmarks.indexOfFirst { it.id == landmarkId }

                if (landmarkIndex != -1) {
                    val oldLandmark = currentLandmarks[landmarkIndex]

                    // Use new name without fare
                    val updatedName = newName

                    // Try to update in service area system first
                    val serviceAreasResult = serviceAreaRepository.getAllServiceAreas()
                    serviceAreasResult.onSuccess { serviceAreas ->
                        var updatedInServiceArea = false

                        for (area in serviceAreas) {
                            val destinationIndex = area.destinations.indexOfFirst { it.id == landmarkId }
                            if (destinationIndex != -1) {
                                val oldDestination = area.destinations[destinationIndex]
                                val updatedDestination = oldDestination.copy(name = newName)

                                serviceAreaRepository.updateDestinationInArea(area.id, updatedDestination)
                                    .onSuccess {
                                        Log.d(TAG, "Updated destination '$newName' in service area '${area.name}'")
                                        updatedInServiceArea = true
                                        // Reload landmarks to get updated data
                                        loadLandmarks()
                                        return@onSuccess
                                    }
                                    .onFailure { exception ->
                                        Log.e(TAG, "Failed to update destination in service area", exception)
                                    }
                            }
                        }

                        if (!updatedInServiceArea) {
                            // Fallback to legacy landmark system
                            updateLegacyLandmark(oldLandmark, updatedName)
                        }
                    }.onFailure { exception ->
                        Log.e(TAG, "Failed to load service areas for editing", exception)
                        // Fallback to legacy landmark system
                        updateLegacyLandmark(oldLandmark, updatedName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error editing landmark", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to edit landmark: ${e.message}"
                )
            }
        }
    }

    private suspend fun updateLegacyLandmark(oldLandmark: CustomLandmark, newName: String) {
        try {
            val updatedLandmark = oldLandmark.copy(name = newName)
            landmarkRepository.saveLandmark(updatedLandmark)

            val currentLandmarks = _uiState.value.landmarks.toMutableList()
            val landmarkIndex = currentLandmarks.indexOfFirst { it.id == oldLandmark.id }
            if (landmarkIndex != -1) {
                currentLandmarks[landmarkIndex] = updatedLandmark
            }

            _uiState.value = _uiState.value.copy(
                landmarks = currentLandmarks,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to edit landmark: ${e.message}"
            )
        }
    }

    fun addDestinationWithFare(location: Point, name: String, regularFare: Double, discountFare: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Create service destination without fares
                val serviceDestination = ServiceDestination(
                    name = name.trim(),
                    latitude = location.latitude(),
                    longitude = location.longitude(),
                    regularFare = 0.0, // Not used
                    discountFare = 0.0, // Not used
                    markerColor = "red"
                )

                // Add to the first service area (or create a default one)
                val areasResult = serviceAreaRepository.getAllServiceAreas()
                areasResult.onSuccess { areas ->
                    val targetArea = areas.firstOrNull()
                    if (targetArea != null) {
                        serviceAreaRepository.addDestinationToArea(targetArea.id, serviceDestination)
                            .onSuccess {
                                Log.d(TAG, "Added destination '$name' to service area '${targetArea.name}' with fare ₱$regularFare")
                                // Reload landmarks to include the new destination
                                loadLandmarks()
                            }
                            .onFailure { exception ->
                                Log.e(TAG, "Failed to add destination to service area", exception)
                                // Fallback to legacy landmark system with correct fare
                                addLegacyLandmarkWithFare(location, name, regularFare)
                            }
                    } else {
                        Log.w(TAG, "No service areas found, using legacy landmark system")
                        addLegacyLandmarkWithFare(location, name, regularFare)
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load service areas", exception)
                    // Fallback to legacy landmark system with correct fare
                    addLegacyLandmarkWithFare(location, name, regularFare)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding destination with fare", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to add destination: ${e.message}"
                )
            }
        }
    }

    private suspend fun addLegacyLandmarkWithFare(location: Point, name: String, fare: Double) {
        try {
            val landmark = CustomLandmark(
                id = generateLandmarkId(),
                name = name, // Name without fare
                latitude = location.latitude(),
                longitude = location.longitude()
            )

            landmarkRepository.saveLandmark(landmark)

            // Update local state
            val currentLandmarks = _uiState.value.landmarks.toMutableList()
            currentLandmarks.add(landmark)
            _uiState.value = _uiState.value.copy(
                landmarks = currentLandmarks,
                isLoading = false,
                isAddingMode = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to add landmark: ${e.message}"
            )
        }
    }

    private fun extractFareFromName(name: String): Double? {
        return try {
            val farePattern = Regex("₱(\\d+(?:\\.\\d{2})?)")
            val matchResult = farePattern.find(name)
            matchResult?.groups?.get(1)?.value?.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    fun clearAllLandmarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Try to clear from service area system first
                val serviceAreasResult = serviceAreaRepository.getAllServiceAreas()
                serviceAreasResult.onSuccess { serviceAreas ->
                    var clearedFromServiceAreas = false

                    for (area in serviceAreas) {
                        if (area.destinations.isNotEmpty()) {
                            // Clear all destinations from this service area
                            for (destination in area.destinations) {
                                serviceAreaRepository.removeDestinationFromArea(area.id, destination.id)
                                    .onSuccess {
                                        clearedFromServiceAreas = true
                                    }
                                    .onFailure { exception ->
                                        Log.e(TAG, "Failed to clear destination from service area", exception)
                                    }
                            }
                        }
                    }

                    if (clearedFromServiceAreas) {
                        Log.d(TAG, "Cleared all destinations from service areas")
                        // Reload landmarks to get updated data
                        loadLandmarks()
                    } else {
                        // Fallback to legacy landmark system
                        clearLegacyLandmarks()
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load service areas for clearing", exception)
                    // Fallback to legacy landmark system
                    clearLegacyLandmarks()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing landmarks", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to clear landmarks: ${e.message}"
                )
            }
        }
    }

    private suspend fun clearLegacyLandmarks() {
        try {
            landmarkRepository.clearAllLandmarks()
            _uiState.value = _uiState.value.copy(
                landmarks = emptyList(),
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to clear landmarks: ${e.message}"
            )
        }
    }

    fun showAddLandmarkMode() {
        _uiState.value = _uiState.value.copy(isAddingMode = true)
    }

    fun hideAddLandmarkMode() {
        _uiState.value = _uiState.value.copy(isAddingMode = false)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun generateLandmarkId(): String {
        return "landmark_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    fun showBoundarySearch() {
        _uiState.value = _uiState.value.copy(showBoundarySearch = true)
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

    fun selectBoundaryResult(result: BoundarySearchResult) {
        viewModelScope.launch {
            // Add the boundary as a landmark for now
            // In a full implementation, this would create a service area with proper boundary
            val location = Point.fromLngLat(result.centerLng, result.centerLat)

            // Create a landmark with the boundary name
            addDestinationWithFare(
                location = location,
                name = "${result.name} (${result.adminLevel})",
                regularFare = 50.0,
                discountFare = 40.0
            )

            // Hide the search dialog
            hideBoundarySearch()
        }
    }
}