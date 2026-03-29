package by.iposdev.visorlink.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class StickerPackRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions
) {
    private val currentUid get() = auth.currentUser!!.uid

    // ─── Observe user's pack list + eagerly load each pack's stickers ─────────

    fun observeUserPacks(): Flow<List<StickerPack>> = callbackFlow {
        val userDataRef = db.collection("users")
            .document(currentUid)
            .collection("stickerData")
            .document("packs")

        val reg = userDataRef.addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val packIds = (snap.get("packIds") as? List<String>) ?: emptyList()
            if (packIds.isEmpty()) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            // Fire-and-forget coroutine to load pack details
            // We use trySend inside a launched coroutine; the outer callbackFlow scope is used.
            kotlinx.coroutines.GlobalScope.async {
                try {
                    val packs = loadPacksWithStickers(packIds)
                    trySend(packs)
                } catch (_: Exception) {
                    trySend(emptyList())
                }
            }
        }

        awaitClose { reg.remove() }
    }

    private suspend fun loadPacksWithStickers(packIds: List<String>): List<StickerPack> =
        coroutineScope {
            packIds.map { packId ->
                async {
                    try {
                        val packDoc = db.collection("stickerPacks").document(packId).get().await()
                        if (!packDoc.exists()) return@async null
                        val pack = packDoc.toObject(StickerPack::class.java)?.copy(id = packDoc.id)
                            ?: return@async null
                        val stickers = loadStickers(packId)
                        pack.copy(stickers = stickers)
                    } catch (_: Exception) { null }
                }
            }.awaitAll().filterNotNull()
        }

    private suspend fun loadStickers(packId: String): List<StickerItem> {
        val snap = db.collection("stickerPacks").document(packId)
            .collection("stickers")
            .get().await()
        return snap.documents.mapNotNull { doc ->
            try { doc.toObject(StickerItem::class.java)?.copy(id = doc.id) }
            catch (_: Exception) { null }
        }
    }

    // ─── Fetch a single pack by id (for AddStickerPackBanner) ─────────────────

    suspend fun getPackById(packId: String): StickerPack? = try {
        val doc = db.collection("stickerPacks").document(packId).get().await()
        if (!doc.exists()) null
        else doc.toObject(StickerPack::class.java)?.copy(id = doc.id)
    } catch (_: Exception) { null }

    // ─── Check if user already has a pack ─────────────────────────────────────

    suspend fun hasPack(packId: String): Boolean = try {
        val doc = db.collection("users").document(currentUid)
            .collection("stickerData").document("packs").get().await()
        @Suppress("UNCHECKED_CAST")
        val ids = (doc.get("packIds") as? List<String>) ?: emptyList()
        packId in ids
    } catch (_: Exception) { false }

    // ─── Cloud Functions ──────────────────────────────────────────────────────

    suspend fun createPack(name: String, emoji: String): String {
        val result = functions.getHttpsCallable("createStickerPack")
            .call(mapOf("name" to name, "emoji" to emoji)).await()
        return (result.data as Map<*, *>)["packId"] as String
    }

    suspend fun deletePack(packId: String) {
        functions.getHttpsCallable("deleteStickerPack")
            .call(mapOf("packId" to packId)).await()
    }

    suspend fun addPackToUser(packId: String): String {
        val result = functions.getHttpsCallable("addStickerPack")
            .call(mapOf("packId" to packId)).await()
        return (result.data as Map<*, *>)["packName"] as? String ?: ""
    }

    // ─── Upload sticker image to Storage, then write to Firestore ─────────────

    suspend fun uploadSticker(packId: String, uri: Uri, emoji: String): StickerItem {
        val fileName = "${System.currentTimeMillis()}.webp"
        val storagePath = "stickerPacks/$packId/$currentUid/$fileName"
        val ref = storage.reference.child(storagePath)
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        val stickerRef = db.collection("stickerPacks").document(packId)
            .collection("stickers").document()
        val data = mapOf(
            "url" to url,
            "emoji" to emoji,
            "storagePath" to storagePath,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        stickerRef.set(data).await()

        // increment stickerCount
        db.collection("stickerPacks").document(packId)
            .update("stickerCount", com.google.firebase.firestore.FieldValue.increment(1)).await()

        return StickerItem(id = stickerRef.id, url = url, emoji = emoji, storagePath = storagePath)
    }

    suspend fun deleteStickerFromPack(packId: String, sticker: StickerItem) {
        try { storage.reference.child(sticker.storagePath).delete().await() } catch (_: Exception) {}
        db.collection("stickerPacks").document(packId)
            .collection("stickers").document(sticker.id).delete().await()
        db.collection("stickerPacks").document(packId)
            .update("stickerCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
    }

    suspend fun renamePack(packId: String, name: String, emoji: String) {
        db.collection("stickerPacks").document(packId)
            .update(mapOf("name" to name, "emoji" to emoji)).await()
    }
}