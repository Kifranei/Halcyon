package com.ella.music.ui.player

import com.ella.music.data.NameSplitConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDynamicCoverNamingTest {
    @Test
    fun landscapeMusicVideoCandidatesIncludeUnderscoreAndHyphenSuffixes() {
        val candidates = buildLandscapeMusicVideoNameCandidates(listOf("Baby", "Taylor Swift - Lover"))

        assertTrue("Baby_MV" in candidates)
        assertTrue("Baby-MV" in candidates)
        assertTrue("Taylor Swift - Lover_MV" in candidates)
        assertTrue("Taylor Swift - Lover-MV" in candidates)
    }

    @Test
    fun detectsLandscapeMusicVideoFileNameForSongCandidates() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        assertTrue(isLandscapeMusicVideoFileName("Baby_MV", songCandidates))
        assertTrue(isLandscapeMusicVideoFileName("Baby-MV", songCandidates))
        assertTrue(isLandscapeMusicVideoFileName("Justin Bieber - Baby_MV", songCandidates))
    }

    @Test
    fun ignoresRegularDynamicCoverNames() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        assertFalse(isLandscapeMusicVideoFileName("Baby", songCandidates))
        assertFalse(isLandscapeMusicVideoFileName("cover", songCandidates))
        assertFalse(isLandscapeMusicVideoFileName("Album-MV", songCandidates))
    }

    @Test
    fun ambientAndMusicVideoCandidateListsAreIndependent() {
        val songCandidates = listOf("Baby", "Justin Bieber - Baby")

        val ambient = playerVideoNameCandidates(songCandidates, musicVideoOnly = false)
        val musicVideos = playerVideoNameCandidates(songCandidates, musicVideoOnly = true)

        assertTrue("Baby" in ambient)
        assertFalse(ambient.any { it.endsWith("MV") })
        assertTrue("Baby_MV" in musicVideos)
        assertTrue("Baby-MV" in musicVideos)
        assertFalse("Baby" in musicVideos)
    }

    @Test
    fun artistTitleBaseNamesCoverAllSeparatorAndOrderVariants() {
        val names = buildArtistTitleMusicVideoBaseNames(artist = "Justin Bieber", title = "Baby")

        assertTrue("Justin Bieber - Baby" in names)
        assertTrue("Justin Bieber-Baby" in names)
        assertTrue("Baby - Justin Bieber" in names)
        assertFalse("Baby" in names)
    }

    @Test
    fun artistTitleBaseNamesAreEmptyWhenArtistOrTitleIsBlank() {
        assertTrue(buildArtistTitleMusicVideoBaseNames(artist = "", title = "Baby").isEmpty())
        assertTrue(buildArtistTitleMusicVideoBaseNames(artist = "  ", title = "Baby").isEmpty())
        assertTrue(buildArtistTitleMusicVideoBaseNames(artist = "Justin Bieber", title = "").isEmpty())
    }

    @Test
    fun musicVideoTiersPreferArtistTitleOverBareTitle() {
        val tiers = buildMusicVideoBaseNameTiers(
            fileBaseName = "01 baby",
            title = "Baby",
            artist = "Justin Bieber"
        )

        assertEquals(3, tiers.size)
        assertEquals(listOf("01 baby"), tiers[0])
        assertTrue("Justin Bieber - Baby" in tiers[1])
        assertTrue("Baby - Justin Bieber" in tiers[1])
        assertFalse("Baby" in tiers[1])
        assertEquals(listOf("Baby"), tiers[2])
    }

    @Test
    fun musicVideoTiersFallBackToTitleOnlyWhenArtistIsBlank() {
        val tiers = buildMusicVideoBaseNameTiers(
            fileBaseName = "Baby",
            title = "Baby",
            artist = ""
        )

        // No artist tier, and the title dedupes into the file-name tier.
        assertEquals(listOf(listOf("Baby")), tiers)
    }

    @Test
    fun musicVideoTiersIncludeFullAndFirstArtistForMultiArtistSongs() {
        val previousSeparators = NameSplitConfigStore.artistCustomSeparators
        try {
            NameSplitConfigStore.artistCustomSeparators = listOf("&")
            val tiers = buildMusicVideoBaseNameTiers(
                fileBaseName = "song file",
                title = "Baby",
                artist = "Justin Bieber&Ludacris"
            )

            val artistTier = tiers[1]
            assertTrue("Justin Bieber&Ludacris - Baby" in artistTier)
            assertTrue("Justin Bieber - Baby" in artistTier)
            assertTrue("Baby - Justin Bieber" in artistTier)
            // The bare title stays in the lower-priority fallback tier.
            assertFalse("Baby" in artistTier)
            assertEquals(listOf("Baby"), tiers[2])
        } finally {
            NameSplitConfigStore.artistCustomSeparators = previousSeparators
        }
    }

    @Test
    fun musicVideoFolderCandidatesAcceptPlainAndSuffixedNames() {
        val candidates = musicVideoFolderFileNameCandidates(listOf("Justin Bieber - Baby", "Baby"))

        assertTrue("Justin Bieber - Baby" in candidates)
        assertTrue("Baby" in candidates)
        assertTrue("Justin Bieber - Baby_MV" in candidates)
        assertTrue("Justin Bieber - Baby-MV" in candidates)
        assertTrue("Baby_MV" in candidates)
        // Plain names come before suffixed ones so un-suffixed MV folders match first.
        assertTrue(candidates.indexOf("Justin Bieber - Baby") < candidates.indexOf("Justin Bieber - Baby_MV"))
    }

    @Test
    fun musicVideoFolderExtensionsAreRelaxed() {
        assertEquals(listOf("mp4", "mkv", "webm", "mov"), MUSIC_VIDEO_FOLDER_EXTENSIONS)
        assertTrue(isSupportedMusicVideoExtension("mkv"))
        assertTrue(isSupportedMusicVideoExtension("MKV"))
        assertFalse(isSupportedMusicVideoExtension("avi"))
    }

    @Test
    fun everyMusicVideoLookupBuildsMkvCandidates() {
        val candidates = musicVideoFileNameCandidates(
            buildLandscapeMusicVideoNameCandidates(listOf("Justin Bieber - Baby"))
        )

        assertTrue("Justin Bieber - Baby_MV.mp4" in candidates)
        assertTrue("Justin Bieber - Baby_MV.mkv" in candidates)
        assertTrue("Justin Bieber - Baby-MV.webm" in candidates)
        assertTrue("Justin Bieber - Baby-MV.mov" in candidates)
    }
}
