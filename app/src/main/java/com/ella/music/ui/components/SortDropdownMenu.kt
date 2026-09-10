package com.ella.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.listmodel.SortDirection
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.basic.Check

internal data class SortDropdownItem(
    val text: String,
    val selected: Boolean,
    val summary: String? = null,
    val onClick: () -> Unit,
    val direction: SortDirection? = null,
    val onSelectAscending: (() -> Unit)? = null,
    val onSelectDescending: (() -> Unit)? = null
)

internal data class DirectionalSortField<T>(
    val field: T,
    val text: String,
    val defaultDirection: SortDirection = SortDirection.Ascending,
    val supportsAscending: Boolean = true,
    val supportsDescending: Boolean = true
)

/**
 * Adapts screens which still persist their sort selection as an enum with separate ascending and
 * descending entries. Keeping that persisted representation avoids a settings migration, while
 * presenting every such pair as one Miuix row with the direction controls on the right.
 */
internal data class DirectionalSortModeField<M>(
    val text: String,
    val ascendingMode: M? = null,
    val descendingMode: M? = null
)

internal fun <T> directionalSortDropdownItems(
    fields: List<DirectionalSortField<T>>,
    selectedField: T,
    selectedDirection: SortDirection,
    ascendingSummary: String,
    descendingSummary: String,
    onSelect: (field: T, direction: SortDirection) -> Unit
): List<SortDropdownItem> =
    fields.map { option ->
        val selected = option.field == selectedField
        SortDropdownItem(
            text = option.text,
            selected = selected,
            summary = if (selected) {
                if (selectedDirection == SortDirection.Descending) descendingSummary else ascendingSummary
            } else {
                null
            },
            direction = if (selected) selectedDirection else option.defaultDirection,
            onSelectAscending = option.supportsAscending.takeIf { it }?.let {
                { onSelect(option.field, SortDirection.Ascending) }
            },
            onSelectDescending = option.supportsDescending.takeIf { it }?.let {
                { onSelect(option.field, SortDirection.Descending) }
            },
            onClick = {
                onSelect(option.field, if (selected) selectedDirection else option.defaultDirection)
            }
        )
    }

internal fun <M> directionalSortModeDropdownItems(
    fields: List<DirectionalSortModeField<M>>,
    selectedMode: M,
    onSelect: (M) -> Unit
): List<SortDropdownItem> =
    fields.mapNotNull { option ->
        val ascendingMode = option.ascendingMode
        val descendingMode = option.descendingMode
        if (ascendingMode == null && descendingMode == null) return@mapNotNull null
        val selectedDirection = when (selectedMode) {
            descendingMode -> SortDirection.Descending
            else -> SortDirection.Ascending
        }
        val selected = selectedMode == ascendingMode || selectedMode == descendingMode
        val defaultMode = ascendingMode ?: descendingMode!!
        SortDropdownItem(
            text = option.text,
            selected = selected,
            direction = if (selected) selectedDirection else {
                if (ascendingMode != null) SortDirection.Ascending else SortDirection.Descending
            },
            onSelectAscending = ascendingMode?.let { mode -> { onSelect(mode) } },
            onSelectDescending = descendingMode?.let { mode -> { onSelect(mode) } },
            onClick = { onSelect(if (selected) selectedMode else defaultMode) }
        )
    }

@Composable
internal fun randomSortDropdownItem(
    selected: Boolean,
    onSelect: () -> Unit
): SortDropdownItem {
    val randomText = stringResource(R.string.common_sort_random)
    val resortText = stringResource(R.string.common_sort_random_resort)
    return SortDropdownItem(
        text = randomText,
        selected = selected,
        summary = resortText,
        onClick = {
            com.ella.music.ui.LibrarySortUiState.reshuffleRandomSort()
            onSelect()
        }
    )
}

@Composable
internal fun SortDropdownMenu(
    items: List<SortDropdownItem>,
    modifier: Modifier = Modifier,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    contentDescription: String = stringResource(R.string.common_sort)
) {
    if (items.isEmpty()) return
    var menuVisible by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val sortMenuStyle by settingsManager.sortMenuStyle.collectAsState(initial = SettingsManager.SORT_MENU_STYLE_DROPDOWN)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    menuVisible = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Sort,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        if (sortMenuStyle == SettingsManager.SORT_MENU_STYLE_BOTTOM_SHEET) {
            SortBottomSheet(
                show = menuVisible,
                items = items,
                onDismissRequest = { menuVisible = false }
            )
        } else {
            SortListPopup(
                show = menuVisible,
                items = items,
                alignment = alignment,
                onDismissRequest = { menuVisible = false }
            )
        }
    }
}

