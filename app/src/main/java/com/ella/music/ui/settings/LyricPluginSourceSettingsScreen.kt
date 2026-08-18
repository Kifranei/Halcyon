package com.ella.music.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.plugin.model.PluginConfigField
import com.ella.music.plugin.model.PluginConfigFieldType
import com.ella.music.plugin.model.defaultValueString
import com.ella.music.plugin.source.LyricoPluginManager
import com.ella.music.plugin.source.LyricoPluginSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricPluginSourceSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val pluginManager = remember(context) { LyricoPluginManager(context, settingsManager) }
    val enabledIds by settingsManager.lyricoPluginEnabledIds.collectAsState(initial = emptySet())
    var reloadToken by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<LyricoPluginSource?>(null) }
    var configTarget by remember { mutableStateOf<LyricoPluginSource?>(null) }
    val sources by produceState(initialValue = emptyList<LyricoPluginSource>(), context, reloadToken) {
        value = pluginManager.availableSources()
    }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val contentScrollState = rememberSettingsScrollState("settings_lyric_plugin_sources")
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { pluginManager.importPluginZip(uri) }
                .onSuccess { manifests ->
                    manifests.forEach { manifest ->
                        settingsManager.setLyricoPluginEnabled(manifest.id, true)
                    }
                    reloadToken++
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_lyric_plugin_import_success,
                            manifests.joinToString("、") { it.name }
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(context, R.string.settings_lyric_plugin_import_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_lyric_plugin_sources),
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
                IconButton(onClick = { importLauncher.launch(PLUGIN_ZIP_MIME_TYPES) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.settings_lyric_plugin_import),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(contentScrollState)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCardGroup {
                Column {
                    if (sources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_lyric_plugin_sources_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(18.dp)
                        )
                    } else {
                        sources.forEach { source ->
                            val manifest = source.manifest
                            val summary = buildString {
                                append(context.getString(R.string.settings_lyric_plugin_source_imported))
                                append(" · ")
                                append(manifest.description.ifBlank { manifest.id })
                                if (manifest.versionName.isNotBlank()) append(" · v${manifest.versionName}")
                            }
                            val checked = manifest.id in enabledIds
                            val onToggle: (Boolean) -> Unit = { enabled ->
                                scope.launch { settingsManager.setLyricoPluginEnabled(manifest.id, enabled) }
                            }
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = manifest.name,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = summary,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    if (manifest.configFields.isNotEmpty()) {
                                        IconButton(
                                            onClick = { configTarget = source }
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.Settings,
                                                contentDescription = stringResource(R.string.settings_lyric_plugin_config),
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(onClick = { pendingDelete = source }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(checked = checked, onCheckedChange = onToggle)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_lyric_plugin_import_later),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    val deleteTarget = pendingDelete
    EllaMiuixDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.common_delete),
        summary = deleteTarget?.manifest?.name.orEmpty(),
        onDismissRequest = { pendingDelete = null }
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_delete),
            onCancel = { pendingDelete = null },
            onConfirm = {
                val target = deleteTarget ?: return@EllaMiuixDialogActions
                scope.launch {
                    settingsManager.setLyricoPluginEnabled(target.manifest.id, false)
                    pluginManager.deletePlugin(target.manifest.id)
                    pendingDelete = null
                    reloadToken++
                }
            }
        )
    }

    configTarget?.let { target ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = target.manifest.name,
            onDismissRequest = { configTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            ) {
                PluginConfigEditor(
                    source = target,
                    pluginManager = pluginManager,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PluginConfigEditor(
    source: LyricoPluginSource,
    pluginManager: LyricoPluginManager,
    modifier: Modifier = Modifier
) {
    var values by remember(source.manifest.id, source.manifest.versionCode) {
        mutableStateOf(pluginManager.pluginConfig(source))
    }

    fun update(field: PluginConfigField, value: String) {
        values = values + (field.key to value)
        pluginManager.setPluginConfigValue(source.manifest.id, field.key, value)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        source.manifest.configFields
            .filter { field -> field.isDependencySatisfied(values) }
            .forEach { field ->
                when (field.type) {
                    PluginConfigFieldType.MARKDOWN -> PluginConfigMarkdown(field)
                    PluginConfigFieldType.DROPDOWN -> PluginConfigDropdown(
                        field = field,
                        value = values[field.key].orEmpty(),
                        onValueChange = { update(field, it) }
                    )
                    PluginConfigFieldType.SWITCH -> PluginConfigSwitch(
                        field = field,
                        value = values[field.key].orEmpty(),
                        onValueChange = { update(field, it) }
                    )
                    PluginConfigFieldType.PASSWORD,
                    PluginConfigFieldType.TEXT,
                    PluginConfigFieldType.NUMBER,
                    PluginConfigFieldType.TEXTAREA -> PluginConfigTextField(
                        field = field,
                        value = values[field.key].orEmpty(),
                        onValueChange = { update(field, it) }
                    )
                }
            }
    }
}

@Composable
private fun PluginConfigMarkdown(field: PluginConfigField) {
    val text = field.defaultValueStringForDisplay()
    if (text.isBlank()) return
    Text(
        text = text,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun PluginConfigDropdown(
    field: PluginConfigField,
    value: String,
    onValueChange: (String) -> Unit
) {
    if (field.options.isEmpty()) return
    val selectedIndex = field.options.indexOfFirst { it.value == value }.takeIf { it >= 0 } ?: 0
    WindowSpinnerPreference(
        title = field.title,
        summary = field.summary ?: field.options.getOrNull(selectedIndex)?.summary.orEmpty(),
        items = field.options.map { option ->
            DropdownItem(
                title = option.label.ifBlank { option.value }
            )
        },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            field.options.getOrNull(index)?.value?.let(onValueChange)
        }
    )
}

@Composable
private fun PluginConfigSwitch(
    field: PluginConfigField,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = field.title, color = MiuixTheme.colorScheme.onSurface)
            field.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = value.equals("true", ignoreCase = true),
            onCheckedChange = { onValueChange(it.toString()) }
        )
    }
}

@Composable
private fun PluginConfigTextField(
    field: PluginConfigField,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        field.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        EllaMiuixTextField(
            value = value,
            onValueChange = onValueChange,
            label = field.title,
            singleLine = field.type != PluginConfigFieldType.TEXTAREA,
            visualTransformation = if (field.type == PluginConfigFieldType.PASSWORD) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun PluginConfigField.isDependencySatisfied(values: Map<String, String>): Boolean {
    val match = dependency?.match ?: return true
    return values[match.key] == match.value
}

private fun PluginConfigField.defaultValueStringForDisplay(): String =
    defaultValueString()

private val PLUGIN_ZIP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
