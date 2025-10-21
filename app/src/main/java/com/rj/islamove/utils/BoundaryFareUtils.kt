package com.rj.islamove.utils

import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import com.rj.islamove.data.repository.BoundaryFareManagementRepository
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import kotlinx.coroutines.runBlocking

/**
 * Utility for determining which barangay boundary a location falls within
 * and calculating boundary-based fares
 *
 * NOTE: Boundaries are now fetched from Firestore (zone_boundaries collection)
 * instead of being hardcoded. Use ZoneBoundaryRepository to manage boundaries.
 */
object BoundaryFareUtils {

    // Boundary fare rules for specific destinations
    data class BoundaryFareRule(
        val destinationName: String,
        val destinationCoordinates: GeoPoint,
        val boundaryFares: Map<String, Double> // boundary_name -> fare
    )

    // Cache for zone boundaries loaded from Firestore
    private var cachedBoundaries: Map<String, List<Point>>? = null
    private var lastCacheUpdate: Long = 0
    private const val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    /**
     * Load zone boundaries from Firestore and cache them
     */
    private suspend fun loadBoundariesFromFirestore(repository: ZoneBoundaryRepository): Map<String, List<Point>> {
        val currentTime = System.currentTimeMillis()

        // Return cached boundaries if still valid
        if (cachedBoundaries != null && (currentTime - lastCacheUpdate) < CACHE_DURATION_MS) {
            Log.d("BoundaryFareUtils", "Using cached boundaries (${cachedBoundaries!!.size} zones)")
            return cachedBoundaries!!
        }

        Log.d("BoundaryFareUtils", "Loading boundaries from Firestore...")

        val result = repository.getAllZoneBoundaries()
        val boundaries = result.getOrNull() ?: emptyList()

        val boundaryMap = boundaries.associate { zoneBoundary ->
            val points = zoneBoundary.points.map { boundaryPoint ->
                Point.fromLngLat(boundaryPoint.longitude, boundaryPoint.latitude)
            }
            zoneBoundary.name to points
        }

        cachedBoundaries = boundaryMap
        lastCacheUpdate = currentTime

        Log.i("BoundaryFareUtils", "Loaded ${boundaryMap.size} zone boundaries from Firestore")
        return boundaryMap
    }

    /**
     * Clear the boundary cache (useful when boundaries are updated)
     */
    fun clearCache() {
        cachedBoundaries = null
        lastCacheUpdate = 0
        Log.d("BoundaryFareUtils", "Boundary cache cleared")
    }

    // Hardcoded boundaries removed - all boundaries now come from Firestore
    // Admins configure boundaries in the Service Area Management screen

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     */
    private fun isPointInPolygon(point: Point, polygon: List<Point>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var p1 = polygon[0]

        for (i in 1 until polygon.size) {
            val p2 = polygon[i]

            if (point.latitude() > kotlin.math.min(p1.latitude(), p2.latitude()) &&
                point.latitude() <= kotlin.math.max(p1.latitude(), p2.latitude()) &&
                point.longitude() <= kotlin.math.max(p1.longitude(), p2.longitude())) {

                if (p1.latitude() != p2.latitude()) {
                    val xinters = (point.latitude() - p1.latitude()) * (p2.longitude() - p1.longitude()) /
                                  (p2.latitude() - p1.latitude()) + p1.longitude()

                    if (p1.longitude() == p2.longitude() || point.longitude() <= xinters) {
                        inside = !inside
                    }
                }
            }
            p1 = p2
        }

        return inside
    }

    /**
     * Calculate the area of a polygon using the Shoelace formula
     */
    private fun calculatePolygonArea(polygon: List<Point>): Double {
        if (polygon.size < 3) return 0.0

        var area = 0.0
        for (i in polygon.indices) {
            val j = (i + 1) % polygon.size
            area += polygon[i].longitude() * polygon[j].latitude()
            area -= polygon[j].longitude() * polygon[i].latitude()
        }
        return kotlin.math.abs(area / 2.0)
    }

