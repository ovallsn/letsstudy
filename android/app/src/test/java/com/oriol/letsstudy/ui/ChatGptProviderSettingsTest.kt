package com.oriol.letsstudy.ui

import com.oriol.letsstudy.ai.StudyAiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptProviderSettingsTest {
    @Test
    fun showsConnectedAndDisconnectedChatGptStates() {
        assertTrue(generationModeOptions(chatGptConnected = true).contains("ChatGPT"))
        assertFalse(generationModeOptions(chatGptConnected = false).contains("ChatGPT"))
        assertEquals("CHATGPT", generationModeValue("ChatGPT"))
        assertEquals("ONLINE", generationModeValue("Quick online"))
        assertEquals("OFFLINE", generationModeValue("On this phone"))
    }

    @Test
    fun requiresSeparateChatGptDisclosureBeforeFirstSend() {
        assertTrue(shouldShowChatGptDisclosure("CHATGPT", disclosureAccepted = false))
        assertFalse(shouldShowChatGptDisclosure("CHATGPT", disclosureAccepted = true))
        assertFalse(shouldShowChatGptDisclosure("ONLINE", disclosureAccepted = false))
    }

    @Test
    fun usageLimitOffersManageUsageAndTheOptedInLocalFallback() {
        assertEquals(
            listOf(ChatGptRecoveryAction.MANAGE_USAGE, ChatGptRecoveryAction.CONTINUE_ON_DEVICE),
            chatGptRecoveryActions(usageLimitReached = true, localFallbackEnabled = true, localModelAvailable = true),
        )
        assertEquals(
            listOf(ChatGptRecoveryAction.MANAGE_USAGE, ChatGptRecoveryAction.OPEN_SETTINGS),
            chatGptRecoveryActions(usageLimitReached = true, localFallbackEnabled = false, localModelAvailable = true),
        )
    }

    @Test
    fun missingLocalModelOffersDownloadAndPreservesFallbackChoice() {
        assertEquals(
            listOf(ChatGptRecoveryAction.MANAGE_USAGE, ChatGptRecoveryAction.DOWNLOAD_ON_DEVICE),
            chatGptRecoveryActions(usageLimitReached = true, localFallbackEnabled = true, localModelAvailable = false),
        )
    }

    @Test
    fun temporaryUnavailableDoesNotOfferQuotaCopy() {
        assertTrue(usageRecoveryActionsFor(StudyAiErrorKind.TEMPORARY_SERVICE).isEmpty())
        assertTrue(usageRecoveryActionsFor(StudyAiErrorKind.NETWORK).isEmpty())
        assertTrue(usageRecoveryActionsFor(null).isEmpty())
    }
}
