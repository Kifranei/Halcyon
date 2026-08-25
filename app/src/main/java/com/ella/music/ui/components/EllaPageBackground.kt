package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import com.ella.music.ui.navigation.Screen
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalSharedAppBackgroundVisible = compositionLocalOf { false }

internal fun supportsNowPlayingFlowBackground(route: String?): Boolean = route in setOf(
    Screen.Home.route,
    Screen.Library.route,
    Screen.Album.route,
    Screen.Artist.route,
    Screen.Folder.route,
    Screen.FolderPlaylists.route,
    Screen.Playlists.route,
    Screen.MetadataCategory.route
)

@Composable
fun isAppWallpaperVisible(): Boolean {
    if (LocalSharedAppBackgroundVisible.current) return true
    val context = LocalContext.current
    val settingsManager = androidx.compose.runtime.remember(context) { SettingsManager.getInstance(context) }
    val appWallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val appWallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    return appWallpaperEnabled && appWallpaperUri.isNotBlank()
}

@Composable
fun ellaPageBackground(): Color {
    if (isAppWallpaperVisible()) return Color.Transparent

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
}

@Composable
fun wallpaperContentOverlayColor(): Color {
    val context = LocalContext.current
    val settingsManager = androidx.compose.runtime.remember(context) { SettingsManager.getInstance(context) }
    val visible = isAppWallpaperVisible()
    if (!visible) return Color.Transparent
    val strength by settingsManager.appWallpaperContentOverlay.collectAsState(initial = 24)
    val backgroundIsLight = MiuixTheme.colorScheme.background.luminance() >= 0.5f
    val baseAlpha = strength.coerceIn(0, 80) / 100f
    return if (backgroundIsLight) {
        Color.White.copy(alpha = (baseAlpha * 0.95f).coerceIn(0f, 0.78f))
    } else {
        Color.Black.copy(alpha = (baseAlpha * 0.82f).coerceIn(0f, 0.70f))
    }
}

@Composable
fun wallpaperAwareCardColor(defaultAlpha: Float = 0.42f): Color {
    if (!isAppWallpaperVisible()) return MiuixTheme.colorScheme.surface
    val context = LocalContext.current
    val settingsManager = androidx.compose.runtime.remember(context) { SettingsManager.getInstance(context) }
    val opacity by settingsManager.homeCardOpacity.collectAsState(initial = 58)
    val backgroundIsLight = MiuixTheme.colorScheme.background.luminance() >= 0.5f
    val base = if (backgroundIsLight) Color.White else Color.Black
    val alpha = (opacity.coerceIn(20, 100) / 100f).coerceAtLeast(defaultAlpha)
    return base.copy(alpha = alpha)
}

@Composable
fun wallpaperAwareCardColors(defaultAlpha: Float = 0.42f) =
    CardDefaults.defaultColors(color = wallpaperAwareCardColor(defaultAlpha))
