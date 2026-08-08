package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.ui.components.EllaMiuixTextField
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsCardGroup(
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color(0xFFFFFFFF)
    val highlightColor = if (isDark) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var lit by remember(highlight) { mutableStateOf(false) }
    LaunchedEffect(highlight) {
        if (!highlight) return@LaunchedEffect
        bringIntoViewRequester.bringIntoView()
        repeat(4) {
            lit = !lit
            delay(180)
        }
        lit = false
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(bottom = 14.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (lit) highlightColor else cardColor
        )
    ) {
        content()
    }
}

/**
 * Gives a search result an anchor inside a large settings card or sheet. The card-level
 * bring-into-view behavior is still useful for the section header, but this smaller anchor keeps
 * the selected preference itself on screen when a result points into a dense group (#410).
 */
@Composable
internal fun SettingsFocusAnchor(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) {
        if (active) bringIntoViewRequester.bringIntoView()
    }
    Box(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester)
    ) {
        content()
    }
}

@Composable
internal fun SplitSettingTextField(
    label: String,
    value: String,
    summary: String,
    singleLine: Boolean = false,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var localValue by remember(label) { mutableStateOf(value) }
    var pendingValue by remember(label) { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        if (pendingValue == value) {
            pendingValue = null
            if (localValue != value) localValue = value
        } else if (pendingValue == null && value != localValue) {
            localValue = value
        }
    }

    LaunchedEffect(localValue) {
        if (pendingValue == localValue || localValue == value) return@LaunchedEffect
        delay(360)
        if (localValue != value) {
            pendingValue = localValue
            onValueChange(localValue)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = summary,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        EllaMiuixTextField(
            value = localValue,
            onValueChange = {
                localValue = it
            },
            label = label,
            singleLine = singleLine,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SettingsIntSliderPreference(
    title: String,
    summary: String,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    enabled: Boolean = true,
    steps: Int = 0,
    showKeyPoints: Boolean = steps > 0,
    onValueChange: (Int) -> Unit
) {
    val safeRange = valueRange.first.toFloat()..valueRange.last.toFloat()
    SliderPreference(
        title = title,
        summary = summary,
        valueText = valueText,
        value = value.coerceIn(valueRange).toFloat(),
        valueRange = safeRange,
        steps = steps,
        showKeyPoints = showKeyPoints,
        enabled = enabled,
        onValueChange = { next ->
            onValueChange(next.toInt().coerceIn(valueRange))
        }
    )
}
