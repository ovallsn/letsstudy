package com.oriol.letsstudy.ai

enum class StudyAiProviderId {
    GEMINI,
    CHATGPT,
    ON_DEVICE,
}

enum class StudyAiTask {
    JOB_QUESTIONS,
    STUDY_PATH,
    PRACTICE_ROUND,
    ANSWER_FEEDBACK,
    CONCEPT_LESSON,
    TUTOR,
}

data class StudyAiRequest(
    val prompt: String,
    val task: StudyAiTask,
    val maxOutputTokens: Int,
    val responseFormatSchema: String? = null,
)

interface StudyAiProvider {
    val id: StudyAiProviderId
    suspend fun generate(request: StudyAiRequest): String
}

enum class StudyAiErrorKind {
    USAGE_LIMIT,
    USAGE_UNAVAILABLE,
    AUTH_REQUIRED,
    NOT_ELIGIBLE,
    UNSUPPORTED_CAPABILITY,
    TEMPORARY_SERVICE,
    NETWORK,
    INVALID_RESPONSE,
    MODEL_NOT_AVAILABLE,
}

class StudyAiException(
    val kind: StudyAiErrorKind,
    val requestId: String? = null,
    cause: Throwable? = null,
    val statusCode: Int? = null,
) : Exception("Study AI request failed: ${kind.name}", cause)

class FirebaseStudyAiProvider(
    private val generator: FastQuestionGenerator,
) : StudyAiProvider {
    override val id = StudyAiProviderId.GEMINI

    override suspend fun generate(request: StudyAiRequest): String = when (request.task) {
        StudyAiTask.JOB_QUESTIONS -> generator.generate(request.prompt)
        StudyAiTask.CONCEPT_LESSON -> generator.generateLesson(request.prompt)
        StudyAiTask.STUDY_PATH,
        StudyAiTask.PRACTICE_ROUND,
        StudyAiTask.ANSWER_FEEDBACK,
        StudyAiTask.TUTOR,
        -> generator.generateWorkspace(request.prompt)
    }
}

class OnDeviceStudyAiProvider(
    private val generator: StudyTextGenerator,
) : StudyAiProvider {
    override val id = StudyAiProviderId.ON_DEVICE

    override suspend fun generate(request: StudyAiRequest): String = generator.generate(
        request.prompt,
        request.maxOutputTokens,
        request.responseFormatSchema,
    )
}
