# VisorLink Android Client — Спецификация интеграции нововведений (Session Updates Spec)

Документация для Android-разработчиков (**Kotlin**, **Jetpack Compose**, **Firebase Android SDK**, **Coroutines & Flow**) по реализации и синхронизации всех нововведений VisorLink:
1. **Каналы: UX подписки, права публикации и блокировка выхода для гостей (Channel Subscription & Permissions)**
2. **BotAPI: Realtime Database статус «Печатает...» (Bot Typing Indicators)**
3. **Gemini AI Bot: Обработка статусов генерации, Concurrency Lock и UI блокировка ввода**
4. **Инвайт-система: Регистрация по кодам и заявки на доступ (Invite-Only Registration)**
5. **Пересылка сообщений (Message Forwarding & Saved Messages)**
6. **Безопасное копирование токенов ботов и вебхуков (Bot Tokens & Webhook Keys Modals)**
7. **Музыка и аудиосообщения (Audio & Music Player, ID3 Metadata Embedding, Media3/ExoPlayer)**

---

## 1. Каналы: UX подписки и права доступа

### 1.1. Концепция и правила ролей
В отличие от обычных групп, в каналах (`chat.type == "channel"`):
- **Неподписанный пользователь (`!isMember`)**:
  - Может просматривать посты публичного канала (`settings.joinByTag == true` или открыт по ссылке).
  - **НЕ ДОЛЖЕН** видеть стандартное поле ввода сообщений.
  - Внизу экрана видит закрепленную панель подписки с кнопкой **«Подписаться»**.
  - **НЕ ДОЛЖЕН** видеть кнопку настроек канала (шестерёнку) в TopBar.
  - При открытии профиля/информации о канале (клик по шапке) **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** показывать кнопку «Покинуть канал». Вместо неё отображается кнопка «Подписаться».
- **Подписанный участник (`isMember && role == "member"`)**:
  - Видит канал в своём списке чатов и в ленте публикаций (`Feed`).
  - Не может писать сообщения (писать могут только `admin` и `owner`).
  - Внизу экрана вместо неактивного текстового поля видит информационную плашку: *«📢 Только администраторы могут писать»*.
  - В окне информации о канале имеет кнопку *«Покинуть канал»*.
- **Администратор / Владелец (`role in ["admin", "owner"]`)**:
  - Видит стандартное поле ввода и может публиковать посты, прикреплять медиа и стикеры.
  - В TopBar видит кнопку настроек канала (шестерёнку).

---

### 1.2. Определение статуса участника (`isChannelMember`)

```kotlin
// Модель чата
data class Chat(
    val id: String,
    val type: String = "direct", // "direct", "group", "channel"
    val name: String = "",
    val memberCount: Int = 0,
    val memberIds: List<String> = emptyList(),
    val createdBy: String = "",
    val tag: String? = null,
    val settings: ChatSettings = ChatSettings()
)

// Проверка участия текущего пользователя (currentUid)
fun isChannelMember(chat: Chat, myMemberRole: String?, currentUid: String): Boolean {
    if (chat.type != "channel") return true
    if (myMemberRole != null) return true
    if (chat.createdBy == currentUid) return true
    return chat.memberIds.contains(currentUid)
}

fun canPostToChannel(chat: Chat, myMemberRole: String?, currentUid: String): Boolean {
    if (chat.type != "channel") return true
    if (chat.createdBy == currentUid) return true
    return myMemberRole == "admin" || myMemberRole == "owner"
}
```

---

### 1.3. Серверные вызовы: Подписка и выход из канала

#### Подписка на канал: `joinChannel`
Вызывается через `FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("joinChannel")`.

- **Параметры**:
  ```json
  {
    "chatId": "channel_chat_id_here"
  }
  ```
- **Результат при успехе**:
  ```json
  {
    "success": true,
    "chatId": "channel_chat_id_here",
    "alreadyMember": false
  }
  ```
- **Fallback (если нет связи с `joinChannel`)**: вызвать `joinByTag` с тегом канала:
  ```json
  {
    "tag": "channel_tag_without_at"
  }
  ```

