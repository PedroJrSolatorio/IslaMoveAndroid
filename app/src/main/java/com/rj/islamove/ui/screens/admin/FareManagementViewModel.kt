package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.FareConfig
import com.rj.islamove.data.repository.SystemConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FareManagementUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fareConfig: FareConfig? = null,
    val originalFareConfig: FareConfig? = null,
    val hasChanges: Boolean = false
)

@HiltViewModel
class FareManagementViewModel @Inject constructor(
    private val systemConfigRepository: SystemConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FareManagementUiState())
    val uiState: StateFlow<FareManagementUiState> = _uiState.asStateFlow()

    fun loadFareConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                systemConfigRepository.getFareConfig()
                    .onSuccess { config ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            fareConfig = config,
                            originalFareConfig = config,
                            hasChanges = false,
                            errorMessage = null
                        )
                        Log.d("FareManagementVM", "Loaded fare config: $config")
                    }
                    .onFailure { exception ->
                        Log.e("FareManagementVM", "Error loading fare config", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load fare config: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareManagementVM", "Error in loadFareConfig", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading fare config: ${e.message}"
                )
            }
        }
    }

    fun updateBaseFare(baseFare: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(baseFare = baseFare)
        updateFareConfig(updatedConfig)
    }

    fun updatePerKmRate(perKmRate: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(perKmRate = perKmRate)
        updateFareConfig(updatedConfig)
    }

    fun updatePerMinuteRate(perMinuteRate: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(perMinuteRate = perMinuteRate)
        updateFareConfig(updatedConfig)
    }

    fun updateMinimumFare(minimumFare: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(minimumFare = minimumFare)
        updateFareConfig(updatedConfig)
    }

    fun updateMaxSurgeMultiplier(maxSurgeMultiplier: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(maxSurgeMultiplier = maxSurgeMultiplier)
        updateFareConfig(updatedConfig)
    }

    fun updateCancellationFee(cancellationFee: Double) {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(cancellationFee = cancellationFee)
        updateFareConfig(updatedConfig)
    }

    fun toggleSurgeEnabled() {
        val currentConfig = _uiState.value.fareConfig ?: return
        val updatedConfig = currentConfig.copy(surgeEnabled = !currentConfig.surgeEnabled)
        updateFareConfig(updatedConfig)
    }

    private fun updateFareConfig(updatedConfig: FareConfig) {
        val hasChanges = updatedConfig != _uiState.value.originalFareConfig
        _uiState.value = _uiState.value.copy(
            fareConfig = updatedConfig,
            hasChanges = hasChanges
        )
    }

    fun saveFareConfig() {
        val currentConfig = _uiState.value.fareConfig ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                systemConfigRepository.updateFareConfig(currentConfig)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            originalFareConfig = currentConfig,
                            hasChanges = false,
                            errorMessage = null
                        )
                        Log.d("FareManagementVM", "Fare config saved successfully")
                    }
                    .onFailure { exception ->
                        Log.e("FareManagementVM", "Failed to save fare config", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to save fare config: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FareManagementVM", "Error saving fare config", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error saving config: ${e.message}"
                )
            }
        }
    }

    fun resetToOriginal() {
        val originalConfig = _uiState.value.originalFareConfig ?: return
        _uiState.value = _uiState.value.copy(
            fareConfig = originalConfig,
            hasChanges = false
        )
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}