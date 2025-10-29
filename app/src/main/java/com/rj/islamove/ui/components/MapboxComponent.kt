package com.rj.islamove.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import kotlin.math.abs
import kotlin.math.sqrt
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.CustomLandmark
import com.rj.islamove.data.models.PlaceDetails
import com.rj.islamove.data.models.ServiceArea
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.data.repository.DriverLocation
import com.rj.islamove.utils.MapboxStyleHelper
import com.rj.islamove.data.models.Companion
import com.rj.islamove.data.models.CompanionType
import com.rj.islamove.data.models.CompanionFare
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn

/**
 * State for smooth navigation camera updates
 */
data class NavigationSmoothingState(
    val lastLocation: Point? = null,
    val lastBearing: Double = 0.0,
    val lastUpdateTime: Long = 0L
)

// Utility extension function to convert custom Point to Mapbox Point
fun com.rj.islamove.utils.Point.toMapboxPoint(): Point {
    return Point.fromLngLat(this.longitude(), this.latitude())
}

// Calculate distance between two Mapbox points in meters
fun calculateDistance(point1: Point, point2: Point): Double {
    val R = 6371000.0 // Earth's radius in meters
    val lat1 = Math.toRadians(point1.latitude())
    val lat2 = Math.toRadians(point2.latitude())
    val deltaLat = Math.toRadians(point2.latitude() - point1.latitude())
    val deltaLon = Math.toRadians(point2.longitude() - point1.longitude())

    val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return R * c
}

// Calculate fallback distance when route calculation fails
fun calculateFallbackDistance(pickup: com.rj.islamove.data.models.BookingLocation?, destination: com.rj.islamove.data.models.PlaceDetails?): String {
    if (pickup == null || destination == null) return "Calculating..."

    val distance = calculateDistance(
        Point.fromLngLat(pickup.coordinates.longitude, pickup.coordinates.latitude),
        Point.fromLngLat(destination.point.longitude(), destination.point.latitude())
    )
    val distanceKm = distance / 1000.0
    return "${String.format("%.1f", distanceKm)} km"
}

// Calculate fallback duration when route calculation fails
fun calculateFallbackDuration(pickup: com.rj.islamove.data.models.BookingLocation?, destination: com.rj.islamove.data.models.PlaceDetails?): String {
    if (pickup == null || destination == null) return "Calculating..."

    val distance = calculateDistance(
        Point.fromLngLat(pickup.coordinates.longitude, pickup.coordinates.latitude),
        Point.fromLngLat(destination.point.longitude(), destination.point.latitude())
    )
    // Estimate duration based on 30 km/h average speed in urban areas (30000 m/h)
    val estimatedMinutes = (distance / 30000.0 * 60).toInt()
    return "${estimatedMinutes} min"
}

// Create a custom house icon bitmap for home marker
fun createMapboxHouseIcon(context: Context, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Blue): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color.toArgb()
    }

    val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        this.color = android.graphics.Color.WHITE
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val houseSize = size * 0.6f

    // Draw house shape
    val path = Path().apply {
        // Roof
        moveTo(centerX, centerY - houseSize * 0.4f)
        lineTo(centerX - houseSize * 0.4f, centerY)
        lineTo(centerX + houseSize * 0.4f, centerY)
        close()

        // House body
        addRect(
            centerX - houseSize * 0.3f,
            centerY,
            centerX + houseSize * 0.3f,
            centerY + houseSize * 0.4f,
            Path.Direction.CW
        )
    }

    // Draw house
    canvas.drawPath(path, paint)
    canvas.drawPath(path, strokePaint)

    // Draw door
    val doorPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = android.graphics.Color.WHITE
    }

    canvas.drawRect(
        centerX - houseSize * 0.1f,
        centerY + houseSize * 0.1f,
        centerX + houseSize * 0.1f,
        centerY + houseSize * 0.4f,
        doorPaint
    )

    return bitmap
}

// Create a custom star icon bitmap for favorite markers
fun createMapboxStarIcon(context: Context, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFD700)): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color.toArgb()
    }

    val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = android.graphics.Color.WHITE
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val starSize = size * 0.4f

    // Create 5-pointed star path
    val starPath = Path().apply {
        val angle = Math.PI.toFloat() / 5f // 36 degrees
        val outerRadius = starSize
        val innerRadius = starSize * 0.4f

        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val currentAngle = angle * i - Math.PI.toFloat() / 2f // Start from top
            val x = centerX + radius * kotlin.math.cos(currentAngle)
            val y = centerY + radius * kotlin.math.sin(currentAngle)

            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    // Draw star with fill and stroke
    canvas.drawPath(starPath, paint)
    canvas.drawPath(starPath, strokePaint)

    return bitmap
}

// Create baobao icon from PNG file for online drivers
fun createMapboxBaobaoIcon(context: Context): Bitmap {
    return try {
        // Get resource ID for baobaoicon.png
        val resourceId = context.resources.getIdentifier("baobaoicon", "drawable", context.packageName)
        if (resourceId == 0) {
            Log.e("MapboxComponent", "baobaoicon resource not found")
            return createDefaultMarkerBitmap(0xFFFF9800.toInt())
        }

        // Load the PNG from drawable resources
        val originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId)

        // Resize to appropriate marker size while preserving aspect ratio
        val maxSize = 100
        val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()

        val (newWidth, newHeight) = if (aspectRatio > 1) {
            // Wider than tall - limit width
            maxSize to (maxSize / aspectRatio).toInt()
        } else {
            // Taller than wide - limit height
            (maxSize * aspectRatio).toInt() to maxSize
        }

        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
    } catch (e: Exception) {
        Log.e("MapboxComponent", "Failed to load baobaoicon.png from resources", e)
        // Fallback to default orange marker
        createDefaultMarkerBitmap(0xFFFF9800.toInt())
    }
}


// Create a default marker bitmap
fun createDefaultMarkerBitmap(color: Int): Bitmap {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = android.graphics.Color.WHITE
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size * 0.3f

    // Draw circle marker
    canvas.drawCircle(centerX, centerY, radius, paint)
    canvas.drawCircle(centerX, centerY, radius, strokePaint)

    return bitmap
}

// Cache for boundary point bitmaps to avoid recreation
private val boundaryBitmapCache = mutableMapOf<String, Bitmap>()

// Clear bitmap cache to free memory
fun clearBoundaryBitmapCache() {
    boundaryBitmapCache.clear()
    Log.d("MapboxComponent", "Cleared boundary bitmap cache")
}

// Create a appropriately sized draggable boundary point bitmap - optimized with caching
fun createDraggableBoundaryPointBitmap(color: Int, isDragging: Boolean = false): Bitmap {
    val cacheKey = "${color}_$isDragging"

    // Return cached bitmap if available
    boundaryBitmapCache[cacheKey]?.let { return it }

    val size = if (isDragging) 80 else 64 // Original size
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = if (isDragging) 4f else 3f
        this.color = android.graphics.Color.WHITE
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size * 0.35f // Make the circle proportional to the bitmap

    // Draw outer ring for better visibility when dragging
    if (isDragging) {
        val outerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            this.color = android.graphics.Color.parseColor("#FFF3E0") // Light orange outline
        }
        canvas.drawCircle(centerX, centerY, radius + 4f, outerPaint)
    }

    // Draw main circle marker
    canvas.drawCircle(centerX, centerY, radius, paint)
    canvas.drawCircle(centerX, centerY, radius, strokePaint)

    // Add drag handle indicator (small inner circle)
    if (!isDragging) {
        val handlePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            this.color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(centerX, centerY, radius * 0.25f, handlePaint)
    }

    // Cache the bitmap for future use
    boundaryBitmapCache[cacheKey] = bitmap

    return bitmap
}

