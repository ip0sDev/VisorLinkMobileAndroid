package by.iposdev.visorlink.data.repository

import android.content.Context
import android.util.Log
import by.iposdev.visorlink.data.model.ChangelogEntry
import by.iposdev.visorlink.data.model.LegalContacts
import by.iposdev.visorlink.data.model.LegalContainer
import by.iposdev.visorlink.data.model.LegalDocument
import by.iposdev.visorlink.data.model.LegalMeta
import by.iposdev.visorlink.data.model.LegalSection
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import by.iposdev.visorlink.data.repository.FlagsRepository
import org.json.JSONArray
import org.json.JSONObject

class LegalRepository(
    private val firestore: FirebaseFirestore,
    private val context: Context,
    private val api: VisorLinkApi? = null,
    private val flagsRepository: FlagsRepository? = null,
    private val functions: FirebaseFunctions? = null
) {
    companion object {
        private const val TAG = "LegalRepository"
        const val DEFAULT_FALLBACK_VERSION = "1.0.0"
        private const val TOS_ASSET = "legal_tos.json"
        private const val PRIVACY_ASSET = "legal_privacy_policy.json"
        private const val LEGAL_PREFS_NAME = "visorlink_legal_prefs"
        private const val KEY_ACCEPTED_VERSION = "accepted_version"
    }

    private val masterKey by lazy {
        androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                LEGAL_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.applicationContext.getSharedPreferences("${LEGAL_PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Чтение локально подтвержденной версии из защищенного хранилища EncryptedSharedPreferences (п. 2.2 ANDROID_COMPLIANCE).
     */
    fun getLocalAcceptedVersion(): String? {
        return securePrefs.getString(KEY_ACCEPTED_VERSION, null)
    }

    /**
     * Запись подтвержденной версии в защищенное хранилище EncryptedSharedPreferences (п. 2.2 ANDROID_COMPLIANCE).
     */
    fun saveLocalAcceptedVersion(version: String) {
        securePrefs.edit().putString(KEY_ACCEPTED_VERSION, version).apply()
    }

    /**
     * Сравнение двух семантических версий (SemVer).
     * Возвращает положительное число, если v1 > v2; 0, если равны; отрицательное, если v1 < v2.
     * Корректно нормализует форматы (например, "1.0" и "1.0.0" считаются эквивалентными).
     */
    fun compareSemVer(v1: String, v2: String): Int {
        val p1 = v1.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size, 3)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    /**
     * Проверка необходимости подписания соглашений согласно п. 2.1 ANDROID_COMPLIANCE:
     * Согласие требуется, только если актуальная серверная версия строго превышает
     * уже подтвержденную пользователем версию (remote.acceptedVersion > local.acceptedVersion).
     */
    fun isConsentRequired(remoteVersion: String, userAcceptedVersion: String?): Boolean {
        if (userAcceptedVersion.isNullOrBlank()) return true
        return compareSemVer(remoteVersion, userAcceptedVersion) > 0
    }

    /**
     * Получение актуальной версии ToS (асинхронно).
     * Пытается прочитать из Firestore, при ошибке/оффлайне берет из встроенного asset-файла.
     */
    suspend fun getLatestVersion(): String = withContext(Dispatchers.IO) {
        try {
            // Путь /internal/legal_tos/tos (подколлекция tos -> документ tos)
            val subSnap = firestore.collection("internal")
                .document("legal_tos")
                .collection("tos")
                .document("tos")
                .get()
                .await()

            if (subSnap.exists()) {
                val v = subSnap.getString("version")
                if (!v.isNullOrBlank()) return@withContext v
            }

            // Фоллбэк: проверка документа /internal/legal_tos
            val parentSnap = firestore.collection("internal")
                .document("legal_tos")
                .get()
                .await()

            if (parentSnap.exists()) {
                val v = parentSnap.getString("version")
                if (!v.isNullOrBlank()) return@withContext v
            }

            loadBundledVersion()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get latest version from Firestore, using bundled fallback: ${e.message}")
            loadBundledVersion()
        }
    }

    /**
     * Реактивный поток отслеживания актуальной версии ToS на лету через snapshot-слушатель.
     */
    fun observeLatestVersion(): Flow<String> = callbackFlow {
        val bundled = loadBundledVersion()
        trySend(bundled)

        if (flagsRepository?.isBackendV2Enabled() == true) {
            if (api != null) {
                try {
                    val doc = api.getInternalDocument("legal_tos")
                    val json = JSONObject(doc.content)
                    val version = json.optString("version")
                    if (!version.isNullOrBlank()) {
                        trySend(version)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch remote ToS version from backend v2: ${e.message}")
                }
            }
            awaitClose { }
            return@callbackFlow
        }

        val docRef = firestore.collection("internal")
            .document("legal_tos")

        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error in legal version snapshot listener: ${error.message}")
                trySend(bundled)
                return@addSnapshotListener
            }

            var version = snapshot?.getString("version")
            if (version.isNullOrBlank()) {
                val tosObj = snapshot?.get("tos") as? Map<*, *>
                version = tosObj?.get("version") as? String
            }
            if (!version.isNullOrBlank()) {
                trySend(version)
            } else {
                trySend(bundled)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Загрузка полного документа Условий использования (ToS).
     */
    suspend fun getTosDocument(): LegalDocument = withContext(Dispatchers.IO) {
        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            try {
                val doc = api.getInternalDocument("legal_tos")
                val json = JSONObject(doc.content)
                val container = parseLegalContainer(json)
                if (container.tos != null) return@withContext container.tos
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch remote ToS from backend v2: ${e.message}")
            }
            return@withContext loadBundledTosDocument()
        }

        try {
            val subSnap = firestore.collection("internal")
                .document("legal_tos")
                .collection("tos")
                .document("tos")
                .get()
                .await()

            if (subSnap.exists() && subSnap.data != null) {
                val json = JSONObject(subSnap.data!!)
                val container = parseLegalContainer(json)
                if (container.tos != null) return@withContext container.tos
            }

            val parentSnap = firestore.collection("internal")
                .document("legal_tos")
                .get()
                .await()

            if (parentSnap.exists() && parentSnap.data != null) {
                val json = JSONObject(parentSnap.data!!)
                val container = parseLegalContainer(json)
                if (container.tos != null) return@withContext container.tos
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote ToS document, using asset fallback: ${e.message}")
        }

        loadBundledTosDocument()
    }

    /**
     * Загрузка полного документа Политики конфиденциальности.
     */
    suspend fun getPrivacyPolicyDocument(): LegalDocument = withContext(Dispatchers.IO) {
        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            try {
                val doc = api.getInternalDocument("legal_privacy_policy")
                val json = JSONObject(doc.content)
                val container = parseLegalContainer(json)
                if (container.privacyPolicy != null) return@withContext container.privacyPolicy!!
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch remote Privacy Policy from backend v2: ${e.message}")
            }
            return@withContext loadBundledPrivacyDocument()
        }

        try {
            val ppSnap = firestore.collection("internal")
                .document("legal_privacy_policy")
                .get()
                .await()

            if (ppSnap.exists() && ppSnap.data != null) {
                val json = JSONObject(ppSnap.data!!)
                val container = parseLegalContainer(json)
                if (container.privacyPolicy != null) return@withContext container.privacyPolicy!!
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote Privacy Policy, using asset fallback: ${e.message}")
        }

        loadBundledPrivacyDocument()
    }

    /**
     * Запись подтверждения согласия с фиксацией аудита в Cloud Function (п. 2.2 ANDROID_COMPLIANCE),
     * сохранением в EncryptedSharedPreferences, Firestore и Backend v2.
     */
    suspend fun recordConsent(
        userId: String,
        version: String,
        agreeTos: Boolean = true,
        agreePrivacy: Boolean = true,
        agreePersonalData: Boolean = true,
        agreeCrossBorder: Boolean = true,
        agreeAge14: Boolean = true
    ): Unit = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        // 1. Локальное сохранение в EncryptedSharedPreferences (п. 2.2 ANDROID_COMPLIANCE)
        saveLocalAcceptedVersion(version)

        // 2. Вызов Callable Cloud Function recordUserLegalConsent для серверного аудита (п. 2.2 ANDROID_COMPLIANCE)
        if (functions != null) {
            try {
                val auditPayload = hashMapOf<String, Any>(
                    "version" to version,
                    "agreeTos" to agreeTos,
                    "agreePrivacy" to agreePrivacy,
                    "agreePersonalData" to agreePersonalData,
                    "agreeCrossBorder" to agreeCrossBorder,
                    "agreeAge14" to agreeAge14
                )
                functions.getHttpsCallable("recordUserLegalConsent")
                    .call(auditPayload)
                    .await()
                Log.i(TAG, "recordUserLegalConsent Cloud Function logged successfully for $userId, version $version")
            } catch (e: Exception) {
                Log.w(TAG, "recordUserLegalConsent Cloud Function call failed: ${e.message}")
            }
        }

        // 3. Синхронизация с Backend v2 (если включен)
        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            try {
                api.updateProfile(by.iposdev.visorlink.data.remote.chat.UpdateProfileRequest(
                    acceptedAt = timestamp,
                    acceptedVersion = version,
                    customization = mapOf(
                        "acceptedAt" to timestamp,
                        "acceptedVersion" to version
                    )
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record consent on backend: ${e.message}")
            }
            try {
                val cacheUid = "${userId}_backend"
                val cached = by.iposdev.visorlink.utils.ChatDataCache.loadProfile(context, cacheUid)
                if (cached != null) {
                    by.iposdev.visorlink.utils.ChatDataCache.saveProfile(
                        context,
                        cached.copy(
                            acceptedVersion = version,
                            acceptedAt = com.google.firebase.Timestamp(java.util.Date(timestamp))
                        )
                    )
                }
            } catch (_: Exception) {}
            return@withContext
        }

        // 4. Обновление Firestore через SetOptions.merge(), предотвращающее ошибку NOT_FOUND
        try {
            firestore.collection("users").document(userId).set(
                mapOf(
                    "acceptedAt" to FieldValue.serverTimestamp(),
                    "acceptedVersion" to version
                ),
                SetOptions.merge()
            ).await()
            Log.i(TAG, "Consent recorded for user $userId (version: $version)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update Firestore consent: ${e.message}")
        }

        // 5. Обновление локального SQLite-кэша профиля
        try {
            val cached = by.iposdev.visorlink.utils.ChatDataCache.loadProfile(context, userId)
            if (cached != null) {
                by.iposdev.visorlink.utils.ChatDataCache.saveProfile(
                    context,
                    cached.copy(
                        acceptedVersion = version,
                        acceptedAt = com.google.firebase.Timestamp(java.util.Date(timestamp))
                    )
                )
            }
        } catch (_: Exception) {}
    }

    fun loadBundledVersion(): String {
        return try {
            val jsonStr = context.assets.open(TOS_ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            json.optString("version", DEFAULT_FALLBACK_VERSION)
        } catch (e: Exception) {
            DEFAULT_FALLBACK_VERSION
        }
    }

    fun loadBundledTosDocument(): LegalDocument {
        return try {
            val jsonStr = context.assets.open(TOS_ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val container = parseLegalContainer(json)
            container.tos ?: LegalDocument(id = "terms_of_service", title = "Правила сервиса", version = DEFAULT_FALLBACK_VERSION)
        } catch (e: Exception) {
            LegalDocument(id = "terms_of_service", title = "Правила сервиса", version = DEFAULT_FALLBACK_VERSION)
        }
    }

    fun loadBundledPrivacyDocument(): LegalDocument {
        return try {
            val jsonStr = context.assets.open(PRIVACY_ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val container = parseLegalContainer(json)
            container.privacyPolicy ?: LegalDocument(id = "privacy_policy", title = "Политика конфиденциальности", version = DEFAULT_FALLBACK_VERSION)
        } catch (e: Exception) {
            LegalDocument(id = "privacy_policy", title = "Политика конфиденциальности", version = DEFAULT_FALLBACK_VERSION)
        }
    }

    private fun parseLegalContainer(json: JSONObject): LegalContainer {
        val version = json.optString("version", DEFAULT_FALLBACK_VERSION)
        val versionCode = json.optInt("versionCode", 1)
        val lastUpdated = json.optNullableString("lastUpdated")
        val updatedAt = json.optNullableString("updatedAt")

        val changelog = mutableListOf<ChangelogEntry>()
        val clArray = json.optJSONArray("changelog")
        if (clArray != null) {
            for (i in 0 until clArray.length()) {
                val entryObj = clArray.optJSONObject(i) ?: continue
                val chList = mutableListOf<String>()
                val chArr = entryObj.optJSONArray("changes")
                if (chArr != null) {
                    for (j in 0 until chArr.length()) {
                        chList.add(chArr.optString(j))
                    }
                }
                changelog.add(
                    ChangelogEntry(
                        version = entryObj.optString("version"),
                        versionCode = entryObj.optInt("versionCode"),
                        date = entryObj.optString("date"),
                        changes = chList
                    )
                )
            }
        }

        var meta: LegalMeta? = null
        val metaObj = json.optJSONObject("meta")
        if (metaObj != null) {
            var contacts: LegalContacts? = null
            val cObj = metaObj.optJSONObject("contacts")
            if (cObj != null) {
                contacts = LegalContacts(
                    supportEmail = cObj.optString("supportEmail", "support@visorlink.org"),
                    privacyEmail = cObj.optNullableString("privacyEmail"),
                    legalEmail = cObj.optString("legalEmail", "legal@visorlink.org"),
                    abuseEmail = cObj.optNullableString("abuseEmail")
                )
            }
            meta = LegalMeta(
                version = metaObj.optString("version", version),
                versionCode = metaObj.optInt("versionCode", versionCode),
                effectiveDate = metaObj.optNullableString("effectiveDate"),
                updatedAt = metaObj.optNullableString("updatedAt"),
                serviceName = metaObj.optString("serviceName", "VisorLink"),
                serviceUrl = metaObj.optString("serviceUrl", "visorlink.org"),
                operator = metaObj.optNullableString("operator"),
                contacts = contacts
            )
        }

        val tosObj = json.optJSONObject("tos")
        val tosDoc = if (tosObj != null) parseLegalDocument(tosObj) else null

        val ppObj = json.optJSONObject("privacy_policy") ?: json.optJSONObject("privacyPolicy")
        val ppDoc = if (ppObj != null) parseLegalDocument(ppObj) else null

        return LegalContainer(
            version = version,
            versionCode = versionCode,
            lastUpdated = lastUpdated,
            updatedAt = updatedAt,
            changelog = changelog,
            meta = meta,
            tos = tosDoc,
            privacyPolicy = ppDoc
        )
    }

    private fun parseLegalDocument(json: JSONObject): LegalDocument {
        val sections = mutableListOf<LegalSection>()
        val secArray = json.optJSONArray("sections")
        if (secArray != null) {
            for (i in 0 until secArray.length()) {
                val secObj = secArray.optJSONObject(i) ?: continue
                val keyPoints = mutableListOf<String>()
                val kpArray = secObj.optJSONArray("keyPoints")
                if (kpArray != null) {
                    for (k in 0 until kpArray.length()) {
                        keyPoints.add(kpArray.optString(k))
                    }
                }
                sections.add(
                    LegalSection(
                        id = secObj.optString("id"),
                        number = secObj.optString("number"),
                        title = secObj.optString("title"),
                        badge = secObj.optNullableString("badge"),
                        keyPoints = keyPoints,
                        content = secObj.optString("content")
                    )
                )
            }
        }

        return LegalDocument(
            id = json.optString("id"),
            title = json.optString("title"),
            subtitle = json.optNullableString("subtitle"),
            version = json.optNullableString("version"),
            versionCode = if (json.has("versionCode")) json.optInt("versionCode") else null,
            lastUpdated = json.optNullableString("lastUpdated"),
            introHtml = json.optNullableString("introHtml"),
            sections = sections
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
    }
}
