package com.ella.music.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class BackupType(val titleRes: Int, val summaryRes: Int) {
    Personalization(
        titleRes = R.string.settings_backup_type_personalization,
        summaryRes = R.string.settings_backup_type_personalization_summary
    ),
    WallpapersAndImages(
        titleRes = R.string.settings_backup_type_wallpapers_and_images,
        summaryRes = R.string.settings_backup_type_wallpapers_and_images_summary
    ),
    LibraryAndScan(
        titleRes = R.string.settings_backup_type_library_scan,
        summaryRes = R.string.settings_backup_type_library_scan_summary
    ),
    AiConfigAndChat(
        titleRes = R.string.settings_backup_type_ai_config_chat,
        summaryRes = R.string.settings_backup_type_ai_config_chat_summary
    ),
    PlaybackStats(
        titleRes = R.string.settings_backup_type_playback_stats,
        summaryRes = R.string.settings_backup_type_playback_stats_summary
    ),
    Playlists(
        titleRes = R.string.settings_backup_type_playlists,
        summaryRes = R.string.settings_backup_type_playlists_summary
    ),
    FolderPlaylists(
        titleRes = R.string.settings_backup_type_folder_playlists,
        summaryRes = R.string.settings_backup_type_folder_playlists_summary
    ),
    Equalizer(
        titleRes = R.string.settings_backup_type_equalizer,
        summaryRes = R.string.settings_backup_type_equalizer_summary
    ),
    OnlineSources(
        titleRes = R.string.settings_backup_type_online_sources,
        summaryRes = R.string.settings_backup_type_online_sources_summary
    ),
    Fonts(
        titleRes = R.string.settings_backup_type_fonts,
        summaryRes = R.string.settings_backup_type_fonts_summary
    )
}

@Composable
internal fun BackupTypeSelectionSheet(
    show: Boolean,
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<BackupType>) -> Unit,
    initialSelected: Set<BackupType> = BackupType.entries.toSet(),
    availableTypes: Set<BackupType> = BackupType.entries.toSet()
) {
    val visibleTypes = remember(availableTypes) { BackupType.entries.filter { it in availableTypes } }
    var selected by remember(show, initialSelected, availableTypes) {
        mutableStateOf(initialSelected.intersect(availableTypes))
    }
    val allSelected = visibleTypes.isNotEmpty() && selected.size == visibleTypes.size

    EllaMiuixBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BackupTypeSelectAllItem(
                    selected = allSelected,
                    onCheckedChange = { checked ->
                        selected = if (checked) visibleTypes.toSet() else emptySet()
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                visibleTypes.forEach { type ->
                    BackupTypeCheckboxItem(
                        type = type,
                        checked = type in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + type else selected - type
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = confirmText,
                onCancel = onDismiss,
                onConfirm = {
                    if (selected.isNotEmpty()) {
                        val confirmedSelection = selected
                        onConfirm(confirmedSelection)
                        onDismiss()
                    } else {
                        onDismiss()
                    }
                }
            )
        }
    }
}

@Composable
private fun BackupTypeSelectAllItem(
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!selected) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackupTypeCheckbox(checked = selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_backup_type_select_all),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BackupTypeCheckboxItem(
    type: BackupType,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackupTypeCheckbox(checked = checked)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(type.titleRes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(type.summaryRes),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackupTypeCheckbox(
    checked: Boolean
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainer
            )
            .combinedClickable(onClick = { }),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Regular.Ok,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
