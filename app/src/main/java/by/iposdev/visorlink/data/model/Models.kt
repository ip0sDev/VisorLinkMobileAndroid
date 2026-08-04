package by.iposdev.visorlink.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.IgnoreExtraProperties

// ─── User ─────────────────────────────────────────────────────────────────────

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

    val isAdmin: Boolean = false,
    val isBot: Boolean = false,
    val botBadge: String = "unverified",
    val ownerId: String? = null,
    val bits: Int = 0,
    val streak: Int = 0,
    val showStreak: Boolean = true,
    val proUntil: Timestamp? = null,
    val trialUsed: Boolean = false,
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
    val lastBitsClaim: Timestamp? = null
) {
    fun isProActive(): Boolean {
        if (proUntil == null) return false
        return proUntil.toDate().time > System.currentTimeMillis()
    }
}

// ─── Chat ─────────────────────────────────────────────────────────────────────

enum class ChatType { DIRECT, GROUP, CHANNEL }

data class ChatSettings(
    val joinByLink: Boolean = true,
    val joinByTag: Boolean = false,
    val allowReactions: Boolean = true,
    val allowComments: Boolean = true,
    val inviteLink: String = ""
)

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
    val settings: ChatSettings = ChatSettings(),
    val lastMessage: Any? = null,
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
    fun lastMessageText(): String {
        return when (lastMessage) {
            is String -> lastMessage
            is Map<*, *> -> (lastMessage["text"] as? String) ?: ""
            else -> ""
        }
    }

    fun chatType() = when (type) {
        "group"   -> ChatType.GROUP
        "channel" -> ChatType.CHANNEL
        else      -> ChatType.DIRECT
    }

    fun displayName(currentUid: String) = when (chatType()) {
        ChatType.DIRECT -> participantData[otherParticipantId(currentUid)]?.get("displayName") ?: ""
        else            -> name
    }

    fun otherParticipantId(currentUid: String) =
        participants.firstOrNull { it != currentUid } ?: ""

    fun otherDisplayName(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("displayName") ?: ""

    fun otherUsername(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("username") ?: ""
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
    ChatType.DIRECT  -> true
    ChatType.GROUP   -> myMember?.canSend() ?: false
    ChatType.CHANNEL -> myMember?.isAdmin() ?: false
}

fun canSendMedia(myMember: Member?, chatType: ChatType): Boolean = when (chatType) {
    ChatType.DIRECT  -> true
    ChatType.CHANNEL -> myMember?.isAdmin() ?: false
    ChatType.GROUP   -> myMember?.canSend() == true && myMember.mediaRestricted == false
}

fun canReact(chat: Chat, chatType: ChatType): Boolean {
    if (chatType != ChatType.CHANNEL) return true
    return chat.settings.allowReactions
}

fun commentsAllowed(channel: Chat, post: Message): Boolean {
    if (channel.settings.allowComments == false) return false
    if (post.commentsEnabled == false) return false
    return true
}

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

data class AlbumImage(
    val url: String? = null,
    val cdnMediaId: String? = null,
    val fileName: String = "",
    val spoiler: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "url"        to url,
        "cdnMediaId" to cdnMediaId,
        "fileName"   to fileName,
        "spoiler"    to spoiler
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
    val stickers: List<StickerItem> = emptyList()
)

data class StickerItem(
    val id: String = "",
    val url: String = "",
    val emoji: String = "🎭",
    val storagePath: String = "",
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null
)

data class UserStickerData(
    val packIds: List<String> = emptyList()
)

// ─── Message ──────────────────────────────────────────────────────────────────

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
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
    val caption: String? = null,
    val images: List<AlbumImage> = emptyList(),
    val forwardFrom: Map<String, Any?>? = null,

    // Telegram Bot Forwarding
    val tg_forwarded: Boolean? = null,
    val tg_forwarded_from: String? = null,
    val tg_forwarded_from_fallback: String? = null,
    val isUnofficialClient: Boolean? = null,

    // Подарки
    val redeemed: Boolean = false,
    val redeemedByUid: String? = null,
    val redeemedByUsername: String? = null,
    val giftType: String? = null,

    // CDN / Временные файлы
    val cdnMediaId: String? = null,
    val mimeType: String? = null,
    val uploadProgress: Float? = null,
    val localFile: java.io.File? = null,
    val localBytes: ByteArray? = null,
    val status: String = "sent"
) {
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
}

