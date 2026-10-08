package com.oriol.letsstudy.data

import java.util.concurrent.TimeUnit

object ReviewSchedule {
    fun afterAnswer(question: StudyQuestionEntity, correct: Boolean, now: Long = System.currentTimeMillis()): StudyQuestionEntity {
        val streak = if (correct) question.reviewStreak + 1 else 0
        val intervalDays = when {
            !correct -> 1
            question.reviewStreak <= 0 -> 1
            question.reviewStreak == 1 -> 3
            else -> (question.reviewIntervalDays.coerceAtLeast(3) * 2).coerceAtMost(30)
        }
        return question.copy(
            reviewIntervalDays = intervalDays,
            reviewStreak = streak,
            nextReviewAt = now + TimeUnit.DAYS.toMillis(intervalDays.toLong()),
            lastReviewedAt = now,
        )
    }
}

fun StudyQuestionEntity.isDueForReview(now: Long = System.currentTimeMillis()): Boolean =
    nextReviewAt > 0 && nextReviewAt <= now
