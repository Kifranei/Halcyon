// Material Symbols, Google, Apache-2.0. User-supplied shuffle variant.
package com.ella.music.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val HomeDailyShuffleIcon: ImageVector
  get() {
    if (_shuffle != null) {
      return _shuffle!!
    }
    _shuffle =
      ImageVector.Builder(
          name = "shuffle",
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
            moveTo(14f, 20f)
            verticalLineTo(18f)
            horizontalLineToRelative(2.6f)
            lineTo(13.43f, 14.83f)
            lineTo(14.85f, 13.4f)
            lineTo(18f, 16.55f)
            verticalLineTo(14f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineTo(14f)
            close()
            moveTo(5.4f, 20f)
            lineTo(4f, 18.6f)
            lineTo(16.6f, 6f)
            horizontalLineTo(14f)
            verticalLineTo(4f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(6f)
            horizontalLineTo(18f)
            verticalLineTo(7.4f)
            lineTo(5.4f, 20f)
            close()
            moveTo(9.18f, 10.58f)
            lineTo(4f, 5.4f)
            lineTo(5.4f, 4f)
            lineToRelative(5.18f, 5.17f)
            lineToRelative(-1.4f, 1.4f)
            close()
          }
        }
        .build()
    return _shuffle!!
  }

private var _shuffle: ImageVector? = null
