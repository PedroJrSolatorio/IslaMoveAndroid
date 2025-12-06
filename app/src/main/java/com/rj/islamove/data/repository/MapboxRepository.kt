package com.rj.islamove.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.BuildConfig
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.RouteInfo
import com.rj.islamove.data.models.NavigationInstruction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Inject
import javax.inject.Singleton
// For direct HTTP requests to Mapbox Directions API
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Singleton
class MapboxRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationCache: LocationCacheRepository
) {

    companion object {
        private const val TAG = "MapboxRepository"
        private const val BACKEND_URL = BuildConfig.RENDER_BASE_URL
        private const val CACHE_EXPIRY_HOURS = 48L // Extended cache to 48 hours
        private const val MAX_CACHE_SIZE = 50 // Reduced cache size

        // API Rate limiting - DYNAMIC based on Mapbox monthly limits
        private var isThrottlingActive = false
        private val MIN_REQUEST_INTERVAL get() = if (isThrottlingActive) 30000L else 1500L // 30s when throttling, 1.5s normally (was 5s!)
        private val SHORT_DISTANCE_THRESHOLD get() = if (isThrottlingActive) 200.0 else 20.0 // 200m when throttling, 20m normally
        private val MAPBOX_MONTHLY_LIMIT = 100000 // Mapbox free tier monthly limit (real limit that matters)
    }

    // In-memory route cache
    private data class CachedRoute(
        val route: RouteInfo,
        val timestamp: Long
    )

    private val routeCache = mutableMapOf<String, CachedRoute>()
    private val lastRequestTime = mutableMapOf<String, Long>()

    // Request deduplication - prevent multiple concurrent requests for the same route
    private val pendingRequests = mutableMapOf<String, kotlinx.coroutines.Deferred<Result<RouteInfo>>>()

    // Monthly request tracking for Mapbox API limits
    private var monthlyRequestCount = 0
    private var lastMonthlyResetTime = System.currentTimeMillis()
    private val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    // Dynamic throttling control
    private var lastThrottleCheckTime = 0L
    private val THROTTLE_ACTIVATION_THRESHOLD = 0.85 // Activate throttling at 85% of monthly limit
    private val THROTTLE_DEACTIVATION_THRESHOLD = 0.70 // Deactivate throttling at 70% of monthly limit

    // Cost monitoring
    private var totalApiCostEstimate = 0.0

    private var accessToken: String? = null

    init {
        initializeMapbox()
    }

    private fun initializeMapbox() {
        try {
            accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "Mapbox access token not found in BuildConfig")
            } else {
                com.mapbox.common.MapboxOptions.accessToken = accessToken!!
//                Log.d(TAG, "Mapbox initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Mapbox", e)
        }
    }

    // Generate cache key for route
    private fun generateCacheKey(origin: BookingLocation, destination: BookingLocation): String {
        val originKey = "${roundForCaching(origin.coordinates.latitude)},${roundForCaching(origin.coordinates.longitude)}"
        val destKey = "${roundForCaching(destination.coordinates.latitude)},${roundForCaching(destination.coordinates.longitude)}"
        return "$originKey->$destKey"
    }

    private fun roundForCaching(coordinate: Double): String {
        return "%.3f".format(coordinate)
    }

    private fun isCacheValid(cachedRoute: CachedRoute): Boolean {
        val ageHours = (System.currentTimeMillis() - cachedRoute.timestamp) / (1000 * 60 * 60)
        return ageHours < CACHE_EXPIRY_HOURS
    }

    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = routeCache.filterValues {
            !isCacheValid(it)
        }.keys
        expiredKeys.forEach { routeCache.remove(it) }

        if (routeCache.size > MAX_CACHE_SIZE) {
            val sortedEntries = routeCache.entries.sortedBy { it.value.timestamp }
            val toRemove = sortedEntries.take(routeCache.size - MAX_CACHE_SIZE)
            toRemove.forEach { routeCache.remove(it.key) }
        }
    }

    /**
     * Check and update dynamic throttling based on Mapbox monthly limits
     */
    private fun updateThrottlingStatus() {
        val currentTime = System.currentTimeMillis()

        // Reset monthly counter if it's a new month
        if (currentTime - lastMonthlyResetTime > ONE_MONTH_MS) {
            Log.i(TAG, "🔄 Monthly reset: Used $monthlyRequestCount Mapbox API calls last month")
            monthlyRequestCount = 0
            lastMonthlyResetTime = currentTime
        }

        // Only check throttling status every 5 minutes to avoid excessive calculations
        if (currentTime - lastThrottleCheckTime < 5 * 60 * 1000L) {
            return
        }
        lastThrottleCheckTime = currentTime

        val monthlyUsagePercent = monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT
        val wasThrottlingActive = isThrottlingActive

        when {
            // Activate throttling if approaching monthly limit (85%)
            monthlyUsagePercent >= THROTTLE_ACTIVATION_THRESHOLD && !isThrottlingActive -> {
                isThrottlingActive = true
//                Log.w(TAG, "🚨 DYNAMIC THROTTLING ACTIVATED! Monthly usage: ${String.format("%.1f", monthlyUsagePercent * 100)}% ($monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT)")
//                Log.w(TAG, "📉 Switched to STRICT limits: Request interval increased to 60s, short distance threshold increased to 1km")
            }
            // Deactivate throttling if usage dropped back down (70%)
            monthlyUsagePercent <= THROTTLE_DEACTIVATION_THRESHOLD && isThrottlingActive -> {
                isThrottlingActive = false
//                Log.i(TAG, "✅ DYNAMIC THROTTLING DEACTIVATED! Monthly usage: ${String.format("%.1f", monthlyUsagePercent * 100)}% ($monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT)")
//                Log.i(TAG, "📈 Switched back to RELAXED limits: Request interval reduced to 5s, short distance threshold reduced to 300m")
            }
        }

        // Log status if throttling state changed
        if (wasThrottlingActive != isThrottlingActive) {
            val status = if (isThrottlingActive) "STRICT" else "RELAXED"
            Log.i(TAG, "🔄 Throttling mode changed to: $status")
        }
    }

    /**
     * Enhanced API limit checking based on monthly Mapbox limits
     */
    private fun isApiLimitExceeded(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Reset monthly counter if it's a new month
        if (currentTime - lastMonthlyResetTime > ONE_MONTH_MS) {
//            Log.i(TAG, "🔄 Monthly reset: Used $monthlyRequestCount Mapbox API calls last month (Cost estimate: $${"%.2f".format(totalApiCostEstimate)})")
            monthlyRequestCount = 0
            lastMonthlyResetTime = currentTime
            totalApiCostEstimate = 0.0 // Reset cost estimate monthly
        }

        // Check if we've exceeded the monthly limit (should never happen with throttling, but as safety)
        if (monthlyRequestCount >= MAPBOX_MONTHLY_LIMIT) {
//            Log.e(TAG, "🚨 MONTHLY LIMIT EXCEEDED! Used $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT API calls")
            return true
        }

        // With dynamic throttling, we shouldn't need hard limits, but add emergency stop at 95%
        if (monthlyRequestCount >= (MAPBOX_MONTHLY_LIMIT * 0.95)) {
//            Log.e(TAG, "🚨 EMERGENCY: Reached 95% of monthly limit! Used $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT")
            return true
        }

        return false
    }

    /**
     * Get route between two points using basic calculation with waypoints
     */
    suspend fun getRoute(
        origin: BookingLocation,
        destination: BookingLocation,
        mode: String = "driving",
        forceRealRoute: Boolean = false // New parameter to force real routing for active rides
    ): Result<RouteInfo> = withContext(Dispatchers.IO) {
        try {
            // REQUEST DEDUPLICATION: Check if we already have a pending request for this exact route
            val cacheKey = generateCacheKey(origin, destination)
            pendingRequests[cacheKey]?.let { pendingDeferred ->
                if (pendingDeferred.isActive) {
//                    Log.d(TAG, "⏳ Deduplicating request: Waiting for existing API call for $cacheKey")
                    return@withContext pendingDeferred.await() // Reuse the existing request!
                } else {
                    pendingRequests.remove(cacheKey)
                }
            }
            // Check if route is very short - use direct route to save API costs
            // BUT bypass this check if forceRealRoute is true (for active rides)
            val distance = calculateDistance(
                origin.coordinates.latitude,
                origin.coordinates.longitude,
                destination.coordinates.latitude,
                destination.coordinates.longitude
            )

//            Log.d(TAG, "Route calculation - forceRealRoute: $forceRealRoute, distance: ${String.format("%.0f", distance)}m, threshold: $SHORT_DISTANCE_THRESHOLD")

            // NEVER use direct routes when forceRealRoute is true - always use real API routing
            if (!forceRealRoute && distance < SHORT_DISTANCE_THRESHOLD) {
//                Log.d(TAG, "Short distance (${String.format("%.0f", distance)}m), using direct route to save API costs")
                val directRoute = createSimpleDirectRoute(origin, destination)
                return@withContext Result.success(directRoute)
            }

            // Skip cache for real-time tracking when forceRealRoute is true
            if (!forceRealRoute) {
                routeCache[cacheKey]?.let { cachedRoute ->
                    if (isCacheValid(cachedRoute)) {
//                        Log.d(TAG, "Using cached route for $cacheKey (${(System.currentTimeMillis() - cachedRoute.timestamp) / (60 * 60 * 1000)}h old)")
                        return@withContext Result.success(cachedRoute.route)
                    } else {
                        routeCache.remove(cacheKey)
                    }
                }
            } else {
                Log.d(TAG, "REAL-TIME: Skipping cache for active tracking (forceRealRoute=true)")
            }

            cleanExpiredCache()

            // Update dynamic throttling based on Mapbox monthly limits
            updateThrottlingStatus()

            // Enhanced API limit checking - BUT bypass for active rides when forceRealRoute is true
            if (!forceRealRoute && isApiLimitExceeded()) {
//                Log.w(TAG, "Monthly API limit exceeded - using cost-free fallback route (Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT, Throttling: ${if(isThrottlingActive) "STRICT" else "RELAXED"})")
                val fallbackRoute = createSimpleDirectRoute(origin, destination)
                return@withContext Result.success(fallbackRoute)
            }

            val currentTime = System.currentTimeMillis()
            lastRequestTime[cacheKey]?.let { lastTime ->
                val timeSinceLastRequest = currentTime - lastTime
                if (!forceRealRoute && timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
//                    Log.d(TAG, "Request throttled for $cacheKey (${timeSinceLastRequest / 1000}s since last request)")
                    val fallbackRoute = createSimpleDirectRoute(origin, destination)
                    return@withContext Result.success(fallbackRoute)
                }
            }

            lastRequestTime[cacheKey] = currentTime

            val routeTypeLog = if (forceRealRoute) "ACTIVE RIDE" else "Regular"
            val monthlyUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100
//            Log.d(TAG, "Creating $routeTypeLog route from ${origin.address} to ${destination.address} (Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT, ${String.format("%.1f", monthlyUsagePercent)}% used)")

            // Create deferred for this request to enable request deduplication
            val deferred = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                calculateActualRoute(origin, destination, forceRealRoute, routeTypeLog, cacheKey)
            }
            pendingRequests[cacheKey] = deferred

            // Wait for result and cleanup
            val result = try {
                deferred.await()
            } finally {
                pendingRequests.remove(cacheKey)
            }

            return@withContext result

        } catch (e: Exception) {
//            Log.e(TAG, "Error getting route", e)
            pendingRequests.remove(generateCacheKey(origin, destination))
            if (forceRealRoute) {
                // For forceRealRoute, return failure instead of straight line
                Result.failure(e)
            } else {
                val fallbackRoute = createSimpleDirectRoute(origin, destination)
                Result.success(fallbackRoute)
            }
        }
    }

    /**
     * Internal method to calculate the actual route (wrapped in deferred for deduplication)
     */
    private suspend fun calculateActualRoute(
        origin: BookingLocation,
        destination: BookingLocation,
        forceRealRoute: Boolean,
        routeTypeLog: String,
        cacheKey: String
    ): Result<RouteInfo> {
        try {
            // Use REAL Mapbox Directions API for proper road routing
            // ALWAYS use real API for active rides (forceRealRoute=true)
            val routeInfo = if (!accessToken.isNullOrEmpty()) {
                try {
                    val apiRoute = getRealMapboxDirectionsRoute(origin, destination)
                    // Increment monthly counter on successful API call
                    monthlyRequestCount++
                    totalApiCostEstimate += 0.5 // Estimate $0.50 per Directions API call
                    val newMonthlyUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100
//                    Log.i(TAG, "$routeTypeLog API call successful! Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT (${String.format("%.1f", newMonthlyUsagePercent)}%) | Est. Cost: $${"%.2f".format(totalApiCostEstimate)}")
                    apiRoute
                } catch (e: Exception) {
//                    Log.w(TAG, "Real Mapbox Directions API failed, using fallback", e)
                    // For active rides, if API fails, try a second time
                    if (forceRealRoute) {
//                        Log.i(TAG, "RETRY: Second attempt for active ride route")
                        try {
                            val retryRoute = getRealMapboxDirectionsRoute(origin, destination)
                            monthlyRequestCount++
                            totalApiCostEstimate += 0.5
                            val retryUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100
//                            Log.i(TAG, "💰 $routeTypeLog API RETRY successful! Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT (${String.format("%.1f", retryUsagePercent)}%)")
                            retryRoute
                        } catch (e2: Exception) {
//                            Log.e(TAG, "🚨 CRITICAL: Both attempts failed for active ride - NO ROUTE AVAILABLE", e2)
                            // For forceRealRoute, return failure instead of straight line
                            return@calculateActualRoute Result.failure(Exception("Failed to calculate real route after 2 attempts: ${e2.message}"))
                        }
                    } else {
                        createSimpleDirectRoute(origin, destination)
                    }
                }
            } else {
//                Log.w(TAG, "No Mapbox access token, using fallback route")
                createSimpleDirectRoute(origin, destination)
            }

            // Cache the route
            routeCache[cacheKey] = CachedRoute(routeInfo, System.currentTimeMillis())
//            Log.d(TAG, "Cached new route for $cacheKey. Cache size: ${routeCache.size}")

            return Result.success(routeInfo)

        } catch (e: Exception) {
//            Log.e(TAG, "Error getting route", e)
            if (forceRealRoute) {
                // For forceRealRoute, return failure instead of straight line
                return Result.failure(e)
            } else {
                val fallbackRoute = createSimpleDirectRoute(origin, destination)
                return Result.success(fallbackRoute)
            }
        }
    }

    /**
     * Get REAL Mapbox Directions API route that follows actual roads
     */
    private suspend fun getRealMapboxDirectionsRoute(
        origin: BookingLocation,
        destination: BookingLocation
    ): RouteInfo = withContext(Dispatchers.IO) {
        try {
//            Log.d(TAG, "Getting REAL Mapbox Directions API route with actual roads")

            if (accessToken.isNullOrEmpty()) {
//                Log.w(TAG, "No Mapbox access token, using fallback")
                return@withContext createSimpleDirectRoute(origin, destination)
            }

            // Use REAL Mapbox Directions API for actual road routing
            getMapboxDirectionsApiRoute(origin, destination)
        } catch (e: Exception) {
//            Log.e(TAG, "Failed to get real Mapbox route, using fallback", e)
            createSimpleDirectRoute(origin, destination)
        }
    }

    /**
     * Get route from Mapbox Directions API via backend proxy
     * This routes through your backend for usage tracking and rate limiting
     */
    private suspend fun getMapboxDirectionsApiRoute(
        origin: BookingLocation,
        destination: BookingLocation
    ): RouteInfo = withContext(Dispatchers.IO) {
        try {
//            Log.d(TAG, "Calling backend proxy for Mapbox Directions API")
//            Log.d(TAG, "Origin: ${origin.coordinates.latitude}, ${origin.coordinates.longitude}")
//            Log.d(TAG, "Destination: ${destination.coordinates.latitude}, ${destination.coordinates.longitude}")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            // Build request to backend proxy
            val coordinates = "${origin.coordinates.longitude},${origin.coordinates.latitude};${destination.coordinates.longitude},${destination.coordinates.latitude}"

            val requestBody = JSONObject().apply {
                put("coordinates", coordinates)
            }

//            Log.d(TAG, "Request body: $requestBody")

            val request = Request.Builder()
                .url("$BACKEND_URL/api/mapbox/directions")
                .post(
                    okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        requestBody.toString()
                    )
                )
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

//            Log.d(TAG, "Response code: ${response.code}")
//            Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")

            if (responseBody != null && responseBody.length < 5000) {
//                Log.d(TAG, "Response body preview: ${responseBody.take(500)}")
            }

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)

                // Check if this is a fallback response due to rate limiting
                if (jsonResponse.optBoolean("fallback", false)) {
//                    Log.w(TAG, "Backend returned fallback due to rate limiting")
                    return@withContext createSimpleDirectRoute(origin, destination)
                }

                val routes = jsonResponse.optJSONArray("routes")
