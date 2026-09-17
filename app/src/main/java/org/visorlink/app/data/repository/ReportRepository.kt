package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ReportCategory(val key: String) {
    SPAM("SPAM"),
    VIOLENCE("VIOLENCE"),
    HARASSMENT("HARASSMENT"),
    ILLEGAL("ILLEGAL"),
    COPYRIGHT("COPYRIGHT")
}

data class ContentReport(
    val reportId: String = UUID.randomUUID().toString(),
    val reporterUid: String = "",
    val targetType: String = "message", // "message", "user", "post", "chat"
    val targetId: String = "",
    val targetSenderUid: String? = null,
    val category: String = ReportCategory.SPAM.key,
    val details: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

class ReportRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "ReportRepository"
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetSenderUid: String?,
        category: ReportCategory,
        details: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val reporterUid = auth.currentUser?.uid ?: "anonymous"
        val report = ContentReport(
            reporterUid = reporterUid,
            targetType = targetType,
            targetId = targetId,
            targetSenderUid = targetSenderUid,
            category = category.key,
            details = details.trim()
        )

        try {
            // 1. Попытка вызова Callable Cloud Function
            try {
                functions.getHttpsCallable("submitReport").call(
                    mapOf(
                        "reportId" to report.reportId,
                        "reporterUid" to report.reporterUid,
                        "targetType" to report.targetType,
                        "targetId" to report.targetId,
                        "targetSenderUid" to report.targetSenderUid,
                        "category" to report.category,
                        "details" to report.details,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "submitReport Cloud Function call failed or not deployed: ${e.message}")
            }

            // 2. Гарантированная запись в коллекцию /reports Firestore
            firestore.collection("reports")
                .document(report.reportId)
                .set(report)
                .await()

            Log.i(TAG, "Report ${report.reportId} successfully submitted for target $targetId ($targetType)")
            Result.success(report.reportId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit report", e)
            Result.failure(e)
        }
    }
}
