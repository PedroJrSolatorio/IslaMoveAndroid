package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.SystemConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemConfigUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val systemConfig: SystemConfig? = null
)

@HiltViewModel
class SystemConfigViewModel @Inject constructor(
    private val systemConfigRepository: SystemConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemConfigUiState())
    val uiState: StateFlow<SystemConfigUiState> = _uiState.asStateFlow()

    fun loadSystemConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                systemConfigRepository.getSystemConfig().collect { result ->
                    result.onSuccess { config ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            systemConfig = config,
                            errorMessage = null
                        )
                        Log.d("SystemConfigVM", "Loaded system config: $config")
                    }.onFailure { exception ->
                        Log.e("SystemConfigVM", "Error loading system config", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load system config: ${exception.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SystemConfigVM", "Error in loadSystemConfig", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading config: ${e.message}"
                )
            }
        }
    }

    fun toggleMaintenanceMode() {
        viewModelScope.launch {
            val currentConfig = _uiState.value.systemConfig ?: return@launch
            
            try {
                val updatedConfig = currentConfig.copy(
                    maintenanceMode = !currentConfig.maintenanceMode,
                    lastUpdated = System.currentTimeMillis()
                )
                
                systemConfigRepository.updateSystemConfig(updatedConfig)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            systemConfig = updatedConfig,
                            errorMessage = null
                        )
                        Log.d("SystemConfigVM", "Maintenance mode toggled: ${updatedConfig.maintenanceMode}")
                    }
                    .onFailure { exception ->
                        Log.e("SystemConfigVM", "Failed to toggle maintenance mode", exception)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to update maintenance mode: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("SystemConfigVM", "Error toggling maintenance mode", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error updating config: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}