//                Log.d(TAG, "Routes array: ${routes != null}, length: ${routes?.length() ?: 0}")

                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
//                    Log.d(TAG, "Route object keys: ${route.keys().asSequence().toList()}")

                    val geometry = route.optString("geometry", "")
//                    Log.d(TAG, "Geometry present: ${geometry.isNotEmpty()}, length: ${geometry.length}")

                    val distance = route.optDouble("distance", 0.0)
                    val duration = route.optDouble("duration", 0.0)
//                    Log.d(TAG, "Distance: ${distance}m, Duration: ${duration}s")

                    val convertedRoute = convertMapboxJsonToRouteInfo(route)
//                    Log.d(TAG, "✅ Converted route: ${convertedRoute.waypoints.size} waypoints, ${convertedRoute.totalDistance}m")
                    return@withContext convertedRoute
                } else {
                    Log.e(TAG, "No routes in response!")
                }
            } else if (response.code == 429) {
                // Rate limit exceeded - use fallback
//                Log.w(TAG, "Backend rate limit exceeded (429), using fallback route")
                return@withContext createSimpleDirectRoute(origin, destination)
            } else {
                Log.e(TAG, "Backend proxy failed: ${response.code}")
//                Log.e(TAG, "Response body: $responseBody")
            }

//            Log.w(TAG, "Backend proxy failed, using fallback route")
            createSimpleDirectRoute(origin, destination)
        } catch (e: Exception) {
//            Log.e(TAG, "❌ Exception calling backend proxy: ${e.message}", e)
            createSimpleDirectRoute(origin, destination)
        }
    }

    /**
     * Get current Mapbox API usage from backend.
     * Optional - it's for showing usage in mobile app's admin section. You don't need to implement it.
     */
    suspend fun getMapboxUsageStats(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$BACKEND_URL/api/mapbox/usage")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val usage = json.getJSONObject("usage")

                val stats = mapOf(
                    "directions" to usage.getInt("directions"),
                    "geocoding" to usage.getInt("geocoding"),
                    "total" to usage.getInt("total"),
                    "limit" to usage.getInt("limit"),
                    "remaining" to usage.getInt("remaining"),
                    "percentUsed" to usage.getDouble("percentUsed"),
                    "status" to json.getString("status"),
                    "daysUntilReset" to json.getInt("daysUntilReset"),
                    "lastReset" to json.getString("lastReset")
                )

                Result.success(stats)
            } else {
                Result.failure(Exception("Failed to get usage stats: ${response.code}"))
            }
        } catch (e: Exception) {
//            Log.e(TAG, "Error getting usage stats", e)
            Result.failure(e)
        }
    }

    /**
     * Convert Mapbox JSON route to our RouteInfo format
     */
    private fun convertMapboxJsonToRouteInfo(
        route: JSONObject
    ): RouteInfo {
        val waypoints = mutableListOf<GeoPoint>()

        try {
            // Get the geometry from the route
            val geometry = route.optString("geometry", "")
            if (!geometry.isNullOrEmpty()) {
                // Decode the polyline6 geometry manually
                val decodedPoints = decodePolyline6(geometry)

                // Add all the REAL road coordinates
                decodedPoints.forEach { point ->
                    waypoints.add(GeoPoint(point.latitude, point.longitude))
                }

//                Log.d(TAG, "Decoded ${decodedPoints.size} REAL road coordinates from Mapbox")
            } else {
//                Log.w(TAG, "No geometry in Mapbox route, using fallback")
                return createDirectFallbackRoute()
            }

            val distance = route.optDouble("distance", 0.0) // Distance is already in meters
            val duration = (route.optDouble("duration", 0.0) / 60.0).toInt() // Convert seconds to minutes

            // Extract turn-by-turn instructions
            val turnByTurnInstructions = extractTurnByTurnInstructions(route)

//            Log.i(TAG, "REAL Mapbox road route: ${String.format("%.0f", distance)}m, ~${duration}min with ${waypoints.size} road points and ${turnByTurnInstructions.size} instructions")

            return RouteInfo(
                waypoints = waypoints,
                totalDistance = distance,
                estimatedDuration = duration,
                routeId = "mapbox_real_roads_${System.currentTimeMillis()}",
                turnByTurnInstructions = turnByTurnInstructions
            )
        } catch (e: Exception) {
//            Log.e(TAG, "Failed to convert Mapbox JSON route", e)
            return createDirectFallbackRoute()
        }
    }

    /**
     * Decode polyline6 geometry manually
     */
    private fun decodePolyline6(encoded: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
            lng += dlng

            points.add(GeoPoint(lat * 1e-6, lng * 1e-6))
        }

        return points
    }

    /**
     * Extract turn-by-turn instructions from Mapbox Directions API response
     */
    private fun extractTurnByTurnInstructions(route: JSONObject): List<NavigationInstruction> {
        val instructions = mutableListOf<NavigationInstruction>()

        try {
            val legs = route.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                for (i in 0 until legs.length()) {
                    val leg = legs.getJSONObject(i)
                    val steps = leg.optJSONArray("steps")

                    if (steps != null) {
                        for (j in 0 until steps.length()) {
                            val step = steps.getJSONObject(j)

                            val instruction = step.optString("name", "Continue straight")
                            val distance = step.optDouble("distance", 0.0)
                            val duration = step.optDouble("duration", 0.0)

                            val maneuver = step.optJSONObject("maneuver")
                            var type = "continue"
                            var modifier = ""
                            var bearing = 0.0
                            var location = GeoPoint(0.0, 0.0)

                            if (maneuver != null) {
                                type = maneuver.optString("type", "continue")
                                modifier = maneuver.optString("modifier", "")
                                bearing = maneuver.optDouble("bearing_after", 0.0)

                                val locationArray = maneuver.optJSONArray("location")
                                if (locationArray != null && locationArray.length() >= 2) {
                                    location = GeoPoint(
                                        locationArray.getDouble(1), // latitude
                                        locationArray.getDouble(0)  // longitude
                                    )
                                }
                            }

                            // Create a proper instruction text
                            val instructionText = createInstructionText(type, modifier, instruction)

                            instructions.add(
                                NavigationInstruction(
                                    instruction = instructionText,
                                    distance = distance,
                                    duration = duration,
                                    type = type,
                                    modifier = modifier,
                                    location = location,
                                    bearing = bearing
                                )
                            )
                        }
                    }
                }
            }

            Log.d(TAG, "Extracted ${instructions.size} turn-by-turn instructions from Mapbox response")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract turn-by-turn instructions", e)
        }

        return instructions
    }

    /**
     * Create readable instruction text from Mapbox maneuver data
     */
    private fun createInstructionText(type: String, modifier: String, stepName: String): String {
        return when (type) {
            "depart" -> "Head ${getDirectionText(modifier)} on $stepName"
            "turn" -> "Turn ${modifier} onto $stepName"
            "merge" -> "Merge ${modifier} onto $stepName"
            "continue" -> if (stepName.isNotEmpty()) "Continue on $stepName" else "Continue straight"
            "arrive" -> "Arrive at your destination"
            "roundabout" -> "Take the roundabout and exit onto $stepName"
            "exit roundabout" -> "Exit the roundabout onto $stepName"
            "rotary" -> "Take the rotary and exit onto $stepName"
            "roundabout turn" -> "At the roundabout, turn ${modifier} onto $stepName"
            "notification" -> stepName
            "new name" -> "Continue onto $stepName"
            "fork" -> "Keep ${modifier} at the fork onto $stepName"
            "end of road" -> "Turn ${modifier} at the end of the road onto $stepName"
            "use lane" -> "Use the ${modifier} lane"
            "ramp" -> "Take the ramp ${modifier} onto $stepName"
            else -> if (stepName.isNotEmpty()) "Continue on $stepName" else "Continue straight"
        }
    }

    /**
     * Convert direction modifier to readable text
     */
    private fun getDirectionText(modifier: String): String {
        return when (modifier) {
            "left" -> "left"
            "right" -> "right"
            "straight" -> "straight"
            "slight left" -> "slightly left"
            "slight right" -> "slightly right"
            "sharp left" -> "sharp left"
            "sharp right" -> "sharp right"
            else -> "straight"
        }
    }

    /**
     * Create direct fallback route when API fails
     */
    private fun createDirectFallbackRoute(): RouteInfo {
        val waypoints = listOf(
            GeoPoint(0.0, 0.0),  // Will be replaced by caller
            GeoPoint(0.0, 0.0)   // Will be replaced by caller
        )

        return RouteInfo(
            waypoints = waypoints,
            totalDistance = 0.0,
            estimatedDuration = 0,
            routeId = "fallback_direct_${System.currentTimeMillis()}"
        )
    }

    /**
     * Create simple direct route between origin and destination (no artificial curves)
     */
    fun createSimpleDirectRoute(origin: BookingLocation, destination: BookingLocation): RouteInfo {
        val distance = calculateDistance(
            origin.coordinates.latitude,
            origin.coordinates.longitude,
            destination.coordinates.latitude,
            destination.coordinates.longitude
        )

        val estimatedDuration = (distance / 40000.0 * 60).toInt() // minutes at 40 km/h (40000 m/h)

        // Create simple direct route with just origin and destination
        val waypoints = listOf(
            GeoPoint(origin.coordinates.latitude, origin.coordinates.longitude),
            GeoPoint(destination.coordinates.latitude, destination.coordinates.longitude)
        )

        Log.i(TAG, "Created simple direct route: ${String.format("%.0f", distance)}m, ~${estimatedDuration}min")

        return RouteInfo(
            waypoints = waypoints,
            totalDistance = distance,
            estimatedDuration = estimatedDuration,
            routeId = "simple_direct_${System.currentTimeMillis()}"
        )
    }

    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    /**
     * Search for locations (placeholder - using Google Places instead)
     */
    suspend fun searchLocations(
        query: String,
        limit: Int = 10
    ): Result<List<LocationSearchResult>> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) {
                return@withContext Result.success(emptyList())
            }

