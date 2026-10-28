package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class OriginOsMediaIslandLyric(val songKey: String, val text: String)

/** Shares the existing lyric timeline with the island without patching session metadata. */
internal object OriginOsMediaIslandLyrics {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private val latest = MutableStateFlow<OriginOsMediaIslandLyric?>(null)
    val current = latest.asStateFlow()

    fun update(song: Song?, line: LyricLine?) {
        val text = line?.takeUnless { it.isOpeningMetadata }?.let {
            sequenceOf(it.text, it.backgroundText)
                .mapNotNull { text -> text?.replace(whitespace, " ")?.trim() }
                .firstOrNull { text -> text.isNotEmpty() }
        }
        latest.value = if (song != null && text != null) {
            OriginOsMediaIslandLyric(song.playbackStackKey(), text)
        } else null
    }

    fun clear() { latest.value = null }
}
