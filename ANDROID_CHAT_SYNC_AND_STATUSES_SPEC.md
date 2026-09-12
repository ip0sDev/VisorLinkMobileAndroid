# VisorLink Android Client — Спецификация интеграции: Синхронизация сообщений, статусы прочтения и индикаторы ввода

Документация для Android-разработчиков (**Kotlin**, **Jetpack Compose**, **Firebase Firestore & RTDB SDK**, **Coroutines & StateFlow**) по внедрению архитектурных улучшений VisorLink:
1. **Монотонная последовательность сообщений (`seq`) и алгоритм гибридной сортировки (Monotonic Sequence & Backward-Compatible Sorting)**.
2. **Галочки прочтения в списке чатов (Read Receipts in Chat List / Sidebar)** с гарантией **0 лишних чтений Firestore**.
3. **Глобальный статус «Печатает...» в списке чатов (Multi-Chat Typing Indicators)** через единый слушатель RTDB / WebSocket.
4. **Режим отладки (Debug Mode): отображение ID сообщений и ID пользователей**.

---

## 1. Архитектурные принципы и лимиты квот (Quota & Cost Efficiency)

> [!IMPORTANT]
> **Критическое правило экономии Cloud Firestore:**
> Категорически запрещено открывать `addSnapshotListener` или запрашивать подколлекции `messages` для каждого чата на главном экране списка чатов! Если у пользователя 50 чатов, это приведёт к сотням и тысячам чтений Firestore при каждом старте приложения и быстро исчерпает квоты (Spark/Blaze limits).

### Архитектурное решение:
1. **Все метаданные последнего сообщения инкапсулируются в документ `/chats/{chatId}`**:
   - `lastMessageSenderId: String?`
   - `lastMessage: Map<String, Any>` (`text`, `senderId`, `senderUsername`, `readBy`)
   - `unreadCount: Map<String, Long>`
   - `lastSeq: Long`
2. **Статусы «Печатает...»** передаются через **Firebase Realtime Database (RTDB)** или WebSocket. RTDB бесплатен по числу операций чтения/записи (тарифицируется только трафик, составляющий доли килобайта) и использует **один глобальный слушатель** на всё приложение.

---

## 2. Секция 1: Монотонный `seq` и гибридная сортировка

### 2.1. Проблема и модель данных
При нестабильном мобильном интернете (Edge/3G, потеря пакетов) сообщения, отправленные через `serverTimestamp()`, могут записываться в базу с задержкой или с локальным рассинхроном системного времени устройств.
Для обеспечения строгого порядка введена монотонно возрастающая последовательность `seq`.

#### Схема полей Firestore:
- **В документе чата `/chats/{chatId}`**:
  - `lastSeq: Long` (целое число >= 1, хранит последний выданный номер в данном чате).
- **В документе сообщения `/chats/{chatId}/messages/{messageId}`**:
  - `seq: Long?` (порядковый номер сообщения внутри конкретного чата).
  - `createdAt: Timestamp` (стандартный серверный таймстамп).

```kotlin
data class Chat(
    val id: String = "",
    val name: String = "",
    val lastSeq: Long = 0L,
    val lastMessageSenderId: String? = null,
    val lastMessage: LastMessage? = null,
    val unreadCount: Map<String, Long> = emptyMap(),
    // ... остальные поля
)

data class Message(
    val id: String = "",
    val seq: Long? = null, // null для старых сообщений (legacy)
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val readBy: List<String> = emptyList(),
    val isSending: Boolean = false // Оптимистичное состояние
)
```

---

### 2.2. Расчёт `nextSeq` при отправке сообщения

Перед записью сообщения в Firestore клиент вычисляет следующий `seq`:

```kotlin
fun calculateNextSeq(
    chat: Chat?,
    currentMessages: List<Message>
): Long {
    val chatLastSeq = chat?.lastSeq ?: 0L
    val maxMsgSeq = currentMessages.maxOfOrNull { it.seq ?: 0L } ?: 0L
    return maxOf(chatLastSeq, maxMsgSeq) + 1L
}
```