//            Log.w(TAG, "Location search not implemented - using Google Places API instead")
            Result.success(emptyList())

        } catch (e: Exception) {
            Log.e(TAG, "Error searching locations", e)
            Result.failure(e)
        }
    }

    /**
     * Reverse geocoding (placeholder - using Google Places instead) - but i dont put google api key
     */
    suspend fun reverseGeocode(
        coordinates: GeoPoint
    ): Result<LocationSearchResult> = withContext(Dispatchers.IO) {
        try {
//            Log.w(TAG, "Reverse geocoding not implemented - using Google Places API instead")

            // Return a basic result
            val locationResult = LocationSearchResult(
                id = "mapbox_${coordinates.latitude}_${coordinates.longitude}",
                name = "Location",
                fullAddress = "Lat: ${String.format("%.6f", coordinates.latitude)}, Lng: ${String.format("%.6f", coordinates.longitude)}",
                shortAddress = "Dinagat Islands",
                coordinates = coordinates,
                placeType = "address",
                barangay = "",
                municipality = "San Jose",
                province = "Dinagat Islands"
            )

            Result.success(locationResult)

        } catch (e: Exception) {
//            Log.e(TAG, "Error reverse geocoding", e)
            Result.failure(e)
        }
    }
}

/**
 * Data class for location search results
 */
data class LocationSearchResult(
    val id: String,
    val name: String,
    val fullAddress: String,
    val shortAddress: String,
    val coordinates: GeoPoint,
    val placeType: String,
    val barangay: String = "",
    val municipality: String = "San Jose",
    val province: String = "Dinagat Islands"
)