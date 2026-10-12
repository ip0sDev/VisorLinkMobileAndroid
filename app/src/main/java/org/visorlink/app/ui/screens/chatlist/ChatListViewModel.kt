package org.visorlink.app.ui.screens.chatlist

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatListViewModel : ViewModel() {

    data class ChatItem(
        val id: String = "",
        val title: String = "",
        val lastMessageAt: Timestamp? = null,
        val avatarUrl: String = "",
        val type: String = ""
    )

    data class ChatListState(
        val chats: List<ChatItem> = emptyList(),
        val isLoading: Boolean = false,
        val errorText: String? = null
    )

    private val _uiState = MutableStateFlow(ChatListState())
    val uiState = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

    fun loadChats() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _uiState.value = ChatListState(errorText = "Пользователь не авторизован")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorText = null)

            try {
                // 1. Запускаем оба запроса параллельно
                val directsDeferred = async {
                    db.collection("chats")
                        .whereArrayContains("participants", currentUserId)
                        .orderBy("lastMessageAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(10)
                        .get()
                        .await()
                }

                val groupsDeferred = async {
                    db.collection("chats")
                        .whereArrayContains("memberIds", currentUserId)
                        .orderBy("lastMessageAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(10)
                        .get()
                        .await()
                }

                // 2. Дожидаемся результатов обоих запросов
                val directDocs = directsDeferred.await().documents
                val groupDocs = groupsDeferred.await().documents

                // 3. Объединяем списки документов и убираем возможные дубликаты по id
                val allDocs = (directDocs + groupDocs).distinctBy { it.id }

                // 4. Парсим каждый документ через наш универсальный парсер
                // Вместо синхронного .map используем нормальный обход
                val chatList = allDocs.map { doc ->
                    val chatType = doc.getString("type") ?: "direct"
                    var title: String? = null
                    var avatarUrl: String? = null

                    if (chatType == "channel" || chatType == "group") {
                        title = doc.getString("name")
                        avatarUrl = doc.getString("avatarUrl")
                    } else {
                        // Личный диалог
                        val participantData = doc.get("participantData") as? Map<*, *>
                        val otherUserEntry = participantData?.entries?.firstOrNull {
                            it.key?.toString() != currentUserId
                        } ?: participantData?.entries?.firstOrNull()

                        val otherUserMap = otherUserEntry?.value as? Map<*, *>
                        val otherUserUid = otherUserEntry?.key?.toString()

                        title = otherUserMap?.get("displayName") as? String
                            ?: otherUserMap?.get("username") as? String

                        avatarUrl = otherUserMap?.get("avatarUrl") as? String

                        // ЕСЛИ АВАТАРКИ В participantData НЕТ — грузим через await() прямо здесь!
                        if (avatarUrl.isNullOrEmpty() && !otherUserUid.isNullOrEmpty()) {
                            try {
                                val userDoc = db.collection("users").document(otherUserUid).get().await()
                                if (userDoc.exists()) {
                                    avatarUrl = userDoc.getString("avatarUrl") ?: userDoc.getString("photoURL")
                                    // Если вдруг и имени не было, можем и его подтянуть
                                    if (title.isNullOrEmpty()) {
                                        title = userDoc.getString("displayName") ?: userDoc.getString("username")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось загрузить аватарку юзера $otherUserUid", e)
                            }
                        }
                    }

                    ChatItem(
                        id = doc.id,
                        title = title ?: doc.getString("name") ?: "Без названия",
                        avatarUrl = avatarUrl ?: "",
                        lastMessageAt = doc.getTimestamp("lastMessageAt"),
                        type = chatType
                    )
                }
                    // 5. Сортируем общий список по дате (самые свежие наверху) и берем топ-10
                    .sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
                    .take(10)

                // 6. Отдаем в UI
                _uiState.value = _uiState.value.copy(
                    chats = chatList,
                    isLoading = false
                )

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки чатов", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorText = e.localizedMessage ?: "Неизвестная ошибка"
                )
            }
        }
    }
}