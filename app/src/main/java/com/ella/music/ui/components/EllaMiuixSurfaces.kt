package com.ella.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.ella.music.data.SettingsManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

val LocalInBottomSheet = androidx.compose.runtime.compositionLocalOf { false }

val LocalInDialog = androidx.compose.runtime.compositionLocalOf { false }

/** True when the active Miuix scheme reads as dark. */
@Composable
private fun ellaSchemeIsDark(): Boolean =
    MiuixTheme.colorScheme.background.luminance() < 0.5f

/** Opaque settings-page canvas; bottom sheets must not reveal an enabled wallpaper or flow. */
@Composable
fun ellaBottomSheetCanvasColor(): Color = ellaPageCanvasColor()

/** Dialog canvas: always page `background` (white in light, dark surface in dark). */
@Composable
fun ellaDialogCanvasColor(): Color = MiuixTheme.colorScheme.background

@Composable
fun ellaOverlayCardColor(): Color {
    val scheme = MiuixTheme.colorScheme
    val dark = ellaSchemeIsDark()
    return when {
        LocalInBottomSheet.current -> {
            // Dark: original grey-ish container cards on background canvas
            if (dark) scheme.secondaryContainer
            // Light: white/elevated card = the lighter of the pair
            else listOf(scheme.background, scheme.secondaryContainer).maxBy { it.luminance() }
        }
        LocalInDialog.current -> {
            // Dark: original
            if (dark) scheme.secondaryContainer
            // Light: grey card on white dialog = the darker of the pair
            else listOf(scheme.background, scheme.secondaryContainer).minBy { it.luminance() }
        }
        else -> scheme.surfaceContainer
    }
}

@Composable
fun ellaOverlayCardColors() = CardDefaults.defaultColors(color = ellaOverlayCardColor())

data class EllaMiuixAction(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val weight: Float = 1f,
    val dangerous: Boolean = false,
    val enabled: Boolean = true
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EllaMiuixBottomSheet(
    show: Boolean,
    title: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    enableNestedScroll: Boolean = true,
    insideMargin: DpSize = DpSize(12.dp, 18.dp),
    content: @Composable () -> Unit
) {
    // MIUIX window surfaces create a separate composition. Re-provide the caller's adjusted
    // density so global interface/font scaling also reaches player and lyric action sheets.
    val inheritedDensity = LocalDensity.current
    WindowBottomSheet(
        show = show,
        title = title,
        startAction = startAction,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = enableNestedScroll,
        cornerRadius = 28.dp,
        insideMargin = insideMargin,
        backgroundColor = ellaBottomSheetCanvasColor(),
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalDensity provides inheritedDensity,
                LocalSettingsCardFrosting provides null,
                LocalSharedAppBackgroundVisible provides false,
                LocalInBottomSheet provides true
            ) {
                val context = LocalContext.current
                val systemBarsMode by SettingsManager.getInstance(context).systemBarsMode.collectAsState(
                    initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
                )
                val systemBarsReserveSpace by SettingsManager.getInstance(context).systemBarsReserveSpace.collectAsState(
                    initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
                )
                val shouldReserveNav = systemBarsReserveSpace || systemBarsMode !in setOf(
                    SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION,
                    SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
                )
                ApplyHalcyonSystemBarsToCurrentWindow()
                Box(
                    modifier = if (shouldReserveNav) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    } else {
                        Modifier
                    }
                ) {
                    content()
                }
            }
        }
    )
}

@Composable
fun EllaMiuixDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val inheritedDensity = LocalDensity.current
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        backgroundColor = ellaDialogCanvasColor(),
        insideMargin = DpSize(22.dp, 20.dp),
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalSettingsCardFrosting provides null,
                LocalSharedAppBackgroundVisible provides false,
                LocalInDialog provides true,
                LocalDensity provides inheritedDensity,
            ) {
                ApplyHalcyonSystemBarsToCurrentWindow()
                content()
            }
        }
    )
}

@Composable
fun EllaMiuixDialogActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmDangerous: Boolean = false,
    cancelDangerous: Boolean = false,
    confirmEnabled: Boolean = true
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = cancelText, onClick = onCancel, dangerous = cancelDangerous),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true, dangerous = confirmDangerous, enabled = confirmEnabled)
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EllaMiuixChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 8.dp,
    content: @Composable RowScope.(contentColor: Color) -> Unit
) {
    val chipBackground = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
    }
    val chipContentColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(chipBackground)
            .then(clickModifier)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        content = { content(chipContentColor) }
    )
}

@Composable
fun EllaMiuixChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 8.dp
) {
    EllaMiuixChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding
    ) { contentColor ->
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Shared selection indicator: a circular container that shows the miuix check icon when selected.
 * Replaces the previously duplicated hand-rolled `Text("✓")` boxes.
 */
@Composable
fun SelectionCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    selectedColor: Color = MiuixTheme.colorScheme.primary,
    unselectedColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    checkColor: Color = MiuixTheme.colorScheme.onPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) selectedColor else unselectedColor),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(size * 0.66f)
            )
        }
    }
}

@Composable
fun EllaMiuixSurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = if (LocalInBottomSheet.current || LocalInDialog.current) ellaOverlayCardColor() else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun EllaMiuixListItem(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
fun EllaMiuixBadge(
    text: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 2.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(color)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        color = contentColor,
        fontWeight = fontWeight,
        maxLines = 1,
        fontSize = fontSize
    )
}

@Composable
fun EllaMiuixSheetActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = cancelText, onClick = onCancel),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true)
        ),
        modifier = modifier
    )
}

@Composable
fun EllaMiuixActionRow(
    actions: List<EllaMiuixAction>,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp
) {
    if (actions.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        actions.forEach { action ->
            val buttonModifier = Modifier.weight(action.weight)
            if (action.dangerous) {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.error,
                        textColor = MiuixTheme.colorScheme.onError
                    )
                )
            } else if (action.primary) {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        textColor = MiuixTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
fun EllaMiuixSheetColumn(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 560.dp,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 12.dp,
    spacing: Dp = 6.dp,
    showHandle: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .navigationBarsPadding()
            .then(scrollModifier)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        if (showHandle) EllaMiuixSheetHandle()
        content()
    }
}

@Composable
fun EllaMiuixSheetHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f))
        )
    }
}

@Composable
fun EllaMiuixMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    val titleColor = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface
    val iconTint = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurfaceVariantActions
    BasicComponent(
        title = text,
        titleColor = BasicComponentDefaults.titleColor(color = titleColor),
        summary = subtitle,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        startAction = icon?.let { image ->
            {
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    )
}

@Composable
fun EllaMiuixActionMenuGroup(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (insideMargin == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = ellaOverlayCardColors(),
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = ellaOverlayCardColors(),
            insideMargin = insideMargin,
            content = content
        )
    }
}

/**
 * One rounded card holding several rows, matching the card used by the Super Island lyric
 * settings sheet. Used for sheet-hosted preference groups and single-choice pickers.
 */
@Composable
fun EllaSheetCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = ellaOverlayCardColors()
    ) {
        Column(content = content)
    }
}

/** Single-choice list inside one [EllaSheetCardGroup]; the selected row shows the Miuix Ok glyph. */
@Composable
fun <T> EllaCheckOptionGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    EllaSheetCardGroup(modifier) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
                    else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = null,
                        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp).size(22.dp)
                    )
                }
            }
        }
    }
}