#### Выход из канала: `leaveChat`
- **Функция**: `leaveChat` (`europe-west1`)
- **Параметры**: `{ "chatId": "channel_chat_id_here" }`
- **Результат**: `{ "success": true }`

---

### 1.4. Jetpack Compose: Нижняя панель канала (`ChannelBottomBar`)

```kotlin
@Composable
fun ChannelBottomBar(
    chat: Chat,
    isMember: Boolean,
    canPost: Boolean,
    isJoining: Boolean,
    onJoinClick: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    when {
        // 1. Гость: Показываем плашку с кнопкой подписки
        !isMember -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chat.memberCount} подписчиков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onJoinClick,
                        enabled = !isJoining,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("📢 Подписаться")
                        }
                    }
                }
            }
        }

        // 2. Подписчик (без прав на постинг): аккуратная полоса только для чтения
        !canPost -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📢 Только администраторы могут писать",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Администратор / Создатель: полноценный ввод сообщения
        else -> {
            MessageInputField(onSend = onSendMessage)
        }
    }
}
```

---

## 2. BotAPI: Realtime Database статус «Печатает...»

### 2.1. Структура RTDB
Статусы печати ботов хранятся в **Firebase Realtime Database** региона `europe-west1`:
`https://visorlink-f9484-default-rtdb.europe-west1.firebasedatabase.app`

Путь:
```text
/typing/{chatId}/{botUid}
```

Значение узла:
```json
{
  "isTyping": true,
  "ts": 1788647000123
}
```

### 2.2. Правило экспирации статуса печати
1. Клиент проверяет поле `ts` (timestamp).
2. Если `isTyping == true`, но `System.currentTimeMillis() - ts > 4000L` (прошло более 4 секунд с последнего heartbeat), считать статус неактивным.
3. Облачные функции ботов отправляют сигнал `isTyping: true` каждые **2.5 секунды**, пока формируется ответ, и удаляют узел (`isTyping: false` или удаление ключа) при завершении.

### 2.3. Реализация Kotlin Flow / Repository

```kotlin
class TypingRepository(
    private val rtdb: FirebaseDatabase
) {
    fun observeBotTyping(chatId: String, botUid: String): Flow<Boolean> = callbackFlow {
        val typingRef = rtdb.getReference("typing").child(chatId).child(botUid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = snapshot.child("isTyping").getValue(Boolean::class.java) ?: false
                val ts = snapshot.child("ts").getValue(Long::class.java) ?: 0L
                val isFresh = (System.currentTimeMillis() - ts) < 4000L
                trySend(isTyping && isFresh)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }

        typingRef.addValueEventListener(listener)
        awaitClose { typingRef.removeEventListener(listener) }
    }
}
```

---

## 3. Gemini Bot: UI блокировка и Concurrency Lock

### 3.1. Архитектура защиты от параллельных запросов
При общении с ботом на базе Gemini реализована двухуровневая защита:

1. **Серверный лок (Firestore)**:
   - При получении запроса сервер атомарно захватывает документ `gemini_locks/{chatId}` на 60 секунд.
   - Если пользователь отправит повторный запрос до завершения первого, сервер возвратит:
     > *«⏳ Пожалуйста, подождите: я ещё генерирую ответ на ваш предыдущий вопрос!»*
2. **Клиентская блокировка (Android UI)**:
   - Когда `isBotTyping == true`:
     - Текстовое поле ввода (`OutlinedTextField`) становится `enabled = false`.
     - Плейсхолдер меняется на: *«⏳ Бот генерирует ответ... Пожалуйста, подождите.»*
     - Кнопки отправки, вложения файлов, голосовых и стикеров **блокируются** (`enabled = false`).
     - Над полем ввода отображается анимированный баннер с индикатором загрузки (`LinearProgressIndicator` или `CircularProgressIndicator`).

