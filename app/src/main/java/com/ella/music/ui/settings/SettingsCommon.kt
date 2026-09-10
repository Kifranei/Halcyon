package com.ella.music.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.about.aboutCardFallbackColor
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import top.yukonga.miuix.kmp.basic.TextField
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.wallpaperAwareCardColor
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object SettingsRememberedValues {
    private val values = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> read(key: String, fallback: T): T = (values[key] as? T) ?: fallback

    fun <T> write(key: String, value: T) {
        values[key] = value
    }
}

@Composable
internal fun <T> kotlinx.coroutines.flow.Flow<T>.collectCachedAsState(
    key: String,
    initial: T
): androidx.compose.runtime.State<T> {
    val state = collectAsState(initial = SettingsRememberedValues.read(key, initial))
    SettingsRememberedValues.write(key, state.value)
    return state
}

@Composable
internal fun rememberSettingsScrollState(key: String): ScrollState {
    // The navigation entry owns this saved state: opening a child preserves the viewport, while
    // popping this page and entering it again creates a fresh state at the top (#161, #473).
    return rememberSaveable(key, saver = ScrollState.Saver) { ScrollState(0) }
}

@Composable
internal fun rememberSettingsLazyListState(key: String): LazyListState {
    return rememberSaveable(key, saver = LazyListState.Saver) { LazyListState(0, 0) }
}

internal typealias SettingsCardFrosting = com.ella.music.ui.components.SettingsCardFrosting
internal val LocalSettingsCardFrosting get() = com.ella.music.ui.components.LocalSettingsCardFrosting

@Composable
internal fun SettingsCardGroup(
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val frosting = LocalSettingsCardFrosting.current
    val cardModifier = com.ella.music.ui.components.frostedCardModifier(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        cornerRadius = 16.dp,
        frosting = frosting
    )
    val inBottomSheet = com.ella.music.ui.components.LocalInBottomSheet.current
    val cardColor = if (frosting != null) {
        com.ella.music.ui.components.frostedCardColor(frosting = frosting, defaultAlpha = 0.42f)
    } else if (inBottomSheet) {
        MiuixTheme.colorScheme.secondaryContainer
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = cardModifier,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        // Search jumps target [SettingsFocusAnchor] so a match lands on the preference
        // itself instead of flashing/scrolling the whole surrounding card (#410).
        if (highlight) {
            SettingsFocusAnchor(active = true, content = content)
        } else {
            content()
        }
    }
}

/**
 * Gives a search result an anchor inside a large settings card or sheet. The selected
 * preference is scrolled into view and flashed, without lighting sibling items.
 */
@Composable
internal fun SettingsFocusAnchor(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    var lit by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(180)
        bringIntoViewRequester.bringIntoView()
        repeat(2) {
            lit = true
            delay(280)
            lit = false
            delay(180)
        }
        bringIntoViewRequester.bringIntoView()
    }
    Column(
        modifier = modifier
            .background(if (lit) highlightColor else Color.Transparent)
            .bringIntoViewRequester(bringIntoViewRequester)
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
    endAction: @Composable (() -> Unit)? = null,
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

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = localValue,
                onValueChange = {
                    localValue = it
                },
                label = label,
                useLabelAsPlaceholder = false,
                singleLine = singleLine,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f)
            )
            endAction?.invoke()
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
            )
        }
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
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    onValueChange: (Int) -> Unit
) {
    val safeRange = valueRange.first.toFloat()..valueRange.last.toFloat()
    SliderPreference(
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        valueText = valueText,
        value = value.coerceIn(valueRange).toFloat(),
        valueRange = safeRange,
        steps = steps,
        showKeyPoints = showKeyPoints,
        enabled = enabled,
        onClick = onClick,
        holdDownState = holdDownState,
        onValueChange = { next ->
            onValueChange(next.toInt().coerceIn(valueRange))
        }
    )
}

internal fun formatMsAsSecondsInput(ms: Int): String =
    String.format(java.util.Locale.US, "%.2f", ms.coerceAtLeast(0) / 1_000f)

internal fun parseSecondsInputToMs(text: String, minMs: Int, maxMs: Int): Int? {
    val seconds = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    val ms = (seconds * 1_000f).toInt()
    if (ms !in minMs..maxMs) return null
    return ms
}

@Composable
internal fun SettingsSecondsInputDialog(
    show: Boolean,
    title: String,
    valueMs: Int,
    minMs: Int,
    maxMs: Int,
    onDismissRequest: () -> Unit,
    onSave: (Int) -> Unit,
    summary: String? = null
) {
    var text by remember(show, valueMs) { mutableStateOf(formatMsAsSecondsInput(valueMs)) }
    val parsedMs = parseSecondsInputToMs(text, minMs, maxMs)
    EllaMiuixDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            EllaMiuixDialogActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_confirm),
                onCancel = onDismissRequest,
                onConfirm = {
                    val next = parsedMs ?: return@EllaMiuixDialogActions
                    onSave(next)
                    onDismissRequest()
                }
            )
        }
    }
}
