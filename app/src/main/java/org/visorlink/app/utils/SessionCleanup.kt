package org.visorlink.app.utils

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Единая точка очистки локальных данных аккаунта при выходе / смене аккаунта (TODO §14.6).
 * Раньше logout не трогал SQLite-кэш переписки, Firestore persistence (CACHE_SIZE_UNLIMITED),
 * черновики и расшифрованные медиа — всё оставалось на диске после смены аккаунта.
 */
object SessionCleanup {

    suspend fun wipeAccountLocalData(
        context: Context,
        uid: String?,
        tfaManager: TfaManager? = null,
        contentKeyManager: ContentKeyManager? = null
    ) {
        // 1. SQLite-кэш: чаты, сообщения, профили, outbox
        try { ChatDataCache.clearAll(context) } catch (_: Exception) {}

        // 2. Firestore persistence-кэш (включён CACHE_SIZE_UNLIMITED)
        try { FirebaseFirestore.getInstance().clearPersistence().await() } catch (_: Exception) {}

        // 3. Черновики сообщений
        try {
            val drafts = DraftManager(context)
            // Префиксы известных мест хранения черновиков; clearDraft по известным chatId невозможен
            // (ключи произвольные) — очищаем всё хранилище черновиков целиком
            drafts.clearAllDrafts()
        } catch (_: Exception) {}

        // 4. Расшифрованные медиа и временные файлы (приватный контент в кэше)
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                val n = file.name
                if (n.startsWith("decrypted_") || n.startsWith("voice_") || n.startsWith("upload_") ||
                    n.startsWith("orig_") || n.startsWith("migr_")
                ) file.delete()
            }
        } catch (_: Exception) {}

        // 5. Кэш 2FA-сессии и локальные обёртки CK аккаунта
        tfaManager?.clearCache()
        if (!uid.isNullOrBlank()) {
            contentKeyManager?.wipeUid(uid)
        }
    }
}
