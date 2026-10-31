package org.visorlink.app.ui.screens.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.visorlink.app.BuildConfig

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
            _uiState.value = _uiState.value.copy(isLoading = true, isSuccess = false, errorText = "")
            try {
                auth.signInWithEmailAndPassword(email, password).await()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = false, errorText = e.message ?: "Ошибка! $e")
            }
            // Android (Kotlin)
            val data = hashMapOf(
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}", // до 60 символов, напр. "Google Pixel 8"
                "osVersion" to Build.VERSION.RELEASE,                    // до 20 символов, напр. "14"
                "appVersion" to BuildConfig.VERSION_NAME                 // до 20 символов, напр. "1.4.0"
            )

            Firebase.functions("europe-west1")
                .getHttpsCallable("registerSession")
                .call(data)
                .await()
            _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true, errorText = "")
        }

    }
}