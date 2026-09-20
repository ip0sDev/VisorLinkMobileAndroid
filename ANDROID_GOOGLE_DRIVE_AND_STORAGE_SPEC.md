# VisorLink Android Client — Спецификация интеграции Google Drive, Firebase Storage и выпиливания CDN (Media & Storage Architecture Spec)

Документация для Android-разработчиков (**Kotlin**, **Jetpack Compose**, **Firebase Android SDK**, **Google Drive REST API / Credential Manager**, **Coroutines & Flow**) по реализации новой архитектуры медиахранилища, модерации аватарок и легальным требованиям VisorLink.

---

## 1. Архитектурный обзор и критические изменения

### 1.1. Выпиливание Backend V2 и стороннего CDN
* **Сторонний CDN (`api.visorlink.org`) и Backend V2 полностью выведены из эксплуатации.**
* Android-клиент **НЕ ДОЛЖЕН** обращаться к старым эндпоинтам загрузки (`/upload`, `/upload/chunked`, `/stickerpacks/create` и т.д.).
* Вся кодовая база клиента работает исключительно через **Firebase** (Auth, Firestore, Cloud Functions, Firebase Storage, Realtime Database) и **личный Google Drive пользователя**.

### 1.2. Легальные требования к хранению пользовательских медиа
1. **Пользовательские вложения (фото, видео, аудиозаписи, голосовые, альбомы):**
   - Хранятся **только** на личном Google Drive пользователя. Серверы VisorLink не сохраняют копии медиафайлов у себя.
   - Приложение запрашивает у Google строго изолированный скоуп: `https://www.googleapis.com/auth/drive.file`.
   - Все загружаемые файлы изолированы в системной папке **«VisorLink Media»** на Диске пользователя.
2. **Аватарки, обложки и данные профиля:**
   - Хранятся в **Firebase Storage** (`users/{uid}/...`).
   - Квота Google Диска пользователя под аватарки **не расходуется**.
   - Анимированные GIF в профиле **запрещены** — клиент конвертирует первый кадр GIF в статичный WebP перед отправкой.
   - Загрузка аватара завершается вызовом Cloud Function с автоматической модерацией **Google Cloud Vision SafeSearch** (`adult`, `violence`, `racy`).
3. **Стикеры:**
   - Создание и загрузка пользовательских стикерпаков **заблокированы** на уровне Firestore Rules и Cloud Functions.
   - Доступен только курируемый **официальный каталог стикеров** (Official Sticker Packs).
4. **Старые вложения (Legacy CDN):**
   - Все сообщения, ссылавшиеся на старый CDN (`cdnMediaId` или `api.visorlink.org`), отображаются как архивные неподдерживаемые вложения: *«Неподдерживаемый формат медиа (Архивное вложение недоступно)»*. Клиент **не делает HTTP-запросов к CDN**, предотвращая 404 ошибки.

---

## 2. Безопасная аутентификация Google Drive на Android

### 2.1. Скоуп авторизации
Приложению разрешено запрашивать **только**:
```kotlin
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
```
> [!IMPORTANT]
> Запрещено запрашивать полные скоупы `drive` или `drive.readonly`. Скоуп `drive.file` предоставляет доступ исключительно к файлам и папкам, созданным самим приложением VisorLink, гарантируя приватность пользователя и защиту от санкций Google CASA.

### 2.2. Получение Server OAuth Client ID
Чтобы избежать утечки ключей в исходниках и сборках, идентификатор OAuth клиента получается динамически через Cloud Function:

```kotlin
// RemoteConfig / Cloud Function Service
class GoogleDriveConfigRepository(
    private val functions: FirebaseFunctions
) {
    private var cachedClientId: String? = null

    suspend fun getGoogleClientId(): String {
        cachedClientId?.let { return it }

        return try {
            val result = functions
                .getHttpsCallable("getGoogleDriveConfig")
                .call()
                .await()

            val data = result.data as? Map<*, *>
            val clientId = data?.get("clientId") as? String ?: ""
            if (clientId.isNotBlank()) {
                cachedClientId = clientId
            }
            clientId
        } catch (e: Exception) {
            Log.e("GoogleDriveConfig", "Failed to fetch Google Client ID", e)
            ""
        }
    }
}
```

### 2.3. Авторизация через Credential Manager / Google Sign-In
Для Android рекомендуется использовать **Google Identity Services** (`Credential Manager`) или `GoogleSignInOptions`:

