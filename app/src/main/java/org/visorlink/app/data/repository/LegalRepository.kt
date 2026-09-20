package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import org.visorlink.app.data.model.ChangelogEntry
import org.visorlink.app.data.model.LegalContacts
import org.visorlink.app.data.model.LegalContainer
import org.visorlink.app.data.model.LegalDocument
import org.visorlink.app.data.model.LegalMeta
import org.visorlink.app.data.model.LegalSection
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.visorlink.app.data.remote.chat.VisorLinkApi
import org.visorlink.app.data.repository.FlagsRepository
import org.json.JSONArray
import org.json.JSONObject

class LegalRepository(
    private val firestore: FirebaseFirestore,
    private val context: Context,
    private val api: VisorLinkApi? = null,
    private val flagsRepository: FlagsRepository? = null
) {
    companion object {
        private const val TAG = "LegalRepository"
        const val DEFAULT_FALLBACK_VERSION = "1.0.0"
        private const val TOS_ASSET = "legal_tos.json"
        private const val PRIVACY_ASSET = "legal_privacy_policy.json"
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

        val docRef = firestore.collection("internal")
            .document("legal_tos")
            .collection("tos")
            .document("tos")

        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error in legal version snapshot listener: ${error.message}")
                trySend(bundled)
                return@addSnapshotListener
            }

            val version = snapshot?.getString("version")
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
            Log.e(TAG, "Failed to fetch remote ToS document from server", e)
            throw IllegalStateException("Не удалось загрузить актуальные условия сервиса (ToS) с сервера: ${e.message}", e)
        }

        throw IllegalStateException("Документ ToS не найден на сервере")
    }

    /**
     * Загрузка полного документа Политики конфиденциальности строго с сервера.
     */
    suspend fun getPrivacyPolicyDocument(): LegalDocument = withContext(Dispatchers.IO) {
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
            Log.e(TAG, "Failed to fetch remote Privacy Policy from server", e)
            throw IllegalStateException("Не удалось загрузить актуальную политику конфиденциальности с сервера: ${e.message}", e)
        }

        throw IllegalStateException("Документ Privacy Policy не найден на сервере")
    }

    /**
     * Запись подтверждения согласия в профиль пользователя /users/{userId} и аудит через Cloud Function.
     */
    suspend fun recordConsent(userId: String, version: String): Unit = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        // 1. Вызов Callable Cloud Function recordUserLegalConsent для серверного аудита (IP, UA, timestamp)
        try {
            val params = hashMapOf(
                "version" to version,
                "agreeTos" to true,
                "agreePrivacy" to true,
                "agreePersonalData" to true,
                "agreeCrossBorder" to true,
                "agreeAge14" to true,
                "userAgent" to "VisorLink Android/${org.visorlink.app.BuildConfig.VERSION_NAME} (SDK ${android.os.Build.VERSION.SDK_INT}; ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})",
                "clientTimestamp" to timestamp
            )
            com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("recordUserLegalConsent")
                .call(params)
                .await()
            Log.i(TAG, "Cloud Function recordUserLegalConsent succeeded for version $version")
        } catch (e: Exception) {
            Log.w(TAG, "Cloud Function recordUserLegalConsent not available or failed: ${e.message}")
        }

        // 2. Локальная фиксация в защищенном хранилище EncryptedSharedPreferences
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "legal_secure_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            securePrefs.edit()
                .putString("accepted_version", version)
                .putLong("accepted_at", timestamp)
                .putBoolean("agree_tos", true)
                .putBoolean("agree_privacy", true)
                .putBoolean("agree_personal_data", true)
                .putBoolean("agree_cross_border", true)
                .putBoolean("agree_age14", true)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save consent into EncryptedSharedPreferences: ${e.message}")
        }

        // 3. Обновление профиля пользователя в Firestore
        firestore.collection("users").document(userId).update(
            mapOf(
                "acceptedAt" to FieldValue.serverTimestamp(),
                "acceptedVersion" to version
            )
        ).await()
        Log.i(TAG, "Consent recorded for user $userId (version: $version)")
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

    fun compareSemVer(v1: String, v2: String): Int {
        val s1 = v1.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val s2 = v2.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(s1.size, s2.size)
        for (i in 0 until maxLen) {
            val part1 = s1.getOrElse(i) { 0 }
            val part2 = s2.getOrElse(i) { 0 }
            if (part1 != part2) {
                return part1.compareTo(part2)
            }
        }
        return 0
    }

    fun isConsentRequired(remoteVersion: String, acceptedVersion: String?): Boolean {
        if (acceptedVersion.isNullOrBlank()) return true
        return compareSemVer(remoteVersion, acceptedVersion) > 0
    }
}

