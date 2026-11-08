package com.ella.music.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ella.music.ui.components.EllaGlassListPopup
import com.ella.music.ui.components.rememberDropdownMaxHeight
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.glass.GlassPopupItem

@Composable
internal fun HomeLibrarySourceMenu(
    show: Boolean,
    options: List<Pair<String, String>>,
    selectedSource: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return
    val hapticFeedback = LocalHapticFeedback.current
    val latestOnSelect by rememberUpdatedState(onSelect)
    val maxDropdownHeight = rememberDropdownMaxHeight()
    val items = options.map { (source, title) ->
        DropdownItem(
            text = title,
            selected = source == selectedSource,
            onClick = { latestOnSelect(source) }
        )
    }
    EllaGlassListPopup(
        show = show,
        maxHeight = maxDropdownHeight,
        onDismissRequest = onDismiss
    ) {
        items.forEachIndexed { index, option ->
            key(option.text, index) {
                GlassPopupItem(
                    text = option.text,
                    selected = option.selected,
                    enabled = option.enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        option.onClick?.invoke()
                    }
                )
            }
        }
    }
}