```kotlin
@Composable
fun BotChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    isBotGenerating: Boolean,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Баннер ожидания ответа AI
        AnimatedVisibility(
            visible = isBotGenerating,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Бот генерирует ответ... Пожалуйста, дождитесь завершения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Строка ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                enabled = !isBotGenerating,
                placeholder = {
                    Text(
                        if (isBotGenerating) "⏳ Бот генерирует ответ... Пожалуйста, подождите."
                        else "Сообщение..."
                    )
                },
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = onSend,
                enabled = !isBotGenerating && text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Отправить")
            }
        }
    }
}
```

### 3.2. Лимиты бесплатного тарифа Gemini (UI обработка)
Если пользователь не ввел собственный ключ через `/key <API_KEY>`:
- Максимальная длина сообщения: **600 символов** (рекомендуется выводить счетчик символов `text.length / 600` в диалоге с ботом).
- Лимит: **3 запроса в сутки**.
- При превышении бот присылает сообщение с инструкцией по созданию бесплатного ключа на `aistudio.google.com`.

---

## 4. Система инвайтов (Invite-Only Registration)

### 4.1. Модель данных Firestore

#### Одноразовый инвайт-код: `/registration_invites/{code}`
```json
{
  "code": "38FJEIF7WL92",
  "createdAt": "Timestamp",
  "createdBy": "admin_uid",
  "isUsed": false,
  "usedBy": null,
  "usedAt": null
}
```

#### Заявка на доступ: `/access_requests/{requestId}`
```json
{
  "requestId": "req_12345",
  "email": "user@example.com",
  "username": "ivan_dev",
  "note": "Хочу тестировать VisorLink",
  "status": "pending", // "pending", "approved", "rejected"
  "createdAt": "Timestamp",
  "processedAt": null,
  "processedBy": null
}
```

---

### 4.2. Cloud Functions для авторизации и регистрации

| Функция | Назначение | Параметры | Ответ |
|---|---|---|---|
| `checkRegistrationCode` | Проверка валидности кода перед регистрацией | `{ "code": "38FJEIF7WL92" }` | `{ "valid": true }` / Ошибка |
| `requestAccess` | Создание заявки на доступ («Хочу получить доступ») | `{ "email": "...", "username": "...", "note": "..." }` | `{ "success": true, "requestId": "..." }` |
| `adminListAccessRequests` | Список заявок (для админки) | `{ "status": "pending" }` | `{ "requests": [...] }` |
| `adminApproveAccessRequest` | Одобрение заявки админом | `{ "requestId": "..." }` | `{ "success": true }` |
| `adminRejectAccessRequest` | Отклонение заявки админом | `{ "requestId": "..." }` | `{ "success": true }` |
| `adminCreateRegistrationCode`| Создание разовой ссылки/кода | `{ "note": "Для партнера" }` | `{ "success": true, "code": "..." }` |

---

### 4.3. Deep Link обработка ссылок приглашения
Приложение должно обрабатывать входящие ссылки вида:
- `https://visorlink.org/invite?code=38FJEIF7WL92`
- `visorlink://invite?code=38FJEIF7WL92`

В `AndroidManifest.xml`:
```xml
<activity android:name=".ui.AuthActivity" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="visorlink.org" android:pathPrefix="/invite" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="visorlink" android:host="invite" />
    </intent-filter>
</activity>
```

При запуске экрана регистрации код автоматически подставляется в поле ввода инвайта и проверяется через `checkRegistrationCode`.

---

## 5. Пересылка сообщений (Message Forwarding & Saved Messages)

### 5.1. Модель данных `forwardFrom`
В сообщении VisorLink пересылка фиксируется вложенным объектом `forwardFrom`.
Принципиальные требования к валидности структуры:
1. `senderId` — строковый UID оригинального автора сообщения.
2. `senderUsername` — никнейм автора сообщения (строка длиной $\le 64$).
3. `chatId` — идентификатор исходного чата. **ВНИМАНИЕ**: если сообщение пересылается из «Избранного» (`Saved Messages`), поле `chatId` **НЕ ДОЛЖНО** присутствовать в документе (или исключаться при сериализации). Ни в коем случае не записывать литерал `null`, иначе правила Firestore отклонят транзакцию.
4. `chatName` — опциональное название чата-источника (для каналов/групп).
5. `messageId` — ID оригинального сообщения.

