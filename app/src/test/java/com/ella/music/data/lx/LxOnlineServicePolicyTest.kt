package com.ella.music.data.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.LxSourceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.json.JSONObject
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LxOnlineServicePolicyTest {
    private fun source() = LxSourceConfig(id = "test", name = "Test", url = "", script = "selected-script")

    private fun qishuiPage(id: String) = """<script>_ROUTER_DATA = {"loaderData":{"track_page":{
        "track_id":"$id","audioWithLyricsOption":{"coverURL":"https://example.test/cover.jpg",
        "trackInfo":{"id":"$id","name":"Fixture track","album":{"name":"Album"},
        "artists":[{"name":"Artist A"},{"name":"Artist B"}],"duration":207273,
        "preview":{"duration":30000}}}}}};</script>"""

    private fun response(request: Request, code: Int = 200, body: String = "", location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
            .body(body.toResponseBody()).apply { if (location != null) header("Location", location) }.build()

    private fun pageClient(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { chain -> handler(chain.request()) }.build()

    // Expected values were generated with LX v1.9.1's original zzcSign implementation.
    @Test fun qqSearchSignaturesMatchTheUpstreamUtf8Protocol() {
        assertEquals("zzcbcc5d42jqovunrdqtdqdvawc5nlhv0elfidcd929a6",
            createQqSearchSignature("""{"query":"Fool For You","page":2}"""))
        assertEquals("zzc30cadc4y885wtzxxvpsagui5zzsr4m1pyda964c0a",
            createQqSearchSignature("""{"query":"谁明浪子心","page":1}"""))
    }

    @Test fun qqDesktopSearchUsesSignedPagingAndPreservesCurrentSongMetadata() = runBlocking {
        var requests = 0
        val client = pageClient { request ->
            requests++
            assertEquals("u.y.qq.com", request.url.host)
            assertEquals("/cgi-bin/musics.fcg", request.url.encodedPath)
            assertEquals("POST", request.method)
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val body = buffer.readUtf8()
            assertEquals(createQqSearchSignature(body), request.url.queryParameter("sign"))
            val search = JSONObject(body).getJSONObject("music.search.SearchCgiService")
            assertEquals("DoSearchForQQMusicDesktop", search.getString("method"))
            val params = search.getJSONObject("param")
            assertEquals("Test track", params.getString("query"))
            assertEquals(2, params.getInt("page_num"))
            assertEquals(30, params.getInt("num_per_page"))
            assertTrue(Regex("[A-F0-9]{32}[0-9]{5}").matches(params.getString("searchid")))
            response(request, body = """{"code":0,"music.search.SearchCgiService":{"code":0,"data":{
                "body":{"song":{"list":[{"id":123,"mid":"song-mid","title":"Display title","name":"Raw name",
                    "interval":180,"singer":[{"mid":"artist-mid","name":"Artist"}],"album":{"name":"Album","mid":"album-mid"},
                    "file":{"media_mid":"media-mid","size_128mp3":100,"size_320mp3":200,"size_flac":300,"size_hires":400}}]}}}}}""")
        }
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), client) { _, _ -> null }
        val track = service.search(" Test track ", source(), page = 2, platform = LxSearchPlatform.QQ).single()
        assertEquals(1, requests)
        assertEquals("Display title", track.song.title)
        assertEquals("song-mid", track.songmid)
        assertEquals("Artist", track.song.artist)
        assertEquals("Album", track.song.album)
        assertEquals(180000L, track.song.duration)
        assertEquals(listOf("128k", "320k", "flac", "flac24bit"), track.qualities.map { it.type })
        assertEquals("flac24bit", track.quality)
        assertEquals(mapOf("songId" to "123", "albumMid" to "album-mid", "strMediaMid" to "media-mid"), track.sourceMetadata)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R500x500M000album-mid.jpg", track.coverUrl)
        assertEquals("", track.song.path)
    }

    @Test fun qqSearchOnlyUsesTheLegacyMetadataEndpointWhenDesktopSearchFails() = runBlocking {
        val hosts = mutableListOf<String>()
        val client = pageClient { request ->
            hosts += request.url.host
            if (request.url.host == "u.y.qq.com") response(request, 503)
            else {
                assertEquals("GET", request.method)
                assertEquals("3", request.url.queryParameter("p"))
                assertEquals("Track & Artist", request.url.queryParameter("w"))
                response(request, body = """{"code":0,"data":{"song":{"list":[{
                    "songmid":"legacy-mid","songname":"Legacy title","singer":[{"name":"Singer","mid":"singer-mid"}],
                    "file":{"size_128":100,"size_320":200}}]}}}""")
            }
        }
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), client) { _, _ -> null }
        val track = service.search("Track & Artist", source(), page = 3, platform = LxSearchPlatform.QQ).single()
        assertEquals(listOf("u.y.qq.com", "c.y.qq.com"), hosts)
        assertEquals("legacy-mid", track.songmid)
        assertEquals("Legacy title", track.song.title)
        assertEquals("320k", track.quality)
        assertEquals("https://y.gtimg.cn/music/photo_new/T001R500x500M000singer-mid.jpg", track.coverUrl)
        assertEquals("", track.song.path)
    }

    @Test fun qqValidEmptySearchDoesNotTriggerASecondMetadataRequest() = runBlocking {
        var requests = 0
        val client = pageClient { request ->
            requests++
            response(request, body = """{"code":0,"req":{"code":0,"data":{"body":{"song":{"list":[]}}}}}""")
        }
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), client) { _, _ -> null }
        assertTrue(service.search("No matching song", source(), platform = LxSearchPlatform.QQ).isEmpty())
        assertEquals(1, requests)
    }

    @Test fun qqMetadataSearchErrorsAreReportedAndCancellationNeverRetries() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var requests = 0
        val failing = LxOnlineService(context, pageClient { request ->
            requests++
            response(request, body = """{"code":0,"req":{"code":1},"data":{}}""")
        }) { _, _ -> null }
        val failure = runCatching { failing.search("Track", source(), platform = LxSearchPlatform.QQ) }.exceptionOrNull()
        assertEquals(context.getString(R.string.lx_online_search_failed), failure?.message)
        assertEquals(2, requests)
        requests = 0
        val cancelled = LxOnlineService(context, pageClient { requests++; throw CancellationException("Search cancelled") }) { _, _ -> null }
        val cancellation = runCatching { cancelled.search("Track", source(), platform = LxSearchPlatform.QQ) }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertEquals(1, requests)
    }

    @Test fun qishuiShortShareLinksResolveTheSongMetadataAndKeepTheFullId() = runBlocking {
        val id = "6925192814062766082"
        val visited = mutableListOf<String>()
        val client = pageClient { request ->
            visited += request.url.host
            if (request.url.host == "qishui.douyin.com") response(request, 302,
                location = "https://music.douyin.com/qishui/share/track?track_id=$id")
            else response(request, body = qishuiPage(id))
        }
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), client) { _, _ -> null }
        val track = service.search("《Fool For You》@汽水音乐 https://qishui.douyin.com/s/iXqGvVDu/",
            source(), platform = LxSearchPlatform.Qishui).single()
        assertEquals(listOf("qishui.douyin.com", "music.douyin.com"), visited)
        assertEquals(id, track.songmid)
        assertEquals("Fixture track", track.song.title)
        assertEquals("Artist A、Artist B", track.song.artist)
        assertEquals("Album", track.song.album)
        assertEquals(207273L, track.song.duration)
        assertEquals("https://example.test/cover.jpg", track.song.coverUrl)
    }

    @Test fun qishuiRedirectsCannotLeaveThePlatformDowngradeHttpsOrLoopForever() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        for (target in listOf("http://music.douyin.com/qishui/share/track?track_id=123",
            "https://example.test/qishui/share/track?track_id=123",
            "https://qishui.douyin.com/s/loop/")) {
            var requests = 0
            val client = pageClient { request -> requests++; response(request, 302, location = target) }
            val service = LxOnlineService(context, client) { _, _ -> null }
            val failure = runCatching { service.search("https://qishui.douyin.com/s/test/", source(),
                platform = LxSearchPlatform.Qishui) }.exceptionOrNull()
            assertEquals(context.getString(R.string.lx_qishui_link_failed), failure?.message)
            assertTrue(requests in 1..5)
        }
    }

    @Test fun qishuiPageCannotAttachAnotherSongsMetadataAndIdLookupSurvivesMissingMetadata() = runBlocking {
        assertEquals(null, parseQishuiTrackPage(qishuiPage("123"), "456"))
        assertEquals(null, parseQishuiTrackPage("<html>not song data</html>", "456"))
        val client = pageClient { throw IOException("Metadata temporarily unavailable") }
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), client) { _, _ -> null }
        assertEquals("456", service.search("456", source(), platform = LxSearchPlatform.Qishui).single().songmid)
    }

    @Test fun qishuiTrackLinksAndShareTextPreserveTheFullIdentifier() {
        val id = "7211848339212486658"
        assertEquals(id, parseQishuiTrackId(" $id "))
        assertEquals(id, parseQishuiTrackId("https://music.douyin.com/qishui/share/track?track_id=$id&auto_play_bgm=1"))
        assertEquals(id, parseQishuiTrackId("https://www.douyin.com/qishui/song/$id"))
        assertEquals(id, parseQishuiTrackId("分享一首歌 https://music.douyin.com/qishui/share/track?track_id=$id 。"))
    }

    @Test fun qishuiDoesNotTreatNamesOtherPlatformsOrPlaylistsAsTrackIds() {
        for (input in listOf("谁明浪子心", "0", "-1", "1.234e18", "123456789012345678901",
            "https://music.163.com/song?id=123", "https://example.test/?track_id=123",
            "https://music.douyin.com.example.test/qishui/share/track?track_id=123",
            "https://music.douyin.com/qishui/share/playlist?track_id=123&playlist_id=456",
            "https://music.douyin.com@evil.test/qishui/share/track?track_id=123",
            "http://music.douyin.com/qishui/share/track?track_id=123",
            "https://qishui.douyin.com/short-link")) {
            assertEquals(input, null, parseQishuiTrackId(input))
        }
    }

    @Test fun qishuiIdLookupRoutesPlaybackToTheImportedQsSourceWithoutPagination() = runBlocking {
        val id = "7211848339212486658"
        val url = "https://example.test/audio?key=test%2Bkey"
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), pageClient { request -> response(request, body = qishuiPage(id)) }) { track, script ->
            assertEquals("qs", track.source)
            assertEquals(id, track.songmid)
            assertEquals("selected-script", script)
            url
        }
        val config = source()
        val result = service.search(id, config, platform = LxSearchPlatform.Qishui).single()
        assertEquals("qs", result.song.onlineSource)
        assertEquals(id, result.song.onlineId)
        assertEquals(url, service.resolvePlayableSong(result, config.script).path)
        assertTrue(service.search(id, config, page = 2, platform = LxSearchPlatform.Qishui).isEmpty())
    }

    @Test fun qishuiPlatformsRecognizeBothDeclaredKeysWithoutInventingCapabilities() {
        val sdOnly = setOf("wy", "sd")
        assertEquals(listOf(LxSearchPlatform.Netease, LxSearchPlatform.Qishui),
            LxSearchPlatform.entries.filter { it.declaredSourceKey(sdOnly) != null })
        assertEquals("sd", LxSearchPlatform.Qishui.declaredSourceKey(sdOnly))
        assertEquals("qs", LxSearchPlatform.Qishui.declaredSourceKey(setOf("qs", "sd")))
        assertEquals(null, LxSearchPlatform.Qishui.declaredSourceKey(setOf("wy")))
    }

    @Test fun qishuiSdLookupPreservesTheDeclaredSourceForPlaybackAndSavedSongMetadata() = runBlocking {
        val id = "6925192814062766082"
        val url = "https://example.test/audio.mp3"
        val service = LxOnlineService(RuntimeEnvironment.getApplication(), pageClient { request ->
            response(request, body = qishuiPage(id))
        }) { track, script ->
            assertEquals("sd", track.source)
            assertEquals("sd", track.song.onlineSource)
            assertEquals(id, track.songmid)
            assertEquals("selected-script", script)
            url
        }
        val track = service.search(id, source(), platform = LxSearchPlatform.Qishui,
            declaredSources = setOf("sd")).single()
        val playable = service.resolvePlayableSong(track, source().script)
        assertEquals(url, playable.path)
        assertEquals("sd", playable.onlineSource)
        assertEquals(id, playable.onlineId)
    }

    @Test fun restoredQishuiSongsUseOnlyAnActuallyDeclaredSourceAndAction() {
        val sdOnly = JSONObject("""{"sd":{"actions":["musicUrl"]}}""")
        assertEquals("sd", LxUserApiRuntime.selectSourceKey(sdOnly, "qs", "musicUrl"))
        assertEquals("sd", LxUserApiRuntime.selectSourceKey(sdOnly, "sd", "musicUrl"))
        assertEquals("qs", LxUserApiRuntime.selectSourceKey(sdOnly, "qs", "lyric"))
        assertEquals("wy", LxUserApiRuntime.selectSourceKey(sdOnly, "wy", "musicUrl"))
        assertEquals("qs", LxUserApiRuntime.selectSourceKey(JSONObject(), "qs", "musicUrl"))
        val both = JSONObject("""{"qs":{"actions":["musicUrl","lyric"]},"sd":{"actions":["musicUrl"]}}""")
        assertEquals("qs", LxUserApiRuntime.selectSourceKey(both, "qs", "musicUrl"))
        assertEquals("sd", LxUserApiRuntime.selectSourceKey(both, "sd", "musicUrl"))
        assertEquals("qs", LxUserApiRuntime.selectSourceKey(both, "sd", "lyric"))
    }

    @Test fun qishuiQualitySelectionUsesTheGlobalPreferenceAndOnlyDeclaredSpellings() {
        val sd = JSONArray("""["128k","320k","hires"]""")
        assertEquals("128k", LxUserApiRuntime.selectRequestedQuality(sd, "320k", "128k"))
        assertEquals("320k", LxUserApiRuntime.selectRequestedQuality(sd, "128k", "320k"))
        assertEquals("hires", LxUserApiRuntime.selectRequestedQuality(sd, "128k", "flac24bit"))
        assertEquals("320k", LxUserApiRuntime.selectRequestedQuality(sd, "128k", "flac"))
        assertEquals("320k", LxUserApiRuntime.selectRequestedQuality(sd, "320k", "auto"))
        assertEquals("320k", LxUserApiRuntime.selectRequestedQuality(JSONArray("""["128k","320k"]"""), "128k", "flac24bit"))
        assertTrue(runCatching { LxUserApiRuntime.selectRequestedQuality(JSONArray(), "128k", "flac24bit") }
            .exceptionOrNull() is IllegalStateException)
    }

    @Test fun responseMagicOverridesMp3NamesAndMisleadingHttpContentTypes() {
        val fixtures = listOf(
            "fLaC\u0000\u0000\u0000\u0022".toByteArray() to ("flac" to "audio/flac"),
            byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray() to ("m4a" to "audio/mp4"),
            "OggS\u0000\u0000OpusHead".toByteArray() to ("opus" to "audio/ogg"),
            "OggS\u0000\u0000vorbis".toByteArray() to ("ogg" to "audio/ogg"),
            "RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray() to ("wav" to "audio/wav"),
            "FORM\u0000\u0000\u0000\u0000AIFF".toByteArray() to ("aiff" to "audio/aiff"),
            byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x50) to ("aac" to "audio/aac"),
            byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte()) to ("mp3" to "audio/mpeg")
        )
        for ((bytes, expected) in fixtures) {
            val format = com.ella.music.data.detectAudioContainerFormat(bytes, "audio/mpeg", "Track.mp3", "audio/mpeg")
            assertEquals(expected.first, format.extension)
            assertEquals(expected.second, format.mimeType)
        }
        val id3Flac = byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 0) + "fLaC".toByteArray()
        assertEquals("flac", com.ella.music.data.detectAudioContainerFormat(id3Flac, "audio/mpeg").extension)
        assertEquals("flac", com.ella.music.data.detectAudioContainerFormat(byteArrayOf(1), "audio/x-flac; charset=binary", "Track.mp3").extension)
    }

    @Test fun audioDownloadsKeepEveryByteUseActualContainerAndPreserveExistingFiles() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("lx-audio-format-").toFile()
        try {
            for ((bytes, extension, mime) in listOf(
                Triple("fLaC".toByteArray() + ByteArray(80) { it.toByte() }, "flac", "audio/flac"),
                Triple(byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray() + ByteArray(80), "m4a", "audio/mp4")
            )) {
                val service = LxOnlineService(RuntimeEnvironment.getApplication(), pageClient { request ->
                    response(request).newBuilder().body(bytes.toResponseBody("audio/mpeg".toMediaType())).build()
                }) { _, _ -> null }
                val original = item().song.copy(path = "https://example.test/download", fileName = "Track.mp3", mimeType = "audio/mpeg")
                val requested = java.io.File(folder, "$extension.mp3")
                val first = service.downloadAudioToFile(original, requested)
                assertEquals("$extension.$extension", first.fileName)
                assertEquals(mime, first.mimeType)
                assertFalse(requested.exists())
                assertArrayEquals(bytes, java.io.File(first.path).readBytes())
                val second = service.downloadAudioToFile(original, requested)
                assertTrue(first.path != second.path)
                assertArrayEquals(bytes, java.io.File(first.path).readBytes())
                assertArrayEquals(bytes, java.io.File(second.path).readBytes())
            }
        } finally {
            folder.listFiles().orEmpty().forEach { it.delete() }
            folder.delete()
        }
    }

    @Test fun partialRangesAndPlaylistResponsesDoNotPublishFakeAudioFiles() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("lx-audio-incomplete-").toFile()
        try {
            for ((code, payload, range) in listOf(
                Triple(206, "ID3fixture", "bytes 0-9/100"),
                Triple(200, "#EXTM3U\nsegment.ts", null),
                Triple(200, "<html>Forbidden</html>", null),
                Triple(200, "", null)
            )) {
                val service = LxOnlineService(RuntimeEnvironment.getApplication(), pageClient { request ->
                    response(request, code, payload).newBuilder().apply { range?.let { header("Content-Range", it) } }.build()
                }) { _, _ -> null }
                val failure = runCatching { service.downloadAudioToFile(item().song.copy(path = "https://example.test/audio"),
                    java.io.File(folder, "Track.mp3")) }.exceptionOrNull()
                assertTrue(failure is IOException)
                assertTrue(folder.listFiles().orEmpty().isEmpty())
            }
        } finally { folder.delete() }
    }

    private fun item() = LxOnlineSong(
        Song(1, "Test track", "Artist", "Album", 0, 180000, "", "Test track.mp3"),
        source = "wy", songmid = "123", quality = "128k"
    )

    @Test fun selectedSourceFailureIsReportedWithoutSwitchingToTheOfficialPreviewUrl() = runBlocking {
        val error = IOException("Source unavailable")
        val service = LxOnlineService(RuntimeEnvironment.getApplication()) { _, script ->
            assertEquals("selected-script", script)
            throw error
        }
        val failure = runCatching { service.resolvePlayableSong(item(), "selected-script") }.exceptionOrNull()
        assertEquals(error.javaClass, failure?.javaClass)
        assertEquals(error.message, failure?.message)
    }

    @Test fun selectedSourceWithNoUrlDoesNotFallBackToAnOfficialPreview() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val service = LxOnlineService(context) { _, _ -> null }
        val failure = runCatching { service.resolvePlayableSong(item(), "selected-script") }.exceptionOrNull()
        assertEquals(context.getString(R.string.lx_service_source_no_playback_url), failure?.message)
    }

    @Test fun builtInPlaybackRemainsAvailableWhenNoCustomSourceIsSelected() = runBlocking {
        val service = LxOnlineService(RuntimeEnvironment.getApplication()) { _, _ -> null }
        assertEquals("https://music.163.com/song/media/outer/url?id=123.mp3", service.resolvePlayableSong(item()).path)
    }

    @Test fun resolvedCustomUrlIsUsedWithoutRewritingIt() = runBlocking {
        val url = "https://example.test/licensed-track.mp3"
        val service = LxOnlineService(RuntimeEnvironment.getApplication()) { _, _ -> url }
        val playable = service.resolvePlayableSong(item(), "selected-script")
        assertEquals(url, playable.path)
        assertEquals(item().song.duration, playable.duration)
    }

    @Test fun cancellationNeverStartsFallbackPlayback() = runBlocking {
        for (script in listOf("", "selected-script")) {
            val error = CancellationException("Playback cancelled")
            val service = LxOnlineService(RuntimeEnvironment.getApplication()) { _, _ -> throw error }
            val failure = runCatching { service.resolvePlayableSong(item(), script) }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals(error.message, failure?.message)
        }
    }
    @Test
    fun `migu search signature matches lx music protocol`() {
        assertEquals(
            "01704e9ed9d7ce24f28e21c4d60ce681",
            createMiguSearchSignature(
                keyword = "See You Again",
                timestamp = "1720000000000",
                deviceId = "963B7AA0D21511ED807EE5846EC87D20"
            )
        )
    }
}
