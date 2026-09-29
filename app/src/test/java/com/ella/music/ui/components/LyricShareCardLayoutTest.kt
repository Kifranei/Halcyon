package com.ella.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricShareCardLayoutTest {

    @Test
    fun shareCardScaleMatchesAppleMusicStickerWidth() {
        assertEquals(1080f / 296f, appleMusicShareCardScale(1080), 0.001f)
        assertEquals(1f, appleMusicShareCardScale(296), 0.001f)
    }

    @Test
    fun bigLyricFontFollowsAppleMusicLineAndCharacterCaps() {
        val shortLines = listOf(
            ShareLyricBlock("Winter Bells", emptyList()),
            ShareLyricBlock("白い息 凍る夜", emptyList())
        )
        assertTrue(shouldUseAppleMusicShareBigFont(shortLines))

        val fourLines = listOf(
            ShareLyricBlock("one", emptyList()),
            ShareLyricBlock("two", emptyList()),
            ShareLyricBlock("three", emptyList()),
            ShareLyricBlock("four", emptyList())
        )
        assertFalse(shouldUseAppleMusicShareBigFont(fourLines))

        val longLine = listOf(
            ShareLyricBlock("x".repeat(81), emptyList())
        )
        assertFalse(shouldUseAppleMusicShareBigFont(longLine))
    }

    @Test
    fun stickerChinAndCoverKeepAppleMusicProportions() {
        val scale = appleMusicShareCardScale(SHARE_CARD_WIDTH)
        assertEquals(84f * scale, APPLE_MUSIC_SHARE_STICKER_CHIN_HEIGHT_DP * scale, 0.01f)
        assertEquals(52f * scale, APPLE_MUSIC_SHARE_STICKER_COVER_DP * scale, 0.01f)
        assertEquals(12f * scale, APPLE_MUSIC_SHARE_STICKER_RADIUS_DP * scale, 0.01f)
    }

    @Test
    fun topMetadataStyleMatchesBottomStyleDimensions() {
        val scale = appleMusicShareCardScale(SHARE_CARD_WIDTH)
        val expectedChinHeight = APPLE_MUSIC_SHARE_STICKER_CHIN_HEIGHT_DP * scale
        val expectedCoverSize = APPLE_MUSIC_SHARE_STICKER_COVER_DP * scale
        assertEquals(84f * scale, expectedChinHeight, 0.01f)
        assertEquals(52f * scale, expectedCoverSize, 0.01f)
    }
}