#### Запись в Firestore (Атомарный Batch):
```kotlin
fun sendMessage(
    chatId: String,
    currentUserId: String,
    currentUsername: String,
    text: String,
    isDirect: Boolean,
    otherUserId: String?,
    nextSeq: Long
) {
    val db = FirebaseFirestore.getInstance()
    val batch = db.batch()

    val msgRef = db.collection("chats").document(chatId).collection("messages").document()
    val chatRef = db.collection("chats").document(chatId)

    val messageData = hashMapOf(
        "seq" to nextSeq,
        "senderId" to currentUserId,
        "senderUsername" to currentUsername,
        "text" to text,
        "type" to "text",
        "createdAt" to FieldValue.serverTimestamp(),
        "readBy" to listOf(currentUserId),
        "deleted" to false
    )
    batch.set(msgRef, messageData)

    val lastMessageMap = hashMapOf(
        "text" to text,
        "senderId" to currentUserId,
        "senderUsername" to currentUsername,
        "readBy" to listOf(currentUserId)
    )

    val chatUpdates = hashMapOf<String, Any>(
        "lastMessage" to lastMessageMap,
        "lastMessageSenderId" to currentUserId,
        "lastMessageAt" to FieldValue.serverTimestamp(),
        "lastSeq" to nextSeq
    )

    // Если чат 1-на-1, инкрементируем счётчик непрочитанных у собеседника
    if (isDirect && !otherUserId.isNullOrBlank()) {
        chatUpdates["unreadCount.$otherUserId"] = FieldValue.increment(1)
    }

    batch.update(chatRef, chatUpdates)
    batch.commit()
}
```

---

### 2.3. Алгоритм гибридной сортировки (Hybrid Backward-Compatible Sorting)

> [!WARNING]
> У собеседника может быть установлена старая версия клиента, которая ещё не записывает `seq`. Старые сообщения также не имеют поля `seq` (`seq == null`).
> Если просто сортировать по `seq ?: 0`, старые сообщения окажутся в самом начале, а новые сообщения без `seq` — упадут под старые!

#### Эталонный алгоритм сортировки для Android:
1. Если у обоих сообщений есть валидный `seq`: сортируем по `seq`.
2. Если у одного или обоих сообщений нет `seq`: сортируем по `createdAt` (миллисекунды таймстампа).
3. При равенстве таймстампов или оптимистичных сообщениях используем стабильный тайбрейкер (порядковый `id` или флаг отправки).

```kotlin
fun sortMessages(messages: List<Message>): List<Message> {
    return messages.sortedWith { a, b ->
        val aTime = a.createdAt?.toDate()?.time ?: Long.MAX_VALUE
        val bTime = b.createdAt?.toDate()?.time ?: Long.MAX_VALUE

        val aSeq = a.seq
        val bSeq = b.seq

        when {
            // 1. Оба сообщения имеют номер последовательности seq
            aSeq != null && bSeq != null -> {
                val seqComp = aSeq.compareTo(bSeq)
                if (seqComp != 0) seqComp else aTime.compareTo(bTime)
            }
            // 2. Одно с seq, другое legacy (без seq)
            aSeq != null && bSeq == null -> {
                // Если разница по времени больше 2 секунд — ориентируемся на реальное время
                if (Math.abs(aTime - bTime) > 2000) {
                    aTime.compareTo(bTime)
                } else {
                    1 // Новое сообщение с seq ставится после legacy
                }
            }
            aSeq == null && bSeq != null -> {
                if (Math.abs(aTime - bTime) > 2000) {
                    aTime.compareTo(bTime)
                } else {
                    -1
                }
            }
            // 3. Оба сообщения старого формата (legacy без seq)
            else -> {
                val timeComp = aTime.compareTo(bTime)
                if (timeComp != 0) timeComp else a.id.compareTo(b.id)
            }
        }
    }
}
```

---

## 3. Секция 2: Галочки прочтения в списке чатов (Read Receipts)

### 3.1. Логика определения статуса последнего сообщения

В элементе списка чатов (`ChatListItem`) для последнего сообщения:
1. **Проверяем, является ли текущий пользователь автором последнего сообщения:**
   ```kotlin
   val isOwnLast = chat.lastMessageSenderId == currentUserId ||
       chat.lastMessage?.senderId == currentUserId
   ```
2. **Если `isOwnLast == true`, вычисляем статус прочтения (`isRead`):**
   ```kotlin
   fun isLastMessageRead(chat: Chat, currentUserId: String): Boolean {
       val lastMsg = chat.lastMessage ?: return false

       // Прямой флаг (для V2 backend или явного флага)
       if (lastMsg.read == true) return true

       // ID собеседника (для direct чата)
       val otherUserId = if (chat.type == "direct") {
           chat.participants.firstOrNull { it != currentUserId }
       } else null

       // 1. Собеседник присутствует в списке прочитавших readBy
       if (otherUserId != null && lastMsg.readBy.contains(otherUserId)) {
           return true
       }

       // 2. В readBy есть кто-либо кроме текущего пользователя
       if (lastMsg.readBy.any { it != currentUserId && it.isNotBlank() }) {
           return true
       }

       // 3. Счётчик непрочитанных у собеседника сброшен в 0
       if (otherUserId != null && chat.unreadCount[otherUserId] == 0L) {
           return true
       }

       return false
   }
   ```

