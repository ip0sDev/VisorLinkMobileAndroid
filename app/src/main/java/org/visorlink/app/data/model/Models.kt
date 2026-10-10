package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.IgnoreExtraProperties

// ─── User ─────────────────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val online: Boolean = false,
    val lastSeen: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val fcmTokens: List<String> = emptyList(),

    @get:PropertyName("isAdmin") @set:PropertyName("isAdmin")
    var isAdmin: Boolean = false,
    var lastMessageAt: Timestamp? = null,
    val isBot: Boolean = false,
    val botBadge: String = "unverified",
    val ownerId: String? = null,
    val bits: Int = 0,
    val streak: Int = 0,
    val showStreak: Boolean = true,
    val proUntil: Timestamp? = null,
    val trialUsed: Boolean = false,
    /** Пишет только сервер (`set2FAEnabled` с кодом) — правила запрещают клиенту менять поле. */
    val tfaEnabled: Boolean = false,
    /** Коды 2FA можно получать в Telegram (`setTfaTelegram`). Пишет только сервер. */
    val tfaTelegram: Boolean? = null,
    /** Привязка Telegram (бот пишет число или строку — читаем как есть). */
    val telegramId: Any? = null,
    val telegramUsername: String? = null,
    val registeredViaOfficialClient: Boolean = true,

    // ДОБАВЛЕНО:
    val ignoreCustomizations: Boolean = false,
    val interestWeights: Map<String, Double> = emptyMap(),
    val tg_username: String? = null,
    val tg_uid: Long? = null,

    val diaryEnabled: Boolean = false,
    val diaryRemindersEnabled: Boolean = false,
    val diaryReminderTime: String = "21:00", // HH:mm

    // Fields from Firestore warnings
    val stickerPackIds: List<String> = emptyList(),
    val customization: Map<String, Any?> = emptyMap(),
    val lastStreakUpdate: Timestamp? = null,
    val ntfyTopics: List<String> = emptyList(),
    val settings: Map<String, Any?> = emptyMap(),
    val mutedChatIds: List<String> = emptyList(),
    val lastBitsClaim: Timestamp? = null,

    // Legal & Compliance consent tracking
    val acceptedAt: Timestamp? = null,
    val acceptedVersion: String? = null,

    // UGC Compliance: Blocked Users
    val blockedUserIds: List<String> = emptyList(),

    /** Режим ID-карты (standard | protogen | beast). Пишет только сервер; нет — Standard. */
    val idMode: String? = null,
) {
    fun isProActive(): Boolean {
        if (proUntil == null) return false
        return proUntil.toDate().time > System.currentTimeMillis()
    }

    /** Telegram привязан (`telegramId`). */
    val hasTelegram: Boolean
        @com.google.firebase.firestore.Exclude get() = telegramId?.toString()?.isNotBlank() == true

    /** «@ник» привязанного Telegram, иначе «ID 123». */
    val telegramAccount: String?
        @com.google.firebase.firestore.Exclude get() = when {
            !telegramUsername.isNullOrBlank() -> "@$telegramUsername"
            hasTelegram -> "ID $telegramId"
            else -> null
        }
}

enum class ChatType { DIRECT, GROUP, CHANNEL, EMERGENCY }

@IgnoreExtraProperties
data class ChatSettings(
    val joinByLink: Boolean = true,
    val joinByTag: Boolean = false,
    val allowReactions: Boolean = true,
    val allowComments: Boolean = true,
    val inviteLink: String = "",
    val isForum: Boolean = false,
    val is_forum: Boolean = false,
    val noForwards: Boolean = false
) {
    val isForumEnabled: Boolean get() = isForum || is_forum
}

@IgnoreExtraProperties
data class LastMessageInfo(
    val text: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val readBy: List<String> = emptyList(),
    val read: Boolean? = null
)

