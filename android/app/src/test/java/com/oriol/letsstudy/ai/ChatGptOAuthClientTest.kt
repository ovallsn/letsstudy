package com.oriol.letsstudy.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.TimeUnit

class ChatGptOAuthClientTest {
    private val server = MockWebServer().apply { start() }
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val issuedClientId = "oaiapp_letsstudy_test"
    private val hostId = "urn:uuid:95cd8899-fd47-47c8-9f3b-06cac6921a70"

    @After
    fun closeServer() {
        server.shutdown()
    }

    @Test
    fun buildsAuthorizationUrlWithMinimumScopesAndPkceS256() = runBlocking {
        enqueueDiscovery()
        enqueueToken(subject = "subject-1")
        enqueueJwks()
        val callback = FakeLoopbackOAuthCallbackServer()
        val client = client(callback)

        val result = client.connect()
        val authorization = requireNotNull(callback.authorizationUrl)
        val query = URI(authorization).rawQuery.split('&').associate {
            val parts = it.split('=', limit = 2)
            java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
        }
        val expectedScopes = setOf("openid", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct")
        val verifier = query.getValue("code_challenge")

        assertEquals("/authorize", URI(authorization).path)
        assertEquals("dynamic_agent_client", query["client_id"])
        assertEquals("LetsStudy", query["agent_name_hint"])
        assertEquals(hostId, query["ext_agent_host_id"])
        assertEquals(expectedScopes, query.getValue("scope").split(' ').toSet())
        assertFalse(query.getValue("scope").contains("email"))
        assertFalse(query.getValue("scope").contains("profile"))
        assertEquals("S256", query["code_challenge_method"])
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest("v".repeat(43).toByteArray()),
        )
        assertEquals(expectedChallenge, query["code_challenge"])
        assertTrue(query.getValue("state").isNotBlank())
        assertTrue(query.getValue("nonce").isNotBlank())
        assertTrue(verifier.length >= 43)
        assertTrue(query.getValue("redirect_uri").startsWith("http://127.0.0.1:"))
        assertTrue(query.getValue("redirect_uri").endsWith("/auth/callback"))
        assertEquals("subject-1", result.subject)
        assertEquals(issuedClientId, result.issuedClientId)
        assertFalse(result.grantedScopes.contains("email"))
        assertFalse(result.grantedScopes.contains("profile"))
        assertFalse(result.toString().contains("not-stored@example.com"))
        assertFalse(result.toString().contains("access-secret"))
        assertFalse(result.toString().contains("refresh-secret"))

        val discovery = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", discovery.method)
        val exchange = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val exchangeBody = exchange.body.readUtf8()
        assertEquals("authorization_code", exchangeBody.formValue("grant_type"))
        assertEquals(issuedClientId, exchangeBody.formValue("client_id"))
        assertEquals("code-1", exchangeBody.formValue("code"))
        assertEquals(query.getValue("redirect_uri"), exchangeBody.formValue("redirect_uri"))
    }

    @Test
    fun rejectsCallbackWithWrongStateOrRedirect() = runBlocking {
        listOf<(String, String) -> String>(
            { redirect, state -> "$redirect?code=code-1&state=wrong&client_id=$issuedClientId" },
            { _, state -> "http://127.0.0.1:1456/auth/callback?code=code-1&state=$state&client_id=$issuedClientId" },
        ).forEach { callbackBuilder ->
            enqueueDiscovery()
            val callback = FakeLoopbackOAuthCallbackServer(callbackBuilder)
            val client = client(callback)

            val priorRequestCount = server.requestCount
            assertOAuthFailure { client(callback).connect() }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals(priorRequestCount + 1, server.requestCount)
        }
    }

