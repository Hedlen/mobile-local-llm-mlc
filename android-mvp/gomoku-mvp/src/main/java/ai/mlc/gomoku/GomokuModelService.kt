package ai.mlc.gomoku

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.porter.local.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class GomokuModelService(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "models/qwen2.5-0.5b-instruct")
    private val base = "https://huggingface.co/mlc-ai/Qwen2.5-0.5B-Instruct-q4f16_1-MLC/resolve/main/"
    private val gson = Gson()
    val modelId = "local/qwen2.5-0.5b-instruct@1"
    val modelLib = "qwen2_q4f16_1_ec234c98ba1f1f6d014a60148428520a"
    fun installed() = File(root, "mlc-chat-config.json").isFile && File(root, "tensor-cache.json").isFile
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
        val target = File(root, name); if (target.isFile && target.length() > 0) return
        target.parentFile?.mkdirs(); val part = File(target.parentFile, target.name + ".part")
        progress("下载 $name")
        URL(base + name).openStream().use { input -> part.outputStream().use { output -> input.copyTo(output) } }
        check(part.renameTo(target)) { "无法保存 $name" }
    }
    data class ModelConfig(@SerializedName("tokenizer_files") val tokenizerFiles: List<String>)
    data class CacheConfig(@SerializedName("records") val records: List<CacheRecord>)
    data class CacheRecord(@SerializedName("dataPath") val dataPath: String)
}