@IgnoreExtraProperties
data class Chat(
    val id: String = "",
    val type: String = "direct",
    val participants: List<String> = emptyList(),
    val participantData: Map<String, Map<String, String>> = emptyMap(),
    val name: String = "",
    val tag: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val createdBy: String = "",
    val memberCount: Int = 0,
    val memberIds: List<String> = emptyList(),
    val isForum: Boolean = false,
    val is_forum: Boolean = false,
    val settings: ChatSettings = ChatSettings(),
    val lastMessage: Any? = null,
    val lastMessageSenderId: String? = null,
    val lastSeq: Long = 0L,
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val unreadCount: Map<String, Int> = emptyMap(),
    /** «ID группы» (группы и каналы). Пишет только сервер — см. [groupIdCard]. */
    val groupId: Map<String, Any?>? = null,
) {
    val isForumActive: Boolean get() = isForum || is_forum || settings.isForum || settings.is_forum

    /** Функция, а не свойство: Firestore и кэш не должны сериализовать её как поле. */
    fun groupIdCard(): org.visorlink.app.data.idcard.GroupIdCard? =
        org.visorlink.app.data.idcard.GroupIdCard.parse(groupId)

    fun unreadCountFor(currentUid: String): Int = unreadCount[currentUid] ?: unreadCount[""] ?: 0

    fun lastMessageInfo(): LastMessageInfo? {
        return when (val lm = lastMessage) {
            is Map<*, *> -> {
                val text = (lm["text"] as? String) ?: ""
                val senderId = (lm["senderId"] as? String) ?: ""
                val senderUsername = (lm["senderUsername"] as? String) ?: ""
                val readByRaw = lm["readBy"]
                val readBy = when (readByRaw) {
                    is List<*> -> readByRaw.filterIsInstance<String>()
                    else -> emptyList()
                }
                val read = lm["read"] as? Boolean
                LastMessageInfo(
                    text = text,
                    senderId = senderId,
                    senderUsername = senderUsername,
                    readBy = readBy,
                    read = read
                )
            }
            is String -> LastMessageInfo(
                text = lm,
                senderId = lastMessageSenderId ?: ""
            )
            else -> if (!lastMessageSenderId.isNullOrBlank()) {
                LastMessageInfo(text = "", senderId = lastMessageSenderId)
            } else null
        }
    }

    fun lastMessageText(): String {
        return when (val lm = lastMessage) {
            is String -> lm
            is Map<*, *> -> (lm["text"] as? String) ?: ""
            else -> ""
        }
    }

    val isEmergency: Boolean get() = type == "emergency" || id.startsWith("emer_")

    fun chatType() = when {
        isEmergency -> ChatType.EMERGENCY
        type == "group" -> ChatType.GROUP
        type == "channel" -> ChatType.CHANNEL
        else -> ChatType.DIRECT
    }

    fun displayName(currentUid: String) = when (chatType()) {
        ChatType.DIRECT, ChatType.EMERGENCY -> participantData[otherParticipantId(currentUid)]?.get("displayName")
            ?: name.ifEmpty { "Emergency Chat" }
        else -> name
    }

    fun otherParticipantId(currentUid: String) =
        participants.firstOrNull { it != currentUid } ?: ""

    fun otherDisplayName(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("displayName") ?: ""

    fun otherUsername(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("username") ?: ""
}

/**
 * Логика определения статуса прочтения последнего сообщения в чате (Секция 3.1 спецификации).
 * Гарантия 0 лишних чтений Firestore.
 */
fun isLastMessageRead(chat: Chat, currentUserId: String): Boolean {
    val lastMsg = chat.lastMessageInfo()
    val senderId = chat.lastMessageSenderId?.takeIf { it.isNotBlank() } ?: lastMsg?.senderId

    if (lastMsg == null && senderId.isNullOrBlank()) return false

    // Прямой флаг (для V2 backend или явного флага)
    if (lastMsg?.read == true) return true

    val isMine = senderId == currentUserId

    return if (isMine) {
        val otherUserId = if (chat.chatType() == ChatType.DIRECT) {
            chat.participants.firstOrNull { it != currentUserId }
        } else null

        // 1. Собеседник присутствует в списке прочитавших readBy
        if (otherUserId != null && lastMsg?.readBy?.contains(otherUserId) == true) {
            return true
        }

        // 2. В readBy есть кто-либо кроме текущего пользователя
        if (lastMsg?.readBy?.any { it != currentUserId && it.isNotBlank() } == true) {
            return true
        }

        // 3. Счётчик непрочитанных у собеседника сброшен в 0
        if (otherUserId != null && chat.unreadCount.containsKey(otherUserId) && (chat.unreadCount[otherUserId] ?: 0) == 0) {
            return true
        }

        false
    } else {
        // Для входящих сообщений: прочитал ли текущий пользователь
        if (lastMsg?.readBy?.contains(currentUserId) == true) return true
        if (chat.unreadCountFor(currentUserId) == 0) return true
        false
    }
}

fun DocumentSnapshot.toChatOrNull(): Chat? {
    try {
        val chat = this.toObject(Chat::class.java)?.copy(id = this.id)
        val sMap = this.get("settings") as? Map<*, *>
        val forumFromMap = (sMap?.get("isForum") as? Boolean)
            ?: (sMap?.get("is_forum") as? Boolean)
            ?: false
        val forumFromRoot = this.getBoolean("isForum")
            ?: this.getBoolean("is_forum")
            ?: false
        val isForum = forumFromMap || forumFromRoot || (chat?.isForumActive == true)

        val baseSettings = chat?.settings ?: ChatSettings()
        val finalSettings = baseSettings.copy(
            joinByLink = (sMap?.get("joinByLink") as? Boolean) ?: baseSettings.joinByLink,
            joinByTag = (sMap?.get("joinByTag") as? Boolean) ?: baseSettings.joinByTag,
            allowReactions = (sMap?.get("allowReactions") as? Boolean) ?: baseSettings.allowReactions,
            allowComments = (sMap?.get("allowComments") as? Boolean) ?: baseSettings.allowComments,
            inviteLink = (sMap?.get("inviteLink") as? String) ?: baseSettings.inviteLink,
            isForum = isForum,
            is_forum = isForum
        )

        val rawUnread = this.get("unreadCount")
        val unreadMap: Map<String, Int> = when (rawUnread) {
            is Map<*, *> -> rawUnread.entries.mapNotNull { (k, v) ->
                val uid = k as? String ?: return@mapNotNull null
                val count = when (v) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull() ?: 0
                    else -> 0
                }
                uid to count
            }.toMap()
            is Number -> mapOf("" to rawUnread.toInt())
            else -> chat?.unreadCount ?: emptyMap()
        }

        val lastSeq = chat?.lastSeq ?: (this.getLong("lastSeq") ?: 0L)
        val lastSenderId = chat?.lastMessageSenderId ?: this.getString("lastMessageSenderId")
        val lastMsg = chat?.lastMessage ?: this.get("lastMessage")

        return (chat ?: Chat(id = this.id)).copy(
            id = this.id,
            isForum = isForum,
            is_forum = isForum,
            settings = finalSettings,
            unreadCount = unreadMap,
            lastSeq = lastSeq,
            lastMessageSenderId = lastSenderId,
            lastMessage = lastMsg
        )
    } catch (e: Exception) {
        android.util.Log.e("ChatParser", "Error parsing chat doc $id", e)
        return null
    }
}

// ─── Member ───────────────────────────────────────────────────────────────────

data class Member(
    val uid: String = "",
    val role: String = "member",
    val joinedAt: Timestamp? = null,
    val muted: Boolean = false,
    val mutedUntil: Timestamp? = null,
    val mediaRestricted: Boolean = false,
    val banned: Boolean = false,
    val bannedAt: Timestamp? = null,
    val bannedBy: String? = null
) {
    fun isAdmin() = role in listOf("admin", "owner")
    fun isOwner() = role == "owner"

    fun canSend(): Boolean {
        if (banned) return false
        if (muted) {
            mutedUntil?.let { if (java.util.Date() > it.toDate()) return true }
            return false
        }
        return true
    }

    fun canSendMedia() = canSend() && !mediaRestricted
}

// ─── Permissions helpers ──────────────────────────────────────────────────────

fun canSendMessage(myMember: Member?, chatType: ChatType): Boolean = when (chatType) {
    ChatType.DIRECT, ChatType.EMERGENCY -> true
    ChatType.GROUP   -> myMember?.canSend() ?: false
    ChatType.CHANNEL -> myMember?.isAdmin() ?: false
}

fun canSendMedia(myMember: Member?, chatType: ChatType): Boolean = when (chatType) {
    ChatType.DIRECT, ChatType.EMERGENCY -> true
    ChatType.CHANNEL -> myMember?.isAdmin() ?: false
    ChatType.GROUP   -> myMember?.canSend() == true && myMember.mediaRestricted == false
}

fun canReact(chat: Chat, chatType: ChatType): Boolean {
    if (chatType != ChatType.CHANNEL) return true
    return chat.settings.allowReactions
}

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

fun commentsAllowed(channel: Chat, post: Message): Boolean {
    if (channel.settings.allowComments == false) return false
    if (post.commentsEnabled == false) return false
    return true
}

// ─── Registration & Invites ──────────────────────────────────────────────────

@IgnoreExtraProperties
data class RegistrationInvite(
    val code: String = "",
    val createdAt: Timestamp? = null,
    val createdBy: String = "",
    val isUsed: Boolean = false,
    val usedBy: String? = null,
    val usedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccessRequest(
    val requestId: String = "",
    val email: String = "",
    val username: String = "",
    val note: String = "",
    val status: String = "pending", // "pending", "approved", "rejected"
    val createdAt: Timestamp? = null,
    val processedAt: Timestamp? = null,
    val processedBy: String? = null
)

// ─── Invites ──────────────────────────────────────────────────────────────────

data class GroupInvite(
    val id: String = "",
    val chatId: String = "",
    val invitedBy: String = "",
    val createdAt: Timestamp? = null,
    val status: String = "pending"
)

data class AppNotification(
    val id: String = "",
    val type: String = "",
    val chatId: String = "",
    val inviteId: String = "",
    val invitedBy: String = "",
    val createdAt: Timestamp? = null,
    val read: Boolean = false
)

// ─── Tag search ───────────────────────────────────────────────────────────────

data class TagSearchResult(
    val found: Boolean = false,
    val chatId: String = "",
    val name: String = "",
    val tag: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val memberCount: Int = 0,
    val type: String = "",
    val joinByTag: Boolean = false
)

// ─── Album Image ──────────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class AlbumImage(
    val url: String? = null,
    val cdnMediaId: String? = null,
    val fileName: String = "",
    val spoiler: Boolean = false,
    val driveFileId: String? = null,
    val previewUrl: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "url"         to url,
        "cdnMediaId"  to cdnMediaId,
        "fileName"    to fileName,
        "spoiler"     to spoiler,
        "driveFileId" to driveFileId,
        "previewUrl"  to previewUrl
    )
}

