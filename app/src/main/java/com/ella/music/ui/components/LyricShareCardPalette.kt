package com.ella.music.ui.components

import android.graphics.Color

internal fun Int.lightenForShare(factor: Float): Int = Color.rgb(
    (Color.red(this) * factor).toInt().coerceIn(0, 255),
    (Color.green(this) * factor).toInt().coerceIn(0, 255),
    (Color.blue(this) * factor).toInt().coerceIn(0, 255)
)

internal fun Int.darkenForShare(factor: Float): Int = Color.rgb(
    (Color.red(this) * factor).toInt().coerceIn(0, 255),
    (Color.green(this) * factor).toInt().coerceIn(0, 255),
    (Color.blue(this) * factor).toInt().coerceIn(0, 255)
)

internal fun Int.ensureShareCardContrast(): Int {
    val luminance = 0.299f * Color.red(this) + 0.587f * Color.green(this) + 0.114f * Color.blue(this)
    return if (luminance > 160f) darkenForShare(0.48f) else this
}
