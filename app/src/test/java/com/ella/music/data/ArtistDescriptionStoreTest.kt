package com.ella.music.data

import com.ella.music.data.model.Song
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.io.path.deleteIfExists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArtistDescriptionStoreTest {
    private lateinit var root: File
    private lateinit var store: ArtistDescriptionStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("artist-description").toFile()
        store = ArtistDescriptionStore(File(root, "internal/descriptions.properties"))
    }

    @After
    fun tearDown() {
        if (!root.exists()) return
        Files.walk(root.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { it.deleteIfExists() }
        }
    }

    @Test
    fun artistNamedFolderUsesNfoAndPreservesExistingFields() {
        val artistFolder = File(root, "Music/Eason Chan").apply { mkdirs() }
        val albumFolder = File(artistFolder, "U87").apply { mkdirs() }
        val audio = File(albumFolder, "01.flac").apply { writeText("") }
        File(artistFolder, "artist.nfo").writeText(
            """
            <artist>
                <name>Eason Chan</name>
                <genre>Pop</genre>
            </artist>
            """.trimIndent()
        )
        val songs = listOf(testSong(audio, "Eason Chan"))

        assertEquals(
            ArtistDescriptionSaveResult.SAVED_TO_NFO,
            store.save("Eason Chan", songs, "Hong Kong singer and actor.")
        )

        val nfo = File(artistFolder, "artist.nfo").readText()
        assertTrue(nfo.contains("<genre>Pop</genre>"))
        assertTrue(nfo.contains("<biography>Hong Kong singer and actor.</biography>"))
        assertEquals(
            ArtistDescriptionRecord(
                text = "Hong Kong singer and actor.",
                storage = ArtistDescriptionStorage.NFO
            ),
            store.load("Eason Chan", songs)
        )
    }

    @Test
    fun existingNfoBiographyHasPriorityOverLocalFallback() {
        val artistFolder = File(root, "Music/Eason Chan").apply { mkdirs() }
        val audio = File(artistFolder, "01.flac").apply { writeText("") }
        val songs = listOf(testSong(audio, "Eason Chan"))
        File(root, "internal/descriptions.properties").apply {
            parentFile?.mkdirs()
            writer().use { writer ->
                Properties().apply {
                    setProperty(
                        ArtistDescriptionStore.artistDescriptionKey("Eason Chan", songs),
                        "Stale local fallback"
                    )
                }.store(writer, null)
            }
        }
        File(artistFolder, "artist.nfo").writeText(
            "<artist><biography>Externally edited biography</biography></artist>"
        )

        assertEquals(
            "Externally edited biography",
            store.load("Eason Chan", songs).text
        )
    }

    @Test
    fun mixedAlbumFoldersWithoutArtistDirectoryStayLocal() {
        val first = File(root, "Music/U87").apply { mkdirs() }
        val second = File(root, "Music/The Key").apply { mkdirs() }
        val songs = listOf(
            testSong(File(first, "01.flac").apply { writeText("") }, "Eason Chan"),
            testSong(File(second, "01.flac").apply { writeText("") }, "Eason Chan")
        )

        assertNull(store.nfoFileFor("Eason Chan", songs))
        assertEquals(
            ArtistDescriptionSaveResult.SAVED_LOCALLY,
            store.save("Eason Chan", songs, "Local-only biography")
        )
        assertEquals(ArtistDescriptionStorage.LOCAL, store.load("Eason Chan", songs).storage)
        assertFalse(File(root, "Music/artist.nfo").exists())
    }

    @Test
    fun albumSubfoldersShareOneArtistNfo() {
        val artistFolder = File(root, "Music/Eason Chan")
        val first = File(artistFolder, "U87").apply { mkdirs() }
        val second = File(artistFolder, "The Key").apply { mkdirs() }
        val songs = listOf(
            testSong(File(first, "01.flac").apply { writeText("") }, "Eason Chan"),
            testSong(File(second, "01.flac").apply { writeText("") }, "Eason Chan")
        )

        assertEquals(
            File(artistFolder, "artist.nfo").canonicalFile,
            store.nfoFileFor("Eason Chan", songs)?.canonicalFile
        )
    }

    @Test
    fun remoteArtistFallsBackToInternalStorageAndCanBeCleared() {
        val songs = listOf(testSong(File("https://example.test/song.flac"), "Eason Chan"))

        assertEquals(
            ArtistDescriptionSaveResult.SAVED_LOCALLY,
            store.save("Eason Chan", songs, "Remote biography")
        )
        assertEquals(ArtistDescriptionStorage.LOCAL, store.load("Eason Chan", songs).storage)

        assertEquals(ArtistDescriptionSaveResult.CLEARED, store.save("Eason Chan", songs, "  "))
        assertEquals(ArtistDescriptionRecord(), store.load("Eason Chan", songs))
        assertFalse(File(root, "internal/descriptions.properties").exists())
    }

    private fun testSong(file: File, artist: String) = Song(
        id = 1L,
        title = "Song",
        artist = artist,
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = file.path,
        fileName = file.name,
        albumArtist = artist
    )
}
