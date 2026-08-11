package com.ella.music.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun rememberDynamicCoverFolderPicker(
    currentFolders: String,
    settingsManager: SettingsManager
): ActivityResultLauncher<Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, readOnly)
        }
        val updated = (currentFolders.lineSequence().map(String::trim) + sequenceOf(uri.toString()))
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
        scope.launch { settingsManager.setDynamicCoverCustomFolders(updated) }
        Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_folder_saved), Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun rememberMusicVideoFolderPicker(
    currentFolders: String,
    settingsManager: SettingsManager
): ActivityResultLauncher<Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, readOnly)
        }
        val updated = (currentFolders.lineSequence().map(String::trim) + sequenceOf(uri.toString()))
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
        scope.launch { settingsManager.setMusicVideoCustomFolders(updated) }
        Toast.makeText(context, context.getString(R.string.settings_music_video_folder_saved), Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun rememberAppearanceImagePicker(
    currentUri: String,
    imageName: String,
    onImagePersisted: suspend (String) -> Unit
): ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(
        GetContentImageContract()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.persistImageReadPermission(uri)
        scope.launch {
            val persisted = context.copyCustomImageIntoApp(uri, imageName)
            if (persisted == null) {
                Toast.makeText(context, context.getString(R.string.settings_custom_image_save_failed), Toast.LENGTH_SHORT).show()
            } else {
                context.deletePersistedCustomImage(currentUri)
                onImagePersisted(persisted)
            }
        }
    }
}

/**
 * MIUI Gallery exposes its album sidebar through ACTION_GET_CONTENT. OpenDocument works with
 * most document providers but hides the Gallery provider on some HyperOS builds. The selected
 * image is copied into app storage immediately, so a persistable document grant is not needed.
 */
private class GetContentImageContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent = Intent(
        Intent.ACTION_GET_CONTENT
    ).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = input.singleOrNull() ?: "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
internal fun rememberDynamicCoverPermissionLauncher(
    settingsManager: SettingsManager
): ActivityResultLauncher<String> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setDynamicCoverEnabled(granted) }
        if (granted) {
            Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_enabled), Toast.LENGTH_SHORT).show()
        } else {
            handleDynamicCoverPermissionDenied(context)
        }
    }
}

internal fun setDynamicCoverEnabled(
    context: Context,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    permissionLauncher: ActivityResultLauncher<String>,
    enabled: Boolean
) {
    if (!enabled) {
        scope.launch { settingsManager.setDynamicCoverEnabled(false) }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch { settingsManager.setDynamicCoverEnabled(true) }
        } else {
            scope.launch { settingsManager.setDynamicCoverEnabled(false) }
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        }
    } else {
        scope.launch { settingsManager.setDynamicCoverEnabled(true) }
    }
}

@Composable
internal fun rememberMusicVideoSyncPermissionLauncher(
    settingsManager: SettingsManager
): ActivityResultLauncher<String> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setMusicVideoSyncEnabled(granted) }
        if (!granted) handleDynamicCoverPermissionDenied(context)
    }
}

internal fun setMusicVideoSyncEnabled(
    context: Context,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    permissionLauncher: ActivityResultLauncher<String>,
    enabled: Boolean
) {
    if (!enabled) {
        scope.launch { settingsManager.setMusicVideoSyncEnabled(false) }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch { settingsManager.setMusicVideoSyncEnabled(true) }
        } else {
            scope.launch { settingsManager.setMusicVideoSyncEnabled(false) }
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        }
    } else {
        scope.launch { settingsManager.setMusicVideoSyncEnabled(true) }
    }
}

private fun handleDynamicCoverPermissionDenied(context: Context) {
    val activity = context as? Activity
    val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        true
    }
    if (!shouldShowRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_permission_grant), Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    } else {
        Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_permission_denied), Toast.LENGTH_SHORT).show()
    }
}