```kotlin
fun getGoogleSignInOptions(serverClientId: String): GoogleSignInOptions {
    return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestServerAuthCode(serverClientId)
        .requestScopes(Scope(DRIVE_FILE_SCOPE))
        .build()
}
```

После получения токена доступа (Access Token):
- Токен сохраняется в зашифрованном виде (`EncryptedSharedPreferences`).
- Фиксируется время истечения токена (`expiresAt = System.currentTimeMillis() + expiresIn - 60_000`).

---

## 3. Сервис Google Drive: папка «VisorLink Media» и загрузка

### 3.1. Создание и получение корневой папки
При первой отправке медиа клиент находит или создаёт папку **«VisorLink Media»**:

```kotlin
suspend fun getOrCreateVisorLinkFolder(accessToken: String): String {
    // 1. Проверяем кэшированный ID папки
    val cachedFolderId = preferences.getString("gdrive_folder_id", null)
    if (cachedFolderId != null && isFolderValid(accessToken, cachedFolderId)) {
        return cachedFolderId
    }

    // 2. Ищем существующую папку по названию
    val searchQuery = "name = 'VisorLink Media' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    val existingId = queryDriveFolder(accessToken, searchQuery)
    if (existingId != null) {
        preferences.edit().putString("gdrive_folder_id", existingId).apply()
        return existingId
    }

    // 3. Создаем новую папку в корне
    val newFolderId = createDriveFolder(accessToken, "VisorLink Media")
    preferences.edit().putString("gdrive_folder_id", newFolderId).apply()
    return newFolderId
}
```

### 3.2. Загрузка файла и публикация ссылки (Permissions)
Каждое отправляемое медиа загружается через **Multipart Resumable Upload** Google Drive API v3:

1. **Загрузка файла в папку:**
```http
POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart
Authorization: Bearer {accessToken}
Content-Type: multipart/related; boundary=boundary_vl

--boundary_vl
Content-Type: application/json; charset=UTF-8

{
  "name": "photo_1710600000000.jpg",
  "parents": ["{folderId}"]
}
--boundary_vl
Content-Type: image/jpeg

[RAW_FILE_BYTES]
--boundary_vl--
```

2. **Публикация файла (Права доступа на чтение по ссылке):**
Чтобы собеседники в чате могли загрузить медиа, файл должен иметь доступ `reader` для `anyone`:
```http
POST https://www.googleapis.com/drive/v3/files/{fileId}/permissions
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "role": "reader",
  "type": "anyone"
}
```

3. **Формирование URL:**
- Прямой URL картинки / видео: `https://lh3.googleusercontent.com/d/{fileId}`
- Ссылка на просмотр: `https://drive.google.com/file/d/{fileId}/view?usp=sharing`
- Превью миниатюры: `https://lh3.googleusercontent.com/d/{fileId}=s400`

---

## 4. Структура сообщений в Firestore

При отправке сообщения в коллекцию `chats/{chatId}/messages`:

```kotlin
data class MessageEntity(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val type: String = "text", // "image", "video", "audio", "voice", "album"
    val text: String = "",
    
    // ── Google Drive Media Attributes ──
    val driveFileId: String? = null,
    val driveUrl: String? = null,
    val url: String? = null,              // https://lh3.googleusercontent.com/d/{fileId}
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val duration: Int? = null,            // Для audio / voice / video
    
    // ── Альбомы ──
    val images: List<DriveMediaItem>? = null,
    
    // ── Устаревшие поля CDN (для проверки Legacy) ──
    val cdnMediaId: String? = null,
    val thumbId: String? = null,
    
    val createdAt: Any? = null,
    val seq: Long = 0
)

data class DriveMediaItem(
    val driveFileId: String,
    val url: String,
    val previewUrl: String? = null,
    val spoiler: Boolean = false
)
```

---

## 5. Обработка устаревших медиа (Legacy CDN Suppression)

Чтобы приложение не крашилось и не спамило сетевыми ошибками 404:

```kotlin
fun isLegacyMediaMessage(message: MessageEntity): Boolean {
    // Если есть driveFileId, сообщение современное и валидное
    if (!message.driveFileId.isNullOrEmpty()) return false
    
    // Если есть старый cdnMediaId или URL ссылается на выключенный CDN
    if (!message.cdnMediaId.isNullOrEmpty()) return true
    if (!message.thumbId.isNullOrEmpty()) return true
    if (message.url?.contains("api.visorlink.org") == true) return true
    if (message.url?.contains("/f/") == true && !message.url.contains("googleusercontent.com")) return true
    
    // Если URL пустой и сообщение не в процессе локальной отправки
    if (message.url.isNullOrEmpty() && message.type != "text" && message.type != "album") return true
    
    return false
}
```

