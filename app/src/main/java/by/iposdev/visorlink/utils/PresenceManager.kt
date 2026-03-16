package by.iposdev.visorlink.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PresenceManager(private val db: FirebaseFirestore) : DefaultLifecycleObserver {

    private var uid: String? = null

    fun attach(uid: String) {
        this.uid = uid
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun detach() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        uid?.let { markOffline(it) }
        uid = null
    }

    override fun onStart(owner: LifecycleOwner) {
        uid?.let { db.collection("users").document(it)
            .update("online", true, "lastSeen", FieldValue.serverTimestamp()) }
    }

    override fun onStop(owner: LifecycleOwner) {
        uid?.let { markOffline(it) }
    }

    private fun markOffline(uid: String) {
        db.collection("users").document(uid)
            .update("online", false, "lastSeen", FieldValue.serverTimestamp())
    }
}