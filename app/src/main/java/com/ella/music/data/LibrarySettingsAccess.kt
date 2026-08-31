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
import com.ella.music.data.SettingsManager.Companion.KEY_PARSE_FEATURED_ARTISTS
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SCAN
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SCAN_LOCAL_PLAYLISTS
import com.ella.music.data.SettingsManager.Companion.KEY_AUTO_SHOW_SEARCH_KEYBOARD
import com.ella.music.data.SettingsManager.Companion.KEY_SEARCH_REOPEN_BEHAVIOR
import com.ella.music.data.SettingsManager.Companion.DEFAULT_SEARCH_REOPEN_BEHAVIOR
import com.ella.music.data.SettingsManager.Companion.KEY_CATEGORY_GRID_COLUMNS
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SONG_GRID
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SONG_GRID_COLUMNS_PHONE
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SONG_GRID_COLUMNS_TABLET
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SONG_TITLE_MARQUEE
import com.ella.music.data.SettingsManager.Companion.KEY_LIBRARY_SONG_LAYOUT
import com.ella.music.data.SettingsManager.Companion.KEY_COVER_EXPORT_FOLDER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST
import com.ella.music.data.SettingsManager.Companion.KEY_SEARCH_CLICK_PLAYBACK_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYLIST_SHOW_RATING_FILTER
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYLIST_SHOW_FAVORITE_FILTER
import com.ella.music.data.SettingsManager.Companion.KEY_FOLDER_PLAYLISTS
import com.ella.music.data.SettingsManager.Companion.KEY_FOLDER_PLAYLIST_CUSTOM_ORDER
import com.ella.music.data.SettingsManager.Companion.KEY_FULL_TAG_SEARCH_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_FULL_TAG_SEARCH_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_FILTER_VIDEO_FILES
import com.ella.music.data.SettingsManager.Companion.KEY_GENRE_PROTECTED_NAMES
import com.ella.music.data.SettingsManager.Companion.KEY_GENRE_SEPARATORS
import com.ella.music.data.SettingsManager.Companion.KEY_INITIAL_SCAN_PROMPT_HANDLED
import com.ella.music.data.SettingsManager.Companion.KEY_SETUP_WIZARD_COMPLETED
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
import com.ella.music.data.SettingsManager.Companion.KEY_SHOW_ARTIST_INTRODUCTION
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_BIO_DOWNLOAD
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_BIO_LASTFM_LANG
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_BIO_SOURCE
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_IMAGE_DOWNLOAD
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_IMAGE_SOURCES
import com.ella.music.data.SettingsManager.Companion.KEY_ARTIST_IMAGE_REGION
import com.ella.music.data.SettingsManager.Companion.KEY_SPOTIFY_CLIENT_ID
import com.ella.music.data.SettingsManager.Companion.KEY_SPOTIFY_CLIENT_SECRET
import com.ella.music.data.SettingsManager.Companion.DEFAULT_ARTIST_BIO_DOWNLOAD
import com.ella.music.data.SettingsManager.Companion.DEFAULT_ARTIST_IMAGE_DOWNLOAD
import com.ella.music.data.SettingsManager.Companion.DEFAULT_ARTIST_IMAGE_SOURCES
import com.ella.music.data.lastfm.DEFAULT_LAST_FM_WIKI_REGION
import com.ella.music.data.lastfm.normalizeLastFmWikiRegion
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
    val filterVideoFiles: Flow<Boolean>
    val playlistSpecialEntriesVisible: Flow<Boolean>
    val showPlayNextInLists: Flow<Boolean>
    val showLocalMusicVideoInLists: Flow<Boolean>
    val showOnlineMusicVideoInLists: Flow<Boolean>
    val showRemoveFromPlaylistButton: Flow<Boolean>
    val excludeSearchResultsFromPlaylist: Flow<Boolean>
    val searchClickPlaybackMode: Flow<Int>
    val playlistShowRatingFilter: Flow<Boolean>
    val playlistShowFavoriteFilter: Flow<Boolean>
    val autoShowSearchKeyboard: Flow<Boolean>
    val searchReopenBehavior: Flow<Int>
    val playNextMode: Flow<Int>
    val showAlbumArtists: Flow<Boolean>
    val showArtistIntroduction: Flow<Boolean>
    val artistBioDownload: Flow<Int>
    val artistBioLastFmLang: Flow<String>
    val artistBioSource: Flow<String>
    val artistImageDownload: Flow<Int>
    val artistImageSourceOrder: Flow<List<String>>
    val artistImageRegion: Flow<String>
    val spotifyClientId: Flow<String>
    val spotifyClientSecret: Flow<String>
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
    val setupWizardCompleted: Flow<Boolean>
    val localPlaylistScanPromptHandled: Flow<Boolean>
    val notificationPermissionPromptHandled: Flow<Boolean>
    val artistSeparators: Flow<String>
    val artistProtectedNames: Flow<String>
    val parseFeaturedArtists: Flow<Boolean>
    val genreSeparators: Flow<String>
    val genreProtectedNames: Flow<String>
    val tagIgnoreCase: Flow<Boolean>
    val playlistCustomOrder: Flow<List<String>>
    val folderPlaylistCustomOrder: Flow<List<String>>
    val addToPlaylistAppendToEnd: Flow<Boolean>
    val categoryGridColumns: Flow<Int>
    val librarySongGridColumnsPhone: Flow<Int>
    val librarySongGridColumnsTablet: Flow<Int>
    val librarySongGrid: Flow<Boolean>
    val librarySongLayout: Flow<Int>
    val librarySongTitleMarquee: Flow<Boolean>
    val folderPlaylists: Flow<List<FolderPlaylist>>
    suspend fun setAutoScan(enabled: Boolean)
    suspend fun setAutoScanLocalPlaylists(enabled: Boolean)
    suspend fun setMinDurationSec(seconds: Int)
    suspend fun setFilterVideoFiles(enabled: Boolean)
    suspend fun setPlaylistSpecialEntriesVisible(visible: Boolean)
    suspend fun setShowPlayNextInLists(enabled: Boolean)
    suspend fun setShowLocalMusicVideoInLists(enabled: Boolean)
    suspend fun setShowOnlineMusicVideoInLists(enabled: Boolean)
    suspend fun setShowRemoveFromPlaylistButton(enabled: Boolean)
    suspend fun setExcludeSearchResultsFromPlaylist(enabled: Boolean)
    suspend fun setSearchClickPlaybackMode(mode: Int)
    suspend fun setPlaylistShowRatingFilter(enabled: Boolean)
    suspend fun setPlaylistShowFavoriteFilter(enabled: Boolean)
    suspend fun setAutoShowSearchKeyboard(enabled: Boolean)
    suspend fun setSearchReopenBehavior(behavior: Int)
    suspend fun setPlayNextMode(mode: Int)
    suspend fun setShowAlbumArtists(enabled: Boolean)
    suspend fun setShowArtistIntroduction(enabled: Boolean)
    suspend fun setArtistBioDownload(mode: Int)
    suspend fun setArtistBioLastFmLang(lang: String)
    suspend fun setArtistBioSource(source: String)
    suspend fun setArtistImageDownload(mode: Int)
    suspend fun setArtistImageSourceOrder(sources: List<String>)
    suspend fun setArtistImageRegion(region: String)
    suspend fun setSpotifyClientId(value: String)
    suspend fun setSpotifyClientSecret(value: String)
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
    suspend fun setLibrarySongGridColumnsPhone(columns: Int)
    suspend fun setLibrarySongGridColumnsTablet(columns: Int)
    suspend fun setLibrarySongGrid(enabled: Boolean)
    suspend fun setLibrarySongLayout(layout: Int)
    suspend fun setLibrarySongTitleMarquee(enabled: Boolean)
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
    suspend fun setSetupWizardCompleted(completed: Boolean)
    suspend fun setLocalPlaylistScanPromptHandled(handled: Boolean)
    suspend fun setNotificationPermissionPromptHandled(handled: Boolean)
    suspend fun setArtistSeparators(separators: String)
    suspend fun setArtistProtectedNames(names: String)
    suspend fun setParseFeaturedArtists(enabled: Boolean)
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
    override val filterVideoFiles: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FILTER_VIDEO_FILES] ?: true }

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
    override val searchClickPlaybackMode: Flow<Int> =
        context.dataStore.data.map {
            SettingsManager.normalizeSearchClickPlaybackMode(it[KEY_SEARCH_CLICK_PLAYBACK_MODE])
        }
    override val playlistShowRatingFilter: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYLIST_SHOW_RATING_FILTER] ?: true }
    override val playlistShowFavoriteFilter: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYLIST_SHOW_FAVORITE_FILTER] ?: true }
    override val autoShowSearchKeyboard: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] ?: true }
    override val searchReopenBehavior: Flow<Int> =
        context.dataStore.data.map {
            SettingsManager.normalizeSearchReopenBehavior(
                it[KEY_SEARCH_REOPEN_BEHAVIOR] ?: DEFAULT_SEARCH_REOPEN_BEHAVIOR
            )
        }
    override val playNextMode: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_PLAY_NEXT_MODE]?.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
                ?: PLAY_NEXT_MODE_REVERSE_STACK
        }

    override val showAlbumArtists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ALBUM_ARTISTS] ?: true }
    override val showArtistIntroduction: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ARTIST_INTRODUCTION] ?: true }
    override val artistBioDownload: Flow<Int> =
        context.dataStore.data.map {
            SettingsManager.normalizeArtistBioDownload(
                it[KEY_ARTIST_BIO_DOWNLOAD] ?: DEFAULT_ARTIST_BIO_DOWNLOAD
            )
        }
    override val artistBioLastFmLang: Flow<String> =
        context.dataStore.data.map {
            normalizeLastFmWikiRegion(it[KEY_ARTIST_BIO_LASTFM_LANG] ?: DEFAULT_LAST_FM_WIKI_REGION)
        }
    override val artistBioSource: Flow<String> =
        context.dataStore.data.map {
            com.ella.music.data.lastfm.normalizeArtistBioSource(it[KEY_ARTIST_BIO_SOURCE]).id
        }
    override val artistImageRegion: Flow<String> =
        context.dataStore.data.map {
            normalizeLastFmWikiRegion(it[KEY_ARTIST_IMAGE_REGION] ?: DEFAULT_LAST_FM_WIKI_REGION)
        }
    override val artistImageDownload: Flow<Int> =
        context.dataStore.data.map {
            SettingsManager.normalizeArtistImageDownload(
                it[KEY_ARTIST_IMAGE_DOWNLOAD] ?: DEFAULT_ARTIST_IMAGE_DOWNLOAD
            )
        }
      override val artistImageSourceOrder: Flow<List<String>> =
          context.dataStore.data.map {
            val raw = it[KEY_ARTIST_IMAGE_SOURCES]
            val stored = raw
                .orEmpty()
                .lineSequence()
                .toList()
            val normalized = SettingsManager.normalizeArtistImageSources(stored)
            if (raw == null) {
                DEFAULT_ARTIST_IMAGE_SOURCES
            } else {
                normalized
            }
        }
    override val spotifyClientId: Flow<String> = context.dataStore.data.map { it[KEY_SPOTIFY_CLIENT_ID].orEmpty() }
    override val spotifyClientSecret: Flow<String> =
        context.dataStore.data.map { it[KEY_SPOTIFY_CLIENT_SECRET].orEmpty() }
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
    override val setupWizardCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SETUP_WIZARD_COMPLETED] ?: false }
    override val localPlaylistScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED] ?: false }
    override val notificationPermissionPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED] ?: false }
    override val artistSeparators: Flow<String> = context.dataStore.data.map {
        it[KEY_ARTIST_SEPARATORS] ?: DEFAULT_ARTIST_SEPARATORS
    }
    override val artistProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_PROTECTED_NAMES] ?: "" }
    override val parseFeaturedArtists: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PARSE_FEATURED_ARTISTS] ?: false
    }
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
    override val librarySongGridColumnsPhone: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LIBRARY_SONG_GRID_COLUMNS_PHONE] ?: 2).coerceIn(1, 4)
    }
    override val librarySongGridColumnsTablet: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LIBRARY_SONG_GRID_COLUMNS_TABLET] ?: 5).coerceIn(3, 8)
    }
    override val librarySongGrid: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LIBRARY_SONG_GRID] ?: false }
    override val librarySongTitleMarquee: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LIBRARY_SONG_TITLE_MARQUEE] ?: true }

    override val librarySongLayout: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_SONG_LAYOUT]
            ?: if (prefs[KEY_LIBRARY_SONG_GRID] == true) {
                SettingsManager.LIBRARY_LAYOUT_GRID
            } else {
                SettingsManager.LIBRARY_LAYOUT_LIST
            }
    }.map { it.coerceIn(SettingsManager.LIBRARY_LAYOUT_LIST, SettingsManager.LIBRARY_LAYOUT_GRID) }

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

    override suspend fun setFilterVideoFiles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FILTER_VIDEO_FILES] = enabled }
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
    override suspend fun setSearchClickPlaybackMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_SEARCH_CLICK_PLAYBACK_MODE] = SettingsManager.normalizeSearchClickPlaybackMode(mode)
        }
    }

    override suspend fun setPlaylistShowRatingFilter(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYLIST_SHOW_RATING_FILTER] = enabled }
    }

    override suspend fun setPlaylistShowFavoriteFilter(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYLIST_SHOW_FAVORITE_FILTER] = enabled }
    }

    override suspend fun setAutoShowSearchKeyboard(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] = enabled }
    }

    override suspend fun setSearchReopenBehavior(behavior: Int) {
        context.dataStore.edit {
            it[KEY_SEARCH_REOPEN_BEHAVIOR] = SettingsManager.normalizeSearchReopenBehavior(behavior)
        }
    }

    override suspend fun setPlayNextMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_PLAY_NEXT_MODE] = mode.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
        }
    }

    override suspend fun setShowAlbumArtists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ALBUM_ARTISTS] = enabled }
    }

    override suspend fun setShowArtistIntroduction(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ARTIST_INTRODUCTION] = enabled }
    }

    override suspend fun setArtistBioDownload(mode: Int) {
        context.dataStore.edit {
            it[KEY_ARTIST_BIO_DOWNLOAD] = SettingsManager.normalizeArtistBioDownload(mode)
        }
    }

    override suspend fun setArtistBioLastFmLang(lang: String) {
        context.dataStore.edit {
            it[KEY_ARTIST_BIO_LASTFM_LANG] = normalizeLastFmWikiRegion(lang)
        }
    }

    override suspend fun setArtistBioSource(source: String) {
        context.dataStore.edit {
            it[KEY_ARTIST_BIO_SOURCE] = com.ella.music.data.lastfm.normalizeArtistBioSource(source).id
        }
    }

    override suspend fun setArtistImageDownload(mode: Int) {
        context.dataStore.edit {
            it[KEY_ARTIST_IMAGE_DOWNLOAD] = SettingsManager.normalizeArtistImageDownload(mode)
        }
    }

    override suspend fun setArtistImageRegion(region: String) {
        context.dataStore.edit {
            it[KEY_ARTIST_IMAGE_REGION] = normalizeLastFmWikiRegion(region)
        }
    }

    override suspend fun setArtistImageSourceOrder(sources: List<String>) {
        context.dataStore.edit {
            it[KEY_ARTIST_IMAGE_SOURCES] = SettingsManager.normalizeArtistImageSources(sources)
                .joinToString(separator = "\n")
        }
    }

    override suspend fun setSpotifyClientId(value: String) {
        context.dataStore.edit {
            val normalized = value.trim()
            if (normalized.isBlank()) it.remove(KEY_SPOTIFY_CLIENT_ID) else it[KEY_SPOTIFY_CLIENT_ID] = normalized
        }
    }

    override suspend fun setSpotifyClientSecret(value: String) {
        context.dataStore.edit {
            val normalized = value.trim()
            if (normalized.isBlank()) it.remove(KEY_SPOTIFY_CLIENT_SECRET) else it[KEY_SPOTIFY_CLIENT_SECRET] = normalized
        }
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

    override suspend fun setLibrarySongGridColumnsPhone(columns: Int) {
        context.dataStore.edit { it[KEY_LIBRARY_SONG_GRID_COLUMNS_PHONE] = columns.coerceIn(1, 4) }
    }

    override suspend fun setLibrarySongGridColumnsTablet(columns: Int) {
        context.dataStore.edit { it[KEY_LIBRARY_SONG_GRID_COLUMNS_TABLET] = columns.coerceIn(3, 8) }
    }

    override suspend fun setLibrarySongGrid(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_LIBRARY_SONG_GRID] = enabled
            it[KEY_LIBRARY_SONG_LAYOUT] = if (enabled) {
                SettingsManager.LIBRARY_LAYOUT_GRID
            } else {
                SettingsManager.LIBRARY_LAYOUT_LIST
            }
        }
    }

    override suspend fun setLibrarySongTitleMarquee(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LIBRARY_SONG_TITLE_MARQUEE] = enabled }
    }

    override suspend fun setLibrarySongLayout(layout: Int) {
        val resolved = layout.coerceIn(
            SettingsManager.LIBRARY_LAYOUT_LIST,
            SettingsManager.LIBRARY_LAYOUT_GRID
        )
        context.dataStore.edit {
            it[KEY_LIBRARY_SONG_LAYOUT] = resolved
            it[KEY_LIBRARY_SONG_GRID] = resolved == SettingsManager.LIBRARY_LAYOUT_GRID
        }
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

    override suspend fun setSetupWizardCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_WIZARD_COMPLETED] = completed }
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

    override suspend fun setParseFeaturedArtists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PARSE_FEATURED_ARTISTS] = enabled }
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
