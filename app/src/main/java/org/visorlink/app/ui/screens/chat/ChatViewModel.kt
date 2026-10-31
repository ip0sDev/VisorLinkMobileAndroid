package org.visorlink.app.ui.screens.chat

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {

    data class Message(
        val id: String = "",
        val text: String = "",
        val senderId: String = "",
        val senderUsername: String = "",
        val seq: Long? = null,
        val type: String = "text",
        val createdAt: Timestamp? = null,
        val readBy: List<String> = emptyList(),
        val deleted: Boolean = false,
        val replyTo: ReplyTo? = null
    )
    data class ReplyTo(
        val id: String = "",
        val text: String = "",
        val senderUsername: String = ""
    )
    data class MessagesState(
        val chats: List<Message> = emptyList(),
        val isLoading: Boolean = false,
        val errorText: String? = null
    )

    private val _uiState = MutableStateFlow(MessagesState())
    val uiState = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

    fun loadChat(chatId: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _uiState.value = MessagesState(errorText = "Пользователь не авторизован")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorText = null)

            val messagesQuery = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
            val messagesSnapshot = messagesQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TAG","Error loading Chats!")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorText = error.localizedMessage
                    )
                }

                if (snapshot != null) {
                    val items = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Message::class.java)?.copy(id = doc.id)
                    }
                    _uiState.value = _uiState.value.copy(
                        chats = items?.reversed() ?: emptyList(),
                        isLoading = false
                    )
                    return@addSnapshotListener
                }
            }

        }
    }
}