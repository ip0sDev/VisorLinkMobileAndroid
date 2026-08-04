package by.iposdev.visorlink.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import by.iposdev.visorlink.data.model.FeedItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class FeedRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) {

    fun getFeedFlow(interestWeights: Map<String, Double>): Flow<List<FeedItem>> = callbackFlow {
        // Простая реализация: берем последние посты.
        // В вебе используется более сложная логика ранжирования на основе interestWeights.
        // Здесь мы можем отфильтровать или отсортировать на стороне клиента для начала.
        val reg = db.collection("discover_feed")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(FeedItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Ранжирование по интересам
                val ranked = if (interestWeights.isNotEmpty()) {
                    items.sortedByDescending { item ->
                        var score = 1.0
                        item.tags.forEach { tag ->
                            score += interestWeights[tag] ?: 0.0
                        }
                        score
                    }
                } else {
                    items
                }

                trySend(ranked)
            }
        awaitClose { reg.remove() }
    }

    suspend fun incrementView(itemId: String) = withContext(Dispatchers.IO) {
        try {
            db.collection("discover_feed").document(itemId)
                .update("views_count", FieldValue.increment(1))
        } catch (_: Exception) {}
    }

    suspend fun toggleLike(chatId: String, messageId: String, isLiked: Boolean) = withContext(Dispatchers.IO) {
        try {
            // В вебе используется вызов Cloud Function 'toggleLike'
            // exports.toggleLike = onCall(...) { const { messageId, isLiked } = request.data; ... }
            functions.getHttpsCallable("toggleLike")
                .call(mapOf(
                    "chatId" to chatId,
                    "messageId" to messageId,
                    "isLiked" to !isLiked // isNowLiked в вебе передается как результат действия
                )).await()
        } catch (_: Exception) {
            // Если функция упала, лайк не засчитается на бэкенде
        }
    }
}