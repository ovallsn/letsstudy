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
    @ColumnInfo(defaultValue = "'JOB'") val studyKind: String = "JOB",
    @ColumnInfo(defaultValue = "''") val goal: String = "",
    @ColumnInfo(defaultValue = "'Beginner'") val level: String = "Beginner",
    @ColumnInfo(defaultValue = "'Balanced'") val intensity: String = "Balanced",
    @ColumnInfo(defaultValue = "''") val target: String = "",
    @ColumnInfo(defaultValue = "'[]'") val modulesJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val lastOpenedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "study_questions",
    indices = [Index("sessionId"), Index(value = ["sessionId", "position"])],
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
    @ColumnInfo(defaultValue = "''") val conceptLessonJson: String = "",
    @ColumnInfo(defaultValue = "'MULTIPLE_CHOICE'") val format: String = "MULTIPLE_CHOICE",
    @ColumnInfo(defaultValue = "''") val moduleId: String = "",
    @ColumnInfo(defaultValue = "'[]'") val alternativesJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val answeredAt: Long = 0,
    @ColumnInfo(defaultValue = "-1") val score: Int = -1,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val reviewIntervalDays: Int = 0,
    @ColumnInfo(defaultValue = "0") val reviewStreak: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextReviewAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastReviewedAt: Long = 0,
)

@Entity(tableName = "tutor_messages", indices = [Index("sessionId")])
data class TutorMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val questionId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "study_activity", indices = [Index("sessionId")])
data class StudyActivityEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startedAt: Long,
    val durationSeconds: Long,
)

@Entity(tableName = "study_deletions")
data class StudyDeletionEntity(
    @PrimaryKey val sessionId: String,
    val deletedAt: Long,
)
