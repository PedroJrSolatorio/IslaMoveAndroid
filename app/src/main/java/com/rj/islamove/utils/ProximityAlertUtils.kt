package com.rj.islamove.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.R
import kotlin.math.*

/**
 * Utility class for handling proximity alerts when driver approaches pickup location
 */
class ProximityAlertUtils(private val context: Context) {

    companion object {
        private const val TAG = "ProximityAlert"
        // Distance thresholds in meters
        const val PROXIMITY_THRESHOLD_METERS = 100.0 // Alert when within 100 meters
        const val FINAL_APPROACH_THRESHOLD_METERS = 50.0 // Final approach alert

        // Vibration patterns (in milliseconds: delay, vibrate, pause, vibrate...)
        private val PROXIMITY_VIBRATION_PATTERN = longArrayOf(0, 200, 150, 200, 150, 200) // Three short vibrations
        private val FINAL_APPROACH_VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400) // Three longer vibrations
    }

    private var soundPool: SoundPool? = null
    private var proximityAlertSoundId: Int = -1
    private var finalApproachSoundId: Int = -1
    private var isInitialized = false

    // Track alert states to avoid spamming
    private var hasTriggeredProximityAlert = false
    private var hasTriggeredFinalApproachAlert = false
    private var lastAlertTime = 0L
    private val alertCooldownMs = 10000L // 10 seconds cooldown between alerts

    init {
        initializeSoundPool()
    }

    private fun initializeSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    Log.d(TAG, "Sound loaded successfully: $sampleId")
                    isInitialized = true
                } else {
                    Log.e(TAG, "Failed to load sound: $sampleId, status: $status")
                }
            }

            // Load proximity alert sounds from raw resources
            try {
                proximityAlertSoundId = soundPool?.load(context, R.raw.proximity_alert, 1) ?: -1
                finalApproachSoundId = soundPool?.load(context, R.raw.final_approach_alert, 1) ?: -1
            } catch (e: Exception) {
                Log.w(TAG, "Could not load custom alert sounds, using fallback", e)
                // Use system defaults as fallback
                proximityAlertSoundId = 1
                finalApproachSoundId = 1
                isInitialized = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool", e)
        }
    }

    /**
     * Calculate distance between two coordinates in meters
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Check if driver is approaching pickup and trigger appropriate alerts
     */
    fun checkProximityAndAlert(
        driverLocation: GeoPoint,
        pickupLocation: GeoPoint
    ) {
        val distance = calculateDistance(
            driverLocation.latitude, driverLocation.longitude,
            pickupLocation.latitude, pickupLocation.longitude
        )

        Log.i(TAG, "📍 PROXIMITY CHECK:")
        Log.i(TAG, "   📱 Driver: ${driverLocation.latitude}, ${driverLocation.longitude}")
        Log.i(TAG, "   🎯 Pickup: ${pickupLocation.latitude}, ${pickupLocation.longitude}")
        Log.i(TAG, "   📏 Distance: ${String.format("%.1f", distance)}m")
        Log.i(TAG, "   🚨 Proximity triggered: $hasTriggeredProximityAlert")
        Log.i(TAG, "   🔥 Final approach triggered: $hasTriggeredFinalApproachAlert")

        val currentTime = System.currentTimeMillis()

        // Check if we're in cooldown period
        if (currentTime - lastAlertTime < alertCooldownMs) {
            Log.d(TAG, "   ⏳ In cooldown period (${(currentTime - lastAlertTime) / 1000}s ago)")
            return
        }

        when {
            distance <= FINAL_APPROACH_THRESHOLD_METERS && !hasTriggeredFinalApproachAlert -> {
                Log.i(TAG, "🔥 FINAL APPROACH ALERT TRIGGERED! (${String.format("%.1f", distance)}m <= ${FINAL_APPROACH_THRESHOLD_METERS}m)")
                triggerFinalApproachAlert()
                hasTriggeredFinalApproachAlert = true
                hasTriggeredProximityAlert = true // Also mark proximity as triggered
                lastAlertTime = currentTime
            }
            distance <= PROXIMITY_THRESHOLD_METERS && !hasTriggeredProximityAlert -> {
                Log.i(TAG, "🚨 PROXIMITY ALERT TRIGGERED! (${String.format("%.1f", distance)}m <= ${PROXIMITY_THRESHOLD_METERS}m)")
                triggerProximityAlert()
                hasTriggeredProximityAlert = true
                lastAlertTime = currentTime
            }
            distance > PROXIMITY_THRESHOLD_METERS -> {
                // Reset alerts when driver moves away (handles cases where driver overshoots)
                if (hasTriggeredProximityAlert || hasTriggeredFinalApproachAlert) {
                    Log.i(TAG, "🔄 Driver moved away from pickup (${String.format("%.1f", distance)}m > ${PROXIMITY_THRESHOLD_METERS}m), resetting alerts")
                    resetAlerts()
                } else {
                    Log.d(TAG, "   ✅ Distance OK, no alerts needed yet")
                }
            }
        }
    }

    /**
     * Trigger proximity alert (vibration + sound)
     */
    private fun triggerProximityAlert() {
        Log.i(TAG, "🔔 PROXIMITY ALERT: Driver approaching pickup location")

        // Vibrate
        vibrate(PROXIMITY_VIBRATION_PATTERN)

        // Play sound
        playProximitySound()
    }

    /**
     * Trigger final approach alert (stronger vibration + different sound)
     */
    private fun triggerFinalApproachAlert() {
        Log.i(TAG, "🚨 FINAL APPROACH ALERT: Driver very close to pickup location")

        // Stronger vibration
        vibrate(FINAL_APPROACH_VIBRATION_PATTERN)

        // Play final approach sound
        playFinalApproachSound()
    }

    /**
     * Vibrate device with given pattern
     */
    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createWaveform(pattern, -1) // -1 means don't repeat
                    vibrator.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
                Log.d(TAG, "Vibration triggered successfully")
            } else {
                Log.w(TAG, "Device does not support vibration")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger vibration", e)
        }
    }

    /**
     * Play proximity alert sound 3 times using multiple fallback methods
     */
    private fun playProximitySound() {
        Log.d(TAG, "🔊 Attempting to play proximity alert sound 3 times...")
        playRepeatedSound(proximityAlertSoundId, R.raw.proximity_alert, "proximity", 3)
    }

    /**
     * Play final approach alert sound 3 times using multiple fallback methods
     */
    private fun playFinalApproachSound() {
        Log.d(TAG, "🔊 Attempting to play final approach alert sound 3 times...")
        playRepeatedSound(finalApproachSoundId, R.raw.final_approach_alert, "final approach", 3)
    }

    /**
     * Play sound multiple times with delays between
     */
    private fun playRepeatedSound(soundPoolId: Int, rawResourceId: Int, soundType: String, repeatCount: Int) {
        // Try different methods in order of preference
        when {
            // Method 1: Try SoundPool
            tryPlayRepeatedSoundWithSoundPool(soundPoolId, soundType, repeatCount) -> {
                Log.d(TAG, "✅ Using SoundPool for repeated $soundType sound")
            }
            // Method 2: Try MediaPlayer
            tryPlayRepeatedSoundWithMediaPlayer(rawResourceId, soundType, repeatCount) -> {
                Log.d(TAG, "✅ Using MediaPlayer for repeated $soundType sound")
            }
            // Method 3: Fallback to system sound
            else -> {
                Log.w(TAG, "⚠️ Falling back to repeated system notification sound")
                playRepeatedSystemSound(repeatCount)
            }
        }
    }

    /**
     * Try playing sound multiple times with SoundPool
     */
    private fun tryPlayRepeatedSoundWithSoundPool(soundId: Int, soundType: String, repeatCount: Int): Boolean {
        return try {
            if (isInitialized && soundId != -1) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat()
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).toFloat()
                val volume = if (maxVolume > 0) currentVolume / maxVolume else 1.0f

                Log.d(TAG, "🎵 Playing $soundType sound $repeatCount times via SoundPool - Volume: $volume")

                // Play the sound multiple times with delays
                for (i in 0 until repeatCount) {
                    val playResult = soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
                    Log.d(TAG, "🔄 SoundPool play #${i+1} result: $playResult")

                    // Add delay between sounds (except for the last one)
                    if (i < repeatCount - 1) {
                        Thread.sleep(400) // 400ms delay between sounds
                    }
                }
                true
            } else {
                Log.w(TAG, "❌ SoundPool not initialized or invalid sound ID for $soundType")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SoundPool repeated play failed for $soundType", e)
            false
        }
    }

    /**
     * Try playing sound multiple times with MediaPlayer
     */
    private fun tryPlayRepeatedSoundWithMediaPlayer(soundResourceId: Int, soundType: String, repeatCount: Int): Boolean {
        return try {
            Log.d(TAG, "🎵 Playing $soundType sound $repeatCount times with MediaPlayer...")

            for (i in 0 until repeatCount) {
                val mediaPlayer = MediaPlayer.create(context, soundResourceId)
                if (mediaPlayer != null) {
                    mediaPlayer.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )

                    mediaPlayer.setOnCompletionListener { mp ->
                        mp.release()
                        Log.d(TAG, "✅ MediaPlayer sound #${i+1} completed and released")
                    }

                    mediaPlayer.start()
                    Log.d(TAG, "🔄 MediaPlayer #${i+1} started")

                    // Wait for sound to finish before playing next (if not the last)
                    if (i < repeatCount - 1) {
                        Thread.sleep(800) // Wait for sound to finish + delay
                    }
                } else {
                    Log.e(TAG, "❌ MediaPlayer.create returned null for play #${i+1}")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaPlayer repeated play failed", e)
            false
        }
    }

    /**
     * Play system notification sound multiple times
     */
    private fun playRepeatedSystemSound(repeatCount: Int) {
        try {
            Log.d(TAG, "🎵 Playing system notification $repeatCount times...")

            for (i in 0 until repeatCount) {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, notification)

                if (ringtone != null) {
                    ringtone.play()
                    Log.d(TAG, "🔄 System sound #${i+1} played")

                    // Add delay between sounds (except for the last one)
                    if (i < repeatCount - 1) {
                        Thread.sleep(500)
                    }
                } else {
                    Log.e(TAG, "❌ Could not get system notification ringtone for play #${i+1}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play repeated system notification sound", e)
        }
    }

    /**
     * Try playing sound with SoundPool
     */
    private fun tryPlaySoundWithSoundPool(soundId: Int, soundType: String): Boolean {
        return try {
            if (isInitialized && soundId != -1) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat()
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).toFloat()
                val volume = if (maxVolume > 0) currentVolume / maxVolume else 1.0f

                Log.d(TAG, "🎵 Playing $soundType sound via SoundPool - Volume: $volume (${currentVolume}/${maxVolume})")

                val playResult = soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
                Log.d(TAG, "✅ SoundPool play result: $playResult for $soundType sound")

                playResult != null && playResult != 0
            } else {
                Log.w(TAG, "❌ SoundPool not initialized or invalid sound ID for $soundType")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SoundPool failed for $soundType sound", e)
            false
        }
    }

    /**
     * Try playing sound with MediaPlayer as fallback
     */
    private fun tryPlaySoundWithMediaPlayer(soundResourceId: Int): Boolean {
        return try {
            Log.d(TAG, "🎵 Trying MediaPlayer as fallback...")

            val mediaPlayer = MediaPlayer.create(context, soundResourceId)
            if (mediaPlayer != null) {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                mediaPlayer.setOnCompletionListener { mp ->
                    mp.release()
                    Log.d(TAG, "✅ MediaPlayer sound completed and released")
                }

                mediaPlayer.start()
                Log.d(TAG, "✅ MediaPlayer started successfully")
                true
            } else {
                Log.e(TAG, "❌ MediaPlayer.create returned null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaPlayer failed", e)
            false
        }
    }

    /**
     * Play system notification sound as final fallback
     */
    private fun playSystemNotificationSound() {
        try {
            Log.d(TAG, "🎵 Playing system notification sound as fallback...")

            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)

            if (ringtone != null) {
                ringtone.play()
                Log.d(TAG, "✅ System notification sound played")
            } else {
                Log.e(TAG, "❌ Could not get system notification ringtone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play system notification sound", e)
        }
    }

    /**
     * Reset alert states (call when new pickup is assigned or trip is completed)
     */
    fun resetAlerts() {
        hasTriggeredProximityAlert = false
        hasTriggeredFinalApproachAlert = false
        lastAlertTime = 0L
        Log.d(TAG, "Alert states reset")
    }


    /**
     * Clean up resources
     */
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            Log.d(TAG, "ProximityAlertUtils resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
}