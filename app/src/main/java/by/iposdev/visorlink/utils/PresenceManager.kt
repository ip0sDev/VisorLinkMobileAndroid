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

class PresenceManager(private val uid: String) : DefaultLifecycleObserver {

    private val rtdb = Firebase.database
    private val presenceRef = rtdb.getReference("presence/$uid")
    private val connectedRef = rtdb.getReference(".info/connected")
    private var connectedListener: ValueEventListener? = null
    private var isAppInForeground = false

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
                // При фоновых FCM-соединениях RTDB может переподключиться и
                // вызвать этот колбэк — без флага мы бы ошибочно ставили online.
                if (isAppInForeground) {
                    presenceRef.setValue(
                        mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
                    )
                }
                // Если фон — ничего не пишем. onDisconnect уже гарантирует offline
                // как только соединение оборвётся.
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        presenceRef.setValue(
            mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        // Явно пишем offline сразу при уходе в фон, не ждём дропа соединения
        presenceRef.setValue(
            mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
        )
    }

    fun detach() {
        connectedListener?.let { connectedRef.removeEventListener(it) }
        isAppInForeground = false
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