    @Test
    fun rejectsMissingPlanScopeAndAccountSwitch() = runBlocking {
        enqueueDiscovery()
        enqueueToken(subject = "subject-1", scope = "openid offline_access resource.invoke")
        val first = client(FakeLoopbackOAuthCallbackServer())
        assertOAuthFailure { first.connect() }
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        enqueueDiscovery()
        enqueueToken(subject = "different-subject")
        enqueueJwks()
        val current = sampleCredentials(subject = "subject-1")
        val second = client(FakeLoopbackOAuthCallbackServer())
        assertOAuthFailure { second.connect(existing = current) }
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
    }

    @Test
    fun reusesIssuedClientIdForReconnect() = runBlocking {
        enqueueDiscovery()
        enqueueToken(subject = "subject-1")
        enqueueJwks()
        val callback = FakeLoopbackOAuthCallbackServer { redirect, state ->
            "$redirect?code=code-2&state=$state"
        }
        val result = client(callback).connect(existing = sampleCredentials(subject = "subject-1"))
        val query = URI(requireNotNull(callback.authorizationUrl)).rawQuery

        assertTrue(query.contains("client_id=$issuedClientId"))
        assertFalse(query.contains("dynamic_agent_client"))
        assertTrue(query.contains("id_token_hint"))
        assertFalse(query.contains("login_hint"))
        assertEquals(issuedClientId, result.issuedClientId)
        assertEquals("subject-1", result.subject)
    }

