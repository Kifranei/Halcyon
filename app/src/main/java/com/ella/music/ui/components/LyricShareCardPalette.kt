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
