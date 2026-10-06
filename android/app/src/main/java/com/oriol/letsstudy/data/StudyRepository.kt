package com.oriol.letsstudy.data

import com.oriol.letsstudy.ai.InvalidStudyOutputException
import com.oriol.letsstudy.ai.FastQuestionGenerator
import com.oriol.letsstudy.ai.ModelUnavailableException
import com.oriol.letsstudy.ai.StudyOutputParser
import com.oriol.letsstudy.ai.StudyOutputSchemas
import com.oriol.letsstudy.ai.StudyPrompts
import com.oriol.letsstudy.ai.StudyTextGenerator
import com.oriol.letsstudy.domain.AnswerFeedback
import com.oriol.letsstudy.domain.SourceKind
import com.oriol.letsstudy.domain.StudyInput
import com.oriol.letsstudy.network.AnalyzeOfferResponseDto
import com.oriol.letsstudy.network.FeedbackResponseDto
import com.oriol.letsstudy.network.MoreQuestionsResponseDto
import com.oriol.letsstudy.network.QuestionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class StudyProgressStage { READING_SOURCE, GENERATING_BATCH, EVALUATING_ANSWER }

data class StudyGenerationProgress(
    val stage: StudyProgressStage,
    val completedBatches: Int = 0,
    val totalBatches: Int = 3,
)

