package com.rj.islamove.data.models

/**
 * Data class for custom landmarks created by admin
 * Firebase Firestore requires no-argument constructor for deserialization
 */
data class CustomLandmark(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val color: String = "red", // Color for the marker: red, blue, green, orange, purple, yellow
    val createdAt: Long = System.currentTimeMillis()
)