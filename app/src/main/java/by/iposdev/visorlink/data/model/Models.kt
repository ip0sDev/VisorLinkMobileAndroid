package by.iposdev.visorlink.data.model

import com.google.firebase.Timestamp

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
    val updatedAt: Timestamp? = null
)

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantData: Map<String, Map<String, String>> = emptyMap(),
    val lastMessage: String? = null,
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
    fun otherParticipantId(currentUid: String) =
        participants.firstOrNull { it != currentUid } ?: ""

    fun otherDisplayName(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("displayName") ?: ""

    fun otherUsername(currentUid: String) =
        participantData[otherParticipantId(currentUid)]?.get("username") ?: ""
}

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
    val createdAt: Timestamp? = null,
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val replyTo: Map<String, Any?>? = null,
    val reactions: List<Map<String, Any>> = emptyList()
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
                    uids = map["uids"] as? List<String> ?: emptyList(),
                    count = (map["count"] as? Long)?.toInt() ?: 0
                )
            } catch (e: Exception) { null }
        }
}

object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VOICE = "voice"
    const val STICKER = "sticker"
}

data class ReplyData(
    val id: String,
    val type: String,
    val text: String?,
    val url: String?,
    val senderUsername: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "type" to type,
        "text" to text, "url" to url,
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

enum class AppTheme {
    MATERIAL3_EXPRESSIVE,
    ONE_UI
}