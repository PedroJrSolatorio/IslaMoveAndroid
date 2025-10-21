package com.rj.islamove.data.repository

import com.rj.islamove.data.models.BookingLocation
import com.rj.islamove.data.models.FareEstimate
import com.rj.islamove.data.models.SanJoseLocation
import com.rj.islamove.data.models.SanJoseLocationsData
import com.rj.islamove.data.models.VehicleCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SanJoseLocationRepository @Inject constructor() {
    
    /**
     * Search San Jose locations based on query
     */
    suspend fun searchLocations(query: String): Result<List<BookingLocation>> {
        return try {
            // Filter locations based on query
            val sanJoseLocations = SanJoseLocationsData.locations.filter { location ->
                location.name.contains(query, ignoreCase = true) ||
                location.barangay.contains(query, ignoreCase = true)
            }

            val bookingLocations = sanJoseLocations.map { location ->
                BookingLocation(
                    address = "${location.name}, ${location.barangay}, San Jose, Dinagat Islands",
                    coordinates = location.coordinates,
                    placeId = location.name.replace(" ", "_").lowercase()
                )
            }
            Result.success(bookingLocations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calculate fare using San Jose Municipal Fare Matrix
     */
    fun calculateFareEstimate(
        pickupLocation: BookingLocation,
        destination: BookingLocation,
        vehicleCategory: VehicleCategory = VehicleCategory.STANDARD,
        discountPercentage: Int? = null
    ): FareEstimate {
        
        android.util.Log.d("SanJoseFareCalculation", "=== FARE CALCULATION DEBUG ===")
        android.util.Log.d("SanJoseFareCalculation", "Original pickup address: '${pickupLocation.address}'")
        android.util.Log.d("SanJoseFareCalculation", "Original destination address: '${destination.address}'")
        android.util.Log.d("SanJoseFareCalculation", "Pickup coordinates: ${pickupLocation.coordinates.latitude}, ${pickupLocation.coordinates.longitude}")
        android.util.Log.d("SanJoseFareCalculation", "Destination coordinates: ${destination.coordinates.latitude}, ${destination.coordinates.longitude}")
        
        // Extract location names from addresses
        val pickupName = extractLocationName(pickupLocation.address)
        val destinationName = extractLocationName(destination.address)
        
        android.util.Log.d("SanJoseFareCalculation", "Extracted pickup name: '$pickupName'")
        android.util.Log.d("SanJoseFareCalculation", "Extracted destination name: '$destinationName'")
        
        // Calculate fare based on distance
        val distance = SanJoseLocationsData.calculateDistance(
            pickupLocation.coordinates.latitude,
            pickupLocation.coordinates.longitude,
            destination.coordinates.latitude,
            destination.coordinates.longitude
        )

        // Use fare calculation function from SanJoseLocationsData
        val baseFare = SanJoseLocationsData.calculateFareEstimate(
            pickupLocation.coordinates.latitude,
            pickupLocation.coordinates.longitude,
            destination.coordinates.latitude,
            destination.coordinates.longitude,
            discountPercentage
        )
        
        android.util.Log.d("SanJoseFareCalculation", "Distance: ${String.format("%.0f", distance)}m")
        android.util.Log.d("SanJoseFareCalculation", "Base fare: $baseFare")
        android.util.Log.d("SanJoseFareCalculation", "Vehicle category: ${vehicleCategory.displayName} (multiplier: ${vehicleCategory.baseMultiplier})")
        
        // Apply vehicle category multiplier
        val totalFare = baseFare * vehicleCategory.baseMultiplier
        
        // Calculate estimated duration based on distance
        val distanceInKm = distance / 1000.0 // Convert meters to km
        val estimatedDuration = max(15, (distanceInKm * 3).toInt()) // Minimum 15 minutes, 3 minutes per km
        
        android.util.Log.d("SanJoseFareCalculation", "Final total fare: $totalFare PHP")
        android.util.Log.d("SanJoseFareCalculation", "Duration: $estimatedDuration min (based on distance: ${String.format("%.1f", distanceInKm)}km)")
        
        val fareEstimate = FareEstimate(
            baseFare = totalFare, // Use the fare matrix amount as base fare
            distanceFare = 0.0,   // No distance fare - matrix covers everything
            timeFare = 0.0,       // No time fare - matrix covers everything
            surgeFactor = 1.0,
            totalEstimate = totalFare, // Total equals base fare from matrix
            currency = "PHP",
            estimatedDuration = estimatedDuration,
            estimatedDistance = distance
        )
        
        android.util.Log.d("SanJoseFareCalculation", "Returning FareEstimate: $fareEstimate")
        return fareEstimate
    }

  
    /**
     * Get all San Jose locations for map display
     */
    fun getAllLocations(): List<SanJoseLocation> {
        return SanJoseLocationsData.locations
    }
    
    /**
     * Check if a location is within San Jose, Dinagat Islands bounds (default boundary)
     */
    fun isWithinSanJose(latitude: Double, longitude: Double): Boolean {
        // San Jose, Dinagat Islands approximate bounds
        val minLat = 9.8  // South bound
        val maxLat = 10.3 // North bound
        val minLng = 125.3 // West bound
        val maxLng = 125.8 // East bound

        val isWithin = latitude in minLat..maxLat && longitude in minLng..maxLng

        // Debug logging to help identify location issues
        if (!isWithin) {
            android.util.Log.d("SanJoseGeoFence", "Location outside bounds - Lat: $latitude, Lng: $longitude")
            android.util.Log.d("SanJoseGeoFence", "Expected bounds: Lat[$minLat-$maxLat], Lng[$minLng-$maxLng]")
            android.util.Log.d("SanJoseGeoFence", "Lat check: ${latitude in minLat..maxLat}, Lng check: ${longitude in minLng..maxLng}")
        } else {
            android.util.Log.d("SanJoseGeoFence", "Location within bounds - Lat: $latitude, Lng: $longitude")
        }

        return isWithin
    }

    /**
     * Check if a location is within a user-defined boundary polygon
     * Uses ray casting algorithm for point-in-polygon test
     */
    fun isWithinBoundary(
        latitude: Double,
        longitude: Double,
        boundaryPoints: List<com.rj.islamove.data.models.BoundaryPointData>
    ): Boolean {
        if (boundaryPoints.size < 3) {
            android.util.Log.w("BoundaryCheck", "Invalid boundary: need at least 3 points")
            return false
        }

        // Ray casting algorithm - count intersections with polygon edges
        var intersections = 0
        val n = boundaryPoints.size

        for (i in 0 until n) {
            val p1 = boundaryPoints[i]
            val p2 = boundaryPoints[(i + 1) % n]

            // Check if point is on an horizontal ray to the right
            if ((p1.latitude > latitude) != (p2.latitude > latitude)) {
                val intersectLng = (p2.longitude - p1.longitude) * (latitude - p1.latitude) /
                                   (p2.latitude - p1.latitude) + p1.longitude

                if (longitude < intersectLng) {
                    intersections++
                }
            }
        }

        // Odd number of intersections = inside polygon
        val isInside = intersections % 2 == 1

        android.util.Log.d("BoundaryCheck", "Point ($latitude, $longitude) is ${if (isInside) "inside" else "outside"} custom boundary")

        return isInside
    }
    
    // REMOVED: getNearestLocation function - no distance calculations needed
    
    private fun extractLocationName(address: String): String {
        // Handle "Current Location" by finding the nearest San Jose location
        if (address.equals("Current Location", ignoreCase = true)) {
            // For "Current Location", assume it's somewhere in San Jose Poblacion
            return "San Jose Poblacion"
        }

        // TEST MODE: If address contains coordinates that are outside San Jose (for testing)
        if (address.contains("Cotabato", ignoreCase = true) ||
            address.contains("Midsayap", ignoreCase = true) ||
            address.contains("6G3P+", ignoreCase = true) ||
            address.contains("Selected Location", ignoreCase = true)) {

            // Map different locations to different San Jose areas for testing fare matrix
            val testLocation = when {
                address.contains("(7.1") || address.contains("125.0") -> "Aurelio" // North coordinates
                address.contains("(7.2") || address.contains("125.1") -> "Sta. Cruz" // Central coordinates
                address.contains("(7.3") || address.contains("125.2") -> "Wilson" // South coordinates
                address.contains("(7.0") || address.contains("124.9") -> "Luna" // West coordinates
                address.contains("(7.4") || address.contains("125.3") -> "Cuarinta" // East coordinates
                else -> "Poblacion" // Default
            }

            android.util.Log.d("SanJoseFareCalculation", "TEST MODE: Mapping external location '$address' to '$testLocation'")
            return testLocation
        }
        
        // Extract the first part before the first comma
        val extractedName = address.split(",").firstOrNull()?.trim() ?: address
        
        // Try to match with known locations for better fare matrix matching
        val knownLocation = SanJoseLocationsData.locations.find { location ->
            location.name.contains(extractedName, ignoreCase = true) ||
            extractedName.contains(location.name, ignoreCase = true) ||
            location.barangay.contains(extractedName, ignoreCase = true) ||
            extractedName.contains(location.barangay, ignoreCase = true)
        }
        
        return knownLocation?.name ?: extractedName
    }
    
    // REMOVED: No distance-based fare calculation - only use base fare from matrix
    
    /**
     * Get fare information for display to users (passengers and drivers)
     */
    fun getFareInfo(
        pickupLocation: BookingLocation,
        destination: BookingLocation,
        discountPercentage: Int? = null
    ): Map<String, Any> {
        // Calculate fare based on distance
        val distance = SanJoseLocationsData.calculateDistance(
            pickupLocation.coordinates.latitude,
            pickupLocation.coordinates.longitude,
            destination.coordinates.latitude,
            destination.coordinates.longitude
        )

        val fare = SanJoseLocationsData.calculateFareEstimate(
            pickupLocation.coordinates.latitude,
            pickupLocation.coordinates.longitude,
            destination.coordinates.latitude,
            destination.coordinates.longitude,
            discountPercentage
        )

        return mapOf(
            "fare" to fare,
            "fareType" to "distance_based_fare",
            "description" to "Fare based on distance traveled",
            "distance" to distance
        )
    }

    // Distance calculation completely removed - only use base fare from matrix

    // Point-in-polygon algorithm removed - no longer needed
}