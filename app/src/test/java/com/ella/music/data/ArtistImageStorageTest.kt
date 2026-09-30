package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistImageStorageTest {
    @Test fun spotifyUsesTheLargestAvailableImageInsteadOfResponseOrdering() {
        val json = """{"artists":{"items":[{"name":"LISA","images":[{"url":"https://image.test/small.jpg","width":160,"height":160},{"url":"https://image.test/large.jpg","width":640,"height":640}]}]}}"""
        assertEquals("https://image.test/large.jpg", ArtistImageRepository.parseSpotifyArtistImageUrl(json, "LISA"))
    }
    @Test fun differentArtistCasingReservesAnotherImageAndItsSourceSidecar() {
        assertEquals("LiSA (1).jpg", nextArtistImageFileName("LiSA", "jpg", listOf("LISA.jpg", "LISA.jpg.source")))
        assertEquals("LiSA (2).jpg", nextArtistImageFileName("LiSA", "jpg", listOf("LISA.jpg", "lisa (1).jpg.source")))
        val dir = java.nio.file.Files.createTempDirectory("artist-case-").toFile()
        try {
            java.io.File(dir, "LISA.jpg").writeText("first artist")
            java.io.File(dir, "LiSA (1).jpg").writeText("second artist")
            assertEquals(listOf("LISA.jpg"), matchingArtistImageFiles(dir, "LISA").map { it.name })
            assertEquals(listOf("LiSA (1).jpg"), matchingArtistImageFiles(dir, "LiSA").map { it.name })
            assertEquals("first artist", java.io.File(dir, "LISA.jpg").readText())
        } finally { dir.listFiles().orEmpty().forEach { it.delete() }; dir.delete() }
    }

    @Test fun imageProvidersPreferLargerVariantsAndRetainTheOriginalFallback() {
        val url = "https://lastfm.freetls.fastly.net/i/u/300x300/image.jpg"
        assertEquals(listOf("https://lastfm.freetls.fastly.net/i/u/770x0/image.jpg", url), preferredArtistImageUrls(SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM, url))
        val netease = "https://p1.music.126.net/cover.jpg?param=100y100"
        assertEquals(listOf("https://p1.music.126.net/cover.jpg?param=1000y1000", netease), preferredArtistImageUrls(SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE, netease))
    }
    @Test fun internalStorageNeverFallsBackToTheCoverFolder() {
        assertEquals("", artistImageDownloadFolder(ARTIST_IMAGE_INTERNAL_STORAGE, "content://covers"))
        assertEquals("content://covers", artistImageDownloadFolder("", "content://covers"))
        assertEquals("content://downloads", artistImageDownloadFolder("content://downloads", "content://covers"))
    }
}
