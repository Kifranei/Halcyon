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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.CoverImageCropper
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.GetContentImageContract
import com.ella.music.ui.components.decodeCoverSource
import com.ella.music.ui.components.rememberCoverImageCropperState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    cropTitle: String = androidx.compose.ui.res.stringResource(R.string.song_more_metadata_cover_crop_title),
    enableCrop: Boolean = false,
    defaultRatio: Float? = null,
    onImagePersisted: suspend (String) -> Unit
): ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmapToCrop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCropSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        com.ella.music.ui.components.GetContentImageContract()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.persistImageReadPermission(uri)
        if (enableCrop) {
            scope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.ella.music.ui.components.decodeCoverSource(context, uri)
                }
                if (bitmap == null) {
                    Toast.makeText(
                        context,
                        R.string.song_more_metadata_cover_crop_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    bitmapToCrop = bitmap
                    showCropSheet = true
                }
            }
        } else {
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

    if (enableCrop) {
        bitmapToCrop?.let { cropBitmap ->
            val cropperState = com.ella.music.ui.components.rememberCoverImageCropperState(
                cropBitmap,
                initialRatio = defaultRatio
            )
            com.ella.music.ui.components.EllaMiuixBottomSheet(
                show = showCropSheet,
                enableNestedScroll = false,
                title = cropTitle,
                endAction = {
                    top.yukonga.miuix.kmp.basic.IconButton(
                        onClick = {
                            val cropped = cropperState.crop()
                            showCropSheet = false
                            scope.launch {
                                val persisted = context.saveCustomBitmapIntoApp(cropped, imageName)
                                if (persisted == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_custom_image_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    context.deletePersistedCustomImage(currentUri)
                                    onImagePersisted(persisted)
                                }
                            }
                        }
                    ) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Regular.Ok,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.common_save),
                            tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
                        )
                    }
                },
                onDismissRequest = { showCropSheet = false },
                onDismissFinished = { bitmapToCrop = null }
            ) {
                com.ella.music.ui.components.CoverImageCropper(
                    state = cropperState,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            }
        }
    }

    return pickerLauncher
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
