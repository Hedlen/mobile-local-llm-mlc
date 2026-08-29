package com.porter.local.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class LocalLlmModelManagerTest {
    private val payload = "verified model".toByteArray()
    private val artifact = LocalLlmModelArtifact(
        path = "params/model.bin",
        url = "https://models.example/model.bin",
        sha256 = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) },
        bytes = payload.size.toLong(),
    )
    private val manifest = LocalLlmModelManifest(
        id = "local/qwen2.5-0.5b-instruct@1",
        displayName = "Qwen 0.5B",
        family = "qwen2",
        parameterCountB = 0.5f,
        quantization = "q4f16_1",
        contextWindowTokens = 2048,
        estimatedMemoryBytes = 1_200_000_000,
        downloadBytes = payload.size.toLong(),
        modelLib = "qwen2_q4f16_1",
        sourceRevision = "0123456789abcdef",
        artifacts = listOf(artifact),
    )

    @Test fun installsVerifiesResolvesAndDeletes() {
        val root = Files.createTempDirectory("models").toFile()
        val manager = manager(root) { _, file -> file.writeBytes(payload) }
        assertEquals(LocalLlmModelInstallState.NOT_INSTALLED, manager.models().single().state)
        assertEquals(LocalLlmModelInstallState.INSTALLED, manager.install(manifest.id).state)
        assertEquals(manifest.id, manager.resolveForLoad(manifest.id).manifest.id)
        manager.delete(manifest.id)
        assertEquals(LocalLlmModelInstallState.NOT_INSTALLED, manager.models().single().state)
    }

    @Test fun rejectsCorruptDownloadAndRemovesPartialFile() {
        val root = Files.createTempDirectory("models").toFile()
        val manager = manager(root) { _, file -> file.writeText("corrupt") }
        assertThrows(LocalLlmException::class.java) { manager.install(manifest.id) }
        assertEquals(LocalLlmModelInstallState.NOT_INSTALLED, manager.models().single().state)
    }

    @Test fun rejectsUnsafeManifestPath() {
        val unsafe = manifest.copy(artifacts = listOf(artifact.copy(path = "../escape")))
        assertThrows(IllegalArgumentException::class.java) { unsafe.validate() }
    }

    private fun manager(root: File, download: (String, File) -> Unit) = LocalLlmModelManager(
        rootDirectory = root,
        catalog = LocalLlmModelCatalog(1, "test", listOf(manifest)),
        downloader = LocalLlmArtifactDownloader(download),
    )
}
