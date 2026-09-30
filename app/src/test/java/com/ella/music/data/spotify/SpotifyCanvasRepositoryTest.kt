package com.ella.music.data.spotify

import android.app.Application
import com.ella.music.data.model.Song
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SpotifyCanvasRepositoryTest {
    @Test fun restSearchAndProtobufFallbackWorkWithoutFollowingAnUntrustedCdnRedirect() = runBlocking {
        val requests = mutableListOf<Request>()
        var trustedDownload = true
        val uri = "spotify:track:3OHfY25tqY28d16oZczHc8"
        val video = "https://canvaz.scdn.co/clip.cnvs.mp4"
        fun field(number: Int, text: ByteArray): ByteArray = byteArrayOf(((number shl 3) or 2).toByte(), text.size.toByte()) + text
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            val body = when (request.url.host) {
                "open.spotify.com" -> "<html></html>".toByteArray()
                "api-partner.spotify.com" -> """{"data":{}}""".toByteArray()
                "api.spotify.com" -> """{"tracks":{"items":[{"uri":"$uri","name":"Fallback song","artists":[{"name":"Artist"}],"duration_ms":100000}]}}""".toByteArray()
                "spclient.wg.spotify.com" -> field(1, field(2, video.toByteArray()) + field(5, uri.toByteArray()))
                "canvaz.scdn.co" -> {
                    if (!trustedDownload) response.code(302).header("Location", "https://example.org/clip.mp4")
                    byteArrayOf(0, 0, 0, 24) + "ftypisom000000000000".toByteArray()
                }
                else -> error("Canvas must never follow an unrelated host")
            }
            response.body(body.toResponseBody()).build()
        }.build()
        val repository = SpotifyCanvasRepository(RuntimeEnvironment.getApplication(), client, { _, _ ->
            SpotifyWebSession("test-access-token", null, System.currentTimeMillis() + 600_000)
        })
        val song = Song(1, "Fallback song", "Artist", "Album", 0, 100_000, "/test.flac", "test.flac")
        assertNotNull(repository.resolve(song, "test-cookie"))
        assertEquals(true, requests.any { it.url.host == "spclient.wg.spotify.com" })
        trustedDownload = false
        assertNull(repository.resolve(song.copy(album = "Another album"), "test-cookie"))
        assertEquals(false, requests.any { it.url.host == "example.org" })
    }
    @Test fun cacheStopsRepeatedDownloadsAndDownloadRequestsCarryNoCredentials() = runBlocking {
        val requests = mutableListOf<Request>()
        var sessions = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = when {
                request.url.host == "open.spotify.com" -> """<script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.test.js"></script>""".toByteArray()
                request.url.host == "open.spotifycdn.com" -> """["canvas","query","${"a".repeat(64)}"],["searchTracks","query","${"b".repeat(64)}"]""".toByteArray()
                request.url.queryParameter("operationName") == "searchTracks" -> """{"data":{"searchV2":{"tracksV2":{"items":[{"item":{"data":{"uri":"spotify:track:3OHfY25tqY28d16oZczHc8","name":"Good Days","artists":{"items":[{"profile":{"name":"SZA"}}]},"albumOfTrack":{"name":"SOS"},"duration":{"totalMilliseconds":100000}}}}]}}}}""".toByteArray()
                request.url.host == "api-partner.spotify.com" -> """{"data":{"trackUnion":{"canvas":{"url":"https://canvaz.scdn.co/clip.cnvs.mp4","type":"VIDEO"}}}}""".toByteArray()
                request.url.host == "canvaz.scdn.co" -> byteArrayOf(0, 0, 0, 24) + "ftypisom000000000000".toByteArray()
                else -> error("Unexpected Spotify request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val repository = SpotifyCanvasRepository(RuntimeEnvironment.getApplication(), client, { _, _ ->
            sessions++
            SpotifyWebSession("test-access-token", "test-client-token", System.currentTimeMillis() + 600_000)
        })
        val song = Song(1, "Good Days", "SZA", "SOS", 0, 100_000, "/test.flac", "test.flac")
        val first = repository.resolve(song, "test-cookie")
        assertNotNull(first)
        assertEquals(first, repository.resolve(song, "test-cookie"))
        assertEquals(1, sessions)
        val downloads = requests.filter { it.url.host == "canvaz.scdn.co" }
        assertEquals(1, downloads.size)
        assertNull(downloads.single().header("Cookie"))
        assertNull(downloads.single().header("Authorization"))
        assertEquals("Bearer test-access-token", requests.first { it.url.host == "api-partner.spotify.com" }.header("Authorization"))
        assertEquals("test-client-token", requests.first { it.url.host == "api-partner.spotify.com" }.header("Client-Token"))
    }
    @Test fun noCookieMakesNoRequestsAndUnavailableSessionIsThrottled() = runBlocking {
        var sessions = 0
        val repository = SpotifyCanvasRepository(RuntimeEnvironment.getApplication(), sessionProvider = { _, _ -> sessions++; null })
        val song = Song(1, "Song", "Artist", "Album", 0, 0, "/test.flac", "test.flac")
        assertNull(repository.resolve(song, ""))
        assertEquals(0, sessions)
        assertNull(repository.resolve(song, "test-cookie"))
        assertNull(repository.resolve(song.copy(title = "Next"), "test-cookie"))
        assertEquals(1, sessions)
        assertEquals(SpotifyCanvasConnection.Failed, repository.connection.value)
    }
}
