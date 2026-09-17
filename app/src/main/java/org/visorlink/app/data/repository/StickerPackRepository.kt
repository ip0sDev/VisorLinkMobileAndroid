package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.utils.ChatDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class StickerPackRepository(
    private val auth: FirebaseAuth,
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "StickerPackRepository"
        const val PREF_STICKER_CACHE_VERSION = "official_curated_v3"
        private const val PREFS_NAME = "sticker_prefs"
        private const val KEY_CACHE_VERSION = "sticker_cache_version"
    }

    private val currentUid get() = auth.currentUser?.uid ?: ""
    private val _packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())

    fun observeUserPacks(): Flow<List<StickerPack>> = _packsFlow.asStateFlow()

    private suspend fun purgeLegacyCacheIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getString(KEY_CACHE_VERSION, null)
        if (savedVersion != PREF_STICKER_CACHE_VERSION) {
            ChatDataCache.clearAllStickerPacks(context)
            prefs.edit().putString(KEY_CACHE_VERSION, PREF_STICKER_CACHE_VERSION).apply()
            Log.i(TAG, "Legacy sticker cache purged for version $PREF_STICKER_CACHE_VERSION")
        }
    }

    suspend fun refreshPacks() = withContext(Dispatchers.IO) {
        try {
            purgeLegacyCacheIfNeeded()

            val uid = currentUid
            if (uid.isNotEmpty()) {
                val cached = ChatDataCache.loadStickerPacks(context, uid)
                if (cached.isNotEmpty() && _packsFlow.value.isEmpty()) {
                    _packsFlow.value = cached
                }
            }

            // Запрашиваем только официальные курируемые стикерпаки из Firestore
            val snapshot = firestore.collection("stickerPacks")
                .whereEqualTo("isOfficial", true)
                .get()
                .await()

            val packs = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val name = doc.getString("name") ?: "Sticker Pack"
                    val emoji = doc.getString("emoji") ?: "✨"
                    val authorId = doc.getString("authorId") ?: "official"
                    val authorName = doc.getString("authorName") ?: doc.getString("author") ?: "VisorLink Official"

                    @Suppress("UNCHECKED_CAST")
                    val rawStickers = doc.get("stickers") as? List<Map<String, Any?>> ?: emptyList()
                    val stickers = rawStickers.mapNotNull { map ->
                        val sId = (map["id"] as? String) ?: return@mapNotNull null
                        val url = (map["url"] as? String) ?: ""
                        // Отсекаем старый CDN и относительные пути /f/
                        if (url.contains("api.visorlink.org") || (url.contains("/f/") && !url.contains("firebasestorage") && !url.contains("googleusercontent.com"))) {
                            return@mapNotNull null
                        }
                        StickerItem(
                            id = sId,
                            url = url,
                            emoji = (map["emoji"] as? String) ?: "🎭",
                            storagePath = (map["storagePath"] as? String) ?: "",
                            sortOrder = ((map["sortOrder"] as? Number)?.toInt()) ?: 0
                        )
                    }.sortedBy { it.sortOrder }

                    StickerPack(
                        id = id,
                        name = name,
                        emoji = emoji,
                        authorId = authorId,
                        authorName = authorName,
                        stickerCount = stickers.size,
                        stickers = stickers
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing sticker pack doc ${doc.id}", e)
                    null
                }
            }

            _packsFlow.value = packs
            if (uid.isNotEmpty()) {
                ChatDataCache.saveStickerPacks(context, uid, packs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch official sticker packs", e)
        }
    }

    suspend fun getPackById(packId: String): StickerPack? {
        val inMemory = _packsFlow.value.find { it.id == packId }
        if (inMemory != null) return inMemory
        return fetchPackDetails(packId)
    }

    suspend fun hasPack(packId: String): Boolean {
        return _packsFlow.value.any { it.id == packId }
    }

    suspend fun fetchPackDetails(packId: String): StickerPack? = withContext(Dispatchers.IO) {
        val local = _packsFlow.value.find { it.id == packId }
        if (local != null) return@withContext local

        try {
            val doc = firestore.collection("stickerPacks").document(packId).get().await()
            if (!doc.exists()) return@withContext null

            val name = doc.getString("name") ?: "Sticker Pack"
            val emoji = doc.getString("emoji") ?: "✨"
            val authorId = doc.getString("authorId") ?: "official"
            val authorName = doc.getString("authorName") ?: doc.getString("author") ?: "VisorLink Official"

            @Suppress("UNCHECKED_CAST")
            val rawStickers = doc.get("stickers") as? List<Map<String, Any?>> ?: emptyList()
            val stickers = rawStickers.mapNotNull { map ->
                val sId = (map["id"] as? String) ?: return@mapNotNull null
                val url = (map["url"] as? String) ?: ""
                if (url.contains("api.visorlink.org") || (url.contains("/f/") && !url.contains("firebasestorage") && !url.contains("googleusercontent.com"))) {
                    return@mapNotNull null
                }
                StickerItem(
                    id = sId,
                    url = url,
                    emoji = (map["emoji"] as? String) ?: "🎭",
                    storagePath = (map["storagePath"] as? String) ?: "",
                    sortOrder = ((map["sortOrder"] as? Number)?.toInt()) ?: 0
                )
            }.sortedBy { it.sortOrder }

            StickerPack(
                id = doc.id,
                name = name,
                emoji = emoji,
                authorId = authorId,
                authorName = authorName,
                stickerCount = stickers.size,
                stickers = stickers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch pack details for $packId", e)
            null
        }
    }

    suspend fun addPackToUser(packId: String): String = withContext(Dispatchers.IO) {
        refreshPacks()
        "Pack Added"
    }

    suspend fun deletePack(packId: String, isOwner: Boolean) = withContext(Dispatchers.IO) {
        refreshPacks()
    }
}