```kotlin
data class ForwardFrom(
    val senderId: String = "",
    val senderUsername: String = "user",
    val chatId: String? = null,
    val chatName: String? = null,
    val messageId: String? = null
)

// Правило одноуровневой пересылки (как в Telegram):
// При пересылке уже пересланного сообщения сохраняется оригинальный forwardFrom.
fun buildForwardFrom(
    message: Message,
    sourceChatId: String?,
    sourceChatName: String?
): ForwardFrom {
    message.forwardFrom?.let { return it }
    return ForwardFrom(
        senderId = message.senderId,
        senderUsername = message.senderUsername.ifEmpty { "user" },
        chatId = sourceChatId?.takeIf { it.isNotBlank() },
        chatName = sourceChatName?.takeIf { it.isNotBlank() },
        messageId = message.id.takeIf { it.isNotBlank() }
    )
}
```

### 5.2. Атомарная отправка (Firestore WriteBatch & Cooldown)
Согласно правилам `firestore.rules`:
1. Создаваемое сообщение должно содержать ключи: `['senderId', 'senderUsername', 'createdAt', 'type']`.
2. При пересылке в обычный чат (`chats/{chatId}/messages`) необходимо в **том же самом `WriteBatch`** обновить поле `lastMessageAt: FieldValue.serverTimestamp()` в документе `users/{myUid}` (проверка `isCooldownPassed()`), а также обновить превью последнего сообщения в `chats/{chatId}`:

```kotlin
suspend fun forwardMessageToChat(
    db: FirebaseFirestore,
    currentUid: String,
    currentUsername: String,
    targetChatId: String,
    message: Message,
    sourceChatId: String?,
    sourceChatName: String?
) {
    val forwardFrom = buildForwardFrom(message, sourceChatId, sourceChatName)
    val batch = db.batch()

    // 1. Документ сообщения
    val msgRef = db.collection("chats").document(targetChatId)
        .collection("messages").document()

    val msgMap = mutableMapOf<String, Any>(
        "senderId" to currentUid,
        "senderUsername" to currentUsername,
        "createdAt" to FieldValue.serverTimestamp(),
        "type" to (message.type.ifEmpty { "text" }),
        "forwardFrom" to mapOfNotNull(
            "senderId" to forwardFrom.senderId,
            "senderUsername" to forwardFrom.senderUsername,
            "chatId" to forwardFrom.chatId,
            "chatName" to forwardFrom.chatName,
            "messageId" to forwardFrom.messageId
        ),
        "readBy" to listOf(currentUid),
        "deleted" to false,
        "reactions" to emptyList<String>()
    )

    // Копирование контента сообщения
    message.text?.let { msgMap["text"] = it }
    message.url?.let { msgMap["url"] = it }
    message.caption?.let { msgMap["caption"] = it }
    message.storagePath?.let { msgMap["storagePath"] = it }
    message.fileName?.let { msgMap["fileName"] = it }
    message.voiceUrl?.let { msgMap["voiceUrl"] = it }
    message.duration?.let { msgMap["duration"] = it }
    message.stickerId?.let { msgMap["stickerId"] = it }
    message.packEmoji?.let { msgMap["packEmoji"] = it }

    batch.set(msgRef, msgMap)

    // 2. Обновление превью чата
    val preview = "↩ " + when (message.type) {
        "text" -> (message.text ?: "").take(60)
        "image" -> "🖼 Фото"
        "voice" -> "🎙 Голосовое сообщение"
        "sticker" -> "${message.packEmoji ?: "😊"} Стикер"
        else -> "Пересланное сообщение"
    }
    batch.update(
        db.collection("chats").document(targetChatId),
        mapOf(
            "lastMessage" to mapOf(
                "text" to preview,
                "senderId" to currentUid,
                "senderUsername" to currentUsername
            ),
            "lastMessageAt" to FieldValue.serverTimestamp()
        )
    )

    // 3. Обновление кулдауна пользователя (ОБЯЗАТЕЛЬНО для правил Firestore!)
    batch.update(
        db.collection("users").document(currentUid),
        "lastMessageAt", FieldValue.serverTimestamp()
    )

    batch.commit().await()
}
```

