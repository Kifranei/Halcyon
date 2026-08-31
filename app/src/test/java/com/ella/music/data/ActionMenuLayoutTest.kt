package com.ella.music.data

import com.ella.music.ui.components.parseSongDateTime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMenuLayoutTest {
    private val defaults = listOf("a", "b", "c")

    @Test
    fun `saved order and hidden actions stay independent`() {
        val layout = ActionMenuLayout.parse("c,a,b;b", defaults)
        assertEquals(listOf("c", "a", "b"), layout.order)
        assertEquals(listOf("c", "a"), layout.visibleIds(defaults))
    }

    @Test
    fun `new actions are appended without reviving hidden actions`() {
        val layout = ActionMenuLayout.parse("b,a;a", defaults)
        assertEquals(listOf("b", "a", "c"), layout.order)
        assertTrue("a" in layout.hidden)
        assertFalse("c" in layout.hidden)
    }

    @Test
    fun `layout serialization round trips`() {
        val original = ActionMenuLayout(listOf("c", "a", "b"), setOf("a", "c"))
        assertEquals(original, ActionMenuLayout.parse(original.serialize(), defaults))
    }

    @Test
    fun songInfoAndQueueLayoutsKeepHiddenFields() {
        val info = ActionMenuLayout.parse(
            "${ActionMenuIds.SONG_INFO_TITLE},${ActionMenuIds.SONG_INFO_ARTIST};${ActionMenuIds.SONG_INFO_PATH}",
            ActionMenuIds.songInfoDefaults
        )
        assertEquals(ActionMenuIds.SONG_INFO_TITLE, info.order.first())
        assertTrue(ActionMenuIds.SONG_INFO_PATH in info.hidden)
        assertTrue(ActionMenuIds.SONG_INFO_TITLE in info.visibleIds(ActionMenuIds.songInfoDefaults))
        assertFalse(ActionMenuIds.SONG_INFO_PATH in info.visibleIds(ActionMenuIds.songInfoDefaults))

        val queue = ActionMenuLayout.parse(
            "${ActionMenuIds.QUEUE_CLEAR},${ActionMenuIds.QUEUE_LOCK};${ActionMenuIds.QUEUE_SHUFFLE}",
            ActionMenuIds.queueToolbarDefaults
        )
        assertEquals(ActionMenuIds.QUEUE_CLEAR, queue.order.first())
        assertTrue(ActionMenuIds.QUEUE_SHUFFLE in queue.hidden)
    }

    @Test
    fun songModifiedTimeParsesTheDisplayedFormat() {
        val parsed = parseSongDateTime("2026-08-26 06:30:15")
        assertTrue(parsed != null && parsed > 0L)
        assertEquals(null, parseSongDateTime("not-a-date"))
    }

    @Test
    fun `casting action is inserted beside audio output for an existing saved layout`() {
        val playerDefaults = listOf(
            ActionMenuIds.ADD_TO_QUEUE,
            ActionMenuIds.AUDIO_OUTPUT,
            ActionMenuIds.CASTING,
            ActionMenuIds.AB_REPEAT
        )
        val layout = ActionMenuLayout.parse(
            "${ActionMenuIds.ADD_TO_QUEUE},${ActionMenuIds.AUDIO_OUTPUT},${ActionMenuIds.AB_REPEAT};",
            playerDefaults
        )

        assertEquals(
            listOf(
                ActionMenuIds.ADD_TO_QUEUE,
                ActionMenuIds.AUDIO_OUTPUT,
                ActionMenuIds.CASTING,
                ActionMenuIds.AB_REPEAT
            ),
            layout.order
        )
    }
}
