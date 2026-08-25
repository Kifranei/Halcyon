package com.ella.music.data.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
    val pronunciationWords: List<LyricWord> = emptyList(),
    val agent: String? = null,
    val agentName: String? = null,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val backgroundTranslation: String? = null,
    val backgroundStartMs: Long? = null,
    val backgroundEndMs: Long? = null,
    val isTtml: Boolean = false,
    val endMs: Long? = null,
    val isOpeningMetadata: Boolean = false
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

fun LyricLine.primaryEndMs(
    nextLine: LyricLine? = null,
    nextLineStartMs: Long? = null,
    fallbackDurationMs: Long = 4_000L
): Long {
    val resolvedNextLineStartMs = nextLineStartMs ?: nextLine?.timeMs
    if (text.isBlank() && !backgroundText.isNullOrBlank()) {
        val backgroundOnlyEnd = (backgroundEndMs
            ?: backgroundWords.maxOfOrNull { it.endMs }
            ?: endMs
            ?: resolvedNextLineStartMs
            ?: (timeMs + fallbackDurationMs))
        val cappedBackgroundOnlyEnd = if (
            resolvedNextLineStartMs != null &&
            backgroundOnlyEnd > resolvedNextLineStartMs
        ) {
            resolvedNextLineStartMs
        } else {
            backgroundOnlyEnd
        }
        return cappedBackgroundOnlyEnd
            .coerceAtLeast(timeMs + 1L)
    }

    val mainWordEndMs = words.maxOfOrNull { it.endMs }
    val backgroundWordEndMs = backgroundWords.maxOfOrNull { it.endMs }
    val backgroundTimedEndMs = listOfNotNull(backgroundEndMs, backgroundWordEndMs).maxOrNull()
    val mainEnd = when {
        mainWordEndMs != null && backgroundTimedEndMs != null -> maxOf(mainWordEndMs, backgroundTimedEndMs)
        mainWordEndMs != null -> mainWordEndMs
        backgroundTimedEndMs != null -> backgroundTimedEndMs
        else -> endMs
    }
    val cappedEnd = when {
        resolvedNextLineStartMs == null -> mainEnd
        mainEnd == null -> resolvedNextLineStartMs
        mainEnd > resolvedNextLineStartMs &&
            !preservesPrimaryOverlapWith(
                nextLine = nextLine,
                sungEndMs = mainWordEndMs ?: backgroundTimedEndMs ?: mainEnd
            ) -> resolvedNextLineStartMs
        else -> mainEnd
    }
    return (cappedEnd ?: timeMs + fallbackDurationMs).coerceAtLeast(timeMs + 1L)
}

private fun LyricLine.preservesPrimaryOverlapWith(
    nextLine: LyricLine?,
    sungEndMs: Long
): Boolean {
    val nextLineStartMs = nextLine?.timeMs ?: return false
    if (sungEndMs <= nextLineStartMs) return false
    val currentAgent = agent?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
    val nextAgent = nextLine.agent?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
    if (currentAgent == nextAgent) return false
    if (!((currentAgent == "v1" && nextAgent == "v2") ||
            (currentAgent == "v2" && nextAgent == "v1"))
    ) {
        return false
    }

    val currentText = normalizedDuetText()
    val nextText = nextLine.normalizedDuetText()
    return currentText.isNotBlank() && currentText == nextText
}

private fun LyricLine.normalizedDuetText(): String =
    buildString {
        append(text)
        if (!backgroundText.isNullOrBlank()) append(backgroundText)
    }.lowercase()
        .replace(Regex("""\s+"""), "")

fun List<LyricLine>.shiftedBy(offsetMs: Long): List<LyricLine> {
    if (offsetMs == 0L || isEmpty()) return this
    fun Long.shift() = (this + offsetMs).coerceAtLeast(0L)
    fun Long?.shiftNullable() = this?.let { (it + offsetMs).coerceAtLeast(0L) }
    fun LyricWord.shifted() = copy(startMs = startMs.shift(), endMs = endMs.shift())

    return map { line ->
        line.copy(
            timeMs = line.timeMs.shift(),
            words = line.words.map { it.shifted() },
            pronunciationWords = line.pronunciationWords.map { it.shifted() },
            backgroundWords = line.backgroundWords.map { it.shifted() },
            backgroundStartMs = line.backgroundStartMs.shiftNullable(),
            backgroundEndMs = line.backgroundEndMs.shiftNullable(),
            endMs = line.endMs.shiftNullable()
        )
    }
}
