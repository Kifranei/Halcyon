package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * [WindowSpinnerPreference] equivalent backed by the OS4 [EllaGlassListPopup]: the row keeps
 * the standard preference layout while the option list is a glass dropdown anchored to the row.
 */
@Composable
internal fun EllaSpinnerPreference(
    items: List<DropdownItem>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    EllaSpinnerEntriesPreference(
        entries = listOf(
            DropdownEntry(
                items = items.mapIndexed { index, item ->
                    item.copy(
                        selected = index == selectedIndex,
                        onClick = {
                            onSelectedIndexChange?.invoke(index)
                            item.onClick?.invoke()
                        },
                    )
                }
            )
        ),
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        selectedIndex = selectedIndex,
        items = items,
    )
}

@Composable
internal fun EllaSpinnerEntriesPreference(
    entries: List<DropdownEntry>,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    selectedIndex: Int = -1,
    items: List<DropdownItem> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val actualEnabled = enabled && entries.any { it.items.isNotEmpty() }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val hapticFeedback = LocalHapticFeedback.current
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val setExpanded: (Boolean) -> Unit = { next ->
        if (expanded != next) {
            expanded = next
            currentOnExpandedChange?.invoke(next)
        }
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val value = items.getOrNull(selectedIndex)?.text
                if (showValue && !value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(end = 8.dp),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(actionColor = actionColor)
            }
        },
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        onClick = {
            setExpanded(!expanded)
            if (expanded) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        role = Role.DropdownList,
        holdDownState = expanded,
        enabled = actualEnabled,
    )

    EllaGlassListPopup(
        show = expanded && actualEnabled,
        onDismissRequest = { setExpanded(false) },
        anchorBoundsOverride = rowBounds,
        maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight,
    ) {
        val lastEntryIdx = entries.lastIndex
        entries.forEachIndexed { entryIdx, entry ->
            entry.items.forEach { option ->
                GlassPopupItem(
                    text = option.text,
                    summary = option.summary,
                    selected = option.selected,
                    enabled = entry.enabled && option.enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (collapseOnSelection) setExpanded(false)
                        option.onClick?.invoke()
                    },
                )
            }
            if (entryIdx < lastEntryIdx) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    thickness = 1.dp
                )
            }
        }
    }
}
