package com.oriol.letsstudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouthAccessTest {
    @Test fun unknownOrUnsupportedAgeNeverOpensAdultWorkspace() {
        assertEquals(null, LearnerAgeGroup.fromStoredValue(null))
        assertEquals(null, LearnerAgeGroup.fromStoredValue("unknown"))
        assertFalse(LearnerAgeGroup.AGE_9_12.canUseAdultWorkspace)
        assertFalse(LearnerAgeGroup.AGE_13_17.canUseAdultWorkspace)
        assertTrue(LearnerAgeGroup.ADULT.canUseAdultWorkspace)
    }

    @Test fun youthCoursesAreCuratedForEachAgeBand() {
        for (group in listOf(LearnerAgeGroup.AGE_9_12, LearnerAgeGroup.AGE_13_17)) {
            val courses = YouthCourseCatalog.forGroup(group)
            assertTrue("Expected multiple offline courses for $group", courses.size >= 2)
            assertTrue(courses.all { it.questions.size >= 5 })
            assertTrue(courses.flatMap { it.questions }.all { q ->
                q.prompt.isNotBlank() && q.options.size == 4 && q.answerIndex in 0..3 && q.explanation.isNotBlank()
            })
        }
        assertTrue(YouthCourseCatalog.forGroup(LearnerAgeGroup.ADULT).isEmpty())
    }

    @Test fun progressCountsAnAnsweredQuestionOnlyOnce() {
        val first = YouthProgress.EMPTY.answer("fractions-1", correct = true)
        val revised = first.answer("fractions-1", correct = false)
        assertEquals(1, revised.answeredCount)
        assertEquals(0, revised.correctCount)
        assertEquals(false, revised.answers["fractions-1"])
    }

    @Test fun progressRestoresOnlyKnownValidAnswers() {
        val restored = YouthProgress.fromStoredValues(setOf("jm-1:true", "jm-2:false", "bad", "unknown:true"), setOf("jm-1", "jm-2"))
        assertEquals(mapOf("jm-1" to true, "jm-2" to false), restored.answers)
        assertEquals(setOf("jm-1:true", "jm-2:false"), restored.storedValues())
    }
}
