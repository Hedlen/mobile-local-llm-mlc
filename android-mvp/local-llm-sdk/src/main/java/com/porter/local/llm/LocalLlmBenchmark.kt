package com.porter.local.llm

import kotlinx.coroutines.flow.collect

data class LocalLlmBenchmarkCase(
    val name: String,
    val request: LocalLlmRequest,
    val warmupRounds: Int = 1,
    val measuredRounds: Int = 3,
)

data class LocalLlmBenchmarkRound(
    val caseName: String,
    val model: String,
    val round: Int,
    val firstTokenMillis: Long?,
    val totalMillis: Long,
    val outputCharacters: Int,
    val usage: LocalLlmUsage?,
    val finishReason: LocalLlmFinishReason,
)

data class LocalLlmBenchmarkReport(
    val schemaVersion: Int = 1,
    val device: LocalLlmDeviceProfile,
    val model: LocalLlmModelManifest,
    val loadMillis: Long,
    val rounds: List<LocalLlmBenchmarkRound>,
)

class LocalLlmBenchmarkRunner(private val client: LocalLlmClient) {
    suspend fun run(
        installedModel: LocalLlmInstalledModel,
        device: LocalLlmDeviceProfile,
        cases: List<LocalLlmBenchmarkCase>,
    ): LocalLlmBenchmarkReport {
        require(cases.isNotEmpty())
        val loadStarted = elapsedMillis()
        client.load(installedModel)
        val loadMillis = elapsedMillis() - loadStarted
        val results = mutableListOf<LocalLlmBenchmarkRound>()
        cases.forEach { benchmark ->
            repeat(benchmark.warmupRounds) {
                client.stream(benchmark.request).collect()
                client.reset()
            }
            repeat(benchmark.measuredRounds) { index ->
                val started = elapsedMillis()
                var firstToken: Long? = null
                var characters = 0
                var usage: LocalLlmUsage? = null
                var reason = LocalLlmFinishReason.UNKNOWN
                client.stream(benchmark.request).collect { event ->
                    when (event) {
                        is LocalLlmEvent.Delta -> {
                            if (firstToken == null) firstToken = elapsedMillis() - started
                            characters += event.text.length
                        }
                        is LocalLlmEvent.Usage -> usage = event.usage
                        is LocalLlmEvent.Completed -> reason = event.finishReason
                        is LocalLlmEvent.Cancelled -> reason = LocalLlmFinishReason.CANCELLED
                        is LocalLlmEvent.Started -> Unit
                    }
                }
                results += LocalLlmBenchmarkRound(
                    benchmark.name,
                    benchmark.request.model,
                    index + 1,
                    firstToken,
                    elapsedMillis() - started,
                    characters,
                    usage,
                    reason,
                )
                client.reset()
            }
        }
        return LocalLlmBenchmarkReport(
            device = device,
            model = installedModel.manifest,
            loadMillis = loadMillis,
            rounds = results,
        )
    }
}

private fun elapsedMillis(): Long = System.nanoTime() / 1_000_000
