package com.oriol.letsstudy.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeoutOrNull

interface FastQuestionGenerator {
    suspend fun generate(prompt: String): String
}

/** One request per round, through the project's free-tier Firebase AI Logic account. */
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
    private val outputSchema = Schema.obj(
        mapOf(
            "coveredTopicsSummary" to Schema.string(),
            "questions" to Schema.array(questionSchema),
        ),
    )
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = outputSchema
            },
        )
    }

    override suspend fun generate(prompt: String): String = withTimeoutOrNull(75_000L) {
        model.generateContent(prompt).text
    } ?: throw InvalidStudyOutputException("Fast online study took too long or returned no questions. Please try again.")
}
