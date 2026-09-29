package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSearchTargetTest {
    @Test fun neteaseLibraryOverridesEverySearchPreference() {
        for (preference in listOf("local", "lx", "musicfree", "unknown", "")) {
            assertEquals("netease", resolveHomeSearchTarget(SettingsManager.LIBRARY_SOURCE_NETEASE, preference))
        }
    }
    @Test fun otherLibrariesRespectEachConfiguredDestination() {
        for (library in listOf("local", "navidrome", "opensubsonic", "emby", "webdav")) {
            for (target in listOf("local", "lx", "musicfree")) {
                assertEquals(target, resolveHomeSearchTarget(library, target))
            }
        }
    }
    @Test fun unsetOrInvalidChoiceFallsBackToLocalLibrary() {
        assertEquals("local", resolveHomeSearchTarget("local", ""))
        assertEquals("local", resolveHomeSearchTarget("emby", "invalid"))
    }
}
