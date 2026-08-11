package com.ella.music.ui.search

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.selectMetadataCategoryCoverSong
import com.ella.music.ui.artist.rememberArtistCoverModel
import com.ella.music.ui.artist.selectArtistCoverSong
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel

@Composable
internal fun LibrarySearchResultsPane(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    songs: List<Song>,
    libraryCacheLoaded: Boolean,
    currentSong: Song?,
    showPlayNextInLists: Boolean,
    songRatingDisplayMode: Int,
    excludeSearchResultsFromPlaylist: Boolean,
    filter: SearchFilter,
    trimmedQuery: String,
    duplicatesOnlyActive: Boolean,
    hasActiveContentFilter: Boolean,
    history: List<String>,
    selectionMode: Boolean,
    selectedSongKeys: Set<String>,
    songSelectionAvailable: Boolean,
    songResults: List<SongSearchResult>,
    songResultGroups: List<Pair<Int, List<SongSearchGroupEntry>>>,
    albumResults: List<Album>,
    artistResults: List<ArtistSearchResult>,
    playlistResults: List<UserPlaylist>,
    categoryResultsByType: Map<String, List<MetadataCategoryItem>>,
    artistCoverFolderUri: String,
    visibleResultCount: Int,
    onSelectHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistoryRequest: () -> Unit,
    onToggleSongSelection: (Song) -> Unit,
    onEnterSongSelection: (Song) -> Unit,
    onSongAction: (Song) -> Unit,
    onActionTarget: (SearchActionTarget) -> Unit,
    onCommitSearch: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val collapsedSongSections = remember(trimmedQuery, filter) { mutableStateMapOf<Int, Boolean>() }
    val selectedSongsForDrag = remember(songResults, selectedSongKeys) {
        songResults
            .map { it.song }
            .filter { it.searchIdentityKey() in selectedSongKeys }
            .distinctBy { it.searchIdentityKey() }
    }
    if (songs.isEmpty() && !libraryCacheLoaded) {
        com.ella.music.ui.components.EllaCenteredLoadingIndicator()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 128.dp)
    ) {
        if (trimmedQuery.isBlank() && !duplicatesOnlyActive && !hasActiveContentFilter) {
            if (history.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        text = stringResource(R.string.library_search_history),
                        actionText = stringResource(R.string.library_search_clear_history),
                        onActionClick = onClearHistoryRequest
                    )
                }
                items(history, key = { it }) { item ->
                    HistoryRow(
                        text = item,
                        onClick = { onSelectHistory(item) },
                        onDelete = { onDeleteHistory(item) }
                    )
                }
            } else {
                item { EmptySearchHint(stringResource(R.string.library_search_empty_hint)) }
            }
            return@LazyColumn
        }

        if (duplicatesOnlyActive) {
            item { SearchSectionHeader(stringResource(R.string.library_search_duplicates)) }
        }
        if (songResults.isNotEmpty()) {
            songResultGroups.forEach { (labelRes, entries) ->
                item {
                    val collapsed = collapsedSongSections[labelRes] == true
                    SearchSectionHeader(
                        text = stringResource(labelRes) + " (${entries.size})",
                        collapsed = collapsed,
                        onHeaderClick = {
                            collapsedSongSections[labelRes] = !collapsed
                        }
                    )
                }
                if (collapsedSongSections[labelRes] != true) {
                    items(entries, key = { entry ->
                        val result = entry.result
                        "${result.song.id}:${result.song.path}:${result.lyricSnippet.orEmpty()}:$labelRes:${entry.keySuffix}"
                    }) { entry ->
                        val result = entry.result
                        val selected = result.song.searchIdentityKey() in selectedSongKeys
                        Column {
                            SongItem(
                                song = result.song,
                                isCurrent = currentSong?.id == result.song.id,
                                loadCoverArt = { song -> mainViewModel.getCoverArtBitmap(song) },
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                loadSongTagInfo = mainViewModel::getSongTagInfo,
                                showPlayNextInLists = showPlayNextInLists,
                                ratingDisplayMode = songRatingDisplayMode,
                                selectionMode = selectionMode,
                                selected = selected,
                                dragSelectedSongs = selectedSongsForDrag,
                                onPlayNext = {
                                    playerViewModel.playNext(result.song)
                                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                },
                                onClick = {
                                    if (selectionMode) {
                                        onToggleSongSelection(result.song)
                                    } else {
                                        val playbackSongs = if (excludeSearchResultsFromPlaylist) {
                                            listOf(result.song)
                                        } else {
                                            songResults.map { it.song }
                                        }
                                        val index = if (excludeSearchResultsFromPlaylist) {
                                            0
                                        } else {
                                            playbackSongs.indexOfFirst {
                                                it.id == result.song.id && it.path == result.song.path
                                            }.coerceAtLeast(0)
                                        }
                                        playerViewModel.setPlaylist(playbackSongs, index)
                                        onCommitSearch()
                                        onNavigateToPlayer()
                                    }
                                },
                                onLongClick = {
                                    if (songSelectionAvailable) {
                                        onEnterSongSelection(result.song)
                                    } else {
                                        onSongAction(result.song)
                                    }
                                },
                                onMore = { onSongAction(result.song) }
                            )
                            result.lyricSnippet?.let { snippet ->
                                LyricSearchMatchLine(snippet = snippet, query = trimmedQuery)
                            } ?: entry.match?.let { match ->
                                SongSearchMatchLine(match = match, query = trimmedQuery)
                            }
                        }
                    }
                }
            }
        }
        if (artistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Artists)) {
            item { SearchSectionHeader(stringResource(R.string.library_search_artists) + " (${artistResults.size})") }
            items(artistResults, key = { it.artist.name }) { result ->
                val coverModel = rememberArtistCoverModel(
                    artistName = result.artist.name,
                    representativeSong = remember(songs, result.artist.name) {
                        selectArtistCoverSong(songs, result.artist.name)
                    },
                    folderLocation = artistCoverFolderUri,
                    mainViewModel = mainViewModel
                )
                ArtistResultRow(
                    result = result,
                    coverModel = coverModel,
                    query = trimmedQuery,
                    onClick = {
                        onCommitSearch()
                        onNavigateToArtist(result.artist.name)
                    },
                    onLongClick = { onActionTarget(SearchActionTarget.ArtistTarget(result.artist)) }
                )
            }
        }
        if (albumResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Albums)) {
            item { SearchSectionHeader(stringResource(R.string.library_search_albums) + " (${albumResults.size})") }
            items(albumResults, key = { it.id }) { album ->
                AlbumResultRow(
                    album = album,
                    coverModel = mainViewModel.getAlbumArtUri(album.artAlbumId),
                    query = trimmedQuery,
                    onClick = {
                        onCommitSearch()
                        onNavigateToAlbum(album.id)
                    },
                    onLongClick = { onActionTarget(SearchActionTarget.AlbumTarget(album)) }
                )
            }
        }
        if (playlistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Playlists)) {
            item { SearchSectionHeader(stringResource(R.string.library_search_playlists) + " (${playlistResults.size})") }
            items(playlistResults, key = { it.id }) { playlist ->
                val playlistSongs = remember(playlist, songs) { mainViewModel.playlistSongs(playlist) }
                val coverSong = playlistSongs.firstOrNull()
                PlaylistResultRow(
                    playlist = playlist,
                    coverModel = coverSong?.coverUrl?.takeIf { it.isNotBlank() }
                        ?: coverSong?.albumId?.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri),
                    query = trimmedQuery,
                    onClick = {
                        onCommitSearch()
                        onNavigateToPlaylist(playlist.id)
                    },
                    onLongClick = { onActionTarget(SearchActionTarget.PlaylistTarget(playlist)) }
                )
            }
        }
        categoryResultsByType.forEach { (categoryType, results) ->
            if (results.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(categoryType.searchLabelRes()) + " (${results.size})") }
                items(results, key = { "$categoryType:${it.name}" }) { item ->
                    MetadataCategoryResultRow(
                        item = item,
                        displayName = if (categoryType == "folder") item.name.substringAfterLast('/').ifBlank { item.name } else item.name,
                        coverModel = selectMetadataCategoryCoverSong(songs, categoryType, item.name)
                            ?.let { song ->
                                song.coverUrl.takeIf { it.isNotBlank() }
                                    ?: song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                            }
                            ?: item.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                            ?: item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri),
                        roundCover = categoryType in listOf("composer", "arranger", "lyricist"),
                        query = trimmedQuery,
                        onClick = {
                            onCommitSearch()
                            onNavigateToMetadataCategory(categoryType, item.name)
                        },
                        onLongClick = { onActionTarget(SearchActionTarget.CategoryTarget(categoryType, item)) }
                    )
                }
            }
        }
        if (songResults.isEmpty() && visibleResultCount == 0) {
            item {
                EmptySearchHint(
                    if (duplicatesOnlyActive) stringResource(R.string.library_search_no_duplicates)
                    else stringResource(R.string.library_search_no_results)
                )
            }
        }
    }
}
