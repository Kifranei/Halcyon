package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ella.music.data.model.Song
import com.ella.music.ui.components.TagEditorOptionKind

internal class PlayerScreenUiState(
    menuExpanded: Boolean = false,
    dynamicCoverSheetSong: Song? = null,
    songInfoExpanded: Boolean = false,
    queueExpanded: Boolean = false,
    artistChoices: List<String> = emptyList(),
    playlistPickerSong: Song? = null,
    playlistPickerSongs: List<Song>? = null,
    createPlaylistSong: Song? = null,
    createPlaylistSongs: List<Song>? = null,
    ratingSheetSong: Song? = null,
    aiSheetSong: Song? = null,
    deleteConfirmSong: Song? = null,
    tagEditorSong: Song? = null,
    tagEditorKind: TagEditorOptionKind = TagEditorOptionKind.Metadata,
    metadataEditorSong: Song? = null,
    lyricTimingEditorSong: Song? = null,
    dynamicCoverFailedPath: String? = null,
    lyricMatchSong: Song? = null,
    musicVideoVisible: Boolean = false
) {
    var menuExpanded by mutableStateOf(menuExpanded)
    var dynamicCoverSheetSong by mutableStateOf(dynamicCoverSheetSong)
    var songInfoExpanded by mutableStateOf(songInfoExpanded)
    var queueExpanded by mutableStateOf(queueExpanded)
    var artistChoices by mutableStateOf(artistChoices)
    var playlistPickerSong by mutableStateOf(playlistPickerSong)
    var playlistPickerSongs by mutableStateOf(playlistPickerSongs)
    var createPlaylistSong by mutableStateOf(createPlaylistSong)
    var createPlaylistSongs by mutableStateOf(createPlaylistSongs)
    var ratingSheetSong by mutableStateOf(ratingSheetSong)
    var aiSheetSong by mutableStateOf(aiSheetSong)
    var deleteConfirmSong by mutableStateOf(deleteConfirmSong)
    var tagEditorSong by mutableStateOf(tagEditorSong)
    var tagEditorKind by mutableStateOf(tagEditorKind)
    var metadataEditorSong by mutableStateOf(metadataEditorSong)
    var lyricTimingEditorSong by mutableStateOf(lyricTimingEditorSong)
    var pendingWriteRetry by mutableStateOf<(suspend () -> Unit)?>(null)
    var dynamicCoverFailedPath by mutableStateOf(dynamicCoverFailedPath)
    var lyricMatchSong by mutableStateOf(lyricMatchSong)
    var musicVideoVisible by mutableStateOf(musicVideoVisible)
}

internal class PlayerLandscapeUiState(
    private val expandedState: MutableState<Boolean>
) {
    var expanded: Boolean
        get() = expandedState.value
        set(value) {
            expandedState.value = value
        }
}

@Composable
internal fun rememberPlayerScreenUiState(): PlayerScreenUiState = remember { PlayerScreenUiState() }

@Composable
internal fun rememberPlayerLandscapeUiState(): PlayerLandscapeUiState {
    val expandedState = rememberSaveable { mutableStateOf(false) }
    return remember(expandedState) {
        PlayerLandscapeUiState(expandedState)
    }
}
