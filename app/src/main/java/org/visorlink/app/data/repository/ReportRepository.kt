package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.visorlink.app.BuildConfig

enum class ReportCategory(val key: String) {
    SPAM("spam"),
    VIOLENCE("violence"),
    HARASSMENT("harassment"),
    ILLEGAL("illegal"),
    COPYRIGHT("copyright")
}

/**
 * Жалобы на контент — callable `submitAbuseReport` (как `ReportModal.jsx` в вебе): сервер пишет
 * `abuse_reports`, не принимает повторную жалобу того же человека и после трёх разных
 * жалоб на сообщение скрывает его (`is_hidden`), для чего ему нужен [chatId].
 *
 * Раньше здесь вызывалась несуществующая функция `submitReport`, а затем шла запись в
 * `/reports`, которую правила Firestore запрещают, — жалобы с Android не доходили никуда.
 */
class ReportRepository(
    private val functions: FirebaseFunctions,
) {
    companion object {
        private const val TAG = "ReportRepository"
    }

    /**
     * @param targetType `message`, `post` или `user`.
     * @param chatId чат сообщения или канал поста; для жалобы на пользователя — `null`.
     */
    suspend fun submitReport(
        targetType: String,
        targetId: String,
        chatId: String?,
        category: ReportCategory,
        details: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            functions.getHttpsCallable("submitAbuseReport").call(
                mapOf(
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "chatId" to chatId,
                    "reason" to category.key,
                    "comment" to details.trim(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "submitAbuseReport failed for $targetType $targetId", e)
            Result.failure(e)
        }
    }
}
