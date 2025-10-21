package com.rj.islamove.data.repository

import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.RouteInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for geocoding operations using Mapbox Search API
 * Replaces the old GoogleMapsRepository
 */
@Singleton
class MapboxGeocodingRepository @Inject constructor(
    private val context: Context,
    private val mapboxPlacesRepository: MapboxPlacesRepository
) {

    /**
     * Search for locations with text query
     */
    suspend fun searchLocation(query: String): Result<List<BookingLocation>> {
        return try {
            // Use Mapbox Places repository to search
            val result = mapboxPlacesRepository.searchPlaces(query)
            if (result.isSuccess) {
                val places = result.getOrNull() ?: emptyList()
                val bookingLocations = places.map { place ->
                    mapboxPlacesRepository.placeDetailsToBookingLocation(place)
                }
                Result.success(bookingLocations)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("MapboxGeocodingRepository", "Error searching location", e)
            Result.failure(e)
        }
    }

    /**
     * Geocode a location name to coordinates
     */
    suspend fun geocodeLocation(locationName: String): Result<BookingLocation?> {
        return try {
            val result = mapboxPlacesRepository.searchPlaces(locationName, limit = 1)
            if (result.isSuccess) {
                val places = result.getOrNull() ?: emptyList()
                val bookingLocation = if (places.isNotEmpty()) {
                    mapboxPlacesRepository.placeDetailsToBookingLocation(places.first())
                } else {
                    null
                }
                Result.success(bookingLocation)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("MapboxGeocodingRepository", "Error geocoding location", e)
            Result.failure(e)
        }
    }

    /**
     * Reverse geocode coordinates to address
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String?> {
        return try {
            val point = Point.fromLngLat(longitude, latitude)
            val result = mapboxPlacesRepository.findNearbyPlaces(point, radius = 100)
            if (result.isSuccess) {
                val places = result.getOrNull() ?: emptyList()
                val address = if (places.isNotEmpty()) {
                    places.first().address ?: "Unknown Location"
                } else {
                    null
                }
                Result.success(address)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("MapboxGeocodingRepository", "Error reverse geocoding", e)
            Result.failure(e)
        }
    }

    /**
     * Get distance and route information between two points
     */
    suspend fun getRouteInfo(
        origin: com.google.firebase.firestore.GeoPoint,
        destination: com.google.firebase.firestore.GeoPoint
    ): Result<RouteInfo> {
        return try {
            // For now, return a simple distance calculation
            // In a real implementation, you would use Mapbox Directions API
            val distance = calculateDistance(
                Point.fromLngLat(origin.longitude, origin.latitude),
                Point.fromLngLat(destination.longitude, destination.latitude)
            )

            val routeInfo = RouteInfo(
                waypoints = listOf(origin, destination),
                totalDistance = distance,
                estimatedDuration = (distance / 50000.0 * 60).toInt() // Rough estimate: 50 km/h (50000 m/h)
            )

            Result.success(routeInfo)
        } catch (e: Exception) {
            Log.e("MapboxGeocodingRepository", "Error getting route info", e)
            Result.failure(e)
        }
    }

    /**
     * Calculate distance between two Points (in meters)
     */
    private fun calculateDistance(point1: Point, point2: Point): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val lat1Rad = Math.toRadians(point1.latitude())
        val lat2Rad = Math.toRadians(point2.latitude())
        val deltaLatRad = Math.toRadians(point2.latitude() - point1.latitude())
        val deltaLonRad = Math.toRadians(point2.longitude() - point1.longitude())

        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}