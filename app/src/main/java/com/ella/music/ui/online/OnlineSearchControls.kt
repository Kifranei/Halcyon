package com.ella.music.ui.online

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.ApplyHalcyonSystemBarsToCurrentWindow
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.wallpaperAwareCardColor
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun onlineSourceCardColor(): Color = when {
    isAppWallpaperVisible() -> wallpaperAwareCardColor(defaultAlpha = 0.42f)
    MiuixTheme.colorScheme.background.luminance() < 0.5f -> MiuixTheme.colorScheme.surfaceContainer
    else -> Color.White
}

/** Shared by LX and MusicFree so spacing, input styling and provider selection stay identical. */
@Composable
internal fun OnlineSearchControls(
    providers: List<String>,
    selectedIndex: Int,
    onProviderSelected: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    var menuVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (providers.isNotEmpty()) {
            Box {
                Button(modifier = Modifier.widthIn(max = 128.dp), onClick = {
                    menuVisible = !menuVisible
                    if (menuVisible) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }) { Text(providers.getOrElse(selectedIndex) { providers.first() }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                WindowListPopup(
                    show = menuVisible,
                    alignment = PopupPositionProvider.Align.Start,
                    onDismissRequest = { menuVisible = false }
                ) {
                    ApplyHalcyonSystemBarsToCurrentWindow()
                    ListPopupColumn {
                        providers.forEachIndexed { index, label ->
                            val select = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onProviderSelected(index)
                                menuVisible = false
                            }
                            DropdownImpl(
                                item = DropdownItem(text = label, selected = index == selectedIndex, onClick = select),
                                optionSize = providers.size,
                                isSelected = index == selectedIndex,
                                index = index,
                                enabled = true,
                                isFirst = index == 0,
                                isLast = index == providers.lastIndex,
                                onSelectedIndexChange = { select() }
                            )
                        }
                    }
                }
            }
        }
        OnlineTextField(
            value = query,
            onValueChange = onQueryChange,
            onSearch = onSearch,
            placeholder = stringResource(R.string.lx_online_search_placeholder),
            modifier = Modifier.weight(1f)
        )
    }
}
