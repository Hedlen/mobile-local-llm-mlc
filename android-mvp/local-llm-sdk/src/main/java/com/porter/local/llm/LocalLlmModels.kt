package com.porter.local.llm

import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class LocalLlmModelCatalog(
    val schemaVersion: Int,
    val runtimeRevision: String,
    val models: List<LocalLlmModelManifest>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported catalog schema: $schemaVersion" }
        require(models.map { it.id }.distinct().size == models.size) { "Duplicate model id" }
        models.forEach(LocalLlmModelManifest::validate)
    }

    fun model(id: String) = models.firstOrNull { it.id == id }
        ?: throw LocalLlmException("model_unknown", "Unknown model: $id")

    companion object {
        fun parse(json: String): LocalLlmModelCatalog =
            Gson().fromJson(json, LocalLlmModelCatalog::class.java).also { catalog ->
                requireNotNull(catalog) { "Catalog must not be null" }
                catalog.models.forEach(LocalLlmModelManifest::validate)
            }
    }
}

data class LocalLlmModelManifest(
    val id: String,
    val displayName: String,
    val family: String,
    val parameterCountB: Float,
    val quantization: String,
    val contextWindowTokens: Int,
    val estimatedMemoryBytes: Long,
    val downloadBytes: Long,
    val modelLib: String,
    val sourceRevision: String,
    val artifacts: List<LocalLlmModelArtifact>,
) {
    internal fun validate() {
        require(id.matches(Regex("[a-z0-9._@/-]+"))) { "Invalid model id: $id" }
        require(displayName.isNotBlank() && family.isNotBlank() && modelLib.isNotBlank())
        require(parameterCountB > 0 && contextWindowTokens > 0)
        require(estimatedMemoryBytes > 0 && downloadBytes >= 0)
        require(sourceRevision.isNotBlank()) { "A pinned source revision is required" }
        require(artifacts.isNotEmpty()) { "At least one artifact is required" }
        artifacts.forEach(LocalLlmModelArtifact::validate)
    }
}

data class LocalLlmModelArtifact(
    val path: String,
    val url: String,
    val sha256: String,
    val bytes: Long,
) {
    internal fun validate() {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains(".."))
        require(url.startsWith("https://")) { "Only HTTPS artifacts are accepted" }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256 for $path" }
        require(bytes > 0)
    }
}

enum class LocalLlmModelInstallState { NOT_INSTALLED, PARTIAL, INSTALLED, CORRUPT }

data class LocalLlmInstalledModel(
    val manifest: LocalLlmModelManifest,
    val directory: File,
    val state: LocalLlmModelInstallState,
)

fun interface LocalLlmArtifactDownloader {
    fun download(url: String, destination: File)
}

class LocalLlmModelManager(
    private val rootDirectory: File,
    val catalog: LocalLlmModelCatalog,
    expectedRuntimeRevision: String? = null,
    private val downloader: LocalLlmArtifactDownloader = LocalLlmArtifactDownloader { url, destination ->
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) {
                throw LocalLlmException("download_failed", "HTTP ${connection.responseCode}", true)
            }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    },
) {
    init {
        if (expectedRuntimeRevision != null && catalog.runtimeRevision != expectedRuntimeRevision) {
            throw LocalLlmException(
                "runtime_incompatible",
                "Catalog requires ${catalog.runtimeRevision}, runtime is $expectedRuntimeRevision",
            )
        }
        rootDirectory.mkdirs()
    }

    fun models(): List<LocalLlmInstalledModel> = catalog.models.map(::inspect)

    fun inspect(model: LocalLlmModelManifest): LocalLlmInstalledModel {
        val directory = modelDirectory(model.id)
        val existing = model.artifacts.count { File(directory, it.path).isFile }
        val state = when {
            existing == 0 -> LocalLlmModelInstallState.NOT_INSTALLED
            existing < model.artifacts.size -> LocalLlmModelInstallState.PARTIAL
            model.artifacts.all { verify(File(directory, it.path), it) } -> LocalLlmModelInstallState.INSTALLED
            else -> LocalLlmModelInstallState.CORRUPT
        }
        return LocalLlmInstalledModel(model, directory, state)
    }

    @Synchronized
    fun install(modelId: String, onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): LocalLlmInstalledModel {
        val model = catalog.model(modelId)
        val directory = modelDirectory(model.id).apply { mkdirs() }
        var completed = 0L
        model.artifacts.forEach { artifact ->
            val destination = File(directory, artifact.path)
            if (verify(destination, artifact)) {
                completed += artifact.bytes
                onProgress(completed, model.downloadBytes)
                return@forEach
            }
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, destination.name + ".part")
            temporary.delete()
            try {
                downloader.download(artifact.url, temporary)
                if (!verify(temporary, artifact)) {
                    throw LocalLlmException("checksum_mismatch", "Checksum mismatch: ${artifact.path}")
                }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Cannot publish ${artifact.path}" }
                completed += artifact.bytes
                onProgress(completed, model.downloadBytes)
            } finally {
                temporary.delete()
            }
        }
        return inspect(model).also {
            check(it.state == LocalLlmModelInstallState.INSTALLED)
        }
    }

    @Synchronized
    fun delete(modelId: String) {
        val directory = modelDirectory(catalog.model(modelId).id)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw LocalLlmException("storage_error", "Failed to delete $modelId", true)
        }
    }

    fun resolveForLoad(modelId: String): LocalLlmInstalledModel {
        val installed = inspect(catalog.model(modelId))
        if (installed.state != LocalLlmModelInstallState.INSTALLED) {
            throw LocalLlmException("model_not_installed", "$modelId is ${installed.state}")
        }
        return installed
    }

    private fun modelDirectory(modelId: String): File =
        File(rootDirectory, sha256(modelId.toByteArray()).take(24))

    private fun verify(file: File, artifact: LocalLlmModelArtifact): Boolean =
        file.isFile && file.length() == artifact.bytes && sha256(file) == artifact.sha256.lowercase()
}

private fun sha256(file: File): String = FileInputStream(file).use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
