package by.iposdev.visorlink.utils

import android.util.Log
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

class PresenceManager(val uid: String) : DefaultLifecycleObserver {

    private val rtdb = Firebase.database
    private val presenceRef = rtdb.getReference("presence/$uid")
    private val connectedRef = rtdb.getReference(".info/connected")
    private var connectedListener: ValueEventListener? = null
    private var attachedLifecycle: Lifecycle? = null
    private var isAppInForeground = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heartbeatJob: Job? = null

    init {
        try {
            presenceRef.keepSynced(true)
        } catch (e: Exception) {
            Log.w("PresenceManager", "Failed to keepSynced for $uid: ${e.message}")
        }
    }

    fun attach(lifecycle: Lifecycle) {
        if (attachedLifecycle == lifecycle) return
        attachedLifecycle?.removeObserver(this)
        attachedLifecycle = lifecycle
        lifecycle.addObserver(this)

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            isAppInForeground = true
            markOnline()
            startHeartbeat()
        }
        startListening()
    }

    private fun markOnline() {
        presenceRef.updateChildren(
            mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    private fun markOffline() {
        presenceRef.updateChildren(
            mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    private fun startListening() {
        if (connectedListener != null) return
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val isConnected = snap.getValue(Boolean::class.java) == true
                if (!isConnected) return

                // onDisconnect всегда регистрируем — это серверная гарантия offline при дропе
                presenceRef.onDisconnect().updateChildren(
                    mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                )

                // online: true пишем ТОЛЬКО если приложение на переднем плане.
                if (isAppInForeground) {
                    markOnline()
                }
            }

            override fun onCancelled(e: DatabaseError) {
                Log.w("PresenceManager", "connectedListener cancelled: ${e.message}")
            }
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isAppInForeground) {
                delay(30_000L) // регулярный пинг каждые 30 секунд
                if (isAppInForeground) {
                    markOnline()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        markOnline()
        startHeartbeat()
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        heartbeatJob?.cancel()
        // Явно пишем offline сразу при уходе в фон, не ждём дропа соединения
        markOffline()
    }

    fun ping() {
        if (isAppInForeground) {
            markOnline()
        }
    }

    fun detach() {
        attachedLifecycle?.removeObserver(this)
        attachedLifecycle = null
        connectedListener?.let { 
            try {
                connectedRef.removeEventListener(it)
            } catch (_: Exception) {}
        }
        connectedListener = null
        isAppInForeground = false
        heartbeatJob?.cancel()
        markOffline()
        scope.cancel()
    }

    companion object {
        fun observePresence(uid: String): Flow<PresenceData?> = callbackFlow {
            val ref = Firebase.database.getReference("presence/$uid")
            try {
                ref.keepSynced(true)
            } catch (_: Exception) {}

            val listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    if (!snap.exists()) {
                        trySend(PresenceData(online = false, lastSeen = null))
                        return
                    }
                    val online = snap.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = when (val raw = snap.child("lastSeen").value) {
                        is Number -> raw.toLong()
                        is String -> raw.toLongOrNull()
                        else -> null
                    }
                    trySend(PresenceData(online, lastSeen))
                }
                override fun onCancelled(e: DatabaseError) {
                    trySend(PresenceData(online = false, lastSeen = null))
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { 
                try {
                    ref.removeEventListener(listener)
                } catch (_: Exception) {}
            }
        }
    }
}