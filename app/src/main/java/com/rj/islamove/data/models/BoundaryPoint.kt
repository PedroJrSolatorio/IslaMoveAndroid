package com.rj.islamove.data.models

/**
 * Represents a geographic point with latitude and longitude
 * Used for defining zone boundaries and service area boundaries
 */
data class BoundaryPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)