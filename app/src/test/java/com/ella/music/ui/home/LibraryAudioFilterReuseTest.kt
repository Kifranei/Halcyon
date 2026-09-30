package com.ella.music.ui.home
import com.ella.music.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.junit.Test
import org.junit.Assert.*

class LibraryAudioFilterReuseTest {
    @Test fun repeatSelectionUsesCompletedResultsButMetadataEditsInvalidateThem() = runBlocking {
        val songs = listOf(Song(987654, "reuse", "artist", "album", 1, 1000, "/reuse.flac", "reuse.flac", dateModified = 1))
        val filter = LibraryAudioFilter(qualities = setOf("HR"))
        val revision = System.nanoTime()
        var reads = 0
        val first = filterLibraryAudio(songs, filter, revision) { reads++; AudioInfo("FLAC", sampleRate = 96000, bitDepth = 24) }.toList()
        assertEquals(songs, first.last().songs)
        val cached = filterLibraryAudio(songs, filter, revision) { error("Repeat selection must reuse results") }.toList()
        assertEquals(songs, cached.single().songs)
        assertEquals(1, reads)
        val invalidated = filterLibraryAudio(songs, filter, revision + 1) { reads++; AudioInfo("MP3", bitRate = 128000) }.toList()
        assertTrue(invalidated.last().songs.isEmpty())
        assertEquals(2, reads)
    }
}
