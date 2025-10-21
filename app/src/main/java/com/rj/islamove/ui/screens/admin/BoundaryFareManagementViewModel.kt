package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.BoundaryFareBatch
import com.rj.islamove.data.models.BoundaryFareManagementUiState
import com.rj.islamove.data.models.BoundaryFareRule
import com.rj.islamove.data.repository.BoundaryFareManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoundaryFareManagementViewModel @Inject constructor(
    private val repository: BoundaryFareManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoundaryFareManagementUiState())
    val uiState: StateFlow<BoundaryFareManagementUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "BoundaryFareMgmtVM"
    }

    init {
        loadFareBatches()
        loadAvailableOptions()
    }

    fun loadFareBatches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.getAllFareBatches()
                    .onSuccess { batches ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            fareBatches = batches,
                            errorMessage = null
                        )
                        Log.d(TAG, "Loaded ${batches.size} fare batches")

                        // If no fare batches exist, create a default one
                        if (batches.isEmpty()) {
                            Log.d(TAG, "No fare batches found, creating default batch")
                            createDefaultFareBatch()
                        }
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error loading fare batches", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load fare batches: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadFareBatches", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading fare batches: ${e.message}"
                )
            }
        }
    }

    private fun loadAvailableOptions() {
        _uiState.value = _uiState.value.copy(
            availableBoundaries = repository.getAvailableBoundaries(),
            availableDestinations = repository.getCommonDestinations()
        )
    }

    private fun createDefaultFareBatch() {
        viewModelScope.launch {
            try {
                val defaultRules = listOf(
                    BoundaryFareRule(
                        fromBoundary = "AURELIO",
                        toLocation = "Municipal Hall",
                        fare = 25.0
                    ),
                    BoundaryFareRule(
                        fromBoundary = "DON RUBEN",
                        toLocation = "Municipal Hall",
                        fare = 15.0
                    ),
                    BoundaryFareRule(
                        fromBoundary = "MAHAYAHAY",
                        toLocation = "Municipal Hall",
                        fare = 20.0
                    )
                )

                val defaultBatch = BoundaryFareBatch(
                    name = "San Jose Municipal Hall Boundary Fares",
                    description = "Default boundary fares for Municipal Hall destinations",
                    rules = defaultRules
                )

                repository.addFareBatch(defaultBatch)
                    .onSuccess {
                        Log.d(TAG, "Created default fare batch")
                        loadFareBatches()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Failed to create default fare batch", exception)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating default fare batch", e)
            }
        }
    }

    fun showAddBatchDialog() {
        _uiState.value = _uiState.value.copy(showBatchDialog = true, editingBatch = null)
    }

    fun showEditBatchDialog(batch: BoundaryFareBatch) {
        _uiState.value = _uiState.value.copy(showBatchDialog = true, editingBatch = batch)
    }

    fun hideBatchDialog() {
        _uiState.value = _uiState.value.copy(showBatchDialog = false, editingBatch = null)
    }

    fun showAddRuleDialog(batch: BoundaryFareBatch) {
        _uiState.value = _uiState.value.copy(
            showRuleDialog = true,
            editingRule = null,
            selectedBatch = batch
        )
    }

    fun showEditRuleDialog(batch: BoundaryFareBatch, rule: BoundaryFareRule) {
        _uiState.value = _uiState.value.copy(
            showRuleDialog = true,
            editingRule = rule,
            selectedBatch = batch
        )
    }

    fun hideRuleDialog() {
        _uiState.value = _uiState.value.copy(
            showRuleDialog = false,
            editingRule = null,
            selectedBatch = null
        )
    }

    fun saveBatch(name: String, description: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val editingBatch = _uiState.value.editingBatch
                val batch = if (editingBatch != null) {
                    editingBatch.copy(name = name, description = description)
                } else {
                    BoundaryFareBatch(name = name, description = description)
                }

                val result = if (editingBatch != null) {
                    repository.updateFareBatch(batch)
                } else {
                    repository.addFareBatch(batch)
                }

                result.onSuccess {
                    Log.d(TAG, "Successfully saved fare batch: $name")
                    hideBatchDialog()
                    loadFareBatches()
                }.onFailure { exception ->
                    Log.e(TAG, "Error saving fare batch", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to save fare batch: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in saveBatch", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error saving fare batch: ${e.message}"
                )
            }
        }
    }

    fun deleteBatch(batch: BoundaryFareBatch) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.deleteFareBatch(batch.id)
                    .onSuccess {
                        Log.d(TAG, "Successfully deleted fare batch: ${batch.name}")
                        loadFareBatches()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error deleting fare batch", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete fare batch: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteBatch", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting fare batch: ${e.message}"
                )
            }
        }
    }

    fun saveRule(fromBoundary: String, toLocation: String, fare: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val selectedBatch = _uiState.value.selectedBatch
                val editingRule = _uiState.value.editingRule

                if (selectedBatch == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No fare batch selected"
                    )
                    return@launch
                }

                val rule = if (editingRule != null) {
                    editingRule.copy(fromBoundary = fromBoundary, toLocation = toLocation, fare = fare)
                } else {
                    BoundaryFareRule(fromBoundary = fromBoundary, toLocation = toLocation, fare = fare)
                }

                val result = if (editingRule != null) {
                    repository.updateFareRuleInBatch(selectedBatch.id, rule)
                } else {
                    repository.addFareRuleToBatch(selectedBatch.id, rule)
                }

                result.onSuccess {
                    Log.d(TAG, "Successfully saved fare rule: $fromBoundary -> $toLocation")
                    hideRuleDialog()
                    loadFareBatches()
                }.onFailure { exception ->
                    Log.e(TAG, "Error saving fare rule", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to save fare rule: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in saveRule", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error saving fare rule: ${e.message}"
                )
            }
        }
    }

    fun deleteRule(batch: BoundaryFareBatch, rule: BoundaryFareRule) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                repository.removeFareRuleFromBatch(batch.id, rule.id)
                    .onSuccess {
                        Log.d(TAG, "Successfully deleted fare rule: ${rule.fromBoundary} -> ${rule.toLocation}")
                        loadFareBatches()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error deleting fare rule", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete fare rule: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteRule", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error deleting fare rule: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}