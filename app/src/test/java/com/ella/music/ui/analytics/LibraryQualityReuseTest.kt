package com.ella.music.ui.analytics
import com.ella.music.data.model.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class LibraryQualityReuseTest {
    private fun song(id: Long) = Song(id, "track", "artist", "album", 1L, 1000L, "/$id.flac", "$id.flac", 1024L, dateModified = 10L)
    @Test fun libraryCacheIdentitySurvivesSortingAndChangesWithContent() {
        val songs = listOf(song(1), song(2))
        assertEquals(songs.libraryAnalysisCacheKey(), songs.reversed().libraryAnalysisCacheKey())
        assertNotEquals(songs.libraryAnalysisCacheKey(), listOf(songs[0].copy(path = "/other.flac"), songs[1]).libraryAnalysisCacheKey())
        assertNotEquals(songs.libraryAnalysisCacheKey(), listOf(songs[0].copy(dateModified = 11L), songs[1]).libraryAnalysisCacheKey())
    }
    @Test fun coldProbesRunInParallelWithBoundedWorkersAndPreserveOrder() = runBlocking {
        val active = AtomicInteger(); val peak = AtomicInteger(); val reads = AtomicInteger()
        val songs = (1L..24L).map(::song)
        val rows = loadLibraryQualityRows(songs) {
            val count = active.incrementAndGet(); peak.updateAndGet { maxOf(it, count) }
            try { Thread.sleep(8); reads.incrementAndGet(); AudioInfo("FLAC", sampleRate = 48000) }
            finally { active.decrementAndGet() }
        }
        assertEquals(songs, rows.map { it.song })
        assertEquals(24, reads.get())
        assertTrue(peak.get() in 2..4)
    }
}
