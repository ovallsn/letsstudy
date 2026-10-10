package com.oriol.letsstudy.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGptCredentialStoreAndroidTest {
    @Test
    fun keystoreCredentialRoundTripUsesFreshGcmNonce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(CREDENTIAL_PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
        val store = AndroidChatGptCredentialStore(context)
        store.clearCredentials()
        val hostId = store.getOrCreateHostId()
        val value = OAuthAuthorizationResult(
            issuedClientId = "test-issued-client",
            subject = "test-account-subject",
            idToken = "test-id-token",
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresAtMillis = 1_800_000_000_000,
            grantedScopes = setOf("openid", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct"),
            hostId = hostId,
        )

        try {
            store.write(value)
            val firstEncryptedValue = preferences.getString(CREDENTIAL_BLOB_KEY, null)
            assertNotNull(firstEncryptedValue)
            assertEquals(value, store.read())

            store.write(value)
            val secondEncryptedValue = preferences.getString(CREDENTIAL_BLOB_KEY, null)
            assertNotNull(secondEncryptedValue)
            assertNotEquals(firstEncryptedValue, secondEncryptedValue)
            assertEquals(value, store.read())
        } finally {
            store.clearCredentials()
        }
    }
}
