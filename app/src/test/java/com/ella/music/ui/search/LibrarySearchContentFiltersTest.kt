package com.ella.music.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchContentFiltersTest {
    @Test
    fun noLyricsFilterDoesNotRequireDynamicCoverOrMv() {
        val noLyrics = LibrarySearchContentFlags(
            hasLyrics = false,
            hasTtmlLyrics = false,
            hasLocalMusicVideo = false,
            hasOnlineMusicVideo = false,
            hasDynamicCover = false
        )
        assertTrue(noLyrics.matches(LibrarySearchContentFilters(noLyrics = true)))
        assertFalse(
            noLyrics.copy(hasLyrics = true).matches(LibrarySearchContentFilters(noLyrics = true))
        )
    }

    @Test
    fun mvTabKeepsLocalOrOnlineWithoutCover() {
        val localOnly = LibrarySearchContentFlags(
            hasLyrics = true,
            hasTtmlLyrics = false,
            hasLocalMusicVideo = true,
            hasOnlineMusicVideo = false,
            hasDynamicCover = false
        )
        assertTrue(localOnly.matches(LibrarySearchContentFilters(musicVideo = true)))
        assertTrue(localOnly.matches(LibrarySearchContentFilters(localMusicVideo = true)))
        assertFalse(localOnly.matches(LibrarySearchContentFilters(onlineMusicVideo = true)))
        assertFalse(
            localOnly.matches(
                LibrarySearchContentFilters(localMusicVideo = true, onlineMusicVideo = true)
            )
        )
    }

    @Test
    fun dynamicCoverIsOnlyRequiredWhenThatFilterIsOn() {
        val song = LibrarySearchContentFlags(
            hasLyrics = false,
            hasTtmlLyrics = false,
            hasLocalMusicVideo = true,
            hasOnlineMusicVideo = true,
            hasDynamicCover = false
        )
        assertTrue(song.matches(LibrarySearchContentFilters(noLyrics = true)))
        assertTrue(song.matches(LibrarySearchContentFilters(musicVideo = true)))
        assertFalse(song.matches(LibrarySearchContentFilters(dynamicCover = true)))
    }
}
