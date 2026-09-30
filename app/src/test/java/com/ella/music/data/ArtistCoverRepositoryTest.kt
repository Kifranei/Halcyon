package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistCoverRepositoryTest {
    @Test fun parenthesizedNumberedCoversPreserveCaseAndOrdering() {
        assertEquals("LiSA", artistCoverMatch("LiSA (1).jpg", ignoreCase = false)?.key)
        assertEquals(1, artistCoverMatch("LiSA (1).jpg")?.order)
        assertEquals(2, artistCoverMatch("LiSA (2).png")?.order)
        assertEquals("lisa", artistCoverMatch("LISA_001.jpg")?.key)
    }
    @Test
    fun imageExtensionsMatchArtistNamesCaseInsensitively() {
        assertEquals(
            "fleetwood mac",
            artistCoverMatch("Fleetwood Mac.JPG")?.key
        )
        assertEquals(
            "taylor swift",
            artistCoverMatch("Taylor   Swift.webp")?.key
        )
    }

    @Test
    fun unsupportedFilesAreIgnored() {
        assertNull(artistCoverMatch("Fleetwood Mac.txt")?.key)
        assertNull(artistCoverMatch("README")?.key)
    }

    @Test
    fun videoExtensionsAlsoMatchArtistNames() {
        assertEquals(
            "fleetwood mac",
            artistCoverMatch("Fleetwood Mac.mp4")?.key
        )
        assertEquals(
            ArtistCoverKind.Video,
            artistCoverMatch("Taylor Swift.webm")?.kind
        )
    }

    @Test
    fun supportedVideoMimeTypesAreRecognized() {
        val match = artistCoverMatch("Aimer.cover", "video/mp4")
        assertEquals("aimer", match?.key)
        assertEquals(ArtistCoverKind.Video, match?.kind)
    }

    @Test
    fun numberedSuffixesMapToTheSameArtistWithOrder() {
        val plain = artistCoverMatch("Taylor Swift.jpg")
        val first = artistCoverMatch("Taylor Swift_01.JPG")
        val second = artistCoverMatch("Taylor Swift_02.png")
        assertEquals("taylor swift", plain?.key)
        assertEquals("taylor swift", first?.key)
        assertEquals("taylor swift", second?.key)
        assertEquals(0, plain?.order)
        assertEquals(1, first?.order)
        assertEquals(2, second?.order)
    }

    @Test
    fun normalizeArtistCoverKeyCleansWhitespace() {
        assertEquals(
            "lana del rey",
            normalizeArtistCoverKey("  Lana   Del Rey  ")
        )
    }

    @Test
    fun folderCoverKeysHonorCaseSensitivity() {
        assertEquals("LiSA", normalizeArtistCoverKey("LiSA", ignoreCase = false))
        assertEquals("LISA", normalizeArtistCoverKey("LISA", ignoreCase = false))
        assertEquals("lisa", normalizeArtistCoverKey("LiSA", ignoreCase = true))
        assertEquals(
            "LiSA",
            artistCoverMatch("LiSA_01.jpg", ignoreCase = false)?.key
        )
        assertEquals(
            "LISA",
            artistCoverMatch("LISA.jpg", ignoreCase = false)?.key
        )
    }
}
