package com.ella.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.EllaMiuixSheetHandle
import androidx.compose.ui.focus.focusRequester
import top.yukonga.miuix.kmp.basic.TextField
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.SongMenuItem
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.openSongWithMediaInfo
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ArtistCreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
internal fun ArtistTagEditorMenu(
    song: Song,
    onDismiss: () -> Unit,
    onOptionClick: (com.ella.music.ui.components.TagEditorOption) -> Unit
) {
    val context = LocalContext.current
    val options = remember(song) {
        buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_edit_tags_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ExplicitSongTitle(
            title = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option ->
            ArtistMenuItem(option.label, onClick = { onOptionClick(option) })
        }
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistSongInfoMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SongInfoSheet(
        song = song,
        audioInfoLoader = mainViewModel::getAudioInfo,
        tagInfoLoader = mainViewModel::getSongTagInfo,
        onOpenMediaInfo = {
            onDismiss()
            openSongWithMediaInfo(context, song)
        },
        onDismiss = onDismiss,
        onUpdateModifiedTime = { mainViewModel.updateSongModifiedTime(song, it) },
        leadingContent = {
            SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        }
    )
}

@Composable
internal fun ArtistAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_ai_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Text(
            text = when {
                result == null -> stringResource(R.string.song_more_loading_ai)
                result?.isSuccess == true -> result?.getOrNull().orEmpty()
                else -> result?.exceptionOrNull()?.message ?: stringResource(R.string.song_more_ai_failed)
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        ArtistMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
internal fun ArtistSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    EllaMiuixSheetColumn(maxHeight = 400.dp, verticalPadding = 16.dp, showHandle = false, content = content)
}

@Composable
internal fun ArtistSheetHandle() {
    EllaMiuixSheetHandle()
}

@Composable
internal fun ArtistMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    EllaMiuixMenuItem(text = text, onClick = onClick, danger = danger)
}
