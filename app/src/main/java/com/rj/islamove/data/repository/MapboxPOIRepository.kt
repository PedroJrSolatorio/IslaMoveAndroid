package com.rj.islamove.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.GeoPoint
import com.rj.islamove.data.models.PlaceDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapboxPOIRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MapboxPOIRepository"
        private const val BASE_URL = "https://api.mapbox.com/search/searchbox/v1/category"
        private const val SEARCH_RADIUS = 10000 // 10km radius
    }

    private var accessToken: String? = null

    init {
        initializeMapbox()
    }

    private fun initializeMapbox() {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            accessToken = appInfo.metaData?.getString("MAPBOX_ACCESS_TOKEN")
            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "Mapbox access token not found in manifest")
            } else {
                Log.d(TAG, "Mapbox POI Repository initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Mapbox", e)
        }
    }

    /**
     * Search for POIs by category around a location
     * 🚨 DISABLED FOR COST CONTROL - Returns empty list to prevent API charges
     */
    suspend fun searchPOIsByCategory(
        center: GeoPoint,
        category: String,
        limit: Int = 50
    ): Result<List<PlaceDetails>> = withContext(Dispatchers.IO) {
        Log.w(TAG, "💰 POI search disabled for cost control. Use built-in map landmarks instead.")
        return@withContext Result.success(emptyList())

        // Original implementation commented out to prevent API costs
        /*
        try {
            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "No access token available")
                return@withContext Result.failure(Exception("No Mapbox access token"))
            }

            val client = OkHttpClient()

            // Build the search URL
            val url = buildString {
                append(BASE_URL)
                append("/$category")
                append("?access_token=$accessToken")
                append("&proximity=${center.longitude},${center.latitude}")
                append("&limit=$limit")
                append("&radius=$SEARCH_RADIUS")
            }

            Log.d(TAG, "Searching POIs: $category around ${center.latitude},${center.longitude}")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val features = jsonResponse.optJSONArray("features")

                    val places = mutableListOf<PlaceDetails>()

                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feature = features.getJSONObject(i)
                            val place = convertFeatureToPlaceDetails(feature, category)
                            if (place != null) {
                                places.add(place)
                            }
                        }
                    }

                    Log.d(TAG, "Found ${places.size} $category POIs")
                    return@withContext Result.success(places)
                }
            } else {
                Log.e(TAG, "Mapbox Search API failed: ${response.code}")
            }

            Result.failure(Exception("Failed to search POIs"))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching POIs for category $category", e)
            Result.failure(e)
        }
        */
    }

    /**
     * Convert Mapbox feature to PlaceDetails
     */
    private fun convertFeatureToPlaceDetails(feature: JSONObject, category: String): PlaceDetails? {
        try {
            val properties = feature.optJSONObject("properties") ?: return null
            val geometry = feature.optJSONObject("geometry") ?: return null
            val coordinates = geometry.optJSONArray("coordinates") ?: return null

            if (coordinates.length() < 2) return null

            val longitude = coordinates.getDouble(0)
            val latitude = coordinates.getDouble(1)

            val name = properties.optString("name", "Unknown Place")
            val address = properties.optString("full_address", "")
            val mapboxId = properties.optString("mapbox_id", "")

            return PlaceDetails(
                id = mapboxId.ifEmpty { "mapbox_${latitude}_${longitude}" },
                name = name,
                point = com.mapbox.geojson.Point.fromLngLat(longitude, latitude),
                address = address,
                rating = null,
                userRatingsTotal = null,
                types = listOf(category),
                phoneNumber = null,
                websiteUri = null,
                isOpen = null,
                openingHours = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting feature to PlaceDetails", e)
            return null
        }
    }

    /**
     * Get all POIs for multiple categories around a location
     * 🚨 DISABLED FOR COST CONTROL - Returns empty map to prevent API charges
     */
    suspend fun getAllPOIsAroundLocation(
        center: GeoPoint,
        categories: List<String> = getDefaultCategories()
    ): Result<Map<String, List<PlaceDetails>>> = withContext(Dispatchers.IO) {
        Log.w(TAG, "💰 POI bulk search disabled for cost control. Use built-in map landmarks instead.")
        return@withContext Result.success(emptyMap())

        /*
        try {
            val allPOIs = mutableMapOf<String, List<PlaceDetails>>()

            for (category in categories) {
                val result = searchPOIsByCategory(center, category, 25) // Limit per category
                if (result.isSuccess) {
                    allPOIs[category] = result.getOrNull() ?: emptyList()
                } else {
                    allPOIs[category] = emptyList()
                }
            }

            val totalPOIs = allPOIs.values.sumOf { it.size }
            Log.i(TAG, "Retrieved $totalPOIs total POIs across ${categories.size} categories")

            Result.success(allPOIs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all POIs", e)
            Result.failure(e)
        }
        */
    }

    /**
     * Get default categories to search for
     */
    fun getDefaultCategories(): List<String> {
        return listOf(
            // Food & Dining
            "restaurant",
            "cafe",
            "fast_food",
            "bar",
            "bakery",

            // Healthcare
            "hospital",
            "pharmacy",
            "clinic",
            "dentist",

            // Transportation
            "gas_station",
            "parking",
            "bus_station",
            "taxi_stand",

            // Shopping
            "supermarket",
            "convenience_store",
            "shopping_mall",
            "department_store",
            "electronics_store",

            // Services
            "bank",
            "atm",
            "post_office",
            "police_station",

            // Education & Religion
            "school",
            "university",
            "library",
            "church",

            // Entertainment & Recreation
            "movie_theater",
            "gym",
            "park",
            "tourist_attraction",

            // Accommodation
            "hotel",
            "lodging"
        )
    }

    /**
     * Get landmark categories with their display properties
     */
    fun getLandmarkCategories(): Map<String, LandmarkCategory> {
        return mapOf(
            "restaurant" to LandmarkCategory("Restaurants", android.graphics.Color.rgb(255, 140, 0)),
            "cafe" to LandmarkCategory("Cafes", android.graphics.Color.rgb(139, 69, 19)),
            "fast_food" to LandmarkCategory("Fast Food", android.graphics.Color.rgb(255, 165, 0)),
            "bar" to LandmarkCategory("Bars", android.graphics.Color.rgb(128, 0, 128)),
            "bakery" to LandmarkCategory("Bakeries", android.graphics.Color.rgb(255, 182, 193)),

            "hospital" to LandmarkCategory("Hospitals", android.graphics.Color.RED),
            "pharmacy" to LandmarkCategory("Pharmacies", android.graphics.Color.rgb(0, 128, 0)),
            "clinic" to LandmarkCategory("Clinics", android.graphics.Color.rgb(255, 20, 147)),

            "gas_station" to LandmarkCategory("Gas Stations", android.graphics.Color.rgb(255, 69, 0)),
            "parking" to LandmarkCategory("Parking", android.graphics.Color.GRAY),
            "bus_station" to LandmarkCategory("Bus Stations", android.graphics.Color.CYAN),

            "supermarket" to LandmarkCategory("Supermarkets", android.graphics.Color.rgb(50, 205, 50)),
            "convenience_store" to LandmarkCategory("Convenience Stores", android.graphics.Color.BLUE),
            "shopping_mall" to LandmarkCategory("Shopping Malls", android.graphics.Color.YELLOW),

            "bank" to LandmarkCategory("Banks", android.graphics.Color.rgb(0, 100, 0)),
            "atm" to LandmarkCategory("ATMs", android.graphics.Color.rgb(72, 61, 139)),
            "post_office" to LandmarkCategory("Post Offices", android.graphics.Color.rgb(30, 144, 255)),
            "police_station" to LandmarkCategory("Police Stations", android.graphics.Color.rgb(25, 25, 112)),

            "school" to LandmarkCategory("Schools", android.graphics.Color.rgb(255, 215, 0)),
            "university" to LandmarkCategory("Universities", android.graphics.Color.rgb(186, 85, 211)),
            "library" to LandmarkCategory("Libraries", android.graphics.Color.rgb(160, 82, 45)),
            "church" to LandmarkCategory("Churches", android.graphics.Color.rgb(138, 43, 226)),

            "movie_theater" to LandmarkCategory("Movie Theaters", android.graphics.Color.rgb(220, 20, 60)),
            "gym" to LandmarkCategory("Gyms", android.graphics.Color.rgb(255, 105, 180)),
            "park" to LandmarkCategory("Parks", android.graphics.Color.GREEN),
            "tourist_attraction" to LandmarkCategory("Tourist Attractions", android.graphics.Color.rgb(0, 255, 127)),

            "hotel" to LandmarkCategory("Hotels", android.graphics.Color.BLUE)
        )
    }
}

/**
 * Data class for landmark category information
 */
data class LandmarkCategory(
    val displayName: String,
    val markerColor: Int
)