package by.iposdev.visorlink.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import by.iposdev.visorlink.data.model.TaskItem
import by.iposdev.visorlink.data.model.Topic
import by.iposdev.visorlink.data.model.toTopicOrNull
import by.iposdev.visorlink.data.model.toTaskItemOrNull

class TopicsRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val currentUid: String get() = auth.currentUser?.uid ?: ""

    /**
     * Подписка на список тем группы.
     * Закрепленная тема "Общий" (isGeneral == true) всегда отображается первой.
     */
    fun topicsFlow(chatId: String): Flow<List<Topic>> = callbackFlow {
        val query = db.collection("chats").document(chatId).collection("topics")
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                android.util.Log.w("TopicsRepo", "topicsFlow ordered query warning (falling back to get): ${error?.message}")
                db.collection("chats").document(chatId).collection("topics")
                    .get()
                    .addOnSuccessListener { fallbackSnap ->
                        val list = fallbackSnap.documents.mapNotNull { doc -> doc.toTopicOrNull() }
                        trySend(sortTopics(list))
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("TopicsRepo", "topicsFlow fallback get error", e)
                        trySend(emptyList())
                    }
                return@addSnapshotListener
            }

            val list = snapshot.documents.mapNotNull { doc -> doc.toTopicOrNull() }
            trySend(sortTopics(list))
        }

        awaitClose { registration.remove() }
    }

    private fun sortTopics(list: List<Topic>): List<Topic> {
        return list.sortedWith { t1, t2 ->
            if (t1.isGeneral) -1
            else if (t2.isGeneral) 1
            else {
                val time1 = t1.lastMessageAt?.toDate()?.time ?: t1.createdAt?.toDate()?.time ?: 0L
                val time2 = t2.lastMessageAt?.toDate()?.time ?: t2.createdAt?.toDate()?.time ?: 0L
                time2.compareTo(time1)
            }
        }
    }

    fun getTopicFlow(chatId: String, topicId: String): Flow<Topic?> = callbackFlow {
        val registration = db.collection("chats").document(chatId).collection("topics").document(topicId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toTopicOrNull())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Создает тему по умолчанию "#Общий", если таковой еще нет в группе.
     */
    suspend fun ensureGeneralTopic(chatId: String): Topic {
        val topicsRef = db.collection("chats").document(chatId).collection("topics")
        val generalQuery = topicsRef.whereEqualTo("isGeneral", true).limit(1).get().await()
        if (!generalQuery.isEmpty) {
            val doc = generalQuery.documents.first()
            return doc.toTopicOrNull() ?: Topic(id = doc.id, isGeneral = true)
        }

        // Проверяем документ с id "general"
        val generalDoc = topicsRef.document("general").get().await()
        if (generalDoc.exists()) {
            return generalDoc.toTopicOrNull() ?: Topic(id = "general", isGeneral = true)
        }

        // Создаем новую системную тему "#Общий"
        val newTopicData = mapOf(
            "title" to "Общий чат",
            "icon" to "💬",
            "color" to "#35C7E8",
            "type" to "chat",
            "isGeneral" to true,
            "isClosed" to false,
            "createdBy" to currentUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCount" to 0
        )
        topicsRef.document("general").set(newTopicData).await()
        return Topic(
            id = "general",
            title = "Общий чат",
            icon = "💬",
            color = "#35C7E8",
            type = "chat",
            isGeneral = true,
            createdBy = currentUid
        )
    }

    suspend fun createTopic(
        chatId: String,
        title: String,
        icon: String?,
        color: String?,
        type: String = "chat"
    ): Result<String> = runCatching {
        val docRef = db.collection("chats").document(chatId).collection("topics").document()
        val data = mapOf(
            "title" to title.trim().take(64),
            "icon" to (icon ?: if (type == "tasks") "📋" else "💬"),
            "color" to (color ?: if (type == "tasks") "#8C6BFF" else "#35C7E8"),
            "type" to type,
            "isGeneral" to false,
            "isClosed" to false,
            "createdBy" to currentUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCount" to 0
        )
        docRef.set(data).await()
        docRef.id
    }

    suspend fun updateTopic(
        chatId: String,
        topicId: String,
        updates: Map<String, Any?>
    ): Result<Unit> = runCatching {
        db.collection("chats").document(chatId).collection("topics").document(topicId)
            .update(updates).await()
    }

    suspend fun deleteTopic(
        chatId: String,
        topicId: String
    ): Result<Unit> = runCatching {
        // Удаляем сам документ темы
        db.collection("chats").document(chatId).collection("topics").document(topicId)
            .delete().await()
    }

    // ─── Задачи (Tasks) ──────────────────────────────────────────────────────────

    fun tasksFlow(chatId: String, topicId: String): Flow<List<TaskItem>> = callbackFlow {
        val query = db.collection("chats").document(chatId).collection("topics").document(topicId)
            .collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                // Fallback без индекса
                db.collection("chats").document(chatId).collection("topics").document(topicId)
                    .collection("tasks")
                    .get()
                    .addOnSuccessListener { fallbackSnap ->
                        val tasks = fallbackSnap.documents.mapNotNull { doc -> doc.toTaskItemOrNull() }
                        trySend(tasks)
                    }
                return@addSnapshotListener
            }

            val tasks = snapshot.documents.mapNotNull { doc -> doc.toTaskItemOrNull() }
            trySend(tasks)
        }

        awaitClose { registration.remove() }
    }

    suspend fun createTask(
        chatId: String,
        topicId: String,
        task: TaskItem
    ): Result<String> = runCatching {
        val docRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            .collection("tasks").document()

        val data = hashMapOf<String, Any?>(
            "title" to task.title.trim(),
            "description" to task.description.trim(),
            "status" to task.status,
            "priority" to task.priority,
            "assigneeId" to task.assigneeId,
            "assigneeName" to task.assigneeName,
            "assigneeAvatar" to task.assigneeAvatar,
            "dueDate" to task.dueDate,
            "createdBy" to currentUid,
            "createdByName" to task.createdByName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data).await()
        docRef.id
    }

    suspend fun updateTaskStatus(
        chatId: String,
        topicId: String,
        taskId: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        db.collection("chats").document(chatId).collection("topics").document(topicId)
            .collection("tasks").document(taskId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun updateTask(
        chatId: String,
        topicId: String,
        taskId: String,
        updates: Map<String, Any?>
    ): Result<Unit> = runCatching {
        val fullUpdates = updates + mapOf("updatedAt" to FieldValue.serverTimestamp())
        db.collection("chats").document(chatId).collection("topics").document(topicId)
            .collection("tasks").document(taskId)
            .update(fullUpdates).await()
    }

    suspend fun deleteTask(
        chatId: String,
        topicId: String,
        taskId: String
    ): Result<Unit> = runCatching {
        db.collection("chats").document(chatId).collection("topics").document(topicId)
            .collection("tasks").document(taskId)
            .delete().await()
    }
}