### 3.2. Сброс непрочитанных и прочтение (При открытии чата)

Когда пользователь открывает экран переписки и читает входящие сообщения:

```kotlin
fun markChatMessagesAsRead(chatId: String, currentUserId: String, unreadMessages: List<Message>) {
    if (unreadMessages.isEmpty()) return

    val db = FirebaseFirestore.getInstance()
    val batch = db.batch()

    // 1. Обновляем сообщения
    for (msg in unreadMessages) {
        val ref = db.collection("chats").document(chatId).collection("messages").document(msg.id)
        batch.update(ref, "readBy", FieldValue.arrayUnion(currentUserId))
    }

    // 2. В ТОМ ЖЕ БАТЧЕ обновляем документ чата (0 лишних запросов!)
    val chatRef = db.collection("chats").document(chatId)
    val chatUpdates = hashMapOf<String, Any>(
        "unreadCount.$currentUserId" to 0L,
        "lastMessage.readBy" to FieldValue.arrayUnion(currentUserId)
    )
    batch.update(chatRef, chatUpdates)

    batch.commit().addOnFailureListener { e ->
        Log.e("ChatSync", "Failed to mark as read", e)
    }
}
```

> **Результат:** В ту же миллисекунду у собеседника, открывшего список чатов, срабатывает уже запущенный `addSnapshotListener` на коллекцию `chats`. Документ чата обновляется, и 1 галочка сменяется на 2 галочки без обращения к подколлекциям сообщений!

---

### 3.3. Jetpack Compose UI: Иконки статуса

```kotlin
@Composable
fun MessageStatusIcon(
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    if (isRead) {
        // Двойная галочка (✓✓)
        Icon(
            painter = painterResource(id = R.drawable.ic_check_double),
            contentDescription = "Прочитано",
            tint = MaterialTheme.colorScheme.primary, // Акцентный цвет
            modifier = modifier.size(16.dp, 11.dp)
        )
    } else {
        // Одинарная галочка (✓)
        Icon(
            painter = painterResource(id = R.drawable.ic_check_single),
            contentDescription = "Отправлено",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = modifier.size(13.dp, 11.dp)
        )
    }
}
```

#### Вектор `ic_check_single.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="14dp"
    android:height="11dp"
    android:viewportWidth="14"
    android:viewportHeight="11">
  <path
      android:pathData="M1,5.5L4.5,9L13,1"
      android:strokeWidth="1.8"
      android:strokeColor="#FFFFFFFF"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
```

#### Вектор `ic_check_double.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="18dp"
    android:height="11dp"
    android:viewportWidth="18"
    android:viewportHeight="11">
  <path
      android:pathData="M1,5.5L4.5,9L10,2"
      android:strokeWidth="1.8"
      android:strokeColor="#FFFFFFFF"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
  <path
      android:pathData="M7,5.5L10.5,9L16,2"
      android:strokeWidth="1.8"
      android:strokeColor="#FFFFFFFF"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
