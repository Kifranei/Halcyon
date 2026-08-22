package com.ella.music.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmModelsTest {

    @Test
    fun historySourceFlagsKeepLocalAndRemoteModesDistinct() {
        assertTrue(ListeningHistorySource.Local.usesLocal)
        assertTrue(!ListeningHistorySource.Local.usesLastFm)
        assertTrue(!ListeningHistorySource.LastFm.usesLocal)
        assertTrue(ListeningHistorySource.LastFm.usesLastFm)
        assertTrue(ListeningHistorySource.Combined.usesLocal)
        assertTrue(ListeningHistorySource.Combined.usesLastFm)
    }

    @Test
    fun remoteSyntheticIdsAreStableAndNeverCollideWithMediaStoreIds() {
        val first = stableLastFmSongId("Winter Bells", "Mai Kuraki", "Winter Bells")
        val second = stableLastFmSongId(" Winter  Bells ", "mai kuraki", "Winter Bells")

        assertEquals(first, second)
        assertTrue(first < 0L)
    }

    @Test
    fun remoteHistoryKeepsDurationWhenTheSongIsNotInTheLocalLibrary() {
        val entry = LastFmTrack(
            title = "Winter Bells",
            artist = "Mai Kuraki",
            album = "Winter Bells",
            playedAt = 1_000_000L,
            durationMs = 211_000L
        ).toPlaybackHistoryEntry()

        assertEquals(211_000L, entry.durationMs)
    }

    @Test
    fun chineseLocaleUsesLastFmZhWikiAndStripsLicenseFooter() {
        assertEquals("zh", lastFmLanguagePrefix(java.util.Locale.SIMPLIFIED_CHINESE))
        assertEquals(
            "https://www.last.fm/zh/music/MONKEY+MAJIK/+wiki",
            lastFmArtistWikiUrl("MONKEY MAJIK", java.util.Locale.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            "https://www.last.fm/zh/music/MONKEY+MAJIK",
            lastFmArtistPageUrl("MONKEY MAJIK", java.util.Locale.SIMPLIFIED_CHINESE)
        )
        val html = """
            <div class="wiki-content"><p>A Japanese-Canadian band.</p>
            User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.</div>
        """.trimIndent()
        assertEquals("A Japanese-Canadian band.", parseLastFmWikiHtml(html))
        assertTrue(artistBioDownloadAllowed(ARTIST_BIO_DOWNLOAD_ALWAYS, wifiConnected = false))
        assertTrue(!artistBioDownloadAllowed(ARTIST_BIO_DOWNLOAD_WIFI, wifiConnected = false))
        assertTrue(!artistBioDownloadAllowed(ARTIST_BIO_DOWNLOAD_NEVER, wifiConnected = true))
    }

    @Test
    fun officialWikiRegionsMatchLastFmLanguageSwitcher() {
        assertEquals(
            listOf("en", "de", "es", "fr", "it", "ja", "pl", "pt", "ru", "sv", "tr", "zh"),
            LAST_FM_WIKI_REGIONS.map { it.code }
        )
        assertEquals("", lastFmWikiHostPrefix("en"))
        assertEquals("ja", lastFmWikiHostPrefix("ja"))
        assertEquals("zh", lastFmWikiHostPrefix("zh"))
        assertEquals("en", normalizeLastFmWikiRegion("EN"))
        assertEquals("en", normalizeLastFmWikiRegion("unknown"))
        assertEquals(
            "https://www.last.fm/ja/music/YOASOBI/+wiki",
            lastFmArtistWikiUrl("YOASOBI", "ja")
        )
        assertEquals(
            "https://www.last.fm/music/Taylor+Swift/+wiki",
            lastFmArtistWikiUrl("Taylor Swift", "en")
        )
    }
}
