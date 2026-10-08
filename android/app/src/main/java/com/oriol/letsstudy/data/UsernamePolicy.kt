package com.oriol.letsstudy.data

import java.util.Locale

object UsernamePolicy {
    private val pattern = Regex("[A-Za-z0-9]+(_[A-Za-z0-9]+)*")
    private val reservedNames = setOf("admin", "administrator", "help", "letsstudy", "moderator", "official", "root", "support", "system")
    private val restrictedTerms = setOf(
        "asshole", "bastard", "bitch", "cabron", "connard", "cunt", "fuck", "fag", "gilipollas", "joder",
        "maricon", "merde", "mierda", "motherfucker", "nigga", "nigger", "pendejo", "porn", "puta", "putain",
        "puto", "salope", "shit", "slut", "whore",
    )
    private val lookalikeMap = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't')

    fun normalize(value: String): String {
        val username = value.trim().lowercase(Locale.ROOT)
        require(username.length in 3..20 && pattern.matches(username)) { "Use 3–20 letters, numbers or underscores for your username." }
        val compact = username.replace("_", "").map { lookalikeMap[it] ?: it }.joinToString("")
        val tokens = username.split('_').map { token ->
            token.trimEnd { it.isDigit() }.map { lookalikeMap[it] ?: it }.joinToString("")
        }
        val reservedRoot = username.substringBefore('_').trimEnd { it.isDigit() }
        require(username !in reservedNames && reservedRoot !in reservedNames && restrictedTerms.none { term -> tokens.any { it == term } || compact.contains(term) }) {
            "This username can't be used. Choose another one."
        }
        return username
    }

    fun isAllowed(value: String): Boolean = runCatching { normalize(value) }.isSuccess
}
