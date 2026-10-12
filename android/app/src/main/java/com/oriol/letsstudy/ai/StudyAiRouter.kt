package com.oriol.letsstudy.ai

class StudyAiRouter(
    private val providers: Map<StudyAiProviderId, StudyAiProvider>,
    private val onLocalFallback: (StudyAiTask) -> Unit = {},
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
                ?: throw StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE, cause = error, fallbackReason = error.kind)
            try {
                val result = localProvider.generate(request)
                onLocalFallback(request.task)
                return result
            } catch (localError: ModelUnavailableException) {
                throw StudyAiException(StudyAiErrorKind.MODEL_NOT_AVAILABLE, cause = localError, fallbackReason = error.kind)
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
