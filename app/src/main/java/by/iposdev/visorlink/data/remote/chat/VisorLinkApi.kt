package by.iposdev.visorlink.data.remote.chat

import retrofit2.http.*

interface VisorLinkApi {

    // --- Users API ---

    @GET("/api/users/me")
    suspend fun getMyProfile(): UserDto

    @GET("/api/users/{uid}")
    suspend fun getUserProfile(@Path("uid") uid: String): UserDto

    @POST("/api/users/sync")
    suspend fun syncUser(@Body request: SyncUserRequest): UserDto

    @GET("/api/users/search")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Query("limit") limit: Int = 50,
        @Query("raw") raw: String = "true"
    ): List<UserDto>

    @GET("/api/users")
    suspend fun getUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("raw") raw: String = "true"
    ): List<UserDto>

    @GET("/api/users/check-username/{username}")
    suspend fun checkUsername(@Path("username") username: String): CheckUsernameResponse

    @PUT("/api/users/me/username")
    suspend fun updateUsername(@Body request: UpdateUsernameRequest): UserDto

    @PUT("/api/users/me/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserDto

    @POST("/api/users/me/fcm-token")
    suspend fun saveFcmToken(@Body request: FcmTokenRequest): SimpleSuccessResponse

    @HTTP(method = "DELETE", path = "/api/users/me/fcm-token", hasBody = true)
    suspend fun removeFcmToken(@Body request: FcmTokenRequest): SimpleSuccessResponse

    @POST("/api/users/me/streak")
    suspend fun claimStreak(): StreakResponse

    @POST("/api/users/me/buy-pro")
    suspend fun buyPro(@Body request: BuyProRequest): BuyProResponse

    @GET("/api/stickers/my")
    suspend fun getMyStickers(): List<StickerPackDto>

    @GET("/api/sticker-packs")
    suspend fun getStickerPacks(): List<StickerPackDto>

    @POST("/api/sticker-packs")
    suspend fun createStickerPack(@Body request: StickerPackDto): StickerPackDto

    @POST("/api/sticker-packs/{packId}/install")
    suspend fun installStickerPack(@Path("packId") packId: String): SimpleSuccessResponse

    @DELETE("/api/sticker-packs/{packId}")
    suspend fun deleteStickerPack(@Path("packId") packId: String): SimpleSuccessResponse

    // --- Chats API ---

    @GET("/api/chats")
    suspend fun getChats(@Query("raw") raw: String = "true"): List<ChatDto>

    @GET("/api/chats/{chatId}")
    suspend fun getChat(@Path("chatId") chatId: String): ChatDto

    @POST("/api/chats/create")
    suspend fun createChat(@Body request: CreateChatRequest): ChatDto

    @POST("/api/chats/direct")
    suspend fun createDirectChat(@Body request: CreateChatRequest): ChatDto

    @GET("/api/chats/find-by-tag/{tag}")
    suspend fun findByTag(@Path("tag") tag: String): ChatDto

    @POST("/api/chats/join/tag")
    suspend fun joinByTag(@Body request: JoinByTagRequest): ChatDto

    @POST("/api/chats/join/invite")
    suspend fun joinByInvite(@Body request: JoinByInviteRequest): ChatDto

    @POST("/api/chats/{chatId}/invite-user")
    suspend fun inviteUser(@Path("chatId") chatId: String, @Body request: InviteUserRequest): SimpleSuccessResponse

    @POST("/api/chats/{chatId}/leave")
    suspend fun leaveChat(@Path("chatId") chatId: String): SimpleSuccessResponse

    @DELETE("/api/chats/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: String): SimpleSuccessResponse

    @GET("/api/chats/{chatId}/members")
    suspend fun getChatMembers(@Path("chatId") chatId: String): List<ChatMemberDto>

    @POST("/api/chats/{chatId}/moderate")
    suspend fun moderateUser(@Path("chatId") chatId: String, @Body request: ModerateUserRequest): SimpleSuccessResponse

    @PUT("/api/chats/{chatId}/members/{targetUid}/role")
    suspend fun setMemberRole(
        @Path("chatId") chatId: String,
        @Path("targetUid") targetUid: String,
        @Body request: SetRoleRequest
    ): SimpleSuccessResponse

    @PUT("/api/chats/{chatId}/settings")
    suspend fun updateChatSettings(
        @Path("chatId") chatId: String,
        @Body request: UpdateChatSettingsRequest
    ): ChatDto

    @POST("/api/chats/{chatId}/invite-link/regenerate")
    suspend fun regenerateInviteLink(@Path("chatId") chatId: String): RegenerateInviteLinkResponse

    // --- Messages API ---

    @GET("/api/chats/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): List<MessageDto>

    @POST("/api/messages/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): MessageDto

    @POST("/api/chats/{chatId}/messages")
    suspend fun sendChatMessage(@Path("chatId") chatId: String, @Body request: SendMessageRequest): MessageDto

    @POST("/api/chats/{chatId}/messages/album")
    suspend fun sendAlbum(@Path("chatId") chatId: String, @Body request: SendAlbumRequest): MessageDto

    @PUT("/api/chats/{chatId}/messages/{msgId}")
    suspend fun editMessage(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String,
        @Body request: EditMessageRequest
    ): MessageDto

    @DELETE("/api/chats/{chatId}/messages/{msgId}")
    suspend fun deleteMessage(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String
    ): SimpleSuccessResponse

    @PUT("/api/chats/{chatId}/messages/{msgId}/reactions")
    suspend fun toggleReaction(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String,
        @Body request: ReactionRequest
    ): MessageDto

    @PUT("/api/chats/{chatId}/messages/{msgId}/read")
    suspend fun markMessageAsRead(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String
    ): SimpleSuccessResponse

    @POST("/api/chats/{chatId}/messages/{msgId}/redeem-gift")
    suspend fun redeemGift(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String
    ): RedeemGiftResponse

    // --- Comments API ---

    @GET("/api/chats/{chatId}/messages/{msgId}/comments")
    suspend fun getComments(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String
    ): List<CommentDto>

    @POST("/api/chats/{chatId}/messages/{msgId}/comments")
    suspend fun addComment(
        @Path("chatId") chatId: String,
        @Path("msgId") msgId: String,
        @Body request: AddCommentRequest
    ): CommentDto

    // --- Feed API ---

    @GET("/api/feed")
    suspend fun getFeed(@Query("limit") limit: Int = 30): List<FeedItemDto>

    @POST("/api/feed/{id}/like")
    suspend fun toggleFeedLike(@Path("id") id: String): ToggleLikeResponse

    @POST("/api/feed/{id}/toggle-comments")
    suspend fun toggleFeedComments(@Path("id") id: String): ToggleCommentsResponse

    // --- Invites & Notifications ---

    @GET("/api/invites")
    suspend fun getInvites(): List<InviteDto>

    @POST("/api/invites/{id}/respond")
    suspend fun respondToInvite(
        @Path("id") id: String,
        @Body request: RespondInviteRequest
    ): SimpleSuccessResponse

    @GET("/api/notifications")
    suspend fun getNotifications(): List<NotificationDto>

    // --- DM Bots ---

    @GET("/api/bots/dm")
    suspend fun listDmBots(): DmBotsResponse

    @POST("/api/bots/dm")
    suspend fun createDmBot(@Body request: CreateDmBotRequest): CreateDmBotResponse

    @POST("/api/bots/dm/{botUid}/regenerate-token")
    suspend fun regenerateDmBotToken(@Path("botUid") botUid: String): RegenerateTokenResponse

    @DELETE("/api/bots/dm/{botUid}")
    suspend fun deleteDmBot(@Path("botUid") botUid: String): SimpleSuccessResponse

    // --- Telegram ---

    @POST("/api/telegram/generate-code")
    suspend fun generateTelegramCode(): TelegramCodeResponse

    @POST("/api/telegram/unbind")
    suspend fun unbindTelegram(): SimpleSuccessResponse

    // --- Internal Docs ---

    @GET("/api/internal/{key}")
    suspend fun getInternalDocument(@Path("key") key: String): InternalDocResponse

    // --- Health Check ---

    @GET("/health")
    suspend fun getHealth(): HealthResponseDto
}

