package org.visorlink.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel: ViewModel() {
    data class AuthState(
        val isLoading: Boolean = false,
        val isSuccess: Boolean = false,
        val errorText:String = ""
    )
   private val _uiState = MutableStateFlow(AuthState())
    val uiState = _uiState.asStateFlow()
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isSuccess = false, errorText = "")
            val auth = FirebaseAuth.getInstance()
            try {
                auth.signInWithEmailAndPassword(email, password).await()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = false, errorText = e.message ?: "Ошибка! $e")
            }
            _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
        }

    }
}

