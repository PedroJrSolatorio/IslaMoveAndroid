package com.rj.islamove.data.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "AuthService"
        var forceLogoutListeners = mutableListOf<() -> Unit>()
    }

    /**
     * Force logout the current user and notify all listeners
     */
    fun forceLogout() {
        Log.d(TAG, "Force logout triggered")

        // Sign out from Firebase
        CoroutineScope(Dispatchers.IO).launch {
            try {
                authRepository.signOut()
                Log.d(TAG, "Firebase sign out completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during Firebase sign out", e)
            }
        }

        // Notify all listeners
        forceLogoutListeners.forEach { listener ->
            try {
                listener()
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying logout listener", e)
            }
        }

        // Clear listeners to prevent memory leaks
        forceLogoutListeners.clear()
    }

    /**
     * Add a listener for force logout events
     */
    fun addForceLogoutListener(listener: () -> Unit) {
        if (!forceLogoutListeners.contains(listener)) {
            forceLogoutListeners.add(listener)
        }
    }

    /**
     * Remove a force logout listener
     */
    fun removeForceLogoutListener(listener: () -> Unit) {
        forceLogoutListeners.remove(listener)
    }

    /**
     * Check if user is currently authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}