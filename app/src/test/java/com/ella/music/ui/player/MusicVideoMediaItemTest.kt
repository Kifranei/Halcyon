package com.ella.music.ui.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicVideoMediaItemTest {

    @Test
    fun `infers supported video containers from ordinary and SAF paths`() {
        assertEquals(
            MimeTypes.VIDEO_MATROSKA,
            inferMusicVideoContainerMimeType(
                "content://provider/tree/primary%3AMVs/document/primary%3AMVs%2Fsong.MKV"
            )
        )
        assertEquals(
            MimeTypes.VIDEO_WEBM,
            inferMusicVideoContainerMimeType("file:///storage/emulated/0/MVs/song.webm")
        )
        assertEquals(
            MimeTypes.VIDEO_QUICK_TIME,
            inferMusicVideoContainerMimeType("file:///storage/emulated/0/MVs/song.mov?token=1")
        )
        assertEquals(
            MimeTypes.VIDEO_MP4,
            inferMusicVideoContainerMimeType("file:///storage/emulated/0/MVs/song.m4v")
        )
    }

    @Test
    fun `uses a meaningful provider type and falls back from octet stream`() {
        assertEquals(
            "video/custom",
            inferMusicVideoContainerMimeType("content://provider/video.bin", "video/custom; charset=utf-8")
        )
        assertEquals(
            MimeTypes.VIDEO_MATROSKA,
            inferMusicVideoContainerMimeType("content://provider/video.mkv", "application/octet-stream")
        )
        assertNull(inferMusicVideoContainerMimeType("content://provider/video.unknown"))
    }
}
