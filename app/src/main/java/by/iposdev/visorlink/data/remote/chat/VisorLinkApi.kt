package by.iposdev.visorlink.data.remote.chat

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VisorLinkApi {
    @GET("/api/users/me")
    suspend fun getMyProfile(): UserDto

    @GET("/api/users/{uid}")
    suspend fun getUserProfile(@Path("uid") uid: String): UserDto

    @POST("/api/users/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserDto

    @GET("/api/users/search")
    suspend fun searchUsers(@Query("query") query: String): List<UserDto>

    @GET("/api/chats")
    suspend fun getChats(): List<ChatDto>

    @GET("/api/feed")
    suspend fun getFeed(): List<FeedItemDto>

    @GET("/api/chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: String): List<MessageDto>

    @POST("/api/chats/create")
    suspend fun createChat(@Body request: CreateChatRequest): ChatDto

    @POST("/api/messages/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): MessageDto
}
