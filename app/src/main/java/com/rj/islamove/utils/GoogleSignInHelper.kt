package com.rj.islamove.utils

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.rj.islamove.R

/**
 * Helper class for Google Sign-In functionality
 * Implements FR-2.1.4: Enable Firebase social authentication (Google)
 */
class GoogleSignInHelper(private val context: Context) {
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Get the Google Sign-In client for starting sign-in flow
     */
    fun getSignInClient(): GoogleSignInClient = googleSignInClient
    
    /**
     * Get the signed-in Google account if available
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Sign out from Google account
     */
    fun signOut(): Task<Void> {
        return googleSignInClient.signOut()
    }
    
    /**
     * Revoke access to Google account
     */
    fun revokeAccess(): Task<Void> {
        return googleSignInClient.revokeAccess()
    }
    
    /**
     * Check if user is already signed in to Google
     */
    fun isSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }
}