package com.ella.music.util

import android.content.res.Configuration
import android.util.DisplayMetrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionUtilsTest {

    @Test
    fun `phone with touchscreen is never detected as television even if it declares tv features`() {
        val result = isTelevisionDevice(
            hasTvFeature = true,
            hasTouchScreen = true,
            densityDpi = DisplayMetrics.DENSITY_XXHIGH,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
        )
        assertFalse(result)
    }

    @Test
    fun `older phone with touchscreen and lower dpi is never detected as television`() {
        val result = isTelevisionDevice(
            hasTvFeature = true,
            hasTouchScreen = true,
            densityDpi = DisplayMetrics.DENSITY_HIGH,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
        )
        assertFalse(result)
    }

    @Test
    fun `device without tv feature is not television`() {
        val result = isTelevisionDevice(
            hasTvFeature = false,
            hasTouchScreen = false,
            densityDpi = DisplayMetrics.DENSITY_TV,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE
        )
        assertFalse(result)
    }

    @Test
    fun `android tv with leanback and without touchscreen is detected as television`() {
        val result = isTelevisionDevice(
            hasTvFeature = true,
            hasTouchScreen = false,
            densityDpi = DisplayMetrics.DENSITY_TV,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE
        )
        assertTrue(result)
    }

    @Test
    fun `4k android tv without touchscreen is detected as television`() {
        val result = isTelevisionDevice(
            hasTvFeature = true,
            hasTouchScreen = false,
            densityDpi = DisplayMetrics.DENSITY_XHIGH,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_XLARGE
        )
        assertTrue(result)
    }
}
