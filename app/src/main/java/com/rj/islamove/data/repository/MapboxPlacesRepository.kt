package com.rj.islamove.data.repository

import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import javax.inject.Inject
import javax.inject.Singleton
import com.rj.islamove.data.models.PlaceDetails
import com.rj.islamove.data.models.BookingLocation

@Singleton
class MapboxPlacesRepository @Inject constructor(
    private val context: Context
) {

    /**
     * Search for tourist attractions near Dinagat Islands
     * Simplified implementation returning mock data
     */
    suspend fun searchTouristAttractions(
        center: Point = Point.fromLngLat(125.5800815, 10.0097818), // San Jose, Dinagat Islands
        radius: Int = 50000 // 50km radius to cover the island
    ): Result<List<PlaceDetails>> {
        return try {
            // Return mock tourist attractions for Dinagat Islands
            val mockAttractions = listOf(
                PlaceDetails(
                    id = "dinagat_church",
                    name = "San Jose Catholic Church",
                    point = Point.fromLngLat(125.5800815, 10.0097818),
                    address = "San Jose, Dinagat Islands",
                    rating = null,
                    userRatingsTotal = null,
                    types = listOf("church", "place_of_worship"),
                    phoneNumber = null,
                    websiteUri = null,
                    isOpen = null,
                    openingHours = null,
                    timestamp = System.currentTimeMillis()
                ),
                PlaceDetails(
                    id = "dinagat_beach",
                    name = "Bitaug Beach",
                    point = Point.fromLngLat(125.6000000, 10.0200000),
                    address = "Bitaug, Dinagat Islands",
                    rating = null,
                    userRatingsTotal = null,
                    types = listOf("beach", "natural_feature"),
                    phoneNumber = null,
                    websiteUri = null,
                    isOpen = null,
                    openingHours = null,
                    timestamp = System.currentTimeMillis()
                )
            )

            Result.success(mockAttractions)
        } catch (e: Exception) {
            Log.e("MapboxPlacesRepository", "Error searching tourist attractions", e)
            Result.failure(e)
        }
    }

    /**
     * Search for places using text query
     * Simplified implementation
     */
    suspend fun searchPlaces(
        query: String,
        center: Point? = null,
        limit: Int = 10
    ): Result<List<PlaceDetails>> {
        return try {
            // Return empty list for now - can be enhanced later
            val places = if (query.contains("San Jose", ignoreCase = true)) {
                listOf(
                    PlaceDetails(
                        id = "san_jose_center",
                        name = "San Jose Municipal Center",
                        point = Point.fromLngLat(125.5800815, 10.0097818),
                        address = "San Jose, Dinagat Islands",
                        rating = null,
                        userRatingsTotal = null,
                        types = listOf("government", "establishment"),
                        phoneNumber = null,
                        websiteUri = null,
                        isOpen = null,
                        openingHours = null,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                emptyList()
            }

            Result.success(places)
        } catch (e: Exception) {
            Log.e("MapboxPlacesRepository", "Error searching places", e)
            Result.failure(e)
        }
    }

    /**
     * Find places near a specific location (for map tap)
     */
    suspend fun findNearbyPlaces(
        location: Point,
        radius: Int = 500 // 500m radius
    ): Result<List<PlaceDetails>> {
        return try {
            // Return empty list for now - basic implementation
            Result.success(emptyList())
        } catch (e: Exception) {
            Log.e("MapboxPlacesRepository", "Error finding nearby places", e)
            Result.failure(e)
        }
    }

    /**
     * Get place details by search result ID
     */
    suspend fun getPlaceDetails(resultId: String): Result<PlaceDetails?> {
        return try {
            Result.success(null)
        } catch (e: Exception) {
            Log.e("MapboxPlacesRepository", "Error fetching place details", e)
            Result.failure(e)
        }
    }

    /**
     * Convert PlaceDetails to BookingLocation for ride booking
     */
    fun placeDetailsToBookingLocation(placeDetails: PlaceDetails): BookingLocation {
        return BookingLocation(
            address = placeDetails.address ?: placeDetails.name,
            coordinates = com.google.firebase.firestore.GeoPoint(
                placeDetails.point.latitude(),
                placeDetails.point.longitude()
            ),
            placeId = placeDetails.id
        )
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