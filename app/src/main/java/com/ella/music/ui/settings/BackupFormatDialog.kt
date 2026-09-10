package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryTransferFormat
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.ellaOverlayCardColor
import com.ella.music.ui.components.EllaMiuixListItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class PlaybackExportFormat {
    Halcyon,
    PrismMusic,
    ConeMusic,
    LunaBeat,
    RawsMusic;

    fun transferFormat(): PlaybackHistoryTransferFormat = when (this) {
        Halcyon -> PlaybackHistoryTransferFormat.HALCYON
        PrismMusic -> PlaybackHistoryTransferFormat.PRISM_MUSIC
        ConeMusic -> PlaybackHistoryTransferFormat.CONE_MUSIC
        LunaBeat -> PlaybackHistoryTransferFormat.LUNA_BEAT
        RawsMusic -> PlaybackHistoryTransferFormat.RAWS_MUSIC
    }

    fun suggestedFileName(timestamp: Long = System.currentTimeMillis()): String = when (this) {
        Halcyon -> "halcyon-listening-history_$timestamp.json"
        PrismMusic -> "prism-listening-sessions_$timestamp.json"
        ConeMusic -> "cone-history_$timestamp.zip"
        LunaBeat -> "LunaBeat_$timestamp.zip"
        RawsMusic -> "rawsmusic_backup_$timestamp.json"
    }
}

@Composable
internal fun BackupFormatDialog(
    onDismissRequest: () -> Unit,
    onFormatSelected: (PlaybackExportFormat) -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.settings_backup_export_format_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_playback_export_halcyon),
                summary = stringResource(R.string.settings_backup_playback_export_halcyon_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.Halcyon) }
            )
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_playback_export_prism),
                summary = stringResource(R.string.settings_backup_playback_export_prism_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.PrismMusic) }
            )
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_playback_export_cone),
                summary = stringResource(R.string.settings_backup_playback_export_cone_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.ConeMusic) }
            )
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_playback_export_lunabeat),
                summary = stringResource(R.string.settings_backup_playback_export_lunabeat_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.LunaBeat) }
            )
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_playback_export_raws),
                summary = stringResource(R.string.settings_backup_playback_export_raws_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.RawsMusic) }
            )
        }
    }
}

@Composable
private fun BackupExportFormatRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = ellaOverlayCardColor())
    ) {
        EllaMiuixListItem(
            title = title,
            summary = summary,
            onClick = onClick
        )
    }
}
