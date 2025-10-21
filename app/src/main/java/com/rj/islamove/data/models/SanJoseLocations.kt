package com.rj.islamove.data.models

import com.google.firebase.firestore.GeoPoint

data class SanJoseLocation(
    val name: String,
    val barangay: String,
    val coordinates: GeoPoint,
    val type: LocationType = LocationType.BARANGAY
)

enum class LocationType {
    POBLACION,
    BARANGAY,
    LANDMARK,
    MUNICIPAL_HALL,
    BEACH,
    SHRINE,
    BOUNDARY
}

data class FareMatrixEntry(
    val id: String = "",
    val fromLocation: String,
    val toLocation: String,
    val regularFare: Double,
    val discountFare: Double, // 20% discount for seniors, PWD, students
    val isActive: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

object SanJoseLocationsData {

    // San Jose, Dinagat Islands coordinates (provincial capital center)
    private const val SAN_JOSE_LAT = 10.0080
    private const val SAN_JOSE_LNG = 125.5718

    val locations = listOf(
        // POBLACION AREA - San Jose (Provincial Capital)
        SanJoseLocation("San Jose Poblacion", "San Jose", GeoPoint(10.0080, 125.5718), LocationType.POBLACION),

        // 12 BARANGAYS OF SAN JOSE, DINAGAT ISLANDS
        SanJoseLocation("Barangay San Jose", "San Jose", GeoPoint(10.0080, 125.5718)),
        SanJoseLocation("Barangay San Juan", "San Juan", GeoPoint(10.0180, 125.5818)),
        SanJoseLocation("Barangay Don Ruben", "Don Ruben", GeoPoint(9.9980, 125.5618)),
        SanJoseLocation("Justiniana Edera", "Justiniana Edera", GeoPoint(10.0280, 125.5918)),
        SanJoseLocation("Barangay Jacquez", "Jacquez", GeoPoint(9.9880, 125.5518)),
        SanJoseLocation("Barangay Aurelio", "Aurelio", GeoPoint(10.0380, 125.6018)),
        SanJoseLocation("Barangay Matingbe", "Matingbe", GeoPoint(9.9780, 125.5418)),
        SanJoseLocation("Barangay Luna", "Luna", GeoPoint(9.9680, 125.5318)),
        SanJoseLocation("Barangay Wilson", "Wilson", GeoPoint(9.9580, 125.5218)),
        SanJoseLocation("Barangay Cuarinta", "Cuarinta", GeoPoint(9.9480, 125.5118)),
        SanJoseLocation("Barangay Mahayahay", "Mahayahay", GeoPoint(10.0480, 125.6118)),
        SanJoseLocation("Barangay Santa Cruz", "Santa Cruz", GeoPoint(10.0580, 125.6218)),

        // DINAGAT ISLANDS LANDMARKS AND TOURIST SPOTS
        SanJoseLocation("PBMA Headquarters", "San Jose", GeoPoint(10.0090, 125.5728), LocationType.SHRINE),
        SanJoseLocation("Islander's Castle", "San Jose", GeoPoint(10.0050, 125.5708), LocationType.LANDMARK),
        SanJoseLocation("San Jose Municipal Hall", "San Jose", GeoPoint(10.0070, 125.5708), LocationType.MUNICIPAL_HALL),
        SanJoseLocation("San Jose Port", "San Jose", GeoPoint(10.0060, 125.5698), LocationType.BEACH),
        SanJoseLocation("Dinagat Islands View Deck", "San Jose", GeoPoint(10.0100, 125.5738), LocationType.LANDMARK),

        // NEARBY MUNICIPALITIES (for context and longer distance calculations)
        SanJoseLocation("Basilisa", "Basilisa", GeoPoint(10.0280, 125.5918)),
        SanJoseLocation("Cagdianao", "Cagdianao", GeoPoint(10.0480, 125.6118)),
        SanJoseLocation("Dinagat", "Dinagat", GeoPoint(10.0680, 125.6318)),
        SanJoseLocation("Libjo", "Libjo", GeoPoint(10.0880, 125.6518)),
        SanJoseLocation("Loreto", "Loreto", GeoPoint(10.1080, 125.6718)),
        SanJoseLocation("San Jose (Surigao del Norte)", "San Jose", GeoPoint(9.7880, 125.5518)),

        // SURIGAO DEL NORTE CONNECTION POINTS
        SanJoseLocation("Surigao City", "Surigao City", GeoPoint(9.7880, 125.4918)),
        SanJoseLocation("Dapa Port", "Dapa", GeoPoint(9.7580, 125.5118)),

        // KEY TRANSPORTATION HUBS
        SanJoseLocation("San Jose Jeepney Terminal", "San Jose", GeoPoint(10.0075, 125.5710), LocationType.LANDMARK),
        SanJoseLocation("San Jose Tricycle Terminal", "San Jose", GeoPoint(10.0065, 125.5715), LocationType.LANDMARK),
        SanJoseLocation("San Jose Market", "San Jose", GeoPoint(10.0085, 125.5725), LocationType.LANDMARK),

        // ADDITIONAL LANDMARKS FOR BETTER COVERAGE
        SanJoseLocation("San Jose Public Plaza", "San Jose", GeoPoint(10.0080, 125.5718), LocationType.LANDMARK),
        SanJoseLocation("San Jose Catholic Church", "San Jose", GeoPoint(10.0080, 125.5718), LocationType.LANDMARK),
        SanJoseLocation("San Jose Public Cemetery", "San Jose", GeoPoint(10.0120, 125.5758), LocationType.LANDMARK),
        SanJoseLocation("San Jose Central School", "San Jose", GeoPoint(10.0090, 125.5728), LocationType.LANDMARK),

        // BEACHES AND RECREATIONAL AREAS
        SanJoseLocation("Bito Beach", "San Jose", GeoPoint(9.9980, 125.5618), LocationType.BEACH),
        SanJoseLocation("San Jose Beach Resort", "San Jose", GeoPoint(9.9950, 125.5588), LocationType.BEACH),
        SanJoseLocation("Sunset Beach", "San Jose", GeoPoint(9.9920, 125.5558), LocationType.BEACH),

        // MOUNTAIN AND HIGHLAND AREAS
        SanJoseLocation("Mount Redondo", "San Jose", GeoPoint(10.0180, 125.5818), LocationType.LANDMARK),
        SanJoseLocation("Dinagat Highlands", "San Jose", GeoPoint(10.0280, 125.5918), LocationType.LANDMARK),

        // ADDITIONAL BARANGAY LANDMARKS
        SanJoseLocation("Don Ruben Elementary School", "Don Ruben", GeoPoint(9.9980, 125.5618), LocationType.LANDMARK),
        SanJoseLocation("Jacquez Chapel", "Jacquez", GeoPoint(9.9880, 125.5518), LocationType.LANDMARK),
        SanJoseLocation("Aurelio Health Center", "Aurelio", GeoPoint(10.0380, 125.6018), LocationType.LANDMARK),
        SanJoseLocation("Matingbe Basketball Court", "Matingbe", GeoPoint(9.9780, 125.5418), LocationType.LANDMARK),
        SanJoseLocation("Luna Day Care Center", "Luna", GeoPoint(9.9680, 125.5318), LocationType.LANDMARK),
        SanJoseLocation("Wilson Barangay Hall", "Wilson", GeoPoint(9.9580, 125.5218), LocationType.LANDMARK),
        SanJoseLocation("Cuarinta Elementary School", "Cuarinta", GeoPoint(9.9480, 125.5118), LocationType.LANDMARK),
        SanJoseLocation("Mahayahay Chapel", "Mahayahay", GeoPoint(10.0480, 125.6118), LocationType.LANDMARK),
        SanJoseLocation("Santa Cruz Beach", "Santa Cruz", GeoPoint(10.0580, 125.6218), LocationType.BEACH)
    )

    // Get location by name (exact match)
    fun getLocationByName(name: String): SanJoseLocation? {
        return locations.find { it.name.equals(name, ignoreCase = true) }
    }

    // Get locations by barangay
    fun getLocationsByBarangay(barangay: String): List<SanJoseLocation> {
        return locations.filter { it.barangay.equals(barangay, ignoreCase = true) }
    }

    // Get all barangays
    fun getAllBarangays(): List<String> {
        return locations.map { it.barangay }.distinct().sorted()
    }

    // Get all location names
    fun getAllLocationNames(): List<String> {
        return locations.map { it.name }.sorted()
    }

    // Calculate distance between two coordinates in meters
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    // Simple fare calculation based on distance
    fun calculateFareEstimate(pickupLat: Double, pickupLng: Double, destLat: Double, destLng: Double, discountPercentage: Int? = null): Double {
        val distance = calculateDistance(pickupLat, pickupLng, destLat, destLng)

        // Base fare calculation (₱15 base + ₱3 per kilometer)
        val baseFare = 15.0
        val perKmRate = 3.0
        val distanceInKm = distance / 1000.0 // Convert meters to km for fare calculation
        val calculatedFare = baseFare + (distanceInKm * perKmRate)

        // Apply discount if available
        return if (discountPercentage != null && discountPercentage > 0) {
            val discountMultiplier = (100 - discountPercentage) / 100.0
            calculatedFare * discountMultiplier
        } else {
            calculatedFare
        }
    }

    // Get nearby locations within a certain radius (in kilometers)
    fun getNearbyLocations(latitude: Double, longitude: Double, radiusKm: Double = 1.0): List<SanJoseLocation> {
        return locations.filter { location ->
            val distance = calculateDistance(latitude, longitude, location.coordinates.latitude, location.coordinates.longitude)
            distance <= radiusKm
        }.sortedBy { location ->
            calculateDistance(latitude, longitude, location.coordinates.latitude, location.coordinates.longitude)
        }
    }

    // Get location type counts
    fun getLocationTypeCounts(): Map<LocationType, Int> {
        return locations.groupingBy { it.type }.eachCount()
    }
}