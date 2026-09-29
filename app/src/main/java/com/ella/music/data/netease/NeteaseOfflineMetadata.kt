package com.ella.music.data.netease

import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.model.Song
import com.ella.music.data.parser.EllaLyricsParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal fun encodeOfflineNeteaseKey(detail: JSONObject, song: Song): String {
    val artists = detail.optJSONArray("ar") ?: detail.optJSONArray("artists") ?: JSONArray()
    val pairs = JSONArray().apply {
        for (i in 0 until artists.length()) {
            val artist = artists.getJSONObject(i)
            put(JSONArray().put(artist.optString("name")).put(artist.optLong("id")))
        }
    }
    val payload = JSONObject().put("musicId", song.onlineId.toLong())
        .put("musicName", song.title).put("artist", pairs)
        .put("albumId", kotlin.math.abs(song.albumId)).put("album", song.album)
        .put("mvId", song.onlineMvId.toLongOrNull() ?: 0)
        .put("duration", song.duration).put("albumPic", song.coverUrl)
        .put("alias", detail.optJSONArray("alia") ?: JSONArray())
        .put("transNames", detail.optJSONArray("tns") ?: JSONArray())
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("#14ljk_!\\]&0U<'(".toByteArray(), "AES"))
    return "163 key(Don't modify):" + Base64.getEncoder().encodeToString(cipher.doFinal("music:$payload".toByteArray()))
}

internal fun offlineNeteaseTags(detail: JSONObject, song: Song, lyrics: NeteaseLyrics): AudioTagInfo {
    val key = encodeOfflineNeteaseKey(detail, song)
    val published = detail.optLong("publishTime")
    return AudioTagInfo(
        title = song.title, artist = song.artist, album = song.album,
        albumArtist = song.albumArtist.ifBlank { song.artist },
        trackNumber = detail.optInt("no").takeIf { it > 0 },
        discNumber = detail.optString("cd").toIntOrNull(),
        year = published.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year.toString() },
        composer = detail.optString("composer").takeIf { it.isNotBlank() },
        lyricist = detail.optString("lyricist").takeIf { it.isNotBlank() },
        comment = key, neteaseKey = key, lyrics = lyrics.original,
        ttmlLyrics = offlineNeteaseTtml(lyrics),
        customTags = buildMap {
            put("163KEY", listOf(key))
            put("NETEASE_METADATA", listOf(detail.toString()))
            put("NETEASE_SONG_ID", listOf(song.onlineId))
            if (song.onlineMvId.isNotBlank()) put("NETEASE_MV_ID", listOf(song.onlineMvId))
            if (lyrics.translation.isNotBlank()) put("LYRICS_TRANSLATION", listOf(lyrics.translation))
            if (lyrics.pronunciation.isNotBlank()) put("LYRICS_ROMANIZATION", listOf(lyrics.pronunciation))
            detail.optString("copyright").takeIf { it.isNotBlank() }?.let { put("COPYRIGHT", listOf(it)) }
        }
    )
}

internal fun offlineNeteaseTtml(lyrics: NeteaseLyrics): String? {
    val lines = mergeOnlineLyricCompanions(EllaLyricsParser.parse(lyrics.original).lyrics, lyrics.translation, lyrics.pronunciation)
    if (lines.isEmpty()) return null
    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun time(ms: Long) = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}s"
    return buildString {
        append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"><body><div>")
        lines.forEachIndexed { index, line ->
            val end = line.endMs ?: lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5000)
            append("<p xml:space=\"preserve\" begin=\"${time(line.timeMs)}\" end=\"${time(end)}\">")
            if (line.words.isEmpty()) append(escape(line.text)) else line.words.forEach { word ->
                append("<span begin=\"${time(word.startMs)}\" end=\"${time(word.endMs)}\">${escape(word.text)}</span>")
            }
            line.translation?.takeIf { it.isNotBlank() }?.let { append("<span ttm:role=\"x-translation\">${escape(it)}</span>") }
            line.pronunciation?.takeIf { it.isNotBlank() }?.let { append("<span ttm:role=\"x-roman\">${escape(it)}</span>") }
            append("</p>")
        }
        append("</div></body></tt>")
    }
}
