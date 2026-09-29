package com.ella.music.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.os.Build
import android.view.Display
import androidx.compose.runtime.mutableFloatStateOf
import java.util.function.Consumer

/**
 * Real HDR for lyric highlights: puts the window into HDR colour mode and tracks the headroom the
 * display currently grants (HDR/SDR ratio). Highlight code multiplies its glow by [gain]; values
 * above SDR white are then shown brighter than the UI on HDR-capable screens (Android 14+).
 *
 * On devices without HDR UI support [gain] stays 1 and callers keep the SDR "towards white" look.
 */
internal object LyricHdrWindow {
    /** Headroom the glow may use; 1 = SDR only. */
    val gain = mutableFloatStateOf(1f)

    /** User-chosen highlight brightness relative to SDR white (1.5×–2.5×); also drives the SDR fallback. */
    val userRatio = mutableFloatStateOf(2.0f)
    private val desiredHeadroom: Float get() = userRatio.floatValue.coerceIn(1.5f, 2.5f)

    private var listener: Consumer<Display>? = null
    private var listenedDisplay: Display? = null

    val isActive: Boolean get() = gain.floatValue > 1.01f

    fun apply(activity: Activity, enabled: Boolean, ratio: Float = userRatio.floatValue) {
        userRatio.floatValue = ratio.coerceIn(1.5f, 2.5f)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            gain.floatValue = 1f
            return
        }
        val display = activity.display
        val supported = display != null && display.isHdrSdrRatioAvailable
        if (!enabled || !supported) {
            release(activity)
            return
        }
        activity.window.colorMode = ActivityInfo.COLOR_MODE_HDR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity.window.desiredHdrHeadroom = desiredHeadroom
        }
        if (listenedDisplay !== display) {
            unregister()
            val callback = Consumer<Display> { updated -> gain.floatValue = headroomOf(updated) }
            display!!.registerHdrSdrRatioChangedListener(activity.mainExecutor, callback)
            listener = callback
            listenedDisplay = display
        }
        gain.floatValue = headroomOf(display!!)
    }

    fun release(activity: Activity) {
        unregister()
        gain.floatValue = 1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            activity.window.colorMode == ActivityInfo.COLOR_MODE_HDR) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
    }

    private fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val display = listenedDisplay
            val callback = listener
            if (display != null && callback != null) runCatching { display.unregisterHdrSdrRatioChangedListener(callback) }
        }
        listener = null
        listenedDisplay = null
    }

    private fun headroomOf(display: Display): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.hdrSdrRatio.takeIf { it.isFinite() }?.coerceIn(1f, desiredHeadroom) ?: 1f
        } else 1f

    private val linearExtended: ColorSpace by lazy { ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB) }

    /**
     * Packs an sRGB colour brightened by [gainFactor] in linear light, so 2× means twice the
     * luminance of the SDR colour (values above 1.0 need an HDR window to be visible).
     */
    fun extendedColor(red: Float, green: Float, blue: Float, alpha: Float, gainFactor: Float): Long {
        fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return android.graphics.Color.pack(
            linear(red) * gainFactor,
            linear(green) * gainFactor,
            linear(blue) * gainFactor,
            alpha.coerceIn(0f, 1f),
            linearExtended
        )
    }
}

internal fun android.content.Context.findActivityForHdr(): Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
