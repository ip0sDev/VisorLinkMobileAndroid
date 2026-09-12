# Интеграция системы баг-репортов VisorLink в Android (Kotlin)

Данный документ описывает полное руководство для Android-разработчиков по интеграции отправки баг-репортов, загрузке скриншотов на CDN и получению статусов от официального бота.

---

## 1. Обзор архитектуры

- **Бэкенд:** Google Cloud Functions (`europe-west1`).
- **Эндпоинт отправки:**
  - **REST API:** `POST https://europe-west1-visorlink-f9484.cloudfunctions.net/submitBugReportRest`
  - **Firebase Callable SDK:** `FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("submitBugReport")`
- **Аутентификация:** Firebase Auth ID Token (`Authorization: Bearer <idToken>`).
- **Ограничения (Рейт-лимит):** Не более **10 баг-репортов в сутки** на пользователя (при превышении возвращается `HTTP 429 Too Many Requests` или `resource-exhausted`).
- **Скриншоты:** Загружаются на CDN VisorLink (`https://api.visorlink.org/upload`) с публичным доступом.

---

## 2. Модели данных (Kotlin Data Classes)

```kotlin
package org.visorlink.app.model.bugreport

import com.google.gson.annotations.SerializedName

/**
 * Запрос на отправку баг-репорта
 */
data class BugReportRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("stepsToReproduce") val stepsToReproduce: String? = null,
    @SerializedName("expectedBehavior") val expectedBehavior: String? = null,
    @SerializedName("actualBehavior") val actualBehavior: String? = null,
    @SerializedName("category") val category: String = "other", // ui, chat, calls, media, auth, other
    @SerializedName("severity") val severity: String = "medium", // low, medium, high, critical
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("buildNumber") val buildNumber: Long,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo,
    @SerializedName("screenshots") val screenshots: List<ScreenshotAttachment> = emptyList(),
    @SerializedName("logs") val logs: String? = null
)

/**
 * Системные метаданные устройства
 */
data class DeviceInfo(
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("model") val model: String,
    @SerializedName("osVersion") val osVersion: String,
    @SerializedName("sdkInt") val sdkInt: Int,
    @SerializedName("screenResolution") val screenResolution: String,
    @SerializedName("densityDpi") val densityDpi: Int,
    @SerializedName("language") val language: String,
    @SerializedName("timeZone") val timeZone: String,
    @SerializedName("freeMemoryMb") val freeMemoryMb: Long? = null
)

/**
 * Прикрепленный скриншот с CDN
 */
data class ScreenshotAttachment(
    @SerializedName("url") val url: String,
    @SerializedName("cdnMediaId") val cdnMediaId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("size") val size: Long
)

/**
 * Ответ сервера
 */
data class BugReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("reportId") val reportId: String,
    @SerializedName("number") val number: Long
)
```

---

## 3. Автоматический сбор информации об устройстве

Создайте вспомогательный класс `DeviceInfoProvider.kt`:

```kotlin
package org.visorlink.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.visorlink.app.model.bugreport.DeviceInfo
import java.util.Locale
import java.util.TimeZone

object DeviceInfoProvider {

    fun getDeviceInfo(context: Context): DeviceInfo {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val freeMemMb = memInfo.availMem / (1024 * 1024)

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            screenResolution = "${width}x${height}",
            densityDpi = density,
            language = Locale.getDefault().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
            freeMemoryMb = freeMemMb
        )
    }

    fun getAppVersion(context: Context): Pair<String, Long> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName ?: "1.0.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (e: PackageManager.NameNotFoundException) {
            Pair("1.0.0", 1L)
        }
    }
}
```

---

## 4. Загрузка скриншотов на CDN VisorLink

Для прикрепления скриншотов они предварительно отправляются на CDN VisorLink:
- **URL:** `POST https://api.visorlink.org/upload`
- **Заголовок:** `Authorization: Bearer <firebaseIdToken>`
- **Multipart Form:** `file` (файл изображения), `zone` = `public`

