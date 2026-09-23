package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.utils.ChatDataCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.google.firebase.firestore.Query

class StickerPackRepository(
    private val auth: FirebaseAuth,
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "StickerPackRepository"
        const val PREF_STICKER_CACHE_VERSION = "official_curated_v5"
        private const val PREFS_NAME = "sticker_prefs"
        private const val KEY_CACHE_VERSION = "sticker_cache_version"
    }

    private val currentUid get() = auth.currentUser?.uid ?: ""
    private val _storePacksFlow = MutableStateFlow<List<StickerPack>>(emptyList())
    private val _userPacksFlow = MutableStateFlow<List<StickerPack>>(emptyList())
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                purgeLegacyCacheIfNeeded()
                val uid = currentUid.ifBlank { "global" }
                val cached = ChatDataCache.loadStickerPacks(context, uid)
                if (cached.isNotEmpty()) {
                    _storePacksFlow.value = cached.filter { it.isOfficial }
                    val userPackIds = ChatDataCache.loadProfile(context, uid)?.stickerPackIds ?: emptyList()
                    _userPacksFlow.value = cached.filter { it.id in userPackIds || (it.authorId == uid && !it.isOfficial) }.toMutableList()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to preload stickers from cache", e)
            }
        }
        startListening()
    }

    private fun startListening() {
        val query = firestore.collection("stickerPacks").whereEqualTo("isOfficial", true)
        listenerRegistration?.remove()
        listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Sticker pack listen failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    processSnapshot(snapshot)
                }
            }
        }
    }

    private suspend fun processSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) = withContext(Dispatchers.IO) {
        val uid = currentUid.ifBlank { "global" }
        try {
            val packs = snapshot.documents.map { doc ->
                async {
                    try {
                        val id = doc.id
                        val name = doc.getString("name") ?: "Sticker Pack"
                        val emoji = doc.getString("emoji") ?: "✨"
                        val authorId = doc.getString("authorId") ?: "official"
                        val authorName = doc.getString("authorName") ?: doc.getString("author") ?: "VisorLink Official"
                        val isOfficial = doc.getBoolean("isOfficial") ?: true

                        val stickersSnapshot = try {
                            firestore.collection("stickerPacks").document(id).collection("stickers")
                                .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
                        } catch (e: Exception) {
                            firestore.collection("stickerPacks").document(id).collection("stickers").get().await()
                        }

                        val stickers = stickersSnapshot.documents.mapNotNull { sDoc ->
                            val url = sDoc.getString("url") ?: return@mapNotNull null
                            if (url.contains("api.visorlink.org") || (url.contains("/f/") && !url.contains("firebasestorage"))) {
                                return@mapNotNull null
                            }
                            StickerItem(
                                id = sDoc.id,
                                url = url,
                                emoji = sDoc.getString("emoji") ?: "🎭",
                                storagePath = sDoc.getString("storagePath") ?: "",
                                sortOrder = sDoc.getLong("sortOrder")?.toInt() ?: 0
                            )
                        }.sortedBy { it.sortOrder }

                        if (stickers.isEmpty()) return@async null

                        StickerPack(
                            id = id,
                            name = name,
                            emoji = emoji,
                            authorId = authorId,
                            authorName = authorName,
                            stickerCount = stickers.size,
                            isOfficial = isOfficial,
                            stickers = stickers
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing sticker pack doc ${doc.id}", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()

            val profile = ChatDataCache.loadProfile(context, uid)
            val userPackIds = profile?.stickerPackIds ?: emptyList()

            _storePacksFlow.value = packs.filter { it.isOfficial }
            _userPacksFlow.value = packs.filter { it.id in userPackIds || (it.authorId == currentUid && !it.isOfficial) }.toMutableList()

            ChatDataCache.saveStickerPacks(context, uid, packs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process snapshot", e)
        }
    }

    fun observeStorePacks(): Flow<List<StickerPack>> = _storePacksFlow.asStateFlow()
    fun observeUserPacks(): Flow<List<StickerPack>> = _userPacksFlow.asStateFlow()

    private suspend fun purgeLegacyCacheIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getString(KEY_CACHE_VERSION, null)
        if (savedVersion != PREF_STICKER_CACHE_VERSION) {
            ChatDataCache.clearAllStickerPacks(context)
            prefs.edit().putString(KEY_CACHE_VERSION, PREF_STICKER_CACHE_VERSION).apply()
            Log.i(TAG, "Legacy sticker cache purged for version $PREF_STICKER_CACHE_VERSION")
        }
    }

    suspend fun refreshPacks(forceServer: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            purgeLegacyCacheIfNeeded()
            val query = firestore.collection("stickerPacks").whereEqualTo("isOfficial", true)
            val snapshot = try {
                if (forceServer) {
                    query.get(Source.SERVER).await()
                } else {
                    query.get().await()
                }
            } catch (serverEx: Exception) {
                Log.w(TAG, "Server sticker query failed, fallback to default source: ${serverEx.message}")
                query.get().await()
            }
            processSnapshot(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch official sticker packs", e)
        }
    }

    suspend fun getPackById(packId: String): StickerPack? {
        val inMemory = _storePacksFlow.value.find { it.id == packId } ?: _userPacksFlow.value.find { it.id == packId }
        if (inMemory != null) return inMemory
        return fetchPackDetails(packId)
    }

    suspend fun hasPack(packId: String): Boolean {
        return _userPacksFlow.value.any { it.id == packId }
    }

    suspend fun fetchPackDetails(packId: String): StickerPack? = withContext(Dispatchers.IO) {
        val local = _storePacksFlow.value.find { it.id == packId } ?: _userPacksFlow.value.find { it.id == packId }
        if (local != null) return@withContext local

        try {
            val doc = firestore.collection("stickerPacks").document(packId).get().await()
            if (!doc.exists()) return@withContext null

            val name = doc.getString("name") ?: "Sticker Pack"
            val emoji = doc.getString("emoji") ?: "✨"
            val authorId = doc.getString("authorId") ?: "official"
            val authorName = doc.getString("authorName") ?: doc.getString("author") ?: "VisorLink Official"
            val isOfficial = doc.getBoolean("isOfficial") ?: true

            val stickersSnapshot = try {
                firestore.collection("stickerPacks").document(packId).collection("stickers")
                    .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
            } catch (e: Exception) {
                firestore.collection("stickerPacks").document(packId).collection("stickers").get().await()
            }

            val stickers = stickersSnapshot.documents.mapNotNull { sDoc ->
                val url = sDoc.getString("url") ?: return@mapNotNull null
                if (url.contains("api.visorlink.org") || (url.contains("/f/") && !url.contains("firebasestorage"))) {
                    return@mapNotNull null
                }
                StickerItem(
                    id = sDoc.id,
                    url = url,
                    emoji = sDoc.getString("emoji") ?: "🎭",
                    storagePath = sDoc.getString("storagePath") ?: "",
                    sortOrder = sDoc.getLong("sortOrder")?.toInt() ?: 0
                )
            }.sortedBy { it.sortOrder }

            if (stickers.isEmpty()) return@withContext null

            StickerPack(
                id = doc.id,
                name = name,
                emoji = emoji,
                authorId = authorId,
                authorName = authorName,
                stickerCount = stickers.size,
                isOfficial = isOfficial,
                stickers = stickers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch pack details for $packId", e)
            null
        }
    }

    suspend fun addPackToUser(packId: String): String = withContext(Dispatchers.IO) {
        val uid = currentUid.ifBlank { return@withContext "Error" }
        try {
            firestore.collection("users").document(uid).set(
                mapOf("stickerPackIds" to com.google.firebase.firestore.FieldValue.arrayUnion(packId)),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            val profile = ChatDataCache.loadProfile(context, uid)
            if (profile != null) {
                val newList = profile.stickerPackIds.toMutableList()
                if (!newList.contains(packId)) newList.add(packId)
                ChatDataCache.saveProfile(context, profile.copy(stickerPackIds = newList))
            } else {
                // If profile is null, fetch it directly
                val userDoc = firestore.collection("users").document(uid).get().await()
                val newIds = userDoc.get("stickerPackIds") as? List<String> ?: listOf(packId)
                val newProfile = org.visorlink.app.data.model.UserProfile(uid = uid, stickerPackIds = newIds)
                ChatDataCache.saveProfile(context, newProfile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add pack to user", e)
        }
        refreshPacks(forceServer = false)
        "Pack Added"
    }

    suspend fun deletePack(packId: String, isOwner: Boolean) = withContext(Dispatchers.IO) {
        val uid = currentUid.ifBlank { return@withContext }
        try {
            _userPacksFlow.update { list -> list.filter { it.id != packId } }
            
            firestore.collection("users").document(uid)
                .update("stickerPackIds", com.google.firebase.firestore.FieldValue.arrayRemove(packId)).await()
            
            val profile = ChatDataCache.loadProfile(context, uid)
            if (profile != null) {
                ChatDataCache.saveProfile(context, profile.copy(stickerPackIds = profile.stickerPackIds - packId))
            }

            if (isOwner) {
                firestore.collection("stickerPacks").document(packId).delete().await()
                _storePacksFlow.update { list -> list.filter { it.id != packId } }
            }
            refreshPacks(forceServer = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete pack $packId", e)
        }
    }
}