data class AlbumImageLocal(
    val uri: android.net.Uri,
    val spoiler: Boolean = false
)

// ─── Sticker Pack ─────────────────────────────────────────────────────────────

data class StickerPack(
    val id: String = "",
    val name: String = "",
    val emoji: String = "📦",
    val authorId: String = "",
    val authorName: String = "",
    val stickerCount: Int = 0,
    val createdAt: Timestamp? = null,
    val isOfficial: Boolean = false,
    val stickers: List<StickerItem> = emptyList()
)

data class StickerItem(
    val id: String = "",
    val url: String = "",
    val emoji: String = "🎭",
    val storagePath: String = "",
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null
) {
    val isLottie: Boolean
        get() = url.substringBefore("?").endsWith(".json", ignoreCase = true) ||
                url.substringBefore("?").endsWith(".lottie", ignoreCase = true)

    val isGif: Boolean
        get() = url.substringBefore("?").endsWith(".gif", ignoreCase = true)
}

data class UserStickerData(
    val packIds: List<String> = emptyList()
)

// ─── Message ──────────────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val seq: Long? = null,
    val senderId: String = "",
    val senderUsername: String = "",
    val senderIsAdmin: Boolean? = null,
    val tags: List<String> = emptyList(),
    val type: String = MessageType.TEXT,
    val text: String? = null,
    val url: String? = null,
    val fileName: String? = null,
    val duration: Int? = null,
    val stickerId: String? = null,
    val packId: String? = null,
    val packName: String? = null,
    val packEmoji: String? = null,
    val createdAt: Timestamp? = null,
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val replyTo: Map<String, Any?>? = null,
    val reactions: List<Map<String, Any>> = emptyList(),
    val readBy: List<String> = emptyList(),
    val spoiler: Boolean = false,
    val commentsEnabled: Boolean? = null,
    val commentsCount: Int = 0,
    /** Просмотры поста канала: пишет только сервер (recordPostViews), один человек — один просмотр. */
    val viewsCount: Int = 0,
    /** Скрыт модерацией (3 жалобы — submitAbuseReport). Пишет сервер. */
    @get:PropertyName("is_hidden") @set:PropertyName("is_hidden")
    var isHidden: Boolean? = null,
    /** `false` — пост канала ещё не опубликован (виден только автору). */
    @get:PropertyName("is_published") @set:PropertyName("is_published")
    var isPublished: Boolean? = null,
    val caption: String? = null,
    val images: List<AlbumImage> = emptyList(),
    val forwardFrom: Map<String, Any?>? = null,
    val topicId: String? = null,

    val lastEdited: Timestamp? = null,
    val editHistory: List<Map<String, Any>> = emptyList(),

    // Telegram Bot Forwarding
    val tg_forwarded: Boolean? = null,
    val tg_forwarded_from: String? = null,
    val tg_forwarded_from_fallback: String? = null,
    val isUnofficialClient: Boolean? = null,

    // Обмен скином ID-карты (type id_trade): только ссылка на idTrades/{tradeId}, пишет сервер
    val tradeId: String? = null,

    // Подарки
    val redeemed: Boolean = false,
    val redeemedByUid: String? = null,
    val redeemedByUsername: String? = null,
    val giftType: String? = null,

    // Аудио и музыка
    val coverCdnMediaId: String? = null,
    val coverUrl: String? = null,
    val title: String? = null,
    val performer: String? = null,
    val fileSize: Long? = null,

    // Google Drive Media
    val driveFileId: String? = null,
    val driveUrl: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,

    // CDN / Временные файлы
    val cdnMediaId: String? = null,
    val thumbUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val uploadProgress: Float? = null,
    val localFile: java.io.File? = null,
    val localBytes: ByteArray? = null,
    val status: String = "sent",
    val unknownPayload: Map<String, Any?> = emptyMap()
) {
    val isGifMedia: Boolean
        get() = type.equals(MessageType.GIF, ignoreCase = true) ||
                url?.substringBefore("?")?.endsWith(".gif", ignoreCase = true) == true ||
                fileName?.endsWith(".gif", ignoreCase = true) == true ||
                mimeType.equals("image/gif", ignoreCase = true)

    val isLottieMedia: Boolean
        get() = type.equals(MessageType.LOTTIE, ignoreCase = true) ||
                url?.substringBefore("?")?.endsWith(".json", ignoreCase = true) == true ||
                url?.substringBefore("?")?.endsWith(".lottie", ignoreCase = true) == true ||
                fileName?.endsWith(".json", ignoreCase = true) == true ||
                fileName?.endsWith(".lottie", ignoreCase = true) == true ||
                (mimeType.equals("application/json", ignoreCase = true) && type == MessageType.STICKER)

    val replyData: ReplyData?
        get() = replyTo?.let {
            ReplyData(
                id = it["id"] as? String ?: "",
                type = it["type"] as? String ?: MessageType.TEXT,
                text = it["text"] as? String,
                url = it["url"] as? String,
                senderUsername = it["senderUsername"] as? String ?: ""
            )
        }

    val parsedReactions: List<Reaction>
        get() = reactions.mapNotNull { map ->
            try {
                @Suppress("UNCHECKED_CAST")
                Reaction(
                    emoji = map["emoji"] as? String ?: return@mapNotNull null,
                    uids  = map["uids"] as? List<String> ?: emptyList(),
                    count = (map["count"] as? Long)?.toInt() ?: 0
                )
            } catch (e: Exception) { null }
        }

    val parsedForwardFrom: ForwardFrom?
        get() = forwardFrom?.let {
            try {
                ForwardFrom(
                    senderId       = it["senderId"] as? String ?: "",
                    senderUsername = it["senderUsername"] as? String ?: "",
                    chatId         = it["chatId"] as? String,
                    chatName       = it["chatName"] as? String,
                    messageId      = it["messageId"] as? String ?: ""
                )
            } catch (_: Exception) { null }
        }

    val senderUid: String get() = senderId
    val senderName: String get() = senderUsername
}

