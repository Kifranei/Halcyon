package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileMigrationTest {
    @Test fun migrationKeepsVisibleOrderAndSeparatesGroups() {
        val order = "folder,album,artist,recent_playback,playlist,genre"
        val shortcuts = migratedHomeItems(order, "album", true).split(',')
        val features = migratedHomeItems(order, "album", false).split(',')
        assertEquals(listOf("folder", "artist", "recent_playback", "playlist"), shortcuts)
        assertEquals("genre", features.first())
        assertTrue(shortcuts.intersect(features.toSet()).isEmpty())
        assertFalse("album" in features)
    }

    @Test fun matchingDoesNotAcceptAnotherSingerWithSimilarName() {
        assertTrue(artistImageNameMatches("周杰伦", "周杰伦 (Jay Chou)"))
        assertTrue(artistImageNameMatches("JAY", "jay"))
        assertFalse(artistImageNameMatches("Jay", "Jay Sean"))
        assertFalse(artistImageNameMatches("", ""))
    }
}
