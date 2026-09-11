package by.iposdev.visorlink.data.repository

import android.content.Context
import by.iposdev.visorlink.data.model.AlbumImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.Timestamp
import by.iposdev.visorlink.data.model.FeedItem
import by.iposdev.visorlink.data.remote.FirestoreCollections
import by.iposdev.visorlink.data.remote.chat.FeedItemDto
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import by.iposdev.visorlink.data.repository.FlagsRepository
import by.iposdev.visorlink.utils.ChatDataCache
import by.iposdev.visorlink.utils.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date

class FeedRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val api: VisorLinkApi,
    private val flagsRepository: FlagsRepository
) {
    private val backendPrefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    private fun isFeedBackendEnabled(): Boolean {
        val serverV2 = flagsRepository.isBackendV2Enabled()
        val serverFlag = flagsRepository.flags.value.isEnabled("test_backend_enabled")
        val userSetting = backendPrefs.getBoolean("use_backend_feed", false)
        return serverV2 || (serverFlag && userSetting)
    }

    private fun isFirestoreDisabled(): Boolean {
        return flagsRepository.isBackendV2Enabled() || (flagsRepository.flags.value.isEnabled("test_backend_enabled") && 
                backendPrefs.getBoolean("disable_firestore_completely", false))
    }


    private fun FeedItemDto.toDomain(): FeedItem {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val author = channelName ?: authorName ?: senderUsername ?: "Канал"
        val avatar = channelAvatar ?: authorAvatarUrl
        val finalLikers = if (likedByMe) {
            val list = likers?.toMutableList() ?: mutableListOf()
            if (currentUid.isNotEmpty() && !list.contains(currentUid)) list.add(currentUid)
            list
        } else (likers ?: emptyList())

        val effectiveCdnId = cdnMediaId
            ?: url?.substringAfter("/f/", "")?.substringBefore("?")?.takeIf { it.isNotEmpty() && !it.contains("/") }
            ?: url?.substringAfter("/p/", "")?.substringBefore("?")?.takeIf { it.isNotEmpty() && !it.contains("/") }

        return FeedItem(
            id = id,
            chatId = chatId,
            messageId = messageId,
            type = type,
            title = title ?: channelName,
            text = text,
            caption = caption,
            url = url,
            cdnMediaId = effectiveCdnId,
            images = images?.map { 
                val imgCdnId = it.cdnMediaId
                    ?: it.url?.substringAfter("/f/", "")?.substringBefore("?")?.takeIf { u -> u.isNotEmpty() && !u.contains("/") }
                    ?: it.url?.substringAfter("/p/", "")?.substringBefore("?")?.takeIf { u -> u.isNotEmpty() && !u.contains("/") }
                AlbumImage(url = it.url, cdnMediaId = imgCdnId, fileName = it.fileName, spoiler = it.spoiler) 
            },
            authorName = author,
            author_name = author,
            authorAvatarUrl = avatar,
            author_avatar_url = avatar,
            likeCount = likeCount,
            likes_count = likeCount,
            views_count = viewsCount,
            comments_count = commentsCount,
            likers = finalLikers,
            liked_uids = finalLikers,
            tags = if (tags.isNotEmpty()) tags else listOfNotNull(channelTag),
            createdAt = Timestamp(Date(createdAt))
        )
    }

    suspend fun getFeed(interestWeights: Map<String, Double>): List<FeedItem> = withContext(Dispatchers.IO) {
        if (isFeedBackendEnabled()) {
            try {
                val net = api.getFeed().map { it.toDomain() }
                return@withContext if (interestWeights.isNotEmpty()) {
                    net.sortedByDescending { item ->
                        var score = 1.0
                        item.tags.forEach { tag -> score += interestWeights[tag] ?: 0.0 }
                        score
                    }
                } else net
            } catch (e: Exception) {
                return@withContext emptyList()
            }
        }
        return@withContext emptyList()
    }

    fun getFeedFlow(interestWeights: Map<String, Double>): Flow<List<FeedItem>> = channelFlow {
        if (isFeedBackendEnabled()) {
            try {
                val net = api.getFeed().map { it.toDomain() }
                val ranked = if (interestWeights.isNotEmpty()) {
                    net.sortedByDescending { item ->
                        var score = 1.0
                        item.tags.forEach { tag -> score += interestWeights[tag] ?: 0.0 }
                        score
                    }
                } else net
                send(ranked)
            } catch (e: Exception) {
                send(emptyList())
            }
            awaitClose { }
            return@channelFlow
        }

        if (isFirestoreDisabled()) {
            send(emptyList())
            awaitClose { }
            return@channelFlow
        }

        val reg = db.collection(FirestoreCollections.DISCOVER_FEED)
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
        if (isFeedBackendEnabled()) return@withContext // Backend should handle views automatically or via another endpoint
        try {
            db.collection(FirestoreCollections.DISCOVER_FEED).document(itemId)
                .update("views_count", FieldValue.increment(1))
        } catch (_: Exception) {}
    }

    suspend fun toggleLike(chatId: String, messageId: String, isLiked: Boolean) = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
        if (!isLiked) {
            ChatDataCache.saveLike(context, uid, messageId)
        } else {
            ChatDataCache.removeLike(context, uid, messageId)
        }

        if (isFeedBackendEnabled()) {
            try {
                api.toggleFeedLike(messageId)
            } catch (_: Exception) {}
            return@withContext
        }

        val data = JSONObject().apply {
            put("messageId", messageId)
            put("isLiked", !isLiked)
        }
        ChatDataCache.addToOutbox(context, chatId, "like", data)
    }
}

