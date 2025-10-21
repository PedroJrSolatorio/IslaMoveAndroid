package com.rj.islamove.data.models

data class Payment(
    val id: String = "",
    val bookingId: String = "",
    val passengerId: String = "",
    val driverId: String = "",
    val amount: Double = 0.0,
    val currency: String = "PHP",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val transactionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val receipt: Receipt? = null,
    val breakdown: FareBreakdown = FareBreakdown()
)

data class FareBreakdown(
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val timeFare: Double = 0.0,
    val surgeFare: Double = 0.0,
    val discount: Double = 0.0,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val discountType: String? = null // "SENIOR", "PWD", "STUDENT"
)

data class Receipt(
    val receiptNumber: String = "",
    val issueDate: Long = System.currentTimeMillis(),
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val distance: Double = 0.0,
    val duration: Int = 0,
    val driverName: String = "",
    val vehiclePlate: String = "",
    val fareBreakdown: FareBreakdown = FareBreakdown(),
    val receiptUrl: String? = null // PDF stored in Firebase Storage
)

enum class PaymentMethod(val displayName: String) {
    CASH("Cash")
}


// Driver earnings model
data class DriverEarnings(
    val driverId: String = "",
    val totalEarnings: Double = 0.0,
    val tripsCompleted: Int = 0,
    val dailyEarnings: Map<String, Double> = emptyMap(), // date -> earnings
    val weeklyEarnings: Map<String, Double> = emptyMap(), // week -> earnings
    val monthlyEarnings: Map<String, Double> = emptyMap(), // month -> earnings
    val pendingPayouts: Double = 0.0,
    val lastPayoutDate: Long? = null,
    val currency: String = "PHP"
)

data class EarningsTransaction(
    val id: String = "",
    val driverId: String = "",
    val bookingId: String = "",
    val amount: Double = 0.0,
    val commission: Double = 0.0, // No commission - drivers keep 100%
    val netEarnings: Double = 0.0, // Equals amount (no commission deducted)
    val date: Long = System.currentTimeMillis(),
    val status: EarningsStatus = EarningsStatus.PENDING
)

enum class EarningsStatus {
    PENDING,
    PROCESSED,
    PAID_OUT
}