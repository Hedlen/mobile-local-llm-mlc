package com.porter.local.llm

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LocalLlmClient {
    private val engine = MLCEngine()
    private val cancelled = AtomicBoolean(false)

    @Volatile
    var state: LocalLlmClientState = LocalLlmClientState.NEW
        private set

    @Volatile
    private var loadedModel: String? = null

    fun initialize(): LocalLlmCapabilities {
        checkNotClosed()
        if (state == LocalLlmClientState.NEW) state = LocalLlmClientState.INITIALIZED
        return capabilities()
    }

    fun capabilities() = LocalLlmCapabilities()

    suspend fun load(model: String, modelPath: String, modelLib: String) {
        require(model.isNotBlank() && modelPath.isNotBlank() && modelLib.isNotBlank())
        checkNotClosed()
        if (state == LocalLlmClientState.NEW) initialize()
        if (state == LocalLlmClientState.GENERATING) {
            throw LocalLlmException("request_in_progress", "Cannot load while generating")
        }
        state = LocalLlmClientState.LOADING
        try {
            withContext(Dispatchers.IO) {
                engine.unload()
                engine.reload(modelPath, modelLib)
            }
            loadedModel = model
            state = LocalLlmClientState.READY
        } catch (error: Throwable) {
            state = LocalLlmClientState.INITIALIZED
            throw LocalLlmException("backend_error", "Failed to load model", true, error)
        }
    }

    fun stream(request: LocalLlmRequest): Flow<LocalLlmEvent> = flow {
        checkNotClosed()
        val activeModel = loadedModel
            ?: throw LocalLlmException("model_not_loaded", "Call load before stream")
        if (activeModel != request.model) {
            throw LocalLlmException("model_incompatible", "Requested model is not loaded")
        }
        if (state != LocalLlmClientState.READY) {
            throw LocalLlmException("queue_full", "MVP supports one generation at a time", true)
        }

        val requestId = UUID.randomUUID().toString()
        var finishReason = LocalLlmFinishReason.STOP
        var terminalEmitted = false
        cancelled.set(false)
        state = LocalLlmClientState.GENERATING
        emit(LocalLlmEvent.Started(requestId, activeModel))

        try {
            val responses = engine.chat.completions.create(
                messages = request.messages.map { it.toMlcMessage() },
                model = activeModel,
                max_tokens = request.maxOutputTokens,
                seed = request.seed,
                stop = request.stop,
                stream = true,
                stream_options = OpenAIProtocol.StreamOptions(include_usage = true),
                temperature = request.temperature,
                top_p = request.topP,
            )

            for (response in responses) {
                if (cancelled.get()) break
                response.choices.forEach { choice ->
                    choice.delta.content?.asText()?.takeIf(String::isNotEmpty)?.let {
                        emit(LocalLlmEvent.Delta(requestId, it))
                    }
                    finishReason = choice.finish_reason.toFinishReason(finishReason)
                }
                response.usage?.let { usage ->
                    emit(LocalLlmEvent.Usage(requestId, usage.toPublicUsage()))
                }
            }

            if (cancelled.get()) {
                emit(LocalLlmEvent.Cancelled(requestId))
            } else {
                emit(LocalLlmEvent.Completed(requestId, finishReason))
            }
            terminalEmitted = true
        } catch (cancel: CancellationException) {
            engine.abortAll()
            throw cancel
        } catch (error: Throwable) {
            throw LocalLlmException("backend_error", "Generation failed", true, error)
        } finally {
            if (!terminalEmitted && cancelled.get()) engine.abortAll()
            if (state != LocalLlmClientState.CLOSED) state = LocalLlmClientState.READY
        }
    }

    fun cancel() {
        if (state != LocalLlmClientState.GENERATING) return
        cancelled.set(true)
        engine.abortAll()
    }

    suspend fun reset() {
        checkNotClosed()
        if (state == LocalLlmClientState.GENERATING) cancel()
        withContext(Dispatchers.IO) { engine.reset() }
    }

    suspend fun unload() {
        checkNotClosed()
        if (state == LocalLlmClientState.GENERATING) cancel()
        withContext(Dispatchers.IO) { engine.unload() }
        loadedModel = null
        state = LocalLlmClientState.INITIALIZED
    }

    suspend fun close() {
        if (state == LocalLlmClientState.CLOSED) return
        if (state == LocalLlmClientState.GENERATING) cancel()
        withContext(Dispatchers.IO) { engine.unload() }
        loadedModel = null
        state = LocalLlmClientState.CLOSED
    }

    private fun checkNotClosed() {
        if (state == LocalLlmClientState.CLOSED) {
            throw LocalLlmException("client_closed", "Client is closed")
        }
    }
}

private fun LocalLlmMessage.toMlcMessage() = OpenAIProtocol.ChatCompletionMessage(
    role = when (role) {
        LocalLlmRole.SYSTEM -> OpenAIProtocol.ChatCompletionRole.system
        LocalLlmRole.USER -> OpenAIProtocol.ChatCompletionRole.user
        LocalLlmRole.ASSISTANT -> OpenAIProtocol.ChatCompletionRole.assistant
    },
    content = content,
)

private fun String?.toFinishReason(previous: LocalLlmFinishReason) = when (this) {
    null -> previous
    "stop" -> LocalLlmFinishReason.STOP
    "length" -> LocalLlmFinishReason.LENGTH
    else -> LocalLlmFinishReason.UNKNOWN
}

private fun OpenAIProtocol.CompletionUsage.toPublicUsage() = LocalLlmUsage(
    promptTokens = prompt_tokens,
    completionTokens = completion_tokens,
    totalTokens = total_tokens,
    prefillTokensPerSecond = extra?.prefill_tokens_per_s,
    decodeTokensPerSecond = extra?.decode_tokens_per_s,
)
