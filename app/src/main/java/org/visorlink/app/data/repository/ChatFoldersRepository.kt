package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.visorlink.app.data.model.ChatFolder
import org.visorlink.app.data.model.ChatFolderRules

/**
 * Репозиторий папок чатов VisorLink PRO.
 * Конфигурация папок хранится в `users/{uid}/chatFolders/config`.
 */
class ChatFoldersRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val tag = "ChatFoldersRepository"

    private fun configRef(uid: String) =
        db.collection("users").document(uid).collection("chatFolders").document("config")

    /**
     * Поток списка сохранённых папок пользователя.
     */
    fun foldersFlow(uid: String): Flow<List<ChatFolder>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = configRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(tag, "Ошибка чтения папок чатов: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }

            val rawList = snapshot?.get("folders") as? List<*> ?: emptyList<Any>()
            val folders = rawList
                .mapNotNull { it as? Map<*, *> }
                .map { ChatFolder.normalize(it) }
                .filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
                .take(ChatFolderRules.MAX_FOLDERS)

            trySend(folders)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Сохраняет полный список папок.
     * Если список пуст, документ удаляется (удаление разрешено и без PRO).
     */
    suspend fun save(uid: String, folders: List<ChatFolder>) {
        if (uid.isEmpty()) return
        val clean = folders
            .map { ChatFolder.normalize(it.toMap()) }
            .filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
            .take(ChatFolderRules.MAX_FOLDERS)

        val docRef = configRef(uid)
        if (clean.isEmpty()) {
            docRef.delete().await()
        } else {
            val data = mapOf(
                "folders" to clean.map { it.toMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data).await()
        }
    }
}
