package com.porter.local.llm

enum class LocalLlmRole { SYSTEM, USER, ASSISTANT }

data class LocalLlmMessage(
    val role: LocalLlmRole,
    val content: String,
)

data class LocalLlmRequest(
    val model: String,
    val messages: List<LocalLlmMessage>,
    val temperature: Float? = null,
    val topP: Float? = null,
    val seed: Int? = null,
    val maxOutputTokens: Int? = null,
    val stop: List<String>? = null,
) {
    init {
        require(model.isNotBlank()) { "model must not be blank" }
        require(messages.isNotEmpty()) { "messages must not be empty" }
        require(temperature == null || temperature >= 0f) { "temperature must be >= 0" }
        require(topP == null || topP in 0f..1f) { "topP must be between 0 and 1" }
        require(maxOutputTokens == null || maxOutputTokens > 0) { "maxOutputTokens must be > 0" }
    }
}

data class LocalLlmUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val prefillTokensPerSecond: Float? = null,
    val decodeTokensPerSecond: Float? = null,
)

enum class LocalLlmFinishReason { STOP, LENGTH, CANCELLED, ERROR, UNKNOWN }

sealed class LocalLlmEvent {
    data class Started(val requestId: String, val model: String) : LocalLlmEvent()
    data class Delta(val requestId: String, val text: String) : LocalLlmEvent()
    data class Usage(val requestId: String, val usage: LocalLlmUsage) : LocalLlmEvent()
    data class Completed(
        val requestId: String,
        val finishReason: LocalLlmFinishReason,
    ) : LocalLlmEvent()
    data class Cancelled(val requestId: String) : LocalLlmEvent()
}

data class LocalLlmCapabilities(
    val platform: String = "android",
    val backend: String = "mlc",
    val accelerator: String = "gpu",
    val supportsStreaming: Boolean = true,
    val supportsCancellation: Boolean = true,
    val supportsJsonSchema: Boolean = false,
    val maxConcurrentGenerations: Int = 1,
)

enum class LocalLlmClientState { NEW, INITIALIZED, LOADING, READY, GENERATING, CLOSED }

class LocalLlmException(
    val code: String,
    message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
