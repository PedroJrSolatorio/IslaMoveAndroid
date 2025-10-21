package com.rj.islamove.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun createSupportTicket(
        ticketId: String,
        description: String,
        timestamp: Long
    ) {
        val currentUser = auth.currentUser
        requireNotNull(currentUser) { "User must be logged in to create support ticket" }

        val supportTicket = mapOf(
            "ticketId" to ticketId,
            "userId" to currentUser.uid,
            "userEmail" to currentUser.email,
            "description" to description,
            "timestamp" to timestamp,
            "status" to "open",
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("supportTickets")
            .document(ticketId)
            .set(supportTicket)
            .await()
    }
}