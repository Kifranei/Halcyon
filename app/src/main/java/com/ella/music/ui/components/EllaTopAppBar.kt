package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EllaSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    centeredTitle: Boolean = false,
    titleStartPadding: Dp = 64.dp,
    titleEndPadding: Dp = 128.dp,
    titleWindowInsetsPadding: Boolean = true,
    bottomContent: @Composable () -> Unit = {},
) {
    if (centeredTitle) {
        SmallTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = titleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent
        )
        return
    }

    Box(modifier = modifier) {
        SmallTopAppBar(
            title = "",
            color = color,
            titleColor = titleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent
        )
        Text(
            text = title,
            color = titleColor,
            maxLines = 1,
            fontSize = MiuixTheme.textStyles.title3.fontSize,
            fontWeight = FontWeight.Medium,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .then(if (titleWindowInsetsPadding) Modifier.windowInsetsPadding(WindowInsets.systemBars) else Modifier)
                .padding(start = titleStartPadding, end = titleEndPadding, top = 12.dp)
        )
    }
}
