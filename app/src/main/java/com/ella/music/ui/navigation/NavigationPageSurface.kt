package com.ella.music.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ella.music.ui.components.ellaPageCanvasColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The shared wallpaper/flow background alone, never another destination's content. */
internal class NavigationPageBackground(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalNavigationPageBackground = staticCompositionLocalOf<NavigationPageBackground?> { null }
internal val LocalNavigationViewportOrigin = staticCompositionLocalOf { Offset.Zero }

@Composable
internal fun rememberNavigationPageBackground(): NavigationPageBackground {
    val layer = rememberGraphicsLayer()
    return remember(layer) { NavigationPageBackground(layer) }
}

internal fun Modifier.recordNavigationPageBackground(background: NavigationPageBackground): Modifier =
    onGloballyPositioned { background.origin = it.positionInRoot() }.drawWithContent {
        background.layer.record { this@drawWithContent.drawContent() }
        drawLayer(background.layer)
    }

/** Each destination is an opaque sheet; its wallpaper moves with it during a pop. */
@Composable
internal fun NavigationPageSurface(content: @Composable () -> Unit) {
    val background = LocalNavigationPageBackground.current
    val origin = LocalNavigationViewportOrigin.current
    val base = if (background == null) ellaPageCanvasColor() else MiuixTheme.colorScheme.background
    Box(Modifier.fillMaxSize().drawWithCache {
        val shadowWidth = 24.dp.toPx()
        val shadow = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.4f to Color.Black.copy(alpha = 0.025f),
            0.75f to Color.Black.copy(alpha = 0.10f),
            1f to Color.Black.copy(alpha = 0.24f),
            startX = -shadowWidth,
            endX = 0f
        )
        onDrawBehind {
            // Outside the sheet's content clip, but inside the navigation viewport:
            // only a page sliding right exposes this shadow over the lower page.
            drawRect(shadow, topLeft = Offset(-shadowWidth, 0f), size = Size(shadowWidth, size.height))
        }
    }.clipToBounds().drawBehind {
        drawRect(base.copy(alpha = 1f))
        if (background != null) {
            translate(-origin.x, -origin.y) { drawLayer(background.layer) }
        }
    }) { content() }
}

internal fun NavGraphBuilder.navigationPage(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(route = route, arguments = arguments) { entry ->
        NavigationPageSurface { content(entry) }
    }
}
