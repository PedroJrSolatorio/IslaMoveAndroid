package com.rj.islamove.data.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMonitorService @Inject constructor(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val authService: AuthService
) {

    companion object {
        private const val TAG = "SessionMonitorService"
    }

    private var monitoringScope: CoroutineScope? = null
    private var isMonitoring = false

    /**
     * Start monitoring the current user's session
     */
    fun startSessionMonitoring(uid: String) {
        if (isMonitoring) {
            Log.d(TAG, "Session monitoring already active")
            return
        }

        Log.d(TAG, "Starting session monitoring for user: $uid")
        isMonitoring = true
        monitoringScope = CoroutineScope(SupervisorJob())

        monitoringScope?.launch {
            try {
                authRepository.monitorCurrentSession(uid).collectLatest { isActive ->
                    if (!isActive) {
                        Log.d(TAG, "Session deactivated, triggering global logout")
                        triggerGlobalLogout()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in session monitoring", e)
                stopSessionMonitoring()
            }
        }
    }

    /**
     * Stop monitoring sessions
     */
    fun stopSessionMonitoring() {
        if (!isMonitoring) return

        Log.d(TAG, "Stopping session monitoring")
        isMonitoring = false
        monitoringScope?.cancel()
        monitoringScope = null
    }

    /**
     * Trigger a global logout across the app
     */
    private fun triggerGlobalLogout() {
        authService.forceLogout()
        stopSessionMonitoring()
    }

    /**
     * Check if monitoring is active
     */
    fun isMonitoringActive(): Boolean = isMonitoring
}