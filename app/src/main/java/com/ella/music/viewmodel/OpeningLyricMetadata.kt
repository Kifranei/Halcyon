package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
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
    "<Arranger>" to { song: Song -> song.arranger },
    "<歌曲名>" to { song: Song -> song.title },
    "<艺术家>" to { song: Song -> song.artist },
    "<专辑>" to { song: Song -> song.album },
    "<流派>" to { song: Song -> song.genre },
    "<年份>" to { song: Song -> song.year },
    "<文件夹>" to { song: Song -> File(song.path).parentFile?.name.orEmpty() },
    "<作曲家>" to { song: Song -> song.composer },
    "<作词家>" to { song: Song -> song.lyricist },
    "<编曲家>" to { song: Song -> song.arranger },
    "<藝術家>" to { song: Song -> song.artist },
    "<專輯>" to { song: Song -> song.album },
    "<流派>" to { song: Song -> song.genre },
    "<年份>" to { song: Song -> song.year },
    "<文件夾>" to { song: Song -> File(song.path).parentFile?.name.orEmpty() },
    "<作曲家>" to { song: Song -> song.composer },
    "<作詞家>" to { song: Song -> song.lyricist },
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
                words = listOf(LyricWord(text = text, startMs = 0L, endMs = endMs)),
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
        words = listOf(LyricWord(text = text, startMs = 0L, endMs = firstStartMs)),
        endMs = firstStartMs,
        isOpeningMetadata = true
    )
    return listOf(opening) + this
}