```

---

## 4. Секция 3: Статус «Печатает...» в списке чатов (RTDB Multi-Chat Typing)

### 4.1. Структура дерева в Firebase Realtime Database
- Путь: `/typing/{chatId}/{userId}`
- Значение:
  ```json
  {
    "uid": "user_123",
    "ts": 1773229988123
  }
  ```
- При начале ввода клиент пишет узел с `onDisconnect().removeValue()`.
- При остановке ввода или таймауте 3 сек узел удаляется: `removeValue()`.

### 4.2. Kotlin Менеджер: Слушатель всех чатов (`SidebarTypingManager`)

Вместо запуска N слушателей под каждый чат создаётся **1 единый слушатель** на узел `/typing`.

```kotlin
@Singleton
class SidebarTypingManager @Inject constructor(
    private val rtdb: FirebaseDatabase
) {
    private val _typingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingMap: StateFlow<Map<String, Boolean>> = _typingMap.asStateFlow()

    private var typingListener: ValueEventListener? = null
    private val typingRef = rtdb.getReference("typing")

    fun startListening(currentUserId: String) {
        if (typingListener != null) return

        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val resultMap = mutableMapOf<String, Boolean>()

                for (chatSnap in snapshot.children) {
                    val chatId = chatSnap.key ?: continue
                    var isSomeoneTyping = false

                    for (userSnap in chatSnap.children) {
                        val uid = userSnap.child("uid").getValue(String::class.java)
                        val ts = userSnap.child("ts").getValue(Long::class.java) ?: 0L

                        // Если печатает НЕ текущий пользователь и событие свежее (< 4 сек)
                        if (uid != null && uid != currentUserId && (now - ts < 4000L)) {
                            isSomeoneTyping = true
                            break
                        }
                    }

                    if (isSomeoneTyping) {
                        resultMap[chatId] = true
                    }
                }
                _typingMap.value = resultMap
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("SidebarTyping", "RTDB typing cancelled: ${error.message}")
            }
        }

        typingRef.addValueEventListener(typingListener!!)
    }

    fun stopListening() {
        typingListener?.let { typingRef.removeEventListener(it) }
        typingListener = null
        _typingMap.value = emptyMap()
    }
}
```

---

### 4.3. Compose UI: Рендеринг элемента списка чата

```kotlin
@Composable
fun ChatItemRow(
    chat: Chat,
    currentUserId: String,
    isTyping: Boolean,
    onClick: () -> Unit
) {
    val isOwnLast = chat.lastMessageSenderId == currentUserId ||
            chat.lastMessage?.senderId == currentUserId
    val isRead = remember(chat, currentUserId) {
        isLastMessageRead(chat, currentUserId)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        ChatAvatar(chat)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Верхняя строка: Имя и время
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = chat.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                chat.lastMessageAt?.let { ts ->
                    Text(
                        text = formatTime(ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Нижняя строка: Превью / Статус «Печатает...»
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTyping) {
                    // Анимированный статус печати
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "✍️", fontSize = 12.sp)
                        Text(
                            text = stringResource(R.string.chat_typing), // "печатает…"
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Если последнее сообщение своё — показываем галочки
                    if (isOwnLast) {
                        MessageStatusIcon(isRead = isRead)
                        Spacer(modifier = Modifier.width(5.dp))
                    }

                    Text(
                        text = chat.lastMessage?.text ?: stringResource(R.string.no_messages),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Счётчик непрочитанных (если > 0 и сообщение не наше)
                val unread = chat.unreadCount[currentUserId] ?: 0L
                if (unread > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UnreadBadge(count = unread)
                }
            }
        }
    }
}
```

---

## 5. Секция 4: Режим отладки (Debug Mode IDs)

В настройках разработчика VisorLink доступен переключатель **«Отображать ID сообщений и пользователей»**.

### 5.1. Хранение настройки
Использовать `DataStore<Preferences>`:
```kotlin
val SHOW_DEBUG_IDS_KEY = booleanPreferencesKey("debug_show_ids")
```

### 5.2. Отображение в UI
1. **В пузыре сообщения (`MessageBubble`):**
   Если флаг включён, под текстом или в метаданных выводится ID и seq:
   ```kotlin
   if (showDebugIds) {
       Text(
           text = "ID: ${message.id.take(8)} | seq: ${message.seq ?: "legacy"}",
           style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
           color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
           fontSize = 9.sp
       )
   }
   ```
2. **В профиле пользователя (`ProfileBottomSheet` / `ProfileScreen`):**
   Отображается плашка с UID пользователя и возможностью скопировать по клику:
   ```kotlin
   if (showDebugIds) {
       Surface(
           shape = RoundedCornerShape(8.dp),
           color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
           modifier = Modifier.clickable {
               clipboardManager.setText(AnnotatedString(user.uid))
               showToast("UID скопирован")
           }
       ) {
           Text(
               text = "UID: ${user.uid}",
               fontFamily = FontFamily.Monospace,
               fontSize = 11.sp,
               modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
           )
       }
   }
   ```

---

## 6. Чеклист проверки для разработчика / Mobile Agent

- [ ] **NextSeq Calculation:** При отправке сообщения `seq` вычисляется как `max(chat.lastSeq, max(localSeqs)) + 1`.
- [ ] **Batch Write:** При отправке сообщения в `chats/{chatId}` атомарно обновляются `lastSeq`, `lastMessageSenderId`, `lastMessage.readBy = [currentUserId]` и инкрементируется `unreadCount.$otherUserId`.
- [ ] **Hybrid Sorting:** Сообщения в списке чата сортируются гибридной функцией: сообщения с `seq` упорядочены строго, а legacy-сообщения без `seq` не вызывают инверсий и не проваливаются вниз.
- [ ] **Zero Extra Reads on Read Receipt:** При открытии экрана чата прочтение обновляет `messages` и `chats/{chatId}` в едином батче. Список чатов получает обновление через существующий слушатель коллекции `chats`.
- [ ] **1 checkmark vs 2 checkmarks:** Если последнее сообщение отправлено текущим пользователем:
  - 1 серая галочка — если сообщение не прочитано собеседником.
  - 2 цветные галочки — если сообщение прочитано.
- [ ] **RTDB Typing Multi-Chat:** В списке чатов работает ровно 1 слушатель узла `typing`. При вводе текста собеседником строка превью сменяется на `✍️ печатает…` и возвращается обратно по истечении 3–4 секунд.
