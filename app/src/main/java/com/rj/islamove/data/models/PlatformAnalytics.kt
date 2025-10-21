package com.rj.islamove.data.models

data class PlatformAnalytics(
    val totalRides: Int = 0,
    val activeRides: Int = 0,
    val completedRides: Int = 0,
    val cancelledRides: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalUsers: Int = 0,
    val totalDrivers: Int = 0,
    val totalPassengers: Int = 0,
    val activeUsers: Int = 0,
    val onlineDrivers: Int = 0,
    val newSignups: Int = 0,
    val avgResponseTime: Int = 0, // in seconds
    val completionRate: Double = 0.0, // percentage
    val averageRating: Double = 0.0,
    val peakHourStart: String = "00:00",
    val peakHourEnd: String = "00:00",
    val retentionRate: Double = 0.0, // 7-day retention percentage
    val lastUpdated: Long = System.currentTimeMillis()
)