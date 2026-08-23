package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavRestoreDefaultsTest {
    @Test
    fun `restore defaults round trip in stable enum order`() {
        val types = setOf(BackupType.Equalizer, BackupType.Playlists, BackupType.Personalization)
        assertEquals(types, types.toWebDavRestoreSetting().toWebDavRestoreTypes())
    }

    @Test
    fun `unknown saved types are ignored`() {
        assertEquals(setOf(BackupType.Playlists), "Missing,Playlists".toWebDavRestoreTypes())
    }
}
