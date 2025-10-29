package com.rj.islamove.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.DriverReport
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_NAME = "driver_reports"
    }

    suspend fun submitReport(report: DriverReport): Result<String> {
        return try {
            val reportWithId = report.copy(
                id = firestore.collection(COLLECTION_NAME).document().id
            )

            firestore.collection(COLLECTION_NAME)
                .document(reportWithId.id)
                .set(reportWithId)
                .await()

            Result.success(reportWithId.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReportsForDriver(driverId: String): Result<List<DriverReport>> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("driverId", driverId)
                .get()
                .await()

            val reports = querySnapshot.documents.mapNotNull { document ->
                document.toObject(DriverReport::class.java)
            }

            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(reportId: String, newStatus: com.rj.islamove.data.models.ReportStatus): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_NAME)
                .document(reportId)
                .update("status", newStatus)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverReportCounts(): Result<Map<String, Int>> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereIn("status", listOf(
                    com.rj.islamove.data.models.ReportStatus.PENDING.name,
                    com.rj.islamove.data.models.ReportStatus.UNDER_REVIEW.name
                ))
                .get()
                .await()

            val reports = querySnapshot.documents.mapNotNull { document ->
                document.toObject(DriverReport::class.java)
            }

            val reportCounts = reports
                .groupBy { it.driverId }
                .mapValues { (_, reports) -> reports.size }

            Result.success(reportCounts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}