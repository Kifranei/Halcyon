package com.ella.music.viewmodel

import android.net.Uri
import com.ella.music.data.PlaylistBatchExportResult
import com.ella.music.data.PlaylistBatchImportResult
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistExportResult
import com.ella.music.data.PlaylistImportMode
import com.ella.music.data.PlaylistImportResult
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.toSong
import com.ella.music.data.repository.isWebDavRemoteSong
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope

internal class MainViewModelPlaylistCoordinator(
    private val playlistStore: PlaylistStore,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
    private val currentSongs: () -> List<Song>,
    private val resolveSongForPlaylist: suspend (Song) -> Song
) {
    /**
     * A playlist-list drag can finish twice before the first disk write reaches the store.
     * Keep the writes ordered so an older drag cannot overwrite the final position.
     */
    private val playlistOrderMutex = Mutex()
    private val playlistMetadataMutex = Mutex()

    fun playlistSongs(playlist: UserPlaylist): List<Song> {
        val libraryByKey = currentSongs().associateBy { it.playlistIdentityKey() }
        return playlist.songs.map { item ->
            val storedSong = item.toSong()
            // A WebDAV playlist record is the durable metadata source after hydration. The
            // library cache can still contain the original filename-only placeholder when the
            // app was opened with a different library source, and must not overwrite it here.
            if (storedSong.isWebDavRemoteSong()) storedSong else libraryByKey[item.key] ?: storedSong
        }
    }

    fun createPlaylist(name: String, onCreated: (UserPlaylist?) -> Unit = {}) {
        scope.launch {
            val playlist = playlistStore.createPlaylist(name)
            if (playlist != null) {
                syncPlaylistCustomOrder(newPlaylistIds = listOf(playlist.id))
            }
            onCreated(playlist)
        }
    }

    fun renamePlaylist(id: String, newName: String, onRenamed: (Boolean) -> Unit = {}) {
        scope.launch {
            onRenamed(playlistStore.renamePlaylist(id, newName))
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            playlistStore.deletePlaylist(id)
            syncPlaylistCustomOrder()
        }
    }

    fun deletePlaylists(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            playlistStore.deletePlaylists(ids)
            syncPlaylistCustomOrder()
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songKey: String) {
        scope.launch { playlistStore.removeSongFromPlaylist(playlistId, songKey) }
    }

    fun removeSongsFromPlaylist(playlistId: String, songKeys: Set<String>) {
        if (songKeys.isEmpty()) return
        scope.launch { playlistStore.removeSongsFromPlaylist(playlistId, songKeys) }
    }

    fun addSongsToPlaylist(playlistId: String, songs: Collection<Song>, appendToEnd: Boolean = false) {
        if (songs.isEmpty()) return
        scope.launch {
            playlistStore.addSongsToPlaylist(playlistId, songs, appendToEnd)
            hydrateWebDavPlaylistSongs(songs)
        }
    }

    private suspend fun hydrateWebDavPlaylistSongs(songs: Collection<Song>) {
        val targets = songs
            .asSequence()
            .filter { it.isWebDavRemoteSong() }
            .distinctBy { it.path.trim().replace('\\', '/').lowercase() }
            .toList()
        if (targets.isEmpty()) return

        playlistMetadataMutex.withLock {
            val nextIndex = AtomicInteger(0)
            val resolvedSongs = mutableListOf<Song>()
            val resolvedMutex = Mutex()
            supervisorScope {
                val workers = List(minOf(2, targets.size)) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val index = nextIndex.getAndIncrement()
                            if (index >= targets.size) return@launch
                            val target = targets[index]
                            try {
                                val resolved = resolveSongForPlaylist(target)
                                if (resolved != target) {
                                    resolvedMutex.withLock { resolvedSongs += resolved }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                // The entry is still playable by URL; a later refresh can retry
                                // metadata without failing the playlist operation itself.
                            }
                        }
                    }
                }
                workers.joinAll()
            }
            if (resolvedSongs.isNotEmpty()) {
                playlistStore.updateSongsMetadata(resolvedSongs)
            }
        }
    }

    fun reorderPlaylistSongs(playlistId: String, orderedKeys: List<String>) {
        if (orderedKeys.isEmpty()) return
        scope.launch { playlistStore.reorderPlaylistSongs(playlistId, orderedKeys) }
    }

    fun reorderPlaylists(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        scope.launch {
            playlistOrderMutex.withLock {
                playlistStore.reorderPlaylists(orderedIds)
                settingsManager.setPlaylistCustomOrder(orderedIds)
            }
        }
    }

    fun importLocalPlaylist(uri: Uri, onResult: (Result<PlaylistImportResult>) -> Unit) {
        scope.launch {
            val beforeIds = playlistStore.playlists.value.mapTo(mutableSetOf()) { it.id }
            val result = runCatching { playlistStore.importLocalPlaylist(uri, currentSongs()) }
            result.getOrNull()?.playlist?.id?.let { id ->
                if (id !in beforeIds) syncPlaylistCustomOrder(newPlaylistIds = listOf(id)) else syncPlaylistCustomOrder()
            }
            onResult(result)
        }
    }

    fun importLocalPlaylists(
        uris: List<Uri>,
        mode: PlaylistImportMode = PlaylistImportMode.MergeKeepExisting,
        onResult: (Result<PlaylistBatchImportResult>) -> Unit
    ) {
        scope.launch {
            val beforeIds = playlistStore.playlists.value.mapTo(mutableSetOf()) { it.id }
            val result = runCatching { playlistStore.importLocalPlaylists(uris, currentSongs(), mode) }
            result.onSuccess {
                val newIds = playlistStore.playlists.value
                    .map { it.id }
                    .filter { it !in beforeIds }
                syncPlaylistCustomOrder(newPlaylistIds = newIds)
            }
            onResult(result)
        }
    }

    fun scanLocalPlaylistFiles(
        onResult: (Result<PlaylistBatchImportResult>) -> Unit = {}
    ) {
        scope.launch {
            val beforeIds = playlistStore.playlists.value.mapTo(mutableSetOf()) { it.id }
            val result = runCatching { playlistStore.importLocalPlaylistFiles(currentSongs()) }
            result.onSuccess {
                val newIds = playlistStore.playlists.value
                    .map { it.id }
                    .filter { it !in beforeIds }
                syncPlaylistCustomOrder(newPlaylistIds = newIds)
            }
            onResult(result)
        }
    }

    fun exportLocalPlaylist(
        playlist: UserPlaylist,
        uri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistExportResult>) -> Unit
    ) {
        scope.launch {
            val result = runCatching { playlistStore.exportLocalPlaylist(playlist, uri, format) }
            onResult(result)
        }
    }

    fun exportLocalPlaylists(
        playlists: List<UserPlaylist>,
        treeUri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistBatchExportResult>) -> Unit
    ) {
        scope.launch {
            val result = runCatching { playlistStore.exportLocalPlaylists(playlists, treeUri, format) }
            onResult(result)
        }
    }

    private suspend fun syncPlaylistCustomOrder(newPlaylistIds: List<String> = emptyList()) {
        playlistOrderMutex.withLock {
            val customPlaylists = playlistStore.playlists.value
                .filterNot { it.isFavorites || it.isFiveStarRating }
            settingsManager.setPlaylistCustomOrder(
                buildPlaylistCustomOrder(
                    customPlaylists = customPlaylists,
                    currentOrder = settingsManager.playlistCustomOrder.first(),
                    newPlaylistIds = newPlaylistIds
                )
            )
        }
    }
}
