package com.rj.islamove.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.User
import com.rj.islamove.data.models.UserType
import com.rj.islamove.data.models.DriverData
import com.rj.islamove.data.models.VerificationStatus
import com.rj.islamove.data.models.StudentDocument
import com.rj.islamove.data.models.DocumentStatus
import com.rj.islamove.data.services.FCMTokenService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.provider.Settings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val fcmTokenService: FCMTokenService
) {

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    companion object {
        private const val SESSIONS_COLLECTION = "user_sessions"
    }

    /**
     * Get unique device ID for session tracking
     */
    private fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * Check if user has active sessions on other devices
     */
    suspend fun checkExistingSession(uid: String): Result<Boolean> {
        return try {
            val currentDeviceId = getDeviceId()
            val sessionsSnapshot = firestore.collection(SESSIONS_COLLECTION)
                .whereEqualTo("userId", uid)
                .whereEqualTo("active", true)
                .get()
                .await()

            // Check if there are active sessions on OTHER devices
            val hasOtherDeviceSession = sessionsSnapshot.documents.any { doc ->
                val deviceId = doc.getString("deviceId")
                deviceId != null && deviceId != currentDeviceId
            }

            Result.success(hasOtherDeviceSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create new session for current device
     */
    suspend fun createSession(uid: String) {
        try {
            val deviceId = getDeviceId()
            val sessionData = mapOf(
                "userId" to uid,
                "deviceId" to deviceId,
                "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                "loginTime" to System.currentTimeMillis(),
                "lastActive" to System.currentTimeMillis(),
                "active" to true
            )

            // Use device ID as document ID to ensure one session per device
            firestore.collection(SESSIONS_COLLECTION)
                .document("${uid}_${deviceId}")
                .set(sessionData)
                .await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to create session", e)
        }
    }

    /**
     * Force logout all other devices except current one
     */
    suspend fun forceLogoutOtherDevices(uid: String): Result<Unit> {
        return try {
            val currentDeviceId = getDeviceId()
            val sessionsSnapshot = firestore.collection(SESSIONS_COLLECTION)
                .whereEqualTo("userId", uid)
                .whereEqualTo("active", true)
                .get()
                .await()

            // Mark all OTHER device sessions as inactive
            val batch = firestore.batch()
            sessionsSnapshot.documents.forEach { doc ->
                val deviceId = doc.getString("deviceId")
                if (deviceId != null && deviceId != currentDeviceId) {
                    batch.update(doc.reference, mapOf(
                        "active" to false,
                        "logoutTime" to System.currentTimeMillis(),
                        "logoutReason" to "Logged in from another device"
                    ))
                }
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End current device session
     */
    private suspend fun endSession(uid: String) {
        try {
            val deviceId = getDeviceId()
            firestore.collection(SESSIONS_COLLECTION)
                .document("${uid}_${deviceId}")
                .update(mapOf(
                    "active" to false,
                    "logoutTime" to System.currentTimeMillis()
                ))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to end session", e)
        }
    }

    /**
     * Monitor current device session for real-time logout
     */
    fun monitorCurrentSession(uid: String): Flow<Boolean> = callbackFlow {
        val deviceId = getDeviceId()
        val sessionDocRef = firestore.collection(SESSIONS_COLLECTION)
            .document("${uid}_${deviceId}")

        val listener = sessionDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("AuthRepository", "Session monitoring error", error)
                trySend(false)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val isActive = snapshot.getBoolean("active") ?: true
                if (!isActive) {
                    android.util.Log.d("AuthRepository", "Session deactivated by another device")
                    trySend(false)
                    close()
                } else {
                    trySend(true)
                }
            } else {
                // Session doesn't exist, log out
                android.util.Log.d("AuthRepository", "Session document not found")
                trySend(false)
                close()
            }
        }

        // Send initial status
        trySend(true)

        // Clean up listener when flow is cancelled
        awaitClose {
            listener.remove()
        }
    }
    
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser
    
    fun isUserLoggedIn(): Boolean = getCurrentUser() != null
    
    suspend fun sendOtp(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    suspend fun verifyOtp(otp: String): Result<FirebaseUser> {
        return try {
            val verificationId = this.verificationId
                ?: return Result.failure(Exception("Verification ID is null"))

            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                // Check if user account is active and not deleted
                val accountStatus = isUserAccountActive(user.uid)
                if (accountStatus.isFailure || !accountStatus.getOrDefault(false)) {
                    // Sign out the user if account is inactive/deleted
                    firebaseAuth.signOut()
                    return Result.failure(Exception("Account is disabled or deleted. Please contact support."))
                }

                // Check if user exists in Firestore
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                var userData: User? = null
                if (userDoc.exists()) {
                    userData = userDoc.toObject(User::class.java)
                } else {
                    // Create new user document
                    val newUser = User(
                        uid = user.uid,
                        phoneNumber = user.phoneNumber ?: "",
                        displayName = user.displayName ?: "",
                        userType = UserType.PASSENGER,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    firestore.collection("users").document(user.uid).set(newUser).await()
                    userData = newUser
                }

                // For OTP, we'll create the session directly and let the caller handle multi-device logic
                // This is because OTP verification is typically used in a different flow
                createSession(user.uid)

                Result.success(user)
            } else {
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Invalid OTP"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                // Check if user account is active and not deleted
                val accountStatus = isUserAccountActive(user.uid)
                if (accountStatus.isFailure || !accountStatus.getOrDefault(false)) {
                    // Sign out the user if account is inactive/deleted
                    firebaseAuth.signOut()
                    return Result.failure(Exception("Account is disabled or deleted. Please contact support."))
                }

                // Check if user exists in Firestore, if not create new user
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                if (!userDoc.exists()) {
                    val newUser = User(
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName ?: "",
                        profileImageUrl = user.photoUrl?.toString(),
                        userType = UserType.PASSENGER,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    firestore.collection("users").document(user.uid).set(newUser).await()
                }

                // Create session for this device
                createSession(user.uid)

                // Update FCM token for notifications
                fcmTokenService.updateUserFCMToken()

                Result.success(user)
            } else {
                Result.failure(Exception("Google authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.1.4: Email authentication - Sign in existing user
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                // Check if user account is active and not deleted
                val accountStatus = isUserAccountActive(user.uid)
                if (accountStatus.isFailure || !accountStatus.getOrDefault(false)) {
                    // Sign out the user if account is inactive/deleted
                    firebaseAuth.signOut()
                    return Result.failure(Exception("Account is disabled or deleted. Please contact support."))
                }

                // Ensure user document exists in Firestore
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                if (!userDoc.exists()) {
                    // Create user document if it doesn't exist (edge case)
                    val newUser = User(
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName ?: "",
                        userType = UserType.PASSENGER,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    firestore.collection("users").document(user.uid).set(newUser).await()
                }

                // Create session for this device
                createSession(user.uid)

                // Update FCM token for notifications
                fcmTokenService.updateUserFCMToken()

                Result.success(user)
            } else {
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * FR-2.1.4: Email authentication - Create new user account
     */
    suspend fun createUserWithEmail(
        email: String,
        password: String,
        displayName: String = "",
        phoneNumber: String = "",
        userType: UserType = UserType.PASSENGER,
        dateOfBirth: String? = null,
        gender: String? = null,
        address: String? = null,
        idDocumentUrl: String? = null
    ): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                // Update Firebase Auth profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates).await()
                
                // Create user document in Firestore with proper role
                val newUser = User(
                    uid = user.uid,
                    email = user.email,
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    address = address,
                    userType = userType,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    // Initialize driver data if user is a driver
                    driverData = if (userType == UserType.DRIVER) {
                        DriverData(verificationStatus = VerificationStatus.PENDING)
                    } else null,
                    // Initialize common user data
                    dateOfBirth = dateOfBirth,
                    gender = gender,
                    // Initialize passenger-specific data
                    studentDocument = if (idDocumentUrl != null) {
                        StudentDocument(
                            studentIdUrl = idDocumentUrl,
                            studentIdNumber = "",
                            school = "",
                            status = DocumentStatus.PENDING_REVIEW,
                            uploadedAt = System.currentTimeMillis()
                        )
                    } else null
                )
                firestore.collection("users").document(user.uid).set(newUser).await()
                
                // Send email verification
                user.sendEmailVerification().await()
                
                Result.success(user)
            } else {
                Result.failure(Exception("User creation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserData(uid: String): Result<User> {
        return try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to parse user data"))
                }
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user account is active and not deleted
     */
    suspend fun isUserAccountActive(uid: String): Result<Boolean> {
        return try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val isActive = userDoc.getBoolean("isActive") ?: true
                val isDeleted = userDoc.getBoolean("isDeleted") ?: false
                Result.success(isActive && !isDeleted)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateUserType(uid: String, userType: UserType): Result<Unit> {
        return try {
            firestore.collection("users").document(uid)
                .update(
                    mapOf(
                        "userType" to userType,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            val currentUser = firebaseAuth.currentUser

            // End session for this device
            currentUser?.let { endSession(it.uid) }

            // Remove FCM token before signing out
            fcmTokenService.removeUserFCMToken()
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Callback class for OTP verification
    inner class OtpVerificationCallbacks(
        private val onVerificationCompleted: (PhoneAuthCredential) -> Unit,
        private val onVerificationFailed: (FirebaseException) -> Unit,
        private val onCodeSent: (String, PhoneAuthProvider.ForceResendingToken?) -> Unit
    ) : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            onVerificationCompleted.invoke(credential)
        }
        
        override fun onVerificationFailed(e: FirebaseException) {
            onVerificationFailed.invoke(e)
        }
        
        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            this@AuthRepository.verificationId = verificationId
            this@AuthRepository.resendToken = token
            onCodeSent.invoke(verificationId, token)
        }
    }
}