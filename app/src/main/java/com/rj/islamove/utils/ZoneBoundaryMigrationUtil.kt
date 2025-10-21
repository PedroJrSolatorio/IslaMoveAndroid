package com.rj.islamove.utils

import android.util.Log
import com.mapbox.geojson.Point
import com.rj.islamove.data.models.BoundaryPoint
import com.rj.islamove.data.models.ZoneBoundary
import com.rj.islamove.data.repository.ZoneBoundaryRepository

/**
 * Utility to migrate hardcoded barangay boundaries from BoundaryFareUtils to Firestore
 * This should be run ONCE by an admin to populate the zone_boundaries collection
 */
object ZoneBoundaryMigrationUtil {
    private const val TAG = "ZoneBoundaryMigration"

    // Hardcoded boundaries from BoundaryFareUtils
    private val barangayBoundaries = mapOf(
        "DON RUBEN" to listOf(
            listOf(
                Point.fromLngLat(125.5734962250065, 10.03183658179857),
                Point.fromLngLat(125.5657766997762, 10.01897338535086),
                Point.fromLngLat(125.5731395672249, 10.01588610000339),
                Point.fromLngLat(125.573763793572, 10.01457623238114),
                Point.fromLngLat(125.576883441137, 10.01454288450369),
                Point.fromLngLat(125.5819653665864, 10.00992545247885),
                Point.fromLngLat(125.5869557927849, 10.00830535476032),
                Point.fromLngLat(125.5865079605079, 10.01565855839661),
                Point.fromLngLat(125.6165845456529, 10.01633054246815),
                Point.fromLngLat(125.6235127006451, 10.02390718057719),
                Point.fromLngLat(125.6101525238816, 10.02142300423256),
                Point.fromLngLat(125.6001521769799, 10.02127959721),
                Point.fromLngLat(125.5874407537495, 10.02343538373322),
                Point.fromLngLat(125.5734962250065, 10.03183658179857)
            )
        ),
        "MATINGBE" to listOf(
            listOf(
                Point.fromLngLat(125.5713768481285, 10.00037913107052),
                Point.fromLngLat(125.5764700486811, 9.993350211497338),
                Point.fromLngLat(125.5900648031143, 9.993900077374025),
                Point.fromLngLat(125.5904034909142, 9.998815177977377),
                Point.fromLngLat(125.5713768481285, 10.00037913107052)
            )
        ),
        "SAN JOSE" to listOf(
            listOf(
                Point.fromLngLat(125.5781669654779, 10.00183807179492),
                Point.fromLngLat(125.578880618731, 9.999764688298461),
                Point.fromLngLat(125.5831120889408, 9.999442071016782),
                Point.fromLngLat(125.5903893559344, 9.998832094600429),
                Point.fromLngLat(125.5913927881719, 10.00433982404878),
                Point.fromLngLat(125.5781669654779, 10.00183807179492)
            )
        ),
        "SAN JUAN" to listOf(
            listOf(
                Point.fromLngLat(125.5818 - 0.015, 10.0180 - 0.010),
                Point.fromLngLat(125.5818 + 0.015, 10.0180 - 0.010),
                Point.fromLngLat(125.5818 + 0.015, 10.0180 + 0.010),
                Point.fromLngLat(125.5818 - 0.015, 10.0180 + 0.010),
                Point.fromLngLat(125.5818 - 0.015, 10.0180 - 0.010)
            )
        ),
        "JUSTINIANA EDERA" to listOf(
            listOf(
                Point.fromLngLat(125.5918 - 0.015, 10.0280 - 0.010),
                Point.fromLngLat(125.5918 + 0.015, 10.0280 - 0.010),
                Point.fromLngLat(125.5918 + 0.015, 10.0280 + 0.010),
                Point.fromLngLat(125.5918 - 0.015, 10.0280 + 0.010),
                Point.fromLngLat(125.5918 - 0.015, 10.0280 - 0.010)
            )
        ),
        "JACQUEZ" to listOf(
            listOf(
                Point.fromLngLat(125.573433798336, 10.00485778252294),
                Point.fromLngLat(125.5756707591237, 10.00237524060106),
                Point.fromLngLat(125.5781662516026, 10.00183946420349),
                Point.fromLngLat(125.5913935341434, 10.00434213089015),
                Point.fromLngLat(125.5914272351192, 10.00584031090346),
                Point.fromLngLat(125.5899794369327, 10.00873055427746),
                Point.fromLngLat(125.5869605767298, 10.0083027000066),
                Point.fromLngLat(125.573433798336, 10.00485778252294)
            )
        ),
        "AURELIO" to listOf(
            listOf(
                Point.fromLngLat(125.6026228645159, 10.00729576895235),
                Point.fromLngLat(125.5899799595042, 10.00873049753719),
                Point.fromLngLat(125.5914281813426, 10.00584059436703),
                Point.fromLngLat(125.5913971471119, 10.00434215306372),
                Point.fromLngLat(125.6142002451448, 10.00274890580053),
                Point.fromLngLat(125.6168993134768, 10.00734773497958),
                Point.fromLngLat(125.6026228645159, 10.00729576895235)
            )
        ),
        "LUNA" to listOf(
            listOf(
                Point.fromLngLat(125.5904062245744, 9.998815153780864),
                Point.fromLngLat(125.5900679836232, 9.993900908490563),
                Point.fromLngLat(125.5995991481587, 9.990602380958203),
                Point.fromLngLat(125.6054199598882, 9.993105568328739),
                Point.fromLngLat(125.61057234217, 9.998875133963541),
                Point.fromLngLat(125.5904062245744, 9.998815153780864)
            )
        ),
        "WILSON" to listOf(
            listOf(
                Point.fromLngLat(125.5886802546179, 10.05643938852453),
                Point.fromLngLat(125.5719836422801, 10.04301908228273),
                Point.fromLngLat(125.5874225253, 10.03229588981534),
                Point.fromLngLat(125.5980988139267, 10.03185939663898),
                Point.fromLngLat(125.6053892535263, 10.03378734028272),
                Point.fromLngLat(125.6052033684707, 10.05322075963551),
                Point.fromLngLat(125.5999784862809, 10.05280413942301),
                Point.fromLngLat(125.5961606179453, 10.05555555685378),
                Point.fromLngLat(125.5886802546179, 10.05643938852453)
            )
        ),
        "CUARINTA" to listOf(
            listOf(
                Point.fromLngLat(125.6051962902842, 10.04492578168702),
                Point.fromLngLat(125.6053937450196, 10.03379011141545),
                Point.fromLngLat(125.6092624258729, 10.03318200159379),
                Point.fromLngLat(125.6130417343169, 10.03437984730024),
                Point.fromLngLat(125.6232328704231, 10.03623096851005),
                Point.fromLngLat(125.6166258305561, 10.05362580026996),
                Point.fromLngLat(125.6052093722137, 10.05321979735193),
                Point.fromLngLat(125.6051962902842, 10.04492578168702)
            )
        ),
        "MAHAYAHAY" to listOf(
            listOf(
                Point.fromLngLat(125.5913977082047, 10.00433765993653),
                Point.fromLngLat(125.5904058450962, 9.998818778730932),
                Point.fromLngLat(125.6105735377439, 9.998878217649215),
                Point.fromLngLat(125.6141998507534, 10.00274700737618),
                Point.fromLngLat(125.5913977082047, 10.00433765993653)
            )
        ),
        "SANTA CRUZ" to listOf(
            listOf(
                Point.fromLngLat(125.6001547867659, 10.02128604810581),
                Point.fromLngLat(125.6101520556057, 10.02142896353291),
                Point.fromLngLat(125.6235176258964, 10.0239151596875),
                Point.fromLngLat(125.6232296269553, 10.03620809595071),
                Point.fromLngLat(125.6130468158152, 10.0343655323358),
                Point.fromLngLat(125.6092650336733, 10.03316865553245),
                Point.fromLngLat(125.6053935788765, 10.03378282529559),
                Point.fromLngLat(125.5980987931886, 10.03185662364937),
                Point.fromLngLat(125.5874211880689, 10.03228644191807),
                Point.fromLngLat(125.5856491748653, 10.02453971637412),
                Point.fromLngLat(125.5874522213382, 10.02344563055514),
                Point.fromLngLat(125.6001547867659, 10.02128604810581)
            )
        ),
        "POBLACION" to listOf(
            listOf(
                Point.fromLngLat(125.5665442383253, 10.01383018628581),
                Point.fromLngLat(125.5687754711319, 10.0057852788571),
                Point.fromLngLat(125.5734248764734, 10.00486453502546),
                Point.fromLngLat(125.5869467756316, 10.00830577962656),
                Point.fromLngLat(125.5819560244198, 10.00991251247797),
                Point.fromLngLat(125.5768821027509, 10.01453590733683),
                Point.fromLngLat(125.5737554465815, 10.01456904415923),
                Point.fromLngLat(125.5731284790869, 10.0157443949333),
                Point.fromLngLat(125.5720696502582, 10.01628516342233),
                Point.fromLngLat(125.5665442383253, 10.01383018628581)
            )
        )
    )

