package com.ella.music.ui.player

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.LyricSharePicker
import com.ella.music.ui.components.SongAiInterpretationSheet
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.SongMoreTagActionSheets
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.openSongWithMediaInfo
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun PlayerScreenSheetHost(
    context: android.content.Context,
    scope: CoroutineScope,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    song: Song?,
    playlists: List<UserPlaylist>,
    artistChoices: List<String>,
    onArtistChoicesChange: (List<String>) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    songInfoExpanded: Boolean,
    onSongInfoExpandedChange: (Boolean) -> Unit,
    dynamicCoverSheetSong: Song?,
    onDynamicCoverSheetSongChange: (Song?) -> Unit,
    ratingSheetSong: Song?,
    onRatingSheetSongChange: (Song?) -> Unit,
    aiSheetSong: Song?,
    onAiSheetSongChange: (Song?) -> Unit,
    deleteConfirmSong: Song?,
    onDeleteConfirmSongChange: (Song?) -> Unit,
    lyricMatchSong: Song?,
    onLyricMatchSongChange: (Song?) -> Unit,
    tagEditorSong: Song?,
    onTagEditorSongChange: (Song?) -> Unit,
    tagEditorKind: TagEditorOptionKind,
    onTagEditorKindChange: (TagEditorOptionKind) -> Unit,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    metadataEditorSong: Song?,
    onMetadataEditorSongChange: (Song?) -> Unit,
    lyricTimingEditorSong: Song?,
    onLyricTimingEditorSongChange: (Song?) -> Unit,
    onWritePermissionRequired: (WritePermissionRequiredException, suspend () -> Unit) -> Unit,
    playlistPickerSong: Song?,
    onPlaylistPickerSongChange: (Song?) -> Unit,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSong: Song?,
    onCreatePlaylistSongChange: (Song?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit
) {
    if (artistChoices.isNotEmpty()) {
        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = { onArtistChoicesChange(emptyList()) },
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            ArtistPickerSheet(
                artists = artistChoices,
                mainViewModel = mainViewModel,
                onArtistSelected = { artist ->
                    onArtistChoicesChange(emptyList())
                    onNavigateToArtist(artist)
                },
                onDismiss = { onArtistChoicesChange(emptyList()) }
            )
        }
    }

    if (songInfoExpanded && song != null) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_song_info),
            onDismissRequest = { onSongInfoExpandedChange(false) }
        ) {
            SongInfoSheet(
                song = song,
                audioInfoLoader = playerViewModel::getAudioInfo,
                tagInfoLoader = playerViewModel::getSongTagInfo,
                onOpenMediaInfo = { openSongWithMediaInfo(context, song) },
                onDismiss = { onSongInfoExpandedChange(false) }
            )
        }
    }

    if (dynamicCoverSheetSong != null) {
        DynamicCoverWebViewSheet(
            show = true,
            song = dynamicCoverSheetSong,
            onDismissRequest = { onDynamicCoverSheetSongChange(null) }
        )
    }

    PlayerLibraryActionSheets(
        context = context,
        scope = scope,
        mainViewModel = mainViewModel,
        ratingSheetSong = ratingSheetSong,
        onRatingSheetSongChange = onRatingSheetSongChange,
        deleteConfirmSong = deleteConfirmSong,
        onDeleteConfirmSongChange = onDeleteConfirmSongChange,
        onWritePermissionRequired = onWritePermissionRequired
    )

    SongMoreTagActionSheets(
        context = context,
        scope = scope,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        tagEditorSong = tagEditorSong,
        onTagEditorSongChange = onTagEditorSongChange,
        tagEditorKind = tagEditorKind,
        metadataEditorId = metadataEditorId,
        lyricTimingEditorId = lyricTimingEditorId,
        editTagTitle = stringResource(R.string.song_more_edit_tags_title),
        lyricTimingTitle = stringResource(R.string.song_more_lyric_timing),
        metadataEditorSong = metadataEditorSong,
        onMetadataEditorSongChange = onMetadataEditorSongChange,
        lyricTimingEditorSong = lyricTimingEditorSong,
        onLyricTimingEditorSongChange = onLyricTimingEditorSongChange,
        onWritePermissionRequired = onWritePermissionRequired
    )

    aiSheetSong?.let { currentSong ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_ai_title),
            onDismissRequest = { onAiSheetSongChange(null) }
        ) {
            SongAiInterpretationSheet(
                song = currentSong,
                mainViewModel = mainViewModel,
                onDismiss = { onAiSheetSongChange(null) }
            )
        }
    }

    lyricMatchSong?.let { currentSong ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_match_online_lyrics),
            onDismissRequest = { onLyricMatchSongChange(null) }
        ) {
            PluginLyricsMatchSheet(
                song = currentSong,
                mainViewModel = mainViewModel,
                onDismiss = { onLyricMatchSongChange(null) },
                onWritePermissionRequired = onWritePermissionRequired,
                onSongUpdated = playerViewModel::refreshCurrentSongAfterExternalEdit
            )
        }
    }

    PlayerPlaylistSheets(
        context = context,
        mainViewModel = mainViewModel,
        playlists = playlists,
        playlistPickerSong = playlistPickerSong,
        onPlaylistPickerSongChange = onPlaylistPickerSongChange,
        playlistPickerSongs = playlistPickerSongs,
        onPlaylistPickerSongsChange = onPlaylistPickerSongsChange,
        createPlaylistSong = createPlaylistSong,
        onCreatePlaylistSongChange = onCreatePlaylistSongChange,
        createPlaylistSongs = createPlaylistSongs,
        onCreatePlaylistSongsChange = onCreatePlaylistSongsChange
    )
}

@Composable
internal fun PlayerLyricShareHost(
    song: Song?,
    lyrics: List<LyricLine>,
    initialLine: LyricLine?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    palette: PlayerPalette,
    annotation: String,
    customInfo: String,
    shareTypeface: Typeface?,
    onDismiss: () -> Unit,
    onShare: (List<LyricLine>, Boolean) -> Unit,
    onVideoShare: ((List<LyricLine>, Boolean) -> Unit)? = null
) {
    initialLine?.let { line ->
        LyricSharePicker(
            song = song,
            lyrics = lyrics,
            initialLine = line,
            cover = embeddedCover ?: paletteBitmap,
            backgroundColors = listOf(palette.top, palette.middle, palette.bottom),
            contentColor = palette.onBackground,
            annotation = annotation,
            customInfo = customInfo,
            shareTypeface = shareTypeface,
            onDismiss = onDismiss,
            onShare = onShare,
            onVideoShare = onVideoShare
        )
    }
}
