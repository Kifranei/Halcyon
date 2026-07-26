package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_ALBUM_DETAIL_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_ALBUM_LIST
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_ARTIST_DETAIL_ALBUM
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_ARTIST_DETAIL_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_ARTIST_LIST
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_FOLDER_DETAIL_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_FOLDER_LIST
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_FOLDER_PLAYLIST_LIST
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_LIBRARY_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_PLAYLIST_DETAIL_SONG
import com.ella.music.data.SettingsManager.Companion.KEY_SORT_PLAYLIST_LIST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * List sort indices. Keeps the in-memory immediate-update SharedFlow so a sort change re-sorts
 * every list in the same frame, before the DataStore disk flow catches up.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface SortSettingsAccess {
    val librarySongSortIndex: Flow<Int>
    val albumListSortIndex: Flow<Int>
    val artistListSortIndex: Flow<Int>
    val albumDetailSongSortIndex: Flow<Int>
    val artistDetailSongSortIndex: Flow<Int>
    val artistDetailAlbumSortIndex: Flow<Int>
    val folderListSortIndex: Flow<Int>
    val folderDetailSongSortIndex: Flow<Int>
    val folderPlaylistListSortIndex: Flow<Int>
    val folderPlaylistDetailSongSortIndex: Flow<Int>
    val folderPlaylistDetailFolderSortIndex: Flow<Int>
    val playlistListSortIndex: Flow<Int>
    val playlistDetailSongSortIndex: Flow<Int>
    fun metadataCategorySortIndex(type: String): Flow<Int>
    fun metadataCategoryDetailSongSortIndex(type: String): Flow<Int>
    fun metadataCategoryDetailAlbumSortIndex(type: String): Flow<Int>
    suspend fun setLibrarySongSortIndex(index: Int)
    suspend fun setAlbumListSortIndex(index: Int)
    suspend fun setArtistListSortIndex(index: Int)
    suspend fun setAlbumDetailSongSortIndex(index: Int)
    suspend fun setArtistDetailSongSortIndex(index: Int)
    suspend fun setArtistDetailAlbumSortIndex(index: Int)
    suspend fun setFolderListSortIndex(index: Int)
    suspend fun setFolderDetailSongSortIndex(index: Int)
    suspend fun setFolderPlaylistListSortIndex(index: Int)
    suspend fun setFolderPlaylistDetailSongSortIndex(index: Int)
    suspend fun setFolderPlaylistDetailFolderSortIndex(index: Int)
    suspend fun setPlaylistListSortIndex(index: Int)
    suspend fun setPlaylistDetailSongSortIndex(index: Int)
    suspend fun setMetadataCategorySortIndex(type: String, index: Int)
    suspend fun setMetadataCategoryDetailSongSortIndex(type: String, index: Int)
    suspend fun setMetadataCategoryDetailAlbumSortIndex(type: String, index: Int)
}

internal class SortSettingsAccessImpl(private val context: Context) : SortSettingsAccess {

    private data class SortIndexUpdate(
        val key: Preferences.Key<Int>,
        val value: Int
    )

    // DataStore correctly persists sort changes, but its disk-backed flow updates after the click
    // coroutine yields. Emit an in-memory value first so every list re-sorts in the same frame.
    private val immediateSortIndexUpdates = MutableSharedFlow<SortIndexUpdate>(extraBufferCapacity = 32)

    private fun sortIndexFlow(key: Preferences.Key<Int>, defaultValue: Int): Flow<Int> = merge(
        context.dataStore.data.map { it[key] ?: defaultValue },
        immediateSortIndexUpdates
            .filter { it.key == key }
            .map { it.value }
    ).distinctUntilChanged()

    private suspend fun setSortIndex(key: Preferences.Key<Int>, index: Int) {
        val safeIndex = index.coerceAtLeast(0)
        immediateSortIndexUpdates.tryEmit(SortIndexUpdate(key, safeIndex))
        context.dataStore.edit { it[key] = safeIndex }
    }

    private fun metadataCategorySortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailSongSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_song_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailAlbumSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_album_${type.safePreferenceSuffix()}")

    private fun String.safePreferenceSuffix(): String =
        lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "unknown" }

    override val librarySongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_LIBRARY_SONG, 0)
    override val albumListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ALBUM_LIST, 0)
    override val artistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_LIST, 0)
    override val albumDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ALBUM_DETAIL_SONG, 0)
    override val artistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_DETAIL_SONG, 0)
    override val artistDetailAlbumSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_DETAIL_ALBUM, 0)
    override val folderListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_LIST, 0)
    override val folderDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_DETAIL_SONG, 0)
    override val folderPlaylistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_LIST, 2)
    override val folderPlaylistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG, 0)
    override val folderPlaylistDetailFolderSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER, 0)
    override val playlistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_PLAYLIST_LIST, 2)

    override val playlistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_PLAYLIST_DETAIL_SONG, 2)

    override fun metadataCategorySortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategorySortKey(type), 0)

    override fun metadataCategoryDetailSongSortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategoryDetailSongSortKey(type), 0)

    override fun metadataCategoryDetailAlbumSortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategoryDetailAlbumSortKey(type), 0)

    override suspend fun setLibrarySongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_LIBRARY_SONG, index)
    }

    override suspend fun setAlbumListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ALBUM_LIST, index)
    }

    override suspend fun setArtistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_LIST, index)
    }

    override suspend fun setAlbumDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ALBUM_DETAIL_SONG, index)
    }

    override suspend fun setArtistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_DETAIL_SONG, index)
    }

    override suspend fun setArtistDetailAlbumSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_DETAIL_ALBUM, index)
    }

    override suspend fun setFolderListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_LIST, index)
    }

    override suspend fun setFolderDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_DETAIL_SONG, index)
    }

    override suspend fun setFolderPlaylistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_LIST, index)
    }

    override suspend fun setFolderPlaylistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG, index)
    }

    override suspend fun setFolderPlaylistDetailFolderSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER, index)
    }

    override suspend fun setPlaylistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_PLAYLIST_LIST, index)
    }

    override suspend fun setPlaylistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_PLAYLIST_DETAIL_SONG, index)
    }

    override suspend fun setMetadataCategorySortIndex(type: String, index: Int) {
        setSortIndex(metadataCategorySortKey(type), index)
    }

    override suspend fun setMetadataCategoryDetailSongSortIndex(type: String, index: Int) {
        setSortIndex(metadataCategoryDetailSongSortKey(type), index)
    }

    override suspend fun setMetadataCategoryDetailAlbumSortIndex(type: String, index: Int) {
        setSortIndex(metadataCategoryDetailAlbumSortKey(type), index)
    }
}
