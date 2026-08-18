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
    @SerializedName("reactions") val reactions: List<ReactionDto>? = null,
    @SerializedName("readBy") val readBy: List<String>? = null,
    @SerializedName("spoiler") val spoiler: Boolean = false,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("images") val images: List<AlbumImageDto>? = null,
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
    @SerializedName("name") val name: String? = null,
    @SerializedName("participants") val participants: List<String>? = null,
    @SerializedName("participantData") val participantData: Map<String, ParticipantDataDto>? = null,
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
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("isOnline") val isOnline: Boolean = false,
    @SerializedName("isAdmin") val isAdmin: Boolean = false,
    @SerializedName("diaryEnabled") val diaryEnabled: Boolean = false,
    @SerializedName("customization") val customization: Map<String, Any?>? = null
)

// --- Feed ---

data class FeedItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("type") val type: String = "post",
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("images") val images: List<AlbumImageDto>? = null,
    @SerializedName("authorName") val authorName: String? = null,
    @SerializedName("authorAvatarUrl") val authorAvatarUrl: String? = null,
    @SerializedName("likeCount") val likeCount: Int = 0,
    @SerializedName("viewsCount") val viewsCount: Int = 0,
    @SerializedName("commentsCount") val commentsCount: Int = 0,
    @SerializedName("likers") val likers: List<String>? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("createdAt") val createdAt: Long
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

data class UpdateProfileRequest(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("customization") val customization: Map<String, Any?>? = null
)
