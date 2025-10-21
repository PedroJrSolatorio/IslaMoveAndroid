package com.rj.islamove.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Repository to maintain user status overrides across navigation and app restarts
 * This prevents status changes from being lost when ViewModels are recreated or app is terminated
 */
@Singleton
class UserStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "user_status_overrides"
        private const val KEY_STATUS_OVERRIDES = "status_overrides"
        private const val KEY_PENDING_OPS = "pending_ops"
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Track user status overrides - these take precedence over Firebase data
    private val _userStatusOverrides = MutableStateFlow<Map<String, Boolean>>(loadStatusOverrides())
    val userStatusOverrides: StateFlow<Map<String, Boolean>> = _userStatusOverrides.asStateFlow()

    // Track pending Firebase operations to avoid removing overrides too early
    private val _pendingFirebaseOps = MutableStateFlow<Set<String>>(loadPendingOperations())
    val pendingFirebaseOps: StateFlow<Set<String>> = _pendingFirebaseOps.asStateFlow()

    fun setStatusOverride(uid: String, isActive: Boolean) {
        _userStatusOverrides.value = _userStatusOverrides.value + (uid to isActive)
        saveStatusOverrides()
    }

    fun removeStatusOverride(uid: String) {
        _userStatusOverrides.value = _userStatusOverrides.value - uid
        saveStatusOverrides()
    }

    fun addPendingOperation(uid: String) {
        _pendingFirebaseOps.value = _pendingFirebaseOps.value + uid
        savePendingOperations()
    }

    fun removePendingOperation(uid: String) {
        _pendingFirebaseOps.value = _pendingFirebaseOps.value - uid
        savePendingOperations()
    }

    fun clearOverride(uid: String) {
        _userStatusOverrides.value = _userStatusOverrides.value - uid
        _pendingFirebaseOps.value = _pendingFirebaseOps.value - uid
        saveStatusOverrides()
        savePendingOperations()
    }

    fun clearAllOverrides() {
        _userStatusOverrides.value = emptyMap()
        _pendingFirebaseOps.value = emptySet()
        saveStatusOverrides()
        savePendingOperations()
    }

    fun getStatusOverride(uid: String): Boolean? {
        return _userStatusOverrides.value[uid]
    }

    fun isPendingOperation(uid: String): Boolean {
        return _pendingFirebaseOps.value.contains(uid)
    }

    private fun loadStatusOverrides(): Map<String, Boolean> {
        return try {
            val json = sharedPrefs.getString(KEY_STATUS_OVERRIDES, null) ?: return emptyMap()
            // Parse JSON manually since we want to keep it simple
            val pairs = json.removeSurrounding("{", "}").split(",")
            val map = mutableMapOf<String, Boolean>()
            for (pair in pairs) {
                if (pair.trim().isNotEmpty()) {
                    val (key, value) = pair.split("=")
                    map[key.trim().removeSurrounding("\"")] = value.trim().toBoolean()
                }
            }
            map
        } catch (e: Exception) {
            android.util.Log.e("UserStatusRepository", "Error loading status overrides", e)
            emptyMap()
        }
    }

    private fun loadPendingOperations(): Set<String> {
        return try {
            val json = sharedPrefs.getString(KEY_PENDING_OPS, null) ?: return emptySet()
            // Parse JSON manually
            json.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            android.util.Log.e("UserStatusRepository", "Error loading pending operations", e)
            emptySet()
        }
    }

    private fun saveStatusOverrides() {
        try {
            val map = _userStatusOverrides.value
            val json = map.entries.joinToString(",", "{", "}") { "\"${it.key}\"=${it.value}" }
            sharedPrefs.edit().putString(KEY_STATUS_OVERRIDES, json).apply()
        } catch (e: Exception) {
            android.util.Log.e("UserStatusRepository", "Error saving status overrides", e)
        }
    }

    private fun savePendingOperations() {
        try {
            val set = _pendingFirebaseOps.value
            val json = set.joinToString(",", "[", "]") { "\"$it\"" }
            sharedPrefs.edit().putString(KEY_PENDING_OPS, json).apply()
        } catch (e: Exception) {
            android.util.Log.e("UserStatusRepository", "Error saving pending operations", e)
        }
    }
}