    /**
     * Migrate all hardcoded boundaries to Firestore
     * Returns the number of boundaries successfully migrated
     */
    suspend fun migrateToFirestore(repository: ZoneBoundaryRepository): Result<Int> {
        return try {
            var successCount = 0
            var failCount = 0

            Log.i(TAG, "Starting migration of ${barangayBoundaries.size} zone boundaries")

            for ((name, polygons) in barangayBoundaries) {
                // Take the first polygon (most boundaries have only one)
                val points = polygons.firstOrNull()?.map { point ->
                    BoundaryPoint(
                        latitude = point.latitude(),
                        longitude = point.longitude()
                    )
                } ?: emptyList()

                val zoneBoundary = ZoneBoundary(
                    name = name,
                    points = points,
                    fillColor = "#FF9800",
                    strokeColor = "#F57C00",
                    strokeWidth = 2.0,
                    isActive = true
                )

                val result = repository.addZoneBoundary(zoneBoundary)
                if (result.isSuccess) {
                    successCount++
                    Log.i(TAG, "✅ Migrated: $name (${points.size} points)")
                } else {
                    failCount++
                    Log.e(TAG, "❌ Failed to migrate: $name - ${result.exceptionOrNull()?.message}")
                }
            }

            Log.i(TAG, "Migration complete: $successCount succeeded, $failCount failed")
            Result.success(successCount)
        } catch (e: Exception) {
            Log.e(TAG, "Migration error", e)
            Result.failure(e)
        }
    }

    /**
     * Check if boundaries have already been migrated
     */
    suspend fun isMigrated(repository: ZoneBoundaryRepository): Boolean {
        return try {
            val result = repository.getAllZoneBoundaries()
            val boundaries = result.getOrNull() ?: emptyList()
            val isMigrated = boundaries.isNotEmpty()
            Log.d(TAG, "Migration status: ${if (isMigrated) "Already migrated (${boundaries.size} boundaries)" else "Not migrated"}")
            isMigrated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking migration status", e)
            false
        }
    }
}