package com.porter.local.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLlmModelRecommenderTest {
    private val models = listOf(0.5f to 1_200_000_000L, 1.5f to 2_200_000_000L, 3f to 4_000_000_000L)
        .map { (parameters, memory) -> model(parameters, memory) }
    private val catalog = LocalLlmModelCatalog(1, "runtime", models)

    @Test fun balancedSelectsMiddleModelWhenAllFit() {
        val result = LocalLlmModelRecommender.recommend(catalog, device(6_000_000_000L))
        assertEquals(1.5f, result.model?.parameterCountB)
    }

    @Test fun qualitySelectsLargestModelThatFits() {
        val result = LocalLlmModelRecommender.recommend(
            catalog,
            device(3_500_000_000L),
            LocalLlmModelRequirements(qualityPreference = LocalLlmQualityPreference.QUALITY),
        )
        assertEquals(1.5f, result.model?.parameterCountB)
    }

    @Test fun balancedUsesOnePointFiveWhenThreeBDoesNotFit() {
        val result = LocalLlmModelRecommender.recommend(catalog, device(3_500_000_000L))
        assertEquals(1.5f, result.model?.parameterCountB)
    }

    @Test fun rejectsLowMemoryState() {
        val result = LocalLlmModelRecommender.recommend(catalog, device(6_000_000_000L).copy(lowMemory = true))
        assertNull(result.model)
    }

    private fun device(available: Long) = LocalLlmDeviceProfile(
        androidApi = 35,
        totalMemoryBytes = 8_000_000_000,
        availableMemoryBytes = available,
        lowMemory = false,
        availableStorageBytes = 10_000_000_000,
        supportedAbis = listOf("arm64-v8a"),
    )

    private fun model(parameters: Float, memory: Long) = LocalLlmModelManifest(
        id = "local/qwen-$parameters@1",
        displayName = "Qwen $parameters",
        family = "qwen2",
        parameterCountB = parameters,
        quantization = "q4f16_1",
        contextWindowTokens = 2048,
        estimatedMemoryBytes = memory,
        downloadBytes = 500_000_000,
        modelLib = "qwen2_$parameters",
        sourceRevision = "0123456789abcdef",
        artifacts = listOf(LocalLlmModelArtifact(
            "model.bin", "https://example.com/model.bin", "0".repeat(64), 1
        )),
    )
}