### Отображение в Jetpack Compose
Если `isLegacyMediaMessage(message) == true`, вместо плеера или превью картинки рендерится заглушка архивного сообщения:

```kotlin
@Composable
fun LegacyMediaPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_alert_triangle),
                contentDescription = null,
                tint = Color(0xFFF59E0B), // Amber warn
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.msg_unsupported_media),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 6. Аватарки и кастомизация: Firebase Storage & SafeSearch

### 6.1. Конвертация анимированных GIF в статичный WebP
По регламенту сервиса любые анимированные аватарки запрещены. При выборе GIF-файла клиент должен извлечь первый кадр:

```kotlin
suspend fun ensureStaticImage(context: Context, imageUri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val mimeType = context.contentResolver.getType(imageUri)
    val isGif = mimeType == "image/gif" || imageUri.path?.endsWith(".gif", ignoreCase = true) == true
    
    val inputStream = context.contentResolver.openInputStream(imageUri)
    val originalBitmap = BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    val outputStream = ByteArrayOutputStream()
    // Конвертируем в WebP (lossless/quality 90)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        originalBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, outputStream)
    } else {
        @Suppress("DEPRECATION")
        originalBitmap.compress(Bitmap.CompressFormat.WEBP, 90, outputStream)
    }
    outputStream.toByteArray()
}
```

### 6.2. Загрузка в Firebase Storage и вызов SafeSearch
```kotlin
suspend fun uploadAvatarWithModeration(uid: String, imageBytes: ByteArray): Result<String> = runCatching {
    val storage = FirebaseStorage.getInstance()
    val storagePath = "users/$uid/avatar_${System.currentTimeMillis()}.webp"
    val fileRef = storage.reference.child(storagePath)

    // 1. Загрузка в Firebase Storage
    val metadata = StorageMetadata.Builder()
        .setContentType("image/webp")
        .build()
        
    fileRef.putBytes(imageBytes, metadata).await()
    val downloadUrl = fileRef.downloadUrl.await().toString()

    // 2. Вызов Cloud Function SafeSearch модерации
    val functions = FirebaseFunctions.getInstance("europe-west1")
    val data = mapOf(
        "storagePath" to storagePath,
        "downloadUrl" to downloadUrl
    )

    val callableResult = functions
        .getHttpsCallable("setProfileAvatarWithSafeSearch")
        .call(data)
        .await()

    val responseMap = callableResult.data as? Map<*, *>
    val finalAvatarUrl = responseMap?.get("avatarUrl") as? String ?: downloadUrl
    finalAvatarUrl
}
```

> [!NOTE]
> Если Google Cloud Vision обнаружит adult, violence или racy контент, функция выбросит ошибку `FirebaseFunctionsException` с кодом `INVALID_ARGUMENT` и автоматически удалит загруженный файл из Storage. Клиент должен показать пользователю `Snackbar` с текстом ошибки.

---

### 7.1. Правила доступа
- Вкладки и кнопки «Создать стикерпак» в Android-клиенте должны быть **удалены**.
- Стикеры загружаются только из коллекции `stickerPacks`, где `isOfficial == true`.
- Модель стикерпака:
```kotlin
data class StickerPack(
    val id: String,
    val name: String,
    val author: String = "VisorLink Official",
    val isOfficial: Boolean = true,
    val stickers: List<StickerItem> = emptyList()
)

