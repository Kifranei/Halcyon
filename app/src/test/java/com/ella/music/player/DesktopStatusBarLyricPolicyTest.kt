package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStatusBarLyricPolicyTest {

    @Test
    fun dashSpaceCreditsStayOrdinarySecondaryTextInDesktopAndStatusBarLyrics() {
        for (candidate in listOf("- harmoe", "  - harmoe", "- ka ze", "- chūn xiāo", "- ハルモエ", "ka ze\n- harmoe")) {
            assertFalse(candidate, isLikelyRomanizationSecondary("ふたりピノキオ", candidate))
            assertFalse(candidate, isLikelyRomanizationSecondary("風が変わっても", candidate))
        }
        assertTrue(isLikelyRomanizationSecondary("風が変わっても", "ka ze ga ka wat te mo"))
        assertTrue(isLikelyRomanizationSecondary("風が変わっても", "ka-ze ga ka-wat-te-mo"))
    }

    @Test
    fun secondaryWhitespaceNormalizationKeepsOneVisualRun() {
        assertEquals(
            "wa ta shi ni ai sare tai",
            "  wa   ta shi\nni\t ai sare tai  ".normalizeDesktopStatusBarSecondaryText()
        )
    }
}