/**
 * Расчёт следующего seq при отправке сообщения (Секция 2.2 спецификации).
 */
fun calculateNextSeq(
    chat: Chat?,
    currentMessages: List<Message>
): Long {
    val chatLastSeq = chat?.lastSeq ?: 0L
    val maxMsgSeq = currentMessages.maxOfOrNull { it.seq ?: 0L } ?: 0L
    return maxOf(chatLastSeq, maxMsgSeq) + 1L
}

/**
 * Алгоритм гибридной обратной совместимой сортировки (Секция 2.3 спецификации).
 */
fun sortMessages(messages: List<Message>): List<Message> {
    return messages.sortedWith { a, b ->
        val aTime = a.createdAt?.let { (it.seconds * 1000L) + (it.nanoseconds / 1_000_000L) } ?: Long.MAX_VALUE
        val bTime = b.createdAt?.let { (it.seconds * 1000L) + (it.nanoseconds / 1_000_000L) } ?: Long.MAX_VALUE

        val aSeq = a.seq
        val bSeq = b.seq

        when {
            // 1. Оба сообщения имеют номер последовательности seq
            aSeq != null && bSeq != null -> {
                val seqComp = aSeq.compareTo(bSeq)
                if (seqComp != 0) {
                    seqComp
                } else {
                    val timeComp = aTime.compareTo(bTime)
                    if (timeComp != 0) timeComp else a.id.compareTo(b.id)
                }
            }
            // 2. Одно с seq, другое legacy (без seq)
            aSeq != null && bSeq == null -> {
                // Если разница по времени больше 2 секунд — ориентируемся на реальное время
                if (kotlin.math.abs(aTime - bTime) > 2000) {
                    aTime.compareTo(bTime)
                } else {
                    1 // Новое сообщение с seq ставится после legacy
                }
            }
            aSeq == null && bSeq != null -> {
                if (kotlin.math.abs(aTime - bTime) > 2000) {
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

object MessageType {
    const val TEXT    = "text"
    const val IMAGE   = "image"
    const val VOICE   = "voice"
    const val AUDIO   = "audio"
    const val STICKER = "sticker"
    const val ALBUM   = "album"
    const val GIFT    = "gift"
    const val VIDEO   = "video"
    const val GIF     = "gif"
    const val LOTTIE  = "lottie"
    /** «ID-карта на обмен»: вид и статус — в idTrades/{tradeId}, само сообщение ничего не доказывает. */
    const val ID_TRADE = "id_trade"
    const val UNKNOWN = "unknown"

    fun isKnown(type: String?): Boolean = when (type?.lowercase()) {
        TEXT, IMAGE, VOICE, AUDIO, STICKER, ALBUM, GIFT, VIDEO, GIF, LOTTIE, ID_TRADE -> true
        else -> false
    }

    /**
     * Тип, у которого содержимое — файл по `url` (как MEDIA_TYPES в вебе, без альбома: у него
     * `images`). Подарок, обмен ID-картой и текст вложения не имеют.
     */
    fun carriesMedia(type: String?): Boolean = when (type?.lowercase()) {
        IMAGE, VOICE, AUDIO, STICKER, VIDEO, GIF, LOTTIE, "file", "media" -> true
        else -> false
    }
}

/**
 * Последнее сообщение — «🪪 ID-карта на обмен» (сервер пишет `lastMessage.text` по-русски с
 * `type: id_trade`): список чатов и тем показывает подпись на языке интерфейса.
 */
fun isIdTradePreview(lastMessage: Any?): Boolean {
    val m = lastMessage as? Map<*, *>
    if (m?.get("type") == MessageType.ID_TRADE) return true
    val text = (m?.get("text") ?: lastMessage) as? String ?: return false
    return text.trim().lowercase() == "🪪 id-карта на обмен"
}

/**
 * Определение устаревших медиасообщений (Legacy CDN Suppression).
 * Сообщения со старого CDN api.visorlink.org не вызывают 404 и отображаются как архивные.
 */
fun isLegacyMediaMessage(message: Message): Boolean {
    // Если есть driveFileId, сообщение современное и валидное
    if (!message.driveFileId.isNullOrEmpty()) return false

    // Если есть старый cdnMediaId или URL ссылается на выключенный CDN
    if (!message.cdnMediaId.isNullOrEmpty()) return true
    if (message.thumbUrl?.contains("api.visorlink.org") == true) return true
    if (message.url?.contains("api.visorlink.org") == true) return true
    if (message.url?.contains("/f/") == true && message.url.contains("googleusercontent.com") != true) return true

    // Медиа без URL и не в процессе локальной отправки. Только для типов с вложением: подарок
    // и обмен ID-картой ссылки не имеют по природе — раньше они попадали в «архивное вложение»
    if (message.url.isNullOrEmpty() && MessageType.carriesMedia(message.type) && message.localFile == null && message.status == SendStatus.SENT) return true

    return false
}

object SendStatus {
    const val SENDING = "sending"
    const val QUEUED  = "queued"
    const val SENT    = "sent"
    const val ERROR   = "error"
}

data class ReplyData(
    val id: String,
    val type: String,
    val text: String?,
    val url: String?,
    val senderUsername: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id"             to id,
        "type"           to type,
        "text"           to text,
        "url"            to url,
        "senderUsername" to senderUsername
    )
}

data class Reaction(
    val emoji: String = "",
    val uids: List<String> = emptyList(),
    val count: Int = 0
) {
    fun toMap() = mapOf("emoji" to emoji, "uids" to uids, "count" to count)
}

data class Sticker(
    val id: String = "",
    val url: String = "",
    val name: String = "",
    val storagePath: String = "",
    val createdAt: Timestamp? = null
)

data class Comment(
    val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val type: String = MessageType.TEXT,
    val text: String? = null,
    val url: String? = null,
    val fileName: String? = null,
    val duration: Int? = null,
    val spoiler: Boolean = false,
    val replyTo: CommentReplyData? = null,
    val reactions: List<Map<String, Any>> = emptyList(),
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val status: String = "sent"
) {
    val parsedReactions: List<Reaction>
        get() = reactions.mapNotNull { map ->
            try {
                @Suppress("UNCHECKED_CAST")
                Reaction(
                    emoji = map["emoji"] as? String ?: return@mapNotNull null,
                    uids  = map["uids"] as? List<String> ?: emptyList(),
                    count = (map["count"] as? Long)?.toInt() ?: 0
                )
            } catch (e: Exception) { null }
        }.filter { it.count > 0 }
}

data class CommentReplyData(
    val id: String = "",
    val type: String = "",
    val text: String? = null,
    val url: String? = null,
    val senderUsername: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id"             to id,
        "type"           to type,
        "text"           to text,
        "url"            to url,
        "senderUsername" to senderUsername
    )
}

fun Comment.toCommentReplyData() = CommentReplyData(
    id             = id,
    type           = type,
    text           = text,
    url            = url,
    senderUsername = senderUsername
)

// ─── Incidents ───────────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class Incident(
    @DocumentId
    val id: String = "",
    val service: String = "",
    val errorTelemetry: String = "",
    val timestamp: Long = 0L,
    val resolved: Boolean = false,
    val resolvedAt: Long? = null,

    @get:Exclude
    val isLocal: Boolean = false,

    @get:Exclude
    val localErrorReason: String? = null
) {
    val isActive: Boolean get() = !resolved
}

data class PresenceData(val online: Boolean = false, val lastSeen: Long? = null)

sealed class TopbarStatus {
    object Online : TopbarStatus()
    object Typing : TopbarStatus()
    object Offline : TopbarStatus()
    object Connecting : TopbarStatus()
    object WaitingForNetwork : TopbarStatus()
    object Updating : TopbarStatus()
    data class LastSeen(val ts: Long?) : TopbarStatus()
    data class MemberCount(val total: Int, val online: Int) : TopbarStatus()
}

enum class SyncState {
    SYNCED,
    CONNECTING,
    UPDATING,
    WAITING_FOR_NETWORK
}

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DateHeader(val label: String) : MessageListItem()
}

/**
 * Оформление приложения. Палитра, формы, типографика и слои глубины для каждой
 * темы собираются в `ui/theme/Theme.kt`; компоненты в `ui/components` читают
 * результат через `LocalVlTokens` и не зависят от этого enum напрямую.
 *
 * [id] стабилен и пишется в SharedPreferences / профиль PRO-кастомизации —
 * при переименовании констант его менять нельзя.
 */
enum class AppTheme(
    val id: String,
    /**
     * false — тему нельзя выбрать в настройках или кастомизации, её включает режим
     * ID-карты (Forge v2). Такие темы не читаются из настроек и профиля.
     */
    val selectable: Boolean = true,
) {
    /** Чистый Material 3 Expressive: плоские поверхности, Material You. */
    MATERIAL3_EXPRESSIVE("m3e"),

    /** Biolume: неоморфный рельеф + редкий сигнальный неон (Abyss / Tidepool). */
    BIOLUME("biolume"),

    /** Forge v2, оттенок Protogen: холодный неон. Включается особым режимом ID-карты. */
    FORGE_PROTOGEN("forge_protogen", selectable = false),

    /** Forge v2, оттенок Beast: тёплый янтарь. Включается особым режимом ID-карты. */
    FORGE_BEAST("forge_beast", selectable = false);

    companion object {
        val Default = MATERIAL3_EXPRESSIVE

        /** Темы для селекторов: Forge v2 пользователь не выбирает. */
        val selectableEntries: List<AppTheme> get() = entries.filter { it.selectable }

        fun fromId(id: String?): AppTheme =
            selectableEntries.firstOrNull { it.id == id } ?: Default
    }
}

val AppTheme.isBiolume: Boolean get() = this == AppTheme.BIOLUME
val AppTheme.isForgeV2: Boolean get() = this == AppTheme.FORGE_PROTOGEN || this == AppTheme.FORGE_BEAST

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorPreset {
    DEFAULT, PURPLE, BLUE, EMERALD, CRIMSON;

    val seedColor: androidx.compose.ui.graphics.Color?
        get() = when (this) {
            DEFAULT -> null
            PURPLE  -> androidx.compose.ui.graphics.Color(0xFF831AD4)
            BLUE    -> androidx.compose.ui.graphics.Color(0xFF0EA5E9)
            EMERALD -> androidx.compose.ui.graphics.Color(0xFF10B981)
            CRIMSON -> androidx.compose.ui.graphics.Color(0xFFE11D48)
        }
}

data class AppSettings(
    val hapticFeedback: Boolean = true,
    val notificationsEnabled: Boolean = true
)

// ─── Topics & Kanban Tasks ───────────────────────────────────────────────────

@IgnoreExtraProperties
data class Topic(
    @DocumentId val id: String = "",
    val title: String = "",
    val icon: String? = null,
    val color: String? = null,
    val type: String = "chat", // "chat" | "tasks"
    val isGeneral: Boolean = false,
    val isClosed: Boolean = false,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val lastMessageAt: Timestamp? = null,
    val lastMessage: Any? = null,
    val unreadCount: Int = 0
) {
    val displayIcon: String get() = if (!icon.isNullOrBlank()) icon else if (isGeneral) "#" else if (type == "tasks") "📋" else "💬"
    val displayColor: String get() = if (!color.isNullOrBlank()) color else if (type == "tasks") "#8C6BFF" else "#35C7E8"
    val isTasks: Boolean get() = type == "tasks"

    fun lastMessageText(): String {
        return when (lastMessage) {
            is String -> lastMessage
            is Map<*, *> -> {
                val text = (lastMessage["text"] as? String) ?: ""
                val sender = (lastMessage["senderUsername"] as? String)
                if (!sender.isNullOrBlank() && text.isNotBlank()) {
                    "$sender: $text"
                } else {
                    text
                }
            }
            else -> ""
        }
    }
}

enum class TaskStatus(val id: String, val title: String, val icon: String) {
    TODO("todo", "К выполнению", "📥"),
    IN_PROGRESS("in_progress", "В работе", "⚡"),
    REVIEW("review", "На проверке", "👀"),
    DONE("done", "Готово", "✅");

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: TODO
    }
}

