package com.oriol.letsstudy.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatGptStudyAiProvider(
    private val credentials: ChatGptCredentialStore,
    private val oauthClient: ChatGptOAuthClient,
    private val client: OpenAiResponsesClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : StudyAiProvider {
    override val id = StudyAiProviderId.CHATGPT

    private val refreshMutex = Mutex()
    private val modelMutex = Mutex()

    @Volatile
    private var cachedAccount: AccountIdentity? = null

    @Volatile
    private var cachedModel: ChatGptModel? = null

    val selectedModelDisplayName: String?
        get() = cachedModel?.displayName

    fun invalidateModelCatalog() {
        cachedAccount = null
        cachedModel = null
    }

    override suspend fun generate(request: StudyAiRequest): String {
        return try {
            val current = currentCredentials()
            val model = selectedModelFor(current)
            client.generate(current, request, model.slug)
        } catch (error: StudyAiException) {
            if (error.kind == StudyAiErrorKind.AUTH_REQUIRED) credentials.clearCredentials()
            throw error
        }
    }

    private suspend fun currentCredentials(): OAuthAuthorizationResult {
        val current = credentials.read() ?: throw StudyAiException(StudyAiErrorKind.AUTH_REQUIRED)
        if (current.expiresAtMillis > nowMillis() + REFRESH_SKEW_MILLIS) return current

        return refreshMutex.withLock {
            val latest = credentials.read() ?: throw StudyAiException(StudyAiErrorKind.AUTH_REQUIRED)
            if (latest.expiresAtMillis > nowMillis() + REFRESH_SKEW_MILLIS) return@withLock latest
            try {
                oauthClient.refresh(latest).also(credentials::write)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ChatGptOAuthException) {
                if (error.failure == ChatGptOAuthFailure.AUTH_REQUIRED) credentials.clearCredentials()
                throw StudyAiException(
                    kind = error.toStudyAiErrorKind(),
                    requestId = error.requestId,
                    cause = error,
                )
            }
        }
    }

    private suspend fun selectedModelFor(current: OAuthAuthorizationResult): ChatGptModel = modelMutex.withLock {
        val account = AccountIdentity(current.subject, current.issuedClientId)
        if (cachedAccount == account && cachedModel != null) return@withLock requireNotNull(cachedModel)

        val selected = client.availableModels(current).firstOrNull()
            ?: throw StudyAiException(StudyAiErrorKind.INVALID_RESPONSE)
        cachedAccount = account
        cachedModel = selected
        selected
    }

    private fun ChatGptOAuthException.toStudyAiErrorKind(): StudyAiErrorKind = when (failure) {
        ChatGptOAuthFailure.AUTH_REQUIRED -> StudyAiErrorKind.AUTH_REQUIRED
        ChatGptOAuthFailure.NOT_ELIGIBLE -> StudyAiErrorKind.NOT_ELIGIBLE
        ChatGptOAuthFailure.NETWORK -> StudyAiErrorKind.NETWORK
        ChatGptOAuthFailure.TEMPORARY_SERVICE -> StudyAiErrorKind.TEMPORARY_SERVICE
        ChatGptOAuthFailure.CONSENT_CANCELLED -> StudyAiErrorKind.AUTH_REQUIRED
        ChatGptOAuthFailure.INVALID_CALLBACK,
        ChatGptOAuthFailure.REGISTRATION_FAILED,
        ChatGptOAuthFailure.INVALID_RESPONSE,
        -> StudyAiErrorKind.INVALID_RESPONSE
    }

    private data class AccountIdentity(
        val subject: String,
        val issuedClientId: String,
    )

    companion object {
        private const val REFRESH_SKEW_MILLIS = 30_000L
    }
}
