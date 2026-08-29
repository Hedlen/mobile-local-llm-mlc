package ai.mlc.gomoku

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.porter.local.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.net.HttpURLConnection

class GomokuModelService(context: Context) {
    private val root = File(context.filesDir, "models/qwen2.5-1.5b-instruct")
    private val bases = listOf(
        "https://hf-mirror.com/mlc-ai/Qwen2.5-1.5B-Instruct-q4f16_1-MLC/resolve/main/",
        "https://huggingface.co/mlc-ai/Qwen2.5-1.5B-Instruct-q4f16_1-MLC/resolve/main/",
    )
    private val gson = Gson()
    val modelId = "local/qwen2.5-1.5b-instruct@1"
    // Must match the 1.5B Android library packaged by mlc-package-config.1.5b.json.
    val modelLib = "qwen2_q4f16_1_586c78736b9d4ec921756daa4b1166d8"
    fun installed(): Boolean = runCatching {
        val model = File(root, "mlc-chat-config.json"); val tensor = File(root, "tensor-cache.json")
        if (!valid(model) || !valid(tensor)) return false
        val config = gson.fromJson(model.readText(), ModelConfig::class.java)
        val cache = gson.fromJson(tensor.readText(), CacheConfig::class.java)
        config.tokenizerFiles.all { valid(File(root, it)) } && cache.records.all { valid(File(root, it.dataPath)) }
    }.getOrDefault(false)
    suspend fun install(progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        root.mkdirs(); fetch("mlc-chat-config.json", progress); fetch("tensor-cache.json", progress)
        val config = gson.fromJson(File(root, "mlc-chat-config.json").readText(), ModelConfig::class.java)
        config.tokenizerFiles.forEach { fetch(it, progress) }
        val cache = gson.fromJson(File(root, "tensor-cache.json").readText(), CacheConfig::class.java)
        cache.records.map { it.dataPath }.distinct().forEach { fetch(it, progress) }
        progress("模型已就绪")
    }
    fun directory() = root
    private fun fetch(name: String, progress: (String) -> Unit) {
        require(!name.startsWith("/") && !name.contains(".."))
        val target = File(root, name); if (valid(target)) return
        target.parentFile?.mkdirs(); val part = File(target.parentFile, target.name + ".part")
        progress("下载 $name")
        var lastError: Throwable? = null
        for (base in bases) {
            repeat(2) { attempt ->
                val connection = (URL(base + name).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 25_000; readTimeout = 90_000; instanceFollowRedirects = true
                }
                try {
                    check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}: $name" }
                    connection.inputStream.use { input -> part.outputStream().use { output -> input.copyTo(output) } }
                    check(valid(part)) { "下载文件为空: $name" }
                    if (target.exists()) target.delete()
                    check(part.renameTo(target)) { "无法保存 $name" }
                    return
                } catch (error: Throwable) {
                    lastError = error
                    part.delete()
                    progress("下载重试 ${attempt + 1}/2：$name")
                } finally { connection.disconnect() }
            }
        }
        throw checkNotNull(lastError)
    }
    private fun valid(file: File) = file.isFile && file.length() > 0
    data class ModelConfig(@SerializedName("tokenizer_files") val tokenizerFiles: List<String>)
    data class CacheConfig(@SerializedName("records") val records: List<CacheRecord>)
    data class CacheRecord(@SerializedName("dataPath") val dataPath: String)
}
