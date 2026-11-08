package com.ella.music.ui.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.EllaGlassListPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.wallpaperAwareCardColor
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun onlineSourceCardColor(): Color = when {
    isAppWallpaperVisible() -> wallpaperAwareCardColor(defaultAlpha = 0.42f)
    MiuixTheme.colorScheme.background.luminance() < 0.5f -> MiuixTheme.colorScheme.surfaceContainer
    else -> Color.White
}

internal val OnlineSourceOkColor = Color(0xFF34C759)
internal val OnlineSourceErrorColor = Color(0xFFE5484D)

/**
 * Shared source status card body: source name, a colored status dot + status word with a
 * trailing metadata line, and a row of capability/platform tags (LX & MusicFree cards).
 */
@Composable
internal fun OnlineSourceCardBody(
    title: String,
    showDot: Boolean,
    dotColor: Color,
    statusWord: String,
    statusRest: String,
    tags: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            if (showDot) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = dotColor)) { append(statusWord) }
                    if (statusRest.isNotEmpty()) {
                        append(" · ")
                        withStyle(SpanStyle(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)) {
                            append(statusRest)
                        }
                    }
                },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (tags.isNotEmpty()) {
            Text(
                text = tags.joinToString(" · "),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Reads a metadata value from a source/plugin script without running it: `@key` comment
 * tags (LX script headers) first, then `key:`/`key =` object fields (MusicFree exports).
 * Only the head of the script is scanned so unrelated code cannot spoof the header.
 */
internal fun scriptMetadataValue(script: String, key: String): String {
    val head = script.take(4000)
    val escapedKey = Regex.escape(key)
    Regex("""(?m)^\s*(?:[/\*]+\s*)?@""" + escapedKey + """\s+(.+?)\s*$""")
        .find(head)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return listOf(
        """\b""" + escapedKey + """\s*:\s*['"]([^'"]+)['"]""",
        """\b""" + escapedKey + """\s*=\s*['"]([^'"]+)['"]""",
        """['"]""" + escapedKey + """['"]\s*:\s*['"]([^'"]+)['"]"""
    ).firstNotNullOfOrNull { pattern ->
        Regex(pattern).find(head)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

/** "v3.2.20 · 作者" style summary from a source script's version/author metadata. */
internal fun scriptCreditSummary(script: String?): String? {
    if (script.isNullOrBlank()) return null
    return listOf(scriptMetadataValue(script, "version"), scriptMetadataValue(script, "author"))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { null }
}

@Composable
internal fun os4NeutralButtonColors(selected: Boolean = false): ButtonColors {
    val neutral = top.yukonga.miuix.kmp.glass.GlassTabRowDefaults.neutralColors()
    return ButtonDefaults.buttonColors(
        color = if (selected) neutral.selectedContainerColor else neutral.restingContainerColor,
        disabledColor = MiuixTheme.colorScheme.disabledSecondaryVariant,
        contentColor = if (selected) neutral.selectedContentColor else neutral.contentColor,
        disabledContentColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    )
}

/** Shared by LX and MusicFree so spacing, input styling and provider selection stay identical. */
@Composable
internal fun OnlineSearchControls(
    providers: List<String>,
    selectedIndex: Int,
    onProviderSelected: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String = stringResource(R.string.lx_online_search_placeholder)
) {
    var menuVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (providers.isNotEmpty()) {
            Box {
                val providerPillColors = top.yukonga.miuix.kmp.glass.GlassTabRowDefaults.neutralColors()
                Box(
                    modifier = Modifier
                        .widthIn(max = 128.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(providerPillColors.selectedContainerColor)
                        .clickable {
                            menuVisible = !menuVisible
                            if (menuVisible) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = providers.getOrElse(selectedIndex) { providers.first() },
                        color = providerPillColors.selectedContentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                EllaGlassListPopup(
                    show = menuVisible,
                    onDismissRequest = { menuVisible = false }
                ) {
                    providers.forEachIndexed { index, label ->
                        GlassPopupItem(
                            text = label,
                            selected = index == selectedIndex,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onProviderSelected(index)
                                menuVisible = false
                            }
                        )
                    }
                }
            }
        }
        OnlineTextField(
            value = query,
            onValueChange = onQueryChange,
            onSearch = onSearch,
            placeholder = placeholder,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun OnlineSearchPaginationEffect(
    listState: LazyListState,
    requests: OnlineProviderSearchRequests,
    resultCount: Int,
    isBusy: Boolean,
    onLoadMore: (automatic: Boolean) -> Unit
) {
    val currentCount by rememberUpdatedState(resultCount)
    val currentBusy by rememberUpdatedState(isBusy)
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(requests.revision) { listState.scrollToItem(0) }
    LaunchedEffect(listState, requests) {
        snapshotFlow {
            val nearEnd = requests.hasMore && !requests.isLoading && !requests.failed && !currentBusy &&
                currentCount > 0 &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= currentCount - 5
            if (nearEnd) currentCount else null
        }.distinctUntilChanged().collect { count ->
            if (count != null) loadMore(true)
        }
    }
}

@Composable
internal fun OnlineSearchPageFooter(requests: OnlineProviderSearchRequests, onLoadMore: () -> Unit) {
    val label = when {
        requests.isLoading -> R.string.lx_online_processing
        requests.failed -> R.string.online_search_page_retry
        requests.hasMore -> R.string.online_search_load_more
        else -> R.string.online_search_no_more
    }
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(enabled = requests.hasMore && !requests.isLoading, onClick = onLoadMore),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(label), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
