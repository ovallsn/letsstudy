package com.oriol.letsstudy.ai

import android.content.Context
import android.content.Intent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class OAuthAuthorizationAttempt(
    val authorizationUrl: String,
    val redirectUri: String,
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val clientId: String,
) {
    override fun toString(): String =
        "OAuthAuthorizationAttempt(authorizationUrl=[redacted], redirectUri=$redirectUri, state=[redacted], nonce=[redacted], codeVerifier=[redacted], clientId=$clientId)"
}

data class OAuthAuthorizationResult(
    val issuedClientId: String,
    val subject: String,
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val grantedScopes: Set<String>,
    val hostId: String,
) {
    override fun toString(): String =
        "OAuthAuthorizationResult(issuedClientId=$issuedClientId, subject=[redacted], idToken=[redacted], accessToken=[redacted], refreshToken=[redacted], expiresAtMillis=$expiresAtMillis, grantedScopes=$grantedScopes, hostId=[redacted])"
}

interface ChatGptOAuthClient {
    suspend fun connect(existing: OAuthAuthorizationResult? = null): OAuthAuthorizationResult
    suspend fun refresh(current: OAuthAuthorizationResult): OAuthAuthorizationResult
    suspend fun revoke(current: OAuthAuthorizationResult)
}

fun interface OAuthBrowserLauncher {
    fun open(url: String)
}

class AndroidOAuthBrowserLauncher(context: Context) : OAuthBrowserLauncher {
    private val applicationContext = context.applicationContext

