package com.rj.islamove.data.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientFCMService @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "ClientFCMService"
    }

    /**
     * Simple solution: Store notification in Firestore and rely on existing FCM client
     * This works without Cloud Functions - the receiving app will process the notification locally
     */
    suspend fun sendNotificationViaFirestore(
        recipientUserId: String,
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (tokens.isEmpty()) {
                return@withContext Result.failure(Exception("No FCM tokens provided"))
            }

            // Store notification data in Firestore
            // The receiving app will listen to this collection and show local notifications
            val notificationDoc = mapOf(
                "recipientId" to recipientUserId,
                "title" to title,
                "body" to body,
                "data" to data,
                "tokens" to tokens,
                "createdAt" to System.currentTimeMillis(),
                "processed" to false,
                "type" to (data["type"] ?: "general")
            )

            firestore.collection("pending_notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "✅ Stored notification in Firestore for user: $recipientUserId")
            Log.d(TAG, "📱 Recipient app will process this notification locally")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to store notification", e)
            Result.failure(e)
        }
    }
}