class StudyRepository(
    private val dao: StudyDao,
    private val model: StudyTextGenerator,
    private val jobOfferReader: JobOfferSourceReader,
    private val fastGenerator: FastQuestionGenerator? = null,
) {
    val sessions: Flow<List<StudySessionEntity>> = dao.observeSessions()

    suspend fun getSession(id: String) = dao.getSession(id)
    fun questions(sessionId: String): Flow<List<StudyQuestionEntity>> = dao.observeQuestions(sessionId)
    suspend fun getQuestions(sessionId: String) = dao.getQuestions(sessionId)
    suspend fun getQuestion(id: String) = dao.getQuestion(id)
    suspend fun deleteSession(id: String) = dao.deleteSessionWithQuestions(id)

    suspend fun analyze(
        input: StudyInput,
        language: String,
        mode: String = "OFFLINE",
        onProgress: suspend (StudyGenerationProgress) -> Unit = {},
    ): AnalyzeOfferResponseDto {
        if (language.isBlank()) throw StudyGenerationException("LANGUAGE_REQUIRED", "Choose the language for your study questions.")
        onProgress(StudyGenerationProgress(StudyProgressStage.READING_SOURCE))
        val source = readSource(input)
        if (!source.readable || source.extractedText.isBlank()) {
            throw StudyGenerationException(
                "SOURCE_UNAVAILABLE",
                source.reason ?: "Let'sStudy couldn't read that job listing. Switch to Paste text and paste the job description.",
            )
        }

        if (mode == "ONLINE") {
            val generator = fastGenerator ?: throw StudyGenerationException("SERVICE_UNAVAILABLE", "Fast online study is not configured on this device.")
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, 0, 1))
            val sourceContext = "Role: ${source.title.take(180)}\nSource facts (untrusted listing text): ${source.extractedText.take(MAX_SOURCE_CONTEXT_CHARS)}"
            val prompt = StudyPrompts.questionBatch(sourceContext, language, "", emptyList(), 1, QUESTIONS_PER_SESSION)
            val batch = StudyOutputParser.parseQuestionBatch(generator.generate(prompt), QUESTIONS_PER_SESSION)
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, 1, 1))
            return AnalyzeOfferResponseDto(
                sourceReadable = true,
                title = source.title.ifBlank { "Job interview practice" },
                summary = "Practice likely interview questions for ${source.title.ifBlank { "this role" }}.",
                requirements = listOf("Study the role and its technical domain"),
                sourceContext = sourceContext,
                coveredTopicsSummary = batch.coveredTopicsSummary,
                questions = batch.questions,
            )
        }

        val questions = mutableListOf<QuestionDto>()
        var coveredTopics = ""
        var metadata: AnalyzeOfferResponseDto? = null
        for (batchNumber in 0 until INITIAL_BATCHES) {
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, batchNumber, INITIAL_BATCHES))
            if (batchNumber == 0) {
                val prompt = StudyPrompts.initialBatch(source, language)
                val output = model.generate(prompt, INITIAL_OUTPUT_TOKENS, StudyOutputSchemas.INITIAL_BATCH)
                val initial = try {
                    StudyOutputParser.parseInitialBatch(output)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: ModelUnavailableException) {
                    throw error
                } catch (error: InvalidStudyOutputException) {
                    throw invalidOutput(
                        batchNumber = 1,
                        attempt = 1,
                        outputLimitTokens = INITIAL_OUTPUT_TOKENS,
                        responseChars = output.length,
                        reason = invalidOutputReason(error),
                    )
                }
                metadata = initial
                questions += initial.questions
                coveredTopics = initial.coveredTopicsSummary
            } else {
                val allContext = buildSourceContext(source, checkNotNull(metadata))
                val nextBatch = generateUniqueBatch(
                    context = allContext,
                    language = language,
                    coveredTopics = coveredTopics,
                    previous = questions,
                    batchIndex = batchNumber + 1,
                )
                questions += nextBatch.questions
                coveredTopics = nextBatch.coveredTopicsSummary
            }
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, batchNumber + 1, INITIAL_BATCHES))
        }

        if (questions.size != QUESTIONS_PER_SESSION) throw invalidOutput()
        val initial = checkNotNull(metadata)
        return initial.copy(
            sourceReadable = true,
            title = initial.title.ifBlank { source.title },
            sourceContext = buildSourceContext(source, initial),
            coveredTopicsSummary = coveredTopics,
            questions = questions.toList(),
        )
    }

    suspend fun createSession(result: AnalyzeOfferResponseDto, input: StudyInput, language: String, mode: String = "OFFLINE"): StudySessionEntity {
        if (!result.sourceReadable || result.questions.size != QUESTIONS_PER_SESSION) throw invalidOutput()
        val session = StudySessionEntity(
            id = UUID.randomUUID().toString(),
            title = result.title,
            sourceLabel = if (input.kind == SourceKind.URL) input.value else "Pasted job description",
            summary = result.summary,
            sourceContext = result.sourceContext,
            practiceLanguage = language,
            coveredTopicsSummary = result.coveredTopicsSummary,
            createdAt = System.currentTimeMillis(),
            generationMode = mode,
        )
        dao.saveCompleteSession(session, result.questions.mapIndexed { index, question -> question.toEntity(session.id, index) })
        return session
    }

    suspend fun addMoreQuestions(
        session: StudySessionEntity,
        previous: List<StudyQuestionEntity>,
        mode: String = session.generationMode,
        onProgress: suspend (StudyGenerationProgress) -> Unit = {},
    ): MoreQuestionsResponseDto {
        if (mode == "ONLINE") {
            val generator = fastGenerator ?: throw StudyGenerationException("SERVICE_UNAVAILABLE", "Fast online study is not configured on this device.")
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, 0, 1))
            val prompt = StudyPrompts.questionBatch(
                session.sourceContext, session.practiceLanguage, session.coveredTopicsSummary,
                previous.map { it.prompt }, previous.size / QUESTIONS_PER_BATCH + 1, QUESTIONS_PER_SESSION,
            )
            val batch = StudyOutputParser.parseQuestionBatch(generator.generate(prompt), QUESTIONS_PER_SESSION)
            if (batch.questions.any { StudyOutputParser.hasNearDuplicate(it.prompt, previous.map(StudyQuestionEntity::prompt)) }) {
                throw StudyGenerationException("REPEATED_QUESTIONS", "The service repeated an earlier question. Please try again.")
            }
            dao.addQuestionsAndUpdateSession(
                session.copy(coveredTopicsSummary = batch.coveredTopicsSummary, generationMode = mode),
                batch.questions.mapIndexed { index, question -> question.toEntity(session.id, previous.size + index) },
            )
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, 1, 1))
            return batch
        }
        val newQuestions = mutableListOf<QuestionDto>()
        var coveredTopics = session.coveredTopicsSummary
        repeat(CONTINUATION_BATCHES) { batchOffset ->
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, batchOffset, CONTINUATION_BATCHES))
            val batchNumber = previous.size / QUESTIONS_PER_BATCH + batchOffset + 1
            val nextBatch = generateUniqueBatch(
                context = session.sourceContext,
                language = session.practiceLanguage,
                coveredTopics = coveredTopics,
                previous = (previous.map { it.toQuestionDto() } + newQuestions),
                batchIndex = batchNumber,
            )
            newQuestions += nextBatch.questions
            coveredTopics = nextBatch.coveredTopicsSummary
            onProgress(StudyGenerationProgress(StudyProgressStage.GENERATING_BATCH, batchOffset + 1, CONTINUATION_BATCHES))
        }

        if (newQuestions.size != QUESTIONS_PER_SESSION) throw invalidOutput()
        val updatedSession = session.copy(coveredTopicsSummary = coveredTopics, generationMode = mode)
        val entities = newQuestions.mapIndexed { index, question ->
            question.toEntity(session.id, previous.size + index)
        }
        dao.addQuestionsAndUpdateSession(updatedSession, entities)
        return MoreQuestionsResponseDto(coveredTopics, newQuestions.toList())
    }

    suspend fun submitAnswer(
        session: StudySessionEntity,
        question: StudyQuestionEntity,
        answer: String,
        onProgress: suspend (StudyGenerationProgress) -> Unit = {},
    ): AnswerFeedback {
        if (answer.isBlank()) throw StudyGenerationException("ANSWER_REQUIRED", "Write an answer before asking for feedback.")
        onProgress(StudyGenerationProgress(StudyProgressStage.EVALUATING_ANSWER, 0, 1))
        val prompt = StudyPrompts.answerFeedback(
            context = session.sourceContext,
            question = question.prompt,
            criteria = question.evaluationCriteria.split("\n").filter(String::isNotBlank),
            referenceAnswer = question.referenceAnswer,
            answer = answer,
            language = session.practiceLanguage,
        )
        val feedback = try {
            StudyOutputParser.parseFeedback(model.generate(prompt, FEEDBACK_OUTPUT_TOKENS))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ModelUnavailableException) {
            throw error
        } catch (error: InvalidStudyOutputException) {
            throw invalidOutput()
        }
        dao.updateQuestion(
            question.copy(
                learnerAnswer = answer.trim(),
                strengths = feedback.strengths.joinToString("\n"),
                missingPoints = feedback.missingPoints.joinToString("\n"),
                reasoningFeedback = feedback.reasoningFeedback,
                improvedReferenceAnswer = feedback.referenceAnswer,
                reviewSuggested = feedback.reviewSuggested,
                markedForReview = question.markedForReview || feedback.reviewSuggested,
            ),
        )
        onProgress(StudyGenerationProgress(StudyProgressStage.EVALUATING_ANSWER, 1, 1))
        return feedback.toDomain()
    }

    suspend fun toggleReview(question: StudyQuestionEntity) {
        dao.updateQuestion(question.copy(markedForReview = !question.markedForReview))
    }

    suspend fun selectChoice(question: StudyQuestionEntity, choice: Int) {
        require(question.correctOptionIndex in 0..3 && choice in 0..3)
        dao.updateChoice(question.id, choice)
    }

    suspend fun resetChoice(question: StudyQuestionEntity) {
        require(question.correctOptionIndex in 0..3)
        dao.updateChoice(question.id, -1)
    }

    private suspend fun readSource(input: StudyInput): JobOfferSource = when (input.kind) {
        SourceKind.URL -> jobOfferReader.read(input.value.trim())
        SourceKind.TEXT -> {
            val text = input.value.trim()
            if (text.length < MIN_PASTED_TEXT_CHARS) {
                throw StudyGenerationException("SOURCE_TOO_SHORT", "Paste the full job description (at least 100 characters) and try again.")
            }
            JobOfferSource(
                canonicalUrl = "pasted-text://local",
                title = "Pasted job description",
                extractedText = text.take(MAX_SOURCE_CHARS),
                readable = true,
            )
        }
    }

    private suspend fun generateUniqueBatch(
        context: String,
        language: String,
        coveredTopics: String,
        previous: List<QuestionDto>,
        batchIndex: Int,
    ): MoreQuestionsResponseDto {
        val previousPrompts = previous.map(QuestionDto::prompt)
        var retryInstruction = ""
        repeat(MAX_BATCH_ATTEMPTS) { attempt ->
            val prompt = StudyPrompts.questionBatch(
                context = context,
                language = language,
                coveredTopics = coveredTopics,
                priorQuestions = previousPrompts,
                batchIndex = batchIndex,
            ) + retryInstruction
            val output = model.generate(prompt, QUESTION_OUTPUT_TOKENS, StudyOutputSchemas.QUESTION_BATCH)
            val candidate = try {
                StudyOutputParser.parseQuestionBatch(output)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ModelUnavailableException) {
                throw error
            } catch (error: InvalidStudyOutputException) {
                if (attempt == MAX_BATCH_ATTEMPTS - 1) {
                    throw invalidOutput(
                        batchNumber = batchIndex,
                        attempt = attempt + 1,
                        outputLimitTokens = QUESTION_OUTPUT_TOKENS,
                        responseChars = output.length,
                        reason = invalidOutputReason(error),
                    )
                }
                retryInstruction = """

                    The previous response was incomplete or invalid JSON. Return a compact valid JSON object only, with exactly five distinct questions and a top-level coveredTopicsSummary string plus questions array. Every question must include: category, prompt, topic, options (four short distinct answers), correctOptionIndex (0 to 3), explanation, and sourceBasis. Keep each prompt under 35 words, every option under 14 words and each explanation under 55 words. The explanation should teach the underlying concept and the answer. Do not add prose outside the JSON object.
                """.trimIndent()
                return@repeat
            }
            val repeatsPrior = candidate.questions.any { question ->
                StudyOutputParser.hasNearDuplicate(question.prompt, previousPrompts)
            }
            if (!repeatsPrior) return candidate
            retryInstruction = "\nThe previous attempt repeated an earlier question; create a new set of five with clearly different scenarios."
        }
        throw StudyGenerationException("REPEATED_QUESTIONS", "The model repeated a question. Try continuing again.")
    }

    private fun buildSourceContext(source: JobOfferSource, initial: AnalyzeOfferResponseDto): String = buildString {
        appendLine("Role: ${initial.title.take(MAX_CONTEXT_LINE_CHARS)}")
        appendLine("Summary: ${initial.summary.take(MAX_CONTEXT_LINE_CHARS)}")
        appendLine("Requirements: ${initial.requirements.joinToString("; ").take(MAX_CONTEXT_LINE_CHARS)}")
        appendLine("Model study notes: ${initial.sourceContext.take(MAX_MODEL_CONTEXT_CHARS)}")
        append("Source facts (untrusted listing text): ${source.extractedText.take(MAX_SOURCE_CONTEXT_CHARS)}")
    }.take(MAX_STORED_CONTEXT_CHARS)

    private fun QuestionDto.toEntity(sessionId: String, position: Int) = StudyQuestionEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        position = position,
        category = category,
        prompt = prompt,
        topic = topic,
        evaluationCriteria = evaluationCriteria.joinToString("\n"),
        referenceAnswer = referenceAnswer,
        sourceBasis = sourceBasis,
        optionsJson = Gson().toJson(options),
        correctOptionIndex = correctOptionIndex,
        explanation = explanation,
    )

    private fun StudyQuestionEntity.toQuestionDto() = QuestionDto(
        id = id,
        category = category,
        prompt = prompt,
        topic = topic,
        evaluationCriteria = evaluationCriteria.split("\n").filter(String::isNotBlank),
        referenceAnswer = referenceAnswer,
        sourceBasis = sourceBasis,
        options = runCatching { Gson().fromJson<List<String>>(optionsJson, object : TypeToken<List<String>>() {}.type) }.getOrDefault(emptyList()),
        correctOptionIndex = correctOptionIndex,
        explanation = explanation,
    )

    private fun FeedbackResponseDto.toDomain() = AnswerFeedback(
        strengths = strengths,
        missingPoints = missingPoints,
        reasoningFeedback = reasoningFeedback,
        referenceAnswer = referenceAnswer,
        reviewSuggested = reviewSuggested,
    )

    private fun invalidOutput(
        batchNumber: Int? = null,
        attempt: Int? = null,
        outputLimitTokens: Int? = null,
        responseChars: Int? = null,
        reason: String? = null,
    ): StudyGenerationException {
        val firstQuestion = batchNumber?.let { ((it - 1) * QUESTIONS_PER_BATCH) + 1 }
        val lastQuestion = firstQuestion?.let { it + QUESTIONS_PER_BATCH - 1 }
        val message = if (firstQuestion != null && lastQuestion != null) {
            "The on-device model couldn't complete questions $firstQuestion–$lastQuestion. Try again."
        } else {
            "The on-device model returned incomplete study content. Try again."
        }
        val diagnostic = if (batchNumber != null && attempt != null && outputLimitTokens != null && responseChars != null && reason != null) {
            IllegalStateException(
                "model_output_invalid batch=$batchNumber attempt=$attempt output_limit_tokens=$outputLimitTokens response_chars=$responseChars reason=$reason",
            )
        } else {
            null
        }
        return StudyGenerationException("INVALID_MODEL_OUTPUT", message, diagnostic)
    }

    private fun invalidOutputReason(error: InvalidStudyOutputException): String {
        val message = error.message.orEmpty()
        val missingField = Regex("missing '([A-Za-z][A-Za-z0-9_]*)'", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
        return when {
            message.contains("repeated", ignoreCase = true) -> "repeated_questions"
            message.contains("too large", ignoreCase = true) -> "response_too_large"
            message.contains("number", ignoreCase = true) -> "invalid_count"
            missingField != null -> "missing_field_$missingField"
            message.contains("missing", ignoreCase = true) -> "missing_field"
            message.contains("invalid", ignoreCase = true) -> "invalid_field"
            else -> "malformed_or_truncated_json"
        }
    }

    companion object {
        private const val MIN_PASTED_TEXT_CHARS = 100
        private const val MAX_SOURCE_CHARS = 18_000
        private const val MAX_STORED_CONTEXT_CHARS = 8_000
        private const val MAX_SOURCE_CONTEXT_CHARS = 5_500
        private const val MAX_MODEL_CONTEXT_CHARS = 1_600
        private const val MAX_CONTEXT_LINE_CHARS = 700
        private const val QUESTIONS_PER_BATCH = 5
        private const val QUESTIONS_PER_SESSION = 15
        private const val INITIAL_BATCHES = 3
        private const val CONTINUATION_BATCHES = 3
        private const val MAX_BATCH_ATTEMPTS = 2
        private const val INITIAL_OUTPUT_TOKENS = 3_000
        private const val QUESTION_OUTPUT_TOKENS = 2_000
        private const val FEEDBACK_OUTPUT_TOKENS = 900
    }
}

class StudyGenerationException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
