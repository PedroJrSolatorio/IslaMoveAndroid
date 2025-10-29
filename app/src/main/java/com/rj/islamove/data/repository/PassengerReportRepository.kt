package com.rj.islamove.data.repository

import com.rj.islamove.data.models.PassengerReport

interface PassengerReportRepository {
    suspend fun submitPassengerReport(report: PassengerReport): Result<Unit>
    suspend fun getPassengerReports(passengerId: String): Result<List<PassengerReport>>
    suspend fun getAllPassengerReports(): Result<List<PassengerReport>>
}