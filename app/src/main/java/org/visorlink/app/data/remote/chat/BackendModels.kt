package org.visorlink.app.data.remote.chat

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// --- DTO ---

data class MessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("senderId") val senderId: String,
    @SerializedName("senderUsername") val senderUsername: String,
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("packId") val packId: String? = null,
    @SerializedName("packName") val packName: String? = null,
    @SerializedName("packEmoji") val packEmoji: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("performer") val performer: String? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("coverCdnMediaId") val coverCdnMediaId: String? = null,
    @SerializedName("coverUrl") val coverUrl: String? = null,
    @SerializedName("images") val images: List<AlbumImageDto>? = null,
    @SerializedName("replyTo") val replyTo: ReplyDto? = null,
    @SerializedName("forwardFrom") val forwardFrom: ForwardDto? = null,
    @SerializedName("reactions") val reactions: List<ReactionDto>? = null,
    @SerializedName("readBy") val readBy: List<String>? = null,
    @SerializedName("spoiler") val spoiler: Boolean = false,
    @SerializedName("isBot") val isBot: Boolean = false,
    @SerializedName("botLabel") val botLabel: String? = null,
    @SerializedName("botBadge") val botBadge: String? = null,
    @SerializedName("commentsCount") val commentsCount: Int = 0,
    @SerializedName("commentsEnabled") val commentsEnabled: Boolean = true,
    @SerializedName("giftType") val giftType: String? = null,
    @SerializedName("redeemed") val redeemed: Boolean = false,
    @SerializedName("redeemedByUsername") val redeemedByUsername: String? = null,
    @SerializedName("topicId") val topicId: String? = null,
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("lastEdited") val lastEdited: Long? = null,
    @SerializedName("createdAt") val createdAt: Long // Unix ms
)

data class ReplyDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("senderUsername") val senderUsername: String
)

data class ForwardDto(
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("senderUsername") val senderUsername: String? = null,
    @SerializedName("chatId") val chatId: String? = null
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
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("participants") val participants: List<String>? = null,
    @SerializedName("memberIds") val memberIds: List<String>? = null,
    @SerializedName("participantData") val participantData: Map<String, ParticipantDataDto>? = null,
    @SerializedName("lastMessage") val lastMessage: JsonElement? = null,
    @SerializedName("lastMessageAt") val lastMessageAt: Long? = null,
    @SerializedName("memberCount") val memberCount: Int? = null,
    @SerializedName("badge") val badge: String? = null,
    @SerializedName("botAllowed") val botAllowed: Boolean? = null,
    @SerializedName("joinByLink") val joinByLink: Boolean? = null,
    @SerializedName("joinByTag") val joinByTag: Boolean? = null,
    @SerializedName("allowReactions") val allowReactions: Boolean? = null,
    @SerializedName("allowComments") val allowComments: Boolean? = null,
    @SerializedName("isForum") val isForum: Boolean? = null,
    @SerializedName("noForwards") val noForwards: Boolean? = null,
    @SerializedName("inviteLink") val inviteLink: String? = null,
    @SerializedName("createdAt") val createdAt: Long
) {
    val lastMessageText: String?
        get() = when {
            lastMessage == null || lastMessage.isJsonNull -> null
            lastMessage.isJsonPrimitive -> lastMessage.asString
            lastMessage.isJsonObject -> {
                val obj = lastMessage.asJsonObject
                obj.get("text")?.asString ?: obj.get("caption")?.asString
            }
            else -> null
        }
}

