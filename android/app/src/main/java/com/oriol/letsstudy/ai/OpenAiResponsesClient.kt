package com.oriol.letsstudy.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.BufferedSource
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatGptModel(
    val slug: String,
    val displayName: String,
)

interface OpenAiResponsesClient {
    suspend fun availableModels(credentials: OAuthAuthorizationResult): List<ChatGptModel>
    suspend fun generate(credentials: OAuthAuthorizationResult, request: StudyAiRequest, model: String): String
}

internal class DefaultOpenAiResponsesClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .readTimeout(180, TimeUnit.SECONDS)
        .build(),
    private val apiBaseUrl: HttpUrl = DEFAULT_API_BASE_URL,
    allowInsecureEndpointsForTesting: Boolean = false,
) : OpenAiResponsesClient {
    init {
        require(apiBaseUrl.encodedPath.endsWith('/'))
        if (!allowInsecureEndpointsForTesting) {
            require(apiBaseUrl.isHttps && apiBaseUrl.host == API_HOST)
        }
    }

    override suspend fun availableModels(credentials: OAuthAuthorizationResult): List<ChatGptModel> {
        val request = Request.Builder()
            .url(apiBaseUrl.newBuilder().addPathSegment("models").build())
            .header("Authorization", credentials.bearerHeader())
            .header("Accept", "application/json")
            .get()
            .build()

        return execute(request) { response ->
            if (!response.isSuccessful) throw responseFailure(response)
            val body = response.body?.let { readBounded(it.source(), MAX_MODELS_RESPONSE_BYTES) }
                ?: throw invalidResponse(response)
            val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
                throw invalidResponse(response)
            }
            val entries = root.get("models")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw invalidResponse(response)
            entries.mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val model = item.asJsonObject
                val slug = model.stringValue("slug") ?: return@mapNotNull null
                val displayName = model.stringValue("display_name") ?: return@mapNotNull null
                if (model.stringValue("visibility") != "list") return@mapNotNull null
                ChatGptModel(slug, displayName)
            }
        }
    }

    override suspend fun generate(
        credentials: OAuthAuthorizationResult,
        request: StudyAiRequest,
        model: String,
    ): String {
        if (request.prompt.isBlank() || model.isBlank() || credentials.accessToken.isBlank()) {
            throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE)
        }
        val body = JsonObject().apply {
            addProperty("model", model)
            add("input", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", request.prompt)
                })
            })
            addProperty("store", false)
            addProperty("stream", true)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(apiBaseUrl.newBuilder().addPathSegment("responses").build())
            .header("Authorization", credentials.bearerHeader())
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return execute(httpRequest) { response ->
            if (!response.isSuccessful) throw responseFailure(response)
            val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
            if (!contentType.equals("text/event-stream", ignoreCase = true)) {
                throw invalidResponse(response)
            }
            val source = response.body?.source() ?: throw invalidResponse(response)
            parseStream(source, request.maxOutputTokens, response.requestId())
        }
    }

    private suspend fun <T> execute(request: Request, consume: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(StudyAiException(StudyAiErrorKind.NETWORK, cause = e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use(consume)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: CancellationException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } catch (error: StudyAiException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } catch (error: ResponseSizeException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(StudyAiException(StudyAiErrorKind.INVALID_RESPONSE))
                        }
                    } catch (error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(StudyAiException(StudyAiErrorKind.NETWORK, cause = error))
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, cause = error))
                        }
                    }
                }
            })
        }

    private fun responseFailure(response: Response): StudyAiException {
        val body = response.body?.let { runCatching { readBounded(it.source(), MAX_ERROR_RESPONSE_BYTES) }.getOrNull() }
        val code = body?.let { text ->
            runCatching { JsonParser.parseString(text).asJsonObject.errorCode() }.getOrNull()
        }
        return StudyAiException(
            kind = failureKind(response.code, code),
            requestId = response.requestId(),
            statusCode = response.code,
        )
    }

    private fun invalidResponse(response: Response) = StudyAiException(
        kind = StudyAiErrorKind.INVALID_RESPONSE,
        requestId = response.requestId(),
        statusCode = response.code,
    )

    private fun parseStream(source: BufferedSource, maxOutputTokens: Int, requestId: String?): String {
        val output = StringBuilder()
        val maximumOutputCharacters = (maxOutputTokens.coerceAtLeast(1).toLong() * MAX_CHARS_PER_OUTPUT_TOKEN)
            .coerceAtMost(MAX_OUTPUT_CHARACTERS.toLong())
            .toInt()
        var eventName: String? = null
        val eventData = StringBuilder()
        var streamBytes = 0L

        while (true) {
            val line = try {
                source.readUtf8LineStrict(MAX_SSE_LINE_BYTES.toLong())
            } catch (_: EOFException) {
                break
            }
            streamBytes += line.toByteArray(StandardCharsets.UTF_8).size + 1
            if (streamBytes > MAX_STREAM_BYTES) {
                throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
            }

            if (line.isEmpty()) {
                val terminal = processEvent(eventName, eventData.toString(), output, maximumOutputCharacters, requestId)
                eventName = null
                eventData.clear()
                if (terminal) return output.toString()
            } else if (!line.startsWith(':')) {
                when {
                    line.startsWith("event:") -> eventName = line.substringAfter(':').trimStart()
                    line.startsWith("data:") -> {
                        val dataLine = line.substringAfter(':').trimStart()
                        if (eventData.length + dataLine.length + 1 > MAX_SSE_EVENT_BYTES) {
                            throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
                        }
                        eventData.append(dataLine).append('\n')
                    }
                }
            }
        }

        throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
    }

    private fun processEvent(
        eventName: String?,
        eventData: String,
        output: StringBuilder,
        maximumOutputCharacters: Int,
        requestId: String?,
    ): Boolean {
        if (eventData.isBlank()) return false
        val payload = runCatching { JsonParser.parseString(eventData.trim()).asJsonObject }.getOrElse {
            throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
        }
        val type = payload.stringValue("type") ?: eventName
        when (type) {
            "response.output_text.delta" -> {
                val delta = payload.stringValue("delta")
                    ?: throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
                if (output.length.toLong() + delta.length > maximumOutputCharacters) {
                    throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
                }
                output.append(delta)
            }
            "response.failed", "error" -> {
                val code = payload.errorCodeFromEvent()
                throw StudyAiException(failureKind(200, code), requestId, statusCode = 200)
            }
            "response.incomplete" -> throw StudyAiException(
                StudyAiErrorKind.INVALID_RESPONSE,
                requestId,
                statusCode = 200,
            )
            "response.completed" -> {
                if (output.isBlank()) {
                    output.append(payload.completedOutputText())
                }
                if (output.isBlank()) {
                    throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
                }
                if (output.length > maximumOutputCharacters) {
                    throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE, requestId, statusCode = 200)
                }
                return true
            }
        }
        return false
    }

    private fun JsonObject.completedOutputText(): String {
        val response = get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: return ""
        val output = response.get("output")?.takeIf { it.isJsonArray }?.asJsonArray ?: return ""
        return buildString {
            output.forEach { item ->
                val content = item.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("content")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
                content.forEach { part ->
                    val text = part.takeIf { it.isJsonObject }?.asJsonObject
                        ?.takeIf { it.stringValue("type") == "output_text" }
                        ?.stringValue("text")
                    if (text != null) append(text)
                }
            }
        }
    }

    private fun JsonObject.errorCodeFromEvent(): String? {
        get("response")?.takeIf { it.isJsonObject }?.asJsonObject?.get("error")
            ?.takeIf { it.isJsonObject }?.asJsonObject?.stringValue("code")?.let { return it }
        return get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.stringValue("code")
            ?: stringValue("code")
    }

    private fun JsonObject.errorCode(): String? {
        val error = get("error") ?: return null
        if (!error.isJsonObject) return null
        return error.asJsonObject.stringValue("code")
    }

    private fun failureKind(status: Int, code: String?): StudyAiErrorKind = when {
        code == "subscription_sharing_usage_limit_exceeded" -> StudyAiErrorKind.USAGE_LIMIT
        code == "subscription_sharing_usage_unavailable" -> StudyAiErrorKind.USAGE_UNAVAILABLE
        code == "subscription_sharing_unsupported_capability" ||
            code == "subscription_sharing_route_not_supported" -> StudyAiErrorKind.UNSUPPORTED_CAPABILITY
        code == "subscription_sharing_user_not_eligible" -> StudyAiErrorKind.NOT_ELIGIBLE
        code == "subscription_sharing_invalid_user" || status == 401 -> StudyAiErrorKind.AUTH_REQUIRED
        status == 403 -> StudyAiErrorKind.NOT_ELIGIBLE
        status == 429 || status >= 500 || status == 200 -> StudyAiErrorKind.TEMPORARY_SERVICE
        else -> StudyAiErrorKind.INVALID_RESPONSE
    }

    private fun readBounded(source: BufferedSource, maximumBytes: Long): String {
        val buffer = Buffer()
        while (buffer.size <= maximumBytes) {
            val remaining = maximumBytes + 1 - buffer.size
            val read = source.read(buffer, minOf(8_192L, remaining))
            if (read == -1L) break
        }
        if (buffer.size > maximumBytes) throw ResponseSizeException()
        return buffer.readUtf8()
    }

    private fun Response.requestId(): String? = header("x-request-id")
        ?.takeIf { it.length in 1..128 && it.all { character -> character.isLetterOrDigit() || character in "-_" } }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun OAuthAuthorizationResult.bearerHeader(): String {
        if (accessToken.isBlank()) throw StudyAiException(StudyAiErrorKind.AUTH_REQUIRED)
        return "Bearer $accessToken"
    }

    companion object {
        private const val API_HOST = "api.openai.com"
        private const val MAX_MODELS_RESPONSE_BYTES = 1_048_576L
        private const val MAX_ERROR_RESPONSE_BYTES = 32_768L
        private const val MAX_SSE_LINE_BYTES = 65_536
        private const val MAX_SSE_EVENT_BYTES = 131_072
        private const val MAX_STREAM_BYTES = 2_097_152L
        private const val MAX_OUTPUT_CHARACTERS = 1_000_000
        private const val MAX_CHARS_PER_OUTPUT_TOKEN = 16L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DEFAULT_API_BASE_URL = "https://api.openai.com/v1/".toHttpUrl()
    }

    private class ResponseSizeException : IOException()
}
