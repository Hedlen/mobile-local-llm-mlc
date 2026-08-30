package ai.mlc.gomoku

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CloudCoachClient(private val context: Context) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isConfigured(): Boolean = BuildConfig.ARK_API_KEY.isNotBlank()

    suspend fun review(system: String, prompt: String): String = withContext(Dispatchers.IO) {
        check(isOnline()) { "网络不可用" }
        check(isConfigured()) { "云端令牌未配置" }
        val payload = JsonObject().apply {
            addProperty("model", BuildConfig.ARK_MODEL)
            add("messages", JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", system) })
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", prompt) })
            })
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 180)
        }.toString()
        val connection = (URL(BuildConfig.ARK_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.ARK_API_KEY}")
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(payload) }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(connection.responseCode in 200..299) { "云端 HTTP ${connection.responseCode}" }
            JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString.trim()
                .also { check(it.isNotBlank()) { "云端返回为空" } }
        } finally {
            connection.disconnect()
        }
    }
}
