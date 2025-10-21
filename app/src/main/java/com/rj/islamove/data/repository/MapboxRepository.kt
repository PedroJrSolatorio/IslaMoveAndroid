package com.rj.islamove.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.RouteInfo
import com.rj.islamove.data.models.NavigationInstruction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton
// For direct HTTP requests to Mapbox Directions API
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class MapboxRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationCache: LocationCacheRepository
) {

    companion object {
        private const val TAG = "MapboxRepository"
        private const val CACHE_EXPIRY_HOURS = 48L // Extended cache to 48 hours
        private const val MAX_CACHE_SIZE = 50 // Reduced cache size

        // API Rate limiting - DYNAMIC based on Mapbox monthly limits
        // Mapbox Free Tier: 100,000 API calls/month - we track usage against this real limit
        private var isThrottlingActive = false
        private val MIN_REQUEST_INTERVAL get() = if (isThrottlingActive) 30000L else 1500L // 30s when throttling, 1.5s normally (was 5s!)
        private val SHORT_DISTANCE_THRESHOLD get() = if (isThrottlingActive) 500.0 else 50.0 // 500m when throttling, 50m normally (was 300m)
        private val MAPBOX_MONTHLY_LIMIT = 100000 // Mapbox free tier monthly limit (real limit that matters)

        // San Jose, Nueva Ecija bounding box
        private const val SAN_JOSE_LAT = 15.7886
        private const val SAN_JOSE_LNG = 121.0748
        private const val SEARCH_RADIUS = 50000 // 50km radius
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

    /**
     * Configure API limits for cost control
     */
    fun configureApiLimits(
        requestInterval: Long = MIN_REQUEST_INTERVAL,
        shortDistanceThreshold: Double = SHORT_DISTANCE_THRESHOLD,
        cacheExpiryHours: Long = CACHE_EXPIRY_HOURS
    ) {
        monthlyRequestCount = 0 // Reset counter when limits change
        Log.d(TAG, "API limits configured: interval=${requestInterval}ms, shortDist=$shortDistanceThreshold, cache=$cacheExpiryHours hours")
    }

    /**
     * Get comprehensive API usage and cost statistics
     */
    fun getApiUsageStats(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val daysUntilMonthlyReset = ((ONE_MONTH_MS - (currentTime - lastMonthlyResetTime)) / (24 * 60 * 60 * 1000))
        val monthlyUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100

        return mapOf(
            // Monthly request tracking (what actually matters for Mapbox)
            "monthlyRequestCount" to monthlyRequestCount,
            "monthlyLimit" to MAPBOX_MONTHLY_LIMIT,
            "monthlyRemaining" to (MAPBOX_MONTHLY_LIMIT - monthlyRequestCount),
            "monthlyUsagePercent" to monthlyUsagePercent,
            "daysUntilMonthlyReset" to daysUntilMonthlyReset,

            // Dynamic throttling status
            "isThrottlingActive" to isThrottlingActive,
            "throttleMode" to if (isThrottlingActive) "STRICT" else "RELAXED",
            "throttleActivationThreshold" to (THROTTLE_ACTIVATION_THRESHOLD * 100),
            "throttleDeactivationThreshold" to (THROTTLE_DEACTIVATION_THRESHOLD * 100),

            // Cost monitoring
            "estimatedMonthlyCost" to totalApiCostEstimate,
            "costPerRequest" to 0.5,

            // Cache efficiency
            "cacheSize" to routeCache.size,
            "maxCacheSize" to MAX_CACHE_SIZE,
            "cacheHitRate" to calculateCacheHitRate(),

            // Dynamic thresholds
            "shortDistanceThreshold" to SHORT_DISTANCE_THRESHOLD,
            "minRequestInterval" to (MIN_REQUEST_INTERVAL / 1000),

            // Safety status based on monthly usage
            "safetyStatus" to when {
                isThrottlingActive -> "DYNAMIC_THROTTLED"
                monthlyUsagePercent >= 90.0 -> "CRITICAL_USAGE"
                monthlyUsagePercent >= 80.0 -> "HIGH_USAGE"
                monthlyUsagePercent >= 60.0 -> "MODERATE_USAGE"
                else -> "SAFE"
            }
        )
    }

    private fun calculateCacheHitRate(): Double {
        val totalAttempts = monthlyRequestCount + routeCache.size
        return if (totalAttempts > 0) {
            (routeCache.size.toDouble() / totalAttempts.toDouble()) * 100.0
        } else 0.0
    }

    init {
        initializeMapbox()
    }

    private fun initializeMapbox() {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            accessToken = appInfo.metaData?.getString("MAPBOX_ACCESS_TOKEN")
            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "Mapbox access token not found in manifest")
            } else {
                Log.d(TAG, "Mapbox initialized successfully")
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
                Log.w(TAG, "🚨 DYNAMIC THROTTLING ACTIVATED! Monthly usage: ${String.format("%.1f", monthlyUsagePercent * 100)}% ($monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT)")
                Log.w(TAG, "📉 Switched to STRICT limits: Request interval increased to 60s, short distance threshold increased to 1km")
            }
            // Deactivate throttling if usage dropped back down (70%)
            monthlyUsagePercent <= THROTTLE_DEACTIVATION_THRESHOLD && isThrottlingActive -> {
                isThrottlingActive = false
                Log.i(TAG, "✅ DYNAMIC THROTTLING DEACTIVATED! Monthly usage: ${String.format("%.1f", monthlyUsagePercent * 100)}% ($monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT)")
                Log.i(TAG, "📈 Switched back to RELAXED limits: Request interval reduced to 5s, short distance threshold reduced to 300m")
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
            Log.i(TAG, "🔄 Monthly reset: Used $monthlyRequestCount Mapbox API calls last month (Cost estimate: $${"%.2f".format(totalApiCostEstimate)})")
            monthlyRequestCount = 0
            lastMonthlyResetTime = currentTime
            totalApiCostEstimate = 0.0 // Reset cost estimate monthly
        }

        // Check if we've exceeded the monthly limit (should never happen with throttling, but as safety)
        if (monthlyRequestCount >= MAPBOX_MONTHLY_LIMIT) {
            Log.e(TAG, "🚨 MONTHLY LIMIT EXCEEDED! Used $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT API calls")
            return true
        }

        // With dynamic throttling, we shouldn't need hard limits, but add emergency stop at 95%
        if (monthlyRequestCount >= (MAPBOX_MONTHLY_LIMIT * 0.95)) {
            Log.e(TAG, "🚨 EMERGENCY: Reached 95% of monthly limit! Used $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT")
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
                    Log.d(TAG, "⏳ Deduplicating request: Waiting for existing API call for $cacheKey")
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

            Log.d(TAG, "Route calculation - forceRealRoute: $forceRealRoute, distance: ${String.format("%.0f", distance)}m, threshold: $SHORT_DISTANCE_THRESHOLD")

            // NEVER use direct routes when forceRealRoute is true - always use real API routing
            if (!forceRealRoute && distance < SHORT_DISTANCE_THRESHOLD) {
                Log.d(TAG, "Short distance (${String.format("%.0f", distance)}m), using direct route to save API costs")
                val directRoute = createSimpleDirectRoute(origin, destination)
                return@withContext Result.success(directRoute)
            }

            // Skip cache for real-time tracking when forceRealRoute is true
            if (!forceRealRoute) {
                routeCache[cacheKey]?.let { cachedRoute ->
                    if (isCacheValid(cachedRoute)) {
                        Log.d(TAG, "Using cached route for $cacheKey (${(System.currentTimeMillis() - cachedRoute.timestamp) / (60 * 60 * 1000)}h old)")
                        return@withContext Result.success(cachedRoute.route)
                    } else {
                        routeCache.remove(cacheKey)
                    }
                }
            } else {
                Log.d(TAG, "⚡ REAL-TIME: Skipping cache for active tracking (forceRealRoute=true)")
            }

            cleanExpiredCache()

            // Update dynamic throttling based on Mapbox monthly limits
            updateThrottlingStatus()

            // Enhanced API limit checking - BUT bypass for active rides when forceRealRoute is true
            if (!forceRealRoute && isApiLimitExceeded()) {
                Log.w(TAG, "💰 Monthly API limit exceeded - using cost-free fallback route (Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT, Throttling: ${if(isThrottlingActive) "STRICT" else "RELAXED"})")
                val fallbackRoute = createSimpleDirectRoute(origin, destination)
                return@withContext Result.success(fallbackRoute)
            }

            val currentTime = System.currentTimeMillis()
            lastRequestTime[cacheKey]?.let { lastTime ->
                val timeSinceLastRequest = currentTime - lastTime
                if (!forceRealRoute && timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    Log.d(TAG, "Request throttled for $cacheKey (${timeSinceLastRequest / 1000}s since last request)")
                    val fallbackRoute = createSimpleDirectRoute(origin, destination)
                    return@withContext Result.success(fallbackRoute)
                }
            }

            lastRequestTime[cacheKey] = currentTime

            val routeTypeLog = if (forceRealRoute) "ACTIVE RIDE" else "Regular"
            val monthlyUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100
            Log.d(TAG, "Creating $routeTypeLog route from ${origin.address} to ${destination.address} (Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT, ${String.format("%.1f", monthlyUsagePercent)}% used)")

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
            Log.e(TAG, "Error getting route", e)
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
                    Log.i(TAG, "💰 $routeTypeLog API call successful! Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT (${String.format("%.1f", newMonthlyUsagePercent)}%) | Est. Cost: $${"%.2f".format(totalApiCostEstimate)}")
                    apiRoute
                } catch (e: Exception) {
                    Log.w(TAG, "Real Mapbox Directions API failed, using fallback", e)
                    // For active rides, if API fails, try a second time
                    if (forceRealRoute) {
                        Log.i(TAG, "🔄 RETRY: Second attempt for active ride route")
                        try {
                            val retryRoute = getRealMapboxDirectionsRoute(origin, destination)
                            monthlyRequestCount++
                            totalApiCostEstimate += 0.5
                            val retryUsagePercent = (monthlyRequestCount.toDouble() / MAPBOX_MONTHLY_LIMIT) * 100
                            Log.i(TAG, "💰 $routeTypeLog API RETRY successful! Monthly: $monthlyRequestCount/$MAPBOX_MONTHLY_LIMIT (${String.format("%.1f", retryUsagePercent)}%)")
                            retryRoute
                        } catch (e2: Exception) {
                            Log.e(TAG, "🚨 CRITICAL: Both attempts failed for active ride - NO ROUTE AVAILABLE", e2)
                            // For forceRealRoute, return failure instead of straight line
                            return@calculateActualRoute Result.failure(Exception("Failed to calculate real route after 2 attempts: ${e2.message}"))
                        }
                    } else {
                        createSimpleDirectRoute(origin, destination)
                    }
                }
            } else {
                Log.w(TAG, "No Mapbox access token, using fallback route")
                createSimpleDirectRoute(origin, destination)
            }

            // Cache the route
            routeCache[cacheKey] = CachedRoute(routeInfo, System.currentTimeMillis())
            Log.d(TAG, "Cached new route for $cacheKey. Cache size: ${routeCache.size}")

            return Result.success(routeInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting route", e)
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
            Log.d(TAG, "Getting REAL Mapbox Directions API route with actual roads")

            if (accessToken.isNullOrEmpty()) {
                Log.w(TAG, "No Mapbox access token, using fallback")
                return@withContext createSimpleDirectRoute(origin, destination)
            }

            // Use REAL Mapbox Directions API for actual road routing
            getMapboxDirectionsApiRoute(origin, destination)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get real Mapbox route, using fallback", e)
            createSimpleDirectRoute(origin, destination)
        }
    }

    /**
     * Get route from Mapbox Directions API that follows REAL roads
     * Using direct HTTP request to Mapbox REST API
     */
    private suspend fun getMapboxDirectionsApiRoute(
        origin: BookingLocation,
        destination: BookingLocation
    ): RouteInfo = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calling Mapbox Directions API via HTTP for real road routing")

            val client = OkHttpClient()

            // Build the Mapbox Directions API URL
            val baseUrl = "https://api.mapbox.com/directions/v5/mapbox/driving/"
            val coordinates = "${origin.coordinates.longitude},${origin.coordinates.latitude};${destination.coordinates.longitude},${destination.coordinates.latitude}"
            val url = "$baseUrl$coordinates?access_token=${accessToken ?: ""}&geometries=polyline6&overview=full&steps=true"

            Log.d(TAG, "Mapbox API URL: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val routes = jsonResponse.optJSONArray("routes")

                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        Log.d(TAG, "SUCCESS: Got real Mapbox route from HTTP API")
                        return@withContext convertMapboxJsonToRouteInfo(route)
                    } else {
                        Log.w(TAG, "No routes found in Mapbox response")
                    }
                }
            } else {
                Log.e(TAG, "Mapbox Directions API failed: ${response.code}")
            }

            Log.w(TAG, "Mapbox API failed, using fallback route")
            createSimpleDirectRoute(origin, destination)
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Mapbox Directions API", e)
            createSimpleDirectRoute(origin, destination)
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

                Log.d(TAG, "Decoded ${decodedPoints.size} REAL road coordinates from Mapbox")
            } else {
                Log.w(TAG, "No geometry in Mapbox route, using fallback")
                return createDirectFallbackRoute()
            }

            val distance = route.optDouble("distance", 0.0) // Distance is already in meters
            val duration = (route.optDouble("duration", 0.0) / 60.0).toInt() // Convert seconds to minutes

            // Extract turn-by-turn instructions
            val turnByTurnInstructions = extractTurnByTurnInstructions(route)

            Log.i(TAG, "REAL Mapbox road route: ${String.format("%.0f", distance)}m, ~${duration}min with ${waypoints.size} road points and ${turnByTurnInstructions.size} instructions")

            return RouteInfo(
                waypoints = waypoints,
                totalDistance = distance,
                estimatedDuration = duration,
                routeId = "mapbox_real_roads_${System.currentTimeMillis()}",
                turnByTurnInstructions = turnByTurnInstructions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert Mapbox JSON route", e)
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
     * Get route polyline points for map display
     */
    suspend fun getRoutePolyline(
        origin: BookingLocation,
        destination: BookingLocation
    ): Result<List<com.mapbox.geojson.Point>> = withContext(Dispatchers.IO) {
        try {
            val routeResult = getRoute(origin, destination)

            if (routeResult.isSuccess) {
                val route = routeResult.getOrNull()!!
                val points = route.waypoints.map { geoPoint ->
                    com.mapbox.geojson.Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)
                }
                Result.success(points)
            } else {
                Result.failure(routeResult.exceptionOrNull() ?: Exception("Failed to get route"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting route polyline", e)
            Result.failure(e)
        }
    }

    /**
     * Calculate estimated travel time
     */
    suspend fun getEstimatedTravelTime(
        origin: BookingLocation,
        destination: BookingLocation
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val routeResult = getRoute(origin, destination)
            if (routeResult.isSuccess) {
                val route = routeResult.getOrNull()!!
                Result.success(route.estimatedDuration)
            } else {
                Result.failure(routeResult.exceptionOrNull() ?: Exception("Failed to get travel time"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting travel time", e)
            Result.failure(e)
        }
    }

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

            Log.w(TAG, "Location search not implemented - using Google Places API instead")
            Result.success(emptyList())

        } catch (e: Exception) {
            Log.e(TAG, "Error searching locations", e)
            Result.failure(e)
        }
    }

    /**
     * Reverse geocoding (placeholder - using Google Places instead)
     */
    suspend fun reverseGeocode(
        coordinates: GeoPoint
    ): Result<LocationSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.w(TAG, "Reverse geocoding not implemented - using Google Places API instead")

            // Return a basic result
            val locationResult = LocationSearchResult(
                id = "mapbox_${coordinates.latitude}_${coordinates.longitude}",
                name = "Location",
                fullAddress = "Lat: ${String.format("%.6f", coordinates.latitude)}, Lng: ${String.format("%.6f", coordinates.longitude)}",
                shortAddress = "Nueva Ecija",
                coordinates = coordinates,
                placeType = "address",
                barangay = "",
                municipality = "San Jose",
                province = "Nueva Ecija"
            )

            Result.success(locationResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error reverse geocoding", e)
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
    val province: String = "Nueva Ecija"
)