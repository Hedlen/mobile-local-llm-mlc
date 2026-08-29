package com.porter.local.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

data class LocalLlmDeviceProfile(
    val androidApi: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val lowMemory: Boolean,
    val availableStorageBytes: Long,
    val supportedAbis: List<String>,
)

enum class LocalLlmQualityPreference { LITE, BALANCED, QUALITY }

data class LocalLlmModelRequirements(
    val minimumContextTokens: Int = 2048,
    val qualityPreference: LocalLlmQualityPreference = LocalLlmQualityPreference.BALANCED,
    val reserveMemoryBytes: Long = 1_000_000_000,
)

data class LocalLlmModelRecommendation(
    val model: LocalLlmModelManifest?,
    val eligibleModels: List<LocalLlmModelManifest>,
    val reason: String,
)

object LocalLlmDeviceProfiler {
    fun capture(context: Context): LocalLlmDeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        return LocalLlmDeviceProfile(
            androidApi = Build.VERSION.SDK_INT,
            totalMemoryBytes = memory.totalMem,
            availableMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            availableStorageBytes = storage.availableBytes,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
    }
}

object LocalLlmModelRecommender {
    fun recommend(
        catalog: LocalLlmModelCatalog,
        device: LocalLlmDeviceProfile,
        requirements: LocalLlmModelRequirements = LocalLlmModelRequirements(),
    ): LocalLlmModelRecommendation {
        if (device.androidApi < 26 || "arm64-v8a" !in device.supportedAbis) {
            return LocalLlmModelRecommendation(null, emptyList(), "Unsupported Android ABI or API")
        }
        if (device.lowMemory) {
            return LocalLlmModelRecommendation(null, emptyList(), "Android reports a low-memory state")
        }
        val memoryBudget = (device.availableMemoryBytes - requirements.reserveMemoryBytes).coerceAtLeast(0)
        val eligible = catalog.models
            .filter { it.contextWindowTokens >= requirements.minimumContextTokens }
            .filter { it.estimatedMemoryBytes <= memoryBudget }
            .filter { it.downloadBytes <= device.availableStorageBytes }
            .sortedBy { it.parameterCountB }
        if (eligible.isEmpty()) {
            return LocalLlmModelRecommendation(null, emptyList(), "No model fits current memory and storage")
        }
        val selected = when (requirements.qualityPreference) {
            LocalLlmQualityPreference.LITE -> eligible.first()
            LocalLlmQualityPreference.QUALITY -> eligible.last()
            LocalLlmQualityPreference.BALANCED ->
                eligible.lastOrNull { it.parameterCountB <= 1.5f } ?: eligible.first()
        }
        return LocalLlmModelRecommendation(
            selected,
            eligible,
            "Selected ${selected.displayName} from ${eligible.size} compatible model(s)",
        )
    }
}
