package com.rj.islamove.utils

import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.RouteInfo
import com.rj.islamove.data.repository.MapboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single Route Manager - Like Google Maps: One API call per ride, follow the route, only recalculate if major deviation
 */
class OptimizedRouteManager(
    private val mapboxRepository: MapboxRepository,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        // Only recalculate if driver is significantly off the planned route
        private const val ROUTE_DEVIATION_THRESHOLD_METERS = 20.0 // 20m off route before recalculation
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L // Check location every 5 seconds
        private const val MIN_RECALCULATION_TIME_MS = 30000L // 30 seconds minimum between recalculations
    }

    private val _currentRoute = MutableStateFlow<RouteInfo?>(null)
    val currentRoute: StateFlow<RouteInfo?> = _currentRoute

    private var locationUpdateJob: Job? = null
    private var routeOrigin: BookingLocation? = null
    private var routeDestination: BookingLocation? = null
    private var lastRecalculationTime = 0L
    private var routeFollowed = false

    // Track the latest driver location for deviation detection
    private var currentDriverLocation: Point? = null
    private var onRouteDeviationCallback: ((RouteInfo?) -> Unit)? = null

    /**
     * Calculate route once - like Google Maps directions
     */
    suspend fun calculateRouteOnce(
        origin: BookingLocation,
        destination: BookingLocation,
        onRouteCalculated: (RouteInfo?) -> Unit
    ) {
        android.util.Log.d("RouteManager", "🗺️ Calculating route ONCE like Google Maps: ${origin.address} -> ${destination.address}")

        val result = withContext(Dispatchers.IO) {
            mapboxRepository.getRoute(origin, destination, forceRealRoute = true)
        }

        result.onSuccess { route ->
            _currentRoute.value = route
            routeOrigin = origin
            routeDestination = destination
            routeFollowed = true
            lastRecalculationTime = System.currentTimeMillis()
            onRouteCalculated(route)
            android.util.Log.d("RouteManager", "✅ Route calculated successfully: ${route.totalDistance}m, ${route.estimatedDuration}min")
        }

        result.onFailure { error ->
            android.util.Log.e("RouteManager", "❌ Failed to calculate route, trying fallback", error)
            // Try fallback route without forcing real route
            val fallbackResult = withContext(Dispatchers.IO) {
                mapboxRepository.getRoute(origin, destination, forceRealRoute = false)
            }

            fallbackResult.onSuccess { fallbackRoute ->
                _currentRoute.value = fallbackRoute
                routeOrigin = origin
                routeDestination = destination
                routeFollowed = true
                lastRecalculationTime = System.currentTimeMillis()
                onRouteCalculated(fallbackRoute)
                android.util.Log.w("RouteManager", "✅ Fallback route calculated: ${fallbackRoute.totalDistance}m, ${fallbackRoute.estimatedDuration}min")
            }

            fallbackResult.onFailure { fallbackError ->
                android.util.Log.e("RouteManager", "❌ Both primary and fallback routes failed", fallbackError)
                // Create a simple direct route as last resort
                val directRoute = mapboxRepository.createSimpleDirectRoute(origin, destination)
                _currentRoute.value = directRoute
                routeOrigin = origin
                routeDestination = destination
                routeFollowed = true
                lastRecalculationTime = System.currentTimeMillis()
                onRouteCalculated(directRoute)
                android.util.Log.w("RouteManager", "📍 Using direct route as last resort: ${directRoute.totalDistance}m, ${directRoute.estimatedDuration}min")
            }
        }
    }

    /**
     * Start following the pre-calculated route - no API calls unless major deviation
     */
    fun startRouteFollowing(
        initialDriverLocation: com.rj.islamove.utils.Point,
        onRouteDeviation: (RouteInfo?) -> Unit
    ) {
        stopRouteFollowing()

        if (_currentRoute.value == null) {
            android.util.Log.w("RouteManager", "⚠️ No route to follow. Call calculateRouteOnce() first.")
            return
        }

        // Store initial location and callback
        currentDriverLocation = initialDriverLocation
        onRouteDeviationCallback = onRouteDeviation

        locationUpdateJob = coroutineScope.launch {
            android.util.Log.d("RouteManager", "🚗 Starting to follow pre-calculated route (no API calls)")

            while (routeFollowed && _currentRoute.value != null) {
                try {
                    // Check if driver has deviated significantly from the route
                    currentDriverLocation?.let { driverLoc ->
                        android.util.Log.v("RouteManager", "🔍 Checking deviation for location: ${driverLoc.latitude()}, ${driverLoc.longitude()}")

                        if (hasSignificantDeviation(driverLoc)) {
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastRecalc = currentTime - lastRecalculationTime

                            android.util.Log.w("RouteManager", "⚠️ DEVIATION DETECTED! Time since last recalc: ${timeSinceLastRecalc}ms (min: ${MIN_RECALCULATION_TIME_MS}ms)")

                            if (currentTime - lastRecalculationTime > MIN_RECALCULATION_TIME_MS) {
                                android.util.Log.w("RouteManager", "🔄 Recalculating route due to significant deviation!")

                                routeOrigin?.let { origin ->
                                    routeDestination?.let { destination ->
                                        val currentLocation = BookingLocation(
                                            address = "Current Location",
                                            coordinates = com.google.firebase.firestore.GeoPoint(
                                                driverLoc.latitude(),
                                                driverLoc.longitude()
                                            )
                                        )

                                        android.util.Log.i("RouteManager", "📍 Recalculating from (${driverLoc.latitude()}, ${driverLoc.longitude()}) to ${destination.address}")
                                        calculateRouteOnce(currentLocation, destination, onRouteDeviation)
                                    }
                                }
                            } else {
                                android.util.Log.d("RouteManager", "🚫 Route deviation detected but too soon to recalculate (wait ${(MIN_RECALCULATION_TIME_MS - timeSinceLastRecalc) / 1000}s more)")
                            }
                        } else {
                            android.util.Log.v("RouteManager", "✅ Driver is on route")
                        }
                    } ?: android.util.Log.w("RouteManager", "⚠️ Current driver location is null, cannot check deviation")

                    delay(LOCATION_UPDATE_INTERVAL_MS)

                } catch (e: Exception) {
                    android.util.Log.e("RouteManager", "Error in route following", e)
                    delay(LOCATION_UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Update the current driver location for deviation detection
     * Call this every time the driver's location updates
     */
    fun updateDriverLocation(newLocation: Point) {
        currentDriverLocation = newLocation
    }

    /**
     * Stop following the current route
     */
    fun stopRouteFollowing() {
        locationUpdateJob?.cancel()
        locationUpdateJob = null
        routeFollowed = false
        android.util.Log.d("RouteManager", "🛑 Stopped following route")
    }

    /**
     * Check if driver has deviated significantly from the planned route
     */
    private fun hasSignificantDeviation(currentLocation: com.rj.islamove.utils.Point): Boolean {
        val route = _currentRoute.value ?: return false

        // Check distance from current location to the nearest point on the route polyline
        val waypoints = route.waypoints
        if (waypoints.isEmpty()) return false

        // Find the minimum distance from current location to any point on the route
        var minDistanceToRoute = Double.MAX_VALUE
        for (waypoint in waypoints) {
            // Convert GeoPoint to Point for distance calculation
            val waypointPoint = com.rj.islamove.utils.Point.fromLngLat(
                waypoint.longitude,
                waypoint.latitude
            )
            val distance = calculateDistance(currentLocation, waypointPoint)
            if (distance < minDistanceToRoute) {
                minDistanceToRoute = distance
            }
        }

        // If driver is more than ROUTE_DEVIATION_THRESHOLD_METERS away from the route line, they've deviated
        if (minDistanceToRoute > ROUTE_DEVIATION_THRESHOLD_METERS) {
            android.util.Log.d("RouteManager", "📏 Route deviation detected: ${minDistanceToRoute.toInt()}m from route (threshold: ${ROUTE_DEVIATION_THRESHOLD_METERS.toInt()}m)")
            return true
        }

        return false
    }

    /**
     * Calculate distance between two points
     */
    private fun calculateDistance(
        point1: com.rj.islamove.utils.Point,
        point2: com.rj.islamove.utils.Point
    ): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(point1.latitude())
        val lat2Rad = Math.toRadians(point2.latitude())
        val deltaLatRad = Math.toRadians(point2.latitude() - point1.latitude())
        val deltaLngRad = Math.toRadians(point2.longitude() - point1.longitude())

        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Clear current route
     */
    fun clearRoute() {
        stopRouteFollowing()
        _currentRoute.value = null
        routeOrigin = null
        routeDestination = null
        android.util.Log.d("RouteManager", "🗑️ Route cleared")
    }

    /**
     * Get current route statistics
     */
    fun getRouteStats(): Map<String, Any> {
        return mapOf<String, Any>(
            "hasRoute" to (_currentRoute.value != null),
            "routeDistance" to (_currentRoute.value?.totalDistance ?: 0.0),
            "routeDuration" to (_currentRoute.value?.estimatedDuration ?: 0),
            "isFollowing" to routeFollowed,
            "origin" to (routeOrigin?.address ?: "None"),
            "destination" to (routeDestination?.address ?: "None")
        )
    }
}