### 5.3. Пересылка в «Избранное» (`savedMessages`)
Для сохранения в «Избранное»:
- Путь коллекции: `savedMessages/{myUid}/messages`.
- Кулдаун для `savedMessages` правилами не требуется, но рекомендуется обновлять `lastMessageAt` для консистентности.
- `chatId` в `forwardFrom` не указывается, если сообщение было сохранено из самого Избранного.

### 5.4. Отрисовка пересланного сообщения в UI
Над основным контентом бабла сообщения рендерится шапка с иконкой стрелки пересылки:
- Если `forwardFrom.chatId == null`:
  - Текст: *«Переслано из Избранного»*
- Если `forwardFrom.chatName != null`:
  - Текст: *«Переслано от @{senderUsername} · {chatName}»*
- Иначе:
  - Текст: *«Переслано от @{senderUsername}»*

---

## 6. Безопасное копирование токенов ботов и вебхуков (Modals & Clipboard)

### 6.1. UX создания ботов и ключей
При создании бота (`createBot`) или генерации ключа вебхука (`createWebhookKey`):
1. **Категорически запрещено** выводить токен через стандартный системный `Toast` или `Snackbar` с автоскрытием, так как длинный токен невозможно успеть скопировать.
2. Необходимо отображать **модальный диалог (`AlertDialog` в Compose)** с:
   - Моноширинным полем отображения ключа (с возможностью скрыть/показать по иконке глаза).
   - Кнопкой **«Скопировать»**, копирующей строку в `ClipboardManager` с тактильным откликом (haptic feedback) и показом короткого уведомления: *«Токен скопирован в буфер обмена»*.
   - Четким предупреждающим бейджем: *«⚠️ Сохраните этот токен прямо сейчас. В целях безопасности он больше никогда не будет показан повторно!»*.
   - Кнопкой подтверждения закрытия диалога: *«Я сохранил токен»*.

### 6.2. Jetpack Compose: `SecretTokenDialog`
```kotlin
@Composable
fun SecretTokenDialog(
    title: String,
    token: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔑 $title", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Скопируйте и сохраните токен прямо сейчас. Он отображается один раз и не может быть восстановлен!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Поле с токеном
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(token))
                        isCopied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Копировать"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isCopied) "Скопировано в буфер!" else "Скопировать токен")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Готово, я сохранил токен")
            }
        }
    )
}
```

---

## 7. Музыка и аудиозаписи (Music & Audio Streaming, Metadata Embedding)

### 7.1. Модель аудиосообщения в Firestore
Сообщения с типом `type == "audio"` содержат метаданные трека:

```kotlin
data class Message(
    val id: String = "",
    val type: String = "text", // "audio", "voice", "image", etc.
    val text: String? = null,
    val cdnMediaId: String? = null,      // Идентификатор аудиофайла на CDN
    val url: String? = null,             // Прямой URL к треку
    val coverCdnMediaId: String? = null, // Идентификатор обложки на CDN
    val coverUrl: String? = null,        // Прямой URL к изображению обложки
    val title: String? = null,           // Название трека
    val performer: String? = null,       // Исполнитель / автор
    val duration: Int? = null,           // Длительность в секундах
    val fileSize: Long? = null,          // Размер файла в байтах
    val fileName: String? = null,        // Имя исходного файла
    val senderUid: String = "",
    val createdAt: Timestamp? = null
)
```

URL для воспроизведения и загрузки с CDN формируется с токеном авторизации:
```kotlin
fun getCdnAudioUrl(cdnMediaId: String, authToken: String): String {
    return "https://cdn.visorlink.org/f/$cdnMediaId?token=$authToken"
}
```

