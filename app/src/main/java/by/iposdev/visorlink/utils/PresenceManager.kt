package by.iposdev.visorlink.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import by.iposdev.visorlink.data.model.PresenceData
import com.google.firebase.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

import kotlinx.coroutines.*

class PresenceManager(private val uid: String) : DefaultLifecycleObserver {

    private val rtdb = Firebase.database
    private val presenceRef = rtdb.getReference("presence/$uid")
    private val connectedRef = rtdb.getReference(".info/connected")
    private var connectedListener: ValueEventListener? = null
    private var isAppInForeground = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heartbeatJob: Job? = null

    fun attach(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
        startListening()
    }

    private fun startListening() {
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                if (snap.getValue(Boolean::class.java) != true) return

                // onDisconnect всегда регистрируем — это серверная гарантия offline при дропе
                presenceRef.onDisconnect().setValue(
                    mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                )

                // online: true пишем ТОЛЬКО если приложение на переднем плане.
                if (isAppInForeground) {
                    presenceRef.setValue(
                        mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
                    )
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isAppInForeground) {
                delay(30_000L) // регулярный пинг каждые 30 секунд
                if (isAppInForeground) {
                    presenceRef.updateChildren(
                        mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
                    )
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        presenceRef.setValue(
            mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
        )
        startHeartbeat()
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        heartbeatJob?.cancel()
        // Явно пишем offline сразу при уходе в фон, не ждём дропа соединения
        presenceRef.setValue(
            mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    fun detach() {
        connectedListener?.let { connectedRef.removeEventListener(it) }
        isAppInForeground = false
        heartbeatJob?.cancel()
        presenceRef.setValue(
            mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    companion object {
        fun observePresence(uid: String): Flow<PresenceData?> = callbackFlow {
            val ref = Firebase.database.getReference("presence/$uid")
            val listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    if (!snap.exists()) {
                        trySend(PresenceData(online = false, lastSeen = null))
                        return
                    }
                    val online = snap.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snap.child("lastSeen").getValue(Long::class.java)
                    trySend(PresenceData(online, lastSeen))
                }
                override fun onCancelled(e: DatabaseError) {
                    trySend(PresenceData(online = false, lastSeen = null))
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
    }
}