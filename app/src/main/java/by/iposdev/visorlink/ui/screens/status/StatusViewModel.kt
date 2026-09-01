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
import java.util.concurrent.TimeUnit

data class ServiceStatus(
    val name: String,
    val isUp: Boolean,
    val lastCheck: Date = Date(),
    val error: String? = null
)

data class TimelineBar(
    val start: Long,
    val end: Long,
    val isUp: Boolean
)

data class StatusUiState(
    val services: List<ServiceStatus> = emptyList(),
    val incidents: List<Incident> = emptyList(),
    val timeline: List<TimelineBar> = emptyList(),
    val isRefreshing: Boolean = false
)

class StatusViewModel(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val client: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    // Используем чистый клиент для пинга (без интерцепторов аутентификации),
    // чтобы проверить именно доступность сервера.
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        refreshStatus()
        listenIncidents()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            // 1. Check VisorLink CDN
            val cdnStatus = withContext(Dispatchers.IO) { checkCdn() }
            if (cdnStatus.isUp) {
                resolveIncident("CDN")
            } else {
                reportIncident("CDN", cdnStatus.error ?: "HTTP 502 / Timeout")
            }

            // 2. Check Firebase Firestore
            val firestoreStatus = checkFirestore()
            if (firestoreStatus.isUp) {
                resolveIncident("Firestore")
            } else {
                reportIncident("Firestore", firestoreStatus.error ?: "Database Error")
            }

            // 3. Check Configuration Server (Flags)
            val flagsStatus = withContext(Dispatchers.IO) { checkFlagsServer() }
            if (flagsStatus.isUp) {
                resolveIncident("Config Server")
            } else {
                reportIncident("Config Server", flagsStatus.error ?: "Network error")
            }

            _uiState.update { it.copy(
                services = listOf(cdnStatus, firestoreStatus, flagsStatus),
                isRefreshing = false
            ) }
        }
    }

    private suspend fun checkCdn(): ServiceStatus {
        return try {
            val request = Request.Builder()
                .url("https://api.visorlink.org/ping")
                .header("User-Agent", "VisorLink/Android")
                .build()
            pingClient.newCall(request).execute().use { response ->
                // Любой ответ от сервера, кроме 5xx (ошибки сервера), означает, что CDN жива.
                // 404 или 401/403 — это ответы сервера, а не ошибки инфраструктуры.
                val isUp = response.code < 500
                ServiceStatus(
                    name = "CDN",
                    isUp = isUp,
                    error = if (!isUp) "HTTP ${response.code} (Server Error)" else null
                )
            }
        } catch (e: Exception) {
            ServiceStatus("CDN", false, error = e.message ?: "Network error")
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

    private suspend fun checkFlagsServer(): ServiceStatus {
        return try {
            val request = Request.Builder()
                .url("https://flags.visorlink.org/ping")
                .header("User-Agent", "VisorLink/Android")
                .build()
            pingClient.newCall(request).execute().use { response ->
                val isUp = response.code < 500
                ServiceStatus(
                    name = "Config Server",
                    isUp = isUp,
                    error = if (!isUp) "HTTP ${response.code}" else null
                )
            }
        } catch (e: Exception) {
            ServiceStatus("Config Server", false, error = e.message ?: "Network error")
        }
    }

    private fun listenIncidents() {
        db.collection("incidents")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("StatusVM", "Listen incidents error: ${error.message}")
                    return@addSnapshotListener
                }
                val remoteList = snap?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Incident::class.java)
                    } catch (e: Exception) {
                        Log.e("StatusVM", "Parse incident error: ${e.message}")
                        null
                    }
                } ?: emptyList()
                
                _uiState.update { state ->
                    val localIncidents = state.incidents.filter { it.isLocal }
                    // Скрываем одинаковые активные инциденты для одного и того же сервиса,
                    // оставляя только самый свежий.
                    val combined = (localIncidents + remoteList)
                        .sortedByDescending { it.timestamp }
                        .distinctBy { incident -> 
                            if (incident.resolved) incident.id else incident.service
                        }

                    val newTimeline = if (combined == state.incidents) {
                        state.timeline
                    } else {
                        calculateTimeline(combined)
                    }

                    state.copy(
                        incidents = combined,
                        timeline = newTimeline
                    )
                }
            }
    }

    private fun calculateTimeline(allIncidents: List<Incident>): List<TimelineBar> {
        val now = System.currentTimeMillis()
        val HOUR_IN_MS = 3600000L
        val bars = mutableListOf<TimelineBar>()

        for (i in 0 until 24) {
            val start = now - (24 - i) * HOUR_IN_MS
            val end = start + HOUR_IN_MS
            
            val hasIncident = allIncidents.any { incident -> 
                incident.timestamp in start until end 
            }
            
            bars.add(TimelineBar(start = start, end = end, isUp = !hasIncident))
        }
        return bars
    }

    private fun reportIncident(service: String, error: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("reportServiceIncident")
                    .call(mapOf("service" to service, "errorTelemetry" to error))
                    .await()
            } catch (e: Exception) {
                Log.e("StatusVM", "Failed to report incident: ${e.message}")
                val local = Incident(
                    id = "local_${System.currentTimeMillis()}",
                    service = service,
                    errorTelemetry = error,
                    timestamp = System.currentTimeMillis(),
                    resolved = false,
                    isLocal = true,
                    localErrorReason = e.message
                )
                _uiState.update { state ->
                    val newList = (listOf(local) + state.incidents).distinctBy { it.id }
                    state.copy(
                        incidents = newList,
                        timeline = calculateTimeline(newList)
                    )
                }
            }
        }
    }

    private fun resolveIncident(service: String) {
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("resolveServiceIncident")
                    .call(mapOf("service" to service))
                    .await()
            } catch (e: Exception) {
                Log.e("StatusVM", "Failed to resolve incident: ${e.message}")
            }
        }
    }
}
