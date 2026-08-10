package by.iposdev.visorlink.data.repository

import by.iposdev.visorlink.data.model.FeedItem
import by.iposdev.visorlink.utils.ChatDataCache
import by.iposdev.visorlink.utils.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class FeedRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context,
    private val networkMonitor: NetworkMonitor
) {

    fun getFeedFlow(interestWeights: Map<String, Double>): Flow<List<FeedItem>> = callbackFlow {
        val reg = db.collection("discover_feed")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(FeedItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                launch(Dispatchers.IO) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val updatedItems = items.map { item ->
                        val localLiked = ChatDataCache.isLiked(context, uid, item.messageId ?: item.id)
                        if (localLiked && !item.displayLikedUids.contains(uid)) {
                            item.copy(likers = item.likers + uid)
                        } else item
                    }

                    // Ранжирование по интересам
                    val ranked = if (interestWeights.isNotEmpty()) {
                        updatedItems.sortedByDescending { item ->
                            var score = 1.0
                            item.tags.forEach { tag ->
                                score += interestWeights[tag] ?: 0.0
                            }
                            score
                        }
                    } else {
                        updatedItems
                    }

                    trySend(ranked)
                }
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
        val data = JSONObject().apply {
            put("messageId", messageId)
            put("isLiked", !isLiked)
        }
        ChatDataCache.addToOutbox(context, chatId, "like", data)
        // Local state update for immediate feedback
        if (!isLiked) {
            ChatDataCache.saveLike(context, "me", messageId) // "me" should be real UID
        } else {
            ChatDataCache.removeLike(context, "me", messageId)
        }
    }
}