data class ParticipantDataDto(
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class ChatMemberDto(
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("userId") val userId: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("role") val role: String = "member", // "owner" | "admin" | "member"
    @SerializedName("joinedAt") val joinedAt: Long? = null,
    @SerializedName("muted") val muted: Boolean = false,
    @SerializedName("banned") val banned: Boolean = false
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("isOnline") val isOnline: Boolean = false,
    @SerializedName("lastSeenAt") val lastSeenAt: Long? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("bits") val bits: Int = 0,
    @SerializedName("streak") val streak: Int = 0,
    @SerializedName("showStreak") val showStreak: Boolean = true,
    @SerializedName("trialUsed") val trialUsed: Boolean = false,
    @SerializedName("isAdmin") val isAdmin: Boolean = false,
    @SerializedName("isBot") val isBot: Boolean = false,
    @SerializedName("botBadge") val botBadge: String? = null,
    @SerializedName("tfaEnabled") val tfaEnabled: Boolean = false,
    @SerializedName("registeredViaOfficialClient") val registeredViaOfficialClient: Boolean = true,
    @SerializedName("proUntil") val proUntil: Long? = null,
    @SerializedName("diaryEnabled") val diaryEnabled: Boolean? = null,
    @SerializedName("customization") val customizationRaw: com.google.gson.JsonElement? = null,
    @SerializedName("acceptedAt") val acceptedAt: Long? = null,
    @SerializedName("acceptedVersion") val acceptedVersion: String? = null
) {
    val actualId: String get() = uid ?: id

    val customization: Map<String, Any?>
        get() = parseCustomization(customizationRaw)

    companion object {
        fun parseCustomization(element: com.google.gson.JsonElement?): Map<String, Any?> {
            if (element == null || element.isJsonNull) return emptyMap()
            if (element.isJsonObject) {
                val map = mutableMapOf<String, Any?>()
                for ((k, v) in element.asJsonObject.entrySet()) {
                    map[k] = when {
                        v.isJsonNull -> null
                        v.isJsonPrimitive && v.asJsonPrimitive.isBoolean -> v.asBoolean
                        v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> v.asNumber
                        v.isJsonPrimitive && v.asJsonPrimitive.isString -> v.asString
                        else -> v.toString()
                    }
                }
                return map
            }
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                val str = element.asString
                return try {
                    val parsed = com.google.gson.JsonParser.parseString(str)
                    if (parsed.isJsonObject) parseCustomization(parsed) else emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            return emptyMap()
        }
    }
}

data class CommentDto(
    @SerializedName("id") val id: String,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("senderId") val senderId: String,
    @SerializedName("senderUsername") val senderUsername: String,
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("replyTo") val replyTo: ReplyDto? = null,
    @SerializedName("reactions") val reactions: List<ReactionDto>? = null,
    @SerializedName("spoiler") val spoiler: Boolean = false,
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("createdAt") val createdAt: Long
)

// --- Stickers ---

data class StickerPackDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("stickers") val stickers: List<StickerItemDto>? = null,
    @SerializedName("authorId") val authorId: String? = null,
    @SerializedName("installCount") val installCount: Int = 0
)

data class StickerItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("name") val name: String? = null
)

// --- Invites & Notifications ---

data class InviteDto(
    @SerializedName("id") val id: String,
    @SerializedName("chatId") val chatId: String,
    @SerializedName("chatName") val chatName: String? = null,
    @SerializedName("inviterId") val inviterId: String? = null,
    @SerializedName("inviterUsername") val inviterUsername: String? = null,
    @SerializedName("status") val status: String = "pending"
)

data class NotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("data") val data: Map<String, Any?>? = null,
    @SerializedName("read") val read: Boolean = false,
    @SerializedName("createdAt") val createdAt: Long
)

// --- DM Bots ---

data class DmBotDto(
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("botUid") val botUid: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("botToken") val botToken: String? = null
) {
    val actualUid: String get() = botUid ?: uid ?: ""
}

data class DmBotsResponse(
    @SerializedName("bots") val bots: List<DmBotDto>? = null
)

// --- Feed ---

data class FeedItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("channelName") val channelName: String? = null,
    @SerializedName("channelAvatar") val channelAvatar: String? = null,
    @SerializedName("channelTag") val channelTag: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("senderUsername") val senderUsername: String? = null,
    @SerializedName("type") val type: String = "post",
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("images") val images: List<AlbumImageDto>? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("authorName") val authorName: String? = null,
    @SerializedName("authorAvatarUrl") val authorAvatarUrl: String? = null,
    @SerializedName("likeCount") val likeCount: Int = 0,
    @SerializedName("likedByMe") val likedByMe: Boolean = false,
    @SerializedName("viewsCount") val viewsCount: Int = 0,
    @SerializedName("commentsCount") val commentsCount: Int = 0,
    @SerializedName("likers") val likers: List<String>? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("createdAt") val createdAt: Long = 0
)

// --- Requests & Responses ---

data class SyncUserRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("registeredViaOfficialClient") val registeredViaOfficialClient: Boolean = true
)

data class UpdateProfileRequest(
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("tfaEnabled") val tfaEnabled: Boolean? = null,
    @SerializedName("showStreak") val showStreak: Boolean? = null,
    @SerializedName("customization") val customization: Map<String, Any?>? = null,
    @SerializedName("acceptedAt") val acceptedAt: Long? = null,
    @SerializedName("acceptedVersion") val acceptedVersion: String? = null
)

data class UpdateUsernameRequest(
    @SerializedName("username") val username: String,
    @SerializedName("newUsername") val newUsername: String? = null,
    @SerializedName("displayName") val displayName: String? = null
)

data class CheckUsernameResponse(
    @SerializedName("username") val username: String,
    @SerializedName("available") val available: Boolean
)

data class FcmTokenRequest(
    @SerializedName("token") val token: String
)

data class StreakResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("claimed") val claimed: Boolean = false,
    @SerializedName("streak") val streak: Int = 0,
    @SerializedName("bits") val bits: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("hoursUntilNext") val hoursUntilNext: Int? = null
)

data class BuyProRequest(
    @SerializedName("useTrial") val useTrial: Boolean = false
)

data class BuyProResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("proUntil") val proUntil: Long? = null,
    @SerializedName("proUntilIso") val proUntilIso: String? = null,
    @SerializedName("bits") val bits: Int? = null,
    @SerializedName("message") val message: String? = null
)