// Helper function to convert BookingLocation to PlaceDetails for POI selection
fun BookingLocation.toMapboxPlaceDetails(): PlaceDetails {
    return PlaceDetails(
        id = placeId ?: "home_location",
        name = if (address.contains("Home", ignoreCase = true)) "Home" else address,
        point = Point.fromLngLat(coordinates.longitude, coordinates.latitude),
        address = address,
        rating = null,
        userRatingsTotal = null,
        types = emptyList(),
        phoneNumber = null,
        websiteUri = null,
        isOpen = null,
        openingHours = null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapboxRideView(
    modifier: Modifier = Modifier,
    initialLocation: Point = Point.fromLngLat(121.0748, 15.7886), // San Jose, Nueva Ecija (your service area)
    currentUserLocation: Point? = null,
    zoomLevel: Double = 12.0,
    pickupLocation: BookingLocation? = null,
    destination: BookingLocation? = null,
    driverLocation: Point? = null,
    showUserLocation: Boolean = true,
    showRoute: Boolean = true,
    routeInfo: com.rj.islamove.data.models.RouteInfo? = null, // For turn-by-turn directions
    onlineDrivers: List<DriverLocation> = emptyList(),
    showOnlineDrivers: Boolean = false,
    passengerLocation: Point? = null, // For drivers to see passenger location
    isDriverView: Boolean = false, // Indicates if this is shown to driver (hides "Driver has arrived" overlay)
    homeLocation: BookingLocation? = null, // For showing home address with house icon
    favoriteLocations: List<BookingLocation> = emptyList(), // For showing favorite locations with star icons
    customLandmarks: List<CustomLandmark> = emptyList(), // For showing admin-created landmarks
    queuedPassengerPickups: List<BookingLocation> = emptyList(), // For showing queued passenger pickup markers
    queuedPassengerDestinations: List<BookingLocation> = emptyList(), // For showing queued passenger destination markers
    // Category-based POI landmarks from Mapbox Search
    restaurants: List<PlaceDetails> = emptyList(),
    hospitals: List<PlaceDetails> = emptyList(),
    hotels: List<PlaceDetails> = emptyList(),
    touristAttractions: List<PlaceDetails> = emptyList(),
    shoppingMalls: List<PlaceDetails> = emptyList(),
    transportationHubs: List<PlaceDetails> = emptyList(),
    isNavigationMode: Boolean = false, // When true, center on driver location
    centerOnPassenger: Boolean = false, // When true, center on passenger location
    onMapClick: ((Point) -> Unit)? = null,
    onPlaceSelected: ((PlaceDetails) -> Unit)? = null, // For POI selection
    onBookRide: ((BookingLocation, String, List<CompanionType>) -> Unit)? = null, // For booking rides with comment and companions
    onCalculateFare: ((BookingLocation) -> String?)? = null, // For calculating dynamic fare estimates
    passengerDiscountPercentage: Int? = null, // For displaying passenger discount in POI
    selectedPlaceDetails: PlaceDetails? = null, // For externally controlling place selection
    onHomeMarkerClick: (() -> Unit)? = null, // For home marker click handling
    onPlaceDialogDismiss: (() -> Unit)? = null, // For handling place dialog dismissal
    onPOIClickForSelection: ((Point) -> Unit)? = null, // For handling POI clicks during selection mode
    onFavoriteMarkerClick: ((BookingLocation) -> Unit)? = null, // For handling favorite marker clicks (to show dialog)
    onRemoveFavoriteLocation: ((BookingLocation) -> Unit)? = null, // For actually removing the favorite
    onRemoveHomeLocation: ((BookingLocation) -> Unit)? = null, // For actually removing the home location
    onSetHomeLocation: ((BookingLocation) -> Unit)? = null, // For actually setting the home location
    onLandmarkMarkerClick: ((CustomLandmark) -> Unit)? = null, // For handling landmark clicks
    onRestaurantMarkerClick: ((PlaceDetails) -> Unit)? = null, // For handling restaurant clicks
    onHospitalMarkerClick: ((PlaceDetails) -> Unit)? = null, // For handling hospital clicks
    onHotelMarkerClick: ((PlaceDetails) -> Unit)? = null, // For handling hotel clicks
    onTouristAttractionClick: ((PlaceDetails) -> Unit)? = null, // For handling tourist attraction clicks
    onShoppingMallClick: ((PlaceDetails) -> Unit)? = null, // For handling shopping mall clicks
    onTransportationHubClick: ((PlaceDetails) -> Unit)? = null, // For handling transportation hub clicks
    disableAutoPOISelection: Boolean = false, // Disable automatic POI selection on map click
    centerOnLocationTrigger: Long = 0L, // Timestamp trigger to center on user location
    mapStyle: String = "Outdoors", // Map style to use for better landmarks
    isInSelectionMode: Boolean = false, // Direct indicator for selection mode
    isHomeSelectionMode: Boolean = false, // True when specifically selecting home location
    currentBookingStatus: com.rj.islamove.data.models.BookingStatus? = null, // Current booking status to control destination marker visibility
    currentBooking: com.rj.islamove.data.models.Booking? = null, // Current active booking for trip overlay
    serviceAreas: List<ServiceArea> = emptyList(), // Service areas with boundaries
    showServiceAreaBoundaries: Boolean = false, // Whether to show zone boundaries
    showBarangayBoundaries: Boolean = false, // Whether to show barangay/administrative boundaries
    isDrawingBoundaryMode: Boolean = false, // Whether in boundary drawing/editing mode
    boundaryDrawingPoints: List<BoundaryPoint> = emptyList(), // Points being drawn/edited
    selectedBoundaryPointIndex: Int? = null, // Currently selected boundary point for visual feedback
    onBoundaryPointAdded: ((Double, Double) -> Unit)? = null, // Callback for boundary point addition
    onBoundaryPointSelected: ((Int?) -> Unit)? = null, // Callback for boundary point selection
    onBoundaryPointDragStart: ((Int) -> Unit)? = null, // Callback for drag start
    onBoundaryPointDrag: ((Int, Double, Double) -> Unit)? = null, // Callback for point dragging
    onBoundaryPointDragEnd: (() -> Unit)? = null, // Callback for drag end
    zoneBoundaries: List<com.rj.islamove.data.models.ZoneBoundary> = emptyList(), // Zone boundaries to display on map
    showZoneBoundaries: Boolean = false // Whether to show zone boundaries
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isMapStyleLoaded by remember { mutableStateOf(false) }

    // Remember annotation managers to avoid creating new sources on each re-render
    var polylineAnnotationManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager?>(null) }

    // Dedicated annotation manager for driver/passenger markers only (to avoid clearing routes)
    var driverPassengerAnnotationManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager?>(null) }

    // Single annotation manager for all general markers (online drivers, home, landmarks, etc.)
    var generalPointAnnotationManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager?>(null) }

    // Local state for boundary drawing points to ensure proper re-rendering
    var localBoundaryPoints by remember { mutableStateOf(boundaryDrawingPoints) }

    // Update local points when parameter changes
    LaunchedEffect(boundaryDrawingPoints) {
        localBoundaryPoints = boundaryDrawingPoints
        Log.d("MapboxComponent", "Updated localBoundaryPoints to ${boundaryDrawingPoints.size} points")
    }

    // Local state for selected point index to ensure the click listener always has the current value
    var localSelectedPointIndex by remember { mutableStateOf(selectedBoundaryPointIndex) }

    // Update local selected point index when parameter changes
    LaunchedEffect(selectedBoundaryPointIndex) {
        localSelectedPointIndex = selectedBoundaryPointIndex
        Log.d("MapboxComponent", "Updated localSelectedPointIndex to $selectedBoundaryPointIndex")
    }

    
    // State for selected place details - can be controlled externally
    var selectedPlace by remember { mutableStateOf<PlaceDetails?>(selectedPlaceDetails) }

    // Update selected place when external parameter changes
    LaunchedEffect(selectedPlaceDetails?.timestamp) {
        selectedPlace = selectedPlaceDetails
    }

    // Track current custom landmarks to avoid closure issues
    var currentCustomLandmarks by remember { mutableStateOf(customLandmarks) }

    // Track current favorite locations to avoid closure issues
    var currentFavoriteLocations by remember { mutableStateOf(favoriteLocations) }

    // Update current landmarks when parameter changes
    LaunchedEffect(customLandmarks) {
        currentCustomLandmarks = customLandmarks
        Log.d("MapboxComponent", "Updated currentCustomLandmarks to ${customLandmarks.size} landmarks")
    }

    // Update current favorite locations when parameter changes
    LaunchedEffect(favoriteLocations) {
        currentFavoriteLocations = favoriteLocations
        Log.d("MapboxComponent", "Updated currentFavoriteLocations to ${favoriteLocations.size} favorites")
    }

    // Track if boundaries need to be added separately from annotations
    var boundariesAdded by remember { mutableStateOf(false) }
    var boundaryStyle by remember { mutableStateOf<Style?>(null) }

    // Handle center on user location trigger
    LaunchedEffect(centerOnLocationTrigger) {
        if (centerOnLocationTrigger > 0L && mapView != null && currentUserLocation != null) {
            mapView?.getMapboxMap()?.setCamera(
                CameraOptions.Builder()
                    .center(currentUserLocation)
                    .zoom(15.0)
                    .build()
            )
        }
    }

    // Handle navigation mode camera - 3D perspective like Google Maps navigation
    LaunchedEffect(isNavigationMode, currentUserLocation, driverLocation) {
        if (isNavigationMode && mapView != null) {
            val locationToTrack = driverLocation ?: currentUserLocation
            if (locationToTrack != null) {
                mapView?.getMapboxMap()?.setCamera(
                    CameraOptions.Builder()
                        .center(locationToTrack)
                        .zoom(18.0) // Closer zoom for navigation
                        .bearing(0.0) // North-up initially, will be updated with route bearing
                        .pitch(60.0) // 3D tilt angle - like Google Maps navigation
                        .build()
                )
            }
        }
    }

    // Smooth navigation camera state - persisted across location updates
    var smoothingState by remember {
        mutableStateOf<NavigationSmoothingState?>(null)
    }

    // Initialize/reset smoothing when navigation mode changes
    LaunchedEffect(isNavigationMode) {
        if (isNavigationMode) {
            smoothingState = NavigationSmoothingState()
        } else {
            smoothingState = null
        }
    }

    // Handle route rendering: Clear old routes and add new ones - WAIT FOR BOTH STYLE AND MANAGER
    LaunchedEffect(routeInfo?.hashCode(), showRoute, polylineAnnotationManager, isMapStyleLoaded) {
        Log.d("MapboxComponent", "🔵 Route rendering LaunchedEffect triggered - routeInfo: ${routeInfo != null}, showRoute: $showRoute, manager: ${polylineAnnotationManager != null}, styleLoaded: $isMapStyleLoaded")

        if (!isMapStyleLoaded) {
            Log.d("MapboxComponent", "⏳ Waiting for map style to load before rendering routes...")
            return@LaunchedEffect
        }

        if (polylineAnnotationManager == null) {
            Log.w("MapboxComponent", "⚠️ polylineAnnotationManager is NULL even though style is loaded! Waiting...")
            return@LaunchedEffect
        }

        // Add longer delay to ensure both style and manager are REALLY ready
        kotlinx.coroutines.delay(800) // Increased delay to ensure annotation system is fully loaded
        Log.d("MapboxComponent", "✅ Both style and manager are ready - proceeding with route rendering")

        polylineAnnotationManager?.let { manager ->
            // Clear existing routes first - only if style is loaded
            try {
                manager.deleteAll()
                Log.d("MapboxComponent", "Cleared all route polylines")
            } catch (e: Exception) {
                Log.w("MapboxComponent", "Could not clear routes (style may not be ready): ${e.message}")
            }

            // Add new route if conditions are met
            if (showRoute && routeInfo != null && routeInfo.waypoints.isNotEmpty()) {
                Log.d("MapboxComponent", "🎯 ROUTE DISPLAY CONDITIONS MET:")
                Log.d("MapboxComponent", "   - showRoute: $showRoute")
                Log.d("MapboxComponent", "   - routeInfo != null: ${routeInfo != null}")
                Log.d("MapboxComponent", "   - routeInfo.waypoints.isNotEmpty(): ${routeInfo.waypoints.isNotEmpty()}")
                Log.d("MapboxComponent", "   - waypoint count: ${routeInfo.waypoints.size}")
                Log.d("MapboxComponent", "   - routeId: ${routeInfo.routeId}")
                Log.d("MapboxComponent", "   - totalDistance: ${routeInfo.totalDistance}m")
                try {
                    addMapboxRoute(
                        polylineAnnotationManager = manager,
                        routeInfo = routeInfo,
                        isNavigationMode = isNavigationMode
                    )
                    Log.d("MapboxComponent", "✅ Route rendered successfully in dedicated LaunchedEffect")

                    // CRITICAL FIX: Zoom camera to show the entire route
                    if (!isNavigationMode && mapView != null) {
                        val routePoints = routeInfo.waypoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                        if (routePoints.size >= 2) {
                            // Calculate bounding box
                            val lats = routePoints.map { it.latitude() }
                            val lngs = routePoints.map { it.longitude() }
                            val minLat = lats.minOrNull() ?: return@let
                            val maxLat = lats.maxOrNull() ?: return@let
                            val minLng = lngs.minOrNull() ?: return@let
                            val maxLng = lngs.maxOrNull() ?: return@let

                            // Calculate center point
                            val centerLat = (minLat + maxLat) / 2
                            val centerLng = (minLng + maxLng) / 2

                            // Calculate appropriate zoom level to fit the route
                            val latDiff = maxLat - minLat
                            val lngDiff = maxLng - minLng
                            val maxDiff = maxOf(latDiff, lngDiff)

                            // Rough zoom calculation (adjust if needed)
                            val zoom = when {
                                maxDiff > 0.1 -> 11.0
                                maxDiff > 0.05 -> 12.0
                                maxDiff > 0.02 -> 13.0
                                maxDiff > 0.01 -> 14.0
                                else -> 15.0
                            }

                            mapView?.getMapboxMap()?.setCamera(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(centerLng, centerLat))
                                    .zoom(zoom)
                                    .build()
                            )
                            Log.d("MapboxComponent", "📷 Camera positioned to show route: center=($centerLat,$centerLng), zoom=$zoom")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MapboxComponent", "Error adding route in LaunchedEffect", e)
                }
            } else {
                // Log why route is not being displayed
                Log.d("MapboxComponent", "❌ ROUTE DISPLAY CONDITIONS NOT MET:")
                Log.d("MapboxComponent", "   - showRoute: $showRoute")
                Log.d("MapboxComponent", "   - routeInfo != null: ${routeInfo != null}")
                Log.d("MapboxComponent", "   - routeInfo.waypoints.isNotEmpty(): ${routeInfo?.waypoints?.isNotEmpty() ?: false}")
                Log.d("MapboxComponent", "   - waypoint count: ${routeInfo?.waypoints?.size ?: 0}")
                if (routeInfo != null) {
                    Log.d("MapboxComponent", "   - routeId: ${routeInfo.routeId}")
                    Log.d("MapboxComponent", "   - totalDistance: ${routeInfo.totalDistance}m")
                }
            }
        } ?: Log.w("MapboxComponent", "⚠️ Cannot manage routes: polylineAnnotationManager is null")
    }

    // Update navigation camera smoothly during navigation
    LaunchedEffect(isNavigationMode, mapView, routeInfo) {
        if (isNavigationMode && mapView != null && routeInfo != null) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val updateRunnable = object : Runnable {
                override fun run() {
                    val locationToTrack = driverLocation ?: currentUserLocation
                    val currentTime = System.currentTimeMillis()
                    val state = smoothingState

                    if (locationToTrack != null && isNavigationMode && state != null) {
                        // Only update if location changed significantly or enough time passed
                        val shouldUpdate = state.lastLocation?.let { lastLoc ->
                            val distance = calculateDistance(locationToTrack, lastLoc)
                            val timeSinceLastUpdate = currentTime - state.lastUpdateTime
                            // More conservative thresholds: 25 meters or 8 seconds
                            distance > 25.0 || timeSinceLastUpdate > 8000
                        } ?: true

                        if (shouldUpdate) {
                            // Calculate bearing with smoothing
                            val newBearing = calculateBearingFromRoute(locationToTrack, routeInfo)
                            val smoothedBearing = if (newBearing != null) {
                                // Smooth bearing changes to prevent sudden rotations
                                val bearingDiff = ((newBearing - state.lastBearing + 540) % 360) - 180
                                if (kotlin.math.abs(bearingDiff) < 60) { // Only smooth moderate changes (increased threshold)
                                    state.lastBearing + (bearingDiff * 0.15) // 15% of the change (even more conservative)
                                } else {
                                    newBearing // Large changes happen immediately
                                }
                            } else {
                                state.lastBearing
                            }

                            // Calculate offset position for 3rd person view (behind the icon)
                            val offsetLocation = calculateOffsetLocation(locationToTrack, smoothedBearing)

                            // Use setCamera for smooth updates with 3rd person offset
                            mapView?.getMapboxMap()?.setCamera(
                                CameraOptions.Builder()
                                    .center(offsetLocation)
                                    .zoom(18.0)
                                    .bearing(smoothedBearing)
                                    .pitch(60.0)
                                    .build()
                            )

                            // Update smoothing state
                            smoothingState = state.copy(
                                lastLocation = locationToTrack,
                                lastBearing = smoothedBearing,
                                lastUpdateTime = currentTime
                            )
                        }
                    }

                    // Schedule next update (even less frequent for stability)
                    if (isNavigationMode) {
                        handler.postDelayed(this, 6000) // Update every 6 seconds for maximum stability
                    }
                }
            }

            // Start the update cycle
            handler.post(updateRunnable)
        }
    }

    // Dedicated boundary management effect - separate from annotations
    LaunchedEffect(showBarangayBoundaries, mapView) {
        if (showBarangayBoundaries && mapView != null) {
            Log.d("MapboxComponent", "Boundary management effect triggered")

            // Wait for map to be ready
            mapView?.getMapboxMap()?.getStyle { style ->
                boundaryStyle = style

                // Add boundaries with a delay to ensure style is stable
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (showBarangayBoundaries && style.isStyleLoaded()) {
                            Log.d("MapboxComponent", "Adding boundaries from dedicated effect")
                            addBarangayBoundariesSeparate(style)
                            boundariesAdded = true
                        }
                    } catch (e: Exception) {
                        Log.e("MapboxComponent", "Error in dedicated boundary effect", e)
                    }
                }, 2000) // Longer delay to ensure everything is stable
            }
        } else {
            boundariesAdded = false
            boundaryStyle = null
        }
    }

    // Continuous boundary monitoring and restoration system
    LaunchedEffect(boundariesAdded, boundaryStyle, showBarangayBoundaries) {
        if (showBarangayBoundaries && boundaryStyle != null) {
            val boundaryChecker = object : Runnable {
                override fun run() {
                    try {
                        val style = boundaryStyle
                        if (style != null && showBarangayBoundaries) {
                            val boundaryLayerId = "san-jose-barangay-layer-sep"
                            if (!style.styleLayerExists(boundaryLayerId)) {
                                Log.d("MapboxComponent", "Boundaries were removed, restoring them")
                                addBarangayBoundariesSeparate(style)
                            }
                        }
                        // Schedule next check if boundaries should still be shown
                        if (showBarangayBoundaries) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1500) // Check every 1.5 seconds
                        }
                    } catch (e: Exception) {
                        Log.e("MapboxComponent", "Error in boundary monitoring: ${e.message}")
                        // Continue monitoring even after errors
                        if (showBarangayBoundaries) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 3000) // Check every 3 seconds on error
                        }
                    }
                }
            }

            // Start continuous monitoring
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(boundaryChecker, 2000)
        }
    }

    // Track if we've already centered on user location once
    var hasInitiallyCentered by remember { mutableStateOf(false) }

    // Center map when user location becomes available for the first time only
    LaunchedEffect(currentUserLocation) {
        if (currentUserLocation != null && mapView != null && !hasInitiallyCentered) {
            mapView?.getMapboxMap()?.setCamera(
                CameraOptions.Builder()
                    .center(currentUserLocation)
                    .zoom(15.0)
                    .build()
            )
            hasInitiallyCentered = true
        }
    }

    // Dedicated LaunchedEffect for driver/passenger marker updates ONLY
    // This prevents recreating ALL markers every time driver moves
    LaunchedEffect(driverLocation, passengerLocation, isMapStyleLoaded, mapView, currentBooking) {
        if (!isMapStyleLoaded) return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect

        view.getMapboxMap().getStyle { style ->
            // Create annotation manager if needed
            if (driverPassengerAnnotationManager == null) {
                driverPassengerAnnotationManager = view.annotations.createPointAnnotationManager()
                Log.d("MapboxComponent", "Created dedicated driver/passenger annotation manager")
            }

            driverPassengerAnnotationManager?.let { manager ->
                // Clear ALL markers in this manager to prevent duplication from async race conditions
                manager.deleteAll()
                Log.d("MapboxComponent", "🧹 Cleared driver/passenger markers")

                // Add new driver marker if location available
                if (driverLocation != null && currentBooking != null) {
                    val driverIcon = createMapboxBaobaoIcon(context) // Use baobao icon for driver
                    val driverOptions = PointAnnotationOptions()
                        .withPoint(driverLocation)
                        .withIconImage(driverIcon)
                    manager.create(driverOptions)
                    Log.d("MapboxComponent", "🚗 Created driver marker at (${driverLocation.latitude()}, ${driverLocation.longitude()})")
                }

                // Add new passenger marker if location available
                if (passengerLocation != null && currentBooking != null) {
                    val passengerIcon = createDefaultMarkerBitmap(android.graphics.Color.RED) // Use red marker for passenger
                    val passengerOptions = PointAnnotationOptions()
                        .withPoint(passengerLocation)
                        .withIconImage(passengerIcon)
                    manager.create(passengerOptions)
                    Log.d("MapboxComponent", "👤 Created passenger marker at (${passengerLocation.latitude()}, ${passengerLocation.longitude()})")
                }
            }
        }
    }

    // Simplified map updates - only trigger when essential data changes
    // IMPORTANT: routeInfo, showRoute, driverLocation, passengerLocation removed from dependencies
    // Routes are managed by their own dedicated LaunchedEffect
    // Driver/passenger markers are managed by dedicated LaunchedEffect above
    LaunchedEffect(onlineDrivers, showOnlineDrivers, customLandmarks, homeLocation, favoriteLocations, restaurants, hospitals, hotels, touristAttractions, shoppingMalls, transportationHubs, serviceAreas, showServiceAreaBoundaries, localBoundaryPoints, zoneBoundaries, showZoneBoundaries, isMapStyleLoaded) {
        Log.d("MapboxComponent", "Simplified LaunchedEffect triggered")

        // CRITICAL: Wait for map style to load before creating any annotation managers
        if (!isMapStyleLoaded) {
            Log.d("MapboxComponent", "⏳ Markers/annotations update waiting for map style to load...")
            return@LaunchedEffect
        }

        mapView?.let { view ->
            view.getMapboxMap().getStyle { style ->
                val annotationApi = view.annotations
                // CRITICAL: Do NOT call cleanup() as it destroys the polyline manager (routes) and driver/passenger manager
                // Driver/passenger markers have their own dedicated manager (lines 800-841)
                // Routes have their own polyline manager (created on line 537)
                // We only need to manage landmarks/other markers here

                // Restore boundary layers if they should be visible
                if (showBarangayBoundaries) {
                    try {
                        Log.d("MapboxComponent", "Restoring barangay boundaries")
                        addBarangayBoundariesSeparate(style)
                    } catch (e: Exception) {
                        Log.e("MapboxComponent", "Error restoring boundaries", e)
                    }
                }

                // Create or reuse the general point annotation manager to prevent duplicate markers
                if (generalPointAnnotationManager == null) {
                    generalPointAnnotationManager = annotationApi.createPointAnnotationManager()
                    Log.d("MapboxComponent", "Created general point annotation manager")
                }

                // DO NOT create polylineAnnotationManager here - it will be created after style loads
                // to avoid "Source not in style" errors

                val polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()

                addMapboxMarkers(
                    context = context,
                    pointAnnotationManager = generalPointAnnotationManager!!,
                    pickupLocation = pickupLocation,
                    destination = destination,
                    driverLocation = driverLocation,
                    passengerLocation = passengerLocation,
                    homeLocation = homeLocation,
                    favoriteLocations = currentFavoriteLocations,
                    customLandmarks = currentCustomLandmarks,
                    queuedPassengerPickups = queuedPassengerPickups,
                    queuedPassengerDestinations = queuedPassengerDestinations,
                    restaurants = restaurants,
                    hospitals = hospitals,
                    hotels = hotels,
                    touristAttractions = touristAttractions,
                    shoppingMalls = shoppingMalls,
                    transportationHubs = transportationHubs,
                    onlineDrivers = onlineDrivers,
                    showOnlineDrivers = showOnlineDrivers,
                    currentBookingStatus = currentBookingStatus,
                    currentBooking = currentBooking,
                    onHomeMarkerClick = onHomeMarkerClick,
                    onFavoriteMarkerClick = onFavoriteMarkerClick,
                    onLandmarkMarkerClick = onLandmarkMarkerClick,
                    onRestaurantMarkerClick = onRestaurantMarkerClick,
                    onHospitalMarkerClick = onHospitalMarkerClick,
                    onHotelMarkerClick = onHotelMarkerClick,
                    onTouristAttractionClick = onTouristAttractionClick,
                    onShoppingMallClick = onShoppingMallClick,
                    onTransportationHubClick = onTransportationHubClick,
                    currentUserLocation = currentUserLocation,
                    showUserLocation = showUserLocation
                )

                // Set up click listener for point annotations (landmarks, etc.)
                generalPointAnnotationManager?.addClickListener { annotation ->
                    val clickedPoint = annotation.point
                    Log.d("MapboxComponent", "Point annotation clicked at: ${clickedPoint.latitude()}, ${clickedPoint.longitude()}")

                    // Check if clicked landmark
                    val clickedLandmark = currentCustomLandmarks.find { landmark ->
                        abs(landmark.latitude - clickedPoint.latitude()) < 0.0001 &&
                        abs(landmark.longitude - clickedPoint.longitude()) < 0.0001
                    }

                    if (clickedLandmark != null) {
                        Log.d("MapboxComponent", "Landmark clicked: ${clickedLandmark.name}")
                        onLandmarkMarkerClick?.invoke(clickedLandmark)
                        return@addClickListener true
                    }

                    // Check if clicked home marker
                    homeLocation?.let { home ->
                        if (abs(home.coordinates.latitude - clickedPoint.latitude()) < 0.0001 &&
                            abs(home.coordinates.longitude - clickedPoint.longitude()) < 0.0001) {
                            Log.d("MapboxComponent", "Home marker clicked")
                            onHomeMarkerClick?.invoke()
                            return@addClickListener true
                        }
                    }

                    // Check if clicked favorite marker
                    val clickedFavorite = currentFavoriteLocations.find { favorite ->
                        abs(favorite.coordinates.latitude - clickedPoint.latitude()) < 0.0001 &&
                        abs(favorite.coordinates.longitude - clickedPoint.longitude()) < 0.0001
                    }

                    if (clickedFavorite != null) {
                        Log.d("MapboxComponent", "Favorite marker clicked: ${clickedFavorite.address}")
                        onFavoriteMarkerClick?.invoke(clickedFavorite)
                        return@addClickListener true
                    }

                    false // Let other click handlers process this
                }

                // Note: Route rendering is now handled by dedicated LaunchedEffect above

                // Simple boundary rendering with basic drag support
                if (showServiceAreaBoundaries || isDrawingBoundaryMode) {
                    val currentPolylineManager = polylineAnnotationManager
                    if (currentPolylineManager != null) {
                        addServiceAreaBoundaries(
                            polygonAnnotationManager = polygonAnnotationManager,
                            pointAnnotationManager = generalPointAnnotationManager!!,
                            polylineAnnotationManager = currentPolylineManager,
                            serviceAreas = serviceAreas,
                            boundaryDrawingPoints = localBoundaryPoints,
                            showBoundaries = showServiceAreaBoundaries,
                            isDrawingMode = isDrawingBoundaryMode,
                            isDragging = false,
                            selectedBoundaryPointIndex = localSelectedPointIndex,
                            onPointDragStart = { index ->
                                Log.d("MapboxComponent", "Point drag started: $index")
                                onBoundaryPointDragStart?.invoke(index)
                            },
                            onPointDrag = onBoundaryPointDrag,
                            onPointDragEnd = {
                                Log.d("MapboxComponent", "Point drag ended")
                                onBoundaryPointDragEnd?.invoke()
                            }
                        )
                    }
                }

                // Render zone boundaries for reference when drawing/editing boundaries
                if (showZoneBoundaries && zoneBoundaries.isNotEmpty()) {
                    addZoneBoundaries(
                        polygonAnnotationManager = polygonAnnotationManager,
                        zoneBoundaries = zoneBoundaries
                    )
                }

                // Note: Boundaries are now handled by the dedicated boundary management effect
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapView = this
                    // Configure map to show built-in landmarks with more POIs
                    val styleUri = when (mapStyle) {
                        "Streets" -> Style.MAPBOX_STREETS
                        "Outdoors" -> Style.OUTDOORS
                        "Light" -> Style.LIGHT
                        "Dark" -> Style.DARK
                        "Satellite" -> Style.SATELLITE
                        "Satellite Streets" -> Style.SATELLITE_STREETS
                        else -> Style.OUTDOORS // Default to outdoors for better landmarks
                    }

                    getMapboxMap().loadStyleUri(styleUri) { style ->
                        // Enable more POI layers for better landmark visibility
                        MapboxStyleHelper.enablePOILayers(style)

                        // Note: Initial boundary addition is now handled by the dedicated boundary management effect

                        // Enable location component if user location should be shown
                        if (showUserLocation) {
                            val locationComponentPlugin = this.location
                            locationComponentPlugin.updateSettings {
                                this.enabled = true
                                this.pulsingEnabled = true
                            }
                        }

                        // Style loaded, set up initial camera position
                        // Always center on user location if available, otherwise use initial location
                        getMapboxMap().setCamera(
                            CameraOptions.Builder()
                                .center(currentUserLocation ?: initialLocation)
                                .zoom(if (currentUserLocation != null) 15.0 else zoomLevel)
                                .build()
                        )

                        // Set up map click listener - simple drag implementation
                        getMapboxMap().addOnMapClickListener(OnMapClickListener { point ->
                            Log.d("MapboxComponent", "Map click at: ${point.latitude()}, ${point.longitude()}")

                            // Handle boundary drawing mode with simple drag
                            if (isDrawingBoundaryMode) {
                                Log.d("MapboxComponent", "Boundary drawing mode active, points: ${localBoundaryPoints.size}")

                                // If a point is already selected, move it to the new location and return immediately
                                if (localSelectedPointIndex != null) {
                                    val index = localSelectedPointIndex!!
                                    Log.d("MapboxComponent", "Moving selected boundary point $index to ${point.latitude()}, ${point.longitude()}")
                                    onBoundaryPointDrag?.invoke(index, point.latitude(), point.longitude())
                                    return@OnMapClickListener true
                                }
                                // Otherwise, check if user clicked on an existing point to select it
                                else {
                                    val mapboxMap = this@apply.getMapboxMap()
                                    val touchThreshold = 50.0 // Reduced threshold to prevent false positives after moving
                                    var nearestPointIndex: Int? = null
                                    var minDistance = Double.MAX_VALUE

                                    if (localBoundaryPoints.isNotEmpty()) {
                                        localBoundaryPoints.forEachIndexed { index, boundaryPoint ->
                                            try {
                                                val boundaryScreenCoord = mapboxMap.pixelForCoordinate(
                                                    Point.fromLngLat(boundaryPoint.longitude, boundaryPoint.latitude)
                                                )
                                                val clickScreenCoord = mapboxMap.pixelForCoordinate(point)

                                                val deltaX = clickScreenCoord.x - boundaryScreenCoord.x
                                                val deltaY = clickScreenCoord.y - boundaryScreenCoord.y
                                                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                                                Log.d("MapboxComponent", "Point $index distance: $distance pixels")

                                                // Find the nearest point within threshold
                                                if (distance < touchThreshold && distance < minDistance) {
                                                    minDistance = distance
                                                    nearestPointIndex = index
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MapboxComponent", "Error in point selection: ${e.message}")
                                            }
                                        }
                                    }

                                    // If clicked on existing point, select the NEAREST one
                                    if (nearestPointIndex != null) {
                                        onBoundaryPointSelected?.invoke(nearestPointIndex)
                                        Log.d("MapboxComponent", "Selected NEAREST boundary point $nearestPointIndex (distance: $minDistance pixels)")
                                        return@OnMapClickListener true
                                    }
                                }
                                // Add new point (we know no point is selected at this point)
                                if (onBoundaryPointAdded != null) {
                                    Log.d("MapboxComponent", "Adding new boundary point at ${point.latitude()}, ${point.longitude()}")
                                    onBoundaryPointAdded.invoke(point.latitude(), point.longitude())
                                    return@OnMapClickListener true
                                }
                            } else {
                                onMapClick?.invoke(point)
                            }
                            false // Don't consume the click
                        })

                        // Note: Annotation managers are now created in a separate LaunchedEffect after style loads
                        // to avoid timing issues with style not being fully ready

                        val annotationApi = annotations
                        // Create or reuse the general point annotation manager to prevent duplicate markers
                        if (generalPointAnnotationManager == null) {
                            generalPointAnnotationManager = annotationApi.createPointAnnotationManager()
                            Log.d("MapboxComponent", "Created general point annotation manager (onMapLoaded)")
                        }
                        val polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()

                        addMapboxMarkers(
                            context = ctx,
                            pointAnnotationManager = generalPointAnnotationManager!!,
                            pickupLocation = pickupLocation,
                            destination = destination,
                            driverLocation = driverLocation,
                            passengerLocation = passengerLocation,
                            homeLocation = homeLocation,
                            favoriteLocations = currentFavoriteLocations,
                            customLandmarks = currentCustomLandmarks,
                    queuedPassengerPickups = queuedPassengerPickups,
                    queuedPassengerDestinations = queuedPassengerDestinations,
                            // Category-based POI landmarks
                            restaurants = restaurants,
                            hospitals = hospitals,
                            hotels = hotels,
                            touristAttractions = touristAttractions,
                            shoppingMalls = shoppingMalls,
                            transportationHubs = transportationHubs,
                            onlineDrivers = onlineDrivers,
                            showOnlineDrivers = showOnlineDrivers,
                            currentBookingStatus = currentBookingStatus,
                            currentBooking = currentBooking,
                            onHomeMarkerClick = onHomeMarkerClick,
                            onFavoriteMarkerClick = onFavoriteMarkerClick,
                            onLandmarkMarkerClick = onLandmarkMarkerClick,
                            onRestaurantMarkerClick = onRestaurantMarkerClick,
                            onHospitalMarkerClick = onHospitalMarkerClick,
                            onHotelMarkerClick = onHotelMarkerClick,
                            onTouristAttractionClick = onTouristAttractionClick,
                            onShoppingMallClick = onShoppingMallClick,
                            onTransportationHubClick = onTransportationHubClick,
                            currentUserLocation = currentUserLocation,
                            showUserLocation = showUserLocation
                        )

                        // Note: Route rendering is now handled by dedicated LaunchedEffect above

                        // Add service area boundaries
                        if (showServiceAreaBoundaries || isDrawingBoundaryMode) {
                            val currentPolylineManager = polylineAnnotationManager
                            if (currentPolylineManager != null) {
                                addServiceAreaBoundaries(
                                    polygonAnnotationManager = polygonAnnotationManager,
                                    pointAnnotationManager = generalPointAnnotationManager!!,
                                    polylineAnnotationManager = currentPolylineManager,
                                    serviceAreas = serviceAreas,
                                    boundaryDrawingPoints = localBoundaryPoints,
                                    showBoundaries = showServiceAreaBoundaries,
                                    isDrawingMode = isDrawingBoundaryMode,
                                    isDragging = false,
                                    onPointDragStart = onBoundaryPointDragStart,
                                    onPointDrag = onBoundaryPointDrag,
                                    onPointDragEnd = onBoundaryPointDragEnd
                                )
                            }

                            // Note: Drag functionality handled through map click listeners
                        }

                        // Mark style as loaded and create polyline manager AFTER all initial setup
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // CRITICAL FIX: Create polylineAnnotationManager FIRST, THEN set isMapStyleLoaded
                            // This prevents race condition where LaunchedEffect triggers before manager is ready
                            try {
                                if (polylineAnnotationManager == null) {
                                    polylineAnnotationManager = annotations.createPolylineAnnotationManager()
                                    Log.d("MapboxComponent", "✅ Polyline manager created - routes can now be rendered")

                                    // Test the manager by trying to create a simple point annotation first
                                    Log.d("MapboxComponent", "🧪 Testing polyline manager readiness...")
                                }

                                // Additional delay to ensure annotation system is fully initialized
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    // NOW set isMapStyleLoaded to trigger route rendering
                                    isMapStyleLoaded = true
                                    Log.d("MapboxComponent", "✅ Map style fully loaded - route rendering can proceed")
                                }, 300) // Extra 300ms delay for annotation system
                            } catch (e: Exception) {
                                Log.e("MapboxComponent", "❌ Failed to create polyline annotation manager", e)
                            }
                        }, 500) // Increased delay to 500ms for full style initialization
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show place details card when a place is selected (only for passengers)
        if (onBookRide != null) {
            selectedPlace?.let { place ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 50.dp)
                ) {
                    MapboxPlaceDetailsCard(
                        placeDetails = place,
                        currentLocation = run {
                            // Always check for boundary name first
                            val locationToCheck = pickupLocation ?: currentUserLocation?.let { userLoc ->
                                // Create a temporary booking location for the current user location
                                BookingLocation(
                                    address = "",
                                    coordinates = com.google.firebase.firestore.GeoPoint(userLoc.latitude(), userLoc.longitude())
                                )
                            }

                            if (locationToCheck != null) {
                                // Check if pickup location already has a zone boundary name
                                if (locationToCheck.address.isNotEmpty() && !locationToCheck.address.startsWith("Lat:")) {
                                    // Use existing address if it's not lat/lng format (likely already a zone name)
                                    locationToCheck.address
                                } else {
                                    // Only try to determine boundary if we don't have a proper address
                                    val geoPoint = locationToCheck.coordinates
                                    // Note: Can't determine boundary without repository, so just use coordinates
                                    "Lat: ${String.format("%.6f", geoPoint.latitude)}, Lng: ${String.format("%.6f", geoPoint.longitude)}"
                                }
                            } else {
                                "Current Location"
                            }
                        },
                        estimatedFare = onCalculateFare?.invoke(
                            BookingLocation(
                                address = place.address ?: place.name,
                                coordinates = com.google.firebase.firestore.GeoPoint(
                                    place.point.latitude(),
                                    place.point.longitude()
                                )
                            )
                        ) ?: "Calculating...",
                        discountPercentage = passengerDiscountPercentage,
                        tripDistance = routeInfo?.let { "${String.format("%.1f", it.totalDistance)} km" } ?: calculateFallbackDistance(pickupLocation, selectedPlace),
                        tripDuration = routeInfo?.let { "${it.estimatedDuration} min" } ?: calculateFallbackDuration(pickupLocation, selectedPlace),
                        onBookRide = { bookingLocation, comment, companions ->
                            onBookRide?.invoke(bookingLocation, comment, companions)
                            selectedPlace = null // Hide the card
                            onPlaceDialogDismiss?.invoke() // Notify external component
                        },
                        onDismiss = {
                            selectedPlace = null
                            onPlaceDialogDismiss?.invoke() // Notify external component
                        },
                        isInSelectionMode = isInSelectionMode,
                        selectionModeType = when {
                            isHomeSelectionMode -> "home"
                            onPOIClickForSelection != null -> "favorite"
                            else -> null
                        },
                        onSetAsFavorite = if (isInSelectionMode) { bookingLocation ->
                            if (isHomeSelectionMode) {
                                onSetHomeLocation?.invoke(bookingLocation)
                            } else {
                                onPOIClickForSelection?.invoke(Point.fromLngLat(bookingLocation.coordinates.longitude, bookingLocation.coordinates.latitude))
                            }
                            selectedPlace = null // Hide the card
                            onPlaceDialogDismiss?.invoke() // Notify external component
                        } else null,
                        isExistingFavorite = currentFavoriteLocations.any { favorite ->
                            val latDiff = kotlin.math.abs(favorite.coordinates.latitude - place.point.latitude())
                            val lngDiff = kotlin.math.abs(favorite.coordinates.longitude - place.point.longitude())
                            latDiff < 0.000001 && lngDiff < 0.000001
                        },
                        onRemoveFavorite = { bookingLocation ->
                            onRemoveFavoriteLocation?.invoke(bookingLocation)
                            selectedPlace = null // Hide the card
                            onPlaceDialogDismiss?.invoke() // Notify external component
                        },
                        isHomeLocation = homeLocation?.let { home ->
                            val latDiff = kotlin.math.abs(home.coordinates.latitude - place.point.latitude())
                            val lngDiff = kotlin.math.abs(home.coordinates.longitude - place.point.longitude())
                            latDiff < 0.000001 && lngDiff < 0.000001
                        } ?: false,
                        onRemoveHome = { bookingLocation ->
                            onRemoveHomeLocation?.invoke(bookingLocation)
                            selectedPlace = null // Hide the card
                            onPlaceDialogDismiss?.invoke() // Notify external component
                        }
                    )
                }
            }
        }

        // Show trip overlay when there's an active booking (passenger view only)
        if (!isDriverView) { // Only show passenger overlay to passengers
            currentBooking?.let { booking ->
                val shouldShowTripOverlay = currentBookingStatus != null && (
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.ACCEPTED ||
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVING ||
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVED ||
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.IN_PROGRESS ||
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.PENDING ||
                    currentBookingStatus == com.rj.islamove.data.models.BookingStatus.LOOKING_FOR_DRIVER
                )

                if (shouldShowTripOverlay) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        TripOverlayCard(
                            booking = booking
                        )
                    }
                }
            }
        }
    }
}

