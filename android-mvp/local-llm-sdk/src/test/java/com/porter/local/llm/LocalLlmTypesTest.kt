package com.porter.local.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalLlmTypesTest {
    @Test
    fun validRequestKeepsCloudStyleFields() {
        val request = LocalLlmRequest(
            model = "local/qwen2.5-0.5b-instruct@1",
            messages = listOf(LocalLlmMessage(LocalLlmRole.USER, "hello")),
            temperature = 0.7f,
            topP = 0.9f,
            maxOutputTokens = 64,
            stop = listOf("</answer>"),
        )

        assertEquals(64, request.maxOutputTokens)
        assertEquals(LocalLlmRole.USER, request.messages.single().role)
    }

    @Test
    fun emptyMessagesAreRejectedBeforeBackendCall() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalLlmRequest(model = "model", messages = emptyList())
        }
    }

    @Test
    fun invalidSamplingIsRejectedBeforeBackendCall() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalLlmRequest(
                model = "model",
                messages = listOf(LocalLlmMessage(LocalLlmRole.USER, "hello")),
                topP = 1.1f,
            )
        }
    }
}
