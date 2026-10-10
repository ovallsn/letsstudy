package com.oriol.letsstudy.ai

class StudyAiRouter(
    private val providers: Map<StudyAiProviderId, StudyAiProvider>,
) {
    suspend fun generate(
        request: StudyAiRequest,
        primary: StudyAiProviderId,
        localFallbackOnChatGptLimit: Boolean,
    ): String {
        val provider = providers[primary] ?: throw missingProviderError(primary)
        try {
            return provider.generate(request)
        } catch (error: ModelUnavailableException) {
            throw StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE, cause = error)
        } catch (error: StudyAiException) {
            if (primary != StudyAiProviderId.CHATGPT ||
                error.kind != StudyAiErrorKind.USAGE_LIMIT ||
                !localFallbackOnChatGptLimit
            ) {
                throw error
            }

            val localProvider = providers[StudyAiProviderId.ON_DEVICE]
                ?: throw StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE, cause = error)
            try {
                return localProvider.generate(request)
            } catch (localError: ModelUnavailableException) {
                throw StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE, cause = localError)
            }
        }
    }

    private fun missingProviderError(provider: StudyAiProviderId): StudyAiException =
        when (provider) {
            StudyAiProviderId.CHATGPT -> StudyAiException(StudyAiErrorKind.AUTH_REQUIRED)
            StudyAiProviderId.ON_DEVICE -> StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE)
            StudyAiProviderId.GEMINI -> StudyAiException(StudyAiErrorKind.TEMPORARY_SERVICE)
        }
}
