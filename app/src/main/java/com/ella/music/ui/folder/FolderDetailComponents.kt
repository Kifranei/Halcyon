package com.ella.music.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.BreadcrumbBar
import com.ella.music.ui.components.BreadcrumbItem
import androidx.compose.ui.focus.focusRequester
import top.yukonga.miuix.kmp.basic.TextField
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CreatePlaylistAndAddSelectedSheet(
    songCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.library_create_playlist_add_count, songCount),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
internal fun ChildFolderRow(
    folder: FolderTreeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val display = rememberFolderDisplaySettings()
    val scale = display.sizePercent / 100f

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(interactionSource = null, indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = (18 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FolderHierarchyCover(folder, Modifier.size((42 * scale).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = (17 * scale).sp,
                lineHeight = (22 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.folder_child_summary, folder.songCount, folder.path),
                fontSize = (13 * scale).sp,
                lineHeight = (17 * scale).sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun FolderBreadcrumbRow(
    crumbs: List<FolderBreadcrumb>,
    currentPath: String,
    onCrumbClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (crumbs.isEmpty()) return
    val current = currentPath.normalizeFolderPath()
    val items = remember(crumbs) {
        crumbs.map { BreadcrumbItem(path = it.path, text = it.label) }
    }
    val highlightIndex = remember(crumbs, current) {
        val idx = crumbs.indexOfLast { it.path.normalizeFolderPath().equals(current, ignoreCase = true) }
        if (idx >= 0) idx else crumbs.lastIndex
    }
    BreadcrumbBar(
        items = items,
        onItemClick = { index ->
            crumbs.getOrNull(index)?.let { onCrumbClick(it.path) }
        },
        highlightIndex = highlightIndex,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        modifier = modifier.fillMaxWidth()
    )
}
