package com.oriol.letsstudy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class ChatGptCredentialStoreTest {
    @Test
    fun refreshReplacementIsAtomicAndSecretsNeverAppearInPlaintext() {
        val preferences = InMemoryCredentialPreferences()
        val store = EncryptedChatGptCredentialStore(preferences, InMemoryCredentialKeyProvider())
        val hostId = store.getOrCreateHostId()
        val first = credentials(hostId, accessToken = "first-access-token", refreshToken = "first-refresh-token")
        val rotated = credentials(hostId, accessToken = "rotated-access-token", refreshToken = "rotated-refresh-token")

        store.write(first)
        val firstBlob = preferences.getString(CREDENTIAL_BLOB_KEY)
        assertNotNull(firstBlob)
        assertFalse(preferences.dump().contains("first-access-token"))
        assertFalse(preferences.dump().contains("first-refresh-token"))
        assertFalse(preferences.dump().contains(first.idToken))
        assertFalse(preferences.dump().contains(first.subject))
        assertFalse(preferences.dump().contains(first.issuedClientId))

        preferences.failNextCommit = true
        runCatching { store.write(rotated) }
        assertEquals(first, store.read())

        store.write(rotated)
        val rotatedBlob = preferences.getString(CREDENTIAL_BLOB_KEY)
        assertNotEquals(firstBlob, rotatedBlob)
        assertEquals(rotated, store.read())
        assertFalse(preferences.dump().contains("first-access-token"))
        assertFalse(preferences.dump().contains("rotated-access-token"))
        assertFalse(preferences.dump().contains("rotated-refresh-token"))
        assertFalse(preferences.dump().contains(rotated.idToken))
        assertFalse(preferences.dump().contains(rotated.subject))
        assertFalse(preferences.dump().contains(rotated.issuedClientId))
    }

    @Test
    fun clearRemovesStoredCredentialsButRetainsHostId() {
        val preferences = InMemoryCredentialPreferences()
        val store = EncryptedChatGptCredentialStore(preferences, InMemoryCredentialKeyProvider())
        val hostId = store.getOrCreateHostId()
        store.write(credentials(hostId))

        store.clearCredentials()

        assertNull(store.read())
        assertEquals(hostId, store.getOrCreateHostId())
        assertTrue(preferences.getString(CREDENTIAL_BLOB_KEY) == null)
    }

    private fun credentials(
        hostId: String,
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
    ) = OAuthAuthorizationResult(
        issuedClientId = "issued-client-id",
        subject = "account-subject",
        idToken = "id-token",
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = 1_800_000_000_000,
        grantedScopes = setOf("openid", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct"),
        hostId = hostId,
    )

    private fun assertNotNull(value: Any?) {
        assertTrue("Expected a stored encrypted credential", value != null)
    }
}

private class InMemoryCredentialPreferences : CredentialPreferences {
    private val values = linkedMapOf<String, String>()
    var failNextCommit = false

    @Synchronized
    override fun getString(key: String): String? = values[key]

    @Synchronized
    override fun commit(puts: Map<String, String>, removals: Set<String>): Boolean {
        if (failNextCommit) {
            failNextCommit = false
            return false
        }
        val next = values.toMutableMap()
        removals.forEach(next::remove)
        next.putAll(puts)
        values.clear()
        values.putAll(next)
        return true
    }

    @Synchronized
    fun dump(): String = values.toString()
}

private class InMemoryCredentialKeyProvider : CredentialKeyProvider {
    private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    override fun getOrCreate(): SecretKey = key

    override fun delete() = Unit
}
