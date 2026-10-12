package com.oriol.letsstudy.ui

import com.oriol.letsstudy.ai.StudyAiErrorKind

internal enum class ChatGptRecoveryAction {
    MANAGE_USAGE,
    OPEN_SETTINGS,
    CONTINUE_ON_DEVICE,
    DOWNLOAD_ON_DEVICE,
}

internal fun chatGptRecoveryActions(
    usageLimitReached: Boolean,
    localFallbackEnabled: Boolean,
    localModelAvailable: Boolean,
): List<ChatGptRecoveryAction> {
    if (!usageLimitReached) return emptyList()
    return buildList {
        add(ChatGptRecoveryAction.MANAGE_USAGE)
        if (!localFallbackEnabled) add(ChatGptRecoveryAction.OPEN_SETTINGS)
        else if (localModelAvailable) add(ChatGptRecoveryAction.CONTINUE_ON_DEVICE)
        else add(ChatGptRecoveryAction.DOWNLOAD_ON_DEVICE)
    }
}

internal fun shouldShowChatGptDisclosure(mode: String, disclosureAccepted: Boolean): Boolean =
    mode.equals("CHATGPT", ignoreCase = true) && !disclosureAccepted

internal fun generationModeOptions(chatGptConnected: Boolean): List<String> = buildList {
    add("Quick online")
    if (chatGptConnected) add("ChatGPT")
    add("On this phone")
}

internal fun generationModeValue(label: String): String = when (label) {
    "ChatGPT" -> "CHATGPT"
    "On this phone" -> "OFFLINE"
    else -> "ONLINE"
}

internal fun generationModeLabel(mode: String): String = when (mode.uppercase()) {
    "CHATGPT" -> "ChatGPT"
    "OFFLINE", "ON_DEVICE" -> "On this phone"
    else -> "Quick online"
}

internal fun usageRecoveryActionsFor(error: StudyAiErrorKind?): List<ChatGptRecoveryAction> =
    if (error == StudyAiErrorKind.USAGE_LIMIT) {
        chatGptRecoveryActions(usageLimitReached = true, localFallbackEnabled = false, localModelAvailable = false)
    } else {
        emptyList()
    }
