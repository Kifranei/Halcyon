package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HyperOsDetectorTest {

    @Test
    fun `bg effect constants are consistent across detector and settings manager`() {
        assertEquals(0, HyperOsDetector.BG_EFFECT_OS2)
        assertEquals(1, HyperOsDetector.BG_EFFECT_OS3)
        assertEquals(2, HyperOsDetector.BG_EFFECT_OS1)

        assertEquals(HyperOsDetector.BG_EFFECT_OS2, SettingsManager.BG_EFFECT_OS2)
        assertEquals(HyperOsDetector.BG_EFFECT_OS3, SettingsManager.BG_EFFECT_OS3)
        assertEquals(HyperOsDetector.BG_EFFECT_OS1, SettingsManager.BG_EFFECT_OS1)
    }

    @Test
    fun `hyperos version regex matches OS major versions`() {
        val regex = Regex("""OS(\d+)""", RegexOption.IGNORE_CASE)

        val os1Match = regex.find("OS1.0.14.0.UNCCNXM")
        assertNotNull(os1Match)
        assertEquals(1, os1Match?.groupValues?.getOrNull(1)?.toIntOrNull())

        val os2Match = regex.find("OS2.0.1.0.VNACNXM")
        assertNotNull(os2Match)
        assertEquals(2, os2Match?.groupValues?.getOrNull(1)?.toIntOrNull())

        val os3Match = regex.find("OS3.0.0.1.WNACNXM")
        assertNotNull(os3Match)
        assertEquals(3, os3Match?.groupValues?.getOrNull(1)?.toIntOrNull())

        val invalidMatch = regex.find("V14.0.23.0.TLCCNXM")
        assertNull(invalidMatch)
    }

    @Test
    fun `default effect mapping assigns OS1 for major 1, OS2 for major 2, OS3 for major 3 or higher`() {
        fun mapMajor(major: Int?): Int = when (major) {
            1 -> HyperOsDetector.BG_EFFECT_OS1
            2 -> HyperOsDetector.BG_EFFECT_OS2
            else -> HyperOsDetector.BG_EFFECT_OS3
        }

        assertEquals(HyperOsDetector.BG_EFFECT_OS1, mapMajor(1))
        assertEquals(HyperOsDetector.BG_EFFECT_OS2, mapMajor(2))
        assertEquals(HyperOsDetector.BG_EFFECT_OS3, mapMajor(3))
        assertEquals(HyperOsDetector.BG_EFFECT_OS3, mapMajor(4))
        assertEquals(HyperOsDetector.BG_EFFECT_OS3, mapMajor(null))
    }
}
