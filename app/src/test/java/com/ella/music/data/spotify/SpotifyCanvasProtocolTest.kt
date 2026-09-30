package com.ella.music.data.spotify

import com.ella.music.data.model.Song
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyCanvasProtocolTest {
    private val uri = "spotify:track:3OHfY25tqY28d16oZczHc8"
    private val video = "https://canvaz.scdn.co/upload/video/clip.cnvs.mp4"
    @Test fun onlyTheRequestedTrackCanSupplyAProtobufCanvas() {
        fun record(track: String) = field(1, field(2, video.toByteArray()) + field(5, track.toByteArray()))
        assertEquals(video, decodeSpotifyCanvasResponse(record(uri), uri))
        assertNull(decodeSpotifyCanvasResponse(record("spotify:track:0000000000000000000000"), uri))
        assertNull(decodeSpotifyCanvasResponse(byteArrayOf(10, 127, 1), uri))
        val request = encodeSpotifyCanvasRequest(uri)
        assertEquals(uri, request.copyOfRange(4, request.size).toString(Charsets.UTF_8))
    }
    @Test fun canvasUrlsAreHttpsVideoFilesOnSpotifyCdnOnly() {
        assertEquals(video, safeSpotifyCanvasVideoUrl(video))
        listOf("http://canvaz.scdn.co/a.mp4", "https://scdn.co.evil.org/a.mp4", "https://example.org/a.mp4", "https://user:password@canvaz.scdn.co/a.mp4", "https://canvaz.scdn.co/a.jpg").forEach { assertNull(safeSpotifyCanvasVideoUrl(it)) }
        assertEquals(video, parseSpotifyCanvasQuery("""{"data":{"trackUnion":{"canvas":{"url":"$video","type":"VIDEO"}}}}"""))
    }
    @Test fun trackMatchingRejectsWrongArtistAndDifferentVersion() {
        val song = Song(1, "Good Days", "SZA", "SOS", 0, 100_000, "/test.flac", "test.flac")
        val raw = """{"tracks":{"items":[{"uri":"spotify:track:0000000000000000000000","name":"Good Days","artists":[{"name":"Other"}],"duration_ms":100000},{"uri":"$uri","name":"Good Days","artists":[{"name":"SZA"}],"album":{"name":"SOS"},"duration_ms":100000}]}}"""
        assertEquals(uri, selectSpotifyCanvasTrack(raw, song, false)?.uri)
        assertNull(selectSpotifyCanvasTrack(raw, song.copy(duration = 300_000), false))
        assertNull(selectSpotifyCanvasTrack(raw, song.copy(title = "Good Days (Live)"), false))
    }
    @Test fun cookiesAndSessionResponsesCannotBecomeAnonymousSessions() {
        assertEquals("sample", normalizeSpotifySessionCookie("sp_dc=sample; sp_t=other"))
        assertEquals("sample", normalizeSpotifySessionCookie("sample"))
        assertNull(normalizeSpotifySessionCookie("sample\r\nHeader: value"))
        assertNull(parseSpotifyWebSession("""{"accessToken":"sample","isAnonymous":true,"accessTokenExpirationTimestampMs":10000}""", 1000))
        assertNull(parseSpotifyWebSession("""{"accessToken":"sample","isAnonymous":false,"accessTokenExpirationTimestampMs":500}""", 1000))
    }
    @Test fun liveQueryLookupPrioritizesMainAndSearchScripts() {
        val root = "https://open.spotifycdn.com/cdn/build/web-player/"
        val html = """<script src="${root}1015.123.js"></script><script src="${root}xpui-routes-search.123.js"></script><script src="${root}web-player.123.js"></script>"""
        assertEquals(listOf("${root}web-player.123.js", "${root}xpui-routes-search.123.js", "${root}1015.123.js"), spotifyWebPlayerScriptUrls(html))
        val hash = "a".repeat(64)
        assertEquals(mapOf("canvas" to hash), spotifyPersistedQueryHashes("""["canvas","query","$hash"]"""))
    }
    private fun field(number: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write((number shl 3) or 2); write(value.size); write(value)
    }.toByteArray()
}
