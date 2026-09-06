package com.ella.music.data.repository

import com.ella.music.data.model.Song
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtworkPolicyTest {
    @Test
    fun mp3AndOggPreferEmbeddedArtwork() {
        assertTrue(song(fileName = "a.mp3").prefersEmbeddedArtwork())
        assertTrue(song(fileName = "b.ogg").prefersEmbeddedArtwork())
        assertTrue(song(fileName = "c.flac").prefersEmbeddedArtwork())
    }

    @Test
    fun songNamedCandidatesUseTheFileStemNotFolderCover() {
        val candidates = songNamedCoverFileCandidates(
            songDirectory = File("/music/Album"),
            fileName = "Track 01.flac",
            path = "/music/Album/Track 01.flac",
            songId = 7L,
            musicThumbnailsDir = File("/music/.thumbnails")
        ).map { it.path.replace('\\', '/') }

        assertTrue(candidates.contains("/music/Album/Track 01.jpg"))
        assertTrue(candidates.contains("/music/.thumbnails/Track 01.jpg"))
        assertTrue(candidates.contains("/music/.thumbnails/7.jpg"))
        assertFalse(candidates.contains("/music/Album/cover.jpg"))
    }

    @Test
    fun folderAlbumCandidatesStayNamedAndDoNotIncludeRandomPhotos() {
        val candidates = folderAlbumCoverFileCandidates(File("/music/Album"))
            .map { it.name.lowercase() }

        assertTrue(candidates.contains("cover.jpg"))
        assertTrue(candidates.contains("folder.png"))
        assertFalse(candidates.any { it.startsWith("screenshot") })
        assertEquals(folderAlbumCoverNames.size * coverImageFileExtensions.size, candidates.size)
    }

    private fun song(fileName: String): Song = Song(
        id = 1L,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1L,
        path = "/music/$fileName",
        fileName = fileName
    )
}
