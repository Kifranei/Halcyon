package com.ella.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions

/**
 * One-time notice shown after the app was installed over a different version without clearing
 * data. [previousVersionName] is null when the old install predates the version marker.
 */
@Composable
internal fun AppUpgradeNoticeDialog(
    show: Boolean,
    previousVersionName: String?,
    onClearData: () -> Unit,
    onLater: () -> Unit
) {
    // Unknown old version: the plain title already says "older version", so don't repeat it in brackets.
    val title = previousVersionName?.let { stringResource(R.string.app_upgrade_title, it) }
        ?: stringResource(R.string.app_upgrade_title_unknown)
    EllaMiuixDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.app_upgrade_message, BuildConfig.VERSION_NAME),
        onDismissRequest = onLater
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.app_upgrade_later),
            confirmText = stringResource(R.string.app_upgrade_clear_data),
            onCancel = onLater,
            onConfirm = onClearData
        )
    }
}

/**
 * Opens the system App info page, where storage (and therefore all app data) can be cleared.
 * Returns false when no activity can handle it, so the caller can fall back to the in-app
 * maintenance screen.
 */
internal fun openAppDetailsSettings(context: Context): Boolean = runCatching {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    Toast.makeText(context, R.string.app_upgrade_clear_data_hint, Toast.LENGTH_LONG).show()
    true
}.getOrDefault(false)
