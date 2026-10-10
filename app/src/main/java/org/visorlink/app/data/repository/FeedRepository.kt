package org.visorlink.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.CuratedChannel
import org.visorlink.app.data.model.FeedPost
import org.visorlink.app.data.model.FeedPosts
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.toChatOrNull
import org.visorlink.app.data.remote.FirestoreCollections

/** Подписки: все мои каналы (для «Подписан» в каталоге) и посты самых свежих из них. */
data class FeedSubscriptions(
    val channels: List<Chat>,
    val posts: List<FeedPost>,
)

/**
 * Лента «Каналы», как в вебе (`FeedWindow.jsx`): каталог «Рекомендуемые» и посты каналов, на
 * которые я подписан. Коллекцию `discover_feed` не читает — посты, лайки и просмотры живут в
 * самих сообщениях каналов, лайк и просмотр меняет только сервер.
 */
class FeedRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    suspend fun curatedChannels(): List<CuratedChannel> {
        val res = functions.getHttpsCallable("getCuratedChannels").call().await()
        val list = (res.data as? Map<*, *>)?.get("channels") as? List<*> ?: return emptyList()
        return list.mapNotNull { (it as? Map<*, *>)?.let(CuratedChannel::fromMap) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun subscriptionsFlow(uid: String): Flow<FeedSubscriptions> = channelFlow {
        val channels = MutableStateFlow<List<Chat>?>(null)
        launch { myChannelsFlow(uid).collect { channels.value = it } }

        // Слушатели сообщений привязаны к набору каналов, а не к их данным: новый пост меняет
        // lastMessageAt канала, и пересоздавать из-за этого все слушатели незачем
        val messages = channels.filterNotNull()
            .map { all -> FeedPosts.channelsToFollow(all).map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyMap())
                else combine(ids.map { id -> channelMessagesFlow(id, uid).map { id to it } }) { it.toMap() }
            }

        combine(channels.filterNotNull(), messages) { all, byChannel ->
            FeedSubscriptions(channels = all, posts = FeedPosts.assemble(FeedPosts.channelsToFollow(all), byChannel))
        }.collect { send(it) }
    }

    /** Каналы, где я участник. Офлайн отвечает кэш Firestore. */
    private fun myChannelsFlow(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = db.collection(FirestoreCollections.CHATS)
            .whereArrayContains("memberIds", uid)
            .whereEqualTo("type", "channel")
            .addSnapshotListener { snap, err ->
                when {
                    snap != null -> trySend(snap.documents.mapNotNull { it.toChatOrNull() })
                    err != null -> close(err)
                }
            }
        awaitClose { reg.remove() }
    }

    /** Последние посты канала, уже без удалённых, скрытых и неопубликованных. */
    private fun channelMessagesFlow(chatId: String, uid: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection(FirestoreCollections.CHATS).document(chatId)
            .collection(FirestoreCollections.MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FeedPosts.PER_CHANNEL.toLong())
            .addSnapshotListener { snap, _ ->
                // Ошибка одного канала (бан, удалён) не должна останавливать всю ленту
                val list = snap?.documents.orEmpty().mapNotNull { it.toMessageOrNull() }
                trySend(list.filter { FeedPosts.isVisible(it, uid) })
            }
        awaitClose { reg.remove() }
    }

    /**
     * Ставит ([liked] = true) или снимает 👍. Сервер идемпотентен: повтор не добавит второй
     * голос, снятие несуществующего не уведёт счётчик в минус. Возвращает итоговое состояние.
     */
    suspend fun setLike(chatId: String, messageId: String, liked: Boolean): Boolean {
        val res = functions.getHttpsCallable("toggleLike")
            .call(mapOf("chatId" to chatId, "messageId" to messageId, "isLiked" to liked))
            .await()
        return (res.data as? Map<*, *>)?.get("liked") as? Boolean ?: liked
    }

    /** Отмечает посты канала просмотренными (сервер считает каждого человека один раз). */
    suspend fun recordViews(chatId: String, messageIds: List<String>) {
        messageIds.distinct().chunked(FeedPosts.MAX_VIEW_BATCH).forEach { batch ->
            functions.getHttpsCallable("recordPostViews")
                .call(mapOf("chatId" to chatId, "messageIds" to batch))
                .await()
        }
    }

    private fun DocumentSnapshot.toMessageOrNull(): Message? = try {
        toObject(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = this.id)
    } catch (_: Exception) {
        null
    }
}
