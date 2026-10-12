package com.oriol.letsstudy.ui

import com.oriol.letsstudy.data.StudyActivityEntity
import com.oriol.letsstudy.data.StudyQuestionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StudyStreakPresentationTest {
    @Test fun expiredStreakDisplaysZeroDays() {
        assertEquals("0-day streak", studyStreakLabel(0))
    }

    @Test fun progressDisplaysZeroDayStudyStreak() {
        assertEquals("0-day study streak", studyStreakLabel(0, includeStudy = true))
    }

    @Test fun activeStreakDisplaysItsDayCount() {
        assertEquals("1-day streak", studyStreakLabel(1))
        assertEquals("8-day streak", studyStreakLabel(8))
    }

    @Test fun shortUnansweredSessionDoesNotKeepTheStreakAlive() {
        val yesterday = LocalDate.now().minusDays(1)

        assertEquals(0, studyStreak(emptyList(), listOf(activityOn(yesterday, 129))))
    }

    @Test fun fiveMinutesOfFocusedStudyKeepsTheStreakAlive() {
        val yesterday = LocalDate.now().minusDays(1)

        assertEquals(1, studyStreak(emptyList(), listOf(activityOn(yesterday, 300))))
    }

    @Test fun answeringAQuestionKeepsTheStreakAlive() {
        val yesterday = LocalDate.now().minusDays(1)

        assertEquals(1, studyStreak(listOf(questionAnsweredOn(yesterday)), emptyList()))
    }

    @Test fun streakExpiresAfterAnUnstudiedDay() {
        val twoDaysAgo = LocalDate.now().minusDays(2)

        assertEquals(0, studyStreak(emptyList(), listOf(activityOn(twoDaysAgo, 300))))
    }

    private fun activityOn(day: LocalDate, durationSeconds: Long) = StudyActivityEntity(
        id = day.toString(),
        sessionId = "session",
        startedAt = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        durationSeconds = durationSeconds,
    )

    private fun questionAnsweredOn(day: LocalDate) = StudyQuestionEntity(
        id = day.toString(),
        sessionId = "session",
        position = 0,
        category = "test",
        prompt = "Question",
        topic = "Topic",
        evaluationCriteria = "Criteria",
        referenceAnswer = "Answer",
        sourceBasis = "Source",
        answeredAt = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )
}
