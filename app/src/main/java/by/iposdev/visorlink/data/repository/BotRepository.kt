package by.iposdev.visorlink.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class DmBot(
    val uid: String,
    val name: String,
    val username: String,
    val token: String? = null
)

class BotRepository(private val functions: FirebaseFunctions) {

    suspend fun listBots(): List<DmBot> {
        val res = functions.getHttpsCallable("listDmBots").call().await()
        val list = res.data as? List<Map<String, Any>> ?: return emptyList()
        return list.map {
            DmBot(
                uid = it["uid"] as String,
                name = it["name"] as String,
                username = it["username"] as String,
                token = it["botToken"] as? String
            )
        }
    }

    suspend fun createBot(name: String, username: String): String {
        val res = functions.getHttpsCallable("createDmBot").call(mapOf("name" to name, "username" to username)).await()
        val data = res.data as Map<*, *>
        return data["botToken"] as String
    }

    suspend fun regenerateToken(botUid: String): String {
        val res = functions.getHttpsCallable("regenerateBotToken").call(mapOf("botUid" to botUid)).await()
        val data = res.data as Map<*, *>
        return data["botToken"] as String
    }

    suspend fun deleteBot(botUid: String) {
        functions.getHttpsCallable("deleteDmBot").call(mapOf("botUid" to botUid)).await()
    }
}