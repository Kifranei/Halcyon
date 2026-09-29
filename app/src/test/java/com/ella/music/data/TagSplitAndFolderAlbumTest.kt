package com.ella.music.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for #675 (custom separators ignored, tag album shown as Unknown Album). */
class TagSplitAndFolderAlbumTest {
    @After
    fun tearDown() {
        NameSplitConfigStore.artistCustomSeparators = emptyList()
        NameSplitConfigStore.artistProtectedNames = emptyList()
    }

    @Test
    fun tagAlbumEqualToFolderNameIsKeptWhenFolderFallbackIsOff() {
        assertEquals(
            "Album",
            LibraryNormalizer.resolveLibraryAlbum(
                album = "Album",
                path = "/music/Artist/Album/01.flac",
                folderNameAsAlbumWhenMissing = false,
                albumFromTag = true
            )
        )
    }

    @Test
    fun inferredFolderAlbumStillCountsAsMissingWhenFolderFallbackIsOff() {
        assertEquals(
            "Unknown Album",
            LibraryNormalizer.resolveLibraryAlbum(
                album = "Album",
                path = "/music/Artist/Album/01.flac",
                folderNameAsAlbumWhenMissing = false,
                albumFromTag = false
            )
        )
        assertEquals(
            "Album",
            LibraryNormalizer.resolveLibraryAlbum(
                album = "",
                path = "/music/Artist/Album/01.flac",
                folderNameAsAlbumWhenMissing = true,
                albumFromTag = false
            )
        )
    }

    @Test
    fun separatorSettingAcceptsSeveralSymbolsOnOneLine() {
        assertEquals(listOf(";", ","), parseNameSeparatorSetting("; ,"))
        assertEquals(listOf(";", ",", "/"), parseNameSeparatorSetting("; ,\n/"))
    }

    @Test
    fun separatorSettingKeepsExistingOnePerLineValues() {
        assertEquals(
            listOf("/", "feat.", "&", ",", "、"),
            parseNameSeparatorSetting(SettingsManager.DEFAULT_ARTIST_SEPARATORS)
        )
        // Lines with letters stay literal, so multi-character word separators survive.
        assertEquals(listOf("feat. x"), parseNameSeparatorSetting("feat. x"))
        assertEquals(listOf("x"), parseNameSeparatorSetting(" x "))
    }

    @Test
    fun protectedNamesKeepInnerSpaces() {
        assertEquals(listOf("Simon & Garfunkel"), parseNameSplitSetting("Simon & Garfunkel"))
    }

    @Test
    fun alphabeticSeparatorOnlyMatchesWholeWords() {
        NameSplitConfigStore.artistCustomSeparators = listOf("and")

        assertEquals(listOf("Sandra"), splitArtistNames("Sandra"))
        assertEquals(listOf("A", "B"), splitArtistNames("A and B"))
        assertEquals(listOf("Sandra", "Andy"), splitArtistNames("Sandra AND Andy"))
    }

    @Test
    fun symbolAndCjkSeparatorsStillMatchInline() {
        NameSplitConfigStore.artistCustomSeparators = listOf(";", "和", "feat.")

        assertEquals(listOf("A", "B"), splitArtistNames("A;B"))
        assertEquals(listOf("周杰伦", "蔡依林"), splitArtistNames("周杰伦和蔡依林"))
        assertEquals(listOf("A", "B"), splitArtistNames("A feat. B"))
        assertEquals(listOf("Defeat.Me"), splitArtistNames("Defeat.Me"))
    }

    @Test
    fun notifyChangedBumpsRevision() {
        val before = NameSplitConfigStore.revision.value
        NameSplitConfigStore.notifyChanged()
        assertTrue(NameSplitConfigStore.revision.value > before)
    }
}
