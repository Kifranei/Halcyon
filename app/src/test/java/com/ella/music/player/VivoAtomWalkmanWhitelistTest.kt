package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoAtomWalkmanWhitelistTest {
    @Test
    fun parsesAndEncodesOriginOsPackageList() {
        val packages = VivoAtomWalkmanWhitelist.parsePackageList("[\"com.example.player\",\"com.other\"]")

        assertEquals(listOf("com.example.player", "com.other"), packages)
        assertEquals("[\"com.example.player\",\"com.other\"]", VivoAtomWalkmanWhitelist.encodePackageList(packages))
    }

    @Test
    fun treatsMissingSettingsValueAsAnEmptyList() {
        assertEquals(emptyList<String>(), VivoAtomWalkmanWhitelist.parsePackageList("null"))
        assertEquals(emptyList<String>(), VivoAtomWalkmanWhitelist.parsePackageList("  "))
    }

    @Test
    fun enablingIsIdempotentAndDisablingRemovesOnlyThisPackage() {
        val current = listOf("com.other", "com.ella.music", "com.third")

        assertEquals(current, VivoAtomWalkmanWhitelist.updatePackageList(current, "com.ella.music", true))
        assertEquals(
            listOf("com.other", "com.third"),
            VivoAtomWalkmanWhitelist.updatePackageList(current, "com.ella.music", false)
        )
        assertEquals(
            listOf("com.other", "com.third", "com.ella.music"),
            VivoAtomWalkmanWhitelist.updatePackageList(listOf("com.other", "com.third"), "com.ella.music", true)
        )
    }

    @Test
    fun writeCommandQuotesJsonAsOneShellArgument() {
        val command = VivoAtomWalkmanWhitelist.buildWriteCommand("[\"com.ella.music\"]")

        assertTrue(command.startsWith("settings put system ${VivoAtomWalkmanWhitelist.SETTINGS_KEY} '"))
        assertTrue(command.endsWith("'"))
    }
}
