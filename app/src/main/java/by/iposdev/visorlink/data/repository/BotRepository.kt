package by.iposdev.visorlink.data.repository

import by.iposdev.visorlink.data.remote.chat.CreateDmBotRequest
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class DmBot(
    val uid: String,
    val name: String,
    val username: String,
    val token: String? = null
)

class BotRepository(
    private val functions: FirebaseFunctions,
    private val api: VisorLinkApi? = null,
    private val flagsRepository: FlagsRepository? = null
) {

    private fun isBackendV2Enabled(): Boolean = flagsRepository?.isBackendV2Enabled() == true && api != null

    suspend fun listBots(): List<DmBot> {
        if (isBackendV2Enabled()) {
            val res = api!!.listDmBots()
            return res.bots?.map {
                DmBot(
                    uid = it.actualUid,
                    name = it.name,
                    username = it.username,
                    token = it.botToken
                )
            } ?: emptyList()
        }

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
        if (isBackendV2Enabled()) {
            val res = api!!.createDmBot(CreateDmBotRequest(name = name, username = username))
            return res.botToken
        }

        val res = functions.getHttpsCallable("createDmBot").call(mapOf("name" to name, "username" to username)).await()
        val data = res.data as Map<*, *>
        return data["botToken"] as String
    }

    suspend fun regenerateToken(botUid: String): String {
        if (isBackendV2Enabled()) {
            val res = api!!.regenerateDmBotToken(botUid)
            return res.botToken
        }

        val res = functions.getHttpsCallable("regenerateBotToken").call(mapOf("botUid" to botUid)).await()
        val data = res.data as Map<*, *>
        return data["botToken"] as String
    }

    suspend fun deleteBot(botUid: String) {
        if (isBackendV2Enabled()) {
            api!!.deleteDmBot(botUid)
            return
        }

        functions.getHttpsCallable("deleteDmBot").call(mapOf("botUid" to botUid)).await()
    }
}