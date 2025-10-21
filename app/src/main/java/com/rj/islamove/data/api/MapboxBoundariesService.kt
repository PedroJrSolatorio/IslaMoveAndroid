package com.rj.islamove.data.api

/**
 * Mapbox Boundaries Service for Admin Service Area Management
 *
 * IMPORTANT: This implementation uses the FREE Mapbox Geocoding API
 * to provide basic administrative boundary search functionality.
 *
 * For full Boundaries Explorer v4 features (detailed polygons, vector tiles),
 * you would need to upgrade to a paid Mapbox plan that includes:
 * - Boundaries API access
 * - Boundaries Lookup API
 *
 * Current Free Tier Implementation:
 * - Search administrative areas using Geocoding API
 * - Get approximate boundaries (bounding boxes)
 * - City/province/region search
 * - 100,000 free geocoding requests/month
 *
 * Upgrade Path:
 * - Uncomment the production boundary methods below
 * - Add your paid plan API endpoints
 * - Enable vector tile boundary layers
 */

import android.util.Log
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.Polygon
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.data.models.ServiceAreaBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapboxBoundariesService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MapboxBoundariesService"
        private const val MAPBOX_API_BASE = "https://api.mapbox.com"

        // Boundaries v4 tilesets
        private const val ADMIN_TILESET = "mapbox.boundaries-adm"
        private const val LOCALITY_TILESET = "mapbox.boundaries-loc"
        private const val POSTAL_TILESET = "mapbox.boundaries-pos"

        // Barangay boundary source for Philippines
        const val BARANGAY_BOUNDARIES_SOURCE_ID = "barangay-boundaries"
        const val BARANGAY_BOUNDARIES_LAYER_ID = "barangay-boundaries-layer"
    }

    data class BoundaryFeature(
        val id: String,
        val name: String,
        val nameEn: String,
        val adminLevel: Int,
        val isoCountryCode: String,
        val geometry: Geometry?,
        val centroid: Pair<Double, Double>? = null,
        val bbox: List<Double>? = null
    )

    data class BoundaryLayer(
        val name: String,
        val description: String,
        val tileset: String,
        val level: Int
    )

    fun getAvailableBoundaryLayers(): List<BoundaryLayer> {
        return listOf(
            BoundaryLayer("Countries", "Country boundaries", ADMIN_TILESET + "0", 0),
            BoundaryLayer("Provinces/States", "First-level administrative divisions", ADMIN_TILESET + "1", 1),
            BoundaryLayer("Cities/Municipalities", "Second-level administrative divisions", ADMIN_TILESET + "2", 2),
            BoundaryLayer("Districts", "Third-level administrative divisions", ADMIN_TILESET + "3", 3),
            BoundaryLayer("Localities", "Local administrative areas", LOCALITY_TILESET + "1", 1),
            BoundaryLayer("Neighborhoods", "Neighborhood boundaries", LOCALITY_TILESET + "2", 2)
        )
    }

    suspend fun searchBoundaries(
        query: String,
        boundingBox: List<Double>? = null,
        adminLevel: Int? = null,
        countryCode: String = "PH" // Default to Philippines
    ): Result<List<BoundaryFeature>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchBoundaries called with query: '$query', country: $countryCode")
        try {
            // For MVP, we'll use the Mapbox Geocoding API to find administrative areas
            // In production, you might want to use the Boundaries Lookup API
            val url = buildString {
                append("$MAPBOX_API_BASE/geocoding/v5/mapbox.places/")
                append(query)
                append(".json?")
                append("country=$countryCode")
                append("&types=region,district,locality,place")

                if (boundingBox != null && boundingBox.size == 4) {
                    append("&bbox=${boundingBox.joinToString(",")}")
                }

                append("&limit=10")
                append("&access_token=${getMapboxAccessToken()}")
            }

            Log.d(TAG, "Searching boundaries: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(Exception("Failed to search boundaries: ${response.code}"))
            }

            val jsonResponse = JSONObject(responseBody)
            val features = jsonResponse.getJSONArray("features")
            val boundaryFeatures = mutableListOf<BoundaryFeature>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")
                val geometry = feature.optJSONObject("geometry")

                val placeName = properties.optString("place_name", "")
                val text = properties.optString("text", "")
                val context = properties.optJSONArray("context")

                // Extract admin level from context
                var detectedAdminLevel = 2 // Default to city level
                var isoCode = countryCode

                if (context != null) {
                    for (j in 0 until context.length()) {
                        val contextItem = context.getJSONObject(j)
                        val id = contextItem.optString("id", "")
                        when {
                            id.startsWith("country") -> {
                                isoCode = contextItem.optString("short_code", countryCode).uppercase()
                            }
                            id.startsWith("region") -> detectedAdminLevel = 1
                            id.startsWith("district") -> detectedAdminLevel = 2
                            id.startsWith("locality") -> detectedAdminLevel = 3
                        }
                    }
                }

                // Get center coordinates
                val center = feature.optJSONArray("center")
                val centroid = if (center != null && center.length() == 2) {
                    Pair(center.getDouble(0), center.getDouble(1))
                } else null

                // Get bounding box
                val bbox = feature.optJSONArray("bbox")
                val boundingBox = if (bbox != null) {
                    (0 until bbox.length()).map { bbox.getDouble(it) }
                } else null

                boundaryFeatures.add(
                    BoundaryFeature(
                        id = feature.optString("id", ""),
                        name = text,
                        nameEn = text,
                        adminLevel = detectedAdminLevel,
                        isoCountryCode = isoCode,
                        geometry = null, // Geometry would need separate API call
                        centroid = centroid,
                        bbox = boundingBox
                    )
                )
            }

            Result.success(boundaryFeatures)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching boundaries", e)
            Result.failure(e)
        }
    }

    suspend fun getBoundaryGeometry(
        featureId: String,
        tileset: String = ADMIN_TILESET + "2"
    ): Result<ServiceAreaBoundary> = withContext(Dispatchers.IO) {
        try {
            // For MVP, we'll return a placeholder boundary
            // In production, you would need to:
            // 1. Use Mapbox Boundaries Lookup API to get the geometry
            // 2. Or use vector tiles to fetch the boundary polygon

            Log.d(TAG, "Getting boundary geometry for feature: $featureId")

            // Placeholder implementation - would need actual Mapbox Boundaries API integration
            val placeholderBoundary = ServiceAreaBoundary(
                points = listOf(
                    BoundaryPoint(15.7886, 121.0748), // San Jose, Nueva Ecija approximate boundary
                    BoundaryPoint(15.7886, 121.0948),
                    BoundaryPoint(15.8086, 121.0948),
                    BoundaryPoint(15.8086, 121.0748),
                    BoundaryPoint(15.7886, 121.0748)
                ),
                fillColor = "#4CAF5080",
                strokeColor = "#4CAF50",
                strokeWidth = 2.0
            )

            Result.success(placeholderBoundary)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting boundary geometry", e)
            Result.failure(e)
        }
    }

    private fun getMapboxAccessToken(): String {
        // In production, this should come from BuildConfig or secure storage
        val token = "pk.eyJ1IjoiamhhenBlcjExMiIsImEiOiJjbWYxeGdiemUyYXB6MmpzZGdoODhnYTM0In0.ZOWrbtXWV8eT0tEkfa-GYQ"
        Log.d(TAG, "Using Mapbox access token: ${token.take(20)}...")
        return token
    }

    fun convertGeometryToBoundary(geometry: Geometry): ServiceAreaBoundary? {
        return when (geometry) {
            is Polygon -> {
                val coordinates = geometry.coordinates()
                if (coordinates.isNotEmpty()) {
                    val points = coordinates[0].map { point ->
                        BoundaryPoint(
                            latitude = point.latitude(),
                            longitude = point.longitude()
                        )
                    }
                    ServiceAreaBoundary(
                        points = points,
                        fillColor = "#4CAF5080",
                        strokeColor = "#4CAF50",
                        strokeWidth = 2.0
                    )
                } else null
            }
            else -> null
        }
    }

    fun getBoundariesTilesetUrl(layer: BoundaryLayer, accessToken: String): String {
        return "mapbox://${layer.tileset}"
    }

    /**
     * Get the tileset URL for Philippines administrative boundaries (including barangays)
     * Uses the free-tier compatible administrative boundaries
     */
    fun getPhilippinesAdminBoundariesUrl(): String {
        // For free tier, we'll use a publicly available tileset for Philippines admin boundaries
        // This provides city/municipality and barangay level boundaries
        return "mapbox://mapbox.boundaries-adm3-v3"
    }

    /**
     * Get the style layer configuration for displaying barangay boundaries
     */
    fun getBarangayBoundaryLayerStyle(): Map<String, Any> {
        return mapOf(
            "id" to BARANGAY_BOUNDARIES_LAYER_ID,
            "type" to "line",
            "source" to BARANGAY_BOUNDARIES_SOURCE_ID,
            "source-layer" to "boundaries_admin_3",
            "filter" to listOf("==", listOf("get", "iso_3166_1"), "PH"),
            "layout" to mapOf(
                "line-cap" to "round",
                "line-join" to "round"
            ),
            "paint" to mapOf(
                "line-color" to "#666666",
                "line-width" to listOf(
                    "interpolate",
                    listOf("linear"),
                    listOf("zoom"),
                    10, 0.5,
                    15, 1.0,
                    20, 2.0
                ),
                "line-opacity" to 0.7
            )
        )
    }

    /**
     * Get the style source configuration for barangay boundaries
     */
    fun getBarangayBoundarySourceConfig(): Map<String, Any> {
        return mapOf(
            "type" to "vector",
            "url" to getPhilippinesAdminBoundariesUrl()
        )
    }
}