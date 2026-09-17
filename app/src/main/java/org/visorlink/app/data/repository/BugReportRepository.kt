package org.visorlink.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.visorlink.app.data.model.bugreport.BugReportRequest
import org.visorlink.app.data.model.bugreport.BugReportResponse

/**
 * Репозиторий отправки баг-репортов в систему FaultyWire через Cloud Functions.
 */
class BugReportRepository(private val functions: FirebaseFunctions) {

    private val gson = Gson()

    suspend fun sendReport(request: BugReportRequest): Result<BugReportResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonString = gson.toJson(request)
            @Suppress("UNCHECKED_CAST")
            val params = gson.fromJson(jsonString, Map::class.java) as Map<String, Any?>

            val result = functions
                .getHttpsCallable("submitBugReport")
                .call(params)
                .await()

            val resultMap = result.data as? Map<*, *>
            val reportId = resultMap?.get("reportId") as? String ?: ""
            val number = (resultMap?.get("number") as? Number)?.toLong() ?: 0L

            Result.success(BugReportResponse(true, reportId, number))
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                Result.failure(Exception("Превышен суточный лимит: не более 10 баг-репортов в сутки."))
            } else {
                Result.failure(Exception(e.message ?: "Ошибка отправки баг-репорта"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
