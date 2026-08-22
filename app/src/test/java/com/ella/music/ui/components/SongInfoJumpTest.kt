package com.ella.music.ui.components

import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class SongInfoJumpTest {
    private val song = Song(
        id = 9L,
        title = "糸",
        artist = "中島みゆき / Guest",
        album = "グッバイガール",
        albumId = 42L,
        duration = 1_000L,
        path = "/storage/emulated/0/Music/中島みゆき - 糸.flac",
        fileName = "中島みゆき - 糸.flac",
        albumArtist = "中島みゆき",
        genre = "J-Pop",
        year = "1998-10-07",
        composer = "中島みゆき",
        arranger = "瀬尾一三",
        lyricist = "中島みゆき"
    )

    @Test
    fun pathOpensFolderCategoryDetailAndDirectoryOpensFolderTree() {
        assertEquals(
            Screen.MetadataCategoryDetail.createRoute("folder", "/storage/emulated/0/Music"),
            songInfoJumpRoute(SongInfoJump.Path, song)
        )
        assertEquals(
            Screen.FolderDetail.createRoute("/storage/emulated/0/Music"),
            songInfoJumpRoute(SongInfoJump.Directory, song)
        )
    }

    @Test
    fun formatAndBitrateOpenMatchingAnalysisBuckets() {
        val info = AudioInfo(format = "FLAC", bitRate = 1_411_000, sampleRate = 44_100, bitDepth = 16)
        assertEquals(
            Screen.LibraryAnalysis.createBucketRoute(false, "FLAC"),
            songInfoJumpRoute(SongInfoJump.Format, song, info)
        )
        assertEquals(
            Screen.LibraryAnalysis.createBucketRoute(true, "LOSSLESS"),
            songInfoJumpRoute(SongInfoJump.Bitrate, song, info)
        )
    }

    @Test
    fun creditsOpenArtistOrMetadataCategoryPages() {
        assertEquals(
            Screen.ArtistDetail.createRoute("中島みゆき"),
            songInfoJumpRoute(SongInfoJump.Artist, song, chosenValue = "中島みゆき")
        )
        assertEquals(
            Screen.AlbumDetail.createRoute(song.albumIdentityId()),
            songInfoJumpRoute(SongInfoJump.Album, song)
        )
        assertEquals(
            Screen.MetadataCategoryDetail.createRoute("year", "1998"),
            songInfoJumpRoute(SongInfoJump.Year, song, chosenValue = song.year)
        )
        assertEquals(
            Screen.MetadataCategoryDetail.createRoute("arranger", "瀬尾一三"),
            songInfoJumpRoute(SongInfoJump.Arranger, song, chosenValue = song.arranger)
        )
    }

    @Test
    fun splitArtistChoicesFollowConfiguredSeparators() {
        assertEquals(
            listOf("中島みゆき / Guest"),
            songInfoJumpChoices(SongInfoJump.Artist, song.artist)
        )
        assertEquals(
            listOf("J-Pop"),
            songInfoJumpChoices(SongInfoJump.Genre, song.genre)
        )
    }
}
