package com.oriol.letsstudy.ui

import java.io.IOException

object StudyGenerationCopy {
    fun onlineFailure(error: Exception): String {
        val chain = generateSequence(error as Throwable?) { it.cause }
            .joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            .lowercase()

        return when {
            generateSequence(error as Throwable?) { it.cause }.any { it is IOException } ->
                "We couldn't connect. Check your internet and try again. Your saved studies are safe."
            listOf("quota", "resource_exhausted", "resource exhausted", "high demand", "rate limit", "429").any(chain::contains) ->
                "Question generation is busy right now. Try again in a little while. Your saved studies are safe."
            listOf("app check", "appcheck", "app_check", "invalid token").any(chain::contains) ->
                "We couldn't prepare new study content. Your saved studies are safe."
            else -> "We couldn't finish this study. Try again in a moment. Your saved studies are safe."
        }
    }

    fun forGenerationError(code: String, fallback: String): String = when (code) {
        "SOURCE_UNAVAILABLE" -> fallback
        "SOURCE_TOO_SHORT" -> fallback
        "LANGUAGE_REQUIRED" -> fallback
        "ANSWER_REQUIRED" -> fallback
        "QUESTION_UNAVAILABLE" -> fallback
        "REPEATED_QUESTIONS" -> "That round was too close to an earlier one. Try again for a fresh set."
        "INVALID_MODEL_OUTPUT" -> "We couldn't finish the full set this time. Your saved studies are safe; try again."
        "SERVICE_UNAVAILABLE" -> "We couldn't prepare new study content. Your saved studies are safe."
        else -> fallback
    }
}
