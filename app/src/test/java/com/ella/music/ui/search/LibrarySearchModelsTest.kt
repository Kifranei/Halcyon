package com.ella.music.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchModelsTest {
    @Test
    fun musicVideoFilterIsASongResultFilter() {
        assertEquals(SearchFilter.MusicVideos, SearchFilter.fromRouteType("mv"))
        assertEquals(SearchFilter.MusicVideos, SearchFilter.fromRouteType("musicvideo"))
        assertTrue(SearchFilter.MusicVideos.acceptsSongResults)
    }
}
