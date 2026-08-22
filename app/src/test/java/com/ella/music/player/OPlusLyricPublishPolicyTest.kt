package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class OPlusLyricPublishPolicyTest {
    @Test
    fun writesWhenLyricInfoIsMissing() {
        assertEquals(
            OPlusLyricPublishAction.Write,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = null,
                currentRawLyric = null,
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]Hi"
            )
        )
    }

    @Test
    fun skipsWhenLyricInfoAlreadyMatches() {
        assertEquals(
            OPlusLyricPublishAction.None,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]Hi"
            )
        )
    }

    @Test
    fun writesWhenRawLyricChanges() {
        assertEquals(
            OPlusLyricPublishAction.Write,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]H[00:01.200]i"
            )
        )
    }

    @Test
    fun clearsWhenTargetHasNoLyrics() {
        assertEquals(
            OPlusLyricPublishAction.Clear,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = null,
                targetRawLyric = null
            )
        )
    }

    @Test
    fun skipsClearWhenAlreadyEmpty() {
        assertEquals(
            OPlusLyricPublishAction.None,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = null,
                currentRawLyric = null,
                targetLyricInfo = null,
                targetRawLyric = null
            )
        )
    }

    @Test
    fun forceWritesWhenLyricInfoAlreadyMatches() {
        val json = """{"lyric":"[00:01.00]Hi"}"""
        val raw = "[00:01.000]Hi"
        assertEquals(
            OPlusLyricPublishAction.Write,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = json,
                currentRawLyric = raw,
                targetLyricInfo = json,
                targetRawLyric = raw,
                force = true
            )
        )
    }

    @Test
    fun firstPublishUsesCachedJsonWhenOverlayBelongsToPreviousSong() {
        assertEquals(
            "cached",
            OPlusLyricPublishPolicy.presentationJson(
                songKey = "song-b",
                overlaySongKey = "song-a",
                overlayJson = "previous",
                cachedJson = "cached"
            )
        )
    }

    @Test
    fun firstPublishPrefersMatchingOverlayOverCache() {
        assertEquals(
            "overlay",
            OPlusLyricPublishPolicy.presentationJson(
                songKey = "song-a",
                overlaySongKey = "song-a",
                overlayJson = "overlay",
                cachedJson = "cached"
            )
        )
    }

    @Test
    fun firstPublishSkipsBlankCacheAndMismatchedOverlay() {
        assertEquals(
            null,
            OPlusLyricPublishPolicy.presentationJson(
                songKey = "song-b",
                overlaySongKey = "song-a",
                overlayJson = "previous",
                cachedJson = "  "
            )
        )
    }

    @Test
    fun keepsSongIdentityMetadataWhileColorOsLyricsAreEnabled() {
        assertEquals(true, OPlusLyricPublishPolicy.shouldKeepSongIdentityMetadata(true))
        assertEquals(false, OPlusLyricPublishPolicy.shouldKeepSongIdentityMetadata(false))
    }
}
