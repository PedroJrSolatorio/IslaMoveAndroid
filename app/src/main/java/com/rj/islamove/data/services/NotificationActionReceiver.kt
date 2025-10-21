package com.rj.islamove.data.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NotificationActionReceiver : BroadcastReceiver() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val bookingId = intent.getStringExtra("booking_id") ?: return
        
        Log.d(TAG, "Received notification action: $action for booking: $bookingId")

        // Dismiss the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(bookingId.hashCode())

        when (action) {
            "ACCEPT_RIDE" -> {
                handleAcceptRide(bookingId)
            }
            "DECLINE_RIDE" -> {
                handleDeclineRide(bookingId)
            }
        }
    }

    private fun handleAcceptRide(bookingId: String) {
        Log.d(TAG, "Handling accept ride for booking: $bookingId")
        // TODO: Re-implement ride acceptance logic when dependency injection is working properly
        // For now, just log the action
        Log.d(TAG, "Accept ride action logged for booking: $bookingId")
    }

    private fun handleDeclineRide(bookingId: String) {
        Log.d(TAG, "Handling decline ride for booking: $bookingId")
        // TODO: Re-implement ride decline logic when dependency injection is working properly
        // For now, just log the action
        Log.d(TAG, "Decline ride action logged for booking: $bookingId")
    }
}