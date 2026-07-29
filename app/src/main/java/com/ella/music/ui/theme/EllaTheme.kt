package com.ella.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ella.music.data.SettingsManager
import com.ella.music.ui.settings.SYSTEM_FONT_PATH
import com.ella.music.ui.components.loadAndroidTypeface
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

const val THEME_FOLLOW_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

// Monet dynamic-color source.
const val MONET_OFF = 0
const val MONET_SYSTEM = 1   // system wallpaper colors (Android 12+)
const val MONET_COVER = 2    // seed from the current song cover

@Composable
fun EllaTheme(
    themeMode: Int = THEME_FOLLOW_SYSTEM,
    appFontPath: String = "",
    appFontWeight: Int = 700,
    monetMode: Int = MONET_OFF,
    keyColor: Color? = null,
    systemDarkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = systemDarkOverride ?: isSystemInDarkTheme()
    val colorSchemeMode = when (themeMode) {
        THEME_LIGHT -> ColorSchemeMode.Light
        THEME_DARK -> ColorSchemeMode.Dark
        else -> if (systemDark) ColorSchemeMode.Dark else ColorSchemeMode.Light
    }
    // Monet variant of the current light/dark choice.
    val monetSchemeMode = when (themeMode) {
        THEME_LIGHT -> ColorSchemeMode.MonetLight
        THEME_DARK -> ColorSchemeMode.MonetDark
        else -> if (systemDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
    }

    val controller = remember(colorSchemeMode, monetSchemeMode, monetMode, keyColor) {
        when (monetMode) {
            // System wallpaper colors: keyColor = null -> platformDynamicColors.
            MONET_SYSTEM -> ThemeController(colorSchemeMode = monetSchemeMode, keyColor = null)
            // Cover-seeded scheme; until a cover seed is available, fall back to the default scheme
            // so we don't briefly flash system/wallpaper colors.
            MONET_COVER -> if (keyColor != null) {
                ThemeController(colorSchemeMode = monetSchemeMode, keyColor = keyColor)
            } else {
                ThemeController(colorSchemeMode = colorSchemeMode)
            }
            else -> ThemeController(colorSchemeMode = colorSchemeMode)
        }
    }
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val appFontScalePercent by settingsManager.appFontScalePercent.collectAsState(
        initial = SettingsManager.DEFAULT_APP_FONT_SCALE_PERCENT
    )
    val appDisplayScalePercent by settingsManager.appDisplayScalePercent.collectAsState(
        initial = SettingsManager.DEFAULT_APP_DISPLAY_SCALE_PERCENT
    )
    val baseDensity = LocalDensity.current
    val adjustedDensity = remember(
        baseDensity.density,
        baseDensity.fontScale,
        appFontScalePercent,
        appDisplayScalePercent
    ) {
        Density(
            density = baseDensity.density * appDisplayScalePercent / 100f,
            fontScale = baseDensity.fontScale * appFontScalePercent / 100f
        )
    }
    val appFontFamily = remember(context) {
        bundledMiSansBoldFontFamily(context)
    }
    val customAppFontFamily = remember(appFontPath, appFontWeight) {
        appFontPath.toCustomAppFontFamily(appFontWeight)
    }
    val preferMiSansByDefault = remember {
        !isXiaomiFamilyDevice()
    }
    val textStyles = remember(appFontFamily, customAppFontFamily, preferMiSansByDefault) {
        val defaults = defaultTextStyles()
        val effectiveFontFamily = customAppFontFamily ?: appFontFamily.takeIf { preferMiSansByDefault }
        if (effectiveFontFamily == null) {
            return@remember defaults
        } else {
            defaults.copy(
                main = defaults.main.copy(fontFamily = effectiveFontFamily),
                paragraph = defaults.paragraph.copy(fontFamily = effectiveFontFamily),
                body1 = defaults.body1.copy(fontFamily = effectiveFontFamily),
                body2 = defaults.body2.copy(fontFamily = effectiveFontFamily),
                button = defaults.button.copy(fontFamily = effectiveFontFamily),
                footnote1 = defaults.footnote1.copy(fontFamily = effectiveFontFamily),
                footnote2 = defaults.footnote2.copy(fontFamily = effectiveFontFamily),
                headline1 = defaults.headline1.copy(fontFamily = effectiveFontFamily),
                headline2 = defaults.headline2.copy(fontFamily = effectiveFontFamily),
                subtitle = defaults.subtitle.copy(fontFamily = effectiveFontFamily),
                title1 = defaults.title1.copy(fontFamily = effectiveFontFamily),
                title2 = defaults.title2.copy(fontFamily = effectiveFontFamily),
                title3 = defaults.title3.copy(fontFamily = effectiveFontFamily),
                title4 = defaults.title4.copy(fontFamily = effectiveFontFamily)
            )
        }
    }

    MiuixTheme(
        controller = controller,
        textStyles = textStyles
    ) {
        CompositionLocalProvider(LocalDensity provides adjustedDensity) {
            content()
        }
    }
}

private fun isXiaomiFamilyDevice(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    return listOf(brand, manufacturer).any { value ->
        value.contains("xiaomi", ignoreCase = true) ||
            value.contains("redmi", ignoreCase = true) ||
            value.contains("poco", ignoreCase = true)
    }
}

private fun String.toCustomAppFontFamily(weight: Int): FontFamily? {
    if (isBlank() || this == SYSTEM_FONT_PATH) return null
    return runCatching {
        FontFamily(loadAndroidTypeface(this, weight.coerceIn(100, 900), italic = false, boldFallback = false))
    }.getOrNull()
}
