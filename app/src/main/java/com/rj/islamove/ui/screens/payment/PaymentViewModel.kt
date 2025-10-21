package com.rj.islamove.ui.screens.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rj.islamove.data.models.*
import com.rj.islamove.data.repository.BookingRepository
import com.rj.islamove.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Loading)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val TAG = "PaymentViewModel"
    }
    
    fun loadBookingForPayment(bookingId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading booking for payment: $bookingId")
                
                val result = bookingRepository.getBooking(bookingId)
                result.fold(
                    onSuccess = { booking ->
                        if (booking.status == BookingStatus.COMPLETED) {
                            // Calculate fare breakdown for display
                            val fareBreakdown = calculateDisplayFareBreakdown(booking)
                            
                            _uiState.value = PaymentUiState.Ready(
                                booking = booking,
                                fareBreakdown = fareBreakdown,
                                isProcessing = false
                            )
                        } else {
                            _uiState.value = PaymentUiState.Error(
                                "Trip must be completed before payment can be processed"
                            )
                        }
                    },
                    onFailure = {
                        _uiState.value = PaymentUiState.Error("Booking not found")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading booking for payment", e)
                _uiState.value = PaymentUiState.Error("Failed to load booking details")
            }
        }
    }
    
    fun processPayment(paymentMethod: PaymentMethod, discountType: String?) {
        val currentState = _uiState.value
        if (currentState !is PaymentUiState.Ready) return
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing payment with method: $paymentMethod")
                
                // Update UI to show processing
                _uiState.value = currentState.copy(isProcessing = true)
                
                // Process the payment
                val result = when (paymentMethod) {
                    PaymentMethod.CASH -> {
                        paymentRepository.processCashPayment(currentState.booking)
                    }
                    else -> {
                        // For digital payments, we would integrate with actual payment gateways
                        // For now, simulate a successful digital payment
                        val transactionId = "TXN${System.currentTimeMillis()}"
                        paymentRepository.processDigitalPayment(
                            booking = currentState.booking,
                            paymentMethod = paymentMethod,
                            transactionId = transactionId
                        )
                    }
                }
                
                result.fold(
                    onSuccess = { payment ->
                        Log.d(TAG, "Payment processed successfully: ${payment.id}")
                        _uiState.value = PaymentUiState.Success(payment)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Payment processing failed", exception)
                        _uiState.value = PaymentUiState.Error(
                            "Payment processing failed: ${exception.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing payment", e)
                _uiState.value = PaymentUiState.Error("Payment processing failed")
            }
        }
    }
    
    private fun calculateDisplayFareBreakdown(booking: Booking): FareBreakdown {
        val fareEstimate = booking.fareEstimate
        val baseFare = fareEstimate.baseFare
        val distanceFare = fareEstimate.distanceFare
        val timeFare = fareEstimate.timeFare
        val surgeFare = if (fareEstimate.surgeFactor > 1.0) {
            (baseFare + distanceFare + timeFare) * (fareEstimate.surgeFactor - 1.0)
        } else 0.0
        
        val subtotal = baseFare + distanceFare + timeFare + surgeFare
        val tax = 0.0 // No tax for transportation
        val total = subtotal + tax
        
        return FareBreakdown(
            baseFare = baseFare,
            distanceFare = distanceFare,
            timeFare = timeFare,
            surgeFare = surgeFare,
            discount = 0.0, // Will be calculated when discount is applied
            subtotal = subtotal,
            tax = tax,
            total = total
        )
    }
    
    fun retryLoadBooking(bookingId: String) {
        _uiState.value = PaymentUiState.Loading
        loadBookingForPayment(bookingId)
    }
}

sealed class PaymentUiState {
    object Loading : PaymentUiState()
    data class Error(val message: String) : PaymentUiState()
    data class Ready(
        val booking: Booking,
        val fareBreakdown: FareBreakdown,
        val isProcessing: Boolean = false
    ) : PaymentUiState()
    data class Success(val payment: Payment) : PaymentUiState()
}