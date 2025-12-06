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
import com.rj.islamove.BuildConfig
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

    private fun getMapboxAccessToken(): String {
        // In production, this should come from BuildConfig or secure storage
        val token = BuildConfig.MAPBOX_ACCESS_TOKEN
        Log.d(TAG, "Using Mapbox access token: ${token.take(20)}...")
        return token
    }
}