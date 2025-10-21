package com.rj.islamove.data.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FCMTokenService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {

    companion object {
        private const val TAG = "FCMTokenService"
        private const val USERS_COLLECTION = "users"
        private const val FCM_TOKENS_FIELD = "fcmTokens"
    }

    /**
     * Get current FCM token and save to user document
     */
    suspend fun updateUserFCMToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.failure(Exception("User not authenticated"))
            }

            // Get current FCM token
            val token = messaging.token.await()
            Log.d(TAG, "Retrieved FCM token: $token")

            // Save token to user document in Firestore
            val userRef = firestore.collection(USERS_COLLECTION).document(currentUser.uid)
            
            // Get existing tokens or create new array
            val userDoc = userRef.get().await()
            val existingTokens = userDoc.get(FCM_TOKENS_FIELD) as? List<String> ?: emptyList()
            
            // Add new token if not already present
            val updatedTokens = if (token !in existingTokens) {
                existingTokens + token
            } else {
                existingTokens
            }

            // Update user document with new tokens
            userRef.update(mapOf(
                FCM_TOKENS_FIELD to updatedTokens,
                "lastTokenUpdate" to System.currentTimeMillis()
            )).await()

            Log.d(TAG, "Successfully updated FCM token for user: ${currentUser.uid}")
            Result.success(token)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Remove current FCM token from user document (for logout)
     */
    suspend fun removeUserFCMToken(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.success(Unit)
            }

            val token = messaging.token.await()
            val userRef = firestore.collection(USERS_COLLECTION).document(currentUser.uid)
            
            // Get existing tokens
            val userDoc = userRef.get().await()
            val existingTokens = userDoc.get(FCM_TOKENS_FIELD) as? List<String> ?: emptyList()
            
            // Remove current token
            val updatedTokens = existingTokens.filter { it != token }

            // Update user document
            userRef.update(mapOf(
                FCM_TOKENS_FIELD to updatedTokens,
                "lastTokenUpdate" to System.currentTimeMillis()
            )).await()

            Log.d(TAG, "Successfully removed FCM token for user: ${currentUser.uid}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Subscribe to topic based on user role and location
     */
    suspend fun subscribeToTopics(userRole: String, city: String = "san_jose"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Subscribe to role-based topic
            messaging.subscribeToTopic("${userRole}_$city").await()
            Log.d(TAG, "Subscribed to topic: ${userRole}_$city")

            // Subscribe to general notifications
            messaging.subscribeToTopic("general_$city").await()
            Log.d(TAG, "Subscribed to topic: general_$city")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to topics", e)
            Result.failure(e)
        }
    }

    /**
     * Unsubscribe from topics (for role change or logout)
     */
    suspend fun unsubscribeFromTopics(userRole: String, city: String = "san_jose"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            messaging.unsubscribeFromTopic("${userRole}_$city").await()
            messaging.unsubscribeFromTopic("general_$city").await()
            
            Log.d(TAG, "Unsubscribed from topics for role: $userRole")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from topics", e)
            Result.failure(e)
        }
    }

    /**
     * Get user's FCM tokens for sending targeted notifications
     */
    suspend fun getUserFCMTokens(userId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val userDoc = firestore.collection(USERS_COLLECTION).document(userId).get().await()
            val tokens = userDoc.get(FCM_TOKENS_FIELD) as? List<String> ?: emptyList()
            
            Log.d(TAG, "Retrieved ${tokens.size} FCM tokens for user: $userId")
            Result.success(tokens)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM tokens for user: $userId", e)
            Result.failure(e)
        }
    }
}