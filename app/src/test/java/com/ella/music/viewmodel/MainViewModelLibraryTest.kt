package com.ella.music.viewmodel

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelLibraryTest {
    @Before
    fun setUp() {
        NameSplitConfigStore.artistCustomSeparators = listOf("/")
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
    }

    @After
    fun tearDown() {
        NameSplitConfigStore.artistCustomSeparators = emptyList()
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
    }

    @Test
    fun arrangerCategoryUsesAlbumArtistCandidateOutsideRoleGroup() {
        val arrangerSong = song(1, arranger = "Target")
        val albumArtistSong = song(2, albumArtist = "Target")

        val item = buildMetadataCategoryItems(
            songs = listOf(arrangerSong, albumArtistSong),
            type = "arranger"
        ).single()

        assertEquals(albumArtistSong.id, item.representativeSong?.id)
    }

    private fun song(id: Long, albumArtist: String = "", arranger: String = "") = Song(
        id = id,
        title = "Song $id",
        artist = "Singer",
        album = "Album $id",
        albumId = id,
        duration = 1L,
        path = "/music/$id.flac",
        fileName = "$id.flac",
        albumArtist = albumArtist,
        arranger = arranger
    )
}
