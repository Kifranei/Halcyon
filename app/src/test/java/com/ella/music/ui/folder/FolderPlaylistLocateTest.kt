package com.ella.music.ui.folder

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPlaylistLocateTest {
    @Test
    fun songRowsSitAfterTheSummaryAndContinuePlaybackHeaders() {
        assertEquals(-1, folderPlaylistSongListIndex(-1))
        assertEquals(2, folderPlaylistSongListIndex(0))
        assertEquals(7, folderPlaylistSongListIndex(5))
        assertEquals(3, folderPlaylistSongListIndex(1, headerCount = 2))
    }
}
