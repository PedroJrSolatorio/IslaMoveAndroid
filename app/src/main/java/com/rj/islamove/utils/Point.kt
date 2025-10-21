package com.rj.islamove.utils

/**
 * Simple Point class to replace Mapbox Point for coordinate storage
 */
data class Point(
    private val longitude: Double,
    private val latitude: Double
) {
    
    companion object {
        /**
         * Create a Point from longitude and latitude
         */
        fun fromLngLat(longitude: Double, latitude: Double): Point {
            return Point(longitude, latitude)
        }
    }
    
    /**
     * Get the latitude
     */
    fun latitude(): Double = latitude
    
    /**
     * Get the longitude
     */
    fun longitude(): Double = longitude
    
    /**
     * Convert to Mapbox Point
     */
    fun toMapboxPoint(): com.mapbox.geojson.Point {
        return com.mapbox.geojson.Point.fromLngLat(longitude, latitude)
    }
}