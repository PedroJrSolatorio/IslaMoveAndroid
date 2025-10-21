package com.rj.islamove.data.repository

import com.rj.islamove.data.models.SanJoseLocation
import com.rj.islamove.data.models.SanJoseLocationsData
import com.rj.islamove.data.models.LocationType
import com.rj.islamove.ui.components.POIInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class POIRepository @Inject constructor() {
    
    /**
     * Find POI near a given coordinate (for map tap detection)
     * Uses Haversine distance calculation
     */
    suspend fun findPOINearLocation(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 100.0 // 100 meters default radius
    ): Result<POIInfo?> {
        return try {
            val nearbyLocations = SanJoseLocationsData.locations.filter { location ->
                val distance = calculateDistance(
                    lat1 = latitude,
                    lon1 = longitude,
                    lat2 = location.coordinates.latitude,
                    lon2 = location.coordinates.longitude
                )
                distance <= radiusMeters
            }
            
            // Return the closest POI if found
            val closestLocation = nearbyLocations.minByOrNull { location ->
                calculateDistance(
                    lat1 = latitude,
                    lon1 = longitude,
                    lat2 = location.coordinates.latitude,
                    lon2 = location.coordinates.longitude
                )
            }
            
            val poiInfo = closestLocation?.let { location ->
                createPOIInfo(location)
            }
            
            Result.success(poiInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all landmarks and tourist attractions
     */
    suspend fun getAllPOIs(): Result<List<POIInfo>> {
        return try {
            val pois = SanJoseLocationsData.locations
                .filter { it.type != LocationType.BARANGAY } // Exclude regular barangays
                .map { location -> createPOIInfo(location) }
            Result.success(pois)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get POIs by type (beaches, landmarks, government buildings, etc.)
     */
    suspend fun getPOIsByType(type: LocationType): Result<List<POIInfo>> {
        return try {
            val pois = SanJoseLocationsData.locations
                .filter { it.type == type }
                .map { location -> createPOIInfo(location) }
            Result.success(pois)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search POIs by name or description
     */
    suspend fun searchPOIs(query: String): Result<List<POIInfo>> {
        return try {
            val filteredPOIs = SanJoseLocationsData.locations
                .filter { location ->
                    location.name.contains(query, ignoreCase = true) ||
                    location.barangay.contains(query, ignoreCase = true)
                }
                .map { location -> createPOIInfo(location) }
            Result.success(filteredPOIs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create POIInfo with enhanced data for specific Dinagat Islands locations
     */
    private fun createPOIInfo(location: SanJoseLocation): POIInfo {
        return when (location.name) {
            "PBMA Headquarters" -> POIInfo(
                location = location,
                rating = 4.2f,
                reviewCount = 89,
                description = "Headquarters of the Philippine Benevolent Missionaries Association (PBMA), a religious organization central to Dinagat Islands' community.",
                hours = "Open daily 6:00 AM - 6:00 PM",
                isOpen = true,
                phone = "+63 xxx xxx xxxx"
            )
            
            "Islander's Castle" -> POIInfo(
                location = location,
                rating = 4.5f,
                reviewCount = 127,
                description = "Famous landmark castle owned by the Ecleo family. Visible during island hopping trips, located on top of the mountain with scenic views.",
                hours = "Open 24 hours",
                isOpen = true
            )
            
            "San Jose Port" -> POIInfo(
                location = location,
                rating = 3.8f,
                reviewCount = 45,
                description = "Main ferry terminal connecting Dinagat Islands to Surigao. Gateway for tourists and locals traveling to and from the island.",
                hours = "24 hours",
                isOpen = true,
                phone = "+63 xxx xxx xxxx"
            )
            
            "Puyangi Beach" -> POIInfo(
                location = location,
                rating = 4.3f,
                reviewCount = 156,
                description = "Beautiful beach resort area with clear waters and white sand. Popular spot for swimming and relaxation.",
                hours = "6:00 AM - 8:00 PM",
                isOpen = true
            )
            
            "Bitaug Beach" -> POIInfo(
                location = location,
                rating = 4.6f,
                reviewCount = 203,
                description = "Pristine beach destination known for its crystal-clear waters and coral reefs. Perfect for snorkeling and swimming.",
                hours = "6:00 AM - 6:00 PM",
                isOpen = true
            )
            
            "Duyong Beach" -> POIInfo(
                location = location,
                rating = 4.4f,
                reviewCount = 178,
                description = "Scenic beach location with stunning sunset views. Popular for beach camping and water activities.",
                hours = "Open 24 hours",
                isOpen = true
            )
            
            "Provincial Capitol" -> POIInfo(
                location = location,
                description = "Seat of the provincial government of Dinagat Islands. Administrative center for government services.",
                hours = "Monday-Friday 8:00 AM - 5:00 PM",
                isOpen = false, // Assuming it's after hours
                phone = "+63 xxx xxx xxxx"
            )
            
            "San Jose Municipal Hall" -> POIInfo(
                location = location,
                description = "Municipal government office providing local government services to residents and visitors.",
                hours = "Monday-Friday 8:00 AM - 5:00 PM",
                isOpen = false,
                phone = "+63 xxx xxx xxxx"
            )
            
            "Local Market" -> POIInfo(
                location = location,
                rating = 4.0f,
                reviewCount = 67,
                description = "Local public market offering fresh produce, seafood, and local delicacies. Great place to experience local culture.",
                hours = "5:00 AM - 7:00 PM",
                isOpen = true
            )
            
            "Health Center" -> POIInfo(
                location = location,
                description = "Municipal health center providing basic healthcare services to the community.",
                hours = "Monday-Friday 7:00 AM - 4:00 PM",
                isOpen = false,
                phone = "+63 xxx xxx xxxx"
            )
            
            else -> POIInfo(
                location = location,
                description = when (location.type) {
                    LocationType.BARANGAY -> "Barangay ${location.barangay} - Local community area"
                    LocationType.BEACH -> "Beautiful beach destination in Dinagat Islands"
                    LocationType.LANDMARK -> "Notable landmark in San Jose, Dinagat Islands"
                    LocationType.MUNICIPAL_HALL -> "Government building providing public services"
                    LocationType.SHRINE -> "Religious site serving the local community"
                    LocationType.POBLACION -> "Central poblacion area of San Jose"
                    LocationType.BOUNDARY -> "Boundary area of San Jose municipality"
                },
                hours = if (location.type == LocationType.BEACH) "Open 24 hours" else null,
                isOpen = location.type == LocationType.BEACH
            )
        }
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLatRad / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}