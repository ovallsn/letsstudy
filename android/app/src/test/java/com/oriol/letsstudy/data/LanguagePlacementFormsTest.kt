package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePlacementFormsTest {
    @Test
    fun alternateFormHasTwentyNewQuestionsForEverySupportedLanguage() {
        LanguagePlacementTest.languages.forEach { language ->
            val original = LanguagePlacementTest.questions(language, 0)
            val alternate = LanguagePlacementTest.questions(language, 1)

            assertEquals(20, alternate.size)
            assertEquals(listOf(4, 4, 4, 4, 4), LanguagePlacementTest.bands.map { band -> alternate.count { it.band == band } })
            assertNotEquals(original.map { it.prompt }.toSet(), alternate.map { it.prompt }.toSet())
            assertEquals(20, alternate.map { it.prompt }.distinct().size)
            alternate.forEach { question ->
                assertEquals(4, question.options.size)
                assertTrue(question.answerIndex in question.options.indices)
                assertTrue(question.explanation.isNotBlank())
            }
            val perfectResult = LanguagePlacementTest.result(language, alternate.map { it.answerIndex }, bankVersion = 1)
            assertEquals(20, perfectResult.correct)
        }
    }
}
