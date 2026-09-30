package com.ella.music.video

import org.junit.Assert.*
import org.junit.Test

class DirectVideoSourceTest {
    @Test fun localMp4AndExtensionlessProviderVideoEnterPlaybackDirectly() {
        assertTrue(isDirectVideoSource("file:///storage/emulated/0/Movies/local video.mp4", null))
        assertTrue(isDirectVideoSource("content://documents/document/123", "video/mp4"))
        assertTrue(isDirectVideoSource("https://cdn.example/video.mp4?token=x", null))
    }
    @Test fun playlistsStillResolveAndUnsupportedSchemesDoNotPlay() {
        assertFalse(isDirectVideoSource("https://cdn.example/index.m3u8", "video/mp2t"))
        assertFalse(isDirectVideoSource("content://documents/123", "application/x-mpegurl"))
        assertFalse(isDirectVideoSource("javascript:demo.mp4", "video/mp4"))
    }
}
