package com.rj.islamove.utils

import android.util.Log
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style

object MapboxStyleHelper {
    private const val TAG = "MapboxStyleHelper"

    /**
     * Enable POI layers to show more landmarks
     */
    fun enablePOILayers(style: Style) {
        try {
            Log.d(TAG, "POI layers enabled for better landmark visibility")
            // The actual POI layer configuration is handled by the Mapbox style itself
            // Using the "Outdoors" style automatically shows more landmarks
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling POI layers", e)
        }
    }

    /**
     * Switch map style to show more landmarks
     */
    fun switchToLandmarkStyle(mapboxMap: MapboxMap, styleName: String = "Outdoors") {
        val styleUri = when (styleName) {
            "Streets" -> Style.MAPBOX_STREETS
            "Outdoors" -> Style.OUTDOORS
            "Light" -> Style.LIGHT
            "Dark" -> Style.DARK
            "Satellite" -> Style.SATELLITE
            "Satellite Streets" -> Style.SATELLITE_STREETS
            else -> Style.OUTDOORS
        }

        mapboxMap.loadStyleUri(styleUri) { style ->
            enablePOILayers(style)
            Log.d(TAG, "Switched to $styleName style with landmarks enabled")
        }
    }
}