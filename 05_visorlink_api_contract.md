# VisorLink Backend — Полная документация API-контракта

> **Base URL:** `https://backend.visorlink.org`
> **Все маршруты доступны как с префиксом `/api`, так и без него** (оба варианта эквивалентны).
> Например: `GET /users/me` ≡ `GET /api/users/me`

---

## Содержание

1. [Аутентификация](#1-аутентификация)
2. [Формат ошибок](#2-формат-ошибок)
3. [Health Check](#3-health-check)
4. [Users API](#4-users-api)
5. [2FA API](#5-2fa-api)
6. [Chats API](#6-chats-api)
7. [Messages API](#7-messages-api)
8. [Gifts API](#8-gifts-api)
9. [Stickers API](#9-stickers-api)
10. [Invites & Notifications API](#10-invites--notifications-api)
11. [Discover Feed API](#11-discover-feed-api)
12. [DM Bots API](#12-dm-bots-api)
13. [Webhooks API (External Bots)](#13-webhooks-api-external-bots)
14. [Telegram Integration API](#14-telegram-integration-api)
15. [Client Attestation API](#15-client-attestation-api)
16. [Incidents API (Status Page)](#16-incidents-api-status-page)
17. [Feature Flags & Internal Docs](#17-feature-flags--internal-docs)
18. [Admin API](#18-admin-api)
19. [WebSocket Protocol](#19-websocket-protocol)
20. [Data Models (DTO Reference)](#20-data-models-dto-reference)

---

## 1. Аутентификация

Все защищённые эндпоинты используют **Firebase Authentication**. Необходимо передавать Firebase ID Token в заголовке:

```
Authorization: Bearer <Firebase_ID_Token>
```

> [!IMPORTANT]
> Токен верифицируется через Firebase Admin SDK на сервере. Если токен невалидный или истёк, сервер вернёт `401 Unauthorized`.

**Дополнительные заголовки:**
| Заголовок | Назначение |
|---|---|
| `Authorization` | `Bearer <Firebase_ID_Token>` — основная аутентификация |
| `X-Admin-Secret` | Секрет для эскалации прав администратора (при `/admin/claim`) |
| `X-Forwarded-For` | IP клиента (используется для 2FA логирования) |
| `User-Agent` | User-Agent клиента (используется для 2FA логирования) |

---

## 2. Формат ошибок

Все ошибки возвращаются в едином формате JSON:

```json
{ "error": "Описание ошибки" }
```

| HTTP Code | Исключение | Описание |
|---|---|---|
| `400` | `BadRequestException` | Невалидные параметры запроса |
| `401` | `UnauthorizedException` | Невалидный или отсутствующий токен |
| `403` | `ForbiddenException` | Нет прав доступа |
| `404` | `NotFoundException` | Ресурс не найден |
| `429` | `TooManyRequestsException` | Кулдаун — слишком частые запросы (сообщения: 500мс) |
| `500` | `Throwable` | Внутренняя ошибка сервера (с `traceId`) |

Ответ 500:
```json
{ "error": "Internal server error", "traceId": "a1b2c3d4" }
```

---

## 3. Health Check

### `GET /health`
**Auth:** Не требуется

**Response `200 OK`:**
```json
{
  "status": "healthy",      // "healthy" | "degraded"
  "database": true,
  "redis": true
}
```

При `status: "degraded"` → HTTP `503 Service Unavailable`

---

## 4. Users API

### `GET /users`
Поиск или список пользователей.

**Auth:** ✅ Required

| Query Param | Type | Default | Description |
|---|---|---|---|
| `q` / `query` | `string` | `""` | Поисковый запрос |
| `limit` | `int` | `50` | Макс. результатов (1–100) |
| `raw` | `string` | — | Если `"true"` — вернёт голый `List<UserDto>` |

**Response `200 OK`** (по умолчанию):
```json
{
  "users": [UserDto, ...],
  "data": [UserDto, ...]   // дублирует users для совместимости
}
```

---

### `GET /users/search`
Аналогичен `GET /users` — тот же контракт, тот же ответ.

---

### `GET /users/me`
Получить профиль текущего пользователя. Если профиль отсутствует — автоматически создаётся.

**Auth:** ✅ Required

**Response `200 OK`:** [`UserDto`](#userdto)

---

### `POST /users/sync`
Синхронизация/инициализация профиля после Firebase Auth sign-up / sign-in.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "username": "string?",           // опциональное имя пользователя
  "displayName": "string?",       // отображаемое имя
  "avatarUrl": "string?",         // URL аватара
  "registeredViaOfficialClient": true  // default: true
}
```

> Все поля опциональны. Тело запроса может быть пустым `{}`.

**Response `200 OK`:** [`UserDto`](#userdto)

---

### `GET /users/{uid}`
Получить публичный профиль другого пользователя.

**Auth:** ✅ Required

| Path Param | Type | Description |
|---|---|---|
| `uid` | `string` | UID целевого пользователя |

**Response `200 OK`:** [`UserDto`](#userdto)
**Response `404`:** `{ "error": "User not found" }`

---

### `PUT /users/me/profile`
Обновить профиль текущего пользователя.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "displayName": "string?",
  "bio": "string?",
  "avatarUrl": "string?",
  "tfaEnabled": true,              // bool?
  "showStreak": true,              // bool?
  "customization": {},             // JSON object?
  "acceptedAt": 1693000000000,     // epoch ms?
  "acceptedVersion": "1.0"         // string?
}
```

**Response `200 OK`:** [`UserDto`](#userdto)

---

### `PUT /users/me/username`
Изменить username текущего пользователя.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "username": "new_name",       // одно из двух полей обязательно
  "newUsername": "new_name",    // альтернативное поле
  "displayName": "string?"     // опционально обновить displayName
}
```

**Response `200 OK`:** [`UserDto`](#userdto)

---

### `GET /users/check-username/{username}`
Проверить доступность username.

**Auth:** Не требуется

| Path Param | Type | Description |
|---|---|---|
| `username` | `string` | Проверяемый username |

**Response `200 OK`:**
```json
{
  "username": "test_user",
  "available": true
}
```

---

### `POST /users/me/fcm-token`
Зарегистрировать FCM push-токен для мобильных уведомлений.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "token": "fcm_token_string"
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /users/me/ntfy-topic`
Зарегистрировать ntfy-топик для десктопных уведомлений.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "token": "ntfy_topic_string"
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /users/me/streak`
Получить ежедневный стрик и бонусные биты.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "success": true,
  "claimed": true,
  "streak": 7,
  "bits": 150,
  "message": "Стрик обновлён!",
  "hoursUntilNext": 18     // null если уже можно клеймить
}
```

---

### `POST /users/me/buy-pro`
Купить или активировать PRO / Trial подписку.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "useTrial": false    // default: false — если true, активирует бесплатный пробный период
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "proUntil": 1700000000000,    // epoch ms
  "proUntilIso": "2025-01-01T00:00:00Z",
  "bits": 100,
  "message": "PRO активирован!"
}
```

---

## 5. 2FA API

### `POST /auth/2fa/request`
Запросить код двухфакторной аутентификации.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "method": "email"    // default: "email"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "method": "email",
  "expiresIn": 300     // секунд
}
```

---

### `POST /auth/2fa/verify`
Верифицировать 2FA код.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "code": "123456"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "verified": true
}
```

---

## 6. Chats API

### `GET /chats`
Список чатов текущего пользователя.

**Auth:** ✅ Required

| Query Param | Type | Default | Description |
|---|---|---|---|
| `raw` | `string` | — | Если `"true"` → голый `List<ChatDto>` |

**Response `200 OK`** (по умолчанию):
```json
{
  "chats": [ChatDto, ...],
  "data": [ChatDto, ...]
}
```

---

### `POST /chats/create` (или `POST /chats`)
Создать новый чат (direct / group / channel).

**Auth:** ✅ Required

**Request Body:**
```json
{
  "type": "group",                // "direct" | "group" | "channel" (default: "direct")
  "targetUid": "uid_string",     // для direct — UID собеседника
  "participantUid": "uid_string", // алиас для targetUid
  "name": "Название чата",       // для group/channel
  "tag": "@channel_tag",          // уникальный тег канала
  "description": "Описание",
  "avatarUrl": "https://...",
  "participantIds": ["uid1", "uid2"],  // для групп
  "isForum": false
}
```

**Response `201 Created`:** [`ChatDto`](#chatdto)

---

### `POST /chats/direct`
Создать direct чат (type принудительно = `"direct"`). Контракт = `POST /chats/create`.

---

### `GET /chats/find-by-tag/{tag}`
Найти чат по тегу.

**Auth:** ✅ Required

| Path Param | Type | Description |
|---|---|---|
| `tag` | `string` | Тег канала (без `@`) |

**Response `200 OK`:** [`ChatDto`](#chatdto)

---

### `POST /chats/join/invite`
Присоединиться по инвайт-коду.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "inviteCode": "abc123xyz"
}
```

**Response `200 OK`:** [`ChatDto`](#chatdto)

---

### `POST /chats/join/tag`
Присоединиться по тегу.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "tag": "channel_tag"
}
```

**Response `200 OK`:** [`ChatDto`](#chatdto)

---

### `GET /chats/{chatId}`
Получить детали чата.

**Auth:** ✅ Required

**Response `200 OK`:** [`ChatDto`](#chatdto)

---

### `PUT /chats/{chatId}/settings`
Обновить настройки чата (требуются права администратора чата).

**Auth:** ✅ Required

**Request Body:**
```json
{
  "name": "string?",
  "tag": "string?",
  "description": "string?",
  "avatarUrl": "string?",
  "joinByLink": true,         // bool?
  "joinByTag": false,         // bool?
  "allowReactions": true,     // bool?
  "allowComments": true,      // bool?
  "noForwards": false,        // bool?
  "botAllowed": false         // bool?
}
```

**Response `200 OK`:** [`ChatDto`](#chatdto)

---

### `POST /chats/{chatId}/leave`
Покинуть чат.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `DELETE /chats/{chatId}`
Удалить чат (требуются права владельца).

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `GET /chats/{chatId}/members`
Список участников чата.

**Auth:** ✅ Required

**Response `200 OK`:** `List<ChatMemberDto>`
```json
[
  {
    "chatId": "chat_123",
    "userId": "uid_abc",
    "username": "john",
    "displayName": "John Doe",
    "avatarUrl": "https://...",
    "role": "owner",          // "owner" | "admin" | "member"
    "joinedAt": 1700000000000,
    "muted": false,
    "banned": false
  }
]
```

---

### `POST /chats/{chatId}/moderate`
Модерация участника (mute, unmute, ban, unban, kick).

**Auth:** ✅ Required (admin/owner)

**Request Body:**
```json
{
  "targetUid": "uid_string",
  "action": "mute",              // "mute" | "unmute" | "ban" | "unban" | "kick"
  "durationSeconds": 3600        // опционально, для "mute"
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `PUT /chats/{chatId}/members/{targetUid}/role`
Установить роль участника.

**Auth:** ✅ Required (admin/owner)

**Request Body:**
```json
{
  "targetUid": "uid_string",
  "role": "admin"               // "owner" | "admin" | "member"
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /chats/{chatId}/invite-link/regenerate`
Перегенерировать инвайт-ссылку чата.

**Auth:** ✅ Required (admin/owner)

**Response `200 OK`:**
```json
{
  "inviteLink": "new_invite_code"
}
```

---

### `POST /chats/{chatId}/allow-bot`
Разрешить бот-сообщения в чате.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /chats/{chatId}/invite-user`
Пригласить пользователя в чат по UID или username.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "targetUid": "uid_string",           // одно из двух
  "targetUsername": "@john_doe"          // можно с @, можно без
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### Channel Bot Keys

#### `GET /chats/{chatId}/bot-keys`
Список ключей канальных ботов.

**Auth:** ✅ Required

**Response `200 OK`:** `List<ChannelBotKeyDto>`

---

#### `POST /chats/{chatId}/bot-keys`
Создать ключ канального бота.

**Auth:** ✅ Required (admin/owner)

**Request Body:**
```json
{
  "label": "My Bot"     // default: "Bot"
}
```

**Response `201 Created`:** [`ChannelBotKeyDto`](#channelbotkeysdto) (с полем `apiKey`)

---

#### `DELETE /chats/{chatId}/bot-keys/{keyId}`
Отозвать ключ канального бота.

**Auth:** ✅ Required (admin/owner)

**Response `200 OK`:**
```json
{ "success": true }
```

---

#### `POST /chats/{chatId}/bot-keys/{keyId}/regenerate`
Перегенерировать ключ канального бота.

**Auth:** ✅ Required (admin/owner)

**Response `200 OK`:** [`ChannelBotKeyDto`](#channelbotkeysdto) (с новым `apiKey`)

---

### Wallpapers (Stub)

> [!NOTE]
> Wallpapers эндпоинты в текущей версии являются заглушками и всегда возвращают пустые ответы.

| Method | Path | Response |
|---|---|---|
| `GET` | `/chats/{chatId}/wallpapers` | `{}` |
| `PUT` | `/chats/{chatId}/wallpapers/{docId}` | `{ "success": true }` |
| `DELETE` | `/chats/{chatId}/wallpapers/{docId}` | `{ "success": true }` |

---

## 7. Messages API

### `GET /chats/{chatId}/messages`
Получить сообщения чата (пагинация курсором).

**Auth:** ✅ Required

| Query Param | Type | Default | Description |
|---|---|---|---|
| `limit` | `int` | `50` | Количество сообщений |
| `before` | `string` | — | ID сообщения — загрузить старее этого |

**Response `200 OK`:** `List<MessageDto>`

---

### `POST /chats/{chatId}/messages`
Отправить сообщение в чат.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "chatId": "",                    // можно опустить — берётся из URL
  "type": "text",                  // "text"|"image"|"voice"|"sticker"|"album"|"gift"|"file"
  "text": "Привет!",
  "caption": "Подпись к медиа",
  "replyToId": "msg_id",          // ID цитируемого сообщения
  "cdnMediaId": "cdn_123",        // ID медиа в CDN
  "url": "https://...",           // URL медиафайла
  "fileName": "photo.jpg",
  "duration": 15,                  // длительность аудио/видео (сек)
  "stickerId": "stk_123",
  "spoiler": false,
  "images": [                      // для type = "album"
    {
      "cdnMediaId": "cdn_456",
      "url": "https://...",
      "fileName": "img1.jpg",
      "spoiler": false
    }
  ],
  "forwardFrom": {                 // для пересланных сообщений
    "senderId": "uid_orig",
    "senderUsername": "orig_user",
    "chatId": "orig_chat"
  },
  "giftType": "premium_box",      // для type = "gift"
  "topicId": "topic_123"          // для форумных чатов
}
```

**Response `201 Created`:** [`MessageDto`](#messagedto)

---

### `POST /messages/send`
**Top-level alias** — альтернативный эндпоинт для отправки сообщений (для совместимости с мобильными клиентами).

**Auth:** ✅ Required

**Request/Response:** Идентично `POST /chats/{chatId}/messages`, но `chatId` обязателен в теле запроса.

---

### `POST /chats/{chatId}/messages/album`
Отправить альбом (несколько изображений).

**Auth:** ✅ Required

**Request Body:**
```json
{
  "images": [
    {
      "cdnMediaId": "cdn_123",
      "url": "https://...",
      "fileName": "img.jpg",
      "spoiler": false
    }
  ],
  "caption": "Описание альбома",
  "replyTo": {                    // опционально
    "id": "msg_123",
    "type": "text",
    "text": "Текст оригинала",
    "url": null,
    "senderUsername": "john"
  }
}
```

**Response `201 Created`:** [`MessageDto`](#messagedto)

---

### `PUT /chats/{chatId}/messages/{msgId}`
Отредактировать сообщение. Допускается в течение 30 минут после отправки.

**Auth:** ✅ Required (только автор)

**Request Body:**
```json
{
  "text": "Отредактированный текст",
  "caption": "Новая подпись"
}
```

**Response `200 OK`:** [`MessageDto`](#messagedto)

---

### `DELETE /chats/{chatId}/messages/{msgId}`
Удалить сообщение.

**Auth:** ✅ Required (автор или admin/owner чата)

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `PUT /chats/{chatId}/messages/{msgId}/reactions`
Переключить реакцию на сообщении (toggle: поставить/снять).

**Auth:** ✅ Required

**Request Body:**
```json
{
  "emoji": "❤️"
}
```

**Response `200 OK`:** [`MessageDto`](#messagedto) (с обновлённым `reactions`)

---

### `PUT /chats/{chatId}/messages/{msgId}/read`
Пометить сообщение как прочитанное.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /chats/{chatId}/messages/{msgId}/redeem-gift`
Активировать подарочное сообщение.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_123",
  "message": MessageDto
}
```

---

### Comments (на сообщениях)

#### `GET /chats/{chatId}/messages/{msgId}/comments`
Получить комментарии к сообщению.

**Auth:** ✅ Required

**Response `200 OK`:** `List<CommentDto>`

---

#### `POST /chats/{chatId}/messages/{msgId}/comments`
Добавить комментарий к сообщению.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "type": "text",             // "text"|"image"|"voice"|"sticker"|"file"
  "text": "Мой комментарий",
  "url": null,
  "fileName": null,
  "duration": null,
  "stickerId": null,
  "replyToId": "comment_id",  // ответ на другой комментарий
  "spoiler": false
}
```

**Response `201 Created`:** [`CommentDto`](#commentdto)

---

## 8. Gifts API

### `POST /gifts/send`
Отправить подарок (PRO подписка) в чат.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "chatId": "chat_123",
  "giftType": "premium_box",     // default: "premium_box"
  "text": "🎁 Подарок!"          // опциональный текст
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_456",
  "message": MessageDto
}
```

---

### `POST /gifts/{messageId}/redeem`
Активировать подарок.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_456",
  "message": MessageDto     // с redeemed = true
}
```

---

## 9. Stickers API

### `GET /stickers/my` (или `GET /sticker-packs`)
Получить стикерпаки текущего пользователя.

**Auth:** ✅ Required

**Response `200 OK`:** `List<StickerPackDto>`

---

### `POST /sticker-packs`
Создать новый стикерпак.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "name": "Мои стикеры",
  "emoji": "📦",               // default: "📦"
  "stickers": [
    {
      "url": "https://cdn.visorlink.org/sticker1.webp",
      "emoji": "😀",
      "name": "happy"
    }
  ]
}
```

**Response `201 Created`:** [`StickerPackDto`](#stickerpackdto)

---

### `POST /sticker-packs/{packId}/install`
Установить стикерпак текущему пользователю.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `DELETE /sticker-packs/{packId}`
Удалить стикерпак (только автор).

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

## 10. Invites & Notifications API

### `GET /invites`
Список приглашений текущего пользователя.

**Auth:** ✅ Required

**Response `200 OK`:** `List<InviteDto>`

---

### `POST /invites/{id}/respond`
Ответить на приглашение (принять / отклонить).

**Auth:** ✅ Required

**Request Body:**
```json
{
  "action": "accept"    // "accept" | "decline"
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `GET /notifications`
Список уведомлений текущего пользователя.

**Auth:** ✅ Required

**Response `200 OK`:** `List<NotificationDto>`

---

## 11. Discover Feed API

### `GET /feed`
Получить ленту рекомендаций.

**Auth:** ✅ Required

| Query Param | Type | Default | Description |
|---|---|---|---|
| `limit` | `int` | `30` | Количество записей |

**Response `200 OK`:** `List<DiscoverFeedDto>`

---

### `POST /feed/{id}/like`
Лайкнуть / снять лайк с записи (toggle).

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "liked": true    // текущее состояние
}
```

---

### `POST /feed/{id}/toggle-comments`
Включить / отключить комментарии к посту.

**Auth:** ✅ Required (только автор поста)

**Response `200 OK`:**
```json
{
  "commentsEnabled": false
}
```

---

## 12. DM Bots API

### `GET /bots/dm`
Список DM-ботов текущего пользователя.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "bots": [DmBotDto, ...]
}
```

---

### `POST /bots/dm`
Создать нового DM-бота.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "name": "My Bot",
  "username": "my_cool_bot"
}
```

**Response `201 Created`:**
```json
{
  "botUid": "bot_uid_123",
  "botToken": "vldm_abc123...",   // ⚠️ Показывается ТОЛЬКО при создании!
  "username": "my_cool_bot"
}
```

---

### `POST /bots/dm/allow`
Разрешить боту отправлять сообщения в чат.

**Auth:** ✅ Required

**Request Body:**
```json
{
  "chatId": "chat_id"    // default: ""
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `PUT /bots/dm/{botUid}`
Редактировать бота (имя, аватар).

**Auth:** ✅ Required (только владелец)

**Request Body:**
```json
{
  "name": "New Name",
  "avatarUrl": "https://..."
}
```

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `DELETE /bots/dm/{botUid}`
Удалить DM-бота.

**Auth:** ✅ Required (только владелец)

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /bots/dm/{botUid}/regenerate-token`
Перегенерировать токен бота.

**Auth:** ✅ Required (только владелец)

**Response `200 OK`:**
```json
{
  "botToken": "vldm_new_token..."
}
```

---

## 13. Webhooks API (External Bots)

### `POST /webhooks/dm-bot`
Отправить сообщение через DM-бота (webhook).

**Auth:** Токен бота через заголовок:
```
X-Bot-Token: vldm_abc123...
```
или:
```
Authorization: Bearer vldm_abc123...
```

**Request Body:**
```json
{
  "targetUsername": "john_doe",
  "text": "Привет от бота!"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_123"
}
```

---

### `POST /webhooks/channel-bot`
Отправить сообщение в канал через Channel Bot.

**Auth:** API-ключ через заголовок:
```
Authorization: Bearer vlck_xyz789...
```
или:
```
X-API-Key: vlck_xyz789...
```

**Request Body:**
```json
{
  "chatId": "channel_id",
  "text": "Новый пост!",
  "preview": "Превью поста"    // опционально
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_456"
}
```

---

## 14. Telegram Integration API

### `POST /telegram/generate-code`
Сгенерировать код привязки Telegram аккаунта.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "code": "VL-ABC123",
  "botUsername": "VisorLinkBot"
}
```

---

### `POST /telegram/unbind` (или `DELETE /telegram/unbind`)
Отвязать Telegram аккаунт.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{ "success": true }
```

---

## 15. Client Attestation API

### `POST /client/verify-attestation`
Верификация клиента (stub, всегда `true`).

**Auth:** Не требуется

**Response `200 OK`:**
```json
{ "verified": true }
```

---

## 16. Incidents API (Status Page)

### `GET /incidents`
Список инцидентов (публичный).

**Auth:** Не требуется

| Query Param | Type | Description |
|---|---|---|
| `raw` | `string` | Если `"true"` → голый `List<IncidentDto>` |

**Response `200 OK`** (по умолчанию):
```json
{
  "incidents": [IncidentDto, ...],
  "data": [IncidentDto, ...]
}
```

---

### `POST /incidents/report`
Создать репорт об инциденте.

**Auth:** Не требуется

**Request Body:**
```json
{
  "service": "api-gateway",        // max 64 символа
  "errorTelemetry": "stack trace"  // max 2000 символов, опционально
}
```

**Response `201 Created`:** [`IncidentDto`](#incidentdto)

---

### `POST /incidents/{id}/resolve`
Разрешить инцидент.

**Auth:** ✅ Required (System Admin)

**Response `200 OK`:**
```json
{ "success": true }
```

---

## 17. Feature Flags & Internal Docs

### `GET /feature-flags`
Получить все фиче-флаги.

**Auth:** Не требуется

**Response `200 OK`:** `Map<String, String>` — объект `{ key: value }`

---

### `GET /internal/{key}`
Получить внутренний документ по ключу.

**Публичные ключи** (без авторизации): `legal_tos`, `legal_privacy_policy`, `about`, `rules`, `terms`
**Остальные ключи:** ✅ Required (System Admin)

**Response `200 OK`:**
```json
{
  "key": "legal_tos",
  "content": "# Текст документа..."
}
```

**Response `404`:**
```json
{ "error": "Document not found" }
```

---

## 18. Admin API

> [!CAUTION]
> Все эндпоинты (кроме `/admin/check` и `/admin/claim`) требуют прав системного администратора.

### `GET /admin/check`
Проверить наличие прав администратора.

**Auth:** ✅ Required

**Response `200 OK`:**
```json
{
  "isAdmin": true,
  "uid": "my_uid"
}
```

---

### `POST /admin/claim`
Получить права первого администратора.

**Auth:** ✅ Required + `X-Admin-Secret` (если в БД уже есть администраторы)

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Admin status granted to uid_123"
}
```

---

### `GET /admin/stats`
Общая статистика платформы.

**Auth:** ✅ System Admin

**Response `200 OK`:**
```json
{
  "totalUsers": 1500,
  "totalChats": 350,
  "totalMessages": 45000,
  "onlineUsers": 42
}
```

---

### `POST /admin/set-admin`
Выдать / снять права администратора.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "targetUid": "uid_string",
  "isAdmin": true              // default: true
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "targetUid": "uid_string",
  "isAdmin": true
}
```

---

### `POST /admin/users/ban`
Глобальный бан / разбан пользователя.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "targetUid": "uid_string",
  "banned": true,             // default: true
  "reason": "Spam"            // опционально
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "targetUid": "uid_string",
  "banned": true
}
```

---

### `POST /admin/verify-email`
Верифицировать email пользователя.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "targetUid": "uid_string"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "targetUid": "uid_string",
  "emailVerified": true
}
```

---

### `POST /admin/verify-channel`
Верифицировать канал (выдать бейдж).

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "chatId": "channel_id",
  "badge": "official"          // "official" | "verified" — default: "official"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "chatId": "channel_id",
  "badge": "official"
}
```

---

### `PUT /admin/bots/{botUid}`
Редактировать бота от имени администратора.

**Auth:** ✅ System Admin

**Request Body:** `EditDmBotRequest` (same as `PUT /bots/dm/{botUid}`)

**Response `200 OK`:**
```json
{ "success": true }
```

---

### `POST /admin/bots/{botUid}/ban`
Забанить бота.

**Auth:** ✅ System Admin

**Response `200 OK`:**
```json
{ "success": true, "banned": true }
```

---

### `POST /admin/bots/{botUid}/revoke`
Отозвать токен бота.

**Auth:** ✅ System Admin

**Response `200 OK`:**
```json
{ "success": true, "botToken": "vldm_new_token..." }
```

---

### `POST /admin/verify-channel-bot`
Верифицировать ключ канального бота.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "keyId": "key_123",
  "badge": "official"     // default: "official"
}
```

**Response `200 OK`:**
```json
{ "success": true, "badge": "official" }
```

---

### `POST /admin/grant-eternal-pro`
Выдать PRO-подписку пользователю (по умолчанию до 2099 года).

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "targetUid": "uid_string",
  "days": 36500              // default: 36500 (~100 лет); 0 = до 2099
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "targetUid": "uid_string",
  "proUntil": 4102444799000,
  "proUntilIso": "2099-12-31T23:59:59Z"
}
```

---

### `POST /admin/send-gift`
Отправить подарок в чат от имени администрации.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "chatId": "chat_123",
  "giftType": "premium_box",      // default: "premium_box"
  "text": "🎁 Подарок от админа!"  // опционально
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "messageId": "msg_789",
  "message": MessageDto
}
```

---

### `POST /admin/feature-flags`
Создать / обновить фиче-флаг.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "key": "new_feature_enabled",
  "value": "true",
  "description": "Включает новую фичу"  // опционально
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "key": "new_feature_enabled",
  "value": "true"
}
```

---

### `DELETE /admin/feature-flags/{key}`
Удалить фиче-флаг.

**Auth:** ✅ System Admin

**Response `200 OK`:**
```json
{
  "success": true,
  "deletedKey": "old_feature"
}
```

---

### `PUT /admin/incidents/{id}`
Редактировать инцидент.

**Auth:** ✅ System Admin

**Request Body:**
```json
{
  "service": "api-gateway",
  "errorTelemetry": "Updated trace"
}
```

**Response `200 OK`:**
```json
{ "success": true, "id": "incident_id" }
```

---

### `DELETE /admin/incidents/{id}`
Удалить инцидент.

**Auth:** ✅ System Admin

**Response `200 OK`:**
```json
{ "success": true, "deletedId": "incident_id" }
```

---

### Migration Endpoints (Admin)

> [!WARNING]
> Миграционные эндпоинты — одноразовые утилиты для переноса данных из Firestore. Не предназначены для мобильного клиента.

| Method | Path | Description |
|---|---|---|
| `GET/POST` | `/admin/migrate-users` | Миграция пользователей из Firestore |
| `GET/POST` | `/admin/migrate-chats?limit=N` | Миграция чатов (limit — сообщений на чат, default 20) |
| `GET/POST` | `/admin/migrate-chats-full` | Полная миграция без лимитов |
| `GET/POST` | `/admin/full-migration` | Полная миграция без лимитов (alias) |
| `GET/POST` | `/admin/migrate-bots` | Миграция DM-ботов и ключей каналов |
| `GET/POST` | `/admin/migrate-from-dump` | Миграция из локального файлового дампа |
| `GET/POST` | `/admin/full-reset-and-migrate` | Полный сброс + миграция из дампа |

---

## 19. WebSocket Protocol

### Подключение

```
wss://backend.visorlink.org/ws?token=<Firebase_ID_Token>
```

> [!IMPORTANT]
> Токен передаётся через query-параметр `token`. При невалидном токене соединение закрывается с кодом `1008 (VIOLATED_POLICY)`.

**Конфигурация:**
- Ping Period: 15 секунд
- Timeout: 30 секунд
- Max Frame Size: 10 MB

---

### Client → Server Actions

Все действия клиента — JSON-объекты:

#### Subscribe на канал
```json
{
  "type": "subscribe",
  "channel": "chat:CHAT_ID"     // или "user:UID", "feed"
}
```

#### Unsubscribe
```json
{
  "type": "unsubscribe",
  "channel": "chat:CHAT_ID"
}
```

#### Typing Indicator
```json
{
  "type": "typing",
  "chatId": "CHAT_ID"
}
```

#### Stop Typing
```json
{
  "type": "stop_typing",
  "chatId": "CHAT_ID"
}
```

#### Ping (поддержание соединения)
```json
{
  "type": "ping"
}
```

---

### Server → Client Events

Все события — объекты `WsEvent`:

```json
{
  "type": "event_type",
  "channel": "chat:CHAT_ID",
  "data": "{ ... }"             // JSON-строка с payload
}
```

#### Типы событий:

| Event Type | Channel Pattern | Data Payload | Описание |
|---|---|---|---|
| `message_new` | `chat:<chatId>` | `MessageDto` (JSON-строка) | Новое сообщение |
| `message_update` | `chat:<chatId>` | `MessageDto` (JSON-строка) | Обновление сообщения (реакции, редактирование) |
| `message_delete` | `chat:<chatId>` | `{ "id": "msg_id", "chatId": "..." }` | Удаление сообщения |
| `chat_update` | `chat:<chatId>` | `ChatDto` (JSON-строка) | Обновление параметров чата |
| `typing` | `chat:<chatId>` | `TypingPayload` | Индикатор набора |
| `presence` | `user:<uid>` | `PresencePayload` | Изменение онлайн-статуса |
| `notification` | `user:<uid>` | Payload уведомления | Персональное уведомление |
| `pong` | `system` | `"pong"` | Ответ на ping |
| `error` | `<channel>` | `"Forbidden subscription"` | Ошибка подписки |

---

### Typing Payload
```json
{
  "chatId": "chat_123",
  "uid": "user_uid",
  "username": "john_doe",
  "isTyping": "true"        // строка "true"/"false"
}
```

### Presence Payload
```json
{
  "uid": "user_uid",
  "isOnline": "true",       // строка
  "lastSeenAt": "1700000000000"  // строка, epoch ms
}
```

---

## 20. Data Models (DTO Reference)

### UserDto

```json
{
  "id": "firebase_uid",
  "uid": "firebase_uid",            // = id
  "username": "john_doe",
  "displayName": "John Doe",
  "email": "john@example.com",
  "bio": "Hello world",
  "avatarUrl": "https://...",
  "isOnline": false,
  "lastSeenAt": 1700000000000,      // epoch ms, nullable
  "createdAt": 1690000000000,       // epoch ms
  "createdAtIso": "2024-01-01T00:00:00Z",
  "bits": 150,
  "streak": 7,
  "showStreak": true,
  "trialUsed": false,
  "isAdmin": false,
  "isBot": false,
  "botBadge": null,                 // "official" | "verified" | "unverified"
  "tfaEnabled": false,
  "registeredViaOfficialClient": true,
  "proUntil": 1730000000000,        // epoch ms, nullable
  "customization": {}               // произвольный JSON
}
```

---

### ChatDto

```json
{
  "id": "chat_123",
  "type": "group",                   // "direct" | "group" | "channel"
  "name": "Название чата",
  "tag": "channel_tag",              // nullable
  "description": "Описание",
  "avatarUrl": "https://...",
  "participants": ["uid1", "uid2"],
  "memberIds": ["uid1", "uid2"],     // = participants
  "participantData": {
    "uid1": {
      "username": "john",
      "displayName": "John",
      "avatarUrl": "https://..."
    }
  },
  "lastMessage": {
    "id": "msg_123",
    "senderId": "uid1",
    "senderUsername": "john",
    "type": "text",
    "text": "Привет!",
    "timestamp": 1700000000000
  },
  "lastMessageAt": 1700000000000,
  "memberCount": 25,
  "badge": "official",               // nullable
  "botAllowed": false,
  "joinByLink": true,
  "joinByTag": false,
  "allowReactions": true,
  "allowComments": true,
  "isForum": false,
  "noForwards": false,
  "inviteLink": "abc123xyz",         // nullable
  "createdAt": 1690000000000
}
```

---

### MessageDto

```json
{
  "id": "msg_123",
  "chatId": "chat_456",
  "senderId": "uid_abc",
  "senderUsername": "john_doe",
  "type": "text",                    // "text"|"image"|"voice"|"sticker"|"album"|"gift"|"file"
  "text": "Привет!",
  "caption": null,
  "url": null,
  "fileName": null,
  "duration": null,                  // секунды, для voice/video
  "stickerId": null,
  "cdnMediaId": null,
  "images": [],                      // для альбомов: List<AlbumImageDto>
  "replyTo": {                       // nullable
    "id": "msg_original",
    "type": "text",
    "text": "Оригинальный текст",
    "url": null,
    "senderUsername": "jane"
  },
  "forwardFrom": {                   // nullable
    "senderId": "uid_orig",
    "senderUsername": "orig_user",
    "chatId": "orig_chat"
  },
  "reactions": [
    {
      "emoji": "❤️",
      "uids": ["uid1", "uid2"],
      "count": 2
    }
  ],
  "readBy": ["uid1"],
  "spoiler": false,
  "isBot": false,
  "botLabel": null,
  "botBadge": null,
  "commentsCount": 3,
  "commentsEnabled": true,
  "giftType": null,                  // "premium_box" для подарков
  "redeemed": false,
  "redeemedByUsername": null,
  "topicId": null,
  "deleted": false,
  "lastEdited": null,                // epoch ms, nullable
  "createdAt": 1700000000000
}
```

---

### CommentDto

```json
{
  "id": "cmt_123",
  "messageId": "msg_456",
  "chatId": "chat_789",
  "senderId": "uid_abc",
  "senderUsername": "john_doe",
  "type": "text",
  "text": "Отличный пост!",
  "url": null,
  "fileName": null,
  "duration": null,
  "stickerId": null,
  "replyTo": null,                   // ReplyDto, nullable
  "reactions": [],
  "spoiler": false,
  "deleted": false,
  "createdAt": 1700000000000
}
```

---

### AlbumImageDto

```json
{
  "cdnMediaId": "cdn_123",
  "url": "https://cdn.visorlink.org/img.jpg",
  "fileName": "photo.jpg",
  "spoiler": false
}
```

---

### ReplyDto

```json
{
  "id": "msg_original",
  "type": "text",
  "text": "Текст оригинала",
  "url": null,
  "senderUsername": "jane_doe"
}
```

---

### ForwardDto

```json
{
  "senderId": "uid_orig",
  "senderUsername": "orig_user",
  "chatId": "orig_chat_id"
}
```

---

### ReactionDto

```json
{
  "emoji": "❤️",
  "uids": ["uid1", "uid2", "uid3"],
  "count": 3
}
```

---

### LastMessageDto

```json
{
  "id": "msg_123",
  "senderId": "uid_abc",
  "senderUsername": "john",
  "type": "text",
  "text": "Последнее сообщение",
  "timestamp": 1700000000000
}
```

---

### ParticipantDataDto

```json
{
  "username": "john_doe",
  "displayName": "John Doe",
  "avatarUrl": "https://..."
}
```

---

### ChatMemberDto

```json
{
  "chatId": "chat_123",
  "userId": "uid_abc",
  "username": "john_doe",
  "displayName": "John Doe",
  "avatarUrl": "https://...",
  "role": "member",              // "owner" | "admin" | "member"
  "joinedAt": 1700000000000,
  "muted": false,
  "banned": false
}
```

---

### StickerPackDto

```json
{
  "id": "pack_123",
  "name": "Мои стикеры",
  "emoji": "📦",
  "authorId": "uid_abc",
  "authorName": "John Doe",
  "stickerCount": 12,
  "stickers": [StickerDto, ...],
  "createdAt": 1700000000000
}
```

### StickerDto

```json
{
  "id": "stk_123",
  "packId": "pack_123",
  "url": "https://cdn.visorlink.org/sticker.webp",
  "storagePath": "stickers/pack_123/stk_123.webp",
  "emoji": "😀",
  "name": "happy"
}
```

---

### InviteDto

```json
{
  "id": "inv_123",
  "chatId": "chat_456",
  "chatName": "Группа разработчиков",
  "chatType": "group",
  "chatTag": null,
  "chatAvatarUrl": "https://...",
  "invitedUid": "uid_target",
  "invitedBy": "uid_inviter",
  "invitedByUsername": "admin_john",
  "invitedByDisplayName": "John Admin",
  "status": "pending",              // "pending" | "accepted" | "declined"
  "createdAt": 1700000000000
}
```

---

### NotificationDto

```json
{
  "id": "notif_123",
  "userId": "uid_abc",
  "type": "invite",
  "chatId": "chat_456",
  "chatName": "Группа",
  "inviteId": "inv_789",
  "invitedBy": "uid_inviter",
  "invitedByUsername": "admin",
  "title": "Приглашение в группу",
  "body": "Вас пригласили в группу ...",
  "read": false,
  "createdAt": 1700000000000
}
```

---

### DiscoverFeedDto

```json
{
  "id": "feed_123",
  "messageId": "msg_456",
  "chatId": "channel_789",
  "channelName": "TechNews",
  "channelAvatar": "https://...",
  "channelTag": "technews",
  "senderId": "uid_abc",
  "senderUsername": "editor",
  "type": "image",
  "text": null,
  "caption": "Новость дня",
  "url": "https://...",
  "cdnMediaId": "cdn_123",
  "images": [],
  "duration": null,
  "likeCount": 42,
  "likedByMe": false,
  "createdAt": 1700000000000
}
```

---

### IncidentDto

```json
{
  "id": "inc_123",
  "service": "api-gateway",
  "errorTelemetry": "Connection timeout at ...",
  "timestamp": 1700000000000,
  "resolved": false,
  "resolvedAt": null
}
```

---

### DmBotDto

```json
{
  "botUid": "bot_uid_123",
  "ownerId": "uid_owner",
  "username": "my_bot",
  "name": "My Cool Bot",
  "avatarUrl": "https://...",
  "badge": "unverified",            // "official" | "verified" | "unverified"
  "banned": false,
  "createdAt": 1700000000000
}
```

---

### ChannelBotKeyDto {#channelbotkeysdto}

```json
{
  "id": "key_123",
  "chatId": "channel_456",
  "label": "News Bot",
  "badge": "unverified",
  "active": true,
  "messagesTotal": 150,
  "createdAt": 1700000000000,
  "lastUsedAt": 1700500000000,
  "apiKey": "vlck_abc123..."        // ⚠️ Только при создании/регенерации
}
```

---

### DailyStreakResponse

```json
{
  "success": true,
  "claimed": true,
  "streak": 7,
  "bits": 150,
  "message": "Стрик обновлён!",
  "hoursUntilNext": 18
}
```

---

### HealthResponse

```json
{
  "status": "healthy",
  "database": true,
  "redis": true
}
```

---

### WsClientAction

```json
{
  "type": "subscribe",             // "subscribe"|"unsubscribe"|"typing"|"stop_typing"|"ping"
  "channel": "chat:CHAT_ID",      // для subscribe/unsubscribe
  "chatId": "CHAT_ID"             // для typing/stop_typing
}
```

---

### WsEvent

```json
{
  "type": "message_new",
  "channel": "chat:CHAT_ID",
  "data": "{ ... }"               // JSON-строка с payload
}
```

---

### TypingPayload

```json
{
  "chatId": "chat_123",
  "uid": "user_uid",
  "username": "john_doe",
  "isTyping": true
}
```

### PresencePayload

```json
{
  "uid": "user_uid",
  "isOnline": true,
  "lastSeenAt": 1700000000000
}
```

---

## Quick Reference: Все эндпоинты

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | ❌ | Health check |
| **2FA** | | | |
| `POST` | `/auth/2fa/request` | ✅ | Запросить 2FA код |
| `POST` | `/auth/2fa/verify` | ✅ | Верифицировать 2FA |
| **Users** | | | |
| `GET` | `/users` | ✅ | Поиск пользователей |
| `GET` | `/users/search` | ✅ | Поиск пользователей (alias) |
| `GET` | `/users/me` | ✅ | Мой профиль |
| `POST` | `/users/sync` | ✅ | Синхронизировать профиль |
| `GET` | `/users/{uid}` | ✅ | Профиль пользователя |
| `PUT` | `/users/me/profile` | ✅ | Обновить профиль |
| `PUT` | `/users/me/username` | ✅ | Изменить username |
| `GET` | `/users/check-username/{username}` | ❌ | Проверить username |
| `POST` | `/users/me/fcm-token` | ✅ | FCM push token |
| `POST` | `/users/me/ntfy-topic` | ✅ | Ntfy topic |
| `POST` | `/users/me/streak` | ✅ | Ежедневный стрик |
| `POST` | `/users/me/buy-pro` | ✅ | Купить PRO |
| **Bots** | | | |
| `GET` | `/bots/dm` | ✅ | Мои DM боты |
| `POST` | `/bots/dm` | ✅ | Создать DM бота |
| `POST` | `/bots/dm/allow` | ✅ | Разрешить бота |
| `PUT` | `/bots/dm/{botUid}` | ✅ | Редактировать бота |
| `DELETE` | `/bots/dm/{botUid}` | ✅ | Удалить бота |
| `POST` | `/bots/dm/{botUid}/regenerate-token` | ✅ | Перегенерировать токен |
| **Webhooks** | | | |
| `POST` | `/webhooks/dm-bot` | 🤖 Bot Token | Webhook DM-бота |
| `POST` | `/webhooks/channel-bot` | 🔑 API Key | Webhook канального бота |
| **Telegram** | | | |
| `POST` | `/telegram/generate-code` | ✅ | Код привязки TG |
| `POST/DELETE` | `/telegram/unbind` | ✅ | Отвязать TG |
| **Client** | | | |
| `POST` | `/client/verify-attestation` | ❌ | Attestation (stub) |
| **Chats** | | | |
| `GET` | `/chats` | ✅ | Мои чаты |
| `POST` | `/chats/create` | ✅ | Создать чат |
| `POST` | `/chats` | ✅ | Создать чат (alias) |
| `POST` | `/chats/direct` | ✅ | Создать DM |
| `GET` | `/chats/find-by-tag/{tag}` | ✅ | Найти по тегу |
| `POST` | `/chats/join/invite` | ✅ | Присоединиться по инвайту |
| `POST` | `/chats/join/tag` | ✅ | Присоединиться по тегу |
| `GET` | `/chats/{chatId}` | ✅ | Детали чата |
| `PUT` | `/chats/{chatId}/settings` | ✅ | Настройки чата |
| `POST` | `/chats/{chatId}/leave` | ✅ | Покинуть чат |
| `DELETE` | `/chats/{chatId}` | ✅ | Удалить чат |
| `GET` | `/chats/{chatId}/members` | ✅ | Участники чата |
| `POST` | `/chats/{chatId}/moderate` | ✅ | Модерация |
| `PUT` | `/chats/{chatId}/members/{uid}/role` | ✅ | Установить роль |
| `POST` | `/chats/{chatId}/invite-link/regenerate` | ✅ | Перегенерировать инвайт |
| `POST` | `/chats/{chatId}/allow-bot` | ✅ | Разрешить бота |
| `POST` | `/chats/{chatId}/invite-user` | ✅ | Пригласить юзера |
| `GET` | `/chats/{chatId}/bot-keys` | ✅ | Ключи канальных ботов |
| `POST` | `/chats/{chatId}/bot-keys` | ✅ | Создать ключ |
| `DELETE` | `/chats/{chatId}/bot-keys/{keyId}` | ✅ | Отозвать ключ |
| `POST` | `/chats/{chatId}/bot-keys/{keyId}/regenerate` | ✅ | Перегенерировать ключ |
| **Messages** | | | |
| `GET` | `/chats/{chatId}/messages` | ✅ | Сообщения (пагинация) |
| `POST` | `/chats/{chatId}/messages` | ✅ | Отправить сообщение |
| `POST` | `/messages/send` | ✅ | Отправить (alias) |
| `POST` | `/chats/{chatId}/messages/album` | ✅ | Отправить альбом |
| `PUT` | `/chats/{chatId}/messages/{msgId}` | ✅ | Редактировать |
| `DELETE` | `/chats/{chatId}/messages/{msgId}` | ✅ | Удалить |
| `PUT` | `/chats/{chatId}/messages/{msgId}/reactions` | ✅ | Toggle реакция |
| `PUT` | `/chats/{chatId}/messages/{msgId}/read` | ✅ | Пометить прочитанным |
| `POST` | `/chats/{chatId}/messages/{msgId}/redeem-gift` | ✅ | Активировать подарок |
| `GET` | `/chats/{chatId}/messages/{msgId}/comments` | ✅ | Комментарии |
| `POST` | `/chats/{chatId}/messages/{msgId}/comments` | ✅ | Добавить комментарий |
| **Gifts** | | | |
| `POST` | `/gifts/send` | ✅ | Отправить подарок |
| `POST` | `/gifts/{messageId}/redeem` | ✅ | Активировать подарок |
| **Stickers** | | | |
| `GET` | `/stickers/my` | ✅ | Мои стикерпаки |
| `GET` | `/sticker-packs` | ✅ | Мои стикерпаки (alias) |
| `POST` | `/sticker-packs` | ✅ | Создать стикерпак |
| `POST` | `/sticker-packs/{packId}/install` | ✅ | Установить стикерпак |
| `DELETE` | `/sticker-packs/{packId}` | ✅ | Удалить стикерпак |
| **Invites & Notifications** | | | |
| `GET` | `/invites` | ✅ | Мои приглашения |
| `POST` | `/invites/{id}/respond` | ✅ | Ответить на приглашение |
| `GET` | `/notifications` | ✅ | Мои уведомления |
| **Feed** | | | |
| `GET` | `/feed` | ✅ | Лента рекомендаций |
| `POST` | `/feed/{id}/like` | ✅ | Лайк/снять лайк |
| `POST` | `/feed/{id}/toggle-comments` | ✅ | Вкл/выкл комментарии |
| **Incidents** | | | |
| `GET` | `/incidents` | ❌ | Список инцидентов |
| `POST` | `/incidents/report` | ❌ | Репорт инцидента |
| `POST` | `/incidents/{id}/resolve` | 🔒 Admin | Разрешить инцидент |
| **System** | | | |
| `GET` | `/feature-flags` | ❌ | Фиче-флаги |
| `GET` | `/internal/{key}` | ❌/🔒 | Документ по ключу |
| **Admin** | | | |
| `GET` | `/admin/check` | ✅ | Проверка admin |
| `POST` | `/admin/claim` | ✅ | Получить admin |
| `GET` | `/admin/stats` | 🔒 Admin | Статистика |
| `POST` | `/admin/set-admin` | 🔒 Admin | Управление admin |
| `POST` | `/admin/users/ban` | 🔒 Admin | Бан пользователя |
| `POST` | `/admin/verify-email` | 🔒 Admin | Верификация email |
| `POST` | `/admin/verify-channel` | 🔒 Admin | Верификация канала |
| `PUT` | `/admin/bots/{botUid}` | 🔒 Admin | Редактировать бота |
| `POST` | `/admin/bots/{botUid}/ban` | 🔒 Admin | Забанить бота |
| `POST` | `/admin/bots/{botUid}/revoke` | 🔒 Admin | Отозвать токен |
| `POST` | `/admin/verify-channel-bot` | 🔒 Admin | Верифицировать ключ |
| `POST` | `/admin/grant-eternal-pro` | 🔒 Admin | Выдать PRO |
| `POST` | `/admin/send-gift` | 🔒 Admin | Отправить подарок |
| `POST` | `/admin/feature-flags` | 🔒 Admin | Создать фиче-флаг |
| `DELETE` | `/admin/feature-flags/{key}` | 🔒 Admin | Удалить фиче-флаг |
| `PUT` | `/admin/incidents/{id}` | 🔒 Admin | Редактировать инцидент |
| `DELETE` | `/admin/incidents/{id}` | 🔒 Admin | Удалить инцидент |
| **WebSocket** | | | |
| `WS` | `/ws?token=<token>` | ✅ | Real-time соединение |

---

> **Легенда:** ✅ Firebase Auth · 🔒 System Admin · 🤖 Bot Token · 🔑 API Key · ❌ Без авторизации