    /**
     * Determine which barangay boundary a location falls within
     * Returns null if pickup location is not inside any defined boundary
     *
     * When a point is inside multiple overlapping boundaries, returns the SMALLEST boundary
     * (most specific location) to ensure consistent fare calculation
     *
     * @param pickupCoordinates The coordinates to check
     * @param zoneBoundaryRepository Optional repository to load boundaries from Firestore
     *                                If null, uses hardcoded boundaries (deprecated)
     */
    fun determineBoundary(
        pickupCoordinates: GeoPoint,
        zoneBoundaryRepository: ZoneBoundaryRepository? = null
    ): String? {
        val pickupPoint = Point.fromLngLat(pickupCoordinates.longitude, pickupCoordinates.latitude)

        Log.i("BoundaryFareUtils", "🔍 Checking boundary for location: ${pickupCoordinates.latitude}, ${pickupCoordinates.longitude}")

        // Load boundaries from Firestore (required)
        val boundaries = if (zoneBoundaryRepository != null) {
            try {
                runBlocking {
                    loadBoundariesFromFirestore(zoneBoundaryRepository)
                }
            } catch (e: Exception) {
                Log.e("BoundaryFareUtils", "❌ Error loading boundaries from Firestore - no boundaries available", e)
                emptyMap()
            }
        } else {
            Log.e("BoundaryFareUtils", "❌ No ZoneBoundaryRepository provided - cannot determine boundaries")
            emptyMap()
        }

        // Track all matching boundaries with their areas
        val matchingBoundaries = mutableListOf<Pair<String, Double>>()

        for ((boundaryName, polygon) in boundaries) {
            Log.d("BoundaryFareUtils", "📍 Checking against boundary: $boundaryName")

            // Check if point is inside this boundary's polygon
            val isInside = isPointInPolygon(pickupPoint, polygon)
            Log.d("BoundaryFareUtils", "🔺 Point in $boundaryName polygon: $isInside")

            if (isInside) {
                val area = calculatePolygonArea(polygon)
                matchingBoundaries.add(boundaryName to area)
                Log.i("BoundaryFareUtils", "✅ Location is within $boundaryName boundary (area: $area)")
            }
        }

        // If multiple boundaries match, choose the smallest one (most specific)
        if (matchingBoundaries.isNotEmpty()) {
            val selectedBoundary = matchingBoundaries.minByOrNull { it.second }?.first

            if (matchingBoundaries.size > 1) {
                Log.w("BoundaryFareUtils", "⚠️  OVERLAPPING BOUNDARIES DETECTED!")
                Log.w("BoundaryFareUtils", "Found ${matchingBoundaries.size} matching boundaries:")
                matchingBoundaries.forEach { (name, area) ->
                    Log.w("BoundaryFareUtils", "  - $name (area: $area)")
                }
                Log.i("BoundaryFareUtils", "🎯 Selected SMALLEST boundary: $selectedBoundary (most specific location)")
            } else {
                Log.i("BoundaryFareUtils", "🎯 BOUNDARY DETECTED: Passenger is in $selectedBoundary")
            }

            return selectedBoundary
        }

        Log.i("BoundaryFareUtils", "❌ Pickup location is not within any defined boundary")
        Log.w("BoundaryFareUtils", "📍 PASSENGER OUTSIDE ALL BOUNDARIES - will use standard fare calculation")
        return null
    }

