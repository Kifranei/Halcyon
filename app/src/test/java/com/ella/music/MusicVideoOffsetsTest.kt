package com.ella.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicVideoOffsetsTest {
    @Test
    fun parsesSecondsAndMatchesBasenameCaseInsensitively() {
        val offsets = MusicVideoOffsetsParser.parse("""{"version":1,"unit":"seconds","offsets":{"Folder/Video.MP4":-7.8,"other.mp4":27}}""")
        assertEquals(-7800L, offsets.forFileName("/storage/emulated/0/Music/video.mp4"))
        assertEquals(27000L, offsets.forFileName("/storage/emulated/0/Music/other.mp4"))
    }
}
