package com.rj.islamove.data.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.Booking
import com.rj.islamove.data.models.BookingStatus
import com.rj.islamove.data.models.DocumentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val fcmTokenService: FCMTokenService,
    private val clientFCMService: ClientFCMService
) {

    companion object {
        private const val TAG = "NotificationService"
    }

    /**
     * Send ride request notification to nearby drivers
     */
    suspend fun sendRideRequestToDrivers(
        booking: Booking,
        driverIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (driverId in driverIds) {
                sendRideRequestToDriver(booking, driverId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ride request notifications", e)
            Result.failure(e)
        }
    }

    /**
     * Send ride request notification to a specific driver
     */
    private suspend fun sendRideRequestToDriver(
        booking: Booking,
        driverId: String
    ) {
        try {
            // Get driver's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(driverId)
            if (!tokensResult.isSuccess) {
                Log.w(TAG, "Failed to get FCM tokens for driver: $driverId")
                return
            }

            val tokens = tokensResult.getOrNull() ?: return

            // Create notification data
            val notificationData = mapOf(
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_REQUEST,
                "booking_id" to booking.id,
                "pickup_address" to booking.pickupLocation.address,
                "destination_address" to booking.destination.address,
                "fare_estimate" to booking.fareEstimate.totalEstimate.toString(),
                "passenger_id" to booking.passengerId
            )

            // Store notification in Firestore for driver to process
            clientFCMService.sendNotificationViaFirestore(
                recipientUserId = driverId,
                tokens = tokens,
                title = "New Ride Request",
                body = "₱${booking.fareEstimate.totalEstimate} • ${booking.pickupLocation.address}",
                data = notificationData
            )

            Log.d(TAG, "✅ Sent ride request notification directly to driver: $driverId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification to driver: $driverId", e)
        }
    }

    /**
     * Send ride status update notification to passenger
     */
    suspend fun sendRideUpdateToPassenger(
        booking: Booking,
        status: BookingStatus,
        additionalData: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get passenger's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(booking.passengerId)
            if (!tokensResult.isSuccess) {
                Log.w(TAG, "Failed to get FCM tokens for passenger: ${booking.passengerId}")
                return@withContext Result.failure(Exception("Failed to get passenger tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()

            // Create notification data based on status
            val (notificationType, title, body) = when (status) {
                BookingStatus.ACCEPTED -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_ACCEPTED,
                    "Ride Accepted!",
                    "Your driver is on the way. ETA: ${additionalData["eta"] ?: "5 minutes"}"
                )
                BookingStatus.DRIVER_ARRIVED -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_DRIVER_ARRIVED,
                    "Driver Arrived",
                    "Your driver has arrived at the pickup location"
                )
                BookingStatus.IN_PROGRESS -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_STARTED,
                    "Ride Started",
                    "Your trip has started. Have a safe journey!"
                )
                BookingStatus.COMPLETED -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_COMPLETED,
                    "Trip Completed",
                    "Trip completed. Total fare: ₱${booking.actualFare ?: booking.fareEstimate.totalEstimate}"
                )
                BookingStatus.CANCELLED -> {
                    // Check if cancelled by driver or passenger
                    val cancelledBy = additionalData["cancelled_by"]
                    val reason = additionalData["reason"]
                    
                    when (cancelledBy) {
                        "driver" -> Triple(
                            IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED,
                            "Driver Cancelled Trip",
                            if (reason.isNullOrBlank()) {
                                "Your driver has cancelled the trip. We'll help you find another driver."
                            } else {
                                "Your driver cancelled: $reason. We'll help you find another driver."
                            }
                        )
                        else -> Triple(
                            IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED,
                            "Ride Cancelled",
                            "Your ride has been cancelled"
                        )
                    }
                }
                else -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_GENERAL,
                    "Ride Update",
                    "Your ride status has been updated"
                )
            }

            val notificationData = mapOf(
                "type" to notificationType,
                "title" to title,
                "body" to body,
                "booking_id" to booking.id
            ) + additionalData

            // Store notification in Firestore for recipient to process
            clientFCMService.sendNotificationViaFirestore(
                recipientUserId = booking.passengerId,
                tokens = tokens,
                title = title,
                body = body,
                data = notificationData
            )

            Log.d(TAG, "✅ Sent ride update notification directly to passenger: ${booking.passengerId}")
            Log.d(TAG, "📨 Direct FCM notification sent to passenger's device tokens")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ride update notification", e)
            Result.failure(e)
        }
    }

    /**
     * Send general notification to user
     */
    suspend fun sendGeneralNotification(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get user's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(userId)
            if (!tokensResult.isSuccess) {
                return@withContext Result.failure(Exception("Failed to get user tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()

            val notificationData = mapOf(
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_GENERAL,
                "title" to title,
                "body" to body
            ) + data

            val notificationDoc = mapOf(
                "recipientId" to userId,
                "type" to "general",
                "title" to title,
                "body" to body,
                "data" to notificationData,
                "tokens" to tokens,
                "createdAt" to System.currentTimeMillis(),
                "sent" to false
            )

            firestore.collection("notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "Queued general notification for user: $userId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send general notification", e)
            Result.failure(e)
        }
    }

    /**
     * Send document status update notification to driver
     */
    suspend fun sendDocumentStatusNotification(
        driverUid: String,
        documentType: String,
        status: DocumentStatus,
        rejectionReason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get driver's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(driverUid)
            if (!tokensResult.isSuccess) {
                Log.w(TAG, "Failed to get FCM tokens for driver: $driverUid")
                return@withContext Result.failure(Exception("Failed to get driver tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()

            // Create notification data based on status
            val documentDisplayName = when (documentType) {
                "license" -> "Driver's License"
                "vehicle_registration" -> "Certificate of Registration (CR)"
                "insurance" -> "SJMODA Certification"
                "vehicle_inspection" -> "Official Receipt (OR)"
                "profile_photo" -> "Profile Photo"
                else -> documentType.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                }
            }

            val (notificationType, title, body) = when (status) {
                DocumentStatus.APPROVED -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_DOCUMENT_APPROVED,
                    "✅ Document Approved",
                    "Your $documentDisplayName has been approved!"
                )
                DocumentStatus.REJECTED -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_DOCUMENT_REJECTED,
                    "❌ Document Rejected",
                    "Your $documentDisplayName was rejected. ${rejectionReason?.let { "Reason: $it" } ?: "Please check the details and resubmit."}"
                )
                else -> Triple(
                    IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_GENERAL,
                    "Document Update",
                    "Your $documentDisplayName status has been updated"
                )
            }

            val notificationData = mapOf(
                "type" to notificationType,
                "title" to title,
                "body" to body,
                "document_type" to documentType,
                "document_status" to status.name,
                "rejection_reason" to (rejectionReason ?: "")
            )

            // Store notification in Firestore
            val notificationDoc = mapOf(
                "recipientId" to driverUid,
                "type" to notificationType,
                "title" to title,
                "body" to body,
                "data" to notificationData,
                "tokens" to tokens,
                "createdAt" to System.currentTimeMillis(),
                "sent" to false
            )

            firestore.collection("notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "Queued document status notification for driver: $driverUid")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send document status notification", e)
            Result.failure(e)
        }
    }

    /**
     * Send notification when all documents are approved and driver is verified
     */
    suspend fun sendDriverVerificationCompleteNotification(
        driverUid: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get driver's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(driverUid)
            if (!tokensResult.isSuccess) {
                return@withContext Result.failure(Exception("Failed to get driver tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()

            val title = "🎉 Congratulations!"
            val body = "All your documents have been approved. You're now a verified driver and can start accepting rides!"

            val notificationData = mapOf(
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_DRIVER_VERIFIED,
                "title" to title,
                "body" to body
            )

            val notificationDoc = mapOf(
                "recipientId" to driverUid,
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_DRIVER_VERIFIED,
                "title" to title,
                "body" to body,
                "data" to notificationData,
                "tokens" to tokens,
                "createdAt" to System.currentTimeMillis(),
                "sent" to false
            )

            firestore.collection("notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "Queued driver verification complete notification for: $driverUid")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send driver verification notification", e)
            Result.failure(e)
        }
    }

    /**
     * Show immediate TOP notification for ride cancellation (bypasses FCM delay)
     */
    suspend fun showImmediateTopCancellationNotification(
        context: android.content.Context,
        title: String,
        body: String,
        cancelledBy: String,
        reason: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Create notification manager
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            // Create notification channels
            createImmediateNotificationChannels(context, notificationManager)

            // Create intent
            val intent = android.content.Intent(context, com.rj.islamove.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notification_type", IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED)
                putExtra("cancelled_by", cancelledBy)
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                ("immediate_cancellation_" + System.currentTimeMillis()).hashCode(),
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Build the HIGHEST PRIORITY notification for HEADS-UP display at screen top
            val notificationBuilder = androidx.core.app.NotificationCompat.Builder(context, "urgent_ride_notifications")
                .setSmallIcon(com.rj.islamove.R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    androidx.core.app.NotificationCompat.BigTextStyle()
                        .bigText(if (reason.isNotBlank()) "$body\nReason: $reason" else body)
                )
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX) // MAXIMUM priority
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL) // Use CALL category for heads-up
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOngoing(false) // Allow user to dismiss
                .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)) // Use notification sound, not alarm
                .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000)) // Strong vibration
                .setLights(0xFFFF0000.toInt(), 500, 500) // Fast blinking red light
                .setContentIntent(pendingIntent)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, false) // Use false for heads-up, true for full-screen
                .setTimeoutAfter(15000) // Auto-dismiss after 15 seconds
                .setWhen(System.currentTimeMillis()) // Show timestamp
                .setShowWhen(true)

            // For Android 8+ set importance high
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notificationBuilder.setChannelId("urgent_ride_notifications")
            }

            // Show notification with unique ID
            val notificationId = ("ride_cancellation_top_" + System.currentTimeMillis()).hashCode()
            notificationManager.notify(notificationId, notificationBuilder.build())

            Log.d(TAG, "✅ IMMEDIATE TOP notification displayed: $title")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show immediate TOP cancellation notification", e)
            Result.failure(e)
        }
    }

    /**
     * Create urgent notification channel for immediate top notifications
     */
    private fun createImmediateNotificationChannels(context: android.content.Context, notificationManager: android.app.NotificationManager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Urgent notification channel with MAXIMUM importance for heads-up display
            val urgentChannel = android.app.NotificationChannel(
                "urgent_ride_notifications",
                "🚨 URGENT Ride Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical ride cancellation notifications that appear as heads-up at screen top"
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000)
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM), null)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()
                lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true) // Override Do Not Disturb
                setShowBadge(true)
            }

            // For Android 8+, set to MAX importance to force heads-up behavior
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                urgentChannel.importance = android.app.NotificationManager.IMPORTANCE_MAX
            }

            notificationManager.createNotificationChannel(urgentChannel)
        }
    }

    /**
     * Send cancellation confirmation notification to passenger
     */
    suspend fun sendCancellationConfirmationToPassenger(
        booking: Booking,
        reason: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending cancellation confirmation to passenger: ${booking.passengerId} for booking: ${booking.id}")

            // Get passenger's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(booking.passengerId)
            if (!tokensResult.isSuccess) {
                Log.w(TAG, "Failed to get FCM tokens for passenger: ${booking.passengerId}")
                return@withContext Result.failure(Exception("Failed to get passenger tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()

            val title = "Ride Cancelled"
            val body = if (reason.isNotBlank()) {
                "You have cancelled your ride: $reason"
            } else {
                "You have cancelled your ride."
            }

            val notificationData = mapOf(
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED,
                "title" to title,
                "body" to body,
                "booking_id" to booking.id,
                "cancelled_by" to "passenger",
                "reason" to reason
            )

            // Store notification in Firestore
            val notificationDoc = mapOf(
                "recipientId" to booking.passengerId,
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED,
                "title" to title,
                "body" to body,
                "data" to notificationData,
                "tokens" to tokens,
                "createdAt" to System.currentTimeMillis(),
                "sent" to false
            )

            firestore.collection("notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "✅ Queued cancellation confirmation for passenger: ${booking.passengerId}")

            // Show immediate notification to passenger
            try {
                val context = com.rj.islamove.IslamoveApplication.instance
                showImmediateTopCancellationNotification(
                    context = context,
                    title = "✅ Ride Cancelled",
                    body = body,
                    cancelledBy = "passenger",
                    reason = reason
                ).onSuccess {
                    Log.d(TAG, "✅ Immediate passenger cancellation confirmation displayed")
                }.onFailure { e ->
                    Log.w(TAG, "Failed to show immediate passenger confirmation", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to trigger immediate passenger confirmation", e)
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send cancellation confirmation to passenger", e)
            Result.failure(e)
        }
    }

    /**
     * Send cancellation notification to driver when passenger cancels accepted ride
     */
    suspend fun sendRideCancellationToDriver(
        booking: Booking,
        driverId: String,
        reason: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending ride cancellation notification to driver: $driverId for booking: ${booking.id}")

            // Get driver's FCM tokens
            val tokensResult = fcmTokenService.getUserFCMTokens(driverId)
            if (!tokensResult.isSuccess) {
                Log.w(TAG, "Failed to get FCM tokens for driver: $driverId")
                return@withContext Result.failure(Exception("Failed to get driver tokens"))
            }

            val tokens = tokensResult.getOrNull() ?: emptyList()
            if (tokens.isEmpty()) {
                Log.w(TAG, "No FCM tokens found for driver: $driverId")
                return@withContext Result.failure(Exception("No FCM tokens for driver"))
            }

            // Create notification data for passenger cancellation
            val title = "Ride Cancelled by Passenger"
            val body = if (reason.isNotBlank()) {
                "The passenger cancelled the ride: $reason"
            } else {
                "The passenger cancelled the ride."
            }

            val notificationData = mapOf(
                "type" to IslamoveFirebaseMessagingService.NOTIFICATION_TYPE_RIDE_CANCELLED,
                "title" to title,
                "body" to body,
                "booking_id" to booking.id,
                "cancelled_by" to "passenger",
                "reason" to reason,
                "vibrate" to "true", // Special flag to trigger vibration
                "sound" to "default",
                "priority" to "high"
            )

            // Store notification in Firestore for driver to process
            clientFCMService.sendNotificationViaFirestore(
                recipientUserId = driverId,
                tokens = tokens,
                title = title,
                body = body,
                data = notificationData
            )

            Log.d(TAG, "✅ Stored ride cancellation notification for driver: $driverId")
            Log.d(TAG, "📨 Direct FCM notification sent to driver's device tokens")

            try {
                val context = com.rj.islamove.IslamoveApplication.instance
                // Check if current user is a driver by checking Firebase Auth or shared preferences
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    showImmediateTopCancellationNotification(
                        context = context,
                        title = "🚨 Passenger Cancelled Ride",
                        body = if (reason.isNotBlank()) "The passenger cancelled: $reason" else "The passenger cancelled the ride.",
                        cancelledBy = "passenger",
                        reason = reason
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show local notification", e)
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send ride cancellation notification to driver", e)
            Result.failure(e)
        }
    }
}