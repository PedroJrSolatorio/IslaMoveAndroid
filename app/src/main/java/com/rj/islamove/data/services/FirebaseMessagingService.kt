package com.rj.islamove.data.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rj.islamove.MainActivity
import com.rj.islamove.R

class IslamoveFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "islamove_notifications"
        private const val RIDE_REQUEST_CHANNEL_ID = "ride_requests"
        private const val RIDE_UPDATE_CHANNEL_ID = "ride_updates"
        private const val GENERAL_CHANNEL_ID = "general_notifications"
        
        // Notification types
        const val NOTIFICATION_TYPE_RIDE_REQUEST = "ride_request"
        const val NOTIFICATION_TYPE_RIDE_ACCEPTED = "ride_accepted"
        const val NOTIFICATION_TYPE_DRIVER_ARRIVED = "driver_arrived"
        const val NOTIFICATION_TYPE_RIDE_STARTED = "ride_started"
        const val NOTIFICATION_TYPE_RIDE_COMPLETED = "ride_completed"
        const val NOTIFICATION_TYPE_RIDE_CANCELLED = "ride_cancelled"
        const val NOTIFICATION_TYPE_GENERAL = "general"

        // Document verification notification types
        const val NOTIFICATION_TYPE_DOCUMENT_APPROVED = "document_approved"
        const val NOTIFICATION_TYPE_DOCUMENT_REJECTED = "document_rejected"
        const val NOTIFICATION_TYPE_ALL_DOCUMENTS_APPROVED = "all_documents_approved"
        const val NOTIFICATION_TYPE_DRIVER_VERIFIED = "driver_verified"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // Send token to server to store for this user
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            handleNotificationMessage(it, remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val notificationType = data["type"] ?: NOTIFICATION_TYPE_GENERAL
        val title = data["title"] ?: "IslaMove"
        val body = data["body"] ?: "New notification"
        
        when (notificationType) {
            NOTIFICATION_TYPE_RIDE_REQUEST -> {
                val bookingId = data["booking_id"] ?: ""
                val pickupAddress = data["pickup_address"] ?: ""
                val destinationAddress = data["destination_address"] ?: ""
                val fareEstimate = data["fare_estimate"] ?: "0"
                
                showRideRequestNotification(
                    bookingId = bookingId,
                    pickupAddress = pickupAddress,
                    destinationAddress = destinationAddress,
                    fareEstimate = fareEstimate
                )
            }
            NOTIFICATION_TYPE_RIDE_ACCEPTED -> {
                val driverName = data["driver_name"] ?: "Driver"
                val vehicleDetails = data["vehicle_details"] ?: ""
                val eta = data["eta"] ?: "5 minutes"

                showRideUpdateNotification(
                    title = "Ride Accepted!",
                    body = "$driverName accepted your ride. ETA: $eta",
                    channelId = RIDE_REQUEST_CHANNEL_ID,  // Use high priority channel for important updates
                    isHighPriority = true
                )
            }
            NOTIFICATION_TYPE_DRIVER_ARRIVED -> {
                showRideUpdateNotification(
                    title = "Driver Arrived",
                    body = "Your driver has arrived at the pickup location",
                    channelId = RIDE_REQUEST_CHANNEL_ID,  // Use high priority channel
                    isHighPriority = true
                )
            }
            NOTIFICATION_TYPE_RIDE_STARTED -> {
                showRideUpdateNotification(
                    title = "Ride Started",
                    body = "Your ride to destination has started",
                    channelId = RIDE_UPDATE_CHANNEL_ID
                )
            }
            NOTIFICATION_TYPE_RIDE_COMPLETED -> {
                val totalFare = data["total_fare"] ?: "0"
                showRideUpdateNotification(
                    title = "Ride Completed",
                    body = "Trip completed. Total fare: ₱$totalFare",
                    channelId = RIDE_UPDATE_CHANNEL_ID
                )
            }
            NOTIFICATION_TYPE_RIDE_CANCELLED -> {
                val cancelledBy = data["cancelled_by"] ?: ""
                val reason = data["reason"] ?: ""
                val title = data["title"] ?: "Ride Cancelled"
                val body = data["body"] ?: "Ride was cancelled"

                // Always show cancellation notifications at the top with high priority
                showRideCancellationNotification(
                    title = title,
                    body = body,
                    cancelledBy = cancelledBy,
                    reason = reason
                )
            }
            else -> {
                showGeneralNotification(title, body)
            }
        }
    }

    private fun handleNotificationMessage(notification: RemoteMessage.Notification, data: Map<String, String>) {
        val title = notification.title ?: "IslaMove"
        val body = notification.body ?: "New notification"
        val notificationType = data["type"] ?: NOTIFICATION_TYPE_GENERAL
        
        when (notificationType) {
            NOTIFICATION_TYPE_RIDE_REQUEST -> {
                showRideRequestNotification(
                    bookingId = data["booking_id"] ?: "",
                    pickupAddress = data["pickup_address"] ?: "",
                    destinationAddress = data["destination_address"] ?: "",
                    fareEstimate = data["fare_estimate"] ?: "0"
                )
            }
            else -> {
                showGeneralNotification(title, body)
            }
        }
    }

    private fun showRideRequestNotification(
        bookingId: String,
        pickupAddress: String,
        destinationAddress: String,
        fareEstimate: String
    ) {
        createNotificationChannels()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", NOTIFICATION_TYPE_RIDE_REQUEST)
            putExtra("booking_id", bookingId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            bookingId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Accept action
        val acceptIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "ACCEPT_RIDE"
            putExtra("booking_id", bookingId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this,
            (bookingId + "_accept").hashCode(),
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Decline action
        val declineIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "DECLINE_RIDE"
            putExtra("booking_id", bookingId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            (bookingId + "_decline").hashCode(),
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, RIDE_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Ride Request")
            .setContentText("₱$fareEstimate • $pickupAddress")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Pickup: $pickupAddress\nDestination: $destinationAddress\nFare: ₱$fareEstimate")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(1000, 1000, 1000))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Decline", declinePendingIntent)
            .setTimeoutAfter(30000) // Auto-dismiss after 30 seconds

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(bookingId.hashCode(), notificationBuilder.build())
    }

    private fun showRideUpdateNotification(
        title: String,
        body: String,
        channelId: String = RIDE_UPDATE_CHANNEL_ID,
        isHighPriority: Boolean = false
    ) {
        createNotificationChannels()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isHighPriority) {
            notificationBuilder
                .setCategory(NotificationCompat.CATEGORY_CALL)  // Force heads-up display
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
                .setFullScreenIntent(pendingIntent, false)  // Enable heads-up
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun showRideCancellationNotification(
        title: String,
        body: String,
        cancelledBy: String,
        reason: String
    ) {
        createNotificationChannels()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", NOTIFICATION_TYPE_RIDE_CANCELLED)
            putExtra("cancelled_by", cancelledBy)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            ("cancellation_" + System.currentTimeMillis()).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build HEADS-UP notification that appears at screen top
        val notificationBuilder = NotificationCompat.Builder(this, RIDE_REQUEST_CHANNEL_ID) // Use high-priority channel
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 $title") // Add urgent emoji
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (reason.isNotBlank()) "$body\nReason: $reason" else body)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX) // Maximum priority for heads-up display
            .setCategory(NotificationCompat.CATEGORY_CALL) // Use CALL category to force heads-up
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Use all default behaviors
            .setAutoCancel(true)
            .setOngoing(false) // Allow dismissal
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000)) // Strong vibration pattern
            .setLights(0xFFFF0000.toInt(), 500, 500) // Fast blinking red light
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .setFullScreenIntent(pendingIntent, false) // Enable heads-up notification
            .setWhen(System.currentTimeMillis()) // Current timestamp
            .setShowWhen(true) // Show timestamp
            .setTimeoutAfter(15000) // Auto-dismiss after 15 seconds

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Use a fixed ID so cancellation notifications replace each other
        val notificationId = "ride_cancellation".hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Showed high-priority cancellation notification: $title - $body")
    }

    private fun showGeneralNotification(title: String, body: String) {
        createNotificationChannels()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, GENERAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ride Request Channel (MAXIMUM importance for heads-up display)
            val rideRequestChannel = NotificationChannel(
                RIDE_REQUEST_CHANNEL_ID,
                "🚨 Urgent Ride Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical ride requests and cancellations that appear as heads-up notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt() // Red light for urgent notifications
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true) // Override Do Not Disturb for urgent notifications
                setShowBadge(true)

                // Force maximum importance for heads-up behavior
                importance = NotificationManager.IMPORTANCE_HIGH
            }

            // Ride Update Channel (Default priority)
            val rideUpdateChannel = NotificationChannel(
                RIDE_UPDATE_CHANNEL_ID,
                "Ride Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for ride status updates"
            }

            // General Channel (Low priority)
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(rideRequestChannel, rideUpdateChannel, generalChannel)
            )
        }
    }

    private fun sendRegistrationToServer(token: String) {
        Log.d(TAG, "Sending FCM token to server: $token")
        // TODO: Implement server call to save FCM token for current user
        // This would typically involve calling your backend API to store the token
        // associated with the current user's ID
    }
}