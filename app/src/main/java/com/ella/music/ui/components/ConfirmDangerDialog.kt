package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R

@Composable
fun ConfirmDangerDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = confirmText ?: stringResource(R.string.common_delete),
            confirmDangerous = true,
            onCancel = onDismiss,
            onConfirm = onConfirm
        )
    }
}