object MessageType {
    const val TEXT    = "text"
    const val IMAGE   = "image"
    const val VOICE   = "voice"
    const val STICKER = "sticker"
    const val ALBUM   = "album"
    const val GIFT    = "gift"
    const val VIDEO   = "video"
    const val GIF     = "gif"
}

object SendStatus {
    const val SENDING = "sending"
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

// ─── Feed ──────────────────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class FeedChannelData(
    var name: String? = null,
    var avatarUrl: String? = null,
    var avatar_url: String? = null,
    var tag: String? = null
)

@IgnoreExtraProperties
data class FeedItem(
    var id: String = "",
    var chatId: String? = null,
    var messageId: String? = null,
    var channelData: FeedChannelData? = null,
    var channel_data: FeedChannelData? = null,
    var authorData: FeedChannelData? = null,

    // Web version might put author name at root too
    var author_name: String? = null,
    var authorName: String? = null,
    var author_avatar_url: String? = null,
    var authorAvatarUrl: String? = null,

    var type: String = "post",
    var title: String? = null,
    var text: String? = null,
    var caption: String? = null,
    var url: String? = null,
    var cdnMediaId: String? = null,
    var images: List<AlbumImage>? = null,
    var duration: Int? = null,
    var tags: List<String> = emptyList(),

    // Field names from web
    var likeCount: Int = 0,
    var likers: List<String> = emptyList(),

    // Field names from previous turn (fallback)
    var likes_count: Int = 0,
    var liked_uids: List<String> = emptyList(),
    var views_count: Int = 0,
    var comments_count: Int = 0,

    var createdAt: Timestamp? = null
) {
    val displayAuthorName: String
        get() = (channelData?.name ?: channel_data?.name ?: authorData?.name ?: author_name ?: authorName ?: "Unknown Channel").ifEmpty { "Unknown Channel" }

    val displayAuthorAvatarUrl: String?
        get() = channelData?.avatarUrl ?: channelData?.avatar_url ?: channel_data?.avatarUrl ?: channel_data?.avatar_url ?: authorData?.avatarUrl ?: author_avatar_url ?: authorAvatarUrl

    val displayChatId: String?
        get() = chatId

    val displayLikesCount: Int
        get() = if (likeCount != 0) likeCount else likes_count

    val displayViewsCount: Int
        get() = views_count

    val displayCommentsCount: Int
        get() = comments_count

    val displayLikedUids: List<String>
        get() = if (likers.isNotEmpty()) likers else liked_uids
}

data class PresenceData(val online: Boolean = false, val lastSeen: Long? = null)

sealed class TopbarStatus {
    object Online : TopbarStatus()
    object Typing : TopbarStatus()
    object Offline : TopbarStatus()
    data class LastSeen(val ts: Long?) : TopbarStatus()
    data class MemberCount(val total: Int, val online: Int) : TopbarStatus()
}

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DateHeader(val label: String) : MessageListItem()
}

enum class AppTheme {
    MATERIAL3_EXPRESSIVE,
    @Deprecated("Заменяется на BIOLUME/FORGE — оставлено для совместимости")
    ONE_UI,
    @Deprecated("Используй BIOLUME", ReplaceWith("BIOLUME"))
    EXTHRU,
    BIOLUME,
    FORGE,
}

val AppTheme.isExthruFamily: Boolean
    @Suppress("DEPRECATION")
    get() = this == AppTheme.BIOLUME || this == AppTheme.FORGE || this == AppTheme.EXTHRU

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