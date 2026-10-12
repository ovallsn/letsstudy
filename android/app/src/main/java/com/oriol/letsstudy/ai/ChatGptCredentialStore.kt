package com.oriol.letsstudy.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ChatGptCredentialStore {
    fun read(): OAuthAuthorizationResult?
    fun write(value: OAuthAuthorizationResult)
    fun getOrCreateHostId(): String
    fun clearCredentials()
}

class AndroidChatGptCredentialStore(context: Context) : ChatGptCredentialStore {
    private val delegate = EncryptedChatGptCredentialStore(
        preferences = SharedPreferencesCredentialPreferences(
            context.applicationContext.getSharedPreferences(CREDENTIAL_PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        keyProvider = AndroidKeystoreCredentialKeyProvider(),
    )

    override fun read(): OAuthAuthorizationResult? = delegate.read()

    override fun write(value: OAuthAuthorizationResult) = delegate.write(value)

    override fun getOrCreateHostId(): String = delegate.getOrCreateHostId()

    override fun clearCredentials() = delegate.clearCredentials()
}

internal interface CredentialPreferences {
    fun getString(key: String): String?
    fun commit(puts: Map<String, String> = emptyMap(), removals: Set<String> = emptySet()): Boolean
}

internal interface CredentialKeyProvider {
    fun getOrCreate(): SecretKey
    fun delete()
}

internal class EncryptedChatGptCredentialStore(
    private val preferences: CredentialPreferences,
    private val keyProvider: CredentialKeyProvider,
    private val secureRandom: SecureRandom = SecureRandom(),
) : ChatGptCredentialStore {
    private val lock = Any()

    override fun read(): OAuthAuthorizationResult? = synchronized(lock) {
        val hostId = preferences.getString(CREDENTIAL_HOST_ID_KEY)
        val encryptedRecord = preferences.getString(CREDENTIAL_BLOB_KEY)
        if (hostId == null || encryptedRecord == null) return@synchronized null
        if (!isValidHostId(hostId)) {
            removeCredentialRecord()
            return@synchronized null
        }

        runCatching {
            val json = JsonParser.parseString(decrypt(encryptedRecord)).asJsonObject
            OAuthAuthorizationResult(
                issuedClientId = json.requiredString("issuedClientId"),
                subject = json.requiredString("subject"),
                idToken = json.requiredString("idToken"),
                accessToken = json.requiredString("accessToken"),
                refreshToken = json.requiredString("refreshToken"),
                expiresAtMillis = json.requiredLong("expiresAtMillis"),
                grantedScopes = json.requiredStringSet("grantedScopes"),
                hostId = hostId,
            ).also(::validateCredential)
        }.getOrElse {
            removeCredentialRecord()
            runCatching { keyProvider.delete() }
            null
        }
    }

    override fun write(value: OAuthAuthorizationResult) = synchronized(lock) {
        validateCredential(value)
        val existingHostId = preferences.getString(CREDENTIAL_HOST_ID_KEY)
        require(existingHostId == null || !isValidHostId(existingHostId) || existingHostId == value.hostId) {
            "The installation identity does not match the credential."
        }
        val puts = buildMap {
            put(CREDENTIAL_BLOB_KEY, encrypt(value.toJson().toString()))
            put(CREDENTIAL_HOST_ID_KEY, value.hostId)
        }
        check(preferences.commit(puts = puts)) { "Could not save the ChatGPT connection." }
    }

    override fun getOrCreateHostId(): String = synchronized(lock) {
        val existing = preferences.getString(CREDENTIAL_HOST_ID_KEY)
        if (existing != null && isValidHostId(existing)) return@synchronized existing

        val created = "urn:uuid:${UUID.randomUUID()}"
        val removals = if (existing == null) emptySet() else setOf(CREDENTIAL_BLOB_KEY)
        check(preferences.commit(puts = mapOf(CREDENTIAL_HOST_ID_KEY to created), removals = removals)) {
            "Could not save the installation identity."
        }
        created
    }

    override fun clearCredentials() = synchronized(lock) {
        check(preferences.commit(removals = setOf(CREDENTIAL_BLOB_KEY))) {
            "Could not remove the ChatGPT connection."
        }
        runCatching { keyProvider.delete() }
        Unit
    }

    private fun removeCredentialRecord() {
        preferences.commit(removals = setOf(CREDENTIAL_BLOB_KEY))
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate(), secureRandom)
        cipher.updateAAD(AUTHENTICATED_DATA)
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val encoded = byteArrayOf(FORMAT_VERSION) + cipher.iv + encrypted
        return ENCRYPTED_PREFIX + Base64.getEncoder().withoutPadding().encodeToString(encoded)
    }

    private fun decrypt(value: String): String {
        require(value.startsWith(ENCRYPTED_PREFIX))
        val encoded = Base64.getDecoder().decode(value.removePrefix(ENCRYPTED_PREFIX))
        require(encoded.size > 1 + GCM_NONCE_BYTES + GCM_TAG_BYTES / 8)
        require(encoded[0] == FORMAT_VERSION)
        val nonce = encoded.copyOfRange(1, 1 + GCM_NONCE_BYTES)
        val ciphertext = encoded.copyOfRange(1 + GCM_NONCE_BYTES, encoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(GCM_TAG_BYTES, nonce))
        cipher.updateAAD(AUTHENTICATED_DATA)
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun validateCredential(value: OAuthAuthorizationResult) {
        require(isValidHostId(value.hostId))
        require(value.issuedClientId.isNotBlank())
        require(value.subject.isNotBlank())
        require(value.idToken.isNotBlank())
        require(value.accessToken.isNotBlank())
        require(value.refreshToken.isNotBlank())
        require(value.expiresAtMillis > 0)
        require(PLAN_USE_SCOPE in value.grantedScopes)
    }

    private fun isValidHostId(value: String): Boolean {
        val rawUuid = value.removePrefix("urn:uuid:")
        if (rawUuid == value) return false
        val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull() ?: return false
        return uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == rawUuid
    }

    private fun OAuthAuthorizationResult.toJson() = JsonObject().apply {
        addProperty("issuedClientId", issuedClientId)
        addProperty("subject", subject)
        addProperty("idToken", idToken)
        addProperty("accessToken", accessToken)
        addProperty("refreshToken", refreshToken)
        addProperty("expiresAtMillis", expiresAtMillis)
        add("grantedScopes", JsonArray().also { array -> grantedScopes.sorted().forEach(array::add) })
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf(String::isNotBlank)
            ?: error("Credential record is incomplete.")

    private fun JsonObject.requiredLong(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            ?.takeIf { it > 0 }
            ?: error("Credential record is incomplete.")

    private fun JsonObject.requiredStringSet(name: String): Set<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.map { element ->
                element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?.takeIf(String::isNotBlank)
                    ?: error("Credential record is incomplete.")
            }
            ?.toSet()
            ?.takeIf { PLAN_USE_SCOPE in it }
            ?: error("Credential record is incomplete.")

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BYTES = 128
        private const val ENCRYPTED_PREFIX = "v1:"
        private const val PLAN_USE_SCOPE = "chatgpt.tokens.use.direct"
        private val AUTHENTICATED_DATA = "com.oriol.letsstudy.chatgpt.credentials.v1".toByteArray(StandardCharsets.UTF_8)
    }
}

internal class SharedPreferencesCredentialPreferences(
    private val preferences: SharedPreferences,
) : CredentialPreferences {
    override fun getString(key: String): String? = preferences.getString(key, null)

    @Suppress("UseKtx")
    override fun commit(puts: Map<String, String>, removals: Set<String>): Boolean {
        val editor = preferences.edit()
        removals.forEach(editor::remove)
        puts.forEach(editor::putString)
        return editor.commit()
    }
}

private class AndroidKeystoreCredentialKeyProvider : CredentialKeyProvider {
    override fun getOrCreate(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun delete() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "letsstudy.chatgpt.credentials.v1"
    }
}

internal const val CREDENTIAL_PREFERENCES_NAME = "chatgpt_credentials_secure"
internal const val CREDENTIAL_BLOB_KEY = "credential_blob"
internal const val CREDENTIAL_HOST_ID_KEY = "installation_host_id"
