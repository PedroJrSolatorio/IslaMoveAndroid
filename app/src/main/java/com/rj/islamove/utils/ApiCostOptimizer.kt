package com.rj.islamove.utils

import android.util.Log

/**
 * Central API Cost Optimization Utility
 *
 * This utility provides static methods to help minimize Mapbox API costs
 * across the entire application.
 */
object ApiCostOptimizer {
    private const val TAG = "ApiCostOptimizer"

    /**
     * Cost optimization recommendations
     */
    fun getOptimizationRecommendations(): List<String> {
        return listOf(
            "💰 Use 'Outdoors' map style for built-in landmarks (no API cost)",
            "🎯 Routes under 1km use direct calculation (no API cost)",
            "⏰ No cooldown restrictions for navigation",
            "📊 No daily limits applied",
            "💾 Routes cached for 48 hours to reduce API calls",
            "🚫 POI search APIs disabled to prevent unexpected charges",
            "📈 No hourly limits applied",
            "⚡ No emergency throttle restrictions"
        )
    }

    /**
     * Estimate daily API costs based on usage patterns
     */
    fun estimateMonthlyApiCost(
        dailyRouteRequests: Int = 50,
        daysPerMonth: Int = 30
    ): Double {
        val routeCostPerRequest = 0.5 // $0.50 per Directions API call
        val monthlyRouteCost = dailyRouteRequests * daysPerMonth * routeCostPerRequest

        // POI search is disabled, so no additional costs
        val monthlyPOICost = 0.0

        val totalMonthlyCost = monthlyRouteCost + monthlyPOICost

        Log.i(TAG, """
            💰 Monthly API Cost Estimate:
            - Route requests: $dailyRouteRequests/day × $daysPerMonth days × $routeCostPerRequest = $${"%.2f".format(monthlyRouteCost)}
            - POI searches: DISABLED = $0.00
            - Total estimated monthly cost: $${"%.2f".format(totalMonthlyCost)}
        """.trimIndent())

        return totalMonthlyCost
    }

    /**
     * Get cost-saving tips based on current usage
     */
    fun getCostSavingTips(currentDailyRequests: Int): List<String> {
        return when {
            currentDailyRequests > 200 -> listOf(
                "🚨 Very high usage detected! Consider these optimizations:",
                "• Increase route caching duration",
                "• Switch to direct routes for short distances",
                "• Review if all route requests are necessary"
            )
            currentDailyRequests > 100 -> listOf(
                "⚠️ Moderate usage - optimization recommended:",
                "• Batch multiple route requests when possible",
                "• Use cached routes from previous trips",
                "• Consider using estimated times for non-critical routes"
            )
            else -> listOf(
                "✅ Usage is within safe limits",
                "• Current optimizations are working well",
                "• Monitor for any usage spikes",
                "• Keep using built-in map landmarks"
            )
        }
    }

    /**
     * Emergency cost control measures
     */
    fun activateEmergencyCostControl(): List<String> {
        Log.e(TAG, "🚨 EMERGENCY COST CONTROL ACTIVATED!")

        return listOf(
            "🛑 All non-essential API calls suspended",
            "📍 Using direct route calculations only",
            "🎯 Only critical navigation requests allowed",
            "💾 Relying heavily on cached data",
            "⏰ All requests throttled to minimum intervals",
            "📊 Monitoring will reset at next daily cycle"
        )
    }

    /**
     * Best practices summary
     */
    fun getBestPractices(): Map<String, List<String>> {
        return mapOf(
            "Route Optimization" to listOf(
                "Cache routes for up to 48 hours",
                "Use direct calculation for distances under 1km",
                "No rate limiting for navigation requests",
                "Batch route requests when possible"
            ),
            "Map Display" to listOf(
                "Use 'Outdoors' style for built-in landmarks",
                "Disable POI search APIs",
                "Rely on Mapbox's included map data",
                "Avoid real-time POI updates"
            ),
            "Cost Monitoring" to listOf(
                "No daily request limits",
                "No emergency throttling restrictions",
                "No hourly usage limits",
                "Monitor estimated daily costs"
            ),
            "User Experience" to listOf(
                "Show cache status to users",
                "Graceful fallback to direct routes",
                "Inform users of cost-saving measures",
                "Provide manual refresh options"
            )
        )
    }
}