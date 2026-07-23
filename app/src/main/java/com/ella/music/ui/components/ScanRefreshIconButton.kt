package com.ella.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanRefreshIconButton(
    enabled: Boolean,
    onScan: () -> Unit,
    onDeepRescan: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    contentDescription: String = stringResource(R.string.library_refresh)
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onScan,
                onLongClick = onDeepRescan
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.SearchDevice,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.size(iconSize)
        )
    }
}
