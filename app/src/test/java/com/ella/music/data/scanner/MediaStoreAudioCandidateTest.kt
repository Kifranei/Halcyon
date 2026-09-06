package com.ella.music.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreAudioCandidateTest {
    @Test
    fun saltStyleScanKeepsIndexedAudioByExtensionAndSize() {
        assertTrue(isMediaStoreAudioCandidate("/storage/emulated/0/Music/a.flac", 2_048L))
        assertTrue(isMediaStoreAudioCandidate("/storage/emulated/0/Music/a.mp3", MEDIA_STORE_MIN_AUDIO_BYTES))
        assertTrue(isMediaStoreAudioCandidate("/storage/emulated/0/Music/a.dsf", 4_096L))
    }

    @Test
    fun saltStyleScanDropsTinyOrUnknownFiles() {
        assertFalse(isMediaStoreAudioCandidate("/storage/emulated/0/Music/a.flac", 999L))
        assertFalse(isMediaStoreAudioCandidate("", 2_048L))
        assertFalse(isMediaStoreAudioCandidate("/storage/emulated/0/Music/note.txt", 2_048L))
        assertFalse(isMediaStoreAudioCandidate("/storage/emulated/0/Music/voice.amr", 2_048L))
    }
}
