// Google Material Symbols, Apache-2.0. User-selected speed vector.
package com.ella.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val MaterialSpeedIcon: ImageVector
  get() {
    if (_speed != null) {
      return _speed!!
    }
    _speed =
      ImageVector.Builder(
          name = "speed",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(12f, 16.09f)
            quadToRelative(0.95f, -0.01f, 1.4f, -0.69f)
            lineTo(19f, 7f)
            lineToRelative(-8.4f, 5.6f)
            quadTo(9.93f, 13.05f, 9.89f, 13.98f)
            reflectiveQuadToRelative(0.56f, 1.53f)
            reflectiveQuadTo(12f, 16.09f)
            close()
            moveTo(12f, 4f)
            quadToRelative(1.48f, 0f, 2.84f, 0.41f)
            reflectiveQuadTo(17.4f, 5.65f)
            lineToRelative(-1.9f, 1.2f)
            quadTo(14.68f, 6.43f, 13.79f, 6.21f)
            reflectiveQuadTo(12f, 6f)
            quadTo(8.68f, 6f, 6.34f, 8.34f)
            quadTo(4f, 10.68f, 4f, 14f)
            quadToRelative(0f, 1.05f, 0.29f, 2.07f)
            reflectiveQuadTo(5.1f, 18f)
            horizontalLineTo(18.9f)
            quadToRelative(0.57f, -0.95f, 0.84f, -1.98f)
            reflectiveQuadTo(20f, 13.9f)
            quadTo(20f, 13f, 19.79f, 12.15f)
            reflectiveQuadTo(19.15f, 10.5f)
            lineToRelative(1.2f, -1.9f)
            quadToRelative(0.75f, 1.17f, 1.19f, 2.5f)
            reflectiveQuadTo(22f, 13.85f)
            reflectiveQuadToRelative(-0.32f, 2.72f)
            reflectiveQuadToRelative(-1.02f, 2.48f)
            quadToRelative(-0.28f, 0.45f, -0.75f, 0.7f)
            reflectiveQuadTo(18.9f, 20f)
            horizontalLineTo(5.1f)
            quadToRelative(-0.53f, 0f, -1f, -0.25f)
            reflectiveQuadTo(3.35f, 19.05f)
            quadTo(2.7f, 17.93f, 2.35f, 16.66f)
            reflectiveQuadTo(2f, 14f)
            quadTo(2f, 11.93f, 2.79f, 10.11f)
            reflectiveQuadTo(4.94f, 6.94f)
            quadTo(6.3f, 5.57f, 8.13f, 4.79f)
            reflectiveQuadTo(12f, 4f)
            close()
            moveToRelative(0.18f, 7.82f)
            close()
          }
        }
        .build()
    return _speed!!
  }

private var _speed: ImageVector? = null
