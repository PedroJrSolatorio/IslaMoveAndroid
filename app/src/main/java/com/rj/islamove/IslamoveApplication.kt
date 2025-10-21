package com.rj.islamove

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IslamoveApplication : Application() {

    companion object {
        lateinit var instance: IslamoveApplication
            private set
        private const val TAG = "IslamoveApplication"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            Log.d(TAG, "Application onCreate started")
            instance = this

            // Set global exception handler FIRST before anything else
            setupGlobalExceptionHandler()

            // Initialize Firebase
            Log.d(TAG, "Initializing Firebase...")
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")

            // Enable Firebase Realtime Database offline persistence
            Log.d(TAG, "Enabling Firebase persistence...")
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d(TAG, "Firebase persistence enabled successfully")

            Log.d(TAG, "Application onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Error in Application onCreate", e)
            // Don't crash, but log the error
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                Log.e(TAG, "===== UNCAUGHT EXCEPTION =====")
                Log.e(TAG, "Thread: ${thread.name}")
                Log.e(TAG, "Exception: ${exception.javaClass.simpleName}")
                Log.e(TAG, "Message: ${exception.message}")
                Log.e(TAG, "Stack trace:", exception)
                Log.e(TAG, "==============================")

                // Call the default handler to maintain normal crash behavior
                // but we've logged it first
                defaultHandler?.uncaughtException(thread, exception)
            } catch (e: Exception) {
                // If logging fails, still call default handler
                Log.e(TAG, "Error in exception handler", e)
                defaultHandler?.uncaughtException(thread, exception)
            }
        }

        Log.d(TAG, "Global exception handler set up successfully")
    }
}