package com.oriol.letsstudy.ai

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiResponsesClientTest {
    private val server = MockWebServer()
    private lateinit var client: DefaultOpenAiResponsesClient
    private val credentials = OAuthAuthorizationResult(
        issuedClientId = "issued-client",
        subject = "account-subject",
        idToken = "id-token",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtMillis = 1_800_000_000_000,
        grantedScopes = setOf("openid", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct"),
        hostId = "urn:uuid:95cd8899-fd47-47c8-9f3b-06cac6921a70",
    )

    @Before
    fun setUp() {
        server.start()
        client = DefaultOpenAiResponsesClient(
            httpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
            apiBaseUrl = server.url("/v1/"),
            allowInsecureEndpointsForTesting = true,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listsOnlyModelsVisibleToTheSignedInAccount() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"models":[{"slug":"first-model","display_name":"First","visibility":"list"},{"slug":"hidden","display_name":"Hidden","visibility":"internal"},{"slug":"second-model","display_name":"Second","visibility":"list"}]}""",
            ),
        )

        val models = client.availableModels(credentials)
        val request = server.takeRequest()

        assertEquals("/v1/models", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals(listOf("first-model", "second-model"), models.map(ChatGptModel::slug))
        assertEquals(listOf("First", "Second"), models.map(ChatGptModel::displayName))
    }

    @Test
    fun postsStoredFalseAndStreamingResponsesRequest() = runBlocking {
        server.enqueue(sseResponse(
            "response.output_text.delta" to """{"type":"response.output_text.delta","delta":"Hello "}""",
            "response.output_text.delta" to """{"type":"response.output_text.delta","delta":"there."}""",
            "response.completed" to completedEvent("Hello there."),
        ))

        val text = client.generate(credentials, request(), "account-model")
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("Hello there.", text)
        assertEquals("POST", request.method)
        assertEquals("/v1/responses", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals("account-model", body.get("model").asString)
        assertFalse(body.get("store").asBoolean)
        assertTrue(body.get("stream").asBoolean)
    }

    @Test
    fun rejectsFailedAndInterruptedStreams() = runBlocking {
        server.enqueue(sseResponse(
            "response.output_text.delta" to """{"type":"response.output_text.delta","delta":"partial answer"}""",
            "response.failed" to """{"type":"response.failed","response":{"error":{"code":"server_error"}}}""",
        ))
        val failed = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull()
        assertTrue(failed is StudyAiException)
        assertEquals(StudyAiErrorKind.TEMPORARY_SERVICE, (failed as StudyAiException).kind)

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(
                    "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n",
                    8,
                )
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val interrupted = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull()
        assertTrue(interrupted is StudyAiException)
        assertTrue((interrupted as StudyAiException).kind in setOf(StudyAiErrorKind.NETWORK, StudyAiErrorKind.INVALID_RESPONSE))
    }

    @Test
    fun mapsUsageLimitAndUnavailableSeparately() = runBlocking {
        server.enqueue(sseResponse(
            "response.failed" to """{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}""",
        ))
        val limited = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull()
        assertTrue(limited is StudyAiException)
        assertEquals(StudyAiErrorKind.USAGE_LIMIT, (limited as StudyAiException).kind)

        server.enqueue(sseResponse(
            "response.failed" to """{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_unavailable"}}}""",
        ))
        val unavailable = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull()
        assertTrue(unavailable is StudyAiException)
        assertEquals(StudyAiErrorKind.USAGE_UNAVAILABLE, (unavailable as StudyAiException).kind)
    }

    @Test
    fun omitsUnsupportedParametersAndFilePayloads() = runBlocking {
        server.enqueue(sseResponse("response.completed" to completedEvent("A concise answer.")))
        val request = request("Use this extracted text: a public description of a pear orchard.")

        client.generate(credentials, request, "account-model")
        val bodyText = server.takeRequest().body.readUtf8()
        val body = JsonParser.parseString(bodyText).asJsonObject
        val serializedInput = body.get("input").toString()

        listOf("max_output_tokens", "temperature", "top_p", "truncation", "file_id", "input_file").forEach {
            assertFalse("Unexpected request field: $it", bodyText.contains(it))
        }
        assertTrue(serializedInput.contains("public description of a pear orchard"))
        assertFalse(serializedInput.contains("file"))
    }

    @Test
    fun mapsHttpUsageErrorsAndKeepsOnlySafeMetadata() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("x-request-id", "req-limit")
                .setBody("""{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"private prompt echoed here"}}"""),
        )
        val limit = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull() as StudyAiException
        assertEquals(StudyAiErrorKind.USAGE_LIMIT, limit.kind)
        assertEquals("req-limit", limit.requestId)
        assertEquals(429, limit.statusCode)
        assertFalse(limit.message.orEmpty().contains("private prompt"))

        server.enqueue(
            MockResponse().setResponseCode(503)
                .setHeader("x-request-id", "req-unavailable")
                .setBody("""{"error":{"code":"subscription_sharing_usage_unavailable"}}"""),
        )
        val unavailable = runCatching { client.generate(credentials, request(), "account-model") }.exceptionOrNull() as StudyAiException
        assertEquals(StudyAiErrorKind.USAGE_UNAVAILABLE, unavailable.kind)
        assertEquals(503, unavailable.statusCode)
    }

    private fun request(prompt: String = "Create a short study explanation.") =
        StudyAiRequest(prompt = prompt, task = StudyAiTask.TUTOR, maxOutputTokens = 300)

    private fun sseResponse(vararg events: Pair<String, String>) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setChunkedBody(
            events.joinToString(separator = "\n") { (event, data) -> "event: $event\ndata: $data\n" } + "\n",
            24,
        )

    private fun completedEvent(text: String): String =
        """{"type":"response.completed","response":{"output":[{"type":"message","content":[{"type":"output_text","text":"$text"}]}]}}"""
}
