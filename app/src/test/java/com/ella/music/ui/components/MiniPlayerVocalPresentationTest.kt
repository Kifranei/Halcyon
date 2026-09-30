package com.ella.music.ui.components

import com.ella.music.data.model.LyricLine
import org.junit.Assert.*
import org.junit.Test

class MiniPlayerVocalPresentationTest {
    @Test fun secondVocalAlignsRightAndBackingTextReplacesTranslation() {
        val line = LyricLine(0, "Lead", translation = "Translation", agent = "v2", backgroundText = "Harmony")
        assertTrue(miniPlayerAlignEnd(line.agent))
        assertEquals("Harmony", miniPlayerBackingText(line) ?: line.translation)
        assertFalse(miniPlayerAlignEnd("v1"))
        assertFalse(miniPlayerAlignEnd(null))
    }
    @Test fun emptyOrMusicalBackingLayerKeepsOrdinarySecondaryText() {
        assertNull(miniPlayerBackingText(LyricLine(0, "Lead", backgroundText = "♪ …")))
        assertNull(miniPlayerBackingText(null))
    }
}
