package by.iposdev.visorlink.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.utils.ChatDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class StickerPackRepository(
    private val auth: FirebaseAuth,
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val currentUid get() = auth.currentUser?.uid ?: ""

    private val _packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())

    fun observeUserPacks(): Flow<List<StickerPack>> = _packsFlow.asStateFlow()

    suspend fun refreshPacks() = withContext(Dispatchers.IO) {
        try {
            ChatDataCache.checkAndPurgeLegacyStickerCache(context)
            val cached = ChatDataCache.loadStickerPacks(context, currentUid)
            if (cached.isNotEmpty() && _packsFlow.value.isEmpty()) {
                _packsFlow.value = cached
            }

            val packs = fetchOfficialStickerPacks()
            if (packs.isNotEmpty()) {
                _packsFlow.value = packs
                ChatDataCache.saveStickerPacks(context, currentUid, packs)
            }
        } catch (e: Exception) {
            Log.e("StickerRepo", "Failed to fetch official packs from Firestore", e)
        }
    }

    suspend fun fetchOfficialStickerPacks(): List<StickerPack> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("stickerPacks")
                .whereEqualTo("isOfficial", true)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val name = doc.getString("name") ?: "Sticker Pack"
                val emoji = doc.getString("emoji") ?: "📦"
                val author = doc.getString("author") ?: "VisorLink Official"
                val authorId = doc.getString("authorId") ?: "official"
                val authorName = doc.getString("authorName") ?: author
                val isOfficial = doc.getBoolean("isOfficial") ?: true
                val stickersRaw = doc.get("stickers") as? List<Map<String, Any>> ?: emptyList()

                val stickers = stickersRaw.mapIndexed { index, sMap ->
                    StickerItem(
                        id = sMap["id"] as? String ?: UUID.randomUUID().toString(),
                        emoji = sMap["emoji"] as? String ?: "🎭",
                        url = sMap["url"] as? String ?: "",
                        storagePath = sMap["storagePath"] as? String ?: "",
                        sortOrder = (sMap["sortOrder"] as? Number)?.toInt() ?: index
                    )
                }.sortedBy { it.sortOrder }

                StickerPack(
                    id = id,
                    name = name,
                    emoji = emoji,
                    author = author,
                    authorId = authorId,
                    authorName = authorName,
                    isOfficial = isOfficial,
                    stickerCount = stickers.size,
                    stickers = stickers
                )
            }
        } catch (e: Exception) {
            Log.e("StickerRepo", "Error querying official sticker packs", e)
            emptyList()
        }
    }

    suspend fun getPackById(packId: String): StickerPack? {
        return _packsFlow.value.find { it.id == packId }
    }

    suspend fun hasPack(packId: String): Boolean {
        return _packsFlow.value.any { it.id == packId }
    }

    suspend fun fetchPackDetails(packId: String): StickerPack? = withContext(Dispatchers.IO) {
        val local = _packsFlow.value.find { it.id == packId }
        if (local != null) return@withContext local

        try {
            val doc = db.collection("stickerPacks").document(packId).get().await()
            if (!doc.exists()) return@withContext null
            val id = doc.id
            val name = doc.getString("name") ?: "Sticker Pack"
            val emoji = doc.getString("emoji") ?: "📦"
            val author = doc.getString("author") ?: "VisorLink Official"
            val authorId = doc.getString("authorId") ?: "official"
            val authorName = doc.getString("authorName") ?: author
            val isOfficial = doc.getBoolean("isOfficial") ?: true
            val stickersRaw = doc.get("stickers") as? List<Map<String, Any>> ?: emptyList()

            val stickers = stickersRaw.mapIndexed { index, sMap ->
                StickerItem(
                    id = sMap["id"] as? String ?: UUID.randomUUID().toString(),
                    emoji = sMap["emoji"] as? String ?: "🎭",
                    url = sMap["url"] as? String ?: "",
                    storagePath = sMap["storagePath"] as? String ?: "",
                    sortOrder = (sMap["sortOrder"] as? Number)?.toInt() ?: index
                )
            }.sortedBy { it.sortOrder }

            StickerPack(
                id = id,
                name = name,
                emoji = emoji,
                author = author,
                authorId = authorId,
                authorName = authorName,
                isOfficial = isOfficial,
                stickerCount = stickers.size,
                stickers = stickers
            )
        } catch (e: Exception) {
            Log.e("StickerRepo", "Failed to fetch pack details for $packId", e)
            null
        }
    }

    // Deprecated legacy stubs to preserve interface compatibility without hitting legacy CDN
    suspend fun createPack(name: String, emoji: String): String = withContext(Dispatchers.IO) {
        throw UnsupportedOperationException("Custom sticker pack creation has been decommissioned.")
    }

    suspend fun deletePack(packId: String, isOwner: Boolean) = withContext(Dispatchers.IO) {
        // No-op for official packs
    }

    suspend fun addPackToUser(packId: String): String = withContext(Dispatchers.IO) {
        "Official pack"
    }

    suspend fun uploadSticker(packId: String, uri: Uri, emoji: String): StickerItem = withContext(Dispatchers.IO) {
        throw UnsupportedOperationException("Custom sticker upload has been decommissioned.")
    }

    suspend fun deleteStickerFromPack(packId: String, sticker: StickerItem) = withContext(Dispatchers.IO) {
        // No-op
    }
}