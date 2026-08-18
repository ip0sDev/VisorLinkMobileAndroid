package by.iposdev.visorlink.data.remote.chat

import com.google.gson.annotations.SerializedName

// --- DTO ---

data class MessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("senderId") val senderId: String,
    @SerializedName("senderUsername") val senderUsername: String,
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("packId") val packId: String? = null,
    @SerializedName("packName") val packName: String? = null,
    @SerializedName("packEmoji") val packEmoji: String? = null,
    @SerializedName("createdAt") val createdAt: Long, // Unix ms
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("replyTo") val replyTo: ReplyDto? = null,
    @SerializedName("reactions") val reactions: List<ReactionDto> = emptyList(),
    @SerializedName("readBy") val readBy: List<String> = emptyList(),
    @SerializedName("spoiler") val spoiler: Boolean = false,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("images") val images: List<AlbumImageDto> = emptyList(),
    @SerializedName("chatId") val chatId: String? = null
)

data class ReplyDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("senderUsername") val senderUsername: String
)

data class ReactionDto(
    @SerializedName("emoji") val emoji: String,
    @SerializedName("uids") val uids: List<String>,
    @SerializedName("count") val count: Int
)

data class AlbumImageDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("fileName") val fileName: String = "",
    @SerializedName("spoiler") val spoiler: Boolean = false
)

data class ChatDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("participants") val participants: List<String>,
    @SerializedName("participantData") val participantData: Map<String, ParticipantDataDto>,
    @SerializedName("lastMessage") val lastMessage: String? = null,
    @SerializedName("lastMessageAt") val lastMessageAt: Long? = null,
    @SerializedName("createdAt") val createdAt: Long
)

data class ParticipantDataDto(
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("isOnline") val isOnline: Boolean
)

// --- Requests ---

data class CreateChatRequest(
    @SerializedName("type") val type: String,
    @SerializedName("targetUid") val targetUid: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("participantIds") val participantIds: List<String> = emptyList()
)

data class SendMessageRequest(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String? = null,
    @SerializedName("replyToId") val replyToId: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("packId") val packId: String? = null,
    @SerializedName("packName") val packName: String? = null,
    @SerializedName("packEmoji") val packEmoji: String? = null
)
