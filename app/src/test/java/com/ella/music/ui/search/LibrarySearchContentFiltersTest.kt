package com.ella.music.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchContentFiltersTest {
    @Test
    fun flagsMatchContentFilters() {
        val flags = LibrarySearchContentFlags(false, true, true, false, true)
        assertTrue(flags.matches(LibrarySearchContentFilters(noLyrics = true)))
        assertTrue(flags.matches(LibrarySearchContentFilters(musicVideo = true)))
        assertTrue(flags.matches(LibrarySearchContentFilters(localMusicVideo = true)))
        assertFalse(flags.matches(LibrarySearchContentFilters(onlineMusicVideo = true)))
        assertFalse(flags.matches(LibrarySearchContentFilters(localMusicVideo = true, onlineMusicVideo = true)))
    }

    @Test
    fun indexIntersectsActiveFilters() {
        val index = LibrarySearchContentIndex("lib", "mv", "cover", setOf("a", "b"), setOf("a"), setOf("a", "c"), setOf("a", "d"), setOf("a", "b"))
        assertEquals(setOf("a"), index.keysFor(LibrarySearchContentFilters(noLyrics = true, ttmlLyrics = true)))
        assertEquals(setOf("a"), index.keysFor(LibrarySearchContentFilters(musicVideo = true, localMusicVideo = true, onlineMusicVideo = true)))
    }
}
