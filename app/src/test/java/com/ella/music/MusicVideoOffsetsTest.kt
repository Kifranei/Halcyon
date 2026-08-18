package com.ella.music

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MusicVideoOffsetsTest {
    @Test
    fun parsesSecondsAndMatchesBasenameCaseInsensitively() {
        val offsets = MusicVideoOffsetsParser.parse("""{"version":1,"unit":"seconds","offsets":{"Folder/Video.MP4":-7.8,"other.mp4":27}}""")
        assertEquals(-7800L, offsets.forFileName("/storage/emulated/0/Music/video.mp4"))
        assertEquals(27000L, offsets.forFileName("/storage/emulated/0/Music/other.mp4"))
    }

    @Test
    fun matchesSafDocumentUrisAndMvSuffixes() {
        val offsets = MusicVideoOffsetsParser.parse(
            """{"unit":"seconds","offsets":{"Music/My Song.flac":2.5}}"""
        )

        assertEquals(
            2500L,
            offsets.forFileName("/document/primary%3AMovies%2FMy%20Song_MV.mp4")
        )
    }

    @Test
    fun parsesMillisecondsAndObjectEntries() {
        val offsets = MusicVideoOffsetsParser.parse(
            """{"unit":"milliseconds","items":[{"fileName":"clip.mp4","delay":1250},{"path":"other.mov","offsetSeconds":-1.25}]}"""
        )

        assertEquals(1250L, offsets.forFileName("clip.mp4"))
        assertEquals(-1250L, offsets.forFileName("other.mov"))
    }

    @Test
    fun rejectsFilesWithoutUsableOffsets() {
        try {
            MusicVideoOffsetsParser.parse("""{"version":1,"offsets":{}}""")
            fail("Expected an invalid empty offset file")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun convertsAudioClockToDelayedMvClockWithoutClampingPreroll() {
        assertEquals(-1500L, musicVideoSyncPositionMs(audioPositionMs = 500L, mvDelayMs = 2000L))
        assertEquals(3500L, musicVideoSyncPositionMs(audioPositionMs = 1500L, mvDelayMs = -2000L))
    }
}
