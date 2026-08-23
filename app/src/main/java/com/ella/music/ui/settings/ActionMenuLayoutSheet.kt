package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.ui.components.EllaMiuixSheetActions
import sh.calvin.reorderable.ReorderableColumn
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ActionMenuLayoutPage(
    savedLayout: String,
    defaultOrder: List<String>,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var layout by remember(savedLayout, defaultOrder) {
        mutableStateOf(ActionMenuLayout.parse(savedLayout, defaultOrder))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionLayoutCommand(
                text = stringResource(R.string.common_select_all),
                modifier = Modifier.weight(1f),
                onClick = { layout = layout.copy(hidden = emptySet()) }
            )
            ActionLayoutCommand(
                text = stringResource(R.string.common_invert_selection),
                modifier = Modifier.weight(1f),
                onClick = { layout = layout.copy(hidden = layout.order.toSet() - layout.hidden) }
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            cornerRadius = 18.dp,
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            ReorderableColumn(
                list = layout.order,
                onSettle = { fromIndex, toIndex ->
                    if (fromIndex in layout.order.indices && toIndex in layout.order.indices) {
                        layout = layout.copy(order = layout.order.move(fromIndex, toIndex))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { index, id, isDragging ->
                val visible = id !in layout.hidden
                ReorderableItem {
                    BasicComponent(
                        title = actionMenuLabel(id),
                        summary = stringResource(R.string.settings_action_menu_position, index + 1),
                        modifier = Modifier
                            .background(
                                if (isDragging) {
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .longPressDraggableHandle()
                            .clickable {
                                layout = layout.copy(
                                    hidden = if (visible) layout.hidden + id else layout.hidden - id
                                )
                            },
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "☰",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                if (visible) {
                                    Icon(
                                        imageVector = MiuixIcons.Basic.Check,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        EllaMiuixSheetActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = onCancel,
            onConfirm = { onSave(layout.serialize()) }
        )
    }
}

@Composable
private fun ActionLayoutCommand(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        onClick = onClick
    ) {
        BasicComponent(title = text)
    }
}

private fun List<String>.move(from: Int, to: Int): List<String> = toMutableList().apply {
    add(to, removeAt(from))
}

@Composable
private fun actionMenuLabel(id: String): String = stringResource(
    when (id) {
        ActionMenuIds.ADD_TO_PLAYLIST -> R.string.song_more_add_to_playlist
        ActionMenuIds.ADD_TO_QUEUE -> R.string.common_add_to_queue
        ActionMenuIds.PLAY_NEXT -> R.string.song_more_play_next
        ActionMenuIds.SHARE -> R.string.common_share
        ActionMenuIds.SPECTRUM -> R.string.song_more_view_spectrum
        ActionMenuIds.AI -> R.string.song_more_ai_title
        ActionMenuIds.INFO -> R.string.song_more_view_song_info
        ActionMenuIds.RATING -> R.string.song_more_set_rating
        ActionMenuIds.EDIT_TAGS -> R.string.song_more_edit_tags_title
        ActionMenuIds.LYRIC_TIMING -> R.string.song_more_lyric_timing
        ActionMenuIds.AUDIO_TOOLS -> R.string.song_more_audio_tools
        ActionMenuIds.REMOVE_FROM_PLAYLIST -> R.string.playlist_remove_song_title
        ActionMenuIds.DELETE -> R.string.song_more_delete_permanently
        ActionMenuIds.AUDIO_OUTPUT -> R.string.player_audio_output_info
        ActionMenuIds.CASTING -> R.string.casting_devices_title
        ActionMenuIds.AB_REPEAT -> R.string.player_repeat_mode
        ActionMenuIds.REMOTE_QUALITY -> R.string.settings_action_menu_remote_quality
        ActionMenuIds.LANDSCAPE -> R.string.player_landscape_lyrics
        ActionMenuIds.LYRICS_DISPLAY -> R.string.player_lyrics_display
        ActionMenuIds.DYNAMIC_COVER -> R.string.player_match_dynamic_cover
        ActionMenuIds.VISUALIZER -> R.string.player_visualizer_settings
        ActionMenuIds.ONLINE_LYRICS -> R.string.player_match_online_lyrics
        ActionMenuIds.LYRIC_OFFSET -> R.string.player_lyric_offset
        ActionMenuIds.KEEP_SCREEN_ON -> R.string.settings_action_menu_keep_screen_on
        ActionMenuIds.DOWNLOAD -> R.string.player_download_lx_song
        else -> R.string.player_more_actions
    }
)