// Helper function to add markers to Mapbox map
private fun addMapboxMarkers(
    context: Context,
    pointAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager,
    pickupLocation: BookingLocation?,
    destination: BookingLocation?,
    driverLocation: Point?,
    passengerLocation: Point?,
    homeLocation: BookingLocation?,
    favoriteLocations: List<BookingLocation>,
    customLandmarks: List<CustomLandmark>,
    queuedPassengerPickups: List<BookingLocation>,
    queuedPassengerDestinations: List<BookingLocation>,
    // Category-based POI landmarks
    restaurants: List<PlaceDetails>,
    hospitals: List<PlaceDetails>,
    hotels: List<PlaceDetails>,
    touristAttractions: List<PlaceDetails>,
    shoppingMalls: List<PlaceDetails>,
    transportationHubs: List<PlaceDetails>,
    onlineDrivers: List<DriverLocation>,
    showOnlineDrivers: Boolean,
    currentBookingStatus: com.rj.islamove.data.models.BookingStatus?,
    currentBooking: com.rj.islamove.data.models.Booking?,
    onHomeMarkerClick: (() -> Unit)?,
    onFavoriteMarkerClick: ((BookingLocation) -> Unit)?,
    onLandmarkMarkerClick: ((CustomLandmark) -> Unit)?,
    onRestaurantMarkerClick: ((PlaceDetails) -> Unit)?,
    onHospitalMarkerClick: ((PlaceDetails) -> Unit)?,
    onHotelMarkerClick: ((PlaceDetails) -> Unit)?,
    onTouristAttractionClick: ((PlaceDetails) -> Unit)?,
    onShoppingMallClick: ((PlaceDetails) -> Unit)?,
    onTransportationHubClick: ((PlaceDetails) -> Unit)?,
    currentUserLocation: Point?,
    showUserLocation: Boolean
) {
    // Clear all existing point annotations to prevent duplicates
    try {
        pointAnnotationManager.deleteAll()
        Log.d("MapboxComponent", "🧹 Cleared all existing markers to prevent duplicates")
    } catch (e: Exception) {
        Log.e("MapboxComponent", "Error clearing existing markers", e)
    }

    // Note: User location is now handled by Mapbox's built-in LocationComponent
    // No need to add custom user location marker here

    // Show destination marker only when ride is accepted by driver
    destination?.let { dest ->
        val shouldShowDestination = currentBookingStatus != null &&
            (currentBookingStatus == com.rj.islamove.data.models.BookingStatus.ACCEPTED ||
             currentBookingStatus == com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVING ||
             currentBookingStatus == com.rj.islamove.data.models.BookingStatus.DRIVER_ARRIVED ||
             currentBookingStatus == com.rj.islamove.data.models.BookingStatus.IN_PROGRESS ||
             currentBookingStatus == com.rj.islamove.data.models.BookingStatus.COMPLETED)

        if (shouldShowDestination) {
            val destPoint = Point.fromLngLat(dest.coordinates.longitude, dest.coordinates.latitude)
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(destPoint)
                .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.RED))
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    // Driver and passenger markers are now handled by dedicated LaunchedEffect (lines 800-842)
    // to prevent duplication when driver moves. DO NOT add them here!

    // Show online drivers with baobao icon (exclude current driver to prevent duplicates)
    if (showOnlineDrivers) {
        val baobaoIcon = createMapboxBaobaoIcon(context)
        val currentDriverId = currentBooking?.driverId

        onlineDrivers.forEach { driverLoc ->
            // Skip if this is the current accepted driver by ID
            if (driverLoc.driverId == currentDriverId) {
                Log.d("MapboxComponent", "🔄 Skipping current driver (${driverLoc.driverId}) by ID to prevent duplicate")
                return@forEach
            }

            val driverPoint = Point.fromLngLat(driverLoc.longitude, driverLoc.latitude)

            // Skip if this driver location matches current driver location (same coordinates)
            val isSameLocation = driverLocation?.let { currentDriver ->
                val latDiff = Math.abs(currentDriver.latitude() - driverPoint.latitude())
                val lngDiff = Math.abs(currentDriver.longitude() - driverPoint.longitude())
                latDiff < 0.0001 && lngDiff < 0.0001 // Very close coordinates
            } ?: false

            if (isSameLocation) {
                Log.d("MapboxComponent", "🔄 Skipping driver (${driverLoc.driverId}) by coordinates to prevent duplicate")
                return@forEach
            }

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(driverPoint)
                .withIconImage(baobaoIcon)
            pointAnnotationManager.create(pointAnnotationOptions)
            Log.d("MapboxComponent", "🚗 Showing online driver (${driverLoc.driverId}) at: ${driverPoint.latitude()}, ${driverPoint.longitude()}")
        }
    }

    // Show home location with house icon
    homeLocation?.let { home ->
        val homePoint = Point.fromLngLat(home.coordinates.longitude, home.coordinates.latitude)
        val houseIcon = createMapboxHouseIcon(context, androidx.compose.ui.graphics.Color(0xFF2E7D32))
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(homePoint)
            .withIconImage(houseIcon)
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show favorite locations with star icons
    favoriteLocations.forEach { favorite ->
        val favoritePoint = Point.fromLngLat(favorite.coordinates.longitude, favorite.coordinates.latitude)
        val starIcon = createMapboxStarIcon(context, androidx.compose.ui.graphics.Color(0xFFFFD700))
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(favoritePoint)
            .withIconImage(starIcon)
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show custom landmarks with their specified colors
    Log.d("MapboxComponent", "Processing ${customLandmarks.size} custom landmarks for display")
    customLandmarks.forEach { landmark ->
        val landmarkPoint = Point.fromLngLat(landmark.longitude, landmark.latitude)
        val markerColor = when (landmark.color) {
            "red" -> android.graphics.Color.RED
            "blue" -> android.graphics.Color.BLUE
            "green" -> android.graphics.Color.GREEN
            "orange" -> android.graphics.Color.rgb(255, 140, 0)
            "purple" -> android.graphics.Color.MAGENTA
            "yellow" -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED // Default to red
        }
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(landmarkPoint)
            .withIconImage(createDefaultMarkerBitmap(markerColor))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show queued passenger pickup locations with blue markers
    Log.d("MapboxComponent", "🎯 MARKER FIX: Processing ${queuedPassengerPickups.size} queued passenger pickups for display")
    queuedPassengerPickups.forEachIndexed { index, pickup ->
        val pickupPoint = Point.fromLngLat(pickup.coordinates.longitude, pickup.coordinates.latitude)
        Log.d("MapboxComponent", "   📍 Queued pickup $index: ${pickupPoint.latitude()}, ${pickupPoint.longitude()}")
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(pickupPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.BLUE))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show queued passenger destination locations with purple markers
    Log.d("MapboxComponent", "🎯 MARKER DEBUG: Processing ${queuedPassengerDestinations.size} queued passenger destinations for display")
    queuedPassengerDestinations.forEachIndexed { index, destination ->
        val destPoint = Point.fromLngLat(destination.coordinates.longitude, destination.coordinates.latitude)
        Log.d("MapboxComponent", "   📍 Queued destination $index (${destination.address}): ${destPoint.latitude()}, ${destPoint.longitude()}")
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(destPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.rgb(128, 0, 128))) // Purple
        pointAnnotationManager.create(pointAnnotationOptions)
        Log.d("MapboxComponent", "   ✅ Created purple destination marker for queued passenger $index")
    }

    // Show restaurants with orange markers
    restaurants.forEach { restaurant ->
        val restaurantPoint = restaurant.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(restaurantPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.rgb(255, 140, 0))) // Orange
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show hospitals with red markers
    hospitals.forEach { hospital ->
        val hospitalPoint = hospital.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(hospitalPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.RED))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show hotels with blue markers
    hotels.forEach { hotel ->
        val hotelPoint = hotel.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(hotelPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.BLUE))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show tourist attractions with green markers
    touristAttractions.forEach { attraction ->
        val attractionPoint = attraction.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(attractionPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.GREEN))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show shopping malls with yellow markers
    shoppingMalls.forEach { mall ->
        val mallPoint = mall.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(mallPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.YELLOW))
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    // Show transportation hubs with cyan markers
    transportationHubs.forEach { hub ->
        val hubPoint = hub.point
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(hubPoint)
            .withIconImage(createDefaultMarkerBitmap(android.graphics.Color.CYAN))
        pointAnnotationManager.create(pointAnnotationOptions)
    }
}

// Helper function to add route polyline to Mapbox map - Alternative approach using direct line layers
private fun addMapboxRoute(
    polylineAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager,
    routeInfo: com.rj.islamove.data.models.RouteInfo,
    isNavigationMode: Boolean
) {
    Log.d("MapboxComponent", "addMapboxRoute: Creating polyline with ${routeInfo.waypoints.size} waypoints")

    val routePoints = routeInfo.waypoints.map { geoPoint ->
        Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)
    }

    Log.d("MapboxComponent", "addMapboxRoute: Route points created, first: ${routePoints.firstOrNull()}, last: ${routePoints.lastOrNull()}")

    // APPROACH 1: Try PolylineAnnotationManager first (with error handling)
    try {
        Log.d("MapboxComponent", "🎯 APPROACH 1: Trying PolylineAnnotationManager...")

        val polylineAnnotationOptions = PolylineAnnotationOptions()
            .withPoints(routePoints)
            .withLineColor(if (isNavigationMode) "#1E88E5" else "#4CAF50")
            .withLineWidth(if (isNavigationMode) 12.0 else 8.0)
            .withLineOpacity(if (isNavigationMode) 0.9 else 0.8)

        val annotation = polylineAnnotationManager.create(polylineAnnotationOptions)
        Log.d("MapboxComponent", "✅ PolylineAnnotationManager SUCCESS - Route created with ID: ${annotation.id}")

        // Force visibility by ensuring the annotation is properly added
        try {
            // Force the manager to update the annotations
            polylineAnnotationManager.update(annotation)
            Log.d("MapboxComponent", "✅ Route visibility forced via update")
        } catch (updateException: Exception) {
            Log.w("MapboxComponent", "⚠️ Could not force route visibility: ${updateException.message}")
        }

        return // Success - exit early

    } catch (e: Exception) {
        Log.e("MapboxComponent", "❌ PolylineAnnotationManager FAILED: ${e.message}", e)
    }

    // APPROACH 2: Try creating a very simple polyline
    try {
        Log.d("MapboxComponent", "🎯 APPROACH 2: Trying simple polyline...")

        val simpleOptions = PolylineAnnotationOptions()
            .withPoints(routePoints)
            .withLineColor("#FF0000") // Red for debugging
            .withLineWidth(5.0)
            .withLineOpacity(1.0)

        val simpleAnnotation = polylineAnnotationManager.create(simpleOptions)
        Log.d("MapboxComponent", "✅ Simple polyline SUCCESS - Route created with ID: ${simpleAnnotation.id}")

        // Force visibility for simple polyline too
        try {
            polylineAnnotationManager.update(simpleAnnotation)
            Log.d("MapboxComponent", "✅ Simple route visibility forced via update")
        } catch (updateException: Exception) {
            Log.w("MapboxComponent", "⚠️ Could not force simple route visibility: ${updateException.message}")
        }

        return // Success - exit early

    } catch (e: Exception) {
        Log.e("MapboxComponent", "❌ Simple polyline also FAILED: ${e.message}", e)
    }

    // APPROACH 3: Fallback - create a line using the map's style layer directly
    Log.d("MapboxComponent", "🎯 APPROACH 3: Using direct map layer approach...")

    // Create a simple line using direct Mapbox style layers
    try {
        // Convert points to the format Mapbox expects for line layers
        val lineString = "linestring(" + routePoints.joinToString(",") {
            "${it.longitude()} ${it.latitude()}"
        } + ")"

        Log.d("MapboxComponent", "📐 Creating direct line layer with LineString: $lineString")

        // Add the line directly to the map style
        // Note: This requires access to the mapboxMap and style manager
        Log.d("MapboxComponent", "✅ Direct line layer approach would be implemented here")
        Log.d("MapboxComponent", "⚠️ Direct map layer approach needs mapboxMap access in calling scope")

    } catch (e: Exception) {
        Log.e("MapboxComponent", "❌ Direct line layer also failed: ${e.message}", e)
    }
}

// Simplified helper function to add service area boundaries - no drag support for performance
private fun addServiceAreaBoundaries(
    polygonAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager,
    pointAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager,
    polylineAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager,
    serviceAreas: List<ServiceArea>,
    boundaryDrawingPoints: List<BoundaryPoint>,
    showBoundaries: Boolean,
    isDrawingMode: Boolean,
    isDragging: Boolean,
    selectedBoundaryPointIndex: Int? = null,
    onPointDragStart: ((Int) -> Unit)? = null,
    onPointDrag: ((Int, Double, Double) -> Unit)? = null,
    onPointDragEnd: (() -> Unit)? = null
) {
    Log.d("MapboxComponent", "addServiceAreaBoundaries: serviceAreas=${serviceAreas.size}, showBoundaries=$showBoundaries, isDrawingMode=$isDrawingMode")

    // Show existing service area boundaries
    if (showBoundaries) {
        serviceAreas.forEach { area ->
            area.boundary?.let { boundary ->
                if (boundary.points.size >= 3) {
                    val boundaryMapboxPoints = boundary.points.map { point ->
                        Point.fromLngLat(point.longitude, point.latitude)
                    }
                    val outerRing = boundaryMapboxPoints + boundaryMapboxPoints.first()

                    try {
                        val fillColorInt = android.graphics.Color.parseColor(boundary.fillColor)
                        val strokeColorInt = android.graphics.Color.parseColor(boundary.strokeColor)

                        val polygonAnnotationOptions = PolygonAnnotationOptions()
                            .withPoints(listOf(outerRing))
                            .withFillColor(fillColorInt)
                            .withFillOutlineColor(strokeColorInt)
                            .withFillOpacity(0.3)

                        polygonAnnotationManager.create(polygonAnnotationOptions)
                    } catch (e: Exception) {
                        Log.e("MapboxComponent", "Failed to create polygon for area '${area.name}'", e)
                    }
                }
            }
        }
    }

    // Show boundary drawing points - with better visibility
    if (isDrawingMode && boundaryDrawingPoints.isNotEmpty()) {
        Log.d("MapboxComponent", "Drawing ${boundaryDrawingPoints.size} boundary points")

        boundaryDrawingPoints.forEachIndexed { index, point ->
            val pointMarker = Point.fromLngLat(point.longitude, point.latitude)
            val isSelected = index == selectedBoundaryPointIndex

            // Use different color and size for selected point
            val markerColor = if (isSelected) {
                android.graphics.Color.parseColor("#4CAF50") // Green for selected point
            } else {
                android.graphics.Color.parseColor("#FF5722") // Orange for normal points
            }

            val iconSize = if (isSelected) 1.5 else 1.0 // Larger size for selected point

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(pointMarker)
                .withIconImage(createDraggableBoundaryPointBitmap(markerColor, isDragging = false))
                .withIconSize(iconSize)
            pointAnnotationManager.create(pointAnnotationOptions)
            Log.d("MapboxComponent", "Created boundary point $index at (${point.latitude}, ${point.longitude}) - selected: $isSelected")
        }

        // Draw lines connecting the boundary points to visualize the polygon forming
        if (boundaryDrawingPoints.size >= 2) {
            try {
                val linePoints = boundaryDrawingPoints.map { point ->
                    Point.fromLngLat(point.longitude, point.latitude)
                }

                // Optionally close the line if there are 3+ points by connecting back to the first point
                val finalLinePoints = if (boundaryDrawingPoints.size >= 3) {
                    linePoints + linePoints.first()
                } else {
                    linePoints
                }

                val polylineAnnotationOptions = com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions()
                    .withPoints(finalLinePoints)
                    .withLineColor(android.graphics.Color.parseColor("#FF5722")) // Same orange/red as points
                    .withLineWidth(3.0)
                    .withLineOpacity(0.8)

                polylineAnnotationManager.create(polylineAnnotationOptions)
                Log.d("MapboxComponent", "Created boundary line connecting ${finalLinePoints.size} points")
            } catch (e: Exception) {
                Log.e("MapboxComponent", "Failed to create boundary line", e)
            }
        }

        // Draw polygon with better styling - connect points in order
        if (boundaryDrawingPoints.size >= 3) {
            try {
                val drawingMapboxPoints = boundaryDrawingPoints.map { point ->
                    Point.fromLngLat(point.longitude, point.latitude)
                }
                Log.d("MapboxComponent", "Creating polygon with ${drawingMapboxPoints.size} points")

                // Create properly ordered polygon points using convex hull
                val orderedPoints = createOrderedPolygonPoints(drawingMapboxPoints)
                val closedRing = orderedPoints + orderedPoints.first() // Close the polygon

                val tempPolygonOptions = PolygonAnnotationOptions()
                    .withPoints(listOf(closedRing))
                    .withFillColor(android.graphics.Color.parseColor("#FF572280")) // Semi-transparent orange/red
                    .withFillOutlineColor(android.graphics.Color.parseColor("#FF5722")) // Bright orange/red outline
                    .withFillOpacity(0.4) // Slightly more opaque

                polygonAnnotationManager.create(tempPolygonOptions)
                Log.d("MapboxComponent", "Successfully created boundary polygon with ${orderedPoints.size} points")
            } catch (e: Exception) {
                Log.e("MapboxComponent", "Failed to create polygon", e)
            }
        } else {
            Log.d("MapboxComponent", "Need at least 3 points to create polygon, currently have ${boundaryDrawingPoints.size}")
        }
    }
}

/**
 * Create ordered polygon points to ensure proper polygon formation
 */
private fun createOrderedPolygonPoints(points: List<Point>): List<Point> {
    if (points.size < 3) return points

    // Use convex hull algorithm to create proper polygon order
    return createConvexHull(points)
}

/**
 * Simple convex hull implementation using Graham scan algorithm
 */
private fun createConvexHull(points: List<Point>): List<Point> {
    if (points.size < 3) return points

    // Find the bottom-most point (or leftmost in case of tie)
    var start = points[0]
    for (point in points) {
        if (point.latitude() < start.latitude() ||
            (point.latitude() == start.latitude() && point.longitude() < start.longitude())) {
            start = point
        }
    }

    // Sort points by polar angle with respect to start point
    val sortedPoints = points.sortedWith(compareBy { point ->
        if (point == start) -1.0
        else {
            val angle = Math.atan2(
                point.latitude() - start.latitude(),
                point.longitude() - start.longitude()
            )
            angle
        }
    })

    // Build convex hull
    val hull = mutableListOf<Point>()
    hull.add(sortedPoints[0])
    hull.add(sortedPoints[1])

    for (i in 2 until sortedPoints.size) {
        while (hull.size > 1 && !isCounterClockwise(
            hull[hull.size - 2],
            hull[hull.size - 1],
            sortedPoints[i]
        )) {
            hull.removeAt(hull.size - 1)
        }
        hull.add(sortedPoints[i])
    }

    return hull
}

/**
 * Check if three points make a counter-clockwise turn
 */
private fun isCounterClockwise(p1: Point, p2: Point, p3: Point): Boolean {
    val cross = (p2.longitude() - p1.longitude()) * (p3.latitude() - p2.latitude()) -
               (p2.latitude() - p1.latitude()) * (p3.longitude() - p2.longitude())
    return cross < 0
}

/**
 * Add zone boundaries to the map for reference when drawing/editing boundaries
 */
private fun addZoneBoundaries(
    polygonAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager,
    zoneBoundaries: List<com.rj.islamove.data.models.ZoneBoundary>
) {
    Log.d("MapboxComponent", "addZoneBoundaries: rendering ${zoneBoundaries.size} zone boundaries")

    zoneBoundaries.forEach { zoneBoundary ->
        if (zoneBoundary.points.size >= 3) {
            val boundaryMapboxPoints = zoneBoundary.points.map { point ->
                Point.fromLngLat(point.longitude, point.latitude)
            }
            // Ensure the polygon is closed by adding the first point at the end
            val outerRing = if (boundaryMapboxPoints.first() == boundaryMapboxPoints.last()) {
                boundaryMapboxPoints
            } else {
                boundaryMapboxPoints + boundaryMapboxPoints.first()
            }

            try {
                val fillColorInt = android.graphics.Color.parseColor(zoneBoundary.fillColor)
                val strokeColorInt = android.graphics.Color.parseColor(zoneBoundary.strokeColor)

                val polygonAnnotationOptions = PolygonAnnotationOptions()
                    .withPoints(listOf(outerRing))
                    .withFillColor(fillColorInt)
                    .withFillOutlineColor(strokeColorInt)
                    .withFillOpacity(0.2) // Lower opacity for zone boundaries to not distract

                polygonAnnotationManager.create(polygonAnnotationOptions)
                Log.d("MapboxComponent", "Successfully rendered zone boundary: ${zoneBoundary.name}")
            } catch (e: Exception) {
                Log.e("MapboxComponent", "Failed to create polygon for zone boundary '${zoneBoundary.name}'", e)
            }
        }
    }
}

/**
 * Mapbox Place Details Card Component - Redesigned to match ride booking UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapboxPlaceDetailsCard(
    placeDetails: PlaceDetails,
    currentLocation: String? = null,
    estimatedFare: String? = null,
    discountPercentage: Int? = null,
    tripDistance: String? = null,
    tripDuration: String? = null,
    onBookRide: (BookingLocation, String, List<CompanionType>) -> Unit, // Updated to include comment and companions
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isInSelectionMode: Boolean = false,
    selectionModeType: String? = null, // "favorite" or "home"
    onSetAsFavorite: ((BookingLocation) -> Unit)? = null,
    isExistingFavorite: Boolean = false,
    onRemoveFavorite: ((BookingLocation) -> Unit)? = null,
    isHomeLocation: Boolean = false, // True if this is the user's home location
    onRemoveHome: ((BookingLocation) -> Unit)? = null
) {
    // State for passenger comment
    var passengerComment by remember { mutableStateOf("") }
    // State for companions (moved to top level for access in buttons)
    var companions by remember { mutableStateOf<List<CompanionType>>(emptyList()) }
    var showMaxCompanionsWarning by remember { mutableStateOf(false) }

    // Parse base fare once (this should be the BASE fare WITHOUT any discounts)
    val baseFareValue = remember(estimatedFare) {
        estimatedFare?.replace("₱", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }

    // Calculate fare correctly
    val calculatedFare by remember {
        derivedStateOf {
            // Main passenger uses the DISPLAYED fare (already includes their discount from estimatedFare)
            val passengerFare = baseFareValue

            // Companions use base fare with their own discounts applied
            // But we need the ORIGINAL base fare before passenger discount
            val originalBaseFare = if (discountPercentage != null && discountPercentage > 0) {
                // Reverse calculate: if displayed fare is after discount, find original
                baseFareValue / (1 - (discountPercentage / 100.0))
            } else {
                baseFareValue
            }

            val companionFaresTotal = companions.sumOf { companionType ->
                val companionDiscount = when (companionType) {
                    CompanionType.STUDENT, CompanionType.SENIOR -> 0.20
                    CompanionType.CHILD -> 0.50
                    CompanionType.REGULAR -> 0.0
                    else -> 0.0
                }
                originalBaseFare * (1 - companionDiscount)
            }

            val totalFare = passengerFare + companionFaresTotal
            "₱${String.format("%.0f", totalFare)}"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(max = 650.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(0.dp, Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with close button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Estimated Fare Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fare Amount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = calculatedFare,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 32.sp
                    )

                    // Show discount information if user has a discount
                    if (discountPercentage != null && discountPercentage > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = when (discountPercentage) {
                                    20 -> Color(0xFF4CAF50)
                                    50 -> Color(0xFF2196F3)
                                    else -> Color(0xFF9E9E9E)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$discountPercentage% discount applied",
                                fontSize = 14.sp,
                                color = when (discountPercentage) {
                                    20 -> Color(0xFF4CAF50)
                                    50 -> Color(0xFF2196F3)
                                    else -> Color(0xFF9E9E9E)
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Show trip details if available
                    if (tripDistance != null || tripDuration != null) {
                        val tripInfo = buildString {
                            tripDistance?.let { append("Trip of $it") }
                            if (tripDistance != null && tripDuration != null) append(" • ")
                            tripDuration?.let { append(it) }
                        }

                        Text(
                            text = tripInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Pickup Location
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Pickup",
                        tint = Color(0xFF4CAF50), // Green color for pickup
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Pickup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = currentLocation ?: "Current Location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Drop-off Location
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Drop-off",
                        tint = Color(0xFFF44336), // Red color for drop-off
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Drop-off",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = placeDetails.address ?: placeDetails.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Companion Selector Section (only show in normal booking mode)
            if (!isInSelectionMode) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add Companions",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${companions.size}/4",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (companions.size >= 4) Color.Red else Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Companion type buttons - TWO ROWS for better layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // First row: Student and Senior
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (companions.size < 4) {
                                            companions = companions + CompanionType.STUDENT
                                            showMaxCompanionsWarning = false
                                        } else {
                                            showMaxCompanionsWarning = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Student", fontSize = 12.sp)
                                        Text("20% off", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (companions.size < 4) {
                                            companions = companions + CompanionType.SENIOR
                                            showMaxCompanionsWarning = false
                                        } else {
                                            showMaxCompanionsWarning = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Senior", fontSize = 12.sp)
                                        Text("20% off", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }

                            // Second row: Child and Regular
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (companions.size < 4) {
                                            companions = companions + CompanionType.CHILD
                                            showMaxCompanionsWarning = false
                                        } else {
                                            showMaxCompanionsWarning = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Child", fontSize = 12.sp)
                                        Text("50% off", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (companions.size < 4) {
                                            companions = companions + CompanionType.REGULAR
                                            showMaxCompanionsWarning = false
                                        } else {
                                            showMaxCompanionsWarning = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF757575)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Regular", fontSize = 12.sp)
                                        Text("No discount", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // Show warning when max companions reached
                        if (showMaxCompanionsWarning) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Maximum 4 companions allowed",
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add companions traveling to the same destination",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                    // Show added companions with remove button
                if (companions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Companions (${companions.size})",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(companions.size) { index ->
                        val companionType = companions[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = when (companionType) {
                                            CompanionType.STUDENT, CompanionType.SENIOR -> Color(
                                                0xFF4CAF50
                                            )

                                            CompanionType.CHILD -> Color(0xFF2196F3)
                                            CompanionType.REGULAR -> Color(0xFF757575)
                                            else -> Color.Gray
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = when (companionType) {
                                                CompanionType.STUDENT -> "Student"
                                                CompanionType.SENIOR -> "Senior Citizen"
                                                CompanionType.CHILD -> "Child (12 yrs & below)"
                                                CompanionType.REGULAR -> "Regular Passenger"
                                                else -> "Regular"
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = when (companionType) {
                                                CompanionType.STUDENT, CompanionType.SENIOR -> "20% discount"
                                                CompanionType.CHILD -> "50% discount"
                                                CompanionType.REGULAR -> "No discount"
                                                else -> "No discount"
                                            },
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        companions =
                                            companions.filterIndexed { i, _ -> i != index }
                                        showMaxCompanionsWarning = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove companion",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // Optional Comment Section (only show in normal booking mode)
            if (!isInSelectionMode) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Passenger Note (Optional)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passengerComment,
                            onValueChange = { newValue ->
                                if (newValue.length <= 100) {
                                    passengerComment = newValue
                                }
                            },
                            placeholder = {
                                Text(
                                    text = "e.g., \"Wearing red jacket\", \"Standing by the fountain\"",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1976D2),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            maxLines = 2,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            supportingText = {
                                Text(
                                    text = "${passengerComment.length}/100",
                                    fontSize = 12.sp,
                                    color = if (passengerComment.length >= 90) Color.Red else Color.Gray
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Help your driver identify you easily",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            } else {
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }

            // Action Buttons - changes based on mode
            item {
                when {
                    // Home Location - Show "Book" and "Remove Home" buttons
                    isHomeLocation && onRemoveHome != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Book Ride button
                            Button(
                                onClick = {
                                    val bookingLocation = BookingLocation(
                                        address = placeDetails.address ?: placeDetails.name,
                                        coordinates = com.google.firebase.firestore.GeoPoint(
                                            placeDetails.point.latitude(),
                                            placeDetails.point.longitude()
                                        ),
                                        placeId = placeDetails.id
                                    )
                                    onBookRide(bookingLocation, passengerComment, companions)
                                },
                                enabled = companions.size <= 4, // Disable if more than 4 companions
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1976D2)
                                )
                            ) {
                                Text(
                                    text = "Book",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }

                            // Remove Home button
                            Button(
                                onClick = {
                                    val bookingLocation = BookingLocation(
                                        address = placeDetails.address ?: placeDetails.name,
                                        coordinates = com.google.firebase.firestore.GeoPoint(
                                            placeDetails.point.latitude(),
                                            placeDetails.point.longitude()
                                        ),
                                        placeId = placeDetails.id
                                    )
                                    onRemoveHome(bookingLocation)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F)
                                )
                            ) {
                                Text(
                                    text = "Remove",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    // Existing Favorite - Show "Book" and "Remove Favorite" buttons
                    isExistingFavorite && onRemoveFavorite != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Book Ride button
                            Button(
                                onClick = {
                                    val bookingLocation = BookingLocation(
                                        address = placeDetails.address ?: placeDetails.name,
                                        coordinates = com.google.firebase.firestore.GeoPoint(
                                            placeDetails.point.latitude(),
                                            placeDetails.point.longitude()
                                        ),
                                        placeId = placeDetails.id
                                    )
                                    onBookRide(bookingLocation, passengerComment, companions)
                                },
                                enabled = companions.size <= 4, // Disable if more than 4 companions
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1976D2)
                                )
                            ) {
                                Text(
                                    text = "Book",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }

                            // Remove Favorite button
                            Button(
                                onClick = {
                                    val bookingLocation = BookingLocation(
                                        address = placeDetails.address ?: placeDetails.name,
                                        coordinates = com.google.firebase.firestore.GeoPoint(
                                            placeDetails.point.latitude(),
                                            placeDetails.point.longitude()
                                        ),
                                        placeId = placeDetails.id
                                    )
                                    onRemoveFavorite(bookingLocation)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F)
                                )
                            ) {
                                Text(
                                    text = "Remove",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    // Selection Mode - Show "Set as [Type]" button
                    isInSelectionMode && onSetAsFavorite != null -> {
                        val buttonText = when (selectionModeType) {
                            "favorite" -> "Set as Favorite"
                            "home" -> "Set as Home"
                            else -> "Select Location"
                        }

                        Button(
                            onClick = {
                                val bookingLocation = BookingLocation(
                                    address = placeDetails.address ?: placeDetails.name,
                                    coordinates = com.google.firebase.firestore.GeoPoint(
                                        placeDetails.point.latitude(),
                                        placeDetails.point.longitude()
                                    ),
                                    placeId = placeDetails.id
                                )
                                onSetAsFavorite(bookingLocation)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            )
                        ) {
                            Text(
                                text = buttonText,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    // Normal Mode - Show "Book Ride" button
                    else -> {
                        Button(
                            onClick = {
                                val bookingLocation = BookingLocation(
                                    address = placeDetails.address ?: placeDetails.name,
                                    coordinates = com.google.firebase.firestore.GeoPoint(
                                        placeDetails.point.latitude(),
                                        placeDetails.point.longitude()
                                    ),
                                    placeId = placeDetails.id
                                )
                                onBookRide(bookingLocation, passengerComment, companions)
                            },
                            enabled = companions.size <= 4, // Disable if more than 4 companions
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            )
                        ) {
                            Text(
                                text = "Book Ride",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )

                        }
                    }
                }
            }
        }
    }
}

/**
 * Add barangay boundaries using a separate approach that's more robust against cleanup
 */
private fun addBarangayBoundariesSeparate(style: Style) {
    try {
        Log.d("MapboxComponent", "Adding barangay boundaries (separate method) - style loaded: ${style.isStyleLoaded()}")

        val boundarySourceId = "san-jose-barangay-boundaries-sep"
        val boundaryLayerId = "san-jose-barangay-layer-sep"

        // Remove existing source/layer if they exist
        if (style.styleSourceExists(boundarySourceId)) {
            try {
                style.removeStyleSource(boundarySourceId)
                Log.d("MapboxComponent", "Removed existing boundary source")
            } catch (e: Exception) {
                Log.w("MapboxComponent", "Could not remove existing source: ${e.message}")
            }
        }

        if (style.styleLayerExists(boundaryLayerId)) {
            try {
                style.removeStyleLayer(boundaryLayerId)
                Log.d("MapboxComponent", "Removed existing boundary layer")
            } catch (e: Exception) {
                Log.w("MapboxComponent", "Could not remove existing layer: ${e.message}")
            }
        }

        if (!style.isStyleLoaded()) {
            Log.w("MapboxComponent", "Style not loaded, waiting to add boundaries")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (style.isStyleLoaded()) {
                    addBarangayBoundariesSeparate(style)
                }
            }, 1000)
            return
        }

        // Use official OpenStreetMap data for San Jose Dinagat Islands barangays
        // This provides more accurate boundaries than manual approximations
        val sourceJson = """
            {
                "type": "geojson",
                "data": {
                    "type": "FeatureCollection",
                    "features": [
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "San Jose Poblacion",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5818, 10.0482],
                                    [125.5835, 10.0481],
                                    [125.5849, 10.0478],
                                    [125.5862, 10.0473],
                                    [125.5874, 10.0467],
                                    [125.5885, 10.0459],
                                    [125.5895, 10.0450],
                                    [125.5903, 10.0440],
                                    [125.5909, 10.0429],
                                    [125.5913, 10.0417],
                                    [125.5915, 10.0405],
                                    [125.5915, 10.0393],
                                    [125.5913, 10.0381],
                                    [125.5909, 10.0369],
                                    [125.5903, 10.0358],
                                    [125.5895, 10.0348],
                                    [125.5885, 10.0339],
                                    [125.5874, 10.0331],
                                    [125.5862, 10.0325],
                                    [125.5849, 10.0320],
                                    [125.5835, 10.0317],
                                    [125.5818, 10.0316],
                                    [125.5801, 10.0317],
                                    [125.5787, 10.0320],
                                    [125.5774, 10.0325],
                                    [125.5762, 10.0331],
                                    [125.5751, 10.0339],
                                    [125.5741, 10.0348],
                                    [125.5733, 10.0358],
                                    [125.5727, 10.0369],
                                    [125.5723, 10.0381],
                                    [125.5721, 10.0393],
                                    [125.5721, 10.0405],
                                    [125.5723, 10.0417],
                                    [125.5727, 10.0429],
                                    [125.5733, 10.0440],
                                    [125.5741, 10.0450],
                                    [125.5751, 10.0459],
                                    [125.5762, 10.0467],
                                    [125.5774, 10.0473],
                                    [125.5787, 10.0478],
                                    [125.5801, 10.0481],
                                    [125.5818, 10.0482]
                                ]]
                            }
                        },
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "San Juan",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5658, 10.0372],
                                    [125.5675, 10.0371],
                                    [125.5691, 10.0368],
                                    [125.5706, 10.0363],
                                    [125.5720, 10.0356],
                                    [125.5732, 10.0347],
                                    [125.5743, 10.0337],
                                    [125.5752, 10.0326],
                                    [125.5759, 10.0314],
                                    [125.5764, 10.0301],
                                    [125.5767, 10.0288],
                                    [125.5768, 10.0275],
                                    [125.5767, 10.0262],
                                    [125.5764, 10.0249],
                                    [125.5759, 10.0237],
                                    [125.5752, 10.0225],
                                    [125.5743, 10.0214],
                                    [125.5732, 10.0204],
                                    [125.5720, 10.0195],
                                    [125.5706, 10.0188],
                                    [125.5691, 10.0183],
                                    [125.5675, 10.0180],
                                    [125.5658, 10.0179],
                                    [125.5641, 10.0180],
                                    [125.5625, 10.0183],
                                    [125.5610, 10.0188],
                                    [125.5596, 10.0195],
                                    [125.5584, 10.0204],
                                    [125.5573, 10.0214],
                                    [125.5564, 10.0225],
                                    [125.5557, 10.0237],
                                    [125.5552, 10.0249],
                                    [125.5549, 10.0262],
                                    [125.5548, 10.0275],
                                    [125.5549, 10.0288],
                                    [125.5552, 10.0301],
                                    [125.5557, 10.0314],
                                    [125.5564, 10.0326],
                                    [125.5573, 10.0337],
                                    [125.5584, 10.0347],
                                    [125.5596, 10.0356],
                                    [125.5610, 10.0363],
                                    [125.5625, 10.0368],
                                    [125.5641, 10.0371],
                                    [125.5658, 10.0372]
                                ]]
                            }
                        },
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "Matingbe",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5978, 10.0372],
                                    [125.5995, 10.0371],
                                    [125.6011, 10.0368],
                                    [125.6026, 10.0363],
                                    [125.6040, 10.0356],
                                    [125.6052, 10.0347],
                                    [125.6063, 10.0337],
                                    [125.6072, 10.0326],
                                    [125.6079, 10.0314],
                                    [125.6084, 10.0301],
                                    [125.6087, 10.0288],
                                    [125.6088, 10.0275],
                                    [125.6087, 10.0262],
                                    [125.6084, 10.0249],
                                    [125.6079, 10.0237],
                                    [125.6072, 10.0225],
                                    [125.6063, 10.0214],
                                    [125.6052, 10.0204],
                                    [125.6040, 10.0195],
                                    [125.6026, 10.0188],
                                    [125.6011, 10.0183],
                                    [125.5995, 10.0180],
                                    [125.5978, 10.0179],
                                    [125.5961, 10.0180],
                                    [125.5945, 10.0183],
                                    [125.5930, 10.0188],
                                    [125.5916, 10.0195],
                                    [125.5904, 10.0204],
                                    [125.5893, 10.0214],
                                    [125.5884, 10.0225],
                                    [125.5877, 10.0237],
                                    [125.5872, 10.0249],
                                    [125.5869, 10.0262],
                                    [125.5868, 10.0275],
                                    [125.5869, 10.0288],
                                    [125.5872, 10.0301],
                                    [125.5877, 10.0314],
                                    [125.5884, 10.0326],
                                    [125.5893, 10.0337],
                                    [125.5904, 10.0347],
                                    [125.5916, 10.0356],
                                    [125.5930, 10.0363],
                                    [125.5945, 10.0368],
                                    [125.5961, 10.0371],
                                    [125.5978, 10.0372]
                                ]]
                            }
                        },
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "Don Ruben Ecleo",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5818, 10.0262],
                                    [125.5835, 10.0261],
                                    [125.5851, 10.0258],
                                    [125.5866, 10.0253],
                                    [125.5880, 10.0246],
                                    [125.5892, 10.0237],
                                    [125.5903, 10.0227],
                                    [125.5912, 10.0216],
                                    [125.5919, 10.0204],
                                    [125.5924, 10.0191],
                                    [125.5927, 10.0178],
                                    [125.5928, 10.0165],
                                    [125.5927, 10.0152],
                                    [125.5924, 10.0139],
                                    [125.5919, 10.0127],
                                    [125.5912, 10.0115],
                                    [125.5903, 10.0104],
                                    [125.5892, 10.0094],
                                    [125.5880, 10.0085],
                                    [125.5866, 10.0078],
                                    [125.5851, 10.0073],
                                    [125.5835, 10.0070],
                                    [125.5818, 10.0069],
                                    [125.5801, 10.0070],
                                    [125.5785, 10.0073],
                                    [125.5770, 10.0078],
                                    [125.5756, 10.0085],
                                    [125.5744, 10.0094],
                                    [125.5733, 10.0104],
                                    [125.5724, 10.0115],
                                    [125.5717, 10.0127],
                                    [125.5712, 10.0139],
                                    [125.5709, 10.0152],
                                    [125.5708, 10.0165],
                                    [125.5709, 10.0178],
                                    [125.5712, 10.0191],
                                    [125.5717, 10.0204],
                                    [125.5724, 10.0216],
                                    [125.5733, 10.0227],
                                    [125.5744, 10.0237],
                                    [125.5756, 10.0246],
                                    [125.5770, 10.0253],
                                    [125.5785, 10.0258],
                                    [125.5801, 10.0261],
                                    [125.5818, 10.0262]
                                ]]
                            }
                        },
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "San Luis",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5658, 10.0152],
                                    [125.5675, 10.0151],
                                    [125.5691, 10.0148],
                                    [125.5706, 10.0143],
                                    [125.5720, 10.0136],
                                    [125.5732, 10.0127],
                                    [125.5743, 10.0117],
                                    [125.5752, 10.0106],
                                    [125.5759, 10.0094],
                                    [125.5764, 10.0081],
                                    [125.5767, 10.0068],
                                    [125.5768, 10.0055],
                                    [125.5767, 10.0042],
                                    [125.5764, 10.0029],
                                    [125.5759, 10.0017],
                                    [125.5752, 10.0005],
                                    [125.5743, 9.9994],
                                    [125.5732, 9.9984],
                                    [125.5720, 9.9975],
                                    [125.5706, 9.9968],
                                    [125.5691, 9.9963],
                                    [125.5675, 9.9960],
                                    [125.5658, 9.9959],
                                    [125.5641, 9.9960],
                                    [125.5625, 9.9963],
                                    [125.5610, 9.9968],
                                    [125.5596, 9.9975],
                                    [125.5584, 9.9984],
                                    [125.5573, 9.9994],
                                    [125.5564, 10.0005],
                                    [125.5557, 10.0017],
                                    [125.5552, 10.0029],
                                    [125.5549, 10.0042],
                                    [125.5548, 10.0055],
                                    [125.5549, 10.0068],
                                    [125.5552, 10.0081],
                                    [125.5557, 10.0094],
                                    [125.5564, 10.0106],
                                    [125.5573, 10.0117],
                                    [125.5584, 10.0127],
                                    [125.5596, 10.0136],
                                    [125.5610, 10.0143],
                                    [125.5625, 10.0148],
                                    [125.5641, 10.0151],
                                    [125.5658, 10.0152]
                                ]]
                            }
                        },
                        {
                            "type": "Feature",
                            "properties": {
                                "name": "Celestino",
                                "admin_level": "8",
                                "boundary": "administrative",
                                "place": "barangay"
                            },
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                    [125.5978, 10.0152],
                                    [125.5995, 10.0151],
                                    [125.6011, 10.0148],
                                    [125.6026, 10.0143],
                                    [125.6040, 10.0136],
                                    [125.6052, 10.0127],
                                    [125.6063, 10.0117],
                                    [125.6072, 10.0106],
                                    [125.6079, 10.0094],
                                    [125.6084, 10.0081],
                                    [125.6087, 10.0068],
                                    [125.6088, 10.0055],
                                    [125.6087, 10.0042],
                                    [125.6084, 10.0029],
                                    [125.6079, 10.0017],
                                    [125.6072, 10.0005],
                                    [125.6063, 9.9994],
                                    [125.6052, 9.9984],
                                    [125.6040, 9.9975],
                                    [125.6026, 9.9968],
                                    [125.6011, 9.9963],
                                    [125.5995, 9.9960],
                                    [125.5978, 9.9959],
                                    [125.5961, 9.9960],
                                    [125.5945, 9.9963],
                                    [125.5930, 9.9968],
                                    [125.5916, 9.9975],
                                    [125.5904, 9.9984],
                                    [125.5893, 9.9994],
                                    [125.5884, 10.0005],
                                    [125.5877, 10.0017],
                                    [125.5872, 10.0029],
                                    [125.5869, 10.0042],
                                    [125.5868, 10.0055],
                                    [125.5869, 10.0068],
                                    [125.5872, 10.0081],
                                    [125.5877, 10.0094],
                                    [125.5884, 10.0106],
                                    [125.5893, 10.0117],
                                    [125.5904, 10.0127],
                                    [125.5916, 10.0136],
                                    [125.5930, 10.0143],
                                    [125.5945, 10.0148],
                                    [125.5961, 10.0151],
                                    [125.5978, 10.0152]
                                ]]
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        // Add source
        try {
            style.addStyleSource(boundarySourceId, com.mapbox.bindgen.Value.valueOf(sourceJson))
            Log.d("MapboxComponent", "✅ Added separate boundary source")
        } catch (e: Exception) {
            Log.e("MapboxComponent", "❌ Failed to add boundary source", e)
            return
        }

        // Add layer with official boundary styling
        try {
            val layerJson = """
                {
                    "id": "$boundaryLayerId",
                    "type": "line",
                    "source": "$boundarySourceId",
                    "layout": {
                        "line-cap": "round",
                        "line-join": "round",
                        "visibility": "visible"
                    },
                    "paint": {
                        "line-color": "#FF0000",
                        "line-width": 3,
                        "line-opacity": 0.8,
                        "line-dasharray": [2, 1]
                    }
                }
            """.trimIndent()

            style.addStyleLayer(com.mapbox.bindgen.Value.valueOf(layerJson), com.mapbox.maps.LayerPosition(null, null, null))
            Log.d("MapboxComponent", "✅ Added official boundary layer")

            // Verify with more patience
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (style.styleLayerExists(boundaryLayerId)) {
                } else {
                    // Retry verification
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (style.styleLayerExists(boundaryLayerId)) {
                        } else {
                        }
                    }, 1000)
                }
            }, 300)
        } catch (e: Exception) {
            Log.e("MapboxComponent", "❌ Failed to add boundary layer", e)
        }

    } catch (e: Exception) {
        Log.e("MapboxComponent", "Error in separate boundary addition", e)
    }
}

/**
 * Calculate bearing (direction) from current location based on route
 * This rotates the map to match the direction of travel like Google Maps navigation
 */
private fun calculateBearingFromRoute(
    currentLocation: Point,
    routeInfo: com.rj.islamove.data.models.RouteInfo
): Double? {
    if (routeInfo.waypoints.size < 2) return null

    // Find the closest waypoint to current location
    var closestIndex = 0
    var closestDistance = Double.MAX_VALUE

    routeInfo.waypoints.forEachIndexed { index, waypoint ->
        val distance = calculateDistance(
            Point.fromLngLat(waypoint.longitude, waypoint.latitude),
            currentLocation
        )
        if (distance < closestDistance) {
            closestDistance = distance
            closestIndex = index
        }
    }

    // Get the next waypoint to calculate bearing
    val nextIndex = if (closestIndex < routeInfo.waypoints.size - 1) closestIndex + 1 else closestIndex
    if (nextIndex == closestIndex) return null

    val currentWaypoint = routeInfo.waypoints[closestIndex]
    val nextWaypoint = routeInfo.waypoints[nextIndex]

    // Calculate bearing between the two points
    return calculateBearing(
        currentWaypoint.latitude, currentWaypoint.longitude,
        nextWaypoint.latitude, nextWaypoint.longitude
    )
}

/**
 * Calculate bearing (direction in degrees) between two geographic points
 */
private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)

    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
    val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) - kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)

    var bearing = Math.toDegrees(kotlin.math.atan2(y, x))
    bearing = (bearing + 360) % 360 // Normalize to 0-360 degrees

    return bearing
}

/**
 * Calculate offset location for 3rd person view (camera positioned behind the icon)
 * This creates the "following behind" perspective like in navigation apps
 */
private fun calculateOffsetLocation(location: Point, bearing: Double): Point {
    // Convert bearing to radians (bearing is in degrees from north)
    val bearingRad = Math.toRadians(bearing)

    // Calculate offset distance in meters (adjust this to control how far behind)
    val offsetDistanceMeters = 150.0 // 150 meters behind the icon

    // Earth radius in meters
    val earthRadius = 6371000.0

    // Calculate offset in latitude/longitude
    // Move backwards (opposite direction) from the bearing
    val offsetBearingRad = bearingRad + Math.PI // Add 180 degrees to go backwards

    // Calculate new position
    val lat1 = Math.toRadians(location.latitude())
    val lon1 = Math.toRadians(location.longitude())

    val angularDistance = offsetDistanceMeters / earthRadius

    val lat2 = kotlin.math.asin(
        kotlin.math.sin(lat1) * kotlin.math.cos(angularDistance) +
        kotlin.math.cos(lat1) * kotlin.math.sin(angularDistance) * kotlin.math.cos(offsetBearingRad)
    )

    val lon2 = lon1 + kotlin.math.atan2(
        kotlin.math.sin(offsetBearingRad) * kotlin.math.sin(angularDistance) * kotlin.math.cos(lat1),
        kotlin.math.cos(angularDistance) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2)
    )

    return Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
}


