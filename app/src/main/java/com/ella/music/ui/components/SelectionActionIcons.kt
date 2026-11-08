package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlayNextActionIcon(
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier.size(27.dp)
) {
    Icon(
        painter = painterResource(R.drawable.ic_play_next_add),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

@Composable
internal fun AddToPlaylistActionIcon(
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier.size(27.dp)
) {
    Icon(
        painter = painterResource(R.drawable.ic_playlist_add),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

/** Sits immediately before play-next on song and category multi-select bars (#771). */
@Composable
internal fun SelectionBatchEditButton(
    onClick: () -> Unit,
    tint: Color = MiuixTheme.colorScheme.primary,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = ActionMenuCommonIcons.edit,
            contentDescription = stringResource(R.string.category_batch_edit),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
