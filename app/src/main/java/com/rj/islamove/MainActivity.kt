package com.rj.islamove

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.rj.islamove.data.repository.DriverRepository
import com.rj.islamove.data.services.SessionMonitorService
import com.rj.islamove.data.services.AuthService
import com.rj.islamove.ui.navigation.IslamoveNavigation
import com.rj.islamove.ui.navigation.Screen
import com.rj.islamove.ui.theme.IslamoveTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var driverRepository: DriverRepository

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var sessionMonitorService: SessionMonitorService

    @Inject
    lateinit var authService: AuthService

    private var logoutJob: Job? = null
    private var wasForceLoggedOut = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called, wasForceLoggedOut: $wasForceLoggedOut")

        try {
            enableEdgeToEdge()

            // Add global force logout listener
            authService.addForceLogoutListener {
                lifecycleScope.launch {
                    try {
                        Log.d(TAG, "Force logout triggered, stopping monitoring")
                        sessionMonitorService.stopSessionMonitoring()

                        // Set flag to prevent auto-login on recreate
                        wasForceLoggedOut = true

                        // DON'T use recreate() - instead finish the activity
                        // This prevents the infinite loop
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during force logout", e)
                    }
                }
            }

            setContent {
                IslamoveTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Start from Login screen if force logged out, otherwise Splash
                        val startDestination = if (wasForceLoggedOut) {
                            Screen.Login.route
                        } else {
                            Screen.Splash.route
                        }

                        IslamoveNavigation(startDestination = startDestination)
                    }
                }
            }

            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            Log.d(TAG, "onStart called")

            // Don't start session monitoring if we were force logged out
            if (wasForceLoggedOut) {
                Log.d(TAG, "Skipping session monitoring - force logged out")
                wasForceLoggedOut = false // Reset flag
                return
            }

            // Start session monitoring when activity starts (becomes visible)
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "User authenticated (${currentUser.uid}), starting session monitoring")
                sessionMonitorService.startSessionMonitoring(currentUser.uid)
            } else {
                Log.d(TAG, "No authenticated user found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart", e)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            Log.d(TAG, "onStop called, stopping session monitoring")
            sessionMonitorService.stopSessionMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStop", e)
        }
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "onDestroy called, isFinishing: $isFinishing")

            // Cancel any ongoing logout operations
            logoutJob?.cancel()

            // Only set driver offline when activity is actually destroyed (app terminated)
            if (isFinishing && !wasForceLoggedOut) {
                handleAppTerminated()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }

    private fun handleAppTerminated() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "App terminating - setting driver offline for: ${currentUser.uid}")

            // Launch coroutine but don't block - use GlobalScope for cleanup operations
            logoutJob = kotlinx.coroutines.GlobalScope.launch {
                try {
                    driverRepository.updateDriverStatus(online = false).onSuccess {
                        Log.d(TAG, "Successfully set driver offline on app termination")
                    }.onFailure { exception ->
                        Log.e(TAG, "Failed to set driver offline on app termination", exception)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting driver offline on app termination", e)
                }
            }
        }
    }
}