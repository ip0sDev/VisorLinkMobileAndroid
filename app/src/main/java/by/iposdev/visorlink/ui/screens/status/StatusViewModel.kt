package by.iposdev.visorlink.ui.screens.status

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import by.iposdev.visorlink.data.model.Incident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date

data class ServiceStatus(
    val name: String,
    val isUp: Boolean,
    val lastCheck: Date = Date(),
    val error: String? = null
)

data class StatusUiState(
    val services: List<ServiceStatus> = emptyList(),
    val incidents: List<Incident> = emptyList(),
    val isRefreshing: Boolean = false
)

class StatusViewModel(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient()

    init {
        refreshStatus()
        listenIncidents()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            val cdnStatus = withContext(Dispatchers.IO) { checkCdn() }
            val firestoreStatus = checkFirestore()

            _uiState.update { it.copy(
                services = listOf(cdnStatus, firestoreStatus),
                isRefreshing = false
            ) }

            if (!cdnStatus.isUp) {
                reportIncident("VisorLink CDN", cdnStatus.error ?: "Unknown error")
            }
            if (!firestoreStatus.isUp) {
                reportIncident("Firestore", firestoreStatus.error ?: "Unknown error")
            }
        }
    }

    private suspend fun checkCdn(): ServiceStatus {
        return try {
            val request = Request.Builder()
                .url("https://api.visorlink.org/ping")
                .header("User-Agent", "VisorLink/Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ServiceStatus("VisorLink CDN", true)
                } else {
                    ServiceStatus("VisorLink CDN", false, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            ServiceStatus("VisorLink CDN", false, error = e.message ?: "Network error")
        }
    }

    private suspend fun checkFirestore(): ServiceStatus {
        return try {
            // Пробное чтение 1 документа из коллекции tags
            db.collection("tags").limit(1).get().await()
            ServiceStatus("Firestore", true)
        } catch (e: Exception) {
            ServiceStatus("Firestore", false, error = e.message)
        }
    }

    private fun listenIncidents() {
        db.collection("incidents")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("StatusVM", "Listen incidents error: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Incident::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("StatusVM", "Parse incident error: ${e.message}")
                        null
                    }
                } ?: emptyList()
                _uiState.update { it.copy(incidents = list) }
            }
    }

    private fun reportIncident(service: String, error: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("reportServiceIncident")
                    .call(mapOf("service" to service, "errorTelemetry" to error))
                    .await()
            } catch (_: Exception) {}
        }
    }
}
