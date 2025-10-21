package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.AnalyticsRepository
import com.rj.islamove.data.repository.RevenueAnalytics
import com.rj.islamove.data.models.TimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FinancialReportsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val revenueAnalytics: RevenueAnalytics? = null,
    val selectedPeriod: TimePeriod = TimePeriod.MONTH,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false
)

@HiltViewModel
class FinancialReportsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinancialReportsUiState())
    val uiState: StateFlow<FinancialReportsUiState> = _uiState.asStateFlow()

    fun loadFinancialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                analyticsRepository.getRevenueAnalytics(_uiState.value.selectedPeriod)
                    .onSuccess { analytics ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            revenueAnalytics = analytics,
                            errorMessage = null
                        )
                        Log.d("FinancialReportsVM", "Loaded financial data: $analytics")
                    }
                    .onFailure { exception ->
                        Log.e("FinancialReportsVM", "Error loading financial data", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load financial data: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("FinancialReportsVM", "Error in loadFinancialData", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading financial data: ${e.message}"
                )
            }
        }
    }

    fun toggleTimePeriod() {
        val currentPeriod = _uiState.value.selectedPeriod
        val nextPeriod = when (currentPeriod) {
            TimePeriod.TODAY -> TimePeriod.WEEK
            TimePeriod.WEEK -> TimePeriod.MONTH
            TimePeriod.MONTH -> TimePeriod.YEAR
            TimePeriod.YEAR -> TimePeriod.TODAY
        }
        
        _uiState.value = _uiState.value.copy(selectedPeriod = nextPeriod)
        loadFinancialData() // Reload data for new period
    }

    fun exportFinancialReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportSuccess = false)
            
            try {
                // In a real implementation, this would generate and save a report file
                // For now, we'll simulate the export process
                kotlinx.coroutines.delay(2000) // Simulate export time
                
                val analytics = _uiState.value.revenueAnalytics
                if (analytics != null) {
                    Log.d("FinancialReportsVM", "Exporting financial report for period: ${analytics.period}")
                    
                    // Here you would typically:
                    // 1. Generate CSV/PDF report
                    // 2. Save to device storage
                    // 3. Share via intent or save to Downloads
                    
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportSuccess = true
                    )
                    
                    // Clear success message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.value = _uiState.value.copy(exportSuccess = false)
                    
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "No data available to export"
                    )
                }
            } catch (e: Exception) {
                Log.e("FinancialReportsVM", "Error exporting report", e)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = "Failed to export report: ${e.message}"
                )
            }
        }
    }

    fun refreshData() {
        loadFinancialData()
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}