private fun PopupPositionProvider.Align.resolve(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return this
    return when (this) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

internal val SafeDropdownPositionProvider = object : PopupPositionProvider {
    private val margins = PaddingValues(horizontal = 0.dp, vertical = 8.dp)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val isEnd = alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End
        val offsetX = if (isEnd) {
            anchorBounds.right - popupContentSize.width - popupMargin.right
        } else {
            anchorBounds.left + popupMargin.left
        }

        val spaceBelow = windowBounds.bottom - anchorBounds.bottom - popupMargin.bottom
        val spaceAbove = anchorBounds.top - windowBounds.top - popupMargin.top

        val offsetY = if (spaceBelow >= popupContentSize.height) {
            anchorBounds.bottom + popupMargin.bottom
        } else if (spaceAbove >= popupContentSize.height) {
            anchorBounds.top - popupContentSize.height - popupMargin.top
        } else if (spaceBelow >= spaceAbove) {
            anchorBounds.bottom + popupMargin.bottom
        } else {
            anchorBounds.top - popupContentSize.height - popupMargin.top
        }

        val minX = windowBounds.left
        val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(minX)
        val minY = windowBounds.top + popupMargin.top
        val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom).coerceAtLeast(minY)

        return IntOffset(
            x = offsetX.coerceIn(minX, maxX),
            y = offsetY.coerceIn(minY, maxY),
        )
    }

    override fun getMargins(): PaddingValues = margins
}

@Composable
private fun SortListPopup(
    show: Boolean,
    items: List<SortDropdownItem>,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    onDismissRequest: () -> Unit
) {
    val view = LocalView.current
    val ascendingLabel = stringResource(R.string.common_sort_ascending)
    val descendingLabel = stringResource(R.string.common_sort_descending)
    WindowListPopup(
        show = show,
        popupPositionProvider = SafeDropdownPositionProvider,
        alignment = alignment,
        maxHeight = 360.dp,
        onDismissRequest = onDismissRequest
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                val hasDirectionChoices = item.onSelectAscending != null || item.onSelectDescending != null
                val summary = when {
                    hasDirectionChoices ->
                        if (item.direction == SortDirection.Descending) descendingLabel else ascendingLabel
                    else -> item.summary
                }
                DropdownImpl(
                    item = DropdownItem(
                        text = item.text,
                        summary = summary,
                        selected = item.selected
                    ),
                    optionSize = items.size,
                    isSelected = item.selected,
                    index = index,
                    onSelectedIndexChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val togglingSelectedField = item.selected &&
                            item.onSelectAscending != null &&
                            item.onSelectDescending != null
                        if (togglingSelectedField) {
                            if (item.direction == SortDirection.Descending) {
                                item.onSelectAscending()
                            } else {
                                item.onSelectDescending()
                            }
                        } else {
                            item.onClick()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SortDirectionPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (active) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    val view = LocalView.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
    }
}

@Composable
private fun SortBottomSheet(
    show: Boolean,
    items: List<SortDropdownItem>,
    onDismissRequest: () -> Unit
) {
    val view = LocalView.current
    val ascendingLabel = stringResource(R.string.common_sort_ascending)
    val descendingLabel = stringResource(R.string.common_sort_descending)
    EllaMiuixBottomSheet(
        show = show,
        title = stringResource(R.string.common_sort),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEach { item ->
                val hasDirectionChoices = item.onSelectAscending != null || item.onSelectDescending != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val togglingSelectedField = item.selected &&
                                item.onSelectAscending != null &&
                                item.onSelectDescending != null
                            if (togglingSelectedField) {
                                if (item.direction == SortDirection.Descending) {
                                    item.onSelectAscending.invoke()
                                } else {
                                    item.onSelectDescending.invoke()
                                }
                            } else {
                                item.onClick()
                            }
                            onDismissRequest()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.text,
                        fontSize = 16.sp,
                        fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (item.selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasDirectionChoices) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SortDirectionPill(
                                text = ascendingLabel,
                                active = item.selected && item.direction != SortDirection.Descending,
                                onClick = {
                                    item.onSelectAscending?.invoke() ?: item.onClick()
                                    onDismissRequest()
                                }
                            )
                            SortDirectionPill(
                                text = descendingLabel,
                                active = item.selected && item.direction == SortDirection.Descending,
                                onClick = {
                                    item.onSelectDescending?.invoke() ?: item.onClick()
                                    onDismissRequest()
                                }
                            )
                        }
                    } else {
                        if (!item.summary.isNullOrBlank()) {
                            Text(
                                text = item.summary,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (item.selected) {
                            Icon(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SortDropdownMenuContent(
    items: List<SortDropdownItem>,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    content: @Composable () -> Unit
) {
    if (items.isEmpty()) {
        content()
        return
    }
    var menuVisible by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val sortMenuStyle by settingsManager.sortMenuStyle.collectAsState(initial = SettingsManager.SORT_MENU_STYLE_DROPDOWN)

    Box(modifier = Modifier.clickable {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        menuVisible = true
    }) {
        content()
        if (sortMenuStyle == SettingsManager.SORT_MENU_STYLE_BOTTOM_SHEET) {
            SortBottomSheet(
                show = menuVisible,
                items = items,
                onDismissRequest = { menuVisible = false }
            )
        } else {
            SortListPopup(
                show = menuVisible,
                items = items,
                alignment = alignment,
                onDismissRequest = { menuVisible = false }
            )
        }
    }
}
