package com.rj.islamove.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.PassengerReport
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassengerReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PassengerReportRepository {

    companion object {
        private const val COLLECTION_NAME = "passenger_reports"
    }

    override suspend fun submitPassengerReport(report: PassengerReport): Result<Unit> {
        return try {
            // Generate Firestore document ID if not provided
            val reportWithId = if (report.reportId.isEmpty()) {
                report.copy(
                    reportId = firestore.collection(COLLECTION_NAME).document().id
                )
            } else {
                report
            }

            Log.d("PassengerReportRepo", "Submitting report with ID: ${reportWithId.reportId}")

            firestore.collection(COLLECTION_NAME)
                .document(reportWithId.reportId)
                .set(reportWithId)
                .await()

            Log.d("PassengerReportRepo", "✅ Passenger report submitted successfully: ${reportWithId.reportId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PassengerReportRepo", "❌ Failed to submit passenger report", e)
            Result.failure(e)
        }
    }

    override suspend fun getPassengerReports(passengerId: String): Result<List<PassengerReport>> {
        return try {
            Log.d("PassengerReportRepo", "🔍 Querying reports for passengerId: $passengerId")

            val snapshot = passengerReportsCollection
                .whereEqualTo("passengerId", passengerId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            Log.d("PassengerReportRepo", "📊 Query returned ${snapshot.documents.size} documents")

            val reports = snapshot.documents.mapNotNull { doc ->
                Log.d("PassengerReportRepo", "  Document ID: ${doc.id}")
                val report = doc.toObject(PassengerReport::class.java)
                if (report == null) {
                    Log.w("PassengerReportRepo", "  ⚠️ Failed to parse document ${doc.id}")
                    Log.w("PassengerReportRepo", "  Data: ${doc.data}")
                } else {
                    Log.d("PassengerReportRepo", "  ✅ Parsed: reportId=${report.reportId}, type=${report.reportType}")
                }
                report
            }

            Log.d("PassengerReportRepo", "✅ Successfully parsed ${reports.size} reports")
            Result.success(reports)
        } catch (e: Exception) {
            Log.e("PassengerReportRepo", "Failed to get passenger reports", e)
            Result.failure(e)
        }
    }

    override suspend fun getAllPassengerReports(): Result<List<PassengerReport>> {
        return try {
            val snapshot = passengerReportsCollection
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val reports = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PassengerReport::class.java)
            }

            Result.success(reports)
        } catch (e: Exception) {
            Log.e("PassengerReportRepo", "Failed to get all passenger reports", e)
            Result.failure(e)
        }
    }

    private val passengerReportsCollection = firestore.collection(COLLECTION_NAME)
}