package com.oriol.letsstudy.data

import com.oriol.letsstudy.domain.StudyInput

sealed interface PendingStudyGeneration {
    data class AnalyzeJob(val input: StudyInput, val language: String, val mode: String) : PendingStudyGeneration
    data class CreatePath(val setup: StudySetup) : PendingStudyGeneration
    data class AddRound(
        val sessionId: String,
        val format: PracticeFormat,
        val moduleId: String?,
        val count: Int,
        val mode: String,
    ) : PendingStudyGeneration
    data class GenerateMoreJobQuestions(val sessionId: String, val mode: String) : PendingStudyGeneration
    data class EvaluateAnswer(val questionId: String, val answer: String, val selfRating: Int?, val workspaceAnswer: Boolean = true) : PendingStudyGeneration
    data class GenerateLesson(val questionId: String) : PendingStudyGeneration
    data class TutorMessage(val sessionId: String, val questionId: String?, val text: String) : PendingStudyGeneration
}
