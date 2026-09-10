package com.ella.music.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import com.ella.music.ui.components.SafeDropdownPositionProvider

@Composable
internal fun RatingFilterMenu(
    selection: HomeRatingFilterSelection,
    onSelectionChange: (HomeRatingFilterSelection) -> Unit,
    showFavorite: Boolean = true,
    showRating: Boolean = true,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    if (!showFavorite && !showRating) return
    var menuVisible by remember { mutableStateOf(false) }
    val active = selection.hasAnyFilter() || menuVisible
    val favoriteLabel = stringResource(R.string.rating_filter_hearted_songs)
    val allRatingsLabel = stringResource(R.string.rating_filter_all)
    val allStarsLabel = stringResource(R.string.rating_filter_rated)
    val starLabels = List(5) { index ->
        stringResource(R.string.rating_filter_star, index + 1)
    }
    val latestSelection by rememberUpdatedState(selection)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val groups = remember(
        selection,
        showFavorite,
        showRating,
        favoriteLabel,
        allRatingsLabel,
        allStarsLabel,
        starLabels
    ) {
        buildRatingFilterGroups(
            selection = selection,
            showFavorite = showFavorite,
            showRating = showRating,
            favoriteLabel = favoriteLabel,
            allRatingsLabel = allRatingsLabel,
            allStarsLabel = allStarsLabel,
            starLabel = { starLabels[it - 1] }
        )
    }
    val entries = groups.map { group ->
        DropdownEntry(
            items = group.rows.map { row ->
                DropdownItem(
                    text = row.text,
                    selected = row.selected,
                    onClick = { latestOnSelectionChange(row.apply(latestSelection)) }
                )
            }
        )
    }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    var isHoldDown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                val next = !menuVisible
                menuVisible = next
                if (next) {
                    isHoldDown = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            },
            holdDownState = isHoldDown,
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Filter,
                contentDescription = stringResource(R.string.rating_filter_menu),
                tint = if (active) MiuixTheme.colorScheme.primary else tint,
                modifier = Modifier.size(24.dp)
            )
        }
        if (entries.any { it.items.isNotEmpty() }) {
            WindowListPopup(
                show = menuVisible,
                popupPositionProvider = SafeDropdownPositionProvider,
                alignment = PopupPositionProvider.Align.Start,
                maxHeight = 360.dp,
                onDismissRequest = { menuVisible = false },
                onDismissFinished = { isHoldDown = false }
            ) {
                ListPopupColumn {
                    val lastEntryIdx = entries.lastIndex
                    entries.forEachIndexed { entryIdx, entry ->
                        val lastItemIdx = entry.items.lastIndex
                        val isFirstEntry = entryIdx == 0
                        val isLastEntry = entryIdx == lastEntryIdx
                        entry.items.forEachIndexed { itemIdx, option ->
                            key(entryIdx, itemIdx) {
                                DropdownImpl(
                                    item = option,
                                    optionSize = entry.items.size,
                                    isSelected = option.selected,
                                    index = itemIdx,
                                    enabled = entry.enabled && option.enabled,
                                    isFirst = isFirstEntry && itemIdx == 0,
                                    isLast = isLastEntry && itemIdx == lastItemIdx,
                                    onSelectedIndexChange = {
                                        currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        option.onClick?.invoke()
                                    }
                                )
                            }
                        }
                        if (entryIdx != lastEntryIdx) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                thickness = 1.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class RatingFilterMenuRow(
    val text: String,
    val selected: Boolean,
    val apply: (HomeRatingFilterSelection) -> HomeRatingFilterSelection
)

internal data class RatingFilterMenuGroup(
    val rows: List<RatingFilterMenuRow>
)

internal fun buildRatingFilterGroups(
    selection: HomeRatingFilterSelection,
    showFavorite: Boolean,
    showRating: Boolean,
    favoriteLabel: String,
    allRatingsLabel: String,
    allStarsLabel: String,
    starLabel: (Int) -> String
): List<RatingFilterMenuGroup> = buildList {
    if (showFavorite) {
        add(
            RatingFilterMenuGroup(
                rows = listOf(
                    RatingFilterMenuRow(
                        text = favoriteLabel,
                        selected = selection.hasFavoriteFilter(),
                        apply = { it.toggleFavoriteFilter() }
                    )
                )
            )
        )
    }
    if (showRating) {
        add(
            RatingFilterMenuGroup(
                rows = buildList {
                    add(
                        RatingFilterMenuRow(
                            text = allRatingsLabel,
                            selected = selection.isUnratedIncluded(),
                            apply = { it.toggleUnrated() }
                        )
                    )
                    add(
                        RatingFilterMenuRow(
                            text = allStarsLabel,
                            selected = selection.areAllStarsIncluded(),
                            apply = { it.toggleAllStars() }
                        )
                    )
                    (1..5).forEach { rating ->
                        add(
                            RatingFilterMenuRow(
                                text = starLabel(rating),
                                selected = selection.isRatingIncluded(rating),
                                apply = { it.toggleStar(rating) }
                            )
                        )
                    }
                }
            )
        )
    }
}
