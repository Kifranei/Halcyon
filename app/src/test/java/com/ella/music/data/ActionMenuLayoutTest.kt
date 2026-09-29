package com.ella.music.data

import com.ella.music.data.model.Song
import com.ella.music.data.model.isNeteaseStream
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

    @Test
    fun `view mv is added after download mv for an existing saved player layout`() {
        val playerDefaults = (ActionMenuIds.playerShortcutDefaults + ActionMenuIds.playerDefaults).distinct()
        val saved = playerDefaults.filterNot { it == ActionMenuIds.VIEW_MV }.reversed()
        val layout = ActionMenuLayout.parse(
            saved.joinToString(",") + ";" + ActionMenuIds.SHARE,
            playerDefaults
        )

        // Existing items keep the user's order; the new id is visible and sits after DOWNLOAD_MV.
        assertEquals(saved, layout.order.filterNot { it == ActionMenuIds.VIEW_MV })
        assertEquals(layout.order.indexOf(ActionMenuIds.DOWNLOAD_MV) + 1, layout.order.indexOf(ActionMenuIds.VIEW_MV))
        assertTrue(ActionMenuIds.VIEW_MV in layout.visibleIds(playerDefaults))
        assertTrue(ActionMenuIds.SHARE in layout.hidden)
        assertTrue(ActionMenuIds.VIEW_MV in ActionMenuIds.playerShortcutCatalog)
    }

    @Test
    fun `view mv is appended when a saved layout has no download mv anchor`() {
        val defaults = listOf(ActionMenuIds.SHARE, ActionMenuIds.VIEW_MV, ActionMenuIds.DELETE)
        val layout = ActionMenuLayout.parse("${ActionMenuIds.DELETE},${ActionMenuIds.SHARE};", defaults)
        assertEquals(listOf(ActionMenuIds.DELETE, ActionMenuIds.SHARE, ActionMenuIds.VIEW_MV), layout.order)
    }

    @Test
    fun `saved view mv position is kept`() {
        val playerDefaults = ActionMenuIds.playerDefaults
        val saved = listOf(ActionMenuIds.VIEW_MV) + playerDefaults.filterNot { it == ActionMenuIds.VIEW_MV }
        val layout = ActionMenuLayout.parse(saved.joinToString(",") + ";", playerDefaults)
        assertEquals(saved, layout.order)
    }

    @Test
    fun `local file only actions are hidden for netease streams only`() {
        val stream = song(path = "halcyon-netease://song/123", onlineSource = "netease")
        val downloaded = song(path = "/storage/emulated/0/Music/Halcyon/a.flac", onlineSource = "netease")
        val downloadedContent = song(path = "content://media/external/audio/media/9", onlineSource = "netease")
        val local = song(path = "/storage/emulated/0/Music/a.flac")

        assertTrue(stream.isNeteaseStream())
        assertFalse(downloaded.isNeteaseStream())
        assertFalse(downloadedContent.isNeteaseStream())
        assertFalse(local.isNeteaseStream())

        val gated = listOf(
            ActionMenuIds.ONLINE_LYRICS, ActionMenuIds.DYNAMIC_COVER, ActionMenuIds.LYRIC_TIMING,
            ActionMenuIds.EDIT_TAGS, ActionMenuIds.RATING, ActionMenuIds.SPECTRUM
        )
        gated.forEach { id ->
            assertFalse(id, ActionMenuIds.isAvailableFor(id, stream))
            assertTrue(id, ActionMenuIds.isAvailableFor(id, downloaded))
            assertTrue(id, ActionMenuIds.isAvailableFor(id, local))
            assertTrue(id, ActionMenuIds.isAvailableFor(id, null))
        }
        listOf(ActionMenuIds.SHARE, ActionMenuIds.DOWNLOAD, ActionMenuIds.VIEW_MV, ActionMenuIds.INFO).forEach { id ->
            assertTrue(id, ActionMenuIds.isAvailableFor(id, stream))
        }
    }

    private fun song(path: String, onlineSource: String = "") = Song(
        id = 1L, title = "t", artist = "a", album = "al", albumId = 1L, duration = 1000L,
        path = path, fileName = "t", onlineSource = onlineSource
    )

    @Test
    fun listDefaultsIncludesRemoveFromRecentPlayback() {
        // #653 rewrite: single/clear recent actions collapsed into remove_from_recent_playback.
        assertTrue(ActionMenuIds.REMOVE_FROM_RECENT_PLAYBACK in ActionMenuIds.listDefaults)
        assertTrue(ActionMenuIds.DELETE_SINGLE_RECENT_PLAYBACK !in ActionMenuIds.listDefaults)
        assertTrue(ActionMenuIds.CLEAR_RECENT_PLAYBACK !in ActionMenuIds.listDefaults)
    }
}
