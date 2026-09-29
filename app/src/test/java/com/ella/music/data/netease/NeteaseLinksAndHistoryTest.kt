package com.ella.music.data.netease

import com.ella.music.data.OnlinePlaybackQuality
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseLinksAndHistoryTest {
    @Test fun presetsBuildDocumentedLinks() {
        val web = NeteaseLinkSettings()
        assertEquals("https://y.music.163.com/m/mv?id=42", NeteaseLinks.build(web, NeteaseLinkKind.MusicVideo, "42"))
        val app = NeteaseLinkSettings(target = NeteaseLinkTarget.App)
        assertEquals("orpheus://mv/42", NeteaseLinks.build(app, NeteaseLinkKind.MusicVideo, "42"))
        assertEquals("orpheus://comment?threadId=R_SO_4_7", NeteaseLinks.build(app, NeteaseLinkKind.Comment, "7"))
        val honor = NeteaseLinkSettings(target = NeteaseLinkTarget.HonorApp)
        assertEquals("honororpheus://album/9", NeteaseLinks.build(honor, NeteaseLinkKind.Album, "9"))
        assertEquals("https://music.163.com/st/artistwiki?artistId=3", NeteaseLinks.build(honor, NeteaseLinkKind.ArtistWiki, "3"))
    }

    @Test fun customTemplatesAppendOrSubstituteAndFallBackWhenBlank() {
        val custom = NeteaseLinkSettings(
            target = NeteaseLinkTarget.Custom,
            custom = mapOf(NeteaseLinkKind.Song to "myapp://track/", NeteaseLinkKind.Artist to "https://x.test/a/{id}/info")
        )
        assertEquals("myapp://track/5", NeteaseLinks.build(custom, NeteaseLinkKind.Song, "5"))
        assertEquals("https://x.test/a/6/info", NeteaseLinks.build(custom, NeteaseLinkKind.Artist, "6"))
        assertEquals("https://y.music.163.com/m/album?id=8", NeteaseLinks.build(custom, NeteaseLinkKind.Album, "8"))
        assertNull(NeteaseLinks.build(custom, NeteaseLinkKind.Song, " "))
    }

    @Test fun webResolverLinksAreRetargetable() {
        assertEquals(NeteaseLinkKind.Artist to "12", NeteaseLinks.parseWebUrl("https://y.music.163.com/m/artist?id=12"))
        assertEquals(NeteaseLinkKind.Album to "34", NeteaseLinks.parseWebUrl("https://music.163.com/m/album?id=34"))
        assertNull(NeteaseLinks.parseWebUrl("https://example.com/m/album?id=34"))
    }

    @Test fun onlineQualityNeverRequestsAboveTheChosenTier() {
        assertEquals("flac", OnlinePlaybackQuality.normalize("lossless"))
        assertEquals("flac24bit", OnlinePlaybackQuality.normalize("hires"))
        assertEquals("auto", OnlinePlaybackQuality.normalize("bogus"))
        assertEquals("flac", OnlinePlaybackQuality.lxTier("lossless", listOf("128k", "320k", "flac", "flac24bit")))
        assertEquals("320k", OnlinePlaybackQuality.lxTier("flac", listOf("128k", "320k")))
        assertNull(OnlinePlaybackQuality.lxTier("auto", listOf("128k")))
        assertEquals("super", OnlinePlaybackQuality.musicFreeCandidates("flac").first())
        assertEquals("high", OnlinePlaybackQuality.musicFreeCandidates("320k").first())
    }

    @Test fun cloudHistoryParsesSongsNewestFirstWithStableIds() {
        val root = JSONObject("""{"code":200,"data":{"total":2,"list":[
            {"resourceId":"1","playTime":1000,"resourceType":"SONG","data":{"id":1,"name":"A","dt":1000,"ar":[{"name":"X"}],"al":{"id":2,"name":"Al"}}},
            {"resourceId":"3","playTime":3000,"resourceType":"SONG","data":{"id":3,"name":"B","dt":2000,"ar":[{"name":"Y"}],"al":{"id":4,"name":"Bl"}}},
            {"resourceId":"5","playTime":2000,"resourceType":"VOICE","data":{"id":5,"name":"V"}}
        ]}}""")
        val plays = parseNeteaseRecentPlays(root)
        assertEquals(listOf("B", "A"), plays.map { it.song.title })
        val entry = plays.first().toPlaybackHistoryEntry()
        assertEquals("netease:3:3000", entry.entryId)
        assertEquals(NETEASE_HISTORY_SOURCE, entry.source)
        assertEquals(3000L, entry.playedAt)
    }

    @Test fun historyEntryRebuildsPlayableNeteaseSong() {
        val entry = com.ella.music.data.PlaybackHistoryEntry(
            songId = -42L, title = "T", artist = "A", album = "B", playedAt = 1L, durationMs = 1000L,
            onlineSource = NETEASE_SOURCE, onlineId = "42", coverUrl = "https://p.example/c.jpg"
        )
        val song = entry.toNeteaseHistorySong()!!
        assertEquals(-42L, song.id)
        assertEquals("$NETEASE_SCHEME://song/42", song.path)
        assertEquals("https://p.example/c.jpg", song.coverUrl)
        assertNull(entry.copy(onlineSource = "").toNeteaseHistorySong())
        assertNull(entry.copy(mediaUri = "halcyon-netease-mv://mv/1").toNeteaseHistorySong())
    }
}
