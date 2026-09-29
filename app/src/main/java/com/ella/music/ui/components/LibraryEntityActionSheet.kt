package com.ella.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

data class LibraryEntityAction(
    val title: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

object LibraryEntityActions {
    @Composable
    fun pin(isPinned: Boolean, onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top),
        icon = if (isPinned) ActionMenuCommonIcons.unpin else ActionMenuCommonIcons.pin,
        onClick = onClick
    )

    @Composable
    fun pinToTop(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_pin_to_top),
        icon = ActionMenuCommonIcons.pin,
        onClick = onClick
    )

    @Composable
    fun share(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_share),
        icon = ActionMenuCommonIcons.share,
        onClick = onClick
    )

    @Composable
    fun addToPlaylist(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.song_more_add_to_playlist),
        icon = actionMenuIcon(ActionMenuIds.ADD_TO_PLAYLIST),
        onClick = onClick
    )

    @Composable
    fun addToQueue(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_add_to_queue),
        icon = ActionMenuCommonIcons.playlist,
        onClick = onClick
    )

    @Composable
    fun playNext(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.song_more_play_next),
        icon = actionMenuIcon(ActionMenuIds.PLAY_NEXT),
        onClick = onClick
    )

    @Composable
    fun desktopShortcut(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_add_desktop_shortcut),
        icon = ActionMenuCommonIcons.home,
        onClick = onClick
    )

    @Composable
    fun deletePermanently(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.song_more_delete_permanently),
        icon = ActionMenuCommonIcons.delete,
        danger = true,
        onClick = onClick
    )

    @Composable
    fun delete(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_delete),
        icon = ActionMenuCommonIcons.delete,
        danger = true,
        onClick = onClick
    )

    @Composable
    fun rename(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.common_rename),
        icon = ActionMenuCommonIcons.edit,
        onClick = onClick
    )

    @Composable
    fun edit(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.folder_playlist_edit),
        icon = ActionMenuCommonIcons.edit,
        onClick = onClick
    )

    @Composable
    fun refresh(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.folder_playlist_more_refresh),
        icon = MiuixIcons.Regular.Refresh,
        onClick = onClick
    )

    @Composable
    fun associate(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.folder_playlist_associate),
        icon = ActionMenuCommonIcons.link,
        onClick = onClick
    )

    @Composable
    fun block(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.folder_block_folder),
        icon = ActionMenuCommonIcons.block,
        danger = true,
        onClick = onClick
    )

    @Composable
    fun export(onClick: () -> Unit) = LibraryEntityAction(
        title = stringResource(R.string.playlist_export_title),
        icon = ActionMenuCommonIcons.download,
        onClick = onClick
    )
}

@Composable
fun LibraryEntityActionSheet(
    show: Boolean = true,
    title: String = stringResource(R.string.player_more_actions),
    onDismissRequest: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    actions: List<LibraryEntityAction>
) {
    if (!show) return
    EllaMiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = title,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            header?.invoke()
            EllaMiuixActionMenuGroup {
                actions.forEach { action ->
                    EllaMiuixMenuItem(
                        text = action.title,
                        subtitle = action.subtitle,
                        danger = action.danger,
                        icon = action.icon,
                        onClick = action.onClick
                    )
                }
            }
        }
    }
}
