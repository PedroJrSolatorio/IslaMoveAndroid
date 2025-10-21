package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.FareMatrixEntry
import com.rj.islamove.data.repository.FareMatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FareMatrixUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fareEntries: List<FareMatrixEntry> = emptyList(),
    val availableLocations: List<String> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingEntry: FareMatrixEntry? = null,
    val hasChanges: Boolean = false
)

@HiltViewModel
class FareMatrixViewModel @Inject constructor(
    private val fareMatrixRepository: FareMatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FareMatrixUiState())
    val uiState: StateFlow<FareMatrixUiState> = _uiState.asStateFlow()

    init {
        loadFareMatrix()
        loadAvailableLocations()
    }

    fun loadFareMatrix() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                fareMatrixRepository.getAllFareEntries()
                    .onSuccess { entries ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            fareEntries = entries,
                            errorMessage = null
                        )
                        Log.d("FareMatrixVM", "Loaded ${entries.size} fare entries")
                    }
                    .onFailure { exception ->
                        Log.e("FareMatrixVM", "Error loading fare entries", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load fare matrix: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareMatrixVM", "Error in loadFareMatrix", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading fare matrix: ${e.message}"
                )
            }
        }
    }

    private fun loadAvailableLocations() {
        viewModelScope.launch {
            try {
                fareMatrixRepository.getUniqueLocations()
                    .onSuccess { locations ->
                        _uiState.value = _uiState.value.copy(availableLocations = locations)
                    }
                    .onFailure { exception ->
                        Log.e("FareMatrixVM", "Error loading locations", exception)
                    }
            } catch (e: Exception) {
                Log.e("FareMatrixVM", "Error in loadAvailableLocations", e)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingEntry = null)
    }

    fun editEntry(entry: FareMatrixEntry) {
        _uiState.value = _uiState.value.copy(
            editingEntry = entry,
            showAddDialog = true
        )
    }

    fun addFareEntry(fromLocation: String, toLocation: String, regularFare: Double, discountFare: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val newEntry = FareMatrixEntry(
                    fromLocation = fromLocation,
                    toLocation = toLocation,
                    regularFare = regularFare,
                    discountFare = discountFare
                )

                fareMatrixRepository.addFareEntry(newEntry)
                    .onSuccess {
                        loadFareMatrix()
                        hideAddDialog()
                        _uiState.value = _uiState.value.copy(hasChanges = true)
                        Log.d("FareMatrixVM", "Added new fare entry: $fromLocation -> $toLocation")
                    }
                    .onFailure { exception ->
                        Log.e("FareMatrixVM", "Failed to add fare entry", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to add fare entry: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareMatrixVM", "Error adding fare entry", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error adding fare entry: ${e.message}"
                )
            }
        }
    }

    fun updateFareEntry(entryId: String, fromLocation: String, toLocation: String, regularFare: Double, discountFare: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val updatedEntry = FareMatrixEntry(
                    id = entryId,
                    fromLocation = fromLocation,
                    toLocation = toLocation,
                    regularFare = regularFare,
                    discountFare = discountFare
                )

                fareMatrixRepository.updateFareEntry(updatedEntry)
                    .onSuccess {
                        loadFareMatrix()
                        hideAddDialog()
                        _uiState.value = _uiState.value.copy(hasChanges = true)
                        Log.d("FareMatrixVM", "Updated fare entry: $fromLocation -> $toLocation")
                    }
                    .onFailure { exception ->
                        Log.e("FareMatrixVM", "Failed to update fare entry", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to update fare entry: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareMatrixVM", "Error updating fare entry", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error updating fare entry: ${e.message}"
                )
            }
        }
    }

    fun deleteFareEntry(entryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                fareMatrixRepository.deleteFareEntry(entryId)
                    .onSuccess {
                        loadFareMatrix()
                        _uiState.value = _uiState.value.copy(hasChanges = true)
                        Log.d("FareMatrixVM", "Deleted fare entry: $entryId")
                    }
                    .onFailure { exception ->
                        Log.e("FareMatrixVM", "Failed to delete fare entry", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete fare entry: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareMatrixVM", "Error deleting fare entry", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting fare entry: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}