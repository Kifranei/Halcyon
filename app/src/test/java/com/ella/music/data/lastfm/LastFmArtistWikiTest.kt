package com.ella.music.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class LastFmArtistWikiTest {
    private fun respondingClient(response: (Request) -> Pair<Int, String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (status, body) = response(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }.build()

    @Test
    fun actualChallengeTitlesAndCaptchaContainersAreRecognized() {
        listOf(
            "<html><title>Client Challenge</title><noscript>JavaScript is disabled in your browser</noscript></html>",
            "<html><title data-test='title'> Just a moment... </title></html>",
            "<html><title>Attention Required! | Cloudflare</title></html>",
            "<html><div id='px-captcha'></div></html>",
            "<html><form id='cf-browser-verification'></form></html>"
        ).forEach { assertTrue(it, isBotChallengeHtml(it)) }
    }

    @Test
    fun normalBiographyScriptsAndApiJsonDoNotTriggerVerification() {
        val wiki = """
            <html><title>Muse biography | Last.fm</title>
            <script src="https://challenges.cloudflare.com/turnstile.js"></script>
            <script>var provider = 'PerimeterX HUMAN Security'; var template = '<div id="px-captcha"></div>';</script>
            <noscript>JavaScript is disabled in your browser</noscript>
            <div class="wiki-content"><p>The album Client Challenge features Just a moment...</p></div></html>
        """.trimIndent()
        assertFalse(isBotChallengeHtml(wiki))
        assertFalse(isBotChallengeHtml("""{"artist":{"bio":{"content":"Client Challenge HUMAN Security <div id='px-captcha'>"}}}"""))
        assertFalse(isBotChallengeHtml("<html><title>Artist</title><p>PerimeterX and HUMAN Security</p></html>"))
        assertFalse(isBotChallengeHtml("<html><script>var widget = '<div id=\"px-captcha\"></div>';</script></html>"))
    }

    @Test
    fun onlyLastFmWebsiteChallengesOpenVerification() {
        val challenge = "<html><title>Client Challenge</title></html>"
        for (status in listOf(200, 403, 503)) {
            val client = respondingClient { status to challenge }
            val error = assertThrows(LastFmVerificationRequiredException::class.java) {
                client.executeText("https://www.last.fm/music/Muse/+wiki")
            }
            assertTrue(error.message.orEmpty().contains("HTTP $status"))
            assertFalse(error.message.orEmpty().contains("Cloudflare"))
        }
        assertTrue(isLastFmWebsiteUrl("https://www.last.fm/music/Muse/+wiki"))
        assertFalse(isLastFmWebsiteUrl("https://last.fm.example.org/"))
        assertFalse(isLastFmWebsiteUrl("https://example.org/?next=last.fm"))
        val apiResult = respondingClient { 200 to challenge }
            .executeText("https://ws.audioscrobbler.com/2.0/")
        assertEquals(challenge, apiResult)
        val wikiError = assertThrows(IllegalStateException::class.java) {
            respondingClient { 403 to challenge }.executeText("https://en.wikipedia.org/w/api.php")
        }
        assertTrue(wikiError.message.orEmpty().contains("HTTP 403"))
    }

    @Test
    fun ordinaryHttpFailuresAreNotChallengesAndNeverExposeApiKeys() {
        for (host in listOf("www.last.fm", "ws.audioscrobbler.com", "en.wikipedia.org")) {
            for (status in listOf(403, 503)) {
                val error = assertThrows(IllegalStateException::class.java) {
                    respondingClient { status to "Service unavailable" }
                        .executeText("https://$host/test?api_key=private-test-key")
                }
                assertTrue(error.message.orEmpty().contains("HTTP $status"))
                assertFalse(error.message.orEmpty().contains("private-test-key"))
            }
        }
    }

    @Test
    fun apiKeySelectsApiAndUsesIsoLanguageWithoutWebRequest() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = respondingClient { request ->
            requests += request
            200 to """{"artist":{"name":"Muse","bio":{"content":"Biography"}}}"""
        }
        val wiki = fetchLastFmArtistWiki("Muse", "zh-tw", "test-key", ArtistBioMenuSource.LastFm, client)
        assertEquals(ArtistWikiSource.LastFmApi, wiki.source)
        assertEquals(1, requests.size)
        assertEquals("ws.audioscrobbler.com", requests.single().url.host)
        assertEquals("zh", requests.single().url.queryParameter("lang"))
    }

    @Test
    fun apiErrorsRemainVisibleWhenWebsiteFallbackRequiresVerification() = runBlocking {
        for ((status, code) in listOf(403 to 10, 200 to 26, 429 to 29, 503 to 11)) {
            var apiRequests = 0
            val client = respondingClient { request ->
                if (request.url.host == "ws.audioscrobbler.com") {
                    apiRequests++
                    status to """{"error":$code,"message":"API failure"}"""
                } else {
                    200 to "<html><title>Client Challenge</title></html>"
                }
            }
            val error = runCatching {
                fetchLastFmArtistWiki("Muse", "en", "test-key", ArtistBioMenuSource.LastFm, client)
            }.exceptionOrNull()
            assertTrue(error is LastFmVerificationRequiredException)
            assertTrue(error?.cause is LastFmArtistApiException)
            assertTrue(error?.message.orEmpty().contains("code $code"))
            assertTrue(error?.message.orEmpty().contains("HTTP 200"))
            assertEquals(1, apiRequests)
        }
    }

    @Test
    fun failedApiCanStillUseSelectedProvidersWebsite() = runBlocking {
        val client = respondingClient { request ->
            if (request.url.host == "ws.audioscrobbler.com") {
                403 to """{"error":10,"message":"Invalid API key"}"""
            } else {
                200 to """<html><div class="wiki-content"><p>Website biography</p></div></html>"""
            }
        }
        val wiki = fetchLastFmArtistWiki("Muse", "en", "test-key", ArtistBioMenuSource.LastFm, client)
        assertEquals(ArtistWikiSource.LastFmHtml, wiki.source)
        assertEquals("Website biography", wiki.text)
    }

    @Test
    fun cancelledFetchDoesNotStartWebsiteFallback() = runBlocking {
        var requests = 0
        val client = respondingClient {
            requests++
            throw CancellationException("Cancelled request")
        }
        val error = runCatching {
            fetchLastFmArtistWiki("Muse", "en", "test-key", ArtistBioMenuSource.LastFm, client)
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, requests)
    }

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
