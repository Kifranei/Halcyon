package com.ella.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.AppLogEntry
import com.ella.music.data.AppLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<EllaLogLevelFilter?>(null) }
    var selectedType by remember { mutableStateOf<EllaLogTypeFilter?>(null) }
    var selectedEntry by remember { mutableStateOf<AppLogEntry?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableIntStateOf(AppLogStore.retentionDays(context)) }
    val retentionOptions = remember { listOf(1, 3, 7, 14, 30) }

    val entries by produceState(initialValue = emptyList<AppLogEntry>(), refreshKey) {
        value = withContext(Dispatchers.IO) { AppLogStore.read(context) }
    }

    val filteredEntries = remember(entries, selectedLevel, selectedType, query) {
        val keyword = query.trim()
        entries.filter { entry ->
            (selectedLevel == null || selectedLevel?.matches(entry) == true) &&
                (selectedType == null || selectedType?.matches(entry) == true) &&
                (keyword.isBlank() || entry.matchesKeyword(context, keyword))
        }
    }
    val allLabel = stringResource(R.string.common_all)
    val shareSubject = stringResource(R.string.logs_share_subject)
    val shareChooserTitle = stringResource(R.string.logs_share_chooser_title)
    val noShareApp = stringResource(R.string.share_no_available_app)
    val logClipLabel = stringResource(R.string.logs_clip_label)
    val copiedToast = stringResource(R.string.logs_copied)

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun shareLogs() {
        scope.launch {
            val filterDescription = buildString {
                append("all persisted logs")
                append("; visible before export=${filteredEntries.size}/${entries.size}")
                append("; level=${selectedLevel?.name ?: "ALL"}")
                append("; type=${selectedType?.name ?: "ALL"}")
                query.trim().takeIf { it.isNotBlank() }?.let { append("; query=${it.take(80)}") }
            }
            val file = withContext(Dispatchers.IO) {
                AppLogStore.info(
                    context,
                    "LogExport",
                    "Export requested: $filterDescription"
                )
                val persistedEntries = AppLogStore.read(context)
                val exportScope = "$filterDescription; persisted after request=${persistedEntries.size}"
                AppLogStore.exportDetailedReport(
                    context = context,
                    entries = persistedEntries,
                    scopeDescription = exportScope
                ).also { exportedFile ->
                    AppLogStore.info(
                        context,
                        "LogExport",
                        "Export finished: entries=${persistedEntries.size}, bytes=${exportedFile.length()}, file=${exportedFile.name}"
                    )
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                putExtra(Intent.EXTRA_TITLE, file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, shareSubject, uri)
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(intent, shareChooserTitle)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.onFailure {
                showToast(noShareApp)
            }
        }
    }

    fun copyEntry(entry: AppLogEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(logClipLabel, entry.formatForCopy(context)))
        showToast(copiedToast)
        showDetailSheet = false
    }

    Scaffold(
        topBar = {
            EllaSmallTopAppBar(
                title = stringResource(R.string.logs_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::shareLogs
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = stringResource(R.string.logs_share_action)
                        )
                    }
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.logs_clear_action),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null
        ) {
            item("filters") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.logs_level_filter),
                        items = listOf(allLabel) + EllaLogLevelFilter.entries.map { stringResource(it.labelRes) },
                        selectedIndex = selectedLevel?.let { EllaLogLevelFilter.entries.indexOf(it) + 1 } ?: 0,
                        onSelectedIndexChange = { index ->
                            selectedLevel = if (index == 0) null else EllaLogLevelFilter.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.logs_type_filter),
                        items = listOf(allLabel) + EllaLogTypeFilter.entries.map { stringResource(it.labelRes) },
                        selectedIndex = selectedType?.let { EllaLogTypeFilter.entries.indexOf(it) + 1 } ?: 0,
                        onSelectedIndexChange = { index ->
                            selectedType = if (index == 0) null else EllaLogTypeFilter.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.logs_retention_filter),
                        items = retentionOptions.map { stringResource(R.string.logs_retention_days, it) },
                        selectedIndex = retentionOptions.indexOf(retentionDays).takeIf { it >= 0 } ?: 2,
                        onSelectedIndexChange = { index ->
                            val days = retentionOptions[index]
                            scope.launch {
                                val removed = withContext(Dispatchers.IO) { AppLogStore.setRetentionDays(context, days) }
                                retentionDays = days
                                refreshKey++
                                showToast(
                                    if (removed > 0) {
                                        context.getString(R.string.logs_retention_removed, removed)
                                    } else {
                                        context.getString(R.string.logs_retention_set, days)
                                    }
                                )
                            }
                        }
                    )
                }
            }

            item("search") {
                EllaMiuixTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.logs_search_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }

            item("summary") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.logs_summary_title),
                        summary = stringResource(
                            R.string.logs_summary,
                            entries.size,
                            filteredEntries.size,
                            entries.count { it.level.equals("ERROR", true) },
                            entries.count { it.level.equals("WARNING", true) || it.level.equals("WARN", true) },
                            retentionDays
                        )
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                item("empty") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(title = if (entries.isEmpty()) stringResource(R.string.logs_empty) else stringResource(R.string.logs_empty_filtered))
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { entry -> "${entry.time}-${entry.level}-${entry.tag}-${entry.message.hashCode()}" }
                ) { entry ->
                    AppLogItem(
                        entry = entry,
                        onClick = {
                            selectedEntry = entry
                            showDetailSheet = true
                        }
                    )
                }
            }
        }
    }

    AppLogDetailSheet(
        show = showDetailSheet,
        entry = selectedEntry,
        onDismiss = { showDetailSheet = false },
        onDismissFinished = {
            showDetailSheet = false
            selectedEntry = null
        },
        onCopy = ::copyEntry
    )

    EllaMiuixDialog(
        show = showClearDialog,
        title = stringResource(R.string.logs_clear_action),
        summary = stringResource(R.string.logs_clear_message, entries.size),
        onDismissRequest = { showClearDialog = false }
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_clear),
            onCancel = { showClearDialog = false },
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { AppLogStore.clear(context) }
                    refreshKey++
                    showClearDialog = false
                    showToast(context.getString(R.string.logs_cleared))
                }
            }
        )
    }
}
