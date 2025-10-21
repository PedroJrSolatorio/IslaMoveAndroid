package com.rj.islamove.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.rj.islamove.data.models.SupportComment
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportCommentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_NAME = "support_comments"
    }

    suspend fun submitComment(comment: SupportComment): Result<String> {
        return try {
            val commentWithId = comment.copy(
                id = firestore.collection(COLLECTION_NAME).document().id
            )

            firestore.collection(COLLECTION_NAME)
                .document(commentWithId.id)
                .set(commentWithId)
                .await()

            Result.success(commentWithId.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserComments(userId: String): Result<List<SupportComment>> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val comments = querySnapshot.documents.mapNotNull { document ->
                document.toObject(SupportComment::class.java)
            }

            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}