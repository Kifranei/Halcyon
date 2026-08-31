package com.ella.music.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmArtistWikiTest {
    @Test
    fun neteaseSearchPrefersExactArtistName() {
        val json = """{"result":{"artists":[{"id":1,"name":"Muse Tribute"},{"id":2,"name":"Muse"}]}}"""

        assertEquals("2", parseNeteaseArtistId(json, "muse"))
    }

    @Test
    fun neteaseSearchPrefersCaseSensitiveArtistNameBeforeLooseMatch() {
        val json = """{"result":{"artists":[{"id":12062254,"name":"LISA"},{"id":16995,"name":"LiSA"}]}}"""

        assertEquals("16995", parseNeteaseArtistId(json, "LiSA"))
        assertEquals("12062254", parseNeteaseArtistId(json, "LISA"))
    }

    @Test
    fun wikipediaSearchPrefersCaseSensitiveArtistNameBeforeLooseMatch() {
        val json = """{"query":{"search":[{"title":"LiSA"},{"title":"LISA"}]}}"""

        assertEquals("LISA", parseWikipediaSearchTitle(json, "LISA"))
        assertEquals("LiSA", parseWikipediaSearchTitle(json, "LiSA"))
    }

    @Test
    fun biographySearchDoesNotFallBackToDifferentArtist() {
        val json = """{"result":{"artists":[{"id":1,"name":"LISA Tribute"}]}}"""

        assertEquals(null, parseNeteaseArtistId(json, "LISA"))
    }

    @Test
    fun neteaseBiographyCombinesBriefAndSections() {
        val json = """{"briefDesc":"Brief","introduction":[{"ti":"Career","txt":"Long text"}]}"""

        val result = parseNeteaseArtistBiography(json)

        assertTrue(result.contains("Brief"))
        assertTrue(result.contains("Career\nLong text"))
    }

    @Test
    fun artistImageParsersRejectPlaceholderCovers() {
        val lastFm = """{"artist":{"name":"Muse","image":[{"#text":"https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png","size":"mega"}]}}"""
        val netease = """{"result":{"artists":[{"name":"Muse","picUrl":"https://p1.music.126.net/6y-UleORITEGyl-Rd-In-A==/5639395138885805.jpg"}]}}"""

        assertEquals(null, parseLastFmArtistImageUrl(lastFm, requestedArtistName = "Muse"))
        assertEquals(null, parseNeteaseArtistImageUrl(netease, "Muse"))
    }

    @Test
    fun imageRegionAndBiographyLanguageCanBeChosenIndependently() {
        assertEquals("zh", normalizeLastFmWikiRegion("zh"))
        assertEquals("ja", normalizeLastFmWikiRegion("ja"))
        assertEquals("JP", spotifyMarketForLastFmRegion("ja"))
        assertEquals("US", spotifyMarketForLastFmRegion("en"))
        assertTrue(ARTIST_BIO_LANGUAGES.any { it.code == "zh" })
        assertTrue(LAST_FM_WIKI_REGIONS.any { it.code == "ja" })
        assertTrue(ARTIST_BIO_LANGUAGES.none { it.code == "de" })
        assertTrue(LAST_FM_WIKI_REGIONS.any { it.code == "de" })
    }

    @Test
    fun wikipediaLanguageUsesChineseVariants() {
        assertEquals("zh", wikipediaLanguage("zh-hk"))
        assertEquals("zh-hk", wikipediaVariant("zh-hk"))
        assertEquals("ko", wikipediaLanguage("ko"))
    }

    @Test
    fun lastFmArtistImagePrefersLargestMatchingImage() {
        val json = """
            {"artist":{"name":"Muse","image":[
              {"#text":"https://example.com/small.jpg","size":"small"},
              {"#text":"https://example.com/mega.jpg","size":"mega"},
              {"#text":"https://example.com/large.jpg","size":"large"}
            ]}}
        """.trimIndent()

        assertEquals(
            "https://example.com/mega.jpg",
            parseLastFmArtistImageUrl(json, requestedArtistName = "Muse")
        )
    }

    @Test
    fun artistImageParsersRejectDifferentArtist() {
        val lastFm = """{"artist":{"name":"Muse Tribute","image":[{"#text":"https://example.com/a.jpg","size":"mega"}]}}"""
        val netease = """{"result":{"artists":[{"name":"Muse Tribute","picUrl":"https://example.com/a.jpg"}]}}"""

        assertEquals(null, parseLastFmArtistImageUrl(lastFm, requestedArtistName = "Muse"))
        assertEquals(null, parseNeteaseArtistImageUrl(netease, "Muse"))
    }

    @Test
    fun defaultBiographySourceDoesNotFallBackToAnotherProvider() {
        assertEquals(
            listOf(ArtistWikiSource.LastFmHtml),
            artistWikiSourceOrder("zh", hasApiKey = false)
        )
        assertEquals(
            listOf(ArtistWikiSource.LastFmHtml),
            artistWikiSourceOrder("ja", hasApiKey = false)
        )
        assertEquals(
            ArtistWikiSource.LastFmApi,
            artistWikiSourceOrder("de", hasApiKey = true).first()
        )
    }

    @Test
    fun preferredBiographySourceDoesNotFallBackToAnotherProvider() {
        assertEquals(
            listOf(ArtistWikiSource.Netease),
            artistWikiSourceOrder("zh", hasApiKey = false, preferred = ArtistBioMenuSource.Netease)
        )
        assertEquals(
            listOf(ArtistWikiSource.LastFmHtml),
            artistWikiSourceOrder("ja", hasApiKey = false, preferred = ArtistBioMenuSource.LastFm)
        )
        assertEquals(
            listOf(ArtistWikiSource.LastFmApi, ArtistWikiSource.LastFmHtml),
            artistWikiSourceOrder("en", hasApiKey = true, preferred = ArtistBioMenuSource.LastFm)
        )
        assertEquals(
            listOf(
                ArtistWikiSource.LastFmHtml
            ),
            artistWikiSourceOrder("zh", hasApiKey = false, preferred = ArtistBioMenuSource.LastFm)
        )
        assertEquals(
            listOf(ArtistWikiSource.WikipediaSelected, ArtistWikiSource.WikipediaEnglish),
            artistWikiSourceOrder("ja", hasApiKey = false, preferred = ArtistBioMenuSource.Wikipedia)
        )
    }
}