enum class TaskPriority(val id: String, val title: String, val icon: String, val hexColor: String) {
    LOW("low", "Низкий", "🟢", "#A8DB6E"),
    MEDIUM("medium", "Средний", "🟡", "#FFC24E"),
    HIGH("high", "Высокий", "🔴", "#FF8A48"),
    URGENT("urgent", "Срочный", "🔥", "#FF4D6A");

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: MEDIUM
    }
}

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "todo", // "todo", "in_progress", "review", "done"
    val priority: String = "medium", // "low", "medium", "high", "urgent"
    val assigneeId: String? = null,
    val assigneeName: String? = null,
    val assigneeAvatar: String? = null,
    val dueDate: String? = null,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

fun DocumentSnapshot.toTopicOrNull(): Topic? {
    try {
        val topic = this.toObject(Topic::class.java)?.copy(id = this.id)
        val isGen = this.getBoolean("isGeneral") ?: (topic?.isGeneral == true)
        val isCls = this.getBoolean("isClosed") ?: (topic?.isClosed == true)
        val tTitle = this.getString("title") ?: topic?.title ?: ""
        val tIcon = this.getString("icon") ?: topic?.icon
        val tColor = this.getString("color") ?: topic?.color
        val tType = this.getString("type") ?: topic?.type ?: "chat"
        val tCreatedBy = this.getString("createdBy") ?: topic?.createdBy ?: ""
        val tCreatedAt = this.getTimestamp("createdAt") ?: topic?.createdAt
        val tLastMsgAt = this.getTimestamp("lastMessageAt") ?: topic?.lastMessageAt
        val tLastMsg = this.get("lastMessage") ?: topic?.lastMessage
        val tUnread = (this.getLong("unreadCount")?.toInt()) ?: topic?.unreadCount ?: 0

        return Topic(
            id = this.id,
            title = tTitle,
            icon = tIcon,
            color = tColor,
            type = tType,
            isGeneral = isGen,
            isClosed = isCls,
            createdBy = tCreatedBy,
            createdAt = tCreatedAt,
            lastMessageAt = tLastMsgAt,
            lastMessage = tLastMsg,
            unreadCount = tUnread
        )
    } catch (e: Exception) {
        android.util.Log.e("TopicParser", "Error parsing topic doc $id", e)
        return null
    }
}

fun DocumentSnapshot.toTaskItemOrNull(): TaskItem? {
    try {
        val task = this.toObject(TaskItem::class.java)?.copy(id = this.id)
        return TaskItem(
            id = this.id,
            title = this.getString("title") ?: task?.title ?: "",
            description = this.getString("description") ?: task?.description ?: "",
            status = this.getString("status") ?: task?.status ?: "todo",
            priority = this.getString("priority") ?: task?.priority ?: "medium",
            assigneeId = this.getString("assigneeId") ?: task?.assigneeId,
            assigneeName = this.getString("assigneeName") ?: task?.assigneeName,
            assigneeAvatar = this.getString("assigneeAvatar") ?: task?.assigneeAvatar,
            dueDate = this.getString("dueDate") ?: task?.dueDate,
            createdBy = this.getString("createdBy") ?: task?.createdBy ?: "",
            createdByName = this.getString("createdByName") ?: task?.createdByName ?: "",
            createdAt = this.getTimestamp("createdAt") ?: task?.createdAt,
            updatedAt = this.getTimestamp("updatedAt") ?: task?.updatedAt
        )
    } catch (e: Exception) {
        android.util.Log.e("TaskParser", "Error parsing task doc $id", e)
        return null
    }
}
