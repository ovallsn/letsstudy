package com.oriol.letsstudy.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConceptLessonUiCopyTest {
    @Test
    fun serviceVerificationErrorsUseLearnerFriendlyLanguageAcrossSupportedLocales() {
        listOf("English", "Spanish", "Thai").forEach { language ->
            val message = ConceptLessonUiCopy.forLanguage(language).appCheckError

            assertTrue(message.isNotBlank())
            assertFalse(message.contains("again", ignoreCase = true))
            assertFalse(message.contains("later", ignoreCase = true))
            assertFalse(message.contains("Firebase", ignoreCase = true))
            assertFalse(message.contains("App Check", ignoreCase = true))
        }
    }
}
