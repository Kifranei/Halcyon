package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import com.ella.music.data.parser.hasCjk
import java.io.File

private val openingLyricTokens = linkedMapOf(
    "<Title>" to { song: Song -> song.title },
    "<Artist>" to { song: Song -> song.artist },
    "<Album>" to { song: Song -> song.album },
    "<Genre>" to { song: Song -> song.genre },
    "<Year>" to { song: Song -> song.year },
    "<Folder>" to { song: Song -> File(song.path).parentFile?.name.orEmpty() },
    "<Composer>" to { song: Song -> song.composer },
    "<Lyricist>" to { song: Song -> song.lyricist },
    "<Producer>" to { song: Song -> song.producer },
    "<Arranger>" to { song: Song -> song.arranger },
    "<歌曲名>" to { song: Song -> song.title },
    "<艺术家>" to { song: Song -> song.artist },
    "<专辑>" to { song: Song -> song.album },
    "<流派>" to { song: Song -> song.genre },
    "<年份>" to { song: Song -> song.year },
    "<文件夹>" to { song: Song -> File(song.path).parentFile?.name.orEmpty() },
    "<作曲家>" to { song: Song -> song.composer },
    "<作词家>" to { song: Song -> song.lyricist },
    "<制作人>" to { song: Song -> song.producer },
    "<编曲家>" to { song: Song -> song.arranger },
    "<藝術家>" to { song: Song -> song.artist },
    "<專輯>" to { song: Song -> song.album },
    "<流派>" to { song: Song -> song.genre },
    "<年份>" to { song: Song -> song.year },
    "<文件夾>" to { song: Song -> File(song.path).parentFile?.name.orEmpty() },
    "<作曲家>" to { song: Song -> song.composer },
    "<作詞家>" to { song: Song -> song.lyricist },
    "<製作人>" to { song: Song -> song.producer },
    "<編曲家>" to { song: Song -> song.arranger }
)

internal fun renderOpeningLyricTemplate(template: String, song: Song): String {
    var result = template.trim()
    openingLyricTokens.forEach { (token, value) -> result = result.replace(token, value(song).trim()) }
    return result
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\s*([-–—·|/])\\s*(?=$|[-–—·|/])"), "")
        .trim(' ', '-', '–', '—', '·', '|', '/')
}

private val openingChunkRegex = Regex("""\S+\s*""")

/**
 * #727: the opening line is conceptually one slow karaoke line spanning the whole blank
 * interval. As a single untimed word it rendered wrongly on every surface — the player kept
 * the first space-chunk dim and the rest statically lit, while the desktop/status-bar swept
 * every chunk at once. Splitting it into space-separated chunks (per-character for CJK) with
 * sequential, length-weighted timings makes it behave like a normal word-timed line.
 * Chunks keep their trailing whitespace because renderers join word texts with "".
 */
private fun openingLyricWords(text: String, endMs: Long): List<LyricWord> {
    val chunks = mutableListOf<String>()
    openingChunkRegex.findAll(text).forEach { match ->
        val token = match.value
        val word = token.trimEnd()
        val trailingWs = token.substring(word.length)
        if (word.hasCjk()) {
            word.forEachIndexed { index, char ->
                chunks.add(char.toString() + if (index == word.lastIndex) trailingWs else "")
            }
        } else {
            chunks.add(token)
        }
    }
    if (chunks.size <= 1) return listOf(LyricWord(text = text, startMs = 0L, endMs = endMs))
    val totalWeight = chunks.sumOf { it.length }.coerceAtLeast(1)
    var cursor = 0L
    return chunks.map { chunk ->
        val start = cursor
        cursor = start + endMs * chunk.length / totalWeight
        LyricWord(text = chunk, startMs = start, endMs = cursor.coerceAtMost(endMs))
    }
}

internal fun List<LyricLine>.withOpeningMetadataLine(
    song: Song,
    template: String,
    fallbackWhenEmpty: Boolean = false
): List<LyricLine> {
    if (template.isBlank()) return this
    val text = renderOpeningLyricTemplate(template, song)
    if (text.isBlank()) return this
    if (isEmpty()) {
        if (!fallbackWhenEmpty) return this
        val endMs = song.duration.coerceAtLeast(1L)
        return listOf(
            LyricLine(
                timeMs = 0L,
                text = text,
                words = openingLyricWords(text, endMs),
                endMs = endMs,
                isOpeningMetadata = true
            )
        )
    }
    val firstStartMs = first().timeMs
    if (firstStartMs <= 0L) return this
    val opening = LyricLine(
        timeMs = 0L,
        text = text,
        words = openingLyricWords(text, firstStartMs),
        endMs = firstStartMs,
        isOpeningMetadata = true
    )
    return listOf(opening) + this
}
