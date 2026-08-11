package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ella.music.data.SettingsManager.Companion.LISTENING_HISTORY_SOURCE_COMBINED
import com.ella.music.data.SettingsManager.Companion.LISTENING_HISTORY_SOURCE_LOCAL
import com.ella.music.data.SettingsManager.Companion.PLAY_NEXT_MODE_FORWARD_STACK
import com.ella.music.data.SettingsManager.Companion.PLAY_NEXT_MODE_REVERSE_STACK
import com.ella.music.data.SettingsManager.Companion.SEARCH_ALL_CATEGORY_TYPES
import com.ella.music.data.SettingsManager.Companion.SEARCH_ALL_SONG_MATCH_TYPES
import com.ella.music.data.SettingsManager.Companion.SONG_RATING_DISPLAY_STAR_NUMBER
import com.ella.music.data.SettingsManager.Companion.SONG_RATING_DISPLAY_STARS
import com.ella.music.data.SettingsManager.Companion.DEFAULT_ARTIST_SEPARATORS
import com.ella.music.data.SettingsManager.Companion.DEFAULT_GENRE_SEPARATORS
import com.ella.music.data.SettingsManager.Companion.KEY_ADD_TO_PLAYLIST_APPEND_TO_END
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_PROTECTED_NAMES
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_SEPARATORS
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SCAN
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SCAN_LOCAL_PLAYLISTS
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SHOW_SEARCH_KEYBOARD
import com.ella.music.data.SettingsManager.Companion.KEY_CATEGORY_GRID_COLUMNS
import com.ella.music.data.SettingsManager.Companion.KEY_COVER_EXPORT_FOLDER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST
import com.ella.music.data.SettingsManager.Companion.KEY_FOLDER_PLAYLISTS
import com.ella.music.data.SettingsManager.Companion.KEY_FOLDER_PLAYLIST_CUSTOM_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_FULL_TAG_SEARCH_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_FULL_TAG_SEARCH_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_GENRE_PROTECTED_NAMES
import com.ella.music.data.SettingsManager.Companion.KEY_GENRE_SEPARATORS
import com.ella.music.data.SettingsManager.Companion.KEY_INITIAL_SCAN_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_LISTENING_HISTORY_SOURCE
import com.ella.music.data.SettingsManager.Companion.KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TIMING_EDITOR_ID
import com.ella.music.data.SettingsManager.Companion.KEY_METADATA_EDITOR_ID
import com.ella.music.data.SettingsManager.Companion.KEY_SPECTRUM_VIEWER_ID
import com.ella.music.data.SettingsManager.Companion.KEY_MIN_DURATION
import com.ella.music.data.SettingsManager.Companion.KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLAY_NEXT_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYLIST_CUSTOM_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE
import com.ella.music.data.SettingsManager.Companion.KEY_SCAN_EXCLUDE_FOLDERS
import com.ella.music.data.SettingsManager.Companion.KEY_SCAN_INCLUDE_FOLDERS
import com.ella.music.data.SettingsManager.Companion.KEY_SEARCH_ALL_CATEGORY_TYPES
import com.ella.music.data.SettingsManager.Companion.KEY_SEARCH_ALL_SONG_MATCH_TYPES
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_ALBUM_ARTISTS
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_LOCAL_MV_IN_LISTS
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_ONLINE_MV_IN_LISTS
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_PLAY_NEXT_IN_LISTS
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_REMOVE_FROM_PLAYLIST_BUTTON
import com.ella.music.data.SettingsManager.Companion.KEY_SONG_RATING_DISPLAY_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_TAG_IGNORE_CASE
import com.ella.music.data.SettingsManager.Companion.KEY_USB_FOLDER_URIS
import com.ella.music.data.SettingsManager.Companion.KEY_USE_ANDROID_MEDIA_LIBRARY
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.toFolderPlaylistJson
import com.ella.music.data.model.toFolderPlaylists
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Media library, scanning, search and playlists: scan folders/prompts, tag splitting, search-all
 * scopes, list behaviour, folder playlists, pinning and grid layout.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface LibrarySettingsAccess {
    val autoScan: Flow<Boolean>
    val autoScanLocalPlaylists: Flow<Boolean>
    val minDurationSec: Flow<Int>
    val playlistSpecialEntriesVisible: Flow<Boolean>
    val showPlayNextInLists: Flow<Boolean>
    val showLocalMusicVideoInLists: Flow<Boolean>
    val showOnlineMusicVideoInLists: Flow<Boolean>
    val showRemoveFromPlaylistButton: Flow<Boolean>
    val excludeSearchResultsFromPlaylist: Flow<Boolean>
    val autoShowSearchKeyboard: Flow<Boolean>
    val playNextMode: Flow<Int>
    val showAlbumArtists: Flow<Boolean>
    val metadataEditorId: Flow<String>
    val lyricTimingEditorId: Flow<String>
    val spectrumViewerId: Flow<String>
    val scanIncludeFolders: Flow<String>
    val scanExcludeFolders: Flow<String>
    val usbFolderUris: Flow<String>
    val useAndroidMediaLibrary: Flow<Boolean>
    val fullTagSearchEnabled: Flow<Boolean>
    val fullTagSearchPromptHandled: Flow<Boolean>
    val coverExportFolderUri: Flow<String>
    val searchAllCategoryTypes: Flow<Set<String>>
    val searchAllSongMatchTypes: Flow<Set<String>>
    val songRatingDisplayMode: Flow<Int>
    val listeningHistorySource: Flow<Int>
    val initialScanPromptHandled: Flow<Boolean>
    val localPlaylistScanPromptHandled: Flow<Boolean>
    val notificationPermissionPromptHandled: Flow<Boolean>
    val artistSeparators: Flow<String>
    val artistProtectedNames: Flow<String>
    val genreSeparators: Flow<String>
    val genreProtectedNames: Flow<String>
    val tagIgnoreCase: Flow<Boolean>
    val playlistCustomOrder: Flow<List<String>>
    val folderPlaylistCustomOrder: Flow<List<String>>
    val addToPlaylistAppendToEnd: Flow<Boolean>
    val categoryGridColumns: Flow<Int>
    val folderPlaylists: Flow<List<FolderPlaylist>>
    suspend fun setAutoScan(enabled: Boolean)
    suspend fun setAutoScanLocalPlaylists(enabled: Boolean)
    suspend fun setMinDurationSec(seconds: Int)
    suspend fun setPlaylistSpecialEntriesVisible(visible: Boolean)
    suspend fun setShowPlayNextInLists(enabled: Boolean)
    suspend fun setShowLocalMusicVideoInLists(enabled: Boolean)
    suspend fun setShowOnlineMusicVideoInLists(enabled: Boolean)
    suspend fun setShowRemoveFromPlaylistButton(enabled: Boolean)
    suspend fun setExcludeSearchResultsFromPlaylist(enabled: Boolean)
    suspend fun setAutoShowSearchKeyboard(enabled: Boolean)
    suspend fun setPlayNextMode(mode: Int)
    suspend fun setShowAlbumArtists(enabled: Boolean)
    suspend fun setMetadataEditorId(id: String)
    suspend fun setLyricTimingEditorId(id: String)
    suspend fun setSpectrumViewerId(id: String)
    suspend fun setPlaylistCustomOrder(ids: List<String>)
    suspend fun setFolderPlaylistCustomOrder(ids: List<String>)
    suspend fun setFolderPlaylistSongOrder(playlistId: String, keys: List<String>)
    suspend fun setFolderPlaylistFolderOrder(playlistId: String, paths: List<String>)
    suspend fun setFolderPlaylistHiddenFolders(playlistId: String, paths: List<String>)
    fun pinnedKeysFlow(namespace: String): Flow<List<String>>
    suspend fun setPinned(namespace: String, key: String, pinned: Boolean)
    suspend fun pinKeysInOrder(namespace: String, keys: List<String>)
    suspend fun setAddToPlaylistAppendToEnd(appendToEnd: Boolean)
    suspend fun setCategoryGridColumns(columns: Int)
    suspend fun upsertFolderPlaylist(
        playlistId: String?,
        name: String,
        folders: List<String>
    ): FolderPlaylist?
    suspend fun deleteFolderPlaylist(playlistId: String)
    suspend fun setScanIncludeFolders(folders: String)
    suspend fun setUseAndroidMediaLibrary(enabled: Boolean)
    suspend fun setFullTagSearchEnabled(enabled: Boolean)
    suspend fun setFullTagSearchPromptHandled(handled: Boolean)
    suspend fun setCoverExportFolderUri(uri: String)
    suspend fun setSearchAllCategoryTypeEnabled(type: String, enabled: Boolean)
    suspend fun setSearchAllSongMatchTypeEnabled(type: String, enabled: Boolean)
    suspend fun setSongRatingDisplayMode(mode: Int)
    suspend fun setListeningHistorySource(source: Int)
    suspend fun setInitialScanPromptHandled(handled: Boolean)
    suspend fun setLocalPlaylistScanPromptHandled(handled: Boolean)
    suspend fun setNotificationPermissionPromptHandled(handled: Boolean)
    suspend fun setArtistSeparators(separators: String)
    suspend fun setArtistProtectedNames(names: String)
    suspend fun setGenreSeparators(separators: String)
    suspend fun setGenreProtectedNames(names: String)
    suspend fun setTagIgnoreCase(enabled: Boolean)
    suspend fun setScanExcludeFolders(folders: String)
    suspend fun setUsbFolderUris(uris: String)
    suspend fun addUsbFolderUri(uri: String)
    suspend fun removeUsbFolderUri(uri: String)
}