    /**
     * Calculate boundary-based fare for a specific destination
     *
     * @param pickupCoordinates The pickup location
     * @param destinationAddress The destination address
     * @param destinationCoordinates The destination coordinates
     * @param repository The fare management repository (for boundary-to-destination fares)
     * @param zoneBoundaryRepository Repository to load zone boundaries and their boundary-to-boundary fares
     */
    fun calculateBoundaryBasedFare(
        pickupCoordinates: GeoPoint,
        destinationAddress: String,
        destinationCoordinates: GeoPoint,
        repository: BoundaryFareManagementRepository? = null,
        zoneBoundaryRepository: ZoneBoundaryRepository? = null
    ): Double? {
        Log.i("BoundaryFareUtils", "💰 CALCULATING BOUNDARY-BASED FARE")
        Log.i("BoundaryFareUtils", "📍 Pickup: ${pickupCoordinates.latitude}, ${pickupCoordinates.longitude}")
        Log.i("BoundaryFareUtils", "🎯 Destination: $destinationAddress")
        Log.i("BoundaryFareUtils", "📍 Destination coords: ${destinationCoordinates.latitude}, ${destinationCoordinates.longitude}")

        // Extract clean destination name (remove fare if present)
        // Format: "City Hall - ₱50" -> "City Hall"
        val cleanDestinationName = destinationAddress.split(" - ₱").firstOrNull()?.trim() ?: destinationAddress
        Log.i("BoundaryFareUtils", "🧹 Clean destination name: $cleanDestinationName")

        // Determine pickup boundary
        val pickupBoundary = determineBoundary(pickupCoordinates, zoneBoundaryRepository)
        if (pickupBoundary != null) {
            Log.i("BoundaryFareUtils", "✅ Pickup boundary detected: $pickupBoundary")

            // PRIORITY 1: Check for boundary-to-destination fare FIRST (for admin-configured landmarks/destinations)
            // This ensures specific landmark/destination fares configured by admin take precedence
            if (repository != null) {
                try {
                    // Use clean destination name for database lookup
                    val fare = runBlocking {
                        repository.getFareForRoute(pickupBoundary, cleanDestinationName).getOrNull()
                    }

                    if (fare != null) {
                        Log.i("BoundaryFareUtils", "🎉 BOUNDARY-TO-DESTINATION FARE APPLIED (PRIORITY 1 - ADMIN CONFIGURED)!")
                        Log.i("BoundaryFareUtils", "📍 Boundary: $pickupBoundary")
                        Log.i("BoundaryFareUtils", "🎯 Destination: $cleanDestinationName")
                        Log.i("BoundaryFareUtils", "💰 Fare: ₱$fare")
                        Log.i("BoundaryFareUtils", "=========================================")
                        return fare
                    } else {
                        Log.w("BoundaryFareUtils", "⚠️  No boundary-to-destination fare found for $pickupBoundary -> $cleanDestinationName")
                    }
                } catch (e: Exception) {
                    Log.e("BoundaryFareUtils", "❌ Error fetching boundary-to-destination fare from database", e)
                }
            }

            // PRIORITY 2: Check if destination is also in a boundary (for boundary-to-boundary fare as fallback)
            // Only use this if no admin-configured boundary-to-destination fare exists
            val destinationBoundary = determineBoundary(destinationCoordinates, zoneBoundaryRepository)
            if (destinationBoundary != null) {
                Log.i("BoundaryFareUtils", "✅ Destination boundary detected: $destinationBoundary")

                // Try to get boundary-to-boundary fare from zone boundaries
                if (zoneBoundaryRepository != null) {
                    try {
                        val boundaries = runBlocking {
                            zoneBoundaryRepository.getAllZoneBoundaries().getOrNull()
                        }

                        // Find the pickup boundary and check if it has a fare to the destination boundary
                        val pickupZoneBoundary = boundaries?.find { it.name == pickupBoundary }
                        val boundaryToBoundaryFare = pickupZoneBoundary?.boundaryFares?.get(destinationBoundary)

                        if (boundaryToBoundaryFare != null) {
                            Log.i("BoundaryFareUtils", "🎉 BOUNDARY-TO-BOUNDARY FARE APPLIED (PRIORITY 2 - FALLBACK)!")
                            Log.i("BoundaryFareUtils", "📍 From Boundary: $pickupBoundary")
                            Log.i("BoundaryFareUtils", "📍 To Boundary: $destinationBoundary")
                            Log.i("BoundaryFareUtils", "💰 Fare: ₱$boundaryToBoundaryFare")
                            Log.i("BoundaryFareUtils", "=========================================")
                            return boundaryToBoundaryFare
                        } else {
                            Log.w("BoundaryFareUtils", "⚠️  No boundary-to-boundary fare found for $pickupBoundary -> $destinationBoundary")
                        }
                    } catch (e: Exception) {
                        Log.e("BoundaryFareUtils", "❌ Error fetching boundary-to-boundary fare", e)
                    }
                }
            } else {
                Log.w("BoundaryFareUtils", "⚠️  No repository provided - boundary fares must come from database")
            }
        } else {
            Log.w("BoundaryFareUtils", "⚠️  No boundary detected for pickup location")
        }

        Log.i("BoundaryFareUtils", "❌ NO ADMIN FARE CONFIGURED - no fare available")
        Log.i("BoundaryFareUtils", "=========================================")
        return null
    }

    /**
     * Check if two coordinates are near each other (within threshold in km)
     */
    private fun isNearDestination(coord1: GeoPoint, coord2: GeoPoint, thresholdKm: Double): Boolean {
        val distance = calculateDistance(coord1.latitude, coord1.longitude, coord2.latitude, coord2.longitude)
        return distance <= thresholdKm
    }

    /**
     * Calculate distance between two coordinates in kilometers
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }



    /**
     * Test boundary detection for a specific coordinate
     * @deprecated Hardcoded boundaries removed - use determineBoundary() with repository instead
     */
    @Deprecated("Use determineBoundary() with ZoneBoundaryRepository instead")
    fun testBoundaryDetection(latitude: Double, longitude: Double): String? {
        Log.w("BoundaryFareUtils", "⚠️ testBoundaryDetection() is deprecated - use determineBoundary() with repository")
        return null
    }

    /**
     * Format pickup location for trip history display
     * Returns boundary name if within a boundary, otherwise returns "Lat, Lng" format
     *
     * @param coordinates The coordinates to format
     * @param zoneBoundaryRepository Optional repository to load boundaries from Firestore
     */
    fun formatPickupLocationForHistory(
        coordinates: GeoPoint,
        zoneBoundaryRepository: ZoneBoundaryRepository? = null
    ): String {
        val boundaryName = determineBoundary(coordinates, zoneBoundaryRepository)
        return if (boundaryName != null) {
            boundaryName
        } else {
            "${String.format("%.6f", coordinates.latitude)}, ${String.format("%.6f", coordinates.longitude)}"
        }
    }

    /**
     * Format pickup location for trip history display (for Location model with separate lat/lng)
     * Returns boundary name if within a boundary, otherwise returns "Lat, Lng" format
     *
     * @param latitude The latitude
     * @param longitude The longitude
     * @param zoneBoundaryRepository Optional repository to load boundaries from Firestore
     */
    fun formatPickupLocationForHistory(
        latitude: Double,
        longitude: Double,
        zoneBoundaryRepository: ZoneBoundaryRepository? = null
    ): String {
        val geoPoint = GeoPoint(latitude, longitude)
        return formatPickupLocationForHistory(geoPoint, zoneBoundaryRepository)
    }
}