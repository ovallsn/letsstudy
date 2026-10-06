package com.oriol.letsstudy.domain

data class Question(
    val id: String,
    val category: String,
    val prompt: String,
    val topic: String,
    val evaluationCriteria: List<String>,
    val referenceAnswer: String,
    val sourceBasis: String,
)

data class StudySession(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val summary: String,
    val sourceContext: String,
    val practiceLanguage: String,
    val coveredTopicsSummary: String,
    val createdAt: Long,
)

data class AnswerFeedback(
    val strengths: List<String>,
    val missingPoints: List<String>,
    val reasoningFeedback: String,
    val referenceAnswer: String,
    val reviewSuggested: Boolean,
)

enum class SourceKind { URL, TEXT }

data class StudyInput(val kind: SourceKind, val value: String)
