package com.oriol.letsstudy.data

data class LanguageProgressionResult(
    val level: String,
    val advanced: Boolean,
)

object LanguageProgression {
    private val levels = listOf("Pre-A1", "A1", "A2", "B1", "B2", "C1", "C2")

    fun baselineLevel(
        activePathLevel: String?,
        latestProgressLevel: String?,
        activePathLanguage: String? = null,
        selectedLanguage: String? = null,
    ): String? {
        val languageMatches = activePathLanguage == null || activePathLanguage.equals(selectedLanguage, ignoreCase = true)
        val matchingPathLevel = activePathLevel.takeIf { languageMatches }
        return matchingPathLevel?.takeIf { it in levels } ?: latestProgressLevel?.takeIf { it in levels }
    }

    fun advance(currentLevel: String?, assessedLevel: String): LanguageProgressionResult {
        val assessedIndex = levels.indexOf(assessedLevel).takeIf { it >= 0 } ?: return LanguageProgressionResult(assessedLevel, false)
        val currentIndex = currentLevel?.let(levels::indexOf) ?: -1
        if (currentIndex < 0) return LanguageProgressionResult(levels[assessedIndex], false)
        if (assessedIndex <= currentIndex) return LanguageProgressionResult(levels[currentIndex], false)
        return LanguageProgressionResult(levels[(currentIndex + 1).coerceAtMost(levels.lastIndex)], true)
    }
}