```kotlin
package org.visorlink.app.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.visorlink.app.model.bugreport.ScreenshotAttachment
import java.io.File

class CdnUploadService(private val okHttpClient: OkHttpClient) {

    suspend fun uploadScreenshot(file: File): ScreenshotAttachment {
        val user = FirebaseAuth.getInstance().currentUser 
            ?: throw IllegalStateException("Пользователь не авторизован")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Не удалось получить токен авторизации")

        val fileReqBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileReqBody)
            .addFormDataPart("zone", "public")
            .build()

        val request = Request.Builder()
            .url("https://api.visorlink.org/upload")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("CDN Upload Failed with HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty CDN response")
        val json = JSONObject(responseBody)

        val mediaId = json.getString("media_id")
        val fileUrl = json.getString("url")

        return ScreenshotAttachment(
            url = fileUrl,
            cdnMediaId = mediaId,
            fileName = file.name,
            size = file.length()
        )
    }
}
```

---

## 5. Вариант А: Отправка через REST API (Retrofit)

### 5.1. Интерфейс Retrofit

```kotlin
package org.visorlink.app.network

import org.visorlink.app.model.bugreport.BugReportRequest
import org.visorlink.app.model.bugreport.BugReportResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BugReportApi {
    @POST("submitBugReportRest")
    suspend fun submitBugReport(
        @Header("Authorization") authHeader: String,
        @Body request: BugReportRequest
    ): Response<BugReportResponse>
}
```

### 5.2. Реализация репозитория

```kotlin
package org.visorlink.app.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import org.visorlink.app.model.bugreport.BugReportRequest
import org.visorlink.app.model.bugreport.BugReportResponse
import org.visorlink.app.network.BugReportApi

class BugReportRepository(private val api: BugReportApi) {

    suspend fun sendReport(request: BugReportRequest): Result<BugReportResponse> {
        return try {
            val user = FirebaseAuth.getInstance().currentUser 
                ?: return Result.failure(IllegalStateException("Требуется авторизация"))
            val token = user.getIdToken(false).await().token 
                ?: return Result.failure(IllegalStateException("Токен отсутствует"))

            val response = api.submitBugReport("Bearer $token", request)

            when {
                response.isSuccessful && response.body() != null -> {
                    Result.success(response.body()!!)
                }
                response.code() == 429 -> {
                    Result.failure(Exception("Превышен суточный лимит: не более 10 баг-репортов в сутки."))
                }
                else -> {
                    Result.failure(Exception("Ошибка отправки: HTTP ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 6. Вариант Б: Отправка через Firebase Functions SDK

Если в проекте уже подключена зависимость `com.google.firebase:firebase-functions-ktx`:

```kotlin
package org.visorlink.app.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import org.visorlink.app.model.bugreport.BugReportRequest
import org.visorlink.app.model.bugreport.BugReportResponse

class FirebaseBugReportRepository {

    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val gson = Gson()

