package com.v2ray.ang.stratos

/**
 * Pure input validation for Stratos VPN.
 *
 * Username rule: starts with "fyx" (lower case) and is 4..8 characters total,
 * letters and digits only. Password rule: 5..10 characters.
 */
object StratosValidators {

    private val USERNAME_REGEX = Regex("^fyx[a-zA-Z0-9]{1,5}$")

    fun isValidUsername(username: String): Boolean = USERNAME_REGEX.matches(username.trim())

    fun isValidPassword(password: String): Boolean = password.length in 5..10

    /** Normalizes a typed username (trims, lower-cases the fyx prefix). */
    fun normalizeUsername(username: String): String {
        val trimmed = username.trim()
        return if (trimmed.length >= 3) trimmed.substring(0, 3).lowercase() + trimmed.substring(3) else trimmed.lowercase()
    }
}
