package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageProgressionTest {
    @Test
    fun activeLanguagePathLevelTakesPrecedenceOverTheLatestAssessment() {
        assertEquals("B2", LanguageProgression.baselineLevel(activePathLevel = "B2", latestProgressLevel = "A2"))
    }

    @Test
    fun activePathLevelIsIgnoredWhenCheckingAnotherLanguage() {
        assertEquals(
            "A2",
            LanguageProgression.baselineLevel(
                activePathLevel = "B2",
                latestProgressLevel = "A2",
                activePathLanguage = "English",
                selectedLanguage = "Spanish",
            ),
        )
    }

    @Test
    fun latestAssessmentIsUsedWhenThereIsNoActivePathLevel() {
        assertEquals("A2", LanguageProgression.baselineLevel(activePathLevel = null, latestProgressLevel = "A2"))
    }
    @Test
    fun firstAssessmentSetsTheStartingLevel() {
        val result = LanguageProgression.advance(currentLevel = null, assessedLevel = "A2")

        assertEquals("A2", result.level)
        assertFalse(result.advanced)
    }

    @Test
    fun assessmentCanAdvanceOnlyOneBandAtATime() {
        val result = LanguageProgression.advance(currentLevel = "A2", assessedLevel = "C1")

        assertEquals("B1", result.level)
        assertTrue(result.advanced)
    }

    @Test
    fun aLowerSingleResultDoesNotLowerTheStudyLevel() {
        val result = LanguageProgression.advance(currentLevel = "B1", assessedLevel = "A2")

        assertEquals("B1", result.level)
        assertFalse(result.advanced)
    }

    @Test
    fun repeatedAssessmentAtTheSameLevelKeepsTheLevel() {
        val result = LanguageProgression.advance(currentLevel = "B1", assessedLevel = "B1")

        assertEquals("B1", result.level)
        assertFalse(result.advanced)
    }
}
