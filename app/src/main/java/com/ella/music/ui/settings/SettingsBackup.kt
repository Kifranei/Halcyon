package com.ella.music.ui.settings

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
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
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.ui.components.EllaMiuixListItem
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val playlistStore = remember { PlaylistStore.getInstance(context) }
    val librarySongs by mainViewModel?.songs?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    suspend fun writeBackupText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }
            output.flush()
        } ?: error(context.getString(R.string.settings_backup_open_failed))
    }
    suspend fun buildApplicationBackupJson(selectedTypes: Set<BackupType> = BackupType.entries.toSet()): JSONObject = withContext(Dispatchers.IO) {
        val filteredSettings = settingsManager.exportSettingsJson().filterBackupSettings(selectedTypes)
        JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .apply {
                if (filteredSettings.length() > 0) put("settings", filteredSettings)
                if (BackupType.Playlists in selectedTypes) put("playlists", playlistStore.exportJson())
                if (BackupType.PlaybackStats in selectedTypes) put("playback", playbackStatsStore.exportJson(librarySongs))
                if (BackupType.AiConfigAndChat in selectedTypes) put("aiChat", exportAiChatBackupJson(context))
            }
    }
    suspend fun buildBackupJson(selectedTypes: Set<BackupType> = BackupType.entries.toSet()): String {
        return buildApplicationBackupJson(selectedTypes).toString(2)
    }
    var showExportTypeSheet by remember { mutableStateOf(false) }
    var showImportTypeSheet by remember { mutableStateOf(false) }
    var pendingExportTypes by remember { mutableStateOf<Set<BackupType>?>(null) }
    var pendingImportRoot by remember { mutableStateOf<JSONObject?>(null) }
    var pendingImportSource by remember { mutableStateOf(BackupImportSource.LocalFile) }
    var exportTypeSelection by remember { mutableStateOf(BackupType.entries.toSet()) }
    var importTypeSelection by remember { mutableStateOf(BackupType.entries.toSet()) }
    val settingsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            pendingExportTypes = null
            return@rememberLauncherForActivityResult
        }
        val types = pendingExportTypes ?: BackupType.entries.toSet()
        pendingExportTypes = null
        backupScope.launch {
            runCatching {
                val backup = buildApplicationBackupJson(types)
                writeBackupText(uri, backup.toString(2))
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val playbackExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            runCatching {
                val exported = playbackStatsStore.exportJson(librarySongs)
                val backup = JSONObject()
                    .put("version", 1)
                    .put("exportedAtMs", System.currentTimeMillis())
                    .put("sessions", exported.optJSONArray("sessions") ?: org.json.JSONArray())
                writeBackupText(uri, backup.toString(2))
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            pendingImportRoot = null
            return@rememberLauncherForActivityResult
        }
        backupScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error(context.getString(R.string.settings_backup_read_failed))
                }
                val root = JSONObject(text)
                // Prism Music exports are listening-history only; app-data restore expects Halcyon JSON.
                if (root.has("sessions") && !root.has("settings") && !root.has("playlists") && !root.has("playback")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.settings_backup_restore_wrong_type), Toast.LENGTH_LONG).show()
                    }
                    return@runCatching
                }
                pendingImportSource = BackupImportSource.LocalFile
                pendingImportRoot = root
                importTypeSelection = root.availableBackupTypes()
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
                val filteredSettings = (root.optJSONObject("settings") ?: root).filterBackupSettings(selectedTypes)
                if (filteredSettings.length() > 0) {
                    settingsManager.restoreSettingsJson(filteredSettings)
                }
                if (BackupType.Playlists in selectedTypes) {
                    val playlistPayload = root.optJSONObject("playlists")
                        ?: root.takeIf { it.has("playlists") }
                    if (playlistPayload != null) {
                        playlistStore.restoreJson(playlistPayload)
                    }
                }
                if (BackupType.PlaybackStats in selectedTypes) {
                    root.optJSONObject("playback")?.let { playbackStatsStore.restoreJson(it) }
                }
                if (BackupType.AiConfigAndChat in selectedTypes) {
                    root.optJSONObject("aiChat")?.let { restoreAiChatBackupJson(context, it) }
                }
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
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error(context.getString(R.string.settings_backup_read_failed))
                }
                val root = JSONObject(text)
                // Detect if this is a settings backup
                if (root.has("settings")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.settings_backup_restore_wrong_type_settings), Toast.LENGTH_LONG).show()
                    }
                    return@runCatching
                }
                playbackStatsStore.restoreJson(root.optJSONObject("playback") ?: root)
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_restore_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_restore_failed), Toast.LENGTH_SHORT).show()
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
            color = pageBackground,
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
                .verticalScroll(rememberScrollState())
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
                            pendingImportRoot = null
                            settingsImportLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
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
                                playbackExportLauncher.launch("prism-listening-sessions_${System.currentTimeMillis()}.json")
                            }
                        )
                    }
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_restore_playback_title),
                        summary = stringResource(R.string.settings_backup_restore_playback_summary),
                        onClick = {
                            playbackImportLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
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
                                        password = effectivePassword
                                    )
                                    val path = webDavBackupPath.trim().ifBlank { "halcyon_backup" }
                                    val fileName = "halcyon_backup_${System.currentTimeMillis()}.json"
                                    val fullUrl = "${effectiveUrl.trimEnd('/')}/$path/$fileName"
                                    val backupContent = withContext(Dispatchers.IO) { buildBackupJson() }
                                    withContext(Dispatchers.IO) {
                                        WebDavClient.uploadFileFromString(fullUrl, config, backupContent)
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
                                        password = effectivePassword
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
                                        .filter { it.name.endsWith(".json") && it.name.isHalcyonBackupFileName() }
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
                                val tmp = java.io.File(context.cacheDir, "webdav_restore.json")
                                WebDavClient.downloadToFile(selectedFile.url, config, tmp)
                                tmp
                            }
                            val text = withContext(Dispatchers.IO) {
                                tempFile.readText(Charsets.UTF_8)
                            }
                            val root = JSONObject(text)
                            withContext(Dispatchers.IO) { tempFile.delete() }
                            pendingImportSource = BackupImportSource.WebDav
                            pendingImportRoot = root
                            importTypeSelection = root.availableBackupTypes()
                            showImportTypeSheet = true
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

    BackupTypeSelectionSheet(
        show = showExportTypeSheet,
        title = stringResource(R.string.settings_backup_export_type_title),
        confirmText = stringResource(R.string.common_export),
        onDismiss = { showExportTypeSheet = false },
        initialSelected = exportTypeSelection,
        onConfirm = { selectedTypes ->
            exportTypeSelection = selectedTypes
            pendingExportTypes = selectedTypes
            settingsExportLauncher.launch("halcyon_settings_${System.currentTimeMillis()}.json")
        }
    )

    BackupTypeSelectionSheet(
        show = showImportTypeSheet,
        title = stringResource(R.string.settings_backup_import_type_title),
        confirmText = stringResource(R.string.common_import),
        onDismiss = {
            showImportTypeSheet = false
            pendingImportRoot = null
            if (pendingImportSource == BackupImportSource.WebDav) {
                webDavDownloading = false
            }
            pendingImportSource = BackupImportSource.LocalFile
        },
        initialSelected = importTypeSelection,
        availableTypes = pendingImportRoot?.availableBackupTypes().orEmpty(),
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
}

private enum class BackupImportSource {
    LocalFile,
    WebDav
}
