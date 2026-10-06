package com.oriol.letsstudy.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceLabel: String,
    val summary: String,
    val sourceContext: String,
    val practiceLanguage: String,
    val coveredTopicsSummary: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'OFFLINE'") val generationMode: String = "OFFLINE",
)

@Entity(
    tableName = "study_questions",
    indices = [Index("sessionId"), Index(value = ["sessionId", "position"], unique = true)],
)
data class StudyQuestionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val category: String,
    val prompt: String,
    val topic: String,
    val evaluationCriteria: String,
    val referenceAnswer: String,
    val sourceBasis: String,
    val learnerAnswer: String = "",
    val strengths: String = "",
    val missingPoints: String = "",
    val reasoningFeedback: String = "",
    val improvedReferenceAnswer: String = "",
    val reviewSuggested: Boolean = false,
    val markedForReview: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'") val optionsJson: String = "[]",
    @ColumnInfo(defaultValue = "-1") val correctOptionIndex: Int = -1,
    @ColumnInfo(defaultValue = "''") val explanation: String = "",
    @ColumnInfo(defaultValue = "-1") val selectedOptionIndex: Int = -1,
)
