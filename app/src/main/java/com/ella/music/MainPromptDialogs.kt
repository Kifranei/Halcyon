package com.ella.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.EllaMiuixTripleDialogActions

@Composable
internal fun LocalPlaylistScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onScan: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.local_playlist_scan_title),
        summary = stringResource(R.string.local_playlist_scan_message),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.local_playlist_scan_skip),
            confirmText = stringResource(R.string.local_playlist_scan_confirm),
            onCancel = onDismiss,
            onConfirm = onScan
        )
    }
}

@Composable
internal fun InitialScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCustomFolderScan: () -> Unit,
    onMediaLibraryScan: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.initial_scan_title),
        summary = stringResource(R.string.initial_scan_message),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixTripleDialogActions(
            firstText = stringResource(R.string.common_cancel),
            secondText = stringResource(R.string.common_custom),
            confirmText = stringResource(R.string.common_confirm),
            onFirst = onDismiss,
            onSecond = onCustomFolderScan,
            onConfirm = onMediaLibraryScan
        )
    }
}

@Composable
internal fun FullTagSearchPromptDialog(
    show: Boolean,
    onChoose: (Boolean) -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.full_tag_search_prompt_title),
        summary = stringResource(R.string.full_tag_search_prompt_message),
        onDismissRequest = { onChoose(false) }
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.full_tag_search_prompt_basic),
            confirmText = stringResource(R.string.full_tag_search_prompt_enable),
            onCancel = { onChoose(false) },
            onConfirm = { onChoose(true) }
        )
    }
}

@Composable
internal fun WebDavCloudRestorePromptDialog(
    show: Boolean,
    restoring: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.settings_backup_webdav_newer_title),
        summary = stringResource(R.string.settings_backup_webdav_newer_message),
        onDismissRequest = { if (!restoring) onDismiss() }
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.settings_backup_webdav_restore_now),
            onCancel = { if (!restoring) onDismiss() },
            onConfirm = { if (!restoring) onRestore() }
        )
    }
}
