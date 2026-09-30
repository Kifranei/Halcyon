package com.ella.music.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

internal fun Modifier.rainbowVisualizer(enabled: Boolean): Modifier = if (!enabled) this else
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(listOf(
                Color(0xFFFF5252), Color(0xFFFFA726), Color(0xFFFFEE58),
                Color(0xFF66BB6A), Color(0xFF26C6DA), Color(0xFF5C6BC0), Color(0xFFAB47BC)
            )),
            blendMode = BlendMode.SrcIn
        )
    }
