package com.ella.music.data

/**
 * Makes a user-facing title safe for Android, Windows and SAF export targets without replacing
 * meaningful punctuation with anonymous underscores.
 */
fun String.sanitizeExportFileName(
    fallback: String = "Halcyon",
    maxLength: Int = 120
): String {
    val sanitized = buildString(length) {
        this@sanitizeExportFileName.forEach { character ->
            append(
                when (character) {
                    '/' -> '／'
                    '\\' -> '＼'
                    ':' -> '：'
                    '*' -> '＊'
                    '?' -> '？'
                    '|' -> '｜'
                    '<' -> '〈'
                    '>' -> '〉'
                    '"' -> '\''
                    else -> if (character.code < 0x20) ' ' else character
                }
            )
        }
    }.trim().trimEnd('.', ' ')
    return sanitized.ifBlank { fallback }.take(maxLength).trimEnd('.', ' ')
}
