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

class CloudCoachClient(private val context: Context, private val settings: CloudSettings) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isConfigured(): Boolean = settings.load() != null

    suspend fun review(system: String, prompt: String): String = withContext(Dispatchers.IO) {
        post(system, prompt, 180, 0.7f)
    }

    suspend fun chooseOpponentMove(persona: String, level: String, candidates: List<String>, fact: String): CloudMoveDecision? {
        val response = post(
            system = "你是五子棋 AI 对手。只能从用户给出的合法候选坐标中选一个。输出严格 JSON：{\"move\":\"行,列\",\"reply\":\"不超过28字的中文对手回应\"}。不得添加 Markdown 或额外文字。",
            prompt = "对手人格：$persona；难度：$level；局面事实：$fact；合法候选：${candidates.joinToString("、")}。",
            maxTokens = 80,
            temperature = 0.55f,
        )
        val json = response.substringAfter('{', "").substringBeforeLast('}', "")
        if (json.isBlank()) return null
        return runCatching {
            val objectValue = JsonParser.parseString("{$json}").asJsonObject
            CloudMoveDecision(objectValue.get("move").asString, objectValue.get("reply").asString.take(40))
        }.getOrNull()
    }

    private fun post(system: String, prompt: String, maxTokens: Int, temperature: Float): String {
        check(isOnline()) { "网络不可用" }
        val config = checkNotNull(settings.load()) { "云端令牌未配置" }
        val payload = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", system) })
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", prompt) })
            })
            addProperty("temperature", temperature)
            addProperty("max_tokens", maxTokens)
        }.toString()
        val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        return try {
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

data class CloudMoveDecision(val coordinate: String, val reply: String)
