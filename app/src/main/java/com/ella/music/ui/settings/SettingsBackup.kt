package com.ella.music.ui.settings

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.buildPlaybackHistoryExportFile
import com.ella.music.data.importPlaybackHistoryFromUri
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.ui.components.EllaMiuixListItem
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "BackupSettings"

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel? = null,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupScope = remember(context, scope) { context.findComponentActivity()?.lifecycleScope ?: scope }
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val playbackStatsStore = remember { PlaybackStatsStore.getInstance(context) }
    val librarySongs by mainViewModel?.songs?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = com.ella.music.ui.components.ellaPageBackground()
    suspend fun writeBackupFile(uri: Uri, file: File) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: error(context.getString(R.string.settings_backup_open_failed))
    }
    var showExportTypeSheet by remember { mutableStateOf(false) }
    var showPlaybackExportFormatDialog by remember { mutableStateOf(false) }
    var showImportTypeSheet by remember { mutableStateOf(false) }
    var showWebDavDefaultTypeSheet by remember { mutableStateOf(false) }
    var pendingExportTypes by remember { mutableStateOf<Set<BackupType>?>(null) }
    var pendingPlaybackExportFormat by remember { mutableStateOf<PlaybackExportFormat?>(null) }
    var pendingImportRoot by remember { mutableStateOf<JSONObject?>(null) }
    var pendingImportSource by remember { mutableStateOf(BackupImportSource.LocalFile) }
    var exportTypeSelection by remember { mutableStateOf(BackupType.entries.toSet()) }
    var importTypeSelection by remember { mutableStateOf(BackupType.entries.toSet()) }
    val settingsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APPLICATION_BACKUP_ZIP_MIME)
    ) { uri ->
        if (uri == null) {
            pendingExportTypes = null
            return@rememberLauncherForActivityResult
        }
        val types = pendingExportTypes ?: BackupType.entries.toSet()
        pendingExportTypes = null
        backupScope.launch {
            var archive: File? = null
            runCatching {
                val createdArchive = buildApplicationBackupZipFile(context, types, librarySongs)
                archive = createdArchive
                writeBackupFile(uri, createdArchive)
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_failed), Toast.LENGTH_SHORT).show()
            }.also {
                withContext(Dispatchers.IO) { archive?.delete() }
            }
        }
    }
    fun exportPlayback(format: PlaybackExportFormat, uri: Uri) {
        backupScope.launch {
            var temporaryFile: File? = null
            try {
                val createdFile = buildPlaybackHistoryExportFile(
                    context = context,
                    format = format.transferFormat(),
                    history = playbackStatsStore.history.value,
                    stats = playbackStatsStore.stats.value,
                    librarySongs = librarySongs
                )
                temporaryFile = createdFile
                writeBackupFile(uri, createdFile)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_backup_export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Throwable) {
                Log.e(TAG, "Playback history export failed", error)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_export_failed) +
                            ": " + transferErrorMessage(error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.IO) { temporaryFile?.delete() }
            }
        }
    }
    val playbackJsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val format = pendingPlaybackExportFormat
        pendingPlaybackExportFormat = null
        if (uri != null && format != null) exportPlayback(format, uri)
    }
    val playbackZipExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APPLICATION_BACKUP_ZIP_MIME)
    ) { uri ->
        val format = pendingPlaybackExportFormat
        pendingPlaybackExportFormat = null
        if (uri != null && format != null) exportPlayback(format, uri)
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            pendingImportRoot?.let { cleanupApplicationBackupAssets(context, it) }
            pendingImportRoot = null
            return@rememberLauncherForActivityResult
        }
        backupScope.launch {
            runCatching {
                val root = withContext(Dispatchers.IO) {
                    val tempFile = runCatching { copyAndValidateApplicationBackupUri(context, uri) }
                        .getOrElse { error(context.getString(R.string.settings_backup_read_failed)) }
                    try {
                        readApplicationBackupFile(context, tempFile)
                    } finally {
                        tempFile.delete()
                    }
                }
                // Prism Music exports are listening-history only; app-data restore expects Halcyon JSON.
                if (root.has("sessions") && !root.has("settings") && !root.has("playlists") && !root.has("playback")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.settings_backup_restore_wrong_type), Toast.LENGTH_LONG).show()
                    }
                    return@runCatching
                }
                pendingImportSource = BackupImportSource.LocalFile
                pendingImportRoot = root
                importTypeSelection = root.availableBackupTypes(root.hasPortableBackupAssets())
                showImportTypeSheet = true
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_restore_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun restoreSelectedTypes(
        root: JSONObject,
        selectedTypes: Set<BackupType>,
        source: BackupImportSource,
        onFinished: () -> Unit = {}
    ) {
        backupScope.launch {
            runCatching {
                restoreApplicationBackup(context, root, selectedTypes)
            }.onSuccess {
                val successMessage = when (source) {
                    BackupImportSource.LocalFile -> R.string.settings_backup_restore_success
                    BackupImportSource.WebDav -> R.string.settings_backup_webdav_download_success
                }
                Toast.makeText(context, context.getString(successMessage), Toast.LENGTH_SHORT).show()
                pendingImportRoot = null
                onFinished()
            }.onFailure {
                val failureMessage = when (source) {
                    BackupImportSource.LocalFile -> R.string.settings_backup_restore_failed
                    BackupImportSource.WebDav -> R.string.settings_backup_webdav_download_failed
                }
                Toast.makeText(context, context.getString(failureMessage), Toast.LENGTH_SHORT).show()
                onFinished()
            }
        }
    }
    val playbackImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            runCatching {
                val result = importPlaybackHistoryFromUri(
                    context = context,
                    uri = uri,
                    librarySongs = librarySongs,
                    store = playbackStatsStore
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_backup_playback_import_success,
                            result.addedCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { error ->
                Log.e(TAG, "Playback history import failed", error)
                backupScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_restore_failed) +
                            ": " + transferErrorMessage(error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // WebDAV backup state
    val savedWebDavUrl by settingsManager.webDavUrl.collectAsState(initial = "")
    val savedWebDavUser by settingsManager.webDavUsername.collectAsState(initial = "")
    val savedWebDavPassword by settingsManager.webDavPassword.collectAsState(initial = "")
    val savedWebDavBackupUrl by settingsManager.webDavBackupUrl.collectAsState(initial = "")
    val savedWebDavBackupPath by settingsManager.webDavBackupPath.collectAsState(initial = "")
    val savedWebDavBackupUser by settingsManager.webDavBackupUsername.collectAsState(initial = "")
    val savedWebDavBackupPassword by settingsManager.webDavBackupPassword.collectAsState(initial = "")
    val webDavAutoBackupEnabled by settingsManager.webDavAutoBackupEnabled.collectAsState(initial = false)
    val webDavAutoBackupInterval by settingsManager.webDavAutoBackupIntervalHours.collectAsState(initial = 24)
    val savedWebDavRestoreDefaultTypes by settingsManager.webDavRestoreDefaultTypes.collectAsState(initial = "")
    var webDavBackupUrl by remember { mutableStateOf(savedWebDavBackupUrl) }
    var webDavBackupPath by remember { mutableStateOf(savedWebDavBackupPath) }
    var webDavBackupUser by remember { mutableStateOf(savedWebDavBackupUser.ifBlank { savedWebDavUser }) }
    var webDavBackupPassword by remember { mutableStateOf(savedWebDavBackupPassword.ifBlank { savedWebDavPassword }) }
    var webDavUploading by remember { mutableStateOf(false) }
    var webDavDownloading by remember { mutableStateOf(false) }
    var webDavBackupFiles by remember { mutableStateOf<List<com.ella.music.data.webdav.WebDavItem>>(emptyList()) }
    var webDavRestoreConfig by remember { mutableStateOf<com.ella.music.data.webdav.WebDavConfig?>(null) }
    LaunchedEffect(savedWebDavBackupUrl) {
        if (savedWebDavBackupUrl.isNotBlank() && webDavBackupUrl.isBlank()) {
            webDavBackupUrl = savedWebDavBackupUrl
        }
    }
    LaunchedEffect(savedWebDavBackupPath) {
        if (savedWebDavBackupPath.isNotBlank() && webDavBackupPath.isBlank()) {
            webDavBackupPath = savedWebDavBackupPath
        }
    }
    LaunchedEffect(savedWebDavUser) {
        if (savedWebDavBackupUser.isBlank() && savedWebDavUser.isNotBlank() && webDavBackupUser.isBlank()) {
            webDavBackupUser = savedWebDavUser
        }
    }
    LaunchedEffect(savedWebDavPassword) {
        if (savedWebDavBackupPassword.isBlank() && savedWebDavPassword.isNotBlank() && webDavBackupPassword.isBlank()) {
            webDavBackupPassword = savedWebDavPassword
        }
    }
    LaunchedEffect(savedWebDavBackupUser) {
        if (savedWebDavBackupUser.isNotBlank()) webDavBackupUser = savedWebDavBackupUser
    }
    LaunchedEffect(savedWebDavBackupPassword) {
        if (savedWebDavBackupPassword.isNotBlank()) webDavBackupPassword = savedWebDavBackupPassword
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_backup),
            color = Color.Transparent,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberSettingsScrollState("settings_backup"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = stringResource(R.string.settings_backup_settings_section))

            SettingsCardGroup(highlight = highlightKey == "backup_settings") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "backup_settings") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_backup_export_settings_title),
                            summary = stringResource(R.string.settings_backup_export_settings_summary),
                            onClick = {
                                showExportTypeSheet = true
                            }
                        )
                    }
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_restore_settings_title),
                        summary = stringResource(R.string.settings_backup_restore_settings_summary),
                        onClick = {
                            pendingImportRoot?.let { cleanupApplicationBackupAssets(context, it) }
                            pendingImportRoot = null
                            settingsImportLauncher.launch(arrayOf(APPLICATION_BACKUP_ZIP_MIME, "application/json", "text/json", "text/*"))
                        }
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_backup_playback_section))

            SettingsCardGroup(highlight = highlightKey == "backup_playback") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "backup_playback") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_backup_export_playback_title),
                            summary = stringResource(R.string.settings_backup_export_playback_summary),
                            onClick = {
                                showPlaybackExportFormatDialog = true
                            }
                        )
                    }
                        ArrowPreference(
                            title = stringResource(R.string.settings_backup_restore_playback_title),
                            summary = stringResource(R.string.settings_backup_restore_playback_summary),
                            onClick = {
                            playbackImportLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/json",
                                    "application/zip",
                                    "application/octet-stream",
                                    "application/x-zip-compressed",
                                    "*/*"
                                )
                            )
                            }
                        )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_backup_webdav_section))

            SettingsCardGroup(highlight = highlightKey == "backup_webdav") {
                Column {
                    SettingsFocusAnchor(active = highlightKey == "backup_webdav") {
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_backup_webdav_url_label),
                            value = webDavBackupUrl,
                            summary = stringResource(R.string.settings_backup_webdav_url_summary),
                            onValueChange = { webDavBackupUrl = it }
                        )
                    }
                    SplitSettingTextField(
                        label = stringResource(R.string.settings_backup_webdav_username_label),
                        value = webDavBackupUser,
                        summary = stringResource(R.string.settings_backup_webdav_username_summary),
                        onValueChange = { webDavBackupUser = it }
                    )
                    SplitSettingTextField(
                        label = stringResource(R.string.settings_backup_webdav_password_label),
                        value = webDavBackupPassword,
                        summary = stringResource(R.string.settings_backup_webdav_password_summary),
                        singleLine = true,
                        isPassword = true,
                        onValueChange = { webDavBackupPassword = it }
                    )
                    SplitSettingTextField(
                        label = stringResource(R.string.settings_backup_webdav_path_label),
                        value = webDavBackupPath,
                        summary = stringResource(R.string.settings_backup_webdav_path_summary),
                        onValueChange = { webDavBackupPath = it }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_backup_webdav_auto_title),
                        summary = stringResource(R.string.settings_backup_webdav_auto_summary),
                        checked = webDavAutoBackupEnabled,
                        onCheckedChange = { enabled ->
                            backupScope.launch {
                                settingsManager.setWebDavBackupUrl(webDavBackupUrl.trim().ifBlank { savedWebDavUrl })
                                settingsManager.setWebDavBackupPath(webDavBackupPath)
                                settingsManager.setWebDavBackupCredentials(webDavBackupUser, webDavBackupPassword)
                                settingsManager.setWebDavAutoBackupEnabled(enabled)
                            }
                        }
                    )
                    SettingsIntSliderPreference(
                        title = stringResource(R.string.settings_backup_webdav_interval_title),
                        summary = stringResource(R.string.settings_backup_webdav_interval_summary),
                        value = webDavAutoBackupInterval,
                        valueRange = 1..168,
                        valueText = stringResource(R.string.settings_backup_webdav_interval_value, webDavAutoBackupInterval),
                        enabled = webDavAutoBackupEnabled,
                        onValueChange = { hours ->
                            backupScope.launch { settingsManager.setWebDavAutoBackupIntervalHours(hours) }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_webdav_restore_defaults_title),
                        summary = stringResource(R.string.settings_backup_webdav_restore_defaults_summary),
                        onClick = { showWebDavDefaultTypeSheet = true }
                    )
                    EllaMiuixListItem(
                        title = stringResource(R.string.settings_backup_webdav_upload),
                        summary = stringResource(R.string.settings_backup_webdav_upload_summary),
                        enabled = !webDavUploading && !webDavDownloading,
                        onClick = {
                            backupScope.launch {
                                val effectiveUrl = webDavBackupUrl.trim().ifBlank { savedWebDavUrl }
                                if (effectiveUrl.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.settings_backup_webdav_not_configured), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val effectiveUser = webDavBackupUser.trim().ifBlank { savedWebDavUser }
                                val effectivePassword = webDavBackupPassword.ifBlank { savedWebDavPassword }
                                // Persist the backup URL and path
                                settingsManager.setWebDavBackupUrl(effectiveUrl)
                                settingsManager.setWebDavBackupPath(webDavBackupPath)
                                settingsManager.setWebDavBackupCredentials(effectiveUser, effectivePassword)
                                webDavUploading = true
                                runCatching {
                                    val config = WebDavConfig(
                                        url = effectiveUrl,
                                        username = effectiveUser,
                                        password = effectivePassword,
                                        customHeaders = settingsManager.webDavCustomHeaders.first()
                                    )
                                    val path = webDavBackupPath.trim().ifBlank { "halcyon_backup" }
                                    val fileName = generateBackupFileName("zip")
                                    val fullUrl = "${effectiveUrl.trimEnd('/')}/$path/$fileName"
                                    val archive = withContext(Dispatchers.IO) {
                                        buildApplicationBackupZipFile(context, librarySongs = librarySongs)
                                    }
                                    withContext(Dispatchers.IO) {
                                        try {
                                            WebDavClient.uploadFileFromFile(fullUrl, config, archive)
                                        } finally {
                                            archive.delete()
                                        }
                                    }
                                }.onSuccess {
                                    Toast.makeText(context, context.getString(R.string.settings_backup_webdav_upload_success), Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, context.getString(R.string.settings_backup_webdav_upload_failed) + ": " + (it.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                                webDavUploading = false
                            }
                        }
                    )
                    EllaMiuixListItem(
                        title = stringResource(R.string.settings_backup_webdav_download),
                        summary = stringResource(R.string.settings_backup_webdav_download_summary),
                        enabled = !webDavUploading && !webDavDownloading,
                        onClick = {
                            backupScope.launch {
                                val effectiveUrl = webDavBackupUrl.trim().ifBlank { savedWebDavUrl }
                                if (effectiveUrl.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.settings_backup_webdav_not_configured), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val effectiveUser = webDavBackupUser.trim().ifBlank { savedWebDavUser }
                                val effectivePassword = webDavBackupPassword.ifBlank { savedWebDavPassword }
                                // Persist the backup URL and path
                                settingsManager.setWebDavBackupUrl(effectiveUrl)
                                settingsManager.setWebDavBackupPath(webDavBackupPath)
                                settingsManager.setWebDavBackupCredentials(effectiveUser, effectivePassword)
                                webDavDownloading = true
                                runCatching {
                                    val config = WebDavConfig(
                                        url = effectiveUrl,
                                        username = effectiveUser,
                                        password = effectivePassword,
                                        customHeaders = settingsManager.webDavCustomHeaders.first()
                                    )
                                    val path = webDavBackupPath.trim().ifBlank { "halcyon_backup" }
                                    val backupDirUrl = "${effectiveUrl.trimEnd('/')}/$path/"
                                    val items = withContext(Dispatchers.IO) {
                                        WebDavClient.list(
                                            config,
                                            backupDirUrl,
                                            forceRefresh = true,
                                            includeNonAudioFiles = true
                                        )
                                    }
                                    val backupFiles = items
                                        .filterNot { it.isDirectory }
                                        .filter {
                                            (it.name.endsWith(".zip", ignoreCase = true) ||
                                                it.name.endsWith(".json", ignoreCase = true)) &&
                                                it.name.isHalcyonBackupFileName()
                                        }
                                        .sortedByDescending { it.name }
                                    if (backupFiles.isEmpty()) {
                                        throw IllegalStateException(context.getString(R.string.settings_backup_webdav_file_not_found))
                                    }
                                    webDavRestoreConfig = config
                                    webDavBackupFiles = backupFiles
                                }.onFailure {
                                    webDavDownloading = false
                                    Toast.makeText(context, context.getString(R.string.settings_backup_webdav_download_failed) + ": " + (it.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    if (webDavBackupFiles.isNotEmpty()) {
        WebDavBackupPickerDialog(
            backupFiles = webDavBackupFiles,
            onDismissRequest = {
                webDavBackupFiles = emptyList()
                webDavRestoreConfig = null
                webDavDownloading = false
            },
            onFileSelected = { selectedFile ->
                val config = webDavRestoreConfig
                webDavBackupFiles = emptyList()
                webDavRestoreConfig = null
                if (config != null) {
                    backupScope.launch {
                        runCatching {
                            val tempFile = withContext(Dispatchers.IO) {
                                val suffix = if (selectedFile.name.endsWith(".zip", ignoreCase = true)) ".zip" else ".json"
                                val tmp = File(
                                    context.cacheDir,
                                    "webdav_restore_${System.currentTimeMillis()}_${System.nanoTime()}$suffix"
                                )
                                WebDavClient.downloadToFile(selectedFile.url, config, tmp)
                                tmp
                            }
                            val root = withContext(Dispatchers.IO) {
                                try {
                                    validateApplicationBackupFileBudgets(tempFile)
                                    readApplicationBackupFile(context, tempFile)
                                } finally {
                                    tempFile.delete()
                                }
                            }
                            val availableTypes = root.availableBackupTypes(root.hasPortableBackupAssets())
                            val defaults = savedWebDavRestoreDefaultTypes
                                .toWebDavRestoreTypes()
                                .intersect(availableTypes)
                            if (defaults.isNotEmpty()) {
                                restoreSelectedTypes(root, defaults, BackupImportSource.WebDav) {
                                    webDavDownloading = false
                                }
                            } else {
                                pendingImportSource = BackupImportSource.WebDav
                                pendingImportRoot = root
                                importTypeSelection = availableTypes
                                showImportTypeSheet = true
                            }
                        }.onSuccess {
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.settings_backup_webdav_download_failed) + ": " + (it.message ?: ""), Toast.LENGTH_LONG).show()
                            webDavDownloading = false
                        }
                    }
                }
            }
        )
    }

    if (showPlaybackExportFormatDialog) {
        BackupFormatDialog(
            onDismissRequest = { showPlaybackExportFormatDialog = false },
            onFormatSelected = { format ->
                showPlaybackExportFormatDialog = false
                pendingPlaybackExportFormat = format
                val fileName = format.suggestedFileName()
                if (format.transferFormat().extension == "zip") {
                    playbackZipExportLauncher.launch(fileName)
                } else {
                    playbackJsonExportLauncher.launch(fileName)
                }
            }
        )
    }

    BackupTypeSelectionSheet(
        show = showExportTypeSheet,
        title = stringResource(R.string.settings_backup_export_type_title),
        confirmText = stringResource(R.string.common_export),
        onDismiss = { showExportTypeSheet = false },
        initialSelected = exportTypeSelection,
        onConfirm = { selectedTypes ->
            exportTypeSelection = selectedTypes
            pendingExportTypes = selectedTypes
            settingsExportLauncher.launch(generateBackupFileName("zip"))
        }
    )

    BackupTypeSelectionSheet(
        show = showImportTypeSheet,
        title = stringResource(R.string.settings_backup_import_type_title),
        confirmText = stringResource(R.string.common_import),
        onDismiss = {
            showImportTypeSheet = false
            pendingImportRoot?.let { cleanupApplicationBackupAssets(context, it) }
            pendingImportRoot = null
            if (pendingImportSource == BackupImportSource.WebDav) {
                webDavDownloading = false
            }
            pendingImportSource = BackupImportSource.LocalFile
        },
        initialSelected = importTypeSelection,
        availableTypes = pendingImportRoot?.let { root ->
            root.availableBackupTypes(root.hasPortableBackupAssets())
        }.orEmpty(),
        onConfirm = { selectedTypes ->
            importTypeSelection = selectedTypes
            val root = pendingImportRoot
            val source = pendingImportSource
            pendingImportRoot = null
            pendingImportSource = BackupImportSource.LocalFile
            if (root != null) {
                restoreSelectedTypes(root, selectedTypes, source) {
                    if (source == BackupImportSource.WebDav) {
                        webDavDownloading = false
                    }
                }
            }
        }
    )

    BackupTypeSelectionSheet(
        show = showWebDavDefaultTypeSheet,
        title = stringResource(R.string.settings_backup_webdav_restore_defaults_title),
        confirmText = stringResource(R.string.common_save),
        onDismiss = { showWebDavDefaultTypeSheet = false },
        initialSelected = savedWebDavRestoreDefaultTypes.toWebDavRestoreTypes()
            .ifEmpty { BackupType.entries.toSet() },
        onConfirm = { selectedTypes ->
            backupScope.launch {
                settingsManager.setWebDavRestoreDefaultTypes(selectedTypes.toWebDavRestoreSetting())
            }
        }
    )
}

private fun transferErrorMessage(error: Throwable): String {
    val messages = buildList {
        var current: Throwable? = error
        while (current != null && size < 3) {
            current.message
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            current = current.cause
        }
    }.distinct()
    return messages.joinToString("; ")
        .ifBlank { error::class.java.simpleName }
}

private enum class BackupImportSource {
    LocalFile,
    WebDav
}
