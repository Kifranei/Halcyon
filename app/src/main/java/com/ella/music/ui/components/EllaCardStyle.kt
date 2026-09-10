package com.ella.music.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ella.music.ui.about.aboutCardBlendColors
import com.ella.music.ui.about.aboutCardFallbackColor
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SettingsCardFrosting(
    val backdrop: LayerBackdrop,
    val blurEnable: Boolean,
    val cardBlendColors: List<BlendColorEntry>
)

val LocalSettingsCardFrosting = staticCompositionLocalOf<SettingsCardFrosting?> { null }

@Composable
fun frostedCardModifier(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    frosting: SettingsCardFrosting? = LocalSettingsCardFrosting.current
): Modifier {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (frosting != null && frosting.blurEnable) {
        modifier.textureBlur(
            backdrop = frosting.backdrop,
            shape = RoundedCornerShape(cornerRadius),
            blurRadius = if (isDark) 72f else 64f,
            noiseCoefficient = BlurDefaults.NoiseCoefficient,
            colors = BlurColors(blendColors = frosting.cardBlendColors),
            enabled = true,
        )
    } else {
        modifier
    }
}

@Composable
fun frostedCardColor(
    frosting: SettingsCardFrosting? = LocalSettingsCardFrosting.current,
    defaultAlpha: Float = 0.42f
): Color {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return when {
        frosting != null && frosting.blurEnable -> Color.Transparent
        frosting != null -> MiuixTheme.colorScheme.surfaceContainer
        isAppWallpaperVisible() -> wallpaperAwareCardColor(defaultAlpha = defaultAlpha)
        isDark -> MiuixTheme.colorScheme.surfaceContainer
        else -> Color(0xFFFFFFFF)
    }
}
