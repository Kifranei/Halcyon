package com.ella.music.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    effectBackground: Boolean = true,
    isDarkTheme: Boolean? = null,
    isOs3: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val isDark = isDarkTheme ?: (MiuixTheme.colorScheme.background.luminance() < 0.5f)
    val surface = if (isDarkTheme != null) {
        if (isDark) Color.Black else Color(0xFFF7F7F7)
    } else {
        MiuixTheme.colorScheme.surface
    }
    if (!effectBackground) {
        Box(
            modifier = modifier
                .background(surface)
                .then(bgModifier),
            content = content
        )
        return
    }
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(
            modifier = modifier
                .background(surface)
                .then(bgModifier),
            content = content
        )
        return
    }
    Box(modifier = modifier) {
        val painter = remember(isOs3) { BgEffectPainter(isOs3 = isOs3) }
        val animTime = rememberFrameTimeSeconds(dynamicBackground)
        val deviceType = DeviceType.PHONE
        val preset = remember(isDark, deviceType, isOs3) { BgEffectConfig.get(deviceType, isDark, isOs3) }
        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            var targetStage = 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(bgModifier)) {
            drawRect(surface)
            if (effectBackground) {
                val drawHeight = size.height * 0.78f
                val stage = colorStage.value
                val base = stage.toInt()
                val fraction = stage - base
                val getColors = { index: Int ->
                    when (index % 4) {
                        0 -> preset.colors2
                        1 -> preset.colors1
                        2 -> preset.colors2
                        3 -> preset.colors3
                        else -> preset.colors2
                    }
                }
                val start = getColors(base)
                val end = getColors(base + 1)
                val currentColors = FloatArray(16) { i -> start[i] + (end[i] - start[i]) * fraction }
                painter.updateResolution(size.width, size.height)
                painter.updatePresetIfNeeded(drawHeight, size.height, size.width, isDark)
                painter.updateColors(currentColors)
                painter.updateAnimTime(animTime())
                drawRect(painter.brush, alpha = alpha())
            }
        }
        content()
    }
}
