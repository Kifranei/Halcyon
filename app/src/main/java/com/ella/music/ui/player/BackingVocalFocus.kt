package com.ella.music.ui.player

import com.ella.music.data.model.LyricLine

internal data class BackingVocalSpan(val lineIndex: Int, val startMs: Long, val endMs: Long)

internal fun backingVocalSpans(lyrics: List<LyricLine>): List<BackingVocalSpan> =
    lyrics.mapIndexedNotNull { index, line ->
        if (line.backgroundText.isNullOrBlank()) return@mapIndexedNotNull null
        val start = line.backgroundStartMs ?: line.backgroundWords.minOfOrNull { it.startMs } ?: line.timeMs
        val end = listOfNotNull(line.backgroundEndMs, line.backgroundWords.maxOfOrNull { it.endMs }).maxOrNull()
            ?: line.endMs ?: return@mapIndexedNotNull null
        BackingVocalSpan(index, start, end.coerceAtLeast(start + 1L))
    }

/** Keep an ongoing backing vocal in the focus region while later lead vocals start. */
internal fun activeBackingVocalIndex(spans: List<BackingVocalSpan>, leadIndex: Int, positionMs: Long): Int? =
    spans.firstOrNull { it.lineIndex <= leadIndex && positionMs in it.startMs until it.endMs }?.lineIndex
