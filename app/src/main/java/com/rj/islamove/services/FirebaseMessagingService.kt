package com.rj.islamove.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCM"
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // TODO: Send token to server or save locally
        // This token is used to send push notifications to this specific app instance
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")
        
        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            // Handle data payload here
            // For example: ride request notifications, driver updates, etc.
            handleDataMessage(remoteMessage.data)
        }
        
        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message notification body: ${it.body}")
            
            // Handle notification payload here
            // The system automatically displays the notification if the app is in background
            // If the app is in foreground, you need to handle it manually
        }
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        val messageType = data["type"]
        
        when (messageType) {
            "ride_request" -> {
                // Handle ride request notification for drivers
                Log.d(TAG, "Ride request notification received")
                // TODO: Show notification or update UI
            }
            "ride_update" -> {
                // Handle ride status updates for passengers
                Log.d(TAG, "Ride update notification received")
                // TODO: Update ride status in UI
            }
            "driver_location" -> {
                // Handle driver location updates for passengers
                Log.d(TAG, "Driver location update received")
                // TODO: Update driver location on map
            }
            else -> {
                Log.d(TAG, "Unknown message type: $messageType")
            }
        }
    }
}