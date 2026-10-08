package com.oriol.letsstudy.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeoutOrNull

interface FastQuestionGenerator {
    suspend fun generate(prompt: String): String

    /** Optional deeper explanation, requested separately so normal question rounds use no extra quota. */
    suspend fun generateLesson(prompt: String): String =
        throw UnsupportedOperationException("Concept lessons are not supported by this generator.")
}

/** Online question and optional lesson requests, through the app owner's Firebase AI Logic project. */
class FirebaseFastQuestionGenerator : FastQuestionGenerator {
    private val questionSchema = Schema.obj(
        mapOf(
            "category" to Schema.string(),
            "prompt" to Schema.string(),
            "topic" to Schema.string(),
            "options" to Schema.array(Schema.string()),
            "correctOptionIndex" to Schema.integer(),
            "explanation" to Schema.string(),
            "sourceBasis" to Schema.string(),
        ),
    )
    private val questionOutputSchema = Schema.obj(
        mapOf(
            "coveredTopicsSummary" to Schema.string(),
            "questions" to Schema.array(questionSchema),
        ),
    )
    private val lessonOutputSchema = Schema.obj(
        mapOf(
            "sections" to Schema.array(
                Schema.obj(
                    mapOf(
                        "id" to Schema.string(),
                        "title" to Schema.string(),
                        "content" to Schema.string(),
                    ),
                ),
            ),
            "keyTerms" to Schema.array(
                Schema.obj(
                    mapOf(
                        "term" to Schema.string(),
                        "definition" to Schema.string(),
                    ),
                ),
            ),
            "rememberThis" to Schema.string(),
        ),
    )
    private val model by lazy { createModel(questionOutputSchema) }
    private val lessonModel by lazy { createModel(lessonOutputSchema) }
    private val workspaceModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-lite",
            generationConfig = generationConfig { responseMimeType = "application/json" },
        )
    }

    suspend fun generateWorkspace(prompt: String): String = withTimeoutOrNull(90_000L) {
        workspaceModel.generateContent(prompt).text
    } ?: throw InvalidStudyOutputException("The study service took too long. Try again; your saved data is safe.")

    private fun createModel(schema: Schema) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            },
        )

    override suspend fun generate(prompt: String): String = withTimeoutOrNull(75_000L) {
        model.generateContent(prompt).text
    } ?: throw InvalidStudyOutputException("Fast online study took too long or returned no questions. Please try again.")

    override suspend fun generateLesson(prompt: String): String = withTimeoutOrNull(75_000L) {
        lessonModel.generateContent(prompt).text
    } ?: throw InvalidStudyOutputException("The topic lesson took too long or returned no content. Please try again.")
}
