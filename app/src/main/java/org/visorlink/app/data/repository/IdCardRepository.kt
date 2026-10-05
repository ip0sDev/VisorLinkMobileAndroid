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

/** Ошибка callable с причиной из `details` (`cooldown`, `no-card`, `not-enough-bits`, `standard-id`). */
class IdCardException(val reason: String?, val waitMs: Long, message: String?, cause: Throwable?) : Exception(message, cause)

/**
 * ID-карты (спека ANDROID_ID_CARDS_SPEC.md). Пишет только сервер: здесь — чтение и callables
 * в europe-west1 (FirebaseFunctions из DI уже на этом регионе).
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

    suspend fun issue(mode: IdMode, species: String?): IdCard =
        call("issueIdCard", mapOf("mode" to mode.id, "species" to species?.trim()?.ifEmpty { null })).card()

    suspend fun setMode(mode: IdMode, species: String?): IdCard =
        call("setIdMode", mapOf("mode" to mode.id, "species" to species?.trim()?.ifEmpty { null })).card()

    suspend fun reissue(): IdCard = call("reissueIdCard", emptyMap<String, Any?>()).card()

    suspend fun setGroupIdCard(chatId: String, enabled: Boolean): GroupIdCard? =
        GroupIdCard.parse(call("setGroupIdCard", mapOf("chatId" to chatId, "enabled" to enabled))["groupId"])

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
    }
}
