package by.iposdev.visorlink.data.model

import com.google.firebase.Timestamp

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
    val fcmTokens: List<String> = emptyList()
)

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
    val lastMessage: String? = null,
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
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

// ─── v4: Two-level comments-allowed check ─────────────────────────────────────

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
    val url: String = "",
    val fileName: String = "",
    val spoiler: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "url"      to url,
        "fileName" to fileName,
        "spoiler"  to spoiler
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
    // ─── Sticker Pack fields ──────────────────────────────────────────────────
    val packId: String? = null,
    val packName: String? = null,
    val packEmoji: String? = null,
    // ─────────────────────────────────────────────────────────────────────────
    val createdAt: Timestamp? = null,
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val replyTo: Map<String, Any?>? = null,
    val reactions: List<Map<String, Any>> = emptyList(),
    val readBy: List<String> = emptyList(),
    val spoiler: Boolean = false,
    // ─── v4 ───────────────────────────────────────────────────────────────────
    val commentsEnabled: Boolean? = null,
    val commentsCount: Int = 0,
    // ─── Album ────────────────────────────────────────────────────────────────
    val caption: String? = null,
    val images: List<AlbumImage> = emptyList()
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
}

object MessageType {
    const val TEXT    = "text"
    const val IMAGE   = "image"
    const val VOICE   = "voice"
    const val STICKER = "sticker"
    const val ALBUM   = "album"
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

// ─── Legacy Sticker (kept for backward compat — old user sticker collection) ──

data class Sticker(
    val id: String = "",
    val url: String = "",
    val name: String = "",
    val storagePath: String = "",
    val createdAt: Timestamp? = null
)

// ─── v4: Comment ──────────────────────────────────────────────────────────────

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
    val createdAt: Timestamp? = null
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

// ─── Presence / Topbar ───────────────────────────────────────────────────────

data class PresenceData(val online: Boolean = false, val lastSeen: Long? = null)

sealed class TopbarStatus {
    object Online : TopbarStatus()
    object Typing : TopbarStatus()
    object Offline : TopbarStatus()
    data class LastSeen(val ts: Long?) : TopbarStatus()
    data class MemberCount(val total: Int, val online: Int) : TopbarStatus()
}

// ─── Message list items ───────────────────────────────────────────────────────

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DateHeader(val label: String) : MessageListItem()
}

// ─── Theme ────────────────────────────────────────────────────────────────────

enum class AppTheme {
    MATERIAL3_EXPRESSIVE,
    ONE_UI,
    EXTHRU,
}
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class AppSettings(
    val hapticFeedback: Boolean = true,
    val notificationsEnabled: Boolean = true
)