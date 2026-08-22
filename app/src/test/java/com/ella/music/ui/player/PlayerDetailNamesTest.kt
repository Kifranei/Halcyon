package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDetailNamesTest {
    @Test
    fun hidesAliasAndTranslatedNameWhenTheyMatchTheTitleIgnoringCase() {
        assertEquals("", displayNamesDistinctFromTitle(listOf("糸"), "糸"))
        assertEquals("", displayNamesDistinctFromTitle(listOf("Halo"), "halo"))
        assertEquals("", displayNamesDistinctFromTitle(listOf("halo", "Halo"), "halo"))
        assertEquals("Night", displayNamesDistinctFromTitle(listOf("Night", "halo"), "Halo"))
        assertTrue(displayNamesDistinctFromTitle(listOf("  "), "Halo").isBlank())
    }
}
