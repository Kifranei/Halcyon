package com.ella.music.ui.components

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MetadataCategoryCoverResolverTest {
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
    fun arrangerCoverUsesTheFourLevelPriority() {
        val collaborativeArranger = song(1, arranger = "Target / Guest")
        val collaborativeAlbumArtist = song(2, albumArtist = "Target / Guest")
        val soleArranger = song(3, arranger = "Target")
        val soleAlbumArtist = song(4, albumArtist = "Target")

        assertEquals(
            soleAlbumArtist,
            selectMetadataCategoryCoverSong(
                listOf(collaborativeArranger, collaborativeAlbumArtist, soleArranger, soleAlbumArtist),
                "arranger",
                "Target"
            )
        )
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
