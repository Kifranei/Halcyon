package com.ella.music.data.repository
import com.ella.music.data.model.AudioInfo
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class PersistentAudioQualityCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun metadataSurvivesRestartWithoutStoringReplayGain() {
        val file = File(temporary.root, "quality.json")
        val cache = PersistentAudioQualityCache(file)
        cache.put("file:100:200", AudioInfo("FLAC", 1200000, 96000, 24, 2, -4f))
        cache.flush()
        val restored = PersistentAudioQualityCache(file)
        assertEquals(AudioInfo("FLAC", 1200000, 96000, 24, 2), restored.get("file:100:200"))
        assertNull(restored.get("file:101:200"))
    }
    @Test fun invalidatingOneFilePreservesTheRestOnDisk() {
        val file = File(temporary.root, "quality.json")
        val cache = PersistentAudioQualityCache(file)
        cache.put("a:1", AudioInfo("FLAC", sampleRate = 48000))
        cache.put("b:1", AudioInfo("MP3", sampleRate = 44100))
        cache.invalidate("a:")
        cache.flush()
        val restored = PersistentAudioQualityCache(file)
        assertNull(restored.get("a:1"))
        assertEquals("MP3", restored.get("b:1")?.format)
    }
    @Test fun corruptCacheFallsBackAndCanBeRebuilt() {
        val file = temporary.newFile("corrupt.json")
        file.writeText("truncated{")
        val cache = PersistentAudioQualityCache(file)
        assertNull(cache.get("song"))
        cache.put("song", AudioInfo("WAV", sampleRate = 48000, bitDepth = 24))
        cache.flush()
        assertEquals(24, PersistentAudioQualityCache(file).get("song")?.bitDepth)
    }
}