data class StickerItem(
    val id: String,
    val emoji: String,
    val url: String // Публичный URL из Firebase Storage
)
```

### 7.2. Принудительная инвалидация локального кэша стикеров (Purge Legacy Cache)
В предыдущих версиях Android-клиента стикерпаки могли кэшироваться в Room DB / SQLite / `SharedPreferences` или Coil/Glide дисковом кэше со ссылками на старый CDN (`api.visorlink.org/f/...`).
Чтобы пользователи не видели сломанные стикеры и приложение не спамило сетевыми ошибками 404:
1. **Сброс локальной БД стикеров при миграции:**
   - При старте приложения проверить ключ версии кэша, например: `PREF_STICKER_CACHE_VERSION = "official_curated_v3"`.
   - Если сохранённая версия ниже, выполнить `DELETE FROM sticker_packs` и очистить кэш стикеров.
2. **Фильтрация при получении из Firestore:**
   - Запрашивать только документы с условием `whereEqualTo("isOfficial", true)`.
   - Дополнительно на клиенте отсекать любые стикеры, URL которых содержит `api.visorlink.org` или относительные пути `/f/`.
3. **Безопасный рендеринг стикеров в истории сообщений:**
   - Если в сообщении типа `sticker` URL указывает на старый CDN, не передавать его в Glide/Coil, а отображать нейтральную заглушку `ic_sticker_placeholder` ("Стикер недоступен").

---

## 8. UI / UX рекомендации для экрана настроек («Хранилище»)

Экран настроек хранилища (`StorageSettingsScreen`) должен содержать:
1. **Google Drive Storage Card:**
   - Статус: Зелёный бейдж `Подключено` (папка «VisorLink Media») или Серый `Не подключено`.
   - Кнопка: **Подключить Google Drive** (с векторной иконкой Google Drive / плюса) или **Отключить** (красный стиль).
   - Описание: *«Все фото, видео, аудиофайлы и голосовые сообщения, отправляемые в чаты, сохраняются в вашей личной папке «VisorLink Media» на Google Диске.»*
2. **Облачное хранилище профиля Card (Firebase Storage):**
   - Информационная плашка с иконкой облака.
   - Текст: *«Аватарки, обложки профиля и стикеры хранятся в защищённом облаке VisorLink и не расходуют место на вашем Google Диске.»*
3. **Строгие UI правила проекта:**
   - В кнопках используются **только векторные иконки (`ImageVector` / SVG)**. Использование эмодзи в кнопках строго запрещено.
   - Никаких псевдоюридических текстов и пугающих предупреждений о скоупах.

---

## 9. Предотвращение бесконечной загрузки при старте и 2FA (Auth Lifecycle Safeguards)

При «холодном старте», чистой установке или медленном соединении профиль пользователя в Firestore (`users/{uid}`) может загружаться с задержкой в 0.5–2 секунды после успешной авторизации в Firebase Auth. 

### Типичная ошибка:
Если экран/роут ждёт подтверждения двухфакторной аутентификации (`isTfaChecking == true`), но документ пользователя ещё не загружен (`userData == null`), состояние проверки зависает, и пользователь видит бесконечный спиннер.

### Правило реализации для Android:
1. **Не блокировать UI проверкой 2FA, если флаг 2FA не включен:**
   - Экран ввода 2FA-кода должен показываться **только** если `userData != null && userData.tfaEnabled == true && !isTfaVerified`.
   - Если `userData` ещё загружается или `userData.tfaEnabled == false`, статус 2FA считается пройденным / не требующим ввода кода.
2. **Сторожевой таймер (Watchdog Timer):**
   - На Splash-экране / Auth Gate обязательно должен быть аварийный таймаут (не более 3.5–4 секунд).
   - Если за 4 секунды документ Firestore не загрузился, сбрасывать `isLoading = false` и пускать пользователя в основной интерфейс с базовым состоянием профиля из `FirebaseAuth.currentUser`.

---

## 10. Чек-лист проверки готовности Android-клиента

- [ ] В проекте полностью удалены зависимости и сетевые вызовы к `api.visorlink.org`.
- [ ] Кнопка отправки медиа (фото, видео, файл, аудио, голос) проверяет привязку Google Drive и открывает диалог подключения, если Диск не привязан.
- [ ] Запрашиваемый скоуп OAuth строго равен `https://www.googleapis.com/auth/drive.file`.
- [ ] Идентификатор OAuth клиента запрашивается с бэкенда через функцию `getGoogleDriveConfig`.
- [ ] Все отправляемые медиа попадают в папку `VisorLink Media` и имеют публичный доступ по ссылке (`anyone / reader`).
- [ ] Старые CDN-сообщения корректно определяются функцией `isLegacyMediaMessage()` и рендерятся через `LegacyMediaPlaceholder` без сетевых вызовов.
- [ ] Загрузка аватарок и обложек профиля идёт в Firebase Storage с конвертацией GIF в статичный WebP и проверкой `setProfileAvatarWithSafeSearch`.
- [ ] Возможность создания пользовательских стикерпаков скрыта/удалена; используется только официальный каталог (`isOfficial == true`).
- [ ] Локальный кэш старых стикеров инвалидирован/очищен при запуске.
- [ ] Экран загрузки / Auth Gate защищен watchdog-таймером и не зависает на проверке 2FA при первом входе / чистой установке.
- [ ] В кнопках интерфейса отсутствуют эмодзи (только векторные иконки).
