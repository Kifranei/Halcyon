package com.ella.music.data.netease

import com.ella.music.data.SettingsManager
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.model.Song
import kotlinx.coroutines.runBlocking
import java.io.IOException
import com.ella.music.data.model.playlistIdentityKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatClawNeteaseTest {
    @Test fun requestCipherMatchesIndependentDotNetUpstreamVector() {
        val json = """{"c":"[{\"id\":33894312}]","e_r":true}"""
        val expected = "11675D69CF25E0559750EF4BF81ECC680607C372872A3914F346ED123220A487F17612330D102063A0BD8603803A35673F889C8048C219BE9379F44AB31EBCD226A4B9DC2EB64403FAB1BC2DC2325D8557BE373343A1EB1E79D8F46AD72FD3A2C7DAB8EB01258AB383A83431AA3FFF93A40D5917131DDA5CAE22FC91BA8EB2F4"
        assertEquals(expected, CatClawNeteaseCrypto.encrypt("/eapi/v3/song/detail", json))
    }
    @Test fun plainRawAndBase64ResponsesAreAllAccepted() {
        val json = """{"code":200,"songs":[]}"""
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        }
        val raw = cipher.doFinal(json.toByteArray())
        assertEquals(json, CatClawNeteaseCrypto.decryptResponse(json.toByteArray()))
        assertEquals(json, CatClawNeteaseCrypto.decryptResponse(raw))
        assertEquals(json, CatClawNeteaseCrypto.decryptResponse(Base64.getEncoder().encode(raw)))
    }
    @Test fun ciphertextStartingWithBraceIsStillDecrypted() {
        // Live cloudsearch responses: ECB block for "{\"result\":{\"sear" encrypts to 7B CE 3F 56 ..., so the
        // raw body begins with '{' although it is ciphertext (issue seen as "Expected ':' after ..." on device).
        val json = """{"result":{"searchQcReminder":null,"songs":[{"id":35023412,"name":"Let Me Hear"}]},"code":200}"""
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        }
        val raw = cipher.doFinal(json.toByteArray())
        assertEquals('{'.code.toByte(), raw[0])
        assertEquals(json, CatClawNeteaseCrypto.decryptResponse(raw))
    }
    @Test fun cookieParserKeepsSessionValuesButDropsCookieAttributes() {
        val jar = neteaseCookies("MUSIC_U=a=b; Path=/; Secure; __csrf=csrf; Domain=.163.com; MUSIC_U=latest")
        assertEquals(mapOf("MUSIC_U" to "latest", "__csrf" to "csrf"), jar)
        assertFalse(NeteaseAccount("MUSIC_A=anonymous", 42).loggedIn)
        assertTrue(NeteaseAccount("MUSIC_U=secret", 42).loggedIn)
        assertFalse(NeteaseAccount("MUSIC_U=secret", 42).toString().contains("secret"))
    }
    @Test fun metadataUsesStableVirtualUriInsteadOfExpiringCdnAddress() {
        val song = parseNeteaseSong(JSONObject("""{"id":42,"name":"Track","dt":120000,"ar":[{"name":"Artist"}],"al":{"id":7,"name":"Album","picUrl":"http://p1.music.126.net/cover.jpg"}}"""))!!
        assertEquals("halcyon-netease://song/42", song.path)
        assertEquals("netease", song.onlineSource)
        assertEquals(-42L, song.id)
        assertEquals(120000L, song.duration)
        assertTrue(song.coverUrl.startsWith("https://"))
        assertEquals(song.playlistIdentityKey(), song.copy(onlineLyrics = "[00:00]Lyrics").playlistIdentityKey())
        assertEquals(SettingsManager.LIBRARY_SOURCE_NETEASE, SettingsManager.normalizeLibrarySource("netease"))
    }
    @Test fun moreThanAThousandFavoritesAndUnavailableTracksKeepTheirFullOrder() {
        val ids = (1L..1505L).toList().reversed()
        val available = ids.filterNot { it == 777L }.associateWith {
            parseNeteaseSong(JSONObject().put("id", it).put("name", "Track $it"))!!
        }
        val songs = reconcileNeteaseTracks(ids, available)
        assertEquals(1505, songs.size)
        assertEquals(ids, songs.map { it.onlineId.toLong() })
        assertEquals("#777", songs.first { it.onlineId == "777" }.title)
    }

    @Test fun playbackProviderDefaultsToOfficialAndPluginsDoNotChangeAccountIdentity() = runBlocking {
        assertEquals(NeteasePlaybackProvider.Official, NeteasePlaybackProvider.fromId(null))
        assertEquals(NeteasePlaybackProvider.Official, NeteasePlaybackProvider.fromId("unknown"))
        val original = parseNeteaseSong(JSONObject("""{"id":42,"name":"Track","dt":120000,"ar":[{"name":"Artist"}],"al":{"id":7,"name":"Album","picUrl":"https://cover.test/a.jpg"}}"""))!!
            .copy(onlineLyrics = "account lyrics", onlineLyricTranslation = "translation", onlineMvId = "9", playbackSourceKey = "playlist:123")
        for (provider in NeteasePlaybackProvider.entries) {
            var lxCalls = 0
            var mfCalls = 0
            val resolved = NeteasePlaybackResolver(
                provider = { provider }, lxSource = { LxSourceConfig("lx", "", "LX", "source") },
                musicFreeSource = { MusicFreePluginConfig("mf", "", "MusicFree", "source") },
                resolveLx = { _, _ -> lxCalls++; original.copy(path = "https://audio.test/track.flac", mimeType = "audio/flac", fileName = "track.flac") },
                resolveMusicFree = { _, _ -> mfCalls++; original.copy(path = "https://audio.test/master.m3u8#scope", mimeType = "application/x-mpegURL", fileName = "track.m3u8", coverUrl = "plugin cover", onlineLyrics = "plugin lyrics") },
                message = { "error $it" }
            ).resolve(original)
            if (provider == NeteasePlaybackProvider.Official) {
                assertNull(resolved)
            } else {
                assertEquals(original, resolved!!.copy(path = original.path, mimeType = original.mimeType, fileName = original.fileName))
                assertEquals("netease", resolved.onlineSource)
                assertEquals("42", resolved.onlineId)
            }
            assertEquals(if (provider == NeteasePlaybackProvider.Lx) 1 else 0, lxCalls)
            assertEquals(if (provider == NeteasePlaybackProvider.MusicFree) 1 else 0, mfCalls)
        }
    }

    @Test fun explicitlySelectedPluginFailuresAndMissingSourcesNeverUseAnotherResolver() = runBlocking {
        val song = parseNeteaseSong(JSONObject().put("id", 42).put("name", "Track"))!!
        val failure = IOException("Plugin fixture failure")
        var mfCalls = 0
        fun resolver(source: LxSourceConfig?) = NeteasePlaybackResolver(
            provider = { NeteasePlaybackProvider.Lx }, lxSource = { source }, musicFreeSource = { null },
            resolveLx = { _, _ -> throw failure }, resolveMusicFree = { _, _ -> mfCalls++; song },
            message = { "Missing selected source" }
        )
        assertSame(failure, runCatching { resolver(LxSourceConfig("lx", "", "LX", "script")).resolve(song) }.exceptionOrNull())
        assertTrue(runCatching { resolver(null).resolve(song) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { resolver(LxSourceConfig("lx", "", "LX", "")).resolve(song) }.exceptionOrNull() is IOException)
        assertEquals(0, mfCalls)
        assertFalse(isNeteasePluginStreamUrl("file:///private/file.mp3"))
        assertFalse(isNeteasePluginStreamUrl("halcyon-netease://song/42"))
        assertFalse(isNeteasePluginStreamUrl("https://"))
        assertTrue(isNeteasePluginStreamUrl("https://audio.test/track.m3u8#private-scope"))
    }

    @Test fun pluginMusicInfoUsesTheOriginalNeteaseIdAndDoesNotCarryAccountLyricsOrTransport() {
        val song = parseNeteaseSong(JSONObject("""{"id":42,"name":"Track","dt":120000,"ar":[{"name":"Artist"}],"al":{"id":7,"name":"Album"}}"""))!!
            .copy(onlineLyrics = "account lyrics", onlineLyricTranslation = "private translation")
        val lx = song.toNeteaseLxItem()
        assertEquals("wy", lx.source)
        assertEquals("42", lx.songmid)
        assertEquals("Album", lx.song.album)
        assertEquals("Artist", lx.sourceMetadata["singer"])
        assertEquals("120", lx.sourceMetadata["duration"])
        assertEquals("", lx.song.path)
        assertEquals("", lx.song.onlineLyrics)
        val raw = JSONObject(song.toNeteaseMusicFreeItem("网易云").rawJson)
        assertEquals("42", raw.getString("id"))
        assertEquals("网易云", raw.getString("platform"))
        assertEquals("Artist", raw.getString("artist"))
        assertEquals(120.0, raw.getDouble("duration"), 0.0)
        assertFalse(raw.has("cookie"))
        assertFalse(raw.has("url"))
        assertFalse(raw.toString().contains("account lyrics"))
        assertTrue(song.matchesNeteasePluginTrack(song.copy(title = "TRACK", artist = "artist")))
        assertFalse(song.matchesNeteasePluginTrack(song.copy(artist = "Cover Artist")))
        assertFalse(song.matchesNeteasePluginTrack(song.copy(title = "Track (Live)")))
        assertFalse(song.matchesNeteasePluginTrack(song.copy(duration = 180000)))
    }
}
