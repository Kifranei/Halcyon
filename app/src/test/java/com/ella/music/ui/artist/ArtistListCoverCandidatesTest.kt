package com.ella.music.ui.artist

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ArtistListCoverCandidatesTest {
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
    fun artistImageCandidatesIncludeAlbumArtistSongsWhenAlbumArtistsAreHidden() {
        val songArtistCandidate = song(id = 1, artist = "Target", albumArtist = "Other")
        val albumArtistCandidate = song(id = 2, artist = "Singer", albumArtist = "Target")

        val candidates = buildArtistCoverCandidates(listOf(songArtistCandidate, albumArtistCandidate))

        assertEquals(
            albumArtistCandidate,
            selectArtistCoverSong(candidates.getValue("Target"), "Target")
        )
    }

    private fun song(id: Long, artist: String, albumArtist: String): Song = Song(
        id = id,
        path = "/music/$id.mp3",
        fileName = "$id.mp3",
        title = "Track $id",
        artist = artist,
        album = "Album",
        albumId = id,
        albumArtist = albumArtist,
        duration = 180_000L,
        dateModified = id,
        fileSize = 1_024L
    )
}
