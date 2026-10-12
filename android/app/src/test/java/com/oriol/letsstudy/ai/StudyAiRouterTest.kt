package com.oriol.letsstudy.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class StudyAiRouterTest {
    private val request = StudyAiRequest("Explain the topic", StudyAiTask.TUTOR, 600)

    @Test
    fun routesToSelectedProvider() = runBlocking {
        val gemini = FakeProvider(StudyAiProviderId.GEMINI) { "Gemini response" }
        val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { "ChatGPT response" }

        val result = StudyAiRouter(mapOf(gemini.id to gemini, chatGpt.id to chatGpt))
            .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = false)

        assertEquals("ChatGPT response", result)
        assertEquals(0, gemini.calls)
        assertEquals(1, chatGpt.calls)
    }

    @Test
    fun fallsBackOnlyForOptedInChatGptUsageLimit() = runBlocking {
        val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { throw StudyAiException(StudyAiErrorKind.USAGE_LIMIT) }
        val local = FakeProvider(StudyAiProviderId.ON_DEVICE) { "Local response" }
        var fallbackTask: StudyAiTask? = null

        val result = StudyAiRouter(mapOf(chatGpt.id to chatGpt, local.id to local), onLocalFallback = { fallbackTask = it })
            .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = true)

        assertEquals("Local response", result)
        assertEquals(StudyAiTask.TUTOR, fallbackTask)
        assertEquals(1, chatGpt.calls)
        assertEquals(1, local.calls)
    }

    @Test
    fun reportsModelUnavailableAfterUsageLimit() = runBlocking {
        val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { throw StudyAiException(StudyAiErrorKind.USAGE_LIMIT) }
        val local = FakeProvider(StudyAiProviderId.ON_DEVICE) { throw ModelUnavailableException() }

        try {
            StudyAiRouter(mapOf(chatGpt.id to chatGpt, local.id to local))
                .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = true)
            fail("Expected a model availability error")
        } catch (error: StudyAiException) {
            assertEquals(StudyAiErrorKind.MODEL_NOT_AVAILABLE, error.kind)
            assertEquals(StudyAiErrorKind.USAGE_LIMIT, error.fallbackReason)
        }
        assertEquals(1, local.calls)
    }

    @Test
    fun doesNotFallbackFor503OrNetworkFailure() = runBlocking {
        listOf(StudyAiErrorKind.USAGE_UNAVAILABLE, StudyAiErrorKind.NETWORK).forEach { kind ->
            val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { throw StudyAiException(kind) }
            val local = FakeProvider(StudyAiProviderId.ON_DEVICE) { "Local response" }

            try {
                StudyAiRouter(mapOf(chatGpt.id to chatGpt, local.id to local))
                    .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = true)
                fail("Expected $kind to be returned without fallback")
            } catch (error: StudyAiException) {
                assertEquals(kind, error.kind)
            }
            assertEquals(0, local.calls)
        }
    }

    @Test
    fun doesNotCallGeminiWhenChatGptFallbackIsLocal() = runBlocking {
        val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { throw StudyAiException(StudyAiErrorKind.USAGE_LIMIT) }
        val local = FakeProvider(StudyAiProviderId.ON_DEVICE) { "Local response" }
        val gemini = FakeProvider(StudyAiProviderId.GEMINI) { "Gemini response" }

        val result = StudyAiRouter(mapOf(chatGpt.id to chatGpt, local.id to local, gemini.id to gemini))
            .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = true)

        assertEquals("Local response", result)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun doesNotFallbackWhenUsageLimitFallbackIsDisabled() = runBlocking {
        val chatGpt = FakeProvider(StudyAiProviderId.CHATGPT) { throw StudyAiException(StudyAiErrorKind.USAGE_LIMIT) }
        val local = FakeProvider(StudyAiProviderId.ON_DEVICE) { "Local response" }

        try {
            StudyAiRouter(mapOf(chatGpt.id to chatGpt, local.id to local))
                .generate(request, StudyAiProviderId.CHATGPT, localFallbackOnChatGptLimit = false)
            fail("Expected usage limit")
        } catch (error: StudyAiException) {
            assertEquals(StudyAiErrorKind.USAGE_LIMIT, error.kind)
        }
        assertEquals(0, local.calls)
    }

    private class FakeProvider(
        override val id: StudyAiProviderId,
        private val response: suspend (StudyAiRequest) -> String,
    ) : StudyAiProvider {
        var calls = 0

        override suspend fun generate(request: StudyAiRequest): String {
            calls++
            return response(request)
        }
    }
}
