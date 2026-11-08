package com.ella.music.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.sourceplugin.SourcePluginConfig
import com.ella.music.data.sourceplugin.SourcePluginRegistry
import com.ella.music.ui.components.*
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.ui.online.SourcePluginAccountSheet
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.RemoveContact

/**
 * Management page for account-source plugins: import scripts (file / URL / plugin-hub JSON),
 * enable or delete them, and open the per-plugin account sheet. Scripts are user-supplied; the
 * app ships none.
 */
@Composable
internal fun SourcePluginSettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SourcePluginRegistry.store(context) }
    val plugins by store.plugins.collectAsState()
    var accountTarget by remember { mutableStateOf<SourcePluginConfig?>(null) }
    var importUrl by rememberSaveable { mutableStateOf("") }
    var showUrlImport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = try {
                val result = store.importFromFile(uri)
                context.getString(R.string.source_plugin_imported, result.plugins.size)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { error.localizedMessage.orEmpty() }
            finally { busy = false }
        }
    }

    fun import(url: String) {
        if (busy || url.isBlank()) return
        scope.launch {
            busy = true
            message = try {
                val result = store.importFromUrl(url)
                context.getString(R.string.source_plugin_imported, result.plugins.size)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { error.localizedMessage.orEmpty() }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(ellaPageBackground()).windowInsetsPadding(WindowInsets.statusBars)) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.source_plugin_settings_title), color = Color.Transparent,
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back)) }
            }
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.source_plugin_settings_summary))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !busy, onClick = { filePicker.launch("*/*") }) {
                        Text(stringResource(R.string.source_plugin_import_file))
                    }
                    Button(enabled = !busy, onClick = { showUrlImport = !showUrlImport }) {
                        Text(stringResource(R.string.source_plugin_import_url))
                    }
                }
                if (showUrlImport) {
                    WebDavTextField(stringResource(R.string.source_plugin_url_hint), importUrl, onValueChange = { importUrl = it })
                    Button(enabled = !busy && importUrl.isNotBlank(), onClick = { import(importUrl) }) {
                        Text(stringResource(R.string.source_plugin_connect))
                    }
                }
                if (busy || message.isNotBlank()) {
                    Text(
                        if (busy) stringResource(R.string.lx_online_processing) else message,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            items(plugins, key = { it.id }) { plugin ->
                EllaSheetCardGroup {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${plugin.name} · ${plugin.version}")
                            if (plugin.author.isNotBlank()) Text(plugin.author)
                        }
                        if (plugin.methods.any { it != "none" }) {
                            IconButton(onClick = { accountTarget = plugin }) {
                                Icon(
                                    imageVector = MiuixIcons.RemoveContact,
                                    contentDescription = stringResource(R.string.source_plugin_account)
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                store.deletePlugin(plugin.id)
                                SourcePluginRegistry.evict(plugin.id)
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.source_plugin_delete)
                            )
                        }
                        Switch(
                            checked = plugin.enabled,
                            onCheckedChange = { scope.launch { store.setEnabled(plugin.id, it) } }
                        )
                    }
                }
            }
            if (plugins.isEmpty()) {
                item { Text(stringResource(R.string.source_plugin_empty)) }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
    accountTarget?.let { target ->
        SourcePluginAccountSheet(target, onDismiss = { accountTarget = null }, mainViewModel = mainViewModel)
    }
}
