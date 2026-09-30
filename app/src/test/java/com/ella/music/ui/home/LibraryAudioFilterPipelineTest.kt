package com.ella.music.ui.home

import com.ella.music.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import org.junit.Assert.*
import org.junit.Test

class LibraryAudioFilterPipelineTest {
    private fun song(id: Long, format: String) = Song(id, "song$id", "artist", "album", 1, 1000, "/$id.$format", "$id.$format")
    @Test fun formatSelectionChangesActualSongsAndResetRestoresAll() = runBlocking {
        val songs = listOf(song(1, "mp3"), song(2, "flac"), song(3, "mp3"))
        val results = filterLibraryAudio(songs, LibraryAudioFilter(formats = setOf("FLAC"))) { error("Known format-only filters must not block on disk metadata") }.toList()
        assertTrue(results.first().songs.isEmpty())
        assertEquals(listOf(2L), results.last().songs.map { it.id })
        assertTrue(results.last().complete)
        assertEquals(songs, filterLibraryAudio(songs, LibraryAudioFilter()) { error("Reset must not read metadata") }.toList().single().songs)
    }
    @Test fun qualityAndFormatAndHrSubgroupsFilterRealCollection() = runBlocking {
        val songs = listOf(song(1, "flac"), song(2, "flac"), song(3, "wav"), song(4, "mp3"))
        val infos = mapOf(1L to AudioInfo("FLAC", sampleRate=48000, bitDepth=24),
            2L to AudioInfo("FLAC", sampleRate=96000, bitDepth=24),
            3L to AudioInfo("WAV", sampleRate=48000, bitDepth=24))
        val filter = LibraryAudioFilter(setOf("HR"), setOf("FLAC"), setOf(24), setOf(48000))
        val result = filterLibraryAudio(songs, filter) { infos.getValue(it.id) }.toList().last()
        assertEquals(listOf(1L), result.songs.map { it.id })
        assertEquals(4, result.checked)
    }
    @Test fun m4aContainerUsesActualCodecRatherThanFilename() = runBlocking {
        val songs = listOf(song(1, "m4a"), song(2, "m4a"))
        val result = filterLibraryAudio(songs, LibraryAudioFilter(formats=setOf("ALAC"))) {
            AudioInfo(if (it.id==1L) "ALAC" else "AAC")
        }.toList().last()
        assertEquals(listOf(1L), result.songs.map { it.id })
    }
    @Test fun dolbyM4aUsesCodecAndNeverFallsIntoOther() = runBlocking {
        val songs = listOf(song(31, "m4a"), song(32, "m4a"), song(33, "m4a"), song(34, "m4a"))
        val infos = mapOf(31L to AudioInfo("audio/eac3-joc", channels = 6),
            32L to AudioInfo("AC4 A-JOC", channels = 2), 33L to AudioInfo("AAC"), 34L to AudioInfo("unknown-codec"))
        val dolby = filterLibraryAudio(songs, LibraryAudioFilter(qualities = setOf(com.ella.music.data.DOLBY_MARK))) { infos.getValue(it.id) }.toList().last()
        assertEquals(listOf(31L, 32L), dolby.songs.map { it.id })
        val ec3 = filterLibraryAudio(songs, LibraryAudioFilter(formats = setOf("EC3"))) { infos.getValue(it.id) }.toList().last()
        assertEquals(listOf(31L), ec3.songs.map { it.id })
        val other = filterLibraryAudio(songs, LibraryAudioFilter(formats = setOf(OTHER_AUDIO_FORMAT))) { infos.getValue(it.id) }.toList().last()
        assertEquals(listOf(34L), other.songs.map { it.id })
    }
    @Test fun rawDolbyExtensionUsesCanonicalCodec() = runBlocking {
        val songs = listOf(song(41, "eac3"), song(42, "ec3"), song(43, "ac4"))
        val selected = filterLibraryAudio(songs, LibraryAudioFilter(formats = setOf("EC3"))) { error("Raw codec filter should use the extension") }.toList().last()
        assertEquals(listOf(41L, 42L), selected.songs.map { it.id })
    }
    @Test fun cancellingAfterFirstCheckpointStopsReadingObsoleteSelection() = runBlocking {
        val songs = (1L..20L).map { song(it, "flac") }
        var reads = 0
        filterLibraryAudio(songs, LibraryAudioFilter(qualities=setOf("HR"))) {
            // Simulate a disk probe; an instantaneous cache loop can finish before cancellation is dispatched.
            Thread.sleep(5)
            reads++; AudioInfo("FLAC", sampleRate=48000, bitDepth=24)
        }.take(2).toList()
        assertTrue(reads < songs.size)
    }
}
