package com.rj.islamove.data.models

import com.mapbox.geojson.Point

/**
 * Data class for place details from Mapbox Search
 */
data class PlaceDetails(
    val id: String,
    val name: String,
    val point: Point,
    val address: String?,
    val rating: Float?,
    val userRatingsTotal: Int?,
    val types: List<String>,
    val phoneNumber: String?,
    val websiteUri: String?,
    val isOpen: Boolean?,
    val openingHours: String?,
    val timestamp: Long = System.currentTimeMillis() // Add timestamp to ensure uniqueness
)