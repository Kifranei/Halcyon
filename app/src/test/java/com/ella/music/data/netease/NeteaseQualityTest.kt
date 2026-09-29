package com.ella.music.data.netease

import org.junit.Assert.*
import org.junit.Test

class NeteaseQualityTest {
    @Test fun automaticStartsAtMasterAndDoesNotForceSpatialMixes() {
        val levels = neteaseQualityLevels("auto")
        assertEquals("jymaster", levels.first())
        assertEquals("standard", levels.last())
        assertFalse(levels.any { it in listOf("sky", "vivid", "jyeffect") })
        assertEquals(levels, levels.distinct())
    }
    @Test fun manualChoiceNeverFallsBackToAHigherStereoTier() {
        assertEquals(listOf("lossless", "exhigh", "standard"), neteaseQualityLevels("lossless"))
        assertEquals(listOf("standard"), neteaseQualityLevels("standard"))
        assertEquals("vivid", neteaseQualityLevels("vivid").first())
        assertEquals(neteaseQualityLevels("auto"), neteaseQualityLevels("invalid"))
    }
    @Test fun highQualityRequestsAreNotPinnedToStandardMp3() {
        val request = neteaseQualityRequest("123", "hires")
        assertEquals("hires", request.getString("level"))
        assertEquals("flac", request.getString("encodeType"))
        assertEquals("[123]", request.getString("ids"))
        assertEquals("c51", neteaseQualityRequest("123", "sky").getString("immerseType"))
        assertEquals("mp3", neteaseQualityRequest("123", "vivid").getString("encodeType"))
    }
    @Test fun servedStreamDrivesBadgeInsteadOfGenericAudio() {
        val hires = neteaseStreamAudioInfo(NeteaseStreamInfo("https://m.music.126.net/x.flac", "hires", "flac", 2_000_000, 96_000))
        assertEquals("FLAC", hires.format)
        assertEquals(24, hires.bitDepth)
        assertEquals("Hi-Res", com.ella.music.data.audioQualitySummary(hires).compactLabel)
        val lossless = neteaseStreamAudioInfo(NeteaseStreamInfo("https://m.music.126.net/x.flac", "lossless", "flac", 900_000, 44_100))
        assertEquals("Lossless", com.ella.music.data.audioQualitySummary(lossless).compactLabel)
        val mp3 = neteaseStreamAudioInfo(NeteaseStreamInfo("https://m.music.126.net/x.mp3", "exhigh", "mp3", 320_000, 44_100))
        assertEquals("HQ", com.ella.music.data.audioQualitySummary(mp3).compactLabel)
        assertEquals(0, mp3.bitDepth)
    }
}
