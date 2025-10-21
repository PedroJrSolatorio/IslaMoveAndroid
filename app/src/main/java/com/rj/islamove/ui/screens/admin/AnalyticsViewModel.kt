package com.rj.islamove.ui.screens.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.repository.AnalyticsRepository
import com.rj.islamove.data.models.TimePeriod
import com.rj.islamove.data.models.PlatformAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val analytics: PlatformAnalytics? = null,
    val selectedPeriod: TimePeriod = TimePeriod.WEEK
)


@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    fun loadAnalyticsData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                analyticsRepository.getPlatformAnalytics(_uiState.value.selectedPeriod)
                    .onSuccess { analytics ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            analytics = analytics,
                            errorMessage = null
                        )
                        Log.d("AnalyticsVM", "Loaded analytics: $analytics")
                    }
                    .onFailure { exception ->
                        Log.e("AnalyticsVM", "Error loading analytics", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load analytics: ${exception.message}"
                        )
                    }
            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error in loadAnalyticsData", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading analytics: ${e.message}"
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
        loadAnalyticsData() // Reload data for new period
    }

    fun refreshData() {
        loadAnalyticsData()
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun selectPeriod(period: TimePeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        loadAnalyticsData() // Reload data for the new period
    }
}