---

### 7.2. Встраивание ID3-тегов перед отправкой на клиенте
> **Важное архитектурное требование**:
> Если пользователь в модалке редактирует название, автора или обложку перед отправкой трека, эти метаданные **обязательно встраиваются в сам файл** перед загрузкой на CDN. Это снижает нагрузку на клиентов и сохраняет метаданные при скачивании файла другими пользователями.

На Android рекомендуется библиотека `mp3agic` или `jaudiotagger`:

```kotlin
// build.gradle.kts (app)
// implementation("com.mpatric:mp3agic:0.9.2")

import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import java.io.File

fun embedId3Metadata(
    sourceFile: File,
    outputFile: File,
    title: String,
    artist: String,
    coverBytes: ByteArray?,
    coverMimeType: String = "image/jpeg"
) {
    val mp3File = Mp3File(sourceFile.absolutePath)
    val id3v2Tag = if (mp3File.hasId3v2Tag()) mp3File.id3v2Tag else ID3v24Tag()

    id3v2Tag.title = title
    id3v2Tag.artist = artist

    if (coverBytes != null && coverBytes.isNotEmpty()) {
        id3v2Tag.setAlbumImage(coverBytes, coverMimeType)
    }

    mp3File.id3v2Tag = id3v2Tag
    mp3File.save(outputFile.absolutePath)
}
```

---

### 7.3. Архитектура плеера в чате (Jetpack Compose + Media3/ExoPlayer)

Плеер в сообщении чата состоит из:
1. **Широкий контейнер бабла (`bubble-audio`)**: `width = 320.dp`, `maxWidth = 88.dp%` ширины экрана. Обычный бабл `max-width: 260.dp` для плеера расширяется, чтобы элементы не сжимались.
2. **Круглая кнопка Play / Pause** (`38.dp`):
   - Размещена слева от обложки либо встроена в левую часть плеера.
   - Выделена цветом `MaterialTheme.colorScheme.primary` со значком треугольника (Play) или двойной полоски (Pause).
3. **Обложка трека** (`44.dp x 44.dp`):
   - Закругленные углы `10.dp`.
   - При активном воспроизведении обложка анимированно вращается по кругу (эффект виниловой пластинки) и становится круглой (`shape = CircleShape`).
4. **Блок названия и автора с эллипсисом**:
   - `maxLines = 1`, `overflow = TextOverflow.Ellipsis` для всех текстовых полей, чтобы длинные названия треков не вылезали за границы бабла.
5. **Кнопка скорости 1x / 1.5x / 2x**:
   - Полупрозрачная пилюля (`background = surface.copy(alpha = 0.15f)`), высота `18.dp`, с моноширинным жирным шрифтом.
6. **Скруббер и тайминги**:
   - Полоса перемотки с ползунком `10.dp`.
   - Под ней разнесены `currentTime` (слева) и общая `duration` (справа).

