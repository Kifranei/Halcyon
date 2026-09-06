package com.ella.music.ui.folder

import android.provider.DocumentsContract
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.ScanRefreshIconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun ScanStatusCard(scanProgress: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (scanProgress > 0) {
                stringResource(R.string.library_scanning_count, scanProgress)
            } else {
                stringResource(R.string.folder_scanning_library)
            },
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
internal fun MediaSourceModeCard(
    useAndroidMediaLibrary: Boolean,
    fullTagSearchEnabled: Boolean,
    customFolderCount: Int,
    allFilesAccessGranted: Boolean,
    highlight: Boolean = false,
    onUseAndroidMediaLibraryChange: (Boolean) -> Unit,
    onFullTagSearchEnabledChange: (Boolean) -> Unit,
    onAllFilesAccessClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scanHighlightBringIntoView(highlight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = scanHighlightCardColor(highlight))
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SwitchPreference(
                title = stringResource(R.string.folder_use_android_media_library),
                summary = if (useAndroidMediaLibrary) {
                    stringResource(R.string.folder_scan_android_media_summary)
                } else {
                    stringResource(R.string.folder_scan_custom_folders_summary, customFolderCount)
                },
                checked = useAndroidMediaLibrary,
                onCheckedChange = onUseAndroidMediaLibraryChange
            )
            SwitchPreference(
                title = stringResource(R.string.settings_full_tag_search),
                summary = if (fullTagSearchEnabled) {
                    stringResource(R.string.settings_full_tag_search_summary_on)
                } else {
                    stringResource(R.string.settings_full_tag_search_summary_off)
                },
                checked = fullTagSearchEnabled,
                onCheckedChange = onFullTagSearchEnabledChange
            )
            SwitchPreference(
                title = stringResource(R.string.folder_all_files_access),
                summary = if (allFilesAccessGranted) {
                    stringResource(R.string.folder_all_files_access_summary_on)
                } else {
                    stringResource(R.string.folder_all_files_access_summary_off)
                },
                checked = allFilesAccessGranted,
                onCheckedChange = { enabled ->
                    if (enabled != allFilesAccessGranted) onAllFilesAccessClick()
                }
            )
        }
    }
}

@Composable
internal fun UsbFoldersCard(
    usbFolderUris: List<String>,
    highlight: Boolean = false,
    onRemove: (String) -> Unit,
    scanEnabled: Boolean,
    onScan: () -> Unit,
    onDeepRescan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scanHighlightBringIntoView(highlight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = scanHighlightCardColor(highlight))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.folder_usb_directories),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.folder_usb_directories_summary, usbFolderUris.size),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                ScanRefreshIconButton(
                    enabled = scanEnabled,
                    onScan = onScan,
                    onDeepRescan = onDeepRescan,
                    iconSize = 22.dp,
                    contentDescription = stringResource(R.string.folder_full_scan)
                )
            }
            usbFolderUris.forEach { uri ->
                val displayName = runCatching {
                    val docUri = Uri.parse(uri)
                    val docId = DocumentsContract.getTreeDocumentId(docUri)
                    docId.substringAfterLast('/').ifBlank { docId.substringBeforeLast('/') }
                }.getOrDefault(uri.substringAfterLast('/').ifBlank { uri })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FolderOutlineIcon(
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = uri,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(uri) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Close,
                            contentDescription = stringResource(R.string.common_remove),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedScanFoldersCard(
    folders: List<String>,
    hiddenFolders: Set<String>,
    highlight: Boolean = false,
    onVisibilityChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    scanEnabled: Boolean,
    onScan: () -> Unit,
    onDeepRescan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scanHighlightBringIntoView(highlight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = scanHighlightCardColor(highlight))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.folder_local_scan_directories),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.folder_local_scan_directories_summary, folders.size),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                ScanRefreshIconButton(
                    enabled = scanEnabled,
                    onScan = onScan,
                    onDeepRescan = onDeepRescan,
                    iconSize = 22.dp,
                    contentDescription = stringResource(R.string.folder_full_scan)
                )
            }
            folders.forEach { folder ->
                val isVisible = folder.normalizeFolderPath().lowercase(Locale.ROOT) !in hiddenFolders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FolderVisibilityCheckbox(
                        checked = isVisible,
                        onCheckedChange = { onVisibilityChange(folder, it) }
                    )
                    FolderOutlineIcon(
                        tint = if (isVisible) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.substringAfterLast('/').ifBlank { folder },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isVisible) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                        )
                        Text(
                            text = folder,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(folder) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Close,
                            contentDescription = stringResource(R.string.common_remove),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FolderVisibilityCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (checked) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                }
            )
            .combinedClickable(onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.folder_show_folder),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BlockedFoldersEntryCard(
    count: Int,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scanHighlightBringIntoView(highlight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = scanHighlightCardColor(highlight)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Folder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.folder_blocked_folders),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.folder_blocked_folders_summary, count),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun scanHighlightCardColor(highlight: Boolean): Color {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color(0xFFFFFFFF)
    val highlightColor = if (isDark) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    var lit by remember(highlight) { mutableStateOf(false) }
    LaunchedEffect(highlight) {
        if (!highlight) return@LaunchedEffect
        repeat(4) {
            lit = !lit
            delay(180)
        }
        lit = false
    }
    return if (lit) highlightColor else cardColor
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.scanHighlightBringIntoView(highlight: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(highlight) {
        if (highlight) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
}

@Composable
internal fun FolderBlockDialog(
    folderPath: String,
    onDismiss: () -> Unit,
    onBlock: () -> Unit
) {
    EllaMiuixDialog(
        show = true,
        title = stringResource(R.string.folder_block_folder),
        summary = folderPath,
        onDismissRequest = onDismiss
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.folder_block),
            onCancel = onDismiss,
            onConfirm = onBlock
        )
    }
}

@Composable
internal fun BlockedFoldersDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.folder_blocked_folders),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .fillMaxHeight(0.6f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folder,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(folder) }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_remove),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_clear), onClick = onClear),
                    EllaMiuixAction(text = stringResource(R.string.common_done), onClick = onDismiss, primary = true)
                ),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
