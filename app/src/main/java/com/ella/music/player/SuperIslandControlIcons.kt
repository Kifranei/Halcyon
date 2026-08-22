package com.ella.music.player

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

internal fun superIslandControlButtonColor(darkBackground: Boolean): Int =
    if (darkBackground) Color.WHITE else 0xFF111111.toInt()

internal fun superIslandSystemUiIsDark(uiMode: Int): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

internal fun renderTintedDrawableBitmap(
    context: Context,
    @DrawableRes resId: Int,
    color: Int,
    sizePx: Int
): Bitmap {
    val drawable = requireNotNull(ContextCompat.getDrawable(context, resId)).mutate()
    DrawableCompat.setTint(drawable, color)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bitmap
}
