package com.ella.music.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics

/**
 * Accurately determines if the current runtime device is a television.
 *
 * Phones and tablets must never be identified as television devices. Real TV / Android TV devices:
 * 1. Declare leanback or television UI mode / system feature.
 * 2. Do NOT feature a touchscreen ([PackageManager.FEATURE_TOUCHSCREEN]).
 * 3. Have TV-typical display density (such as [DisplayMetrics.DENSITY_TV] ~213dpi or <= [DisplayMetrics.DENSITY_XHIGH] on large screens).
 */
fun isTelevisionDevice(context: Context): Boolean {
    val pm = context.packageManager
    val hasTvFeature = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("android.hardware.type.television") ||
        (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

    val hasTouchScreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    val densityDpi = context.resources.displayMetrics.densityDpi
    val screenLayout = context.resources.configuration.screenLayout

    return isTelevisionDevice(
        hasTvFeature = hasTvFeature,
        hasTouchScreen = hasTouchScreen,
        densityDpi = densityDpi,
        screenLayout = screenLayout
    )
}

fun isTelevisionDevice(
    hasTvFeature: Boolean,
    hasTouchScreen: Boolean,
    densityDpi: Int,
    screenLayout: Int
): Boolean {
    if (!hasTvFeature) return false
    // Telephones and tablets have touchscreens; televisions do not.
    if (hasTouchScreen) return false

    val isLargeScreen = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    val isTvDensity = densityDpi == DisplayMetrics.DENSITY_TV ||
        densityDpi <= DisplayMetrics.DENSITY_XHIGH ||
        isLargeScreen

    return isTvDensity
}
