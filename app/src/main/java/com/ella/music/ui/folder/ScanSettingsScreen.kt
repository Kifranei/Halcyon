package com.ella.music.ui.folder

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ScanRefreshIconButton
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun ScanSettingsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanIncludeFolders by mainViewModel.settingsManager.scanIncludeFolders.collectAsState(initial = "")
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val useAndroidMediaLibrary by mainViewModel.settingsManager.useAndroidMediaLibrary.collectAsState(initial = true)
    val fullTagSearchEnabled by mainViewModel.settingsManager.fullTagSearchEnabled.collectAsState(initial = true)
    val savedFolders = remember(scanIncludeFolders) { scanIncludeFolders.toFolderSettingList() }
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val blockedFolderKeys = remember(blockedFolders) {
        blockedFolders.map { it.normalizeFolderPath().lowercase(Locale.ROOT) }.toSet()
    }
    val showManualScanHint = remember(context) {
        {
            Toast.makeText(context, R.string.folder_scan_manual_needed, Toast.LENGTH_SHORT).show()
        }
    }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var pendingRemoveScanFolder by remember { mutableStateOf<String?>(null) }
    var pendingRemoveUsbUri by remember { mutableStateOf<String?>(null) }

    val usbFolderUrisRaw by mainViewModel.settingsManager.usbFolderUris.collectAsState(initial = "")
    val usbFolderUris = remember(usbFolderUrisRaw) {
        usbFolderUrisRaw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, readWrite)
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(uri, readOnly)
            }
            val folderPath = uri.toPrimaryStoragePath()
            if (folderPath == null) {
                // Non-primary storage (e.g. USB drive) -- store the SAF URI instead
                scope.launch {
                    mainViewModel.settingsManager.setUseAndroidMediaLibrary(false)
                    mainViewModel.settingsManager.addUsbFolderUri(uri.toString())
                }
                Toast.makeText(context, R.string.folder_usb_added, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    mainViewModel.settingsManager.setUseAndroidMediaLibrary(false)
                    mainViewModel.settingsManager.setScanIncludeFolders(
                        (savedFolders + folderPath).distinct().joinToString("；")
                    )
                }
                showManualScanHint()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.folder_scan_settings),
            color = ellaPageBackground(),
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            titleStartPadding = if (showBackButton) 64.dp else 20.dp,
            actions = {
                ScanRefreshIconButton(
                    enabled = !isScanning,
                    onScan = { mainViewModel.scanMusic() },
                    onDeepRescan = { mainViewModel.fullRescanMusic() },
                    contentDescription = stringResource(R.string.folder_full_scan)
                )
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.folder_add_custom_directory),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (isScanning) {
                item { ScanStatusCard(scanProgress = scanProgress) }
            }

            item {
                MediaSourceModeCard(
                    useAndroidMediaLibrary = useAndroidMediaLibrary,
                    fullTagSearchEnabled = fullTagSearchEnabled,
                    customFolderCount = savedFolders.size,
                    highlight = highlightKey == "scan_media_source",
                    onUseAndroidMediaLibraryChange = { enabled ->
                        scope.launch {
                            mainViewModel.settingsManager.setUseAndroidMediaLibrary(enabled)
                            mainViewModel.scanMusic()
                        }
                    },
                    onFullTagSearchEnabledChange = { enabled ->
                        scope.launch {
                            mainViewModel.settingsManager.setFullTagSearchEnabled(enabled)
                            if (!enabled) {
                                mainViewModel.settingsManager.setUseAndroidMediaLibrary(true)
                                mainViewModel.clearLibrarySnapshotCache()
                            }
                            mainViewModel.scanMusic()
                        }
                    }
                )
            }

            item {
                SavedScanFoldersCard(
                    folders = savedFolders,
                    hiddenFolders = blockedFolderKeys,
                    highlight = highlightKey == "scan_folders",
                    onVisibilityChange = { folderPath, visible ->
                        scope.launch {
                            val normalizedPath = folderPath.normalizeFolderPath()
                            val nextBlockedFolders = if (visible) {
                                blockedFolders.filterNot {
                                    it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                                }
                            } else {
                                (blockedFolders + normalizedPath).distinctBy {
                                    it.normalizeFolderPath().lowercase(Locale.ROOT)
                                }
                            }
                            mainViewModel.settingsManager.setScanExcludeFolders(nextBlockedFolders.joinToString("；"))
                        }
                        showManualScanHint()
                    },
                    onRemove = { folderPath ->
                        pendingRemoveScanFolder = folderPath
                    },
                    scanEnabled = !isScanning,
                    onScan = { mainViewModel.scanMusic() },
                    onDeepRescan = { mainViewModel.fullRescanMusic() }
                )
            }

            item {
                BlockedFoldersEntryCard(
                    count = blockedFolders.size,
                    highlight = highlightKey == "scan_blocked_folders",
                    onClick = { showBlockedDialog = true }
                )
            }

            if (usbFolderUris.isNotEmpty()) {
                item {
                    UsbFoldersCard(
                        usbFolderUris = usbFolderUris,
                        highlight = highlightKey == "scan_usb_folders",
                        onRemove = { uri -> pendingRemoveUsbUri = uri },
                        scanEnabled = !isScanning,
                        onScan = { mainViewModel.scanMusic() },
                        onDeepRescan = { mainViewModel.fullRescanMusic() }
                    )
                }
            }
        }

        if (showBlockedDialog) {
            BlockedFoldersDialog(
                folders = blockedFolders,
                onDismiss = { showBlockedDialog = false },
                onRemove = { folderPath ->
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            blockedFolders.filterNot { it == folderPath }.joinToString("；")
                        )
                    }
                    showManualScanHint()
                },
                onClear = {
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders("")
                    }
                    showManualScanHint()
                    showBlockedDialog = false
                }
            )
        }

        pendingRemoveScanFolder?.let { folderPath ->
            ConfirmDangerDialog(
                show = true,
                title = stringResource(R.string.folder_remove_scan_folder_title),
                message = stringResource(R.string.folder_remove_scan_folder_message, folderPath),
                confirmText = stringResource(R.string.common_remove),
                onDismiss = { pendingRemoveScanFolder = null },
                onConfirm = {
                    scope.launch {
                        val normalizedPath = folderPath.normalizeFolderPath()
                        mainViewModel.settingsManager.setScanIncludeFolders(
                            savedFolders.filterNot {
                                it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                            }.joinToString("；")
                        )
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            blockedFolders.filterNot {
                                it.normalizeFolderPath().equals(normalizedPath, ignoreCase = true)
                            }.joinToString("；")
                        )
                    }
                    showManualScanHint()
                    pendingRemoveScanFolder = null
                }
            )
        }

        pendingRemoveUsbUri?.let { uri ->
            ConfirmDangerDialog(
                show = true,
                title = stringResource(R.string.folder_remove_usb_folder_title),
                message = stringResource(R.string.folder_remove_usb_folder_message),
                confirmText = stringResource(R.string.common_remove),
                onDismiss = { pendingRemoveUsbUri = null },
                onConfirm = {
                    scope.launch {
                        mainViewModel.settingsManager.removeUsbFolderUri(uri)
                    }
                    Toast.makeText(context, R.string.folder_usb_removed, Toast.LENGTH_SHORT).show()
                    pendingRemoveUsbUri = null
                }
            )
        }
    }
}

internal fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
