package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.*

@Singleton
class PaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val paymentsCollection = firestore.collection("payments")
    private val earningsCollection = firestore.collection("driver_earnings")
    private val transactionsCollection = firestore.collection("earnings_transactions")
    
    companion object {
        private const val TAG = "PaymentRepository"
        // No commission - drivers keep 100% of fare from matrix
    }

    /**
     * Process payment for completed trip
     */
    suspend fun processPayment(
        booking: Booking,
        paymentMethod: PaymentMethod,
        discountType: String? = null
    ): Result<Payment> {
        return try {
            Log.d(TAG, "Processing payment for booking: ${booking.id}")

            // Calculate final fare breakdown using booking's discount percentage
            val fareBreakdown = calculateFinalFareBreakdown(
                fareEstimate = booking.fareEstimate,
                actualDistance = booking.route?.totalDistance ?: 0.0,
                actualDuration = booking.route?.estimatedDuration ?: 0,
                discountType = discountType,
                discountPercentage = booking.passengerDiscountPercentage
            )
            
            // Create payment record
            val payment = Payment(
                id = paymentsCollection.document().id,
                bookingId = booking.id,
                passengerId = booking.passengerId,
                driverId = booking.driverId ?: "",
                amount = fareBreakdown.total,
                paymentMethod = paymentMethod,
                status = PaymentStatus.PENDING,
                breakdown = fareBreakdown,
                receipt = generateReceipt(booking, fareBreakdown)
            )
            
            // Save payment to Firestore
            paymentsCollection.document(payment.id).set(payment).await()
            
            // Process driver earnings
            processDriverEarnings(payment)
            
            // Update booking payment status
            updateBookingPaymentStatus(booking.id, BookingPaymentStatus.PAID)
            
            // Mark payment as completed
            val completedPayment = payment.copy(
                status = PaymentStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
            paymentsCollection.document(payment.id).set(completedPayment).await()
            
            Log.d(TAG, "Payment processed successfully: ${payment.id}")
            Result.success(completedPayment)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing payment", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate final fare breakdown with discounts
     */
    private fun calculateFinalFareBreakdown(
        fareEstimate: FareEstimate,
        actualDistance: Double,
        actualDuration: Int,
        discountType: String?,
        discountPercentage: Int? = null
    ): FareBreakdown {
        val baseFare = fareEstimate.baseFare
        val distanceFare = fareEstimate.distanceFare
        val timeFare = fareEstimate.timeFare
        val surgeFare = if (fareEstimate.surgeFactor > 1.0) {
            (baseFare + distanceFare + timeFare) * (fareEstimate.surgeFactor - 1.0)
        } else 0.0

        val subtotal = baseFare + distanceFare + timeFare + surgeFare

        // Apply discount from booking's passengerDiscountPercentage if available
        val discount = if (discountPercentage != null && discountPercentage > 0) {
            subtotal * (discountPercentage / 100.0)
        } else {
            // Fallback to legacy discountType (20% for seniors, PWD, students)
            when (discountType?.uppercase()) {
                "SENIOR", "PWD", "STUDENT" -> subtotal * 0.20
                else -> 0.0
            }
        }

        Log.d(TAG, "💰 Fare breakdown: subtotal=₱$subtotal, discountPercentage=$discountPercentage%, discount=₱$discount, total=₱${subtotal - discount}")

        val tax = 0.0 // No tax for transportation in this implementation
        val total = subtotal - discount + tax

        return FareBreakdown(
            baseFare = baseFare,
            distanceFare = distanceFare,
            timeFare = timeFare,
            surgeFare = surgeFare,
            discount = discount,
            subtotal = subtotal,
            tax = tax,
            total = total,
            discountType = discountType
        )
    }
    
    /**
     * Generate receipt for the trip
     */
    private fun generateReceipt(booking: Booking, fareBreakdown: FareBreakdown): Receipt {
        val receiptNumber = "ISL${System.currentTimeMillis()}"
        
        return Receipt(
            receiptNumber = receiptNumber,
            pickupAddress = booking.pickupLocation.address,
            destinationAddress = booking.destination.address,
            distance = booking.route?.totalDistance ?: 0.0,
            duration = booking.route?.estimatedDuration ?: 0,
            driverName = "Driver", // TODO: Get actual driver name from user repository
            vehiclePlate = "ABC-1234", // TODO: Get actual vehicle info
            fareBreakdown = fareBreakdown
        )
    }
    
    /**
     * Process driver earnings - no commission, drivers keep 100% of fare
     */
    private suspend fun processDriverEarnings(payment: Payment) {
        try {
            val commission = 0.0  // No commission
            val netEarnings = payment.amount  // Driver keeps full amount

            // Create earnings transaction
            val transaction = EarningsTransaction(
                id = transactionsCollection.document().id,
                driverId = payment.driverId,
                bookingId = payment.bookingId,
                amount = payment.amount,
                commission = commission,
                netEarnings = netEarnings,
                status = EarningsStatus.PROCESSED
            )

            transactionsCollection.document(transaction.id).set(transaction).await()

            // Update driver earnings summary
            updateDriverEarningsSummary(payment.driverId, netEarnings)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing driver earnings", e)
        }
    }
    
    /**
     * Update driver earnings summary
     */
    private suspend fun updateDriverEarningsSummary(driverId: String, earnings: Double) {
        try {
            val earningsDoc = earningsCollection.document(driverId)
            val currentEarnings = earningsDoc.get().await().toObject(DriverEarnings::class.java)
                ?: DriverEarnings(driverId = driverId)
            
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val thisWeek = SimpleDateFormat("yyyy-'W'ww", Locale.getDefault()).format(Date())
            val thisMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            
            val updatedEarnings = currentEarnings.copy(
                totalEarnings = currentEarnings.totalEarnings + earnings,
                tripsCompleted = currentEarnings.tripsCompleted + 1,
                dailyEarnings = currentEarnings.dailyEarnings + (today to 
                    (currentEarnings.dailyEarnings[today] ?: 0.0) + earnings),
                weeklyEarnings = currentEarnings.weeklyEarnings + (thisWeek to 
                    (currentEarnings.weeklyEarnings[thisWeek] ?: 0.0) + earnings),
                monthlyEarnings = currentEarnings.monthlyEarnings + (thisMonth to 
                    (currentEarnings.monthlyEarnings[thisMonth] ?: 0.0) + earnings),
                pendingPayouts = currentEarnings.pendingPayouts + earnings
            )
            
            earningsDoc.set(updatedEarnings).await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating driver earnings summary", e)
        }
    }
    
    /**
     * Update booking payment status
     */
    private suspend fun updateBookingPaymentStatus(bookingId: String, status: BookingPaymentStatus) {
        try {
            firestore.collection("bookings").document(bookingId)
                .update("paymentStatus", status.name).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking payment status", e)
        }
    }
    
    /**
     * Get payment by booking ID
     */
    fun getPaymentByBookingId(bookingId: String): Flow<Payment?> = flow {
        try {
            val snapshot = paymentsCollection.whereEqualTo("bookingId", bookingId).get().await()
            val payment = snapshot.documents.firstOrNull()?.toObject(Payment::class.java)
            emit(payment)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment by booking ID", e)
            emit(null)
        }
    }
    
    /**
     * Get driver earnings
     */
    fun getDriverEarnings(driverId: String): Flow<DriverEarnings?> = flow {
        try {
            val snapshot = earningsCollection.document(driverId).get().await()
            val earnings = snapshot.toObject(DriverEarnings::class.java)
            emit(earnings)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting driver earnings", e)
            emit(null)
        }
    }
    
    /**
     * Get payment history for passenger
     */
    fun getPassengerPaymentHistory(passengerId: String): Flow<List<Payment>> = flow {
        try {
            val snapshot = paymentsCollection
                .whereEqualTo("passengerId", passengerId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()
            
            val payments = snapshot.documents.mapNotNull { 
                it.toObject(Payment::class.java) 
            }
            emit(payments)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting passenger payment history", e)
            emit(emptyList())
        }
    }
    
    /**
     * Get earnings transactions for driver
     */
    fun getDriverTransactions(driverId: String): Flow<List<EarningsTransaction>> = flow {
        try {
            val snapshot = transactionsCollection
                .whereEqualTo("driverId", driverId)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()
            
            val transactions = snapshot.documents.mapNotNull { 
                it.toObject(EarningsTransaction::class.java) 
            }
            emit(transactions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting driver transactions", e)
            emit(emptyList())
        }
    }
    
    /**
     * Process cash payment (mark as completed immediately)
     */
    suspend fun processCashPayment(booking: Booking): Result<Payment> {
        return processPayment(booking, PaymentMethod.CASH)
    }
    
    /**
     * Process digital payment (GCash, PayMaya, etc.)
     */
    suspend fun processDigitalPayment(
        booking: Booking,
        paymentMethod: PaymentMethod,
        transactionId: String
    ): Result<Payment> {
        return try {
            val result = processPayment(booking, paymentMethod)
            if (result.isSuccess) {
                val payment = result.getOrThrow()
                val updatedPayment = payment.copy(transactionId = transactionId)
                paymentsCollection.document(payment.id).set(updatedPayment).await()
                Result.success(updatedPayment)
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing digital payment", e)
            Result.failure(e)
        }
    }
}