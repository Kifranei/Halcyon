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
    fun chineseFallbackDoesNotOverrideOtherSelectedRegions() {
        assertEquals(
            listOf(
                ArtistWikiSource.Netease,
                ArtistWikiSource.LastFmHtml,
                ArtistWikiSource.WikipediaSelected,
                ArtistWikiSource.WikipediaEnglish
            ),
            artistWikiSourceOrder("zh", hasApiKey = false)
        )
        assertEquals(
            listOf(
                ArtistWikiSource.LastFmHtml,
                ArtistWikiSource.WikipediaSelected,
                ArtistWikiSource.WikipediaEnglish
            ),
            artistWikiSourceOrder("ja", hasApiKey = false)
        )
        assertEquals(
            ArtistWikiSource.LastFmApi,
            artistWikiSourceOrder("de", hasApiKey = true).first()
        )
        assertEquals(
            listOf(
                ArtistWikiSource.LastFmHtml,
                ArtistWikiSource.WikipediaSelected,
                ArtistWikiSource.WikipediaEnglish
            ),
            artistWikiSourceOrder("zh", hasApiKey = false, vpnActive = true)
        )
    }
}
