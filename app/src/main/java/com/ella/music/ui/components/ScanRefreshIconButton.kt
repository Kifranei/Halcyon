package com.ella.music.ui.components

import androidx.compose.foundation.clickable
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

@Composable
fun ScanRefreshIconButton(
    enabled: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    contentDescription: String = stringResource(R.string.library_refresh)
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onScan),
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
