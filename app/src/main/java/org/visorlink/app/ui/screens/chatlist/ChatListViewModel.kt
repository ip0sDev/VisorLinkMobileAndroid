package org.visorlink.app.ui.screens.chatlist

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel: ViewModel() {
    data class ChatListState(
        val avatarUrl: String = "",
        val errorText: String = "",
        val userName: String = "",
        val uid: String = "",
    )
    private val _uiState = MutableStateFlow(ChatListState())
    val uiState = _uiState.asStateFlow()
    fun getData(errorText: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorText = "", userName = "",uid="", avatarUrl = "")
            val auth = FirebaseAuth.getInstance()
            val db = Firebase.firestore
            val userId = auth.currentUser?.uid
            val docRef = db.collection("users").document("$userId")
            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        Log.d(TAG, "DocumentSnapshot data: ${document.data}")
                        val displayName = document.get("displayName")
                        _uiState.value = _uiState.value.copy(errorText = "${document.data}", userName = "$displayName",uid="$userId", avatarUrl = "${document.get("avatarUrl")}")
                    } else {
                        Log.d(TAG, "No such document")
                        _uiState.value = _uiState.value.copy(errorText = "No such document with UID $userId")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "get failed with ", exception)
                    _uiState.value = _uiState.value.copy(errorText = "$exception")
                }
        }

    }
}