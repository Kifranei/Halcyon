package com.ella.music.ui.online

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.SavedRemoteServer
import com.ella.music.data.remote.isSubsonicLike
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.ui.settings.SettingsCardGroup
import com.ella.music.ui.settings.rememberSettingsLazyListState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Manage multiple saved servers for a remote music [provider] (Navidrome / OpenSubsonic / Emby): add, edit,
 * delete, and pick the active one. The active server backs [SettingsManager.navidromeConfig] /
 * [SettingsManager.embyConfig] used by the rest of the app.
 */
@Composable
fun RemoteServerSettingsScreen(
    provider: RemoteMusicProvider,
    onBack: () -> Unit,
    onNavigateToEditor: (serverId: String?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val providerName = when (provider) {
        RemoteMusicProvider.Navidrome -> stringResource(R.string.remote_source_navidrome)
        RemoteMusicProvider.OpenSubsonic -> stringResource(R.string.remote_source_opensubsonic)
        RemoteMusicProvider.Emby -> stringResource(R.string.remote_source_emby)
        RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
    }
    val servers by when (provider) {
        RemoteMusicProvider.Navidrome -> settingsManager.navidromeServers
        RemoteMusicProvider.OpenSubsonic -> settingsManager.openSubsonicServers
        RemoteMusicProvider.Emby -> settingsManager.embyServers
        RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
    }.collectAsState(initial = emptyList())
    val activeId by when (provider) {
        RemoteMusicProvider.Navidrome -> settingsManager.navidromeActiveServerId
        RemoteMusicProvider.OpenSubsonic -> settingsManager.openSubsonicActiveServerId
        RemoteMusicProvider.Emby -> settingsManager.embyActiveServerId
        RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
    }.collectAsState(initial = "")

    var pendingDelete by remember { mutableStateOf<SavedRemoteServer?>(null) }
    val pageBackground = ellaPageBackground()
    val listState = rememberSettingsLazyListState("settings_remote_${provider.name}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.remote_server_manage_title, providerName),
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (servers.isEmpty()) {
                item {
                    SettingsCardGroup {
                        BasicComponent(
                            title = stringResource(R.string.remote_server_empty),
                            summary = stringResource(R.string.remote_server_manage_summary)
                        )
                    }
                }
            }
            items(servers, key = { it.id }) { server ->
                RemoteServerRow(
                    server = server,
                    isActive = server.id == activeId,
                    onSetActive = {
                        scope.launch {
                            when (provider) {
                                RemoteMusicProvider.Navidrome -> settingsManager.setActiveNavidromeServer(server.id)
                                RemoteMusicProvider.OpenSubsonic -> settingsManager.setActiveOpenSubsonicServer(server.id)
                                RemoteMusicProvider.Emby -> settingsManager.setActiveEmbyServer(server.id)
                                RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
                            }
                        }
                    },
                    onEdit = { onNavigateToEditor(server.id) },
                    onDelete = { pendingDelete = server }
                )
            }
            item {
                SettingsCardGroup {
                    ArrowPreference(
                        title = stringResource(R.string.remote_server_add),
                        summary = providerName,
                        onClick = { onNavigateToEditor(null) }
                    )
                }
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }

    pendingDelete?.let { server ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.remote_server_edit),
            message = stringResource(R.string.remote_server_delete_message, server.name.ifBlank { server.config.baseUrl }),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch {
                    when (provider) {
                        RemoteMusicProvider.Navidrome -> settingsManager.deleteNavidromeServer(server.id)
                        RemoteMusicProvider.OpenSubsonic -> settingsManager.deleteOpenSubsonicServer(server.id)
                        RemoteMusicProvider.Emby -> settingsManager.deleteEmbyServer(server.id)
                        RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
                    }
                }
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun RemoteServerRow(
    server: SavedRemoteServer,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsCardGroup {
        Column {
            BasicComponent(
                title = server.name.ifBlank { server.config.baseUrl },
                summary = buildString {
                    append(server.config.baseUrl)
                    if (isActive) {
                        append('\n')
                        append(stringResource(R.string.remote_server_active))
                    }
                }
            )
            if (!isActive) {
                ArrowPreference(
                    title = stringResource(R.string.remote_server_set_active),
                    summary = server.name.ifBlank { server.config.baseUrl },
                    onClick = onSetActive
                )
            }
            ArrowPreference(
                title = stringResource(R.string.remote_server_edit),
                summary = server.name.ifBlank { server.config.baseUrl },
                onClick = onEdit
            )
            ArrowPreference(
                title = stringResource(R.string.common_delete),
                summary = server.name.ifBlank { server.config.baseUrl },
                onClick = onDelete
            )
        }
    }
}

@Composable
fun RemoteServerEditorScreen(
    provider: RemoteMusicProvider,
    serverId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    val isSubsonicLike = provider.isSubsonicLike
    val providerName = stringResource(
        when (provider) {
            RemoteMusicProvider.Navidrome -> R.string.remote_source_navidrome
            RemoteMusicProvider.OpenSubsonic -> R.string.remote_source_opensubsonic
            RemoteMusicProvider.Emby -> R.string.remote_source_emby
            RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
        }
    )

    val servers by when (provider) {
        RemoteMusicProvider.Navidrome -> settingsManager.navidromeServers
        RemoteMusicProvider.OpenSubsonic -> settingsManager.openSubsonicServers
        RemoteMusicProvider.Emby -> settingsManager.embyServers
        RemoteMusicProvider.Lx -> error("Unsupported remote server provider: ${provider.id}")
    }.collectAsState(initial = emptyList())

    val existing = remember(servers, serverId) {
        if (serverId != null) servers.firstOrNull { it.id == serverId } else null
    }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var secondaryUrl by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remoteWriteEnabled by remember { mutableStateOf(false) }
    var streamMaxBitRate by remember { mutableStateOf("") }
    var downloadMaxBitRate by remember { mutableStateOf("") }
    var coverArtSize by remember { mutableStateOf("512") }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(existing, serverId) {
        if (!isInitialized) {
            if (serverId == null) {
                isInitialized = true
            } else if (existing != null) {
                name = existing.name
                url = existing.config.baseUrl
                secondaryUrl = existing.config.secondaryBaseUrl
                user = existing.config.username
                password = existing.config.password
                remoteWriteEnabled = existing.config.remoteWriteEnabled
                streamMaxBitRate = existing.config.streamMaxBitRate.takeIf { it > 0 }?.toString().orEmpty()
                downloadMaxBitRate = existing.config.downloadMaxBitRate.takeIf { it > 0 }?.toString().orEmpty()
                coverArtSize = (existing.config.coverArtSize.takeIf { it > 0 } ?: 512).toString()
                isInitialized = true
            }
        }
    }

    val pageBackground = ellaPageBackground()

    val doSave = {
        if (!saving && url.isNotBlank()) {
            saving = true
            status = null
            scope.launch {
                val id = existing?.id ?: serverId ?: settingsManager.newRemoteServerId()
                val trimmedUrl = url.trim().trimEnd('/')
                val displayName = name.trim().ifBlank {
                    trimmedUrl.substringAfter("://").substringBefore('/').ifBlank { trimmedUrl }
                }
                runCatching {
                    if (isSubsonicLike) {
                        val config = RemoteMusicSourceConfig(
                            provider = provider,
                            baseUrl = trimmedUrl,
                            username = user.trim(),
                            password = password,
                            secondaryBaseUrl = secondaryUrl.trim().trimEnd('/'),
                            remoteWriteEnabled = remoteWriteEnabled,
                            streamMaxBitRate = streamMaxBitRate.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            downloadMaxBitRate = downloadMaxBitRate.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            coverArtSize = coverArtSize.toIntOrNull()?.coerceIn(64, 2048) ?: 512
                        )
                        navidromeService.test(config)
                        val server = SavedRemoteServer(id = id, name = displayName, config = config)
                        when (provider) {
                            RemoteMusicProvider.Navidrome -> settingsManager.upsertNavidromeServer(server)
                            RemoteMusicProvider.OpenSubsonic -> settingsManager.upsertOpenSubsonicServer(server)
                            RemoteMusicProvider.Emby,
                            RemoteMusicProvider.Lx -> error("Unsupported Subsonic-like provider: ${provider.id}")
                        }
                    } else {
                        val login = embyService.login(trimmedUrl, user.trim(), password)
                        val config = RemoteMusicSourceConfig(
                            provider = provider,
                            baseUrl = trimmedUrl,
                            username = user.trim(),
                            token = login.token,
                            userId = login.userId,
                            serverName = login.serverName
                        )
                        when (provider) {
                            RemoteMusicProvider.Emby -> settingsManager.upsertEmbyServer(
                                SavedRemoteServer(id = id, name = displayName, config = config)
                            )
                            RemoteMusicProvider.Lx,
                            RemoteMusicProvider.Navidrome,
                            RemoteMusicProvider.OpenSubsonic -> error("Unsupported provider for Emby login path: ${provider.id}")
                        }
                    }
                }.onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.remote_source_saved_named, providerName),
                        Toast.LENGTH_SHORT
                    ).show()
                    saving = false
                    onSaved()
                }.onFailure { error ->
                    saving = false
                    status = error.localizedMessage ?: context.getString(R.string.remote_source_request_failed)
                }
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(if (serverId == null) R.string.remote_server_add else R.string.remote_server_edit),
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
            },
            actions = {
                IconButton(
                    onClick = doSave,
                    enabled = !saving && url.isNotBlank()
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        tint = if (!saving && url.isNotBlank()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WebDavTextField(stringResource(R.string.remote_server_name_label), name, onValueChange = { name = it })
                    WebDavTextField(stringResource(R.string.webdav_url), url, onValueChange = { url = it })
                    if (isSubsonicLike) {
                        WebDavTextField(
                            stringResource(R.string.remote_server_secondary_url_label),
                            secondaryUrl,
                            onValueChange = { secondaryUrl = it }
                        )
                    }
                    WebDavTextField(stringResource(R.string.webdav_username), user, onValueChange = { user = it })
                    WebDavTextField(
                        label = stringResource(R.string.webdav_password),
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (isSubsonicLike) {
                        WebDavTextField(
                            stringResource(R.string.remote_server_stream_bitrate_label),
                            streamMaxBitRate,
                            onValueChange = { streamMaxBitRate = it.filter(Char::isDigit).take(4) }
                        )
                        WebDavTextField(
                            stringResource(R.string.remote_server_download_bitrate_label),
                            downloadMaxBitRate,
                            onValueChange = { downloadMaxBitRate = it.filter(Char::isDigit).take(4) }
                        )
                        WebDavTextField(
                            stringResource(R.string.remote_server_cover_size_label),
                            coverArtSize,
                            onValueChange = { coverArtSize = it.filter(Char::isDigit).take(4) }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.remote_playlist_write_title),
                            summary = stringResource(R.string.remote_playlist_write_summary),
                            checked = remoteWriteEnabled,
                            onCheckedChange = { remoteWriteEnabled = it }
                        )
                    }
                }
            }

            status?.let {
                Text(
                    text = it,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = doSave,
                enabled = !saving && url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
