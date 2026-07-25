package by.iposdev.visorlink.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object UpdateApiClient {
    private const val BASE_URL = "https://update-android.visorlink.org/api"

    suspend fun register(installId: String, channel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/register")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("install_id", installId)
                put("channel", channel)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            conn.responseCode == 200
        } catch (e: Exception) {
            Log.e("UpdateApi", "Register failed", e)
            false
        }
    }

    suspend fun setChannel(installId: String, channel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/channel")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("install_id", installId)
                put("channel", channel)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            conn.responseCode == 200
        } catch (e: Exception) {
            Log.e("UpdateApi", "Set channel failed", e)
            false
        }
    }

    data class UpdateInfo(
        val isAvailable: Boolean,
        val versionCode: Int = 0,
        val versionName: String = "",
        val sha256: String = "",
        val changelog: String = "",
        val downloadUrl: String = ""
    )

    suspend fun checkUpdate(installId: String, packageName: String, versionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/check_update?install_id=$installId&package_name=$packageName&version_code=$versionCode")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                if (json.getBoolean("update_available")) {
                    UpdateInfo(
                        isAvailable = true,
                        versionCode = json.getInt("version_code"),
                        versionName = json.getString("version_name"),
                        sha256 = json.getString("sha256"),
                        changelog = json.getString("changelog"),
                        downloadUrl = json.getString("download_url")
                    )
                } else {
                    UpdateInfo(isAvailable = false)
                }
            } else null
        } catch (e: Exception) {
            Log.e("UpdateApi", "Check update failed", e)
            null
        }
    }
}