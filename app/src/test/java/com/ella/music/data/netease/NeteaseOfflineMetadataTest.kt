package com.ella.music.data.netease

import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.parser.EllaLyricsParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NeteaseOfflineMetadataTest {
    private val detail = JSONObject("""{"id":12345,"name":"A & B","mv":123,"ar":[{"id":7,"name":"Artist"}],"al":{"id":9,"name":"Album"},"dt":200000,"no":4,"cd":"2","publishTime":1609459200000,"alia":["Alias"]}""")

    @Test fun encryptedKeyRoundTripsSongArtistAlbumAndMv() {
        val song = parseNeteaseSong(detail)!!
        val key = encodeOfflineNeteaseKey(detail, song)
        assertTrue(key.startsWith("163 key(Don't modify):"))
        val decoded = decodeNeteaseKey(key)!!
        assertEquals("12345", decoded.musicId)
        assertEquals("123", decoded.mvId)
        assertEquals("9", decoded.albumId)
        assertEquals("7", decoded.artists.single().id)
        assertEquals("A & B", decoded.musicName)
    }

    @Test fun embeddedTtmlRetainsWordTimingTranslationAndRomanization() {
        val lyrics = NeteaseLyrics("[1000,1000](1000,500,0)A&(1500,500,0)B", "[00:01.00]翻译", "[00:01.00]roman")
        val tags = offlineNeteaseTags(detail, parseNeteaseSong(detail)!!, lyrics)
        assertEquals(4, tags.trackNumber)
        assertEquals("2021", tags.year)
        assertEquals(lyrics.original, tags.lyrics)
        assertTrue(tags.customTags.getValue("NETEASE_METADATA").single().contains("publishTime"))
        val parsed = EllaLyricsParser.parse(tags.ttmlLyrics!!).lyrics.single()
        assertEquals("A&B", parsed.text)
        assertEquals(1000L, parsed.words.first().startMs)
        assertEquals(2000L, parsed.words.last().endMs)
        assertEquals("翻译", parsed.translation)
        assertEquals("roman", parsed.pronunciation)
    }

    @Test fun formatComesFromBytesNotRequestedQualityOrUrlSuffix() {
        assertEquals("flac", detectNeteaseAudioFormat("fLaC1234".toByteArray()).first)
        assertEquals("m4a", detectNeteaseAudioFormat(byteArrayOf(0,0,0,24) + "ftypM4A ".toByteArray()).first)
        assertEquals("mp3", detectNeteaseAudioFormat("ID3test".toByteArray()).first)
        assertThrows(java.io.IOException::class.java) { detectNeteaseAudioFormat("<html>failure".toByteArray()) }
    }
}
