package com.ella.music.data

import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryResumeStoreTest {
    @Test
    fun categoryKeysStayIndependent() {
        val albumKey = CategoryResumeKeys.album(11L)
        val playlistKey = CategoryResumeKeys.playlist("favorites")
        val folderKey = CategoryResumeKeys.folder("/Music/A")

        assertNotEquals(albumKey, playlistKey)
        assertNotEquals(playlistKey, folderKey)
        assertEquals("album:11", albumKey)
        assertEquals("playlist:favorites", playlistKey)
        assertEquals("folder:/Music/A", folderKey)
        assertNotEquals(CategoryResumeKeys.HOME, CategoryResumeKeys.DASHBOARD)
    }

    @Test
    fun resumeLooksUpTheRecordedSongNotTheGloballyLatest() {
        val albumSongs = listOf(song(1, "h"), song(2, "v"))
        val folderSongs = listOf(song(1, "h"), song(2, "v"))
        val lastInAlbum = albumSongs.first().playlistIdentityKey()
        val lastInFolder: String? = null

        assertEquals(0, albumSongs.indexOfFirst { it.playlistIdentityKey() == lastInAlbum })
        assertEquals(-1, folderSongs.indexOfFirst { it.playlistIdentityKey() == lastInFolder })
    }

    private fun song(id: Long, title: String): Song = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = "/music/$title.flac",
        fileName = "$title.flac"
    )
}
