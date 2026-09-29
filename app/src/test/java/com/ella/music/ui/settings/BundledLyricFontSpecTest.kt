package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledLyricFontSpecTest {
    @Test
    fun interIsAvailableAsABundledLyricFont() {
        val inter = BUNDLED_FONT_SPECS.single { it.displayName == "Inter Bold" }

        assertEquals("Inter-Bold.ttf", inter.fileName)
        assertEquals("fonts/Inter-Bold.ttf", inter.assetPath)
    }

    @Test
    fun bundledFontDestinationsAreUnique() {
        assertEquals(BUNDLED_FONT_SPECS.size, BUNDLED_FONT_SPECS.map { it.fileName }.distinct().size)
        assertTrue(BUNDLED_FONT_SPECS.all { it.assetPath.startsWith("fonts/") })
    }

    @Test
    fun buildFontDropdownOptionsDeduplicatesDefaultTitle() {
        val fonts = listOf(
            FontChoice("Inter Bold", "/app/files/lyric_builtin_fonts/Inter-Bold.ttf", "内置", 0),
            FontChoice("MiSans Bold", "/app/files/lyric_builtin_fonts/MiSans-Bold.ttf", "内置", 0),
            FontChoice("Custom Font", "/app/files/lyric_custom_fonts/Custom.ttf", "自定义", 1)
        )

        // Western dropdown: default is "Inter Bold"
        val westernOptions = buildFontDropdownOptions(
            fonts = fonts,
            currentPath = "",
            currentName = "Inter Bold",
            defaultTitle = "Inter Bold"
        )
        // Must contain default at index 0 and system default at index 1
        assertEquals(4, westernOptions.size)
        assertEquals("Inter Bold", westernOptions[0].name)
        assertEquals("", westernOptions[0].path)
        assertEquals("系统默认", westernOptions[1].name)
        assertEquals(SYSTEM_FONT_PATH, westernOptions[1].path)
        assertEquals("MiSans Bold", westernOptions[2].name)
        assertEquals("/app/files/lyric_builtin_fonts/MiSans-Bold.ttf", westernOptions[2].path)
        assertEquals("Custom Font", westernOptions[3].name)
        assertEquals(1, westernOptions.count { it.name == "Inter Bold" })

        // CJK dropdown: default is "MiSans Bold"
        val cjkOptions = buildFontDropdownOptions(
            fonts = fonts,
            currentPath = "",
            currentName = "MiSans Bold",
            defaultTitle = "MiSans Bold"
        )
        assertEquals(4, cjkOptions.size)
        assertEquals("MiSans Bold", cjkOptions[0].name)
        assertEquals("", cjkOptions[0].path)
        assertEquals("系统默认", cjkOptions[1].name)
        assertEquals(SYSTEM_FONT_PATH, cjkOptions[1].path)
        assertEquals("Inter Bold", cjkOptions[2].name)
        assertEquals("/app/files/lyric_builtin_fonts/Inter-Bold.ttf", cjkOptions[2].path)
        assertEquals("Custom Font", cjkOptions[3].name)
        assertEquals(1, cjkOptions.count { it.name == "MiSans Bold" })
    }

    @Test
    fun resolveFontDropdownSelectedIndexMatchesCorrectly() {
        val options = listOf(
            FontDropdownOption("Inter Bold", ""),
            FontDropdownOption("MiSans Bold", "/app/files/lyric_builtin_fonts/MiSans-Bold.ttf"),
            FontDropdownOption("Custom Font", "/app/files/lyric_custom_fonts/Custom.ttf")
        )

        // Blank current path -> index 0
        assertEquals(0, resolveFontDropdownSelectedIndex(options, "", "Inter Bold"))

        // Bundled Inter-Bold path -> maps to default index 0
        assertEquals(0, resolveFontDropdownSelectedIndex(options, "/data/user/0/com.ella.music/files/lyric_builtin_fonts/Inter-Bold.ttf", "Inter Bold"))

        // Other bundled font in list -> maps to its item index 1
        assertEquals(1, resolveFontDropdownSelectedIndex(options, "/app/files/lyric_builtin_fonts/MiSans-Bold.ttf", "Inter Bold"))

        // Custom font -> maps to its item index 2
        assertEquals(2, resolveFontDropdownSelectedIndex(options, "/app/files/lyric_custom_fonts/Custom.ttf", "Inter Bold"))
    }
}
