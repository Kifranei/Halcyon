package com.ella.music.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongMoreActionSheet(
    song: Song,
    extraTopContent: (@Composable ColumnScope.() -> Unit)?,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: (() -> Unit)?,
    onLyricTiming: (() -> Unit)?,
    onAudioTools: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showSpectrum: Boolean,
    showAddToQueue: Boolean
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val savedLayout by settingsManager.listActionMenuLayout.collectAsState(initial = "")
    val visibleActions = remember(savedLayout) {
        ActionMenuLayout.parse(savedLayout, ActionMenuIds.listDefaults)
            .visibleIds(ActionMenuIds.listDefaults)
    }
    EllaMiuixSheetColumn(
        verticalPadding = 8.dp,
        spacing = 12.dp,
        showHandle = false
    ) {
        extraTopContent?.invoke(this)
        EllaMiuixActionMenuGroup {
            visibleActions.forEach { actionId ->
                when (actionId) {
                ActionMenuIds.ADD_TO_PLAYLIST -> SongMenuItem(
                    stringResource(R.string.song_more_add_to_playlist),
                    onAddToPlaylist,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.ADD_TO_QUEUE -> if (showAddToQueue) {
                    SongMenuItem(
                        stringResource(R.string.common_add_to_queue),
                        onAddToQueue,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.PLAY_NEXT -> SongMenuItem(
                    stringResource(R.string.song_more_play_next),
                    onPlayNext,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.SHARE -> SongMenuItem(
                    stringResource(R.string.common_share),
                    onShare,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.SPECTRUM -> if (showSpectrum) {
                    SongMenuItem(
                        stringResource(R.string.song_more_view_spectrum),
                        onSpectrum,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.AI -> SongMenuItem(
                    stringResource(R.string.song_more_ai_title),
                    onAiInterpret,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.INFO -> SongMenuItem(
                    stringResource(R.string.song_more_view_song_info),
                    onInfo,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.RATING -> SongMenuItem(
                    stringResource(R.string.song_more_set_rating),
                    onRating,
                    icon = actionMenuIcon(actionId)
                )
                ActionMenuIds.EDIT_TAGS -> onEditTag?.let {
                    SongMenuItem(
                        stringResource(R.string.song_more_edit_tags_title),
                        it,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.LYRIC_TIMING -> onLyricTiming?.let {
                    SongMenuItem(
                        stringResource(R.string.song_more_lyric_timing),
                        it,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.AUDIO_TOOLS -> onAudioTools?.let {
                    SongMenuItem(
                        stringResource(R.string.song_more_audio_tools),
                        it,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.REMOVE_FROM_PLAYLIST -> onRemoveFromPlaylist?.let {
                    SongMenuItem(
                        stringResource(R.string.playlist_remove_song_title),
                        it,
                        danger = true,
                        icon = actionMenuIcon(actionId)
                    )
                }
                ActionMenuIds.DELETE -> onDelete?.let {
                    SongMenuItem(
                        stringResource(R.string.song_more_delete_permanently),
                        it,
                        danger = true,
                        icon = actionMenuIcon(actionId)
                    )
                }
                }
            }
        }
    }
}

@Composable
internal fun SongTagEditorSheet(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    SongSheetColumn {
        ExplicitSongTitle(
            title = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option -> SongMenuItem(option.label, onClick = { onOptionClick(option) }) }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}
