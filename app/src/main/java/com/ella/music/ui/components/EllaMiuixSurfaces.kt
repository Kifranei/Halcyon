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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

val LocalInBottomSheet = androidx.compose.runtime.compositionLocalOf { false }

data class EllaMiuixAction(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val weight: Float = 1f
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
        insideMargin = DpSize(20.dp, 18.dp),
        backgroundColor = MiuixTheme.colorScheme.background,
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalDensity provides inheritedDensity,
                LocalSettingsCardFrosting provides null,
                LocalSharedAppBackgroundVisible provides false,
                LocalInBottomSheet provides true
            ) {
                ApplyHalcyonSystemBarsToCurrentWindow()
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)) {
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
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        backgroundColor = MiuixTheme.colorScheme.background,
        insideMargin = DpSize(22.dp, 20.dp),
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalSettingsCardFrosting provides null,
                LocalSharedAppBackgroundVisible provides false,
                LocalInBottomSheet provides true
            ) {
                content()
            }
        }
    )
}

@Composable
fun EllaMiuixWideDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    portraitActions: @Composable () -> Unit,
    landscapeActions: @Composable () -> Unit
) {
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerDpSize
    val isLandscape = windowSize.width > windowSize.height

    WindowDialog(
        show = show,
        title = if (isLandscape) null else title,
        summary = if (isLandscape) null else summary,
        onDismissRequest = onDismissRequest,
        backgroundColor = MiuixTheme.colorScheme.background,
        insideMargin = DpSize(22.dp, 20.dp),
        modifier = if (isLandscape) modifier.widthIn(max = 560.dp) else modifier,
        content = {
            CompositionLocalProvider(
                LocalSettingsCardFrosting provides null,
                LocalSharedAppBackgroundVisible provides false,
                LocalInBottomSheet provides true
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = title,
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            if (!summary.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = summary,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MiuixTheme.colorScheme.dividerLine)
                                .padding(horizontal = 20.dp)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(
                                space = 12.dp,
                                alignment = Alignment.CenterVertically
                            )
                        ) {
                            landscapeActions()
                        }
                    }
                } else {
                    portraitActions()
                }
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
fun EllaMiuixTripleDialogActions(
    firstText: String,
    secondText: String,
    confirmText: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = firstText, onClick = onFirst),
            EllaMiuixAction(text = secondText, onClick = onSecond),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true)
        ),
        modifier = modifier,
        spacing = 8.dp
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
    color: Color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
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
            if (action.primary) {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    modifier = buttonModifier,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
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
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        content = content
    )
}
