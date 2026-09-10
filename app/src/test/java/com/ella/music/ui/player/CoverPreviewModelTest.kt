package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverPreviewModelTest {
    @Test
    fun originalSourceWinsOverADecodedThumbnail() {
        assertEquals(
            "original",
            preferredCoverPreviewModel(originalModel = "original", decodedFallback = "thumb")
        )
    }

    @Test
    fun decodedFallbackIsUsedOnlyWhenTheOriginalIsMissing() {
        assertEquals(
            "thumb",
            preferredCoverPreviewModel(originalModel = null, decodedFallback = "thumb")
        )
        assertNull(preferredCoverPreviewModel(originalModel = null, decodedFallback = null))
    }
}
