package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistCoverRepositoryTest {
    @Test
    fun imageExtensionsMatchArtistNamesCaseInsensitively() {
        assertEquals(
            "fleetwood mac",
            artistCoverMatchKey("Fleetwood Mac.JPG")
        )
        assertEquals(
            "taylor swift",
            artistCoverMatchKey("Taylor   Swift.webp")
        )
    }

    @Test
    fun unsupportedFilesAreIgnored() {
        assertNull(artistCoverMatchKey("Fleetwood Mac.txt"))
        assertNull(artistCoverMatchKey("README"))
    }

    @Test
    fun videoExtensionsAlsoMatchArtistNames() {
        assertEquals(
            "fleetwood mac",
            artistCoverMatchKey("Fleetwood Mac.mp4")
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
            artistCoverMatchKey("LiSA_01.jpg", ignoreCase = false)
        )
        assertEquals(
            "LISA",
            artistCoverMatchKey("LISA.jpg", ignoreCase = false)
        )
    }
}
