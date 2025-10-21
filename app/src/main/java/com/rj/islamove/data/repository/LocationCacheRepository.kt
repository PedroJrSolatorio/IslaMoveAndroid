package com.rj.islamove.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.rj.islamove.data.models.FareEstimate
import com.rj.islamove.data.models.RouteInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent cache repository to reduce Google Maps API costs
 * Stores frequently used data in SharedPreferences and in-memory cache
 */
@Singleton
class LocationCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("location_cache", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "LocationCacheRepository"
        private const val CACHE_EXPIRY_DAYS = 7L // 7 days for location data
        private const val ROUTE_CACHE_EXPIRY_HOURS = 48L // 48 hours for routes
        private const val FARE_CACHE_EXPIRY_HOURS = 24L // 24 hours for fare estimates
        private const val MAX_CACHE_ENTRIES = 500 // Prevent unlimited cache growth

        // Cache key prefixes
        private const val REVERSE_GEOCODE_PREFIX = "reverse_geocode_"
        private const val ROUTE_PREFIX = "route_"
        private const val FARE_PREFIX = "fare_"
        private const val SEARCH_PREFIX = "search_"
    }

    // In-memory cache for faster access
    private val memoryCache = mutableMapOf<String, CacheEntry<*>>()

    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val expiryHours: Long
    ) {
        fun isExpired(): Boolean {
            val ageHours = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)
            return ageHours > expiryHours
        }
    }

    /**
     * Cache reverse geocoding results to reduce API calls
     */
    fun cacheReverseGeocode(coordinates: GeoPoint, result: LocationSearchResult) {
        val key = generateReverseGeocodeKey(coordinates)
        val cacheEntry = CacheEntry(result, System.currentTimeMillis(), CACHE_EXPIRY_DAYS * 24)

        // Store in memory cache
        memoryCache[key] = cacheEntry

        // Store in persistent cache
        try {
            val json = gson.toJson(cacheEntry)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "Cached reverse geocode for: ${coordinates.latitude}, ${coordinates.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache reverse geocode", e)
        }
    }

    /**
     * Get cached reverse geocoding result
     */
    fun getCachedReverseGeocode(coordinates: GeoPoint): LocationSearchResult? {
        val key = generateReverseGeocodeKey(coordinates)

        // Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired() && entry.data is LocationSearchResult) {
                Log.d(TAG, "Memory cache hit for reverse geocode: $key")
                return entry.data
            } else {
                memoryCache.remove(key)
            }
        }

        // Check persistent cache
        return try {
            val json = prefs.getString(key, null) ?: return null
            val entry = gson.fromJson<CacheEntry<LocationSearchResult>>(
                json,
                object : TypeToken<CacheEntry<LocationSearchResult>>() {}.type
            )

            if (!entry.isExpired()) {
                // Add back to memory cache
                memoryCache[key] = entry
                Log.d(TAG, "Persistent cache hit for reverse geocode: $key")
                entry.data
            } else {
                // Remove expired entry
                prefs.edit().remove(key).apply()
                null
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse cached reverse geocode", e)
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Cache route information
     */
    fun cacheRoute(origin: GeoPoint, destination: GeoPoint, route: RouteInfo) {
        val key = generateRouteKey(origin, destination)
        val cacheEntry = CacheEntry(route, System.currentTimeMillis(), ROUTE_CACHE_EXPIRY_HOURS)

        memoryCache[key] = cacheEntry

        try {
            val json = gson.toJson(cacheEntry)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "Cached route: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache route", e)
        }
    }

    /**
     * Get cached route
     */
    fun getCachedRoute(origin: GeoPoint, destination: GeoPoint): RouteInfo? {
        val key = generateRouteKey(origin, destination)

        // Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired() && entry.data is RouteInfo) {
                Log.d(TAG, "Memory cache hit for route: $key")
                return entry.data
            } else {
                memoryCache.remove(key)
            }
        }

        // Check persistent cache
        return try {
            val json = prefs.getString(key, null) ?: return null
            val entry = gson.fromJson<CacheEntry<RouteInfo>>(
                json,
                object : TypeToken<CacheEntry<RouteInfo>>() {}.type
            )

            if (!entry.isExpired()) {
                memoryCache[key] = entry
                Log.d(TAG, "Persistent cache hit for route: $key")
                entry.data
            } else {
                prefs.edit().remove(key).apply()
                null
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse cached route", e)
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Cache fare estimates
     */
    fun cacheFareEstimate(origin: GeoPoint, destination: GeoPoint, fare: FareEstimate) {
        val key = generateFareKey(origin, destination)
        val cacheEntry = CacheEntry(fare, System.currentTimeMillis(), FARE_CACHE_EXPIRY_HOURS)

        memoryCache[key] = cacheEntry

        try {
            val json = gson.toJson(cacheEntry)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "Cached fare estimate: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache fare estimate", e)
        }
    }

    /**
     * Get cached fare estimate
     */
    fun getCachedFareEstimate(origin: GeoPoint, destination: GeoPoint): FareEstimate? {
        val key = generateFareKey(origin, destination)

        // Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired() && entry.data is FareEstimate) {
                Log.d(TAG, "Memory cache hit for fare: $key")
                return entry.data
            } else {
                memoryCache.remove(key)
            }
        }

        // Check persistent cache
        return try {
            val json = prefs.getString(key, null) ?: return null
            val entry = gson.fromJson<CacheEntry<FareEstimate>>(
                json,
                object : TypeToken<CacheEntry<FareEstimate>>() {}.type
            )

            if (!entry.isExpired()) {
                memoryCache[key] = entry
                Log.d(TAG, "Persistent cache hit for fare: $key")
                entry.data
            } else {
                prefs.edit().remove(key).apply()
                null
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse cached fare", e)
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Cache search results
     */
    fun cacheSearchResults(query: String, results: List<LocationSearchResult>) {
        val key = SEARCH_PREFIX + query.lowercase().replace(" ", "_")
        val cacheEntry = CacheEntry(results, System.currentTimeMillis(), CACHE_EXPIRY_DAYS * 24)

        memoryCache[key] = cacheEntry

        try {
            val json = gson.toJson(cacheEntry)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "Cached search results for: $query")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache search results", e)
        }
    }

    /**
     * Get cached search results
     */
    fun getCachedSearchResults(query: String): List<LocationSearchResult>? {
        val key = SEARCH_PREFIX + query.lowercase().replace(" ", "_")

        // Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired() && entry.data is List<*>) {
                Log.d(TAG, "Memory cache hit for search: $query")
                @Suppress("UNCHECKED_CAST")
                return entry.data as List<LocationSearchResult>
            } else {
                memoryCache.remove(key)
            }
        }

        // Check persistent cache
        return try {
            val json = prefs.getString(key, null) ?: return null
            val entry = gson.fromJson<CacheEntry<List<LocationSearchResult>>>(
                json,
                object : TypeToken<CacheEntry<List<LocationSearchResult>>>() {}.type
            )

            if (!entry.isExpired()) {
                memoryCache[key] = entry
                Log.d(TAG, "Persistent cache hit for search: $query")
                entry.data
            } else {
                prefs.edit().remove(key).apply()
                null
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse cached search results", e)
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Clean up expired cache entries
     */
    fun cleanupExpiredEntries() {
        val allKeys = prefs.all.keys.toList()
        var cleanedCount = 0

        for (key in allKeys) {
            try {
                val json = prefs.getString(key, null) ?: continue
                val entry = gson.fromJson<CacheEntry<Any>>(
                    json,
                    object : TypeToken<CacheEntry<Any>>() {}.type
                )

                if (entry.isExpired()) {
                    prefs.edit().remove(key).apply()
                    memoryCache.remove(key)
                    cleanedCount++
                }
            } catch (e: Exception) {
                // Remove corrupted entries
                prefs.edit().remove(key).apply()
                memoryCache.remove(key)
                cleanedCount++
            }
        }

        // Also clean memory cache
        val expiredMemoryKeys = memoryCache.filterValues { it.isExpired() }.keys
        expiredMemoryKeys.forEach { memoryCache.remove(it) }

        Log.i(TAG, "Cleaned up $cleanedCount expired cache entries")
    }

    /**
     * Clear all cache (for debugging or cache reset)
     */
    fun clearAllCache() {
        prefs.edit().clear().apply()
        memoryCache.clear()
        Log.i(TAG, "Cleared all cache entries")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val persistentCount = prefs.all.size
        val memoryCount = memoryCache.size
        return "Cache Stats - Persistent: $persistentCount, Memory: $memoryCount"
    }

    // Generate cache keys with appropriate precision for location clustering
    private fun generateReverseGeocodeKey(coordinates: GeoPoint): String {
        // Round to ~100m precision to group nearby locations
        val lat = "%.3f".format(coordinates.latitude)
        val lng = "%.3f".format(coordinates.longitude)
        return "${REVERSE_GEOCODE_PREFIX}${lat}_${lng}"
    }

    private fun generateRouteKey(origin: GeoPoint, destination: GeoPoint): String {
        val originKey = "%.3f,%.3f".format(origin.latitude, origin.longitude)
        val destKey = "%.3f,%.3f".format(destination.latitude, destination.longitude)
        return "${ROUTE_PREFIX}${originKey}_to_${destKey}"
    }

    private fun generateFareKey(origin: GeoPoint, destination: GeoPoint): String {
        val originKey = "%.3f,%.3f".format(origin.latitude, origin.longitude)
        val destKey = "%.3f,%.3f".format(destination.latitude, destination.longitude)
        return "${FARE_PREFIX}${originKey}_to_${destKey}"
    }
}