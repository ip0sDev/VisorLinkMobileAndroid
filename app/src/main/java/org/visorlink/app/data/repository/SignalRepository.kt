package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.visorlink.app.data.model.SignalPeer
import org.visorlink.app.data.model.SignalStats
import org.visorlink.app.data.remote.EconomyCaller
import java.util.TimeZone

/**
 * «Сигнал» (веб `src/services/signalService.ts`): статистика `signalStats/{uid}` и достижения
 * `achievements/{uid}` — пишет только сервер, читает владелец; часовой пояс — `users/{uid}.timeZone`;
 * заморозки стрика — callable `streakFreeze`.
 */
class SignalRepository(
    private val db: FirebaseFirestore,
    private val context: Context
) {
    /**
     * Без правил `signalStats` чтение вернёт `permission-denied` — тогда пустая статистика.
     */
    fun stats(uid: String): Flow<SignalStats> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(SignalStats.EMPTY)
            close()
            return@callbackFlow
        }
        val reg = db.collection("signalStats").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("SignalRepository", "signalStats error: ${err.message}")
                    trySend(SignalStats.EMPTY)
                    return@addSnapshotListener
                }
                val data = snap?.data
                trySend(parseStats(data))
            }
        awaitClose { reg.remove() }
    }

    private fun parseStats(d: Map<String, Any?>?): SignalStats {
        if (d == null) return SignalStats.EMPTY
        val peers = mutableMapOf<String, SignalPeer>()
        val rawPeers = d["peers"] as? Map<*, *>
        rawPeers?.forEach { (rawUid, rawVal) ->
            val pMap = rawVal as? Map<*, *> ?: return@forEach
            val total = (pMap["total"] as? Number)?.toInt() ?: 0
            val last = parseMillis(pMap["last"])
            val recent = parseCounts(pMap["recent"])
            peers[rawUid.toString()] = SignalPeer(total = total, last = last, recent = recent)
        }
        val days = parseCounts(d["days"])
        val since = if (d.containsKey("since")) parseMillis(d["since"]) else null
        return SignalStats(days = days, peers = peers, since = since)
    }

    private fun parseCounts(raw: Any?): Map<String, Int> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val result = mutableMapOf<String, Int>()
        map.forEach { (k, v) ->
            if (v is Number) {
                result[k.toString()] = v.toInt()
            }
        }
        return result
    }

    private fun parseMillis(raw: Any?): Long = when (raw) {
        is Number -> raw.toLong()
        is Timestamp -> raw.toDate().time
        is java.util.Date -> raw.time
        else -> 0L
    }

    /**
     * Полученные достижения: id -> когда (мс).
     */
    fun achievements(uid: String): Flow<Map<String, Long>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val reg = db.collection("achievements").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("SignalRepository", "achievements error: ${err.message}")
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val rawUnlocked = snap?.data?.get("unlocked") as? Map<*, *>
                val result = mutableMapOf<String, Long>()
                rawUnlocked?.forEach { (k, v) ->
                    val vMap = v as? Map<*, *>
                    val at = parseMillis(vMap?.get("at"))
                    result[k.toString()] = at
                }
                trySend(result)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Купить заморозку: 150 Bits, в запасе не больше двух. Ошибки — `max`, `not-enough-bits`.
     */
    suspend fun buyStreakFreeze(): Int {
        val res = EconomyCaller.callEconomy("streakFreeze", mapOf("action" to "buy"))
        return (res["freezes"] as? Number)?.toInt() ?: 0
    }

    /**
     * Пояс устройства сменился — пишем одно поле (по нему сервер раскладывает сообщения по дням).
     */
    suspend fun syncTimeZone(uid: String, saved: String?) {
        val tz = try {
            TimeZone.getDefault().id
        } catch (_: Exception) {
            return
        }
        if (tz.isNullOrBlank() || tz.length > 64 || tz == saved) return
        try {
            db.collection("users").document(uid).update("timeZone", tz).await()
        } catch (e: Exception) {
            Log.w("SignalRepository", "Failed to sync timeZone: ${e.message}")
        }
    }

    // ── Какие достижения уже показаны уведомлением — на устройстве, как localStorage веба ──

    private fun seenKey(uid: String): String = "vl_ach_seen_$uid"

    private val prefs by lazy {
        context.getSharedPreferences("visorlink_achievements", Context.MODE_PRIVATE)
    }

    fun loadSeen(uid: String): Set<String>? {
        val set = prefs.getStringSet(seenKey(uid), null)
        if (set != null) return set.toSet()
        val str = prefs.getString(seenKey(uid), null) ?: return null
        return if (str.isEmpty()) emptySet() else str.split(",").toSet()
    }

    fun saveSeen(uid: String, ids: Set<String>) {
        prefs.edit().putStringSet(seenKey(uid), ids).apply()
    }
}
