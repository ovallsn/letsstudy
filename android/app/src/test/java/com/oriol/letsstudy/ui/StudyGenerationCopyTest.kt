package com.oriol.letsstudy.ui

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGenerationCopyTest {
    @Test
    fun quotaErrorsOfferAPlainRetryWithoutProviderJargon() {
        val message = StudyGenerationCopy.onlineFailure(IllegalStateException("Quota exceeded for model"))

        assertTrue(message.contains("try again", ignoreCase = true))
        assertTrue(message.contains("saved studies are safe"))
        assertFalse(message.contains("Quota"))
        assertFalse(message.contains("Gemini"))
    }

    @Test
    fun appVerificationErrorsDoNotExposeDeveloperConsoleInstructions() {
        val message = StudyGenerationCopy.onlineFailure(IllegalStateException("Firebase App Check token is invalid"))

        assertTrue(message.contains("saved studies are safe"))
        assertFalse(message.contains("try again", ignoreCase = true))
        assertFalse(message.contains("Firebase"))
        assertFalse(message.contains("App Check"))
    }

    @Test
    fun appVerificationErrorCodesDoNotSuggestWaitingOrRetrying() {
        listOf("AppCheck", "APP_CHECK").forEach { code ->
            val message = StudyGenerationCopy.onlineFailure(IllegalStateException(code))

            assertTrue(message.contains("saved studies are safe"))
            assertFalse(message.contains("try again", ignoreCase = true))
        }
    }

    @Test
    fun networkErrorsKeepTheNextStepClear() {
        val message = StudyGenerationCopy.onlineFailure(IOException("network unavailable"))

        assertTrue(message.contains("internet"))
        assertTrue(message.contains("try again", ignoreCase = true))
    }

    @Test
    fun incompleteOutputKeepsTheLearnerOrientedMessage() {
        val message = StudyGenerationCopy.forGenerationError("INVALID_MODEL_OUTPUT", "Model response was invalid")

        assertTrue(message.contains("full set"))
        assertFalse(message.contains("model"))
    }
}