internal class LibrarySettingsAccessImpl(private val context: Context) : LibrarySettingsAccess {

    override val autoScan: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SCAN] ?: false }
    override val autoScanLocalPlaylists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SCAN_LOCAL_PLAYLISTS] ?: false }

    override val minDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_MIN_DURATION] ?: 15 }

    override val playlistSpecialEntriesVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE] ?: false }
    override val showPlayNextInLists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] ?: false }
    override val showLocalMusicVideoInLists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_LOCAL_MV_IN_LISTS] ?: true }
    override val showOnlineMusicVideoInLists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ONLINE_MV_IN_LISTS] ?: true }
    override val showRemoveFromPlaylistButton: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_REMOVE_FROM_PLAYLIST_BUTTON] ?: true }
    override val excludeSearchResultsFromPlaylist: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST] ?: false }
    override val autoShowSearchKeyboard: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] ?: true }
    override val playNextMode: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_PLAY_NEXT_MODE]?.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
                ?: PLAY_NEXT_MODE_REVERSE_STACK
        }

    override val showAlbumArtists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ALBUM_ARTISTS] ?: true }
    override val metadataEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_METADATA_EDITOR_ID] ?: "" }
    override val lyricTimingEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_TIMING_EDITOR_ID] ?: "" }
    override val spectrumViewerId: Flow<String> =
        context.dataStore.data.map { it[KEY_SPECTRUM_VIEWER_ID] ?: "builtin" }

    override val scanIncludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_INCLUDE_FOLDERS] ?: "" }
    override val scanExcludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_EXCLUDE_FOLDERS] ?: "" }
    override val usbFolderUris: Flow<String> = context.dataStore.data.map { it[KEY_USB_FOLDER_URIS] ?: "" }
    override val useAndroidMediaLibrary: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_USE_ANDROID_MEDIA_LIBRARY] ?: true }
    override val fullTagSearchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FULL_TAG_SEARCH_ENABLED] ?: false }
    override val fullTagSearchPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FULL_TAG_SEARCH_PROMPT_HANDLED] ?: false }
    override val coverExportFolderUri: Flow<String> =
        context.dataStore.data.map { it[KEY_COVER_EXPORT_FOLDER_URI] ?: "" }
    override val searchAllCategoryTypes: Flow<Set<String>> = context.dataStore.data.map {
        parseSearchAllCategoryTypes(it[KEY_SEARCH_ALL_CATEGORY_TYPES])
    }
    override val searchAllSongMatchTypes: Flow<Set<String>> = context.dataStore.data.map {
        parseSearchAllSongMatchTypes(it[KEY_SEARCH_ALL_SONG_MATCH_TYPES])
    }
    override val songRatingDisplayMode: Flow<Int> = context.dataStore.data.map {
        (it[KEY_SONG_RATING_DISPLAY_MODE] ?: SONG_RATING_DISPLAY_STAR_NUMBER)
            .coerceIn(SONG_RATING_DISPLAY_STAR_NUMBER, SONG_RATING_DISPLAY_STARS)
    }
    override val listeningHistorySource: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LISTENING_HISTORY_SOURCE] ?: LISTENING_HISTORY_SOURCE_LOCAL)
            .coerceIn(LISTENING_HISTORY_SOURCE_LOCAL, LISTENING_HISTORY_SOURCE_COMBINED)
    }
    override val initialScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] ?: false }
    override val localPlaylistScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED] ?: false }
    override val notificationPermissionPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED] ?: false }
    override val artistSeparators: Flow<String> = context.dataStore.data.map {
        it[KEY_ARTIST_SEPARATORS] ?: DEFAULT_ARTIST_SEPARATORS
    }
    override val artistProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_PROTECTED_NAMES] ?: "" }
    override val genreSeparators: Flow<String> = context.dataStore.data.map {
        it[KEY_GENRE_SEPARATORS] ?: DEFAULT_GENRE_SEPARATORS
    }
    override val genreProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_GENRE_PROTECTED_NAMES] ?: "" }
    override val tagIgnoreCase: Flow<Boolean> = context.dataStore.data.map { it[KEY_TAG_IGNORE_CASE] ?: false }

    override val playlistCustomOrder: Flow<List<String>> = context.dataStore.data.map {
        it[KEY_PLAYLIST_CUSTOM_ORDER]
            .orEmpty()
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    override val folderPlaylistCustomOrder: Flow<List<String>> = context.dataStore.data.map {
        it[KEY_FOLDER_PLAYLIST_CUSTOM_ORDER]
            .orEmpty()
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    override val addToPlaylistAppendToEnd: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ADD_TO_PLAYLIST_APPEND_TO_END] ?: false }
    override val categoryGridColumns: Flow<Int> = context.dataStore.data.map {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        if (tablet) {
            (it[KEY_CATEGORY_GRID_COLUMNS] ?: 5).coerceIn(5, 8)
        } else {
            (it[KEY_CATEGORY_GRID_COLUMNS] ?: 2).coerceIn(1, 4)
        }
    }

    override val folderPlaylists: Flow<List<FolderPlaylist>> =
        context.dataStore.data.map { it[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists() }

    override suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN] = enabled }
    }

    override suspend fun setAutoScanLocalPlaylists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN_LOCAL_PLAYLISTS] = enabled }
    }

    override suspend fun setMinDurationSec(seconds: Int) {
        context.dataStore.edit { it[KEY_MIN_DURATION] = seconds }
    }

    override suspend fun setPlaylistSpecialEntriesVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE] = visible }
    }

    override suspend fun setShowPlayNextInLists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] = enabled }
    }

    override suspend fun setShowLocalMusicVideoInLists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LOCAL_MV_IN_LISTS] = enabled }
    }

    override suspend fun setShowOnlineMusicVideoInLists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ONLINE_MV_IN_LISTS] = enabled }
    }

    override suspend fun setShowRemoveFromPlaylistButton(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_REMOVE_FROM_PLAYLIST_BUTTON] = enabled }
    }

    override suspend fun setExcludeSearchResultsFromPlaylist(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST] = enabled }
    }

    override suspend fun setAutoShowSearchKeyboard(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] = enabled }
    }

    override suspend fun setPlayNextMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_PLAY_NEXT_MODE] = mode.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
        }
    }

    override suspend fun setShowAlbumArtists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ALBUM_ARTISTS] = enabled }
    }

    override suspend fun setMetadataEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_METADATA_EDITOR_ID) else it[KEY_METADATA_EDITOR_ID] = safeId
        }
    }

    override suspend fun setLyricTimingEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_LYRIC_TIMING_EDITOR_ID) else it[KEY_LYRIC_TIMING_EDITOR_ID] = safeId
        }
    }

    override suspend fun setSpectrumViewerId(id: String) {
        context.dataStore.edit {
            it[KEY_SPECTRUM_VIEWER_ID] = id.trim().ifBlank { "builtin" }
        }
    }

    override suspend fun setPlaylistCustomOrder(ids: List<String>) {
        context.dataStore.edit {
            it[KEY_PLAYLIST_CUSTOM_ORDER] = ids
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
        }
    }

    override suspend fun setFolderPlaylistCustomOrder(ids: List<String>) {
        context.dataStore.edit {
            it[KEY_FOLDER_PLAYLIST_CUSTOM_ORDER] = ids
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
        }
    }

    override suspend fun setFolderPlaylistSongOrder(playlistId: String, keys: List<String>) {
        updateFolderPlaylistOrder(playlistId = playlistId, songOrder = keys, folderOrder = null)
    }

    override suspend fun setFolderPlaylistFolderOrder(playlistId: String, paths: List<String>) {
        updateFolderPlaylistOrder(playlistId = playlistId, songOrder = null, folderOrder = paths)
    }

    override suspend fun setFolderPlaylistHiddenFolders(playlistId: String, paths: List<String>) {
        val safeId = playlistId.trim()
        if (safeId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists()
            if (current.none { it.id == safeId }) return@edit
            val next = current.map { playlist ->
                if (playlist.id != safeId) playlist else playlist.copy(
                    hiddenFolders = paths
                        .map { it.replace('\\', '/').trim().trimEnd('/') }
                        .filter(String::isNotBlank)
                        .distinctBy { it.lowercase(Locale.ROOT) },
                    updatedAt = System.currentTimeMillis()
                )
            }
            prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
    }

    private suspend fun updateFolderPlaylistOrder(
        playlistId: String,
        songOrder: List<String>?,
        folderOrder: List<String>?
    ) {
        val safeId = playlistId.trim()
        if (safeId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists()
            val target = current.firstOrNull { it.id == safeId } ?: return@edit
            val next = current.map { playlist ->
                if (playlist.id != safeId) return@map playlist
                playlist.copy(
                    songOrder = songOrder?.map(String::trim)?.filter(String::isNotBlank)?.distinct()
                        ?: playlist.songOrder,
                    folderOrder = folderOrder?.map { it.replace('\\', '/').trim().trimEnd('/') }
                        ?.filter(String::isNotBlank)
                        ?.distinctBy { it.lowercase(Locale.ROOT) }
                        ?: playlist.folderOrder,
                    updatedAt = System.currentTimeMillis()
                )
            }
            prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
    }

    // Generic "pin to top" store, keyed by an arbitrary namespace (e.g. "artist",
    // "album", "category:genre"). The ordered list keeps the most-recently pinned first.
    override fun pinnedKeysFlow(namespace: String): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey("pinned_$namespace")]
                ?.split("\n")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?: emptyList()
        }

    override suspend fun setPinned(namespace: String, key: String, pinned: Boolean) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val prefKey = stringPreferencesKey("pinned_$namespace")
            val current = prefs[prefKey]
                ?.split("\n")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toMutableList()
                ?: mutableListOf()
            current.remove(trimmed)
            if (pinned) current.add(0, trimmed)
            prefs[prefKey] = current.joinToString("\n")
        }
    }

    override suspend fun pinKeysInOrder(namespace: String, keys: List<String>) {
        val selectedKeys = keys.map(String::trim).filter(String::isNotBlank).distinct()
        if (selectedKeys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val prefKey = stringPreferencesKey("pinned_$namespace")
            val existing = prefs[prefKey]
                ?.split("\n")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                .orEmpty()
            // Keep the caller's tap order at the top. Existing pins retain their relative order
            // after the newly selected group, so a batch action never reverses the user's order.
            prefs[prefKey] = (selectedKeys + existing.filterNot { it in selectedKeys })
                .joinToString("\n")
        }
    }

    override suspend fun setAddToPlaylistAppendToEnd(appendToEnd: Boolean) {
        context.dataStore.edit { it[KEY_ADD_TO_PLAYLIST_APPEND_TO_END] = appendToEnd }
    }

    override suspend fun setCategoryGridColumns(columns: Int) {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        context.dataStore.edit { it[KEY_CATEGORY_GRID_COLUMNS] = columns.coerceIn(if (tablet) 5 else 1, if (tablet) 8 else 4) }
    }

    override suspend fun upsertFolderPlaylist(
        playlistId: String?,
        name: String,
        folders: List<String>
    ): FolderPlaylist? {
        val safeName = name.trim()
        val safeFolders = folders
            .map { it.replace('\\', '/').trim().trimEnd('/').ifBlank { "/" } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (safeName.isBlank() || safeFolders.isEmpty()) return null
        var saved: FolderPlaylist? = null
        context.dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val current = prefs[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists()
            val existing = playlistId?.let { id -> current.firstOrNull { it.id == id } }
            if (current.any { it.id != existing?.id && it.name.trim().equals(safeName, ignoreCase = true) }) {
                return@edit
            }
            val nextItem = FolderPlaylist(
                id = existing?.id ?: "folder-playlist-$now",
                name = safeName,
                folders = safeFolders,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                songOrder = existing?.songOrder.orEmpty(),
                folderOrder = existing?.folderOrder.orEmpty()
                    .filter { path -> safeFolders.any { it.equals(path, ignoreCase = true) } },
                hiddenFolders = existing?.hiddenFolders.orEmpty()
                    .filter { path -> safeFolders.any { it.equals(path, ignoreCase = true) } }
            )
            saved = nextItem
            val next = if (existing == null) {
                current + nextItem
            } else {
                current.map { if (it.id == existing.id) nextItem else it }
            }
            prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
        return saved
    }

    override suspend fun deleteFolderPlaylist(playlistId: String) {
        val safeId = playlistId.trim()
        if (safeId.isBlank()) return
        context.dataStore.edit { prefs ->
            val next = prefs[KEY_FOLDER_PLAYLISTS].orEmpty()
                .toFolderPlaylists()
                .filterNot { it.id == safeId }
            if (next.isEmpty()) prefs.remove(KEY_FOLDER_PLAYLISTS) else prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
    }

    override suspend fun setScanIncludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_INCLUDE_FOLDERS] = folders.trim() }
    }

    override suspend fun setUseAndroidMediaLibrary(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_ANDROID_MEDIA_LIBRARY] = enabled }
    }

    override suspend fun setFullTagSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FULL_TAG_SEARCH_ENABLED] = enabled }
    }

    override suspend fun setFullTagSearchPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_FULL_TAG_SEARCH_PROMPT_HANDLED] = handled }
    }

    override suspend fun setCoverExportFolderUri(uri: String) {
        context.dataStore.edit { it[KEY_COVER_EXPORT_FOLDER_URI] = uri.trim() }
    }

    override suspend fun setSearchAllCategoryTypeEnabled(type: String, enabled: Boolean) {
        val normalized = type.trim().lowercase()
        if (normalized !in SEARCH_ALL_CATEGORY_TYPES) return
        context.dataStore.edit { prefs ->
            val current = parseSearchAllCategoryTypes(prefs[KEY_SEARCH_ALL_CATEGORY_TYPES])
            val next = if (enabled) current + normalized else current - normalized
            prefs[KEY_SEARCH_ALL_CATEGORY_TYPES] = next.sorted().joinToString(",")
        }
    }

    override suspend fun setSearchAllSongMatchTypeEnabled(type: String, enabled: Boolean) {
        val normalized = type.trim().lowercase()
        if (normalized !in SEARCH_ALL_SONG_MATCH_TYPES) return
        context.dataStore.edit { prefs ->
            val current = parseSearchAllSongMatchTypes(prefs[KEY_SEARCH_ALL_SONG_MATCH_TYPES])
            val next = if (enabled) current + normalized else current - normalized
            prefs[KEY_SEARCH_ALL_SONG_MATCH_TYPES] = next.sorted().joinToString(",")
        }
    }

    override suspend fun setSongRatingDisplayMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_SONG_RATING_DISPLAY_MODE] = mode.coerceIn(
                SONG_RATING_DISPLAY_STAR_NUMBER,
                SONG_RATING_DISPLAY_STARS
            )
        }
    }

    override suspend fun setListeningHistorySource(source: Int) {
        context.dataStore.edit {
            it[KEY_LISTENING_HISTORY_SOURCE] = source.coerceIn(
                LISTENING_HISTORY_SOURCE_LOCAL,
                LISTENING_HISTORY_SOURCE_COMBINED
            )
        }
    }

    override suspend fun setInitialScanPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] = handled }
    }

    override suspend fun setLocalPlaylistScanPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED] = handled }
    }

    override suspend fun setNotificationPermissionPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED] = handled }
    }

    override suspend fun setArtistSeparators(separators: String) {
        context.dataStore.edit { it[KEY_ARTIST_SEPARATORS] = separators.trim() }
    }

    override suspend fun setArtistProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_ARTIST_PROTECTED_NAMES] = names.trim() }
    }

    override suspend fun setGenreSeparators(separators: String) {
        context.dataStore.edit { it[KEY_GENRE_SEPARATORS] = separators.trim() }
    }

    override suspend fun setGenreProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_GENRE_PROTECTED_NAMES] = names.trim() }
    }

    override suspend fun setTagIgnoreCase(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TAG_IGNORE_CASE] = enabled }
    }

    override suspend fun setScanExcludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_EXCLUDE_FOLDERS] = folders.trim() }
    }

    override suspend fun setUsbFolderUris(uris: String) {
        context.dataStore.edit { it[KEY_USB_FOLDER_URIS] = uris.trim() }
    }

    override suspend fun addUsbFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USB_FOLDER_URIS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = (existing + uri.trim()).distinct().joinToString("\n")
            prefs[KEY_USB_FOLDER_URIS] = updated
        }
    }

    override suspend fun removeUsbFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USB_FOLDER_URIS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() && it != uri.trim() }
            if (existing.isEmpty()) {
                prefs.remove(KEY_USB_FOLDER_URIS)
            } else {
                prefs[KEY_USB_FOLDER_URIS] = existing.joinToString("\n")
            }
        }
    }

    private fun parseSearchAllCategoryTypes(raw: String?): Set<String> {
        val saved = raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SEARCH_ALL_CATEGORY_TYPES }
            .toSet()
        return if (raw == null) SEARCH_ALL_CATEGORY_TYPES else saved
    }

    private fun parseSearchAllSongMatchTypes(raw: String?): Set<String> {
        val saved = raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SEARCH_ALL_SONG_MATCH_TYPES }
            .toSet()
        return if (raw == null) SEARCH_ALL_SONG_MATCH_TYPES else saved
    }
}