    override fun open(url: String) {
        applicationContext.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

enum class ChatGptOAuthFailure {
    CONSENT_CANCELLED,
    INVALID_CALLBACK,
    REGISTRATION_FAILED,
    NOT_ELIGIBLE,
    AUTH_REQUIRED,
    NETWORK,
    TEMPORARY_SERVICE,
    INVALID_RESPONSE,
}

enum class ChatGptOAuthStage {
    DISCOVERY,
    CALLBACK_LISTENER,
    BROWSER,
    CALLBACK,
    TOKEN_EXCHANGE,
    IDENTITY_VERIFICATION,
}

class ChatGptOAuthException(
    val failure: ChatGptOAuthFailure,
    val requestId: String? = null,
    cause: Throwable? = null,
    val stage: ChatGptOAuthStage? = null,
) : Exception(failure.messageForUser(), cause)

internal class OpenAiChatGptOAuthClient(
    private val callbackServerFactory: () -> LoopbackOAuthCallbackServer,
    private val browserLauncher: OAuthBrowserLauncher,
    private val hostIdProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val discoveryUrl: String = OPEN_ID_CONFIGURATION_URL,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nonceProvider: (() -> String)? = null,
    private val codeVerifierProvider: (() -> String)? = null,
    private val allowInsecureEndpointsForTesting: Boolean = false,
) : ChatGptOAuthClient {
    private val secureRandom = SecureRandom()
    private val refreshMutex = Mutex()
    private val latestResults = ConcurrentHashMap<String, OAuthAuthorizationResult>()

    override suspend fun connect(existing: OAuthAuthorizationResult?): OAuthAuthorizationResult {
        val hostId = hostIdProvider().takeIf(::isValidHostId)
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        val discovery = atStage(ChatGptOAuthStage.DISCOVERY) { loadDiscovery() }
        val callbackServer = try {
            callbackServerFactory()
        } catch (error: Exception) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.NETWORK, cause = error, stage = ChatGptOAuthStage.CALLBACK_LISTENER)
        }
        try {
            val state = randomUrlSafeString()
            val nonce = nonceProvider?.invoke()?.takeIf(String::isNotBlank) ?: randomUrlSafeString()
            val verifier = codeVerifierProvider?.invoke()?.takeIf { it.length in MIN_PKCE_VERIFIER_LENGTH..MAX_PKCE_VERIFIER_LENGTH }
                ?: randomUrlSafeString()
            val clientId = existing?.issuedClientId ?: DYNAMIC_CLIENT_ID
            val attempt = buildAuthorizationAttempt(
                discovery = discovery,
                redirectUri = callbackServer.redirectUri,
                state = state,
                nonce = nonce,
                codeVerifier = verifier,
                clientId = clientId,
                existing = existing,
                hostId = hostId,
            )
            try {
                browserLauncher.open(attempt.authorizationUrl)
            } catch (error: Exception) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.NETWORK, cause = error, stage = ChatGptOAuthStage.BROWSER)
            }
            val callback = atStage(ChatGptOAuthStage.CALLBACK, ChatGptOAuthFailure.NETWORK) {
                callbackServer.awaitCallback()
            }
            val callbackValues = atStage(ChatGptOAuthStage.CALLBACK) { validateCallback(callback, attempt, existing) }
            val issuedClientId = callbackValues.clientId
            val tokenResponse = atStage(ChatGptOAuthStage.TOKEN_EXCHANGE) {
                postForm(
                    url = discovery.tokenEndpoint,
                    fields = listOf(
                        "grant_type" to "authorization_code",
                        "client_id" to issuedClientId,
                        "code" to callbackValues.code,
                        "code_verifier" to attempt.codeVerifier,
                        "redirect_uri" to attempt.redirectUri,
                        "resource" to API_RESOURCE,
                    ),
                )
            }
            val scopes = parseScopes(tokenResponse.json, required = true).intersect(REQUESTED_SCOPE_SET)
            if (!scopes.contains(PLAN_USE_SCOPE)) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.NOT_ELIGIBLE, tokenResponse.requestId)
            }
            val idToken = tokenResponse.json.stringValue("id_token")
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, tokenResponse.requestId)
            val subject = atStage(ChatGptOAuthStage.IDENTITY_VERIFICATION) {
                validateIdToken(idToken, issuedClientId, attempt.nonce, discovery.jwksUri)
            }
            if (existing != null && subject != existing.subject) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.AUTH_REQUIRED, tokenResponse.requestId)
            }
            val accessToken = tokenResponse.json.stringValue("access_token")
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, tokenResponse.requestId)
            val refreshToken = tokenResponse.json.stringValue("refresh_token")
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, tokenResponse.requestId)
            val result = OAuthAuthorizationResult(
                issuedClientId = issuedClientId,
                subject = subject,
                idToken = idToken,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expirationTime(tokenResponse.json),
                grantedScopes = scopes,
                hostId = hostId,
            )
            latestResults[issuedClientId] = result
            return result
        } finally {
            callbackServer.close()
        }
    }

    override suspend fun refresh(current: OAuthAuthorizationResult): OAuthAuthorizationResult = refreshMutex.withLock {
        val latest = latestResults[current.issuedClientId] ?: current
        if (latest.accessToken != current.accessToken && latest.expiresAtMillis > nowMillis() + REFRESH_SKEW_MILLIS) {
            return@withLock latest
        }
        val discovery = loadDiscovery()
        val tokenResponse = postForm(
            url = discovery.tokenEndpoint,
            fields = listOf(
                "grant_type" to "refresh_token",
                "client_id" to latest.issuedClientId,
                "refresh_token" to latest.refreshToken,
                "resource" to API_RESOURCE,
            ),
            allowAuthError = true,
        )
        val scopes = parseScopes(tokenResponse.json, required = false)
            .intersect(REQUESTED_SCOPE_SET)
            .ifEmpty { latest.grantedScopes }
        if (!scopes.contains(PLAN_USE_SCOPE)) {
            latestResults.remove(latest.issuedClientId)
            throw ChatGptOAuthException(ChatGptOAuthFailure.AUTH_REQUIRED, tokenResponse.requestId)
        }
        val refreshed = latest.copy(
            accessToken = tokenResponse.json.stringValue("access_token")
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, tokenResponse.requestId),
            refreshToken = tokenResponse.json.stringValue("refresh_token") ?: latest.refreshToken,
            expiresAtMillis = expirationTime(tokenResponse.json),
            grantedScopes = scopes,
        )
        latestResults[latest.issuedClientId] = refreshed
        refreshed
    }

    override suspend fun revoke(current: OAuthAuthorizationResult) {
        val discovery = loadDiscovery()
        val response = execute(
            Request.Builder()
                .url(discovery.revocationEndpoint)
                .post(
                    formBody(
                        listOf(
                            "token" to current.refreshToken,
                            "token_type_hint" to "refresh_token",
                            "client_id" to current.issuedClientId,
                        ),
                    ),
                )
                .build(),
        )
        if (response.statusCode !in 200..299) {
            throw ChatGptOAuthException(responseFailure(response.statusCode, response.json), response.requestId)
        }
        latestResults.remove(current.issuedClientId)
    }

    private suspend fun loadDiscovery(): Discovery = withContext(Dispatchers.IO) {
        val response = execute(Request.Builder().url(discoveryUrl).get().build())
        if (response.statusCode !in 200..299) {
            throw ChatGptOAuthException(responseFailure(response.statusCode, response.json), response.requestId)
        }
        val json = response.json
        if (json.stringValue("issuer") != OPEN_ID_ISSUER) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.requestId)
        }
        val authorizationEndpoint = json.stringValue("authorization_endpoint")
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.requestId)
        val tokenEndpoint = json.stringValue("token_endpoint")
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.requestId)
        val revocationEndpoint = json.stringValue("revocation_endpoint")
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.requestId)
        val jwksUri = json.stringValue("jwks_uri")
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.requestId)
        listOf(authorizationEndpoint, tokenEndpoint, revocationEndpoint, jwksUri).forEach(::validateServiceUrl)
        Discovery(authorizationEndpoint, tokenEndpoint, revocationEndpoint, jwksUri)
    }

    private suspend fun <T> atStage(
        stage: ChatGptOAuthStage,
        unexpectedFailure: ChatGptOAuthFailure = ChatGptOAuthFailure.INVALID_RESPONSE,
        operation: suspend () -> T,
    ): T = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ChatGptOAuthException) {
        if (error.stage != null) throw error
        throw ChatGptOAuthException(error.failure, error.requestId, error.cause, stage)
    } catch (error: Exception) {
        throw ChatGptOAuthException(unexpectedFailure, stage = stage, cause = error)
    }

    private fun buildAuthorizationAttempt(
        discovery: Discovery,
        redirectUri: String,
        state: String,
        nonce: String,
        codeVerifier: String,
        clientId: String,
        existing: OAuthAuthorizationResult?,
        hostId: String,
    ): OAuthAuthorizationAttempt {
        val redirect = parseUri(redirectUri)
        if (redirect.scheme != "http" || redirect.host != LOOPBACK_ADDRESS || redirect.path != CALLBACK_PATH || redirect.port <= 0) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
        }
        val codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        val builder = discovery.authorizationEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("ext_agent_host_id", hostId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("scope", REQUESTED_SCOPES.joinToString(" "))
            .addQueryParameter("resource", API_RESOURCE)
            .addQueryParameter("state", state)
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", codeChallenge)
        if (existing == null) {
            builder.addQueryParameter("agent_name_hint", AGENT_NAME)
        } else {
            builder.addQueryParameter("id_token_hint", existing.idToken)
        }
        return OAuthAuthorizationAttempt(
            authorizationUrl = builder.build().toString(),
            redirectUri = redirectUri,
            state = state,
            nonce = nonce,
            codeVerifier = codeVerifier,
            clientId = clientId,
        )
    }

    private fun validateCallback(
        callback: String,
        attempt: OAuthAuthorizationAttempt,
        existing: OAuthAuthorizationResult?,
    ): CallbackValues {
        val callbackUri = parseUri(callback)
        val expectedUri = parseUri(attempt.redirectUri)
        if (callbackUri.scheme != expectedUri.scheme || callbackUri.host != expectedUri.host ||
            callbackUri.port != expectedUri.port || callbackUri.path != CALLBACK_PATH || callbackUri.rawFragment != null
        ) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
        }
        val values = parseQuery(callbackUri.rawQuery ?: "")
        val returnedState = values.single("state")
        if (returnedState == null || !MessageDigest.isEqual(
                attempt.state.toByteArray(StandardCharsets.UTF_8),
                returnedState.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
        }
        values.single("error")?.let { error ->
            if (error == "access_denied") throw ChatGptOAuthException(ChatGptOAuthFailure.CONSENT_CANCELLED)
            throw ChatGptOAuthException(ChatGptOAuthFailure.REGISTRATION_FAILED)
        }
        val code = values.single("code")?.takeIf(String::isNotBlank)
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
        val callbackClientId = values.single("client_id")
        val issuedClientId = if (existing == null) {
            callbackClientId?.takeIf { it.isNotBlank() && it != DYNAMIC_CLIENT_ID }
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.REGISTRATION_FAILED)
        } else {
            if (callbackClientId != null && callbackClientId != existing.issuedClientId) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
            }
            existing.issuedClientId
        }
        return CallbackValues(code, issuedClientId)
    }

    private suspend fun validateIdToken(
        idToken: String,
        clientId: String,
        expectedNonce: String,
        jwksUri: String,
    ): String = withContext(Dispatchers.IO) {
        try {
            val parsed = SignedJWT.parse(idToken)
            if (parsed.header.algorithm != JWSAlgorithm.RS256) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
            }
            val jwksResponse = execute(Request.Builder().url(jwksUri).get().build())
            if (jwksResponse.statusCode !in 200..299) {
                throw ChatGptOAuthException(responseFailure(jwksResponse.statusCode, jwksResponse.json), jwksResponse.requestId)
            }
            val jwkSet = JWKSet.parse(jwksResponse.body)
            val keySource = JWKSource<SecurityContext> { selector, _ -> selector.select(jwkSet) }
            val processor = DefaultJWTProcessor<SecurityContext>().apply {
                jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, keySource)
            }
            val claims = processor.process(parsed, null as SecurityContext?)
            val now = nowMillis()
            val subject = claims.subject?.takeIf(String::isNotBlank)
            if (claims.issuer != OPEN_ID_ISSUER || clientId !in claims.audience ||
                claims.expirationTime == null || claims.expirationTime.time <= now ||
                claims.getStringClaim("nonce") != expectedNonce || subject == null
            ) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
            }
            subject
        } catch (error: ChatGptOAuthException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
    }

    private suspend fun postForm(
        url: String,
        fields: List<Pair<String, String>>,
        allowAuthError: Boolean = false,
    ): JsonResponse {
        val response = execute(Request.Builder().url(url).post(formBody(fields)).build())
        if (response.statusCode !in 200..299) {
            val failure = if (allowAuthError && response.isInvalidGrant) {
                ChatGptOAuthFailure.AUTH_REQUIRED
            } else {
                responseFailure(response.statusCode, response.json)
            }
            if (failure == ChatGptOAuthFailure.AUTH_REQUIRED) latestResults.clear()
            throw ChatGptOAuthException(failure, response.requestId)
        }
        return response
    }

    private suspend fun execute(request: Request): JsonResponse {
        var dnsRetry = 0
        while (true) {
            try {
                return withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use(::readResponse)
                }
            } catch (error: ChatGptOAuthException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: UnknownHostException) {
                if (dnsRetry == DNS_RETRY_DELAYS_MILLIS.size) {
                    throw ChatGptOAuthException(ChatGptOAuthFailure.NETWORK, cause = error)
                }
                dnsRetry += 1
                delay(DNS_RETRY_DELAYS_MILLIS[dnsRetry - 1])
            } catch (error: IOException) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.NETWORK, cause = error)
            } catch (error: Exception) {
                throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
            }
        }
    }

    private fun readResponse(response: Response): JsonResponse {
        val responseBody = response.body
        if (responseBody != null && responseBody.contentLength() > MAX_RESPONSE_BYTES) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.header("x-request-id"))
        }
        val buffer = Buffer()
        val source = responseBody?.source()
        if (source != null) {
            while (true) {
                val read = source.read(buffer, RESPONSE_READ_CHUNK_BYTES)
                if (read == -1L) break
                if (buffer.size > MAX_RESPONSE_BYTES) {
                    throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.header("x-request-id"))
                }
            }
        }
        val bytes = buffer.readByteArray()
        val body = String(bytes, StandardCharsets.UTF_8)
        val json = if (body.isBlank()) JsonObject() else try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.header("x-request-id"))
        } catch (error: ChatGptOAuthException) {
            throw error
        } catch (error: Exception) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE, response.header("x-request-id"))
        }
        val errorCode = json.errorCode()
        return JsonResponse(response.code, body, json, response.header("x-request-id"), errorCode)
    }

    private fun formBody(fields: List<Pair<String, String>>): FormBody = FormBody.Builder().apply {
        fields.forEach { (key, value) -> add(key, value) }
    }.build()

    private fun parseScopes(json: JsonObject, required: Boolean): Set<String> {
        val value = json.stringValue("scope")
        if (required && value == null) throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        return value?.split(Regex("\\s+"))?.filter(String::isNotBlank)?.toSet().orEmpty()
    }

    private fun expirationTime(json: JsonObject): Long {
        val expiresIn = json.get("expires_in")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong?.takeIf { it > 0 }
            ?: throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        return try {
            Math.addExact(nowMillis(), Math.multiplyExact(expiresIn, 1_000L))
        } catch (error: ArithmeticException) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
    }

    private fun randomUrlSafeString(): String {
        val bytes = ByteArray(PKCE_RANDOM_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun validateServiceUrl(value: String) {
        val url = try {
            value.toHttpUrl()
        } catch (error: IllegalArgumentException) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
        if (!allowInsecureEndpointsForTesting && (url.scheme != "https" || url.host != AUTH_HOST)) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
        if (allowInsecureEndpointsForTesting && url.scheme !in setOf("http", "https")) {
            throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_RESPONSE)
        }
    }

    private fun isValidHostId(value: String): Boolean {
        val rawUuid = value.removePrefix("urn:uuid:")
        if (rawUuid == value) return false
        val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull() ?: return false
        return uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == rawUuid
    }

    private fun parseUri(value: String): URI = try {
        URI(value)
    } catch (error: Exception) {
        throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
    }

    private fun parseQuery(query: String): QueryValues {
        val values = mutableMapOf<String, MutableList<String>>()
        query.split('&').filter(String::isNotEmpty).forEach { part ->
            val separator = part.indexOf('=')
            val rawKey = if (separator < 0) part else part.substring(0, separator)
            val rawValue = if (separator < 0) "" else part.substring(separator + 1)
            val key = decodeQueryValue(rawKey)
            val value = decodeQueryValue(rawValue)
            values.getOrPut(key) { mutableListOf() }.add(value)
        }
        return QueryValues(values)
    }

    private fun decodeQueryValue(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (error: Exception) {
        throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
    }

    private fun responseFailure(statusCode: Int, json: JsonObject): ChatGptOAuthFailure = when {
        statusCode == 401 -> ChatGptOAuthFailure.AUTH_REQUIRED
        statusCode == 400 && json.errorCode() == "invalid_grant" -> ChatGptOAuthFailure.AUTH_REQUIRED
        statusCode >= 500 || statusCode == 429 -> ChatGptOAuthFailure.TEMPORARY_SERVICE
        statusCode in 400..499 -> ChatGptOAuthFailure.REGISTRATION_FAILED
        else -> ChatGptOAuthFailure.INVALID_RESPONSE
    }

    private data class Discovery(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val revocationEndpoint: String,
        val jwksUri: String,
    )

    private data class CallbackValues(val code: String, val clientId: String)

    private data class QueryValues(val values: Map<String, List<String>>) {
        fun single(name: String): String? {
            val entries = values[name] ?: return null
            if (entries.size != 1) throw ChatGptOAuthException(ChatGptOAuthFailure.INVALID_CALLBACK)
            return entries.single()
        }
    }

    private data class JsonResponse(
        val statusCode: Int,
        val body: String,
        val json: JsonObject,
        val requestId: String?,
        private val errorCode: String?,
    ) {
        val isInvalidGrant: Boolean get() = errorCode == "invalid_grant"
    }

    companion object {
        private const val OPEN_ID_ISSUER = "https://auth.openai.com"
        private const val OPEN_ID_CONFIGURATION_URL = "$OPEN_ID_ISSUER/.well-known/openid-configuration"
        private const val AUTH_HOST = "auth.openai.com"
        private const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
        private const val AGENT_NAME = "LetsStudy"
        private const val API_RESOURCE = "https://api.openai.com/v1"
        private const val PLAN_USE_SCOPE = "chatgpt.tokens.use.direct"
        private const val CALLBACK_PATH = "/auth/callback"
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
        private const val PKCE_RANDOM_BYTES = 32
        private const val MIN_PKCE_VERIFIER_LENGTH = 43
        private const val MAX_PKCE_VERIFIER_LENGTH = 128
        private const val REFRESH_SKEW_MILLIS = 30_000L
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val RESPONSE_READ_CHUNK_BYTES = 8_192L
        private val DNS_RETRY_DELAYS_MILLIS = longArrayOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)
        private val REQUESTED_SCOPES = listOf("openid", "offline_access", "resource.invoke", PLAN_USE_SCOPE)
        private val REQUESTED_SCOPE_SET = REQUESTED_SCOPES.toSet()
    }
}

private fun JsonObject.stringValue(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.errorCode(): String? {
    val error = get("error") ?: return null
    if (error.isJsonObject) return error.asJsonObject.stringValue("code")
    return error.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}

private fun ChatGptOAuthFailure.messageForUser(): String = when (this) {
    ChatGptOAuthFailure.CONSENT_CANCELLED -> "ChatGPT connection was cancelled."
    ChatGptOAuthFailure.INVALID_CALLBACK -> "The ChatGPT sign-in response could not be verified."
    ChatGptOAuthFailure.REGISTRATION_FAILED -> "ChatGPT could not complete this connection."
    ChatGptOAuthFailure.NOT_ELIGIBLE -> "This ChatGPT account did not grant study access."
    ChatGptOAuthFailure.AUTH_REQUIRED -> "Reconnect your ChatGPT account to continue."
    ChatGptOAuthFailure.NETWORK -> "Could not reach ChatGPT. Check your connection and try again."
    ChatGptOAuthFailure.TEMPORARY_SERVICE -> "ChatGPT is temporarily unavailable. Try again later."
    ChatGptOAuthFailure.INVALID_RESPONSE -> "ChatGPT returned a response that could not be verified."
}