data class CreateChatRequest(
    @SerializedName("type") val type: String = "direct",
    @SerializedName("targetUid") val targetUid: String? = null,
    @SerializedName("participantUid") val participantUid: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("participantIds") val participantIds: List<String> = emptyList(),
    @SerializedName("isForum") val isForum: Boolean = false
)

data class SendMessageRequest(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("replyToId") val replyToId: String? = null,
    @SerializedName("cdnMediaId") val cdnMediaId: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("packId") val packId: String? = null,
    @SerializedName("packName") val packName: String? = null,
    @SerializedName("packEmoji") val packEmoji: String? = null,
    @SerializedName("spoiler") val spoiler: Boolean = false,
    @SerializedName("images") val images: List<AlbumImageDto>? = null,
    @SerializedName("forwardFrom") val forwardFrom: ForwardDto? = null,
    @SerializedName("giftType") val giftType: String? = null,
    @SerializedName("topicId") val topicId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("performer") val performer: String? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("coverCdnMediaId") val coverCdnMediaId: String? = null
)

data class SendAlbumRequest(
    @SerializedName("images") val images: List<AlbumImageDto>,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("replyTo") val replyTo: ReplyDto? = null
)

data class EditMessageRequest(
    @SerializedName("text") val text: String,
    @SerializedName("caption") val caption: String? = null
)

data class ReactionRequest(
    @SerializedName("emoji") val emoji: String
)

data class AddCommentRequest(
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("stickerId") val stickerId: String? = null,
    @SerializedName("replyToId") val replyToId: String? = null,
    @SerializedName("spoiler") val spoiler: Boolean = false
)

data class CreateDmBotRequest(
    @SerializedName("name") val name: String,
    @SerializedName("username") val username: String
)

data class CreateDmBotResponse(
    @SerializedName("botUid") val botUid: String,
    @SerializedName("botToken") val botToken: String,
    @SerializedName("username") val username: String
)

data class RegenerateTokenResponse(
    @SerializedName("botToken") val botToken: String
)

data class JoinByTagRequest(
    @SerializedName("tag") val tag: String
)

data class JoinByInviteRequest(
    @SerializedName("inviteCode") val inviteCode: String
)

data class InviteUserRequest(
    @SerializedName("targetUid") val targetUid: String? = null,
    @SerializedName("targetUsername") val targetUsername: String? = null
)

data class ModerateUserRequest(
    @SerializedName("targetUid") val targetUid: String,
    @SerializedName("action") val action: String,
    @SerializedName("durationSeconds") val durationSeconds: Int? = null
)

data class SetRoleRequest(
    @SerializedName("targetUid") val targetUid: String,
    @SerializedName("role") val role: String
)

data class UpdateChatSettingsRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("joinByLink") val joinByLink: Boolean? = null,
    @SerializedName("joinByTag") val joinByTag: Boolean? = null,
    @SerializedName("allowReactions") val allowReactions: Boolean? = null,
    @SerializedName("allowComments") val allowComments: Boolean? = null,
    @SerializedName("noForwards") val noForwards: Boolean? = null,
    @SerializedName("botAllowed") val botAllowed: Boolean? = null
)

data class RegenerateInviteLinkResponse(
    @SerializedName("inviteLink") val inviteLink: String
)

data class RespondInviteRequest(
    @SerializedName("action") val action: String
)

data class SimpleSuccessResponse(
    @SerializedName("success") val success: Boolean = true
)

data class TelegramCodeResponse(
    @SerializedName("code") val code: String,
    @SerializedName("botUsername") val botUsername: String? = null
)

data class InternalDocResponse(
    @SerializedName("key") val key: String,
    @SerializedName("content") val content: String
)

data class UsersSearchResponse(
    @SerializedName("users") val users: List<UserDto>? = null,
    @SerializedName("data") val data: List<UserDto>? = null
)

data class ChatsResponse(
    @SerializedName("chats") val chats: List<ChatDto>? = null,
    @SerializedName("data") val data: List<ChatDto>? = null
)

data class ToggleLikeResponse(
    @SerializedName("liked") val liked: Boolean
)

data class ToggleCommentsResponse(
    @SerializedName("commentsEnabled") val commentsEnabled: Boolean
)

data class RedeemGiftResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("message") val message: MessageDto? = null
)

// --- WebSocket Models ---

data class WsEvent(
    @SerializedName("type") val type: String,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("data") val data: JsonElement? = null
)

data class TypingPayload(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("uid") val uid: String,
    @SerializedName("username") val username: String,
    @SerializedName("isTyping") val isTyping: String? = null
)

data class PresencePayload(
    @SerializedName("uid") val uid: String,
    @SerializedName("isOnline") val isOnline: String? = null,
    @SerializedName("lastSeenAt") val lastSeenAt: String? = null
)

data class DeleteMessagePayload(
    @SerializedName("id") val id: String,
    @SerializedName("chatId") val chatId: String? = null
)

// --- Health Check ---

data class HealthResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("database") val database: Boolean = false,
    @SerializedName("redis") val redis: Boolean = false
)