    @Test
    fun handlesConsentCancellationWithoutTokenExchange() = runBlocking {
        enqueueDiscovery()
        val callback = FakeLoopbackOAuthCallbackServer { redirect, state ->
            "$redirect?error=access_denied&state=$state"
        }

        assertOAuthFailure { client(callback).connect() }

        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/.well-known/openid-configuration", request.path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun concurrentRefreshUsesOneRotatedTokenSet() = runBlocking {
        enqueueDiscovery()
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"rotated-access","refresh_token":"rotated-refresh","expires_in":3600,"scope":"openid offline_access resource.invoke chatgpt.tokens.use.direct"}""",
            ),
        )
        val oauth = client(FakeLoopbackOAuthCallbackServer())
        val current = sampleCredentials(subject = "subject-1")

        val results = coroutineScope { List(6) { async { oauth.refresh(current) } }.map { it.await() } }

        assertTrue(results.all { it.accessToken == "rotated-access" })
        assertTrue(results.all { it.refreshToken == "rotated-refresh" })
        assertEquals(2, server.requestCount)
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val refreshRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = refreshRequest.body.readUtf8()
        assertTrue(body.contains("client_id=$issuedClientId"))
        assertTrue(body.contains("refresh_token=refresh-secret"))
        assertFalse(body.contains("scope="))
    }

    @Test
    fun revocationUsesIssuedClientIdAndNeverLogsTokens() = runBlocking {
        enqueueDiscovery()
        server.enqueue(MockResponse().setResponseCode(200))
        val current = sampleCredentials(subject = "subject-1")
        val oauth = client(FakeLoopbackOAuthCallbackServer())

        oauth.revoke(current)

        val discovery = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val revocation = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = revocation.body.readUtf8()
        assertEquals("/.well-known/openid-configuration", discovery.path)
        assertTrue(body.contains("client_id=$issuedClientId"))
        assertTrue(body.contains("token=refresh-secret"))
        assertTrue(body.contains("token_type_hint=refresh_token"))
        assertFalse(revocation.toString().contains("access-secret"))
        assertFalse(current.toString().contains("refresh-secret"))
    }

    private fun client(callback: FakeLoopbackOAuthCallbackServer): OpenAiChatGptOAuthClient =
        OpenAiChatGptOAuthClient(
            callbackServerFactory = { callback },
            browserLauncher = OAuthBrowserLauncher { callback.onAuthorizationOpened(it) },
            hostIdProvider = { hostId },
            httpClient = OkHttpClient(),
            discoveryUrl = server.url(".well-known/openid-configuration").toString(),
            nowMillis = { 1_800_000_000_000L },
            nonceProvider = { "nonce-placeholder" },
            codeVerifierProvider = { "v".repeat(43) },
            allowInsecureEndpointsForTesting = true,
        )

    private fun enqueueDiscovery() {
        server.enqueue(
            MockResponse().setBody(
                """{"issuer":"https://auth.openai.com","authorization_endpoint":"${server.url("authorize")}","token_endpoint":"${server.url("token")}","revocation_endpoint":"${server.url("revoke")}","jwks_uri":"${server.url("jwks")}"}""",
            ),
        )
    }

    private fun enqueueToken(
        subject: String,
        scope: String = "openid offline_access resource.invoke chatgpt.tokens.use.direct email profile",
    ) {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"access-secret","refresh_token":"refresh-secret","id_token":"${idToken(subject)}","expires_in":3600,"scope":"$scope"}""",
            ),
        )
    }

    private fun enqueueJwks() {
        val publicKey = keyPair.public as java.security.interfaces.RSAPublicKey
        val modulus = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.modulus.toUnsignedBytes())
        val exponent = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.publicExponent.toUnsignedBytes())
        server.enqueue(
            MockResponse().setBody(
                """{"keys":[{"kty":"RSA","kid":"test-key","use":"sig","alg":"RS256","n":"$modulus","e":"$exponent"}]}""",
            ),
        )
    }

    private fun idToken(subject: String): String {
        val nonce = "nonce-placeholder"
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"alg":"RS256","kid":"test-key","typ":"JWT"}""".toByteArray(),
        )
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"iss":"https://auth.openai.com","aud":"$issuedClientId","sub":"$subject","exp":1800003600,"iat":1800000000,"nonce":"$nonce","email":"not-stored@example.com"}""".toByteArray(),
        )
        val input = "$header.$payload"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(input.toByteArray())
        }.sign()
        return "$input.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
    }

    private fun sampleCredentials(subject: String) = OAuthAuthorizationResult(
        issuedClientId = issuedClientId,
        subject = subject,
        idToken = "id-token-hint",
        accessToken = "access-secret",
        refreshToken = "refresh-secret",
        expiresAtMillis = 1_800_000_000_000L,
        grantedScopes = setOf("openid", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct"),
        hostId = hostId,
    )

    private fun queryValue(url: String, key: String): String? =
        URI(url).rawQuery.split('&').firstNotNullOfOrNull { part ->
            val pair = part.split('=', limit = 2)
            if (java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8) == key) {
                java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            } else null
        }

    private fun assertOAuthFailure(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            throw AssertionError("Expected an OAuth failure")
        } catch (error: ChatGptOAuthException) {
            assertTrue(error.message.orEmpty().isNotBlank())
            assertFalse(error.message.orEmpty().contains("access-secret"))
            assertFalse(error.message.orEmpty().contains("refresh-secret"))
        }
    }

    private fun String.formValue(key: String): String? =
        split('&').firstNotNullOfOrNull { part ->
            val pair = part.split('=', limit = 2)
            if (java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8) == key) {
                java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            } else null
        }

    private fun BigInteger.toUnsignedBytes(): ByteArray {
        val bytes = toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private inner class FakeLoopbackOAuthCallbackServer(
        private val callbackBuilder: (String, String) -> String = { redirect, state ->
            "$redirect?code=code-1&state=$state&client_id=oaiapp_letsstudy_test"
        },
    ) : LoopbackOAuthCallbackServer {
        override val redirectUri = "http://127.0.0.1:1455/auth/callback"
        var authorizationUrl: String? = null
        private var callbackUrl: String? = null

        fun onAuthorizationOpened(url: String) {
            authorizationUrl = url
            callbackUrl = callbackBuilder(redirectUri, requireNotNull(queryValue(url, "state")))
        }

        override suspend fun awaitCallback(): String = requireNotNull(callbackUrl)

        override fun close() = Unit
    }
}