    suspend fun sendReport(request: BugReportRequest): Result<BugReportResponse> {
        return try {
            // Преобразуем дата-класс в Map для Firebase callable
            val jsonString = gson.toJson(request)
            @Suppress("UNCHECKED_CAST")
            val params = gson.fromJson(jsonString, Map::class.java) as Map<String, Any?>

            val result = functions
                .getHttpsCallable("submitBugReport")
                .call(params)
                .await()

            val resultMap = result.data as? Map<*, *>
            val reportId = resultMap?.get("reportId") as? String ?: ""
            val number = (resultMap?.get("number") as? Number)?.toLong() ?: 0L

            Result.success(BugReportResponse(true, reportId, number))
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                Result.failure(Exception("Превышен суточный лимит: не более 10 репортов в день."))
            } else {
                Result.failure(Exception(e.message ?: "Ошибка вызова функции"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 7. Пример UI: BottomSheetDialogFragment на Kotlin

```kotlin
package org.visorlink.app.ui.bugreport

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.visorlink.app.databinding.DialogBugReportBinding
import org.visorlink.app.util.DeviceInfoProvider

class BugReportBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogBugReportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogBugReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Иконки кнопок должны быть векторными Drawable (R.drawable.ic_close, R.drawable.ic_send, R.drawable.ic_attach)
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnSubmit.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val desc = binding.etDescription.text.toString().trim()

            if (title.length < 3) {
                binding.tilTitle.error = "Минимум 3 символа"
                return@setOnClickListener
            }
            if (desc.length < 5) {
                binding.tilDescription.error = "Опишите проблему подробнее"
                return@setOnClickListener
            }

            binding.progressBar.isVisible = true
            binding.btnSubmit.isEnabled = false

            val (vName, vCode) = DeviceInfoProvider.getAppVersion(requireContext())
            val dInfo = DeviceInfoProvider.getDeviceInfo(requireContext())

            val req = BugReportRequest(
                title = title,
                description = desc,
                stepsToReproduce = binding.etSteps.text?.toString(),
                category = getSelectedCategory(),
                severity = getSelectedSeverity(),
                platform = "android",
                appVersion = vName,
                buildNumber = vCode,
                deviceInfo = dInfo
            )

            // Отправка через ViewModel/Репозиторий...
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## 8. Взаимодействие с ботом (FaultyWire Bot)

Когда администратор сервиса изменяет статус тикета в канбане (например, переводит в «В процессе» или «Исправлен»), облачная функция находит автора бага и отправляет ему личное сообщение от официального системного бота:

- **UID бота:** `bot_faultywire`
- **Username:** `faultywire`
- **Отображаемое имя:** `FaultyWire Bot`
- **ID диалога в Firestore:** `[bot_faultywire, userUid].sort().join('_')`
- **Аватар бота:** Администрация платформы может динамически устанавливать и обновлять аватарку бота через панель администратора. Актуальный URL аватара всегда доступен в документе пользователя Firestore: `users/bot_faultywire` (поле `avatarUrl`), а также кэшируется в `participantData` соответствующего чата. В Android-клиенте загружайте аватарку через Coil/Glide с fallback-иконкой жука при `avatarUrl == null`.
- **Бейдж верификации:** Бот имеет статус `botBadge: "official"`. Рядом с именем бота в шапке чата и списке диалогов необходимо отображать официальный бейдж с галочкой (Official Bot).

В списке чатов Android-приложения автоматически появится (или поднимется наверх) диалог с `FaultyWire Bot` с текстом:
> 🛠️ **Ваш баг-репорт принят в работу!**  
> Тикет: **«...»** (#14)  
> Статус: **В процессе решения**  
> Инженеры VisorLink уже работают над исправлением.

И при закрытии/исправлении:
> ✅ **Баг успешно исправлен!**  
> Тикет: **«...»** (#14)  
> Статус: **Исправлен**  
> В ближайшее время будет выпущен фикс / обновление приложения. Спасибо за помощь в развитии платформы! 🚀

Никаких дополнительных сокетов для бота настраивать не нужно: сообщение приходит в обычную ветку личных сообщений Firestore (`chats/{chatId}/messages`), которая уже слушается вашим мобильным приложением.

---

## 9. Форматирование сообщений и рендеринг Markdown (Без сырых звездочек!)

Системные боты VisorLink и пользователи могут использовать базовую Markdown-разметку:
- `**жирный текст**` (заголовки, статусы, номера тикетов);
- `*курсивный текст*` (комментарии инженеров, примечания);
- `` `моноширинный код` `` (хеши коммитов, ID, технические параметры);
- `~~зачеркнутый текст~~`.

> ⚠️ **КРИТИЧЕСКИ ВАЖНО ДЛЯ ANDROID:**  
> Не отображайте сырой текст со звездочками (например, `**Баг успешно исправлен!**`). Текстовый компонент баббла сообщений **обязан парсить эти теги** в стилизованный текст!

### 9.1. Парсер для Jetpack Compose (`AnnotatedString`)

```kotlin
package org.visorlink.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

object MarkdownTextParser {

    private val MD_PATTERN = Pattern.compile("(\\*\\*([^*]+?)\\*\\*|\\*([^*]+?)\\*|~~([^~]+?)~~|`([^`]+?)`)")

    fun parse(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            val matcher = MD_PATTERN.matcher(text)
            var lastIndex = 0

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()

                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }

                val fullMatch = matcher.group(1) ?: ""
                when {
                    // **bold**
                    fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                        val content = fullMatch.substring(2, fullMatch.length - 2)
                        val spanStart = length
                        append(content)
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), spanStart, length)
                    }
                    // *italic*
                    fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                        val content = fullMatch.substring(1, fullMatch.length - 1)
                        val spanStart = length
                        append(content)
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), spanStart, length)
                    }
                    // ~~strikethrough~~
                    fullMatch.startsWith("~~") && fullMatch.endsWith("~~") -> {
                        val content = fullMatch.substring(2, fullMatch.length - 2)
                        val spanStart = length
                        append(content)
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), spanStart, length)
                    }
                    // `code`
                    fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                        val content = fullMatch.substring(1, fullMatch.length - 1)
                        val spanStart = length
                        append(content)
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            spanStart,
                            length
                        )
                    }
                }
                lastIndex = end
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    /**
     * Очистка текста от Markdown-символов для сниппета в списке чатов
     */
    fun stripMarkdown(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+?)\\*"), "$1")
            .replace(Regex("`([^`]+?)`"), "$1")
            .replace(Regex("~~([^~]+?)~~"), "$1")
    }
}
```

### 9.2. Очистка превью в списке диалогов
В RecyclerView / LazyColumn элементов чата передавайте `MarkdownTextParser.stripMarkdown(chat.lastMessage?.text)`. В списке чатов пользователь должен видеть чистую строку:
> `🛠️ Ваш баг-репорт принят в работу!`  
а не сырое `🛠️ **Ваш баг-репорт...`.

---

## 10. Отладочные плашки сообщений (Debug IDs)

В веб-версии VisorLink существует настройка разработчика (DevTab): *«Показывать ID сообщений и пользователей»*. При ее включении под бабблом выводится плашка `id: <messageId> (#seq)`.

В Android-приложении:
- Для обычных пользователей **НИКАКИХ технических ID сообщений под бабблами отображаться не должно**.
- Если реализуется экран для разработчиков (Debug / QA menu), вывод `message.id` и `message.seq` делайте строго опциональным по флагу `BuildConfig.DEBUG` или скрытому тумблеру в профиле пользователя.

---

## 11. Сбор и прикрепление диагностических логов (`logs`)

Поле `logs` в запросе `BugReportRequest` принимает до **5000 символов** текста.

Рекомендуется реализовать кольцевой буфер логов в памяти (Ring Buffer):
1. Перехватывать последние 60–100 записей `Log.e`, `Log.w` и неперехваченные исключения (`Thread.setDefaultUncaughtExceptionHandler`).
2. В диалоге баг-репорта добавить тумблер **«Прикрепить диагностические логи»** (включен по умолчанию).
3. При отправке обрезать строку логов: `logs?.take(5000)`.
4. Инженеры в Enterprise-панели FaultyWire смогут раскрыть секцию **«ДИАГНОСТИЧЕСКИЕ ЛОГИ & STACK TRACE»** и сразу увидеть контекст сбоя без необходимости запрашивать у пользователя повторные действия.

---

## 12. Отклонение тикетов (Реджекты и удаление)

Модераторы и администраторы платформы могут отклонить некорректные тикеты (спам, невоспроизводимые ошибки, штатное поведение и др.):

1. **Действие бэкенда (`adminRejectBugReport`):**
   - Запись баг-репорта **полностью удаляется** из коллекции Firestore `bugReports/{reportId}`.
   - Автору и подписчикам тикета от лица официального бота `FaultyWire Bot` (`bot_faultywire`) отправляется уведомление в личные сообщения:
     > ❌ **Ваш баг-репорт отклонен**  
     >  
     > Тикет: **«Название бага»** (#12)  
     > Причина: **Не удается воспроизвести проблему**  
     >  
     > 💬 *Комментарий инженера:*  
     > «Укажите, пожалуйста, модель наушников и версию кодека.»  
     >  
     > Запись тикета была удалена из базы данных FaultyWire. Если у вас появятся новые детали или подробные шаги воспроизведения, вы можете составить новый репорт.

2. **Поведение на клиенте Android:**
   - Так как документ удаляется из Firestore, список активных тикетов пользователя в реальном времени исключает данный баг-репорт.
   - Пользователь видит диалог с `FaultyWire Bot` в топе списка чатов с отформатированным уведомлением и причиной отклонения.