```kotlin
@Composable
fun AudioMessageBubble(
    message: Message,
    authToken: String,
    isPlaying: Boolean,
    progress: Float,
    currentTimeSeconds: Int,
    durationSeconds: Int,
    playbackSpeed: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotate"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Кнопка Play / Pause
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 2. Обложка (Vinyl Spin)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(if (isPlaying) CircleShape else RoundedCornerShape(10.dp))
                        .rotate(if (isPlaying) rotation else 0f)
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!message.coverUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = message.coverUrl,
                            contentDescription = "Обложка",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵", fontSize = 18.sp)
                        }
                    }
                }

                // 3. Заголовок, исполнитель и кнопка скорости
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.title ?: "Без названия",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Кнопка скорости
                        TextButton(
                            onClick = onSpeedChange,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Кнопка скачать
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Скачать",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Автор и размер файла
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.performer ?: "Неизвестный исполнитель",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (message.fileSize != null && message.fileSize > 0) {
                            Text("•", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatFileSize(message.fileSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Скруббер (Slider)
            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            )

            // 5. Времена
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(currentTimeSeconds), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(durationSeconds), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

---

### 7.4. Закрепленный плеер над полем ввода чата (`AudioPlaybackDockBar`)
По аналогии с веб-клиентом VisorLink, при активном воспроизведении аудиозаписи над полем ввода чата открывается плашка `AudioPlaybackDockBar`.

Она позволяет:
1. Видеть трек и исполнителя, не теряя контекста при прокрутке длинной ленты чата.
2. Ставить на паузу / запускать трек.
3. По клику на название трека плавно скроллить чат прямо к сообщению с треком (`scrollToMessage(messageId)`).
4. Перематывать трек на мини-скруббере.
5. Закрыть плеер крестиком (`✕`).

```kotlin
@Composable
fun AudioPlaybackDockBar(
    title: String,
    performer: String?,
    coverUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onClickInfo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Кнопка плей/пауза
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Миниатюра обложки
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🎵", fontSize = 14.sp)
                }
            }

            // Название и исполнитель (клик скроллит к сообщению)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClickInfo() },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = performer ?: "Аудиозапись",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Мини-прогресс бар
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Кнопка закрытия плеера
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
            }
        }
    }
}
```

---

## 8. Чек-лист проверки для Android QA

### Каналы и права:
- [ ] При открытии любого публичного канала гостем (`!isMember`) отображается нижняя плашка с кнопкой **«📢 Подписаться»**.
- [ ] При клике на кнопку «Подписаться» вызывается `joinChannel`, статус меняется на подписчика, канал появляется в списке диалогов.
- [ ] Гость не может открыть меню настроек канала с кнопкой «Покинуть канал».
- [ ] Подписанный пользователь видит плашку *«Только администраторы могут писать»* и может покинуть канал из карточки информации о канале.

### AI Боты:
- [ ] При генерации ответа Gemini ботом статус «Печатает...» отображается в реальном времени из RTDB (`/typing/{chatId}/{botUid}`).
- [ ] Пока Gemini генерирует ответ, текстовое поле заблокировано (`disabled`), повторная отправка сообщений невозможна.
- [ ] Индикатор ожидания ответа бота вращается строго вокруг своего геометрического центра без дрожания и смещения.

### Пересылка сообщений:
- [ ] Пересылка сообщений работает в:
  - Любые личные диалоги (`direct`)
  - Группы и форумы
  - Каналы (только если текущий пользователь — админ)
  - «Избранное» (`savedMessages`)
- [ ] Если у канала/группы включена настройка `settings.noForwards == true`, кнопка пересылки скрыта или блокируется.
- [ ] В сообщении отображается корректный лейбл автора (`@username`) и источника.

### Инвайты и безопасность:
- [ ] При попытке регистрации без инвайт-кода приложение предлагает кнопку «Запросить доступ» или ввод кода.
- [ ] При переходе по ссылке `visorlink.org/invite?code=...` код корректно считывается из Intent и валидируется.
- [ ] При создании бота и вебхука токен выводится в модальном диалоге с надежной кнопкой копирования в буфер обмена.

### Музыка и аудиоплеер:
- [ ] При отправке аудиофайла можно изменить название, имя автора и загрузить обложку (встраиваются прямо в файл через ID3 теги).
- [ ] Бабл аудиосообщения имеет правильную ширину (не сжимается до 260px) и не обрезает элементы интерфейса.
- [ ] Длинные названия треков и авторов обрезаются аккуратным троеточием (`ellipsis`) без поломки верстки.
- [ ] Плеер имеет четкую круглую кнопку воспроизведения/паузы со статусом загрузки (буферизации).
- [ ] При воспроизведении трека обложка вращается по кругу (виниловый диск).
- [ ] Переключение скорости `1x / 1.5x / 2x` работает корректно и оформлено в едином стиле приложения.
- [ ] При старте трека над строкой ввода открывается компактная плашка `AudioPlaybackDockBar`, позволяющая управлять треком из любого места чата и переходить к сообщению по тапу.


