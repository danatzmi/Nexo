package com.nexo.app.domain.model

import kotlin.random.Random

/** Uppercase, letters/digits only, capped at 8 chars — mirrors iOS's `customJoinCode` sanitization in `CreateGymView`. */
fun sanitizeJoinCode(rawCode: String): String =
    rawCode.filter { it.isLetterOrDigit() }.uppercase().take(8)

/**
 * A 6-character-ish join code derived from the gym name plus a random
 * 2-digit suffix (e.g. "Iron Temple" -> "IRON99") — mirrors iOS's
 * `generatedCodePreview` fallback when no custom code is supplied.
 */
fun generateJoinCode(gymName: String, random: Random = Random.Default): String {
    val letters = gymName.filter { it.isLetter() }.uppercase()
    val prefix = letters.take(4).ifEmpty { "GYM" }
    val suffix = random.nextInt(10, 100)
    return "$prefix$suffix"
}
