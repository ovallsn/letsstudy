package com.oriol.letsstudy.network

data class QuestionDto(
    val id: String,
    val category: String,
    val prompt: String,
    val topic: String,
    val evaluationCriteria: List<String>,
    val referenceAnswer: String,
    val sourceBasis: String,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = -1,
    val explanation: String = "",
)

data class AnalyzeOfferResponseDto(
    val sourceReadable: Boolean,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    val sourceContext: String,
    val coveredTopicsSummary: String,
    val questions: List<QuestionDto>,
)

data class MoreQuestionsResponseDto(val coveredTopicsSummary: String, val questions: List<QuestionDto>)

data class FeedbackResponseDto(
    val strengths: List<String>,
    val missingPoints: List<String>,
    val reasoningFeedback: String,
    val referenceAnswer: String,
    val reviewSuggested: Boolean,
)
