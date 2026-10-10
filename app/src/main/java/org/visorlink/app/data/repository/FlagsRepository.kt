package org.visorlink.app.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.visorlink.app.BuildConfig
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.model.flags.ConfigRequest
import org.visorlink.app.data.model.flags.PairRequest
import org.visorlink.app.data.remote.flags.AegisKeyManager
import org.visorlink.app.data.remote.flags.FlagsApi
import com.auth0.android.jwt.JWT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.HttpException
import java.util.UUID

class FlagsRepository(
    private val context: Context,
    private val api: FlagsApi,
    private val keyManager: AegisKeyManager
) {
    private val prefs = context.getSharedPreferences("visorlink_flags_prefs", Context.MODE_PRIVATE)
    private val _flags = MutableStateFlow(AppFlags())
    val flags: StateFlow<AppFlags> = _flags.asStateFlow()

    private val fetchMutex = Mutex()

    /** `elapsedRealtime` последней успешной загрузки, `null` — ещё не было. */
    @Volatile
    private var lastFetchAt: Long? = null

    fun notifyFlagsChanged() {
        _flags.value = _flags.value.copy()
    }

    init {
        loadInitialFlags()
    }

    private fun loadInitialFlags() {
        val overridesJson = prefs.getString("local_overrides", null)
        val overrides = try {
            if (overridesJson != null) {
                val map = mutableMapOf<String, Boolean>()
                val json = JSONObject(overridesJson)
                json.keys().forEach { key ->
                    map[key] = json.getBoolean(key)
                }
                map
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap<String, Boolean>()
        }
        // В Debug-режиме переопределение может как включать, так и выключать флаг.
        // В релизе переопределение может только выключить флаг, так что `true` ничего не значит,
        // а ключи удалённых флагов больше никто не читает.
        val cleanOverrides = if (BuildConfig.DEBUG) {
            overrides.filter { (key, _) -> key !in AppFlags.REMOVED_KEYS }
        } else {
            overrides.filter { (key, value) -> !value && key !in AppFlags.REMOVED_KEYS }
        }
        if (cleanOverrides != overrides) saveOverrides(cleanOverrides)
        prefs.edit().remove("is_flipper_enabled").apply()

        val cachedClaimsJson = prefs.getString("cached_server_claims", null)
        val cachedClaims = try {
            if (cachedClaimsJson != null) {
                val map = mutableMapOf<String, Any?>()
                val json = JSONObject(cachedClaimsJson)
                json.keys().forEach { key ->
                    map[key] = json.get(key)
                }
                map
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap<String, Any?>()
        }

        _flags.value = AppFlags(serverClaims = cachedClaims, localOverrides = cleanOverrides)
    }

    fun getInstallId(): String {
        var id = prefs.getString("install_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("install_id", id).apply()
        }
        return id
    }

    fun getDeviceId(): String? {
        return prefs.getString("device_id", null)
    }

    /**
     * Возвращает постоянный идентификатор клиента для отображения в UI и таргетирования в панели Hermes.
     * Если устройство уже зарегистрировано на сервере Hermes — возвращает `device_id`, иначе локальный `install_id`.
     */
    fun getClientFlagsId(): String {
        return getDeviceId() ?: getInstallId()
    }

    suspend fun pairIfNeeded(): String {
        val existingId = getDeviceId()
        val keyExists = keyManager.hasKey()

        if (existingId != null && keyExists) {
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Using existing device_id: $existingId and existing key.")
            return existingId
        }

        if (existingId != null && !keyExists) {
            if (BuildConfig.DEBUG) Log.w("FlagsRepo", "Device ID exists but Key is missing! Clearing ID and re-pairing.")
            prefs.edit().remove("device_id").apply()
        }

        val installId = getInstallId()
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Starting pairing process for install_id: $installId...")
        val publicKeyPem = keyManager.generatePublicKeyPem()
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Generated Public Key PEM:\n$publicKeyPem")
        
        val response = api.pair(PairRequest(publicKeyPem, installId))
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Pairing successful, received device_id: ${response.deviceId}")
        
        prefs.edit().putString("device_id", response.deviceId).apply()
        return response.deviceId
    }

    /**
     * Загружает флаги с сервера. Вызовы не пересекаются: два параллельных запуска иначе
     * могли бы одновременно пройти pairing и получить разные `device_id`.
     *
     * @return `true`, если конфиг получен и применён.
     */
    suspend fun fetchFlags(): Boolean = fetchMutex.withLock { fetchLocked() }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    /**
     * Автообновление конфига, пока приложение на экране: раз в [AUTO_REFRESH_MS] (10 минут).
     * Вызывается при уходе приложения на передний план; [stopAutoRefresh] — при уходе в фон.
     * Первая проверка — сразу, если конфиг старше интервала.
     */
    fun startAutoRefresh(scope: kotlinx.coroutines.CoroutineScope) {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = scope.launch {
            while (true) {
                runCatching { refreshIfStale(AUTO_REFRESH_MS) }
                kotlinx.coroutines.delay(AUTO_REFRESH_MS)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * Повторная загрузка, если с последней успешной прошло больше [maxAgeMs]. Возраст
     * проверяется под тем же замком: на холодном старте загрузка из VisorLinkApp и
     * проверка при onResume иначе ушли бы на сервер обе.
     */
    suspend fun refreshIfStale(maxAgeMs: Long) {
        fetchMutex.withLock {
            val last = lastFetchAt
            if (last == null || SystemClock.elapsedRealtime() - last >= maxAgeMs) fetchLocked()
        }
    }

    /**
     * 401 значит, что сервер не знает этот `device_id` (запись удалили или отозвали):
     * регистрируемся заново тем же ключом и повторяем запрос — один раз, без цикла.
     * Раньше сброс стоял под `BuildConfig.DEBUG`, и в релизе такое устройство навсегда
     * оставалось на последних закэшированных флагах, включая режим обслуживания.
     */
    private suspend fun fetchLocked(): Boolean {
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Fetching flags...")
        return try {
            requestConfig()
        } catch (e: HttpException) {
            if (e.code() != 401) {
                if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error fetching flags", e)
                return false
            }
            if (BuildConfig.DEBUG) Log.w("FlagsRepo", "Received 401. Clearing device_id and re-pairing.")
            prefs.edit().remove("device_id").apply()
            try {
                requestConfig()
            } catch (retry: Exception) {
                if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error fetching flags after re-pair", retry)
                false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error fetching flags", e)
            false
        }
    }

    private suspend fun requestConfig(): Boolean {
        val deviceId = pairIfNeeded()
        val timestamp = System.currentTimeMillis() / 1000L
        val nonce = UUID.randomUUID().toString().replace("-", "")

        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Signing payload: deviceId=$deviceId, timestamp=$timestamp, nonce=$nonce")
        val signature = keyManager.signPayload(deviceId, timestamp, nonce)
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Generated Signature: $signature")

        val response = api.getConfig(
            signature = signature,
            request = ConfigRequest(deviceId, timestamp, nonce)
        )

        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Config response received. Token: ${response.token}")
        val applied = parseAndApplyFlags(response.token)
        if (applied) lastFetchAt = SystemClock.elapsedRealtime()
        return applied
    }

    private fun parseAndApplyFlags(token: String): Boolean {
        return try {
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Parsing JWT Token: $token")
            val jwt = JWT(token)

            val allClaimsMap = mutableMapOf<String, Any?>()
            val claimsJson = JSONObject()
            jwt.claims.forEach { (key, claim) ->
                if (key !in listOf("iss", "sub", "iat", "exp")) {
                    val value = when {
                        claim.asBoolean() != null -> claim.asBoolean()
                        claim.asInt() != null -> claim.asInt()
                        claim.asDouble() != null -> claim.asDouble()
                        else -> claim.asString()
                    }
                    allClaimsMap[key] = value
                    claimsJson.put(key, value)
                    if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Claim: $key = $value")
                }
            }
            // Кэшируем claims для offline-first работы
            prefs.edit().putString("cached_server_claims", claimsJson.toString()).apply()

            _flags.value = _flags.value.copy(serverClaims = allClaimsMap)
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error parsing flags JWT", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Переключатель Flag Flipper. В Debug-сборке сохраняет и true, и false,
     * чтобы можно было включить любую фичу без сервера. В релизе включить — значит
     * вернуть значение сервера, поэтому хранятся только выключения.
     */
    fun toggleFlag(key: String, enabled: Boolean) {
        val canonical = AppFlags.canonical(key)
        val currentOverrides = _flags.value.localOverrides
            .filterKeys { AppFlags.canonical(it) != canonical }
            .toMutableMap()
        if (BuildConfig.DEBUG) {
            currentOverrides[canonical] = enabled
        } else {
            if (!enabled) currentOverrides[canonical] = false
        }

        saveOverrides(currentOverrides)
        _flags.value = _flags.value.copy(localOverrides = currentOverrides)
    }

    private fun saveOverrides(overrides: Map<String, Boolean>) {
        val json = JSONObject()
        overrides.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("local_overrides", json.toString()).apply()
    }

    companion object {
        /** Как часто перечитывать конфиг флагов, пока приложение на экране. */
        const val AUTO_REFRESH_MS = 10 * 60_000L
    }
}
