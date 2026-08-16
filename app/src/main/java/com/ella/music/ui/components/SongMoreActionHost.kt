package com.ella.music.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SongMoreActionHost(
    actionSong: Song?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onDismissAction: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onSongRemovedFromPlaylist: ((Song) -> Unit)? = null,
    deleteFromLibrary: Boolean = true,
    showDelete: Boolean = true,
    showLocalFileActions: Boolean = true,
    showAddToQueue: Boolean = true,
    resolveSongForAction: (suspend (Song) -> Song)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    showSongTitleInSheetHeader: Boolean = true,
    extraTopContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val actionSheetTitle = stringResource(R.string.song_more_actions_title)
    val addToPlaylistFailed = stringResource(R.string.song_more_add_to_playlist_failed)
    val addToQueueFailed = stringResource(R.string.song_more_add_to_queue_failed)
    val playNextFailed = stringResource(R.string.song_more_play_next_failed)
    val shareFailed = stringResource(R.string.song_more_share_failed)
    val addedToPlayNext = stringResource(R.string.song_more_added_to_play_next)
    val addedToQueue = stringResource(R.string.song_more_added_to_queue)
    val noArtistJump = stringResource(R.string.song_more_no_artist_jump)
    val noAlbumJump = stringResource(R.string.song_more_no_album_jump)
    val selectArtistTitle = stringResource(R.string.song_more_select_artist)
    val addToPlaylistTitle = stringResource(R.string.song_more_add_to_playlist_title)
    val editTagTitle = stringResource(R.string.song_more_edit_tags_title)
    val lyricTimingTitle = stringResource(R.string.song_more_lyric_timing)
    val aiInterpretTitle = stringResource(R.string.song_more_ai_title)
    val playlists by mainViewModel.playlists.collectAsState(initial = emptyList())
    val metadataEditorId by mainViewModel.settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by mainViewModel.settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val scope = rememberCoroutineScope()
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorKind by remember { mutableStateOf(TagEditorOptionKind.Metadata) }
    var metadataEditorSong by remember { mutableStateOf<Song?>(null) }
    var lyricTimingEditorSong by remember { mutableStateOf<Song?>(null) }
    var audioToolsSong by remember { mutableStateOf<Song?>(null) }
    var ratingSong by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var aiSong by remember { mutableStateOf<Song?>(null) }
    var coverPreviewSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var dangerConfirmTitle by remember { mutableStateOf("") }
    var dangerConfirmMessage by remember { mutableStateOf("") }
    var dangerConfirmText by remember { mutableStateOf("") }
    var dangerConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWriteRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val writePermissionNeeded = stringResource(R.string.song_more_metadata_write_permission_needed)
    val actionCoverModel by produceState<Any?>(
        initialValue = null,
        actionSong?.let { listOf(it.playlistIdentityKey(), it.dateModified, it.fileSize).joinToString("|") }
    ) {
        value = actionSong?.let { song ->
            withContext(Dispatchers.IO) { mainViewModel.getOriginalCoverModel(song) }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWriteRetry?.let { retry ->
                scope.launch { retry() }
                pendingWriteRetry = null
            }
        } else {
            pendingWriteRetry = null
            Toast.makeText(context, writePermissionNeeded, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeAction() = onDismissAction()

    fun requestDangerConfirm(
        title: String,
        message: String,
        confirmText: String,
        action: () -> Unit
    ) {
        dangerConfirmTitle = title
        dangerConfirmMessage = message
        dangerConfirmText = confirmText
        dangerConfirmAction = action
    }

    fun runResolvedSongAction(
        sourceSong: Song,
        failureMessage: String,
        action: (Song) -> Unit
    ) {
        scope.launch {
            runCatching {
                resolveSongForAction?.invoke(sourceSong) ?: sourceSong
            }.onSuccess { resolvedSong ->
                action(resolvedSong)
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: failureMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    actionSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = if (showSongTitleInSheetHeader) {
                song.title.toSongTitlePresentation().text.ifBlank { actionSheetTitle }
            } else {
                actionSheetTitle
            },
            onDismissRequest = ::closeAction
        ) {
            SongMoreActionSheet(
                song = song,
                extraTopContent = {
                    SongMoreCoverPreview(
                        song = song,
                        coverModel = actionCoverModel,
                        onPreview = { coverPreviewSong = song },
                        onArtist = {
                            val artists = splitArtistNames(song.artist)
                                .distinctBy { it.tagIdentityKey() }
                            when (artists.size) {
                                0 -> Toast.makeText(context, noArtistJump, Toast.LENGTH_SHORT).show()
                                1 -> onNavigateToArtist(artists.first())
                                else -> artistChoices = artists
                            }
                            closeAction()
                        },
                        onAlbum = {
                            val albumId = song.albumIdentityId()
                            if (albumId > 0L) {
                                onNavigateToAlbum(albumId)
                                closeAction()
                            } else {
                                Toast.makeText(context, noAlbumJump, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    extraTopContent?.invoke(this)
                },
                onDismiss = ::closeAction,
                onAddToPlaylist = {
                    runResolvedSongAction(song, addToPlaylistFailed) { resolvedSong ->
                        playlistSong = resolvedSong
                        closeAction()
                    }
                },
                onAddToQueue = {
                    runResolvedSongAction(song, addToQueueFailed) { resolvedSong ->
                        playerViewModel.addToPlaylist(resolvedSong)
                        Toast.makeText(context, addedToQueue, Toast.LENGTH_SHORT).show()
                        closeAction()
                    }
                },
                onPlayNext = {
                    runResolvedSongAction(song, playNextFailed) { resolvedSong ->
                        playerViewModel.playNext(resolvedSong)
                        Toast.makeText(context, addedToPlayNext, Toast.LENGTH_SHORT).show()
                        closeAction()
                    }
                },
                onShare = {
                    runResolvedSongAction(song, shareFailed) { resolvedSong ->
                        shareLocalSong(context, resolvedSong)
                        closeAction()
                    }
                },
                onSpectrum = {
                    scope.launch { SpectrumViewerLauncher.openSelected(context, song) }
                    closeAction()
                },
                onInfo = {
                    infoSong = song
                    closeAction()
                },
                onRating = {
                    ratingSong = song
                    closeAction()
                },
                onAiInterpret = {
                    aiSong = song
                    closeAction()
                },
                onArtist = {
                    val artists = splitArtistNames(song.artist)
                        .distinctBy { it.tagIdentityKey() }
                    when (artists.size) {
                        0 -> Toast.makeText(context, noArtistJump, Toast.LENGTH_SHORT).show()
                        1 -> onNavigateToArtist(artists.first())
                        else -> artistChoices = artists
                    }
                    closeAction()
                },
                onAlbum = {
                    val albumId = song.albumIdentityId()
                    if (albumId > 0L) {
                        onNavigateToAlbum(albumId)
                    } else {
                        Toast.makeText(context, noAlbumJump, Toast.LENGTH_SHORT).show()
                    }
                    closeAction()
                },
                onEditTag = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.Metadata
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onLyricTiming = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.LyricTiming
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onAudioTools = if (showLocalFileActions) {
                    {
                        audioToolsSong = song
                        closeAction()
                    }
                } else null,
                onRemoveFromPlaylist = onSongRemovedFromPlaylist?.let {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = context.getString(R.string.playlist_remove_song_title),
                            message = context.getString(
                                R.string.song_more_remove_from_playlist_message,
                                song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                            ),
                            confirmText = context.getString(R.string.common_remove)
                        ) {
                            it(song)
                        }
                    }
                },
                onDelete = if (showDelete) {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_song_title)
                            } else {
                                context.getString(R.string.song_more_remove_from_library_title)
                            },
                            message = if (deleteFromLibrary) {
                                context.getString(
                                    R.string.song_more_delete_song_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            } else {
                                context.getString(
                                    R.string.song_more_remove_from_library_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            },
                            confirmText = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_permanently)
                            } else {
                                context.getString(R.string.common_remove)
                            }
                        ) {
                            if (onDeleteSong != null) {
                                onDeleteSong(song)
                            } else if (deleteFromLibrary) {
                                scope.launch {
                                    val result = mainViewModel.deleteSongsResult(listOf(song))
                                    if (result.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.library_deleted_songs, 1), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val error = result.exceptionOrNull()
                                        if (error is WritePermissionRequiredException) {
                                            pendingWriteRetry = {
                                                mainViewModel.removeSongsFromLibrary(listOf(song))
                                                Toast.makeText(context, context.getString(R.string.library_deleted_songs, 1), Toast.LENGTH_SHORT).show()
                                            }
                                            writePermissionLauncher.launch(
                                                IntentSenderRequest.Builder(error.intentSender).build()
                                            )
                                        } else {
                                            Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                mainViewModel.removeSongsFromLibrary(listOf(song))
                            }
                        }
                    }
                } else null,
                showSpectrum = showLocalFileActions,
                showAddToQueue = showAddToQueue
            )
        }
    }

    ConfirmDangerDialog(
        show = dangerConfirmAction != null,
        title = dangerConfirmTitle,
        message = dangerConfirmMessage,
        confirmText = dangerConfirmText,
        onDismiss = { dangerConfirmAction = null },
        onConfirm = {
            val action = dangerConfirmAction
            dangerConfirmAction = null
            action?.invoke()
        }
    )

    if (artistChoices.isNotEmpty()) {
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = selectArtistTitle,
            onDismissRequest = { artistChoices = emptyList() }
        ) {
            ArtistPickerContent(
                artists = artistChoices,
                mainViewModel = mainViewModel,
                onArtistSelected = { artist ->
                    artistChoices = emptyList()
                    onNavigateToArtist(artist)
                },
                onDismiss = { artistChoices = emptyList() }
            )
        }
    }

    SongMorePlaylistActionSheets(
        context = context,
        mainViewModel = mainViewModel,
        playlists = playlists,
        playlistSong = playlistSong,
        onPlaylistSongChange = { playlistSong = it },
        createPlaylistSong = createPlaylistSong,
        onCreatePlaylistSongChange = { createPlaylistSong = it },
        addToPlaylistTitle = addToPlaylistTitle
    )

    SongMoreTagActionSheets(
        context = context,
        scope = scope,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        tagEditorSong = tagEditorSong,
        onTagEditorSongChange = { tagEditorSong = it },
        tagEditorKind = tagEditorKind,
        metadataEditorId = metadataEditorId,
        lyricTimingEditorId = lyricTimingEditorId,
        editTagTitle = editTagTitle,
        lyricTimingTitle = lyricTimingTitle,
        metadataEditorSong = metadataEditorSong,
        onMetadataEditorSongChange = { metadataEditorSong = it },
        lyricTimingEditorSong = lyricTimingEditorSong,
        onLyricTimingEditorSongChange = { lyricTimingEditorSong = it },
        onWritePermissionRequired = { error, retry ->
            pendingWriteRetry = retry
            writePermissionLauncher.launch(
                IntentSenderRequest.Builder(error.intentSender).build()
            )
        }
    )

    audioToolsSong?.let { song ->
        SongAudioToolsSheet(
            song = song,
            onDismiss = { audioToolsSong = null },
            onExported = { mainViewModel.scanMusic() }
        )
    }

    SongMoreInfoActionSheets(
        context = context,
        scope = scope,
        mainViewModel = mainViewModel,
        ratingSong = ratingSong,
        onRatingSongChange = { ratingSong = it },
        infoSong = infoSong,
        onInfoSongChange = { infoSong = it },
        aiSong = aiSong,
        onAiSongChange = { aiSong = it },
        aiInterpretTitle = aiInterpretTitle,
        onWritePermissionRequired = { error, retry ->
            pendingWriteRetry = retry
            writePermissionLauncher.launch(
                IntentSenderRequest.Builder(error.intentSender).build()
            )
        }
    )

    coverPreviewSong?.let { song ->
        val model = if (song == actionSong) actionCoverModel else null
        if (model != null) {
            CoverPreviewDialog(
                model = model,
                title = listOf(song.title.ifBlank { song.fileName }, song.artist.takeIf(String::isNotBlank))
                    .filterNotNull()
                    .joinToString(" - "),
                saveName = listOf(song.artist.takeIf(String::isNotBlank), song.title.ifBlank { song.fileName })
                    .filterNotNull()
                    .joinToString(" - "),
                onDismiss = { coverPreviewSong = null }
            )
        } else {
            coverPreviewSong = null
        }
    }
}

@Composable
private fun SongMoreCoverPreview(
    song: Song,
    coverModel: Any?,
    onPreview: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (coverModel != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onPreview)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 3000,
                    loadOriginal = true
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                text = song.title.ifBlank { song.fileName },
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onArtist)
            )
            Text(
                text = song.album.ifBlank { stringResource(R.string.player_unknown_album) },
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onAlbum)
            )
        }
    }
}
