package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.visorlink.app.BuildConfig
import org.visorlink.app.data.idcard.GroupIdCard
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.IdOfferStatus
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdTrade
import org.visorlink.app.data.idcard.IdTradeOffer
import java.util.concurrent.ConcurrentHashMap

/** Состояние подписки на карту. */
sealed interface IdCardState {
    /** Выключено (нет флага или uid). */
    data object Off : IdCardState
    data object Loading : IdCardState
    /** [card] == null — карты нет. */
    data class Ready(val card: IdCard?) : IdCardState
    /** Правила не пустили к чужой карте (смотрящий без особого режима) — это норма, не ошибка. */
    data object Denied : IdCardState
}

val IdCardState.card: IdCard? get() = (this as? IdCardState.Ready)?.card

/** Инвентарь скинов. Ошибка подписки — пустой список, как в вебе. */
sealed interface IdSkinsState {
    data object Loading : IdSkinsState
    data class Ready(val skins: List<IdSkin>) : IdSkinsState
}

/** Обмен: [Ready.trade] == null — не найден или нет доступа (не участник чата). */
sealed interface IdTradeState {
    data object Loading : IdTradeState
    data class Ready(val trade: IdTrade?) : IdTradeState
}

/**
 * Ошибка callable с причиной из `details`: `cooldown` (+ `waitMs`), `no-card`, `not-enough-bits`,
 * `standard-id`, у инвентаря и обмена — `no-slot`, `equipped`, `already-listed`, `trade-closed`…
 */
class IdCardException(val reason: String?, val waitMs: Long, message: String?, cause: Throwable?) : Exception(message, cause)

/**
 * ID-карты (спеки ANDROID_ID_CARDS_SPEC.md, ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md): карта,
 * инвентарь скинов и обмены. Пишет только сервер: здесь — чтение и callables в europe-west1
 * (FirebaseFunctions из DI уже на этом регионе).
 */
class IdCardRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    flagsRepository: FlagsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteEnabled = MutableStateFlow(false)

    /**
     * Флаг `id_cards_enabled`: Remote Config, поверх — локальное переопределение из
     * FlagFlipper (`flags.localOverrides`) для отладки, как меню разработчика в вебе.
     */
    val idCardsEnabled: StateFlow<Boolean> = combine(remoteEnabled, flagsRepository.flags) { remote, flags ->
        flags.localOverrides[FLAG] ?: (remote || flags.serverClaims[FLAG] == true)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val cardFlows = ConcurrentHashMap<String, Flow<IdCardState>>()
    private val skinFlows = ConcurrentHashMap<String, Flow<IdSkinsState>>()
    private val tradeFlows = ConcurrentHashMap<String, Flow<IdTradeState>>()

    init {
        val rc = FirebaseRemoteConfig.getInstance()
        scope.launch {
            try {
                rc.setDefaultsAsync(mapOf(FLAG to false)).await()
                remoteEnabled.value = rc.getBoolean(FLAG)
                rc.fetchAndActivate().await()
                remoteEnabled.value = rc.getBoolean(FLAG)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Remote Config fetch failed", e)
            }
        }
        // Флаг включают при деплое функций — подхватываем без перезапуска
        rc.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                if (FLAG !in configUpdate.updatedKeys) return
                rc.activate().addOnCompleteListener { remoteEnabled.value = rc.getBoolean(FLAG) }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Remote Config update failed", error)
            }
        })
    }

    /**
     * Карта пользователя: одна подписка Firestore на uid для всех экранов, держится 4 с
     * после ухода последнего подписчика (профиль закрыли и тут же открыли).
     */
    fun cardFlow(uid: String?): Flow<IdCardState> {
        if (uid.isNullOrEmpty()) return flowOf(IdCardState.Off)
        return cardFlows.getOrPut(uid) {
            callbackFlow {
                trySend(IdCardState.Loading)
                val reg = db.collection("idCards").document(uid).addSnapshotListener { snap, err ->
                    if (err != null) {
                        val denied = err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        if (BuildConfig.DEBUG && !denied) Log.w(TAG, "idCards/$uid failed", err)
                        trySend(if (denied) IdCardState.Denied else IdCardState.Ready(null))
                        return@addSnapshotListener
                    }
                    trySend(IdCardState.Ready(if (snap?.exists() == true) IdCard.parse(snap.data) else null))
                }
                awaitClose { reg.remove() }
            }.shareIn(scope, SharingStarted.WhileSubscribed(4_000), replay = 1)
        }
    }

    /**
     * Свои скины (правила пускают только владельца), по дате получения. Одна подписка на uid для
     * всех экранов (вкладка «ID-карта», выбор скина для обмена) — как useIdSkins в вебе.
     */
    fun skinsFlow(uid: String?): Flow<IdSkinsState> {
        if (uid.isNullOrEmpty()) return flowOf(IdSkinsState.Ready(emptyList()))
        return skinFlows.getOrPut(uid) {
            callbackFlow {
                trySend(IdSkinsState.Loading)
                val reg = db.collection("idCards").document(uid).collection("skins").addSnapshotListener { snap, err ->
                    if (err != null) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "idCards/$uid/skins failed", err)
                        trySend(IdSkinsState.Ready(emptyList()))
                        return@addSnapshotListener
                    }
                    val skins = snap?.documents.orEmpty()
                        .mapNotNull { IdSkin.parse(it.id, it.data) }
                        .sortedBy { it.obtainedAt }
                    trySend(IdSkinsState.Ready(skins))
                }
                awaitClose { reg.remove() }
            }.shareIn(scope, SharingStarted.WhileSubscribed(4_000), replay = 1)
        }
    }

    /** Обмен для карточки в чате. Нет доступа или документа — `Ready(null)`, не ошибка. */
    fun tradeFlow(tradeId: String): Flow<IdTradeState> {
        if (tradeId.isEmpty() || '/' in tradeId) return flowOf(IdTradeState.Ready(null))
        return tradeFlows.getOrPut(tradeId) {
            callbackFlow {
                trySend(IdTradeState.Loading)
                val reg = db.collection("idTrades").document(tradeId).addSnapshotListener { snap, err ->
                    if (err != null) {
                        val denied = err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        if (BuildConfig.DEBUG && !denied) Log.w(TAG, "idTrades/$tradeId failed", err)
                        trySend(IdTradeState.Ready(null))
                        return@addSnapshotListener
                    }
                    trySend(IdTradeState.Ready(if (snap?.exists() == true) IdTrade.parse(snap.id, snap.data) else null))
                }
                awaitClose { reg.remove() }
            }.shareIn(scope, SharingStarted.WhileSubscribed(4_000), replay = 1)
        }
    }

    /** Своё предложение в чужом открытом обмене (id документа = uid). */
    fun myOfferFlow(tradeId: String, uid: String): Flow<IdTradeOffer?> = callbackFlow {
        val reg = db.collection("idTrades").document(tradeId).collection("offers").document(uid)
            .addSnapshotListener { snap, err ->
                trySend(if (err == null && snap?.exists() == true) IdTradeOffer.parse(snap.id, snap.data) else null)
            }
        awaitClose { reg.remove() }
    }

    /** Ожидающие предложения — только для автора обмена, в окне «Предложения». `null` — загрузка. */
    fun pendingOffersFlow(tradeId: String): Flow<List<IdTradeOffer>?> = callbackFlow {
        trySend(null)
        val reg = db.collection("idTrades").document(tradeId).collection("offers")
            .whereEqualTo("status", IdOfferStatus.PENDING.id)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "idTrades/$tradeId/offers failed", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().mapNotNull { IdTradeOffer.parse(it.id, it.data) }.sortedBy { it.createdAt })
            }
        awaitClose { reg.remove() }
    }

    suspend fun issue(mode: IdMode, species: String?): IdCard =
        call("issueIdCard", mapOf("mode" to mode.id, "species" to species?.trim()?.ifEmpty { null })).card()

    suspend fun setMode(mode: IdMode, species: String?): IdCard =
        call("setIdMode", mapOf("mode" to mode.id, "species" to species?.trim()?.ifEmpty { null })).card()

    suspend fun setGroupIdCard(chatId: String, enabled: Boolean): GroupIdCard? =
        GroupIdCard.parse(call("setGroupIdCard", mapOf("chatId" to chatId, "enabled" to enabled))["groupId"])

    // ── Инвентарь (idSkinInventory). «Заменить документ» (reissueIdCard) сервер больше не выполняет ──

    /** Прокрутка: −200 Bits, новый скин в инвентарь (не надевается). Раз в неделю, нужен свободный слот. */
    suspend fun rollSkin(): IdSkin {
        val raw = call(INVENTORY, mapOf("action" to "roll"))["skin"] as? Map<*, *>
        return raw?.let { IdSkin.parse(it["id"] as? String ?: "", it) } ?: throw IdCardException(null, 0, "Empty skin", null)
    }

    /** Надеть; если скин был выставлен на обмен — обмен закрывается. */
    suspend fun equipSkin(skinId: String): IdCard? =
        IdCard.parse(call(INVENTORY, mapOf("action" to "equip", "skinId" to skinId))["card"] as? Map<*, *>)

    /** Продать сервису за 100 Bits (надетый — нельзя); открытый обмен с ним закрывается. */
    suspend fun sellSkin(skinId: String) {
        call(INVENTORY, mapOf("action" to "sell", "skinId" to skinId))
    }

    /** −100 Bits, +1 слот (до 20). Возвращает новое число слотов. */
    suspend fun buySkinSlot(): Int? = (call(INVENTORY, mapOf("action" to "buySlot"))["slots"] as? Number)?.toInt()

    // ── Обмен в чатах (idSkinTrade): только скин на скин ──

    /** Выставить скин в ЛС или группе; сообщение `id_trade` пишет сервер. Возвращает tradeId. */
    suspend fun postTrade(chatId: String, skinId: String, note: String): String? =
        call(TRADE, mapOf("action" to "post", "chatId" to chatId, "skinId" to skinId, "note" to note.trim()))["tradeId"] as? String

    /** Предложить свой скин; повторное предложение заменяет прежнее. */
    suspend fun offerTrade(tradeId: String, skinId: String) {
        call(TRADE, mapOf("action" to "offer", "tradeId" to tradeId, "skinId" to skinId))
    }

    suspend fun acceptOffer(tradeId: String, offerId: String) {
        call(TRADE, mapOf("action" to "accept", "tradeId" to tradeId, "offerId" to offerId))
    }

    suspend fun declineOffer(tradeId: String, offerId: String) {
        call(TRADE, mapOf("action" to "decline", "tradeId" to tradeId, "offerId" to offerId))
    }

    suspend fun withdrawOffer(tradeId: String) {
        call(TRADE, mapOf("action" to "withdraw", "tradeId" to tradeId))
    }

    /** Снять с обмена (автор). */
    suspend fun closeTrade(tradeId: String) {
        call(TRADE, mapOf("action" to "close", "tradeId" to tradeId))
    }

    private fun Map<*, *>.card(): IdCard =
        IdCard.parse(this["card"] as? Map<*, *>) ?: throw IdCardException(null, 0, "Empty card", null)

    private suspend fun call(name: String, data: Map<String, Any?>): Map<*, *> = try {
        functions.getHttpsCallable(name).call(data).await().getData() as? Map<*, *> ?: emptyMap<Any, Any>()
    } catch (e: FirebaseFunctionsException) {
        val details = e.details as? Map<*, *>
        throw IdCardException(
            reason = details?.get("reason") as? String,
            waitMs = (details?.get("waitMs") as? Number)?.toLong() ?: 0,
            message = e.message,
            cause = e,
        )
    }

    private companion object {
        const val TAG = "IdCardRepository"
        const val FLAG = "id_cards_enabled"
        const val INVENTORY = "idSkinInventory"
        const val TRADE = "idSkinTrade"
    }
}
