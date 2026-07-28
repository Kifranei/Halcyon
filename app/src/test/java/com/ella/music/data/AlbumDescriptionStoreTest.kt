package com.ella.music.data

import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.io.path.deleteIfExists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlbumDescriptionStoreTest {
    private lateinit var root: File
    private lateinit var store: AlbumDescriptionStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("album-description").toFile()
        store = AlbumDescriptionStore(File(root, "internal/descriptions.properties"))
    }

    @After
    fun tearDown() {
        if (!root.exists()) return
        Files.walk(root.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { it.deleteIfExists() }
        }
    }

    @Test
    fun writableAlbumFolderUsesNfoAndPreservesExistingFields() {
        val albumFolder = File(root, "Music/Test Album").apply { mkdirs() }
        val audio = File(albumFolder, "01 Song.flac").apply { writeText("") }
        File(albumFolder, "album.nfo").writeText(
            """
            <album>
                <title>Test Album</title>
                <genre>Pop</genre>
            </album>
            """.trimIndent()
        )
        val album = testAlbum()
        val songs = listOf(testSong(audio))

        assertEquals(
            AlbumDescriptionSaveResult.SAVED_TO_NFO,
            store.save(album, songs, "A local album introduction.")
        )

        val nfo = File(albumFolder, "album.nfo").readText()
        assertTrue(nfo.contains("<genre>Pop</genre>"))
        assertTrue(nfo.contains("<review>A local album introduction.</review>"))
        assertEquals(
            AlbumDescriptionRecord(
                text = "A local album introduction.",
                storage = AlbumDescriptionStorage.NFO
            ),
            store.load(album, songs)
        )
    }

    @Test
    fun existingNfoReviewHasPriorityOverLocalFallback() {
        val albumFolder = File(root, "Music/Test Album").apply { mkdirs() }
        val audio = File(albumFolder, "01 Song.flac").apply { writeText("") }
        val album = testAlbum()
        val songs = listOf(testSong(audio))
        File(root, "internal/descriptions.properties").apply {
            parentFile?.mkdirs()
            writer().use { writer ->
                Properties().apply {
                    setProperty(
                        AlbumDescriptionStore.albumDescriptionKey(album, songs),
                        "Stale local fallback"
                    )
                }.store(writer, null)
            }
        }
        File(albumFolder, "album.nfo").writeText(
            "<album><review>Externally edited review</review></album>"
        )

        assertEquals(
            "Externally edited review",
            store.load(album, songs).text
        )
    }

    @Test
    fun remoteAlbumFallsBackToInternalStorageAndCanBeCleared() {
        val album = testAlbum()
        val songs = listOf(testSong(File("https://example.test/song.flac")))

        assertEquals(
            AlbumDescriptionSaveResult.SAVED_LOCALLY,
            store.save(album, songs, "Remote introduction")
        )
        assertEquals(AlbumDescriptionStorage.LOCAL, store.load(album, songs).storage)

        assertEquals(AlbumDescriptionSaveResult.CLEARED, store.save(album, songs, "  "))
        assertEquals(AlbumDescriptionRecord(), store.load(album, songs))
        assertFalse(File(root, "internal/descriptions.properties").exists())
    }

    @Test
    fun numberedDiscFoldersShareOneAlbumNfo() {
        val albumFolder = File(root, "Music/Test Album")
        val discOne = File(albumFolder, "CD1").apply { mkdirs() }
        val discTwo = File(albumFolder, "Disc 2").apply { mkdirs() }
        val songs = listOf(
            testSong(File(discOne, "01.flac").apply { writeText("") }),
            testSong(File(discTwo, "01.flac").apply { writeText("") })
        )

        assertEquals(File(albumFolder, "album.nfo").canonicalFile, store.nfoFileFor(songs)?.canonicalFile)
        assertEquals(
            AlbumDescriptionSaveResult.SAVED_TO_NFO,
            store.save(testAlbum(), songs, "Multi-disc introduction")
        )
        assertTrue(File(albumFolder, "album.nfo").isFile)
    }

    private fun testAlbum() = Album(
        id = 1L,
        name = "Test Album",
        artist = "Test Artist",
        albumArtist = "Test Artist",
        songCount = 1,
        year = "2026"
    )

    private fun testSong(file: File) = Song(
        id = 1L,
        title = "Song",
        artist = "Test Artist",
        album = "Test Album",
        albumId = 1L,
        duration = 180_000L,
        path = file.path,
        fileName = file.name,
        albumArtist = "Test Artist",
        year = "2026"
    )
}
