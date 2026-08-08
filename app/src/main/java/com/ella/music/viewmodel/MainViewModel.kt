package com.ella.music.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.PlaylistBatchImportResult
import com.ella.music.data.PlaylistBatchExportResult
import com.ella.music.data.PlaylistExportResult
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportResult
import com.ella.music.data.PlaylistImportMode
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackHistorySource
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.lastfm.LastFmHistoryStore
import com.ella.music.data.lastfm.ListeningHistorySource
import com.ella.music.data.ArtistCoverRepository
import com.ella.music.data.ArtistCoverAsset
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.remote.OpenSubsonicCollectionsStore
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.analytics.prewarmLibraryAnalysisCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository.getInstance(application)
    val settingsManager = SettingsManager.getInstance(application)
    private val artistCoverRepository = ArtistCoverRepository.getInstance(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val openSubsonicCollectionsStore = OpenSubsonicCollectionsStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val lastFmHistoryStore = LastFmHistoryStore.getInstance(application)
    private val aiCoordinator = MainViewModelAiCoordinator(getApplication(), settingsManager, repository)
    private val neteaseLinkResolver = MainNeteaseLinkResolver(
        repository = repository,
        songsForArtist = ::getSongsForArtist,
        songsForAlbum = ::getSongsForAlbum
    )

    val songs: StateFlow<List<Song>> = repository.songs

    private val playlistCoordinator = MainViewModelPlaylistCoordinator(
        playlistStore = playlistStore,
        settingsManager = settingsManager,
        scope = viewModelScope,
        currentSongs = { songs.value }
    )

    val albums: StateFlow<List<Album>> = repository.albums

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val scanSummaryEvents = repository.scanSummaryEvents
    val playbackStats: StateFlow<List<SongPlaybackStats>> = playbackStatsStore.stats
    val listeningHistorySource: StateFlow<ListeningHistorySource> = settingsManager.listeningHistorySource
        .map(ListeningHistorySource::fromPreference)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListeningHistorySource.Local)
    private val lastFmDailyListenMs: StateFlow<Map<String, Long>> = combine(
        lastFmHistoryStore.history,
        songs,
        playbackStatsStore.history,
        playbackStatsStore.hiddenRemoteHistoryEntryIds
    ) { history, librarySongs, localHistory, hiddenRemoteEntryIds ->
        estimateLastFmDailyListenMs(
            history = history.filterNot { "lastfm:${it.cacheKey}" in hiddenRemoteEntryIds },
            librarySongs = librarySongs,
            existingHistory = localHistory
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val playbackHistory: StateFlow<List<PlaybackHistoryEntry>> = combine(
        playbackStatsStore.history,
        lastFmHistoryStore.history,
        listeningHistorySource,
        playbackStatsStore.hiddenRemoteHistoryEntryIds
    ) { local, lastFm, source, hiddenRemoteEntryIds ->
        val remote = lastFm.map { it.toPlaybackHistoryEntry() }
            .filterNot { it.entryId in hiddenRemoteEntryIds }
        when (source) {
            ListeningHistorySource.Local -> local
            ListeningHistorySource.LastFm -> remote
            ListeningHistorySource.Combined -> mergePlaybackHistorySources(local, remote)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dailyListenMs: StateFlow<Map<String, Long>> = combine(
        playbackHistory,
        lastFmDailyListenMs,
        listeningHistorySource
    ) { visibleHistory, lastFm, source ->
        val visibleLocal = visibleHistory
            .filter { it.source == PlaybackHistorySource.LOCAL }
            .groupBy { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it.playedAt)) }
            .mapValues { (_, entries) -> entries.sumOf { it.listenedMs } }
        when (source) {
            ListeningHistorySource.Local -> visibleLocal
            ListeningHistorySource.LastFm -> lastFm
            ListeningHistorySource.Combined -> mergeDailyListenMs(visibleLocal, lastFm)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    suspend fun removePlaybackHistoryEntry(entry: PlaybackHistoryEntry) {
        if (entry.source == PlaybackHistorySource.LOCAL) {
            playbackStatsStore.removeHistoryEntry(entry)
        } else if (entry.source == PlaybackHistorySource.LAST_FM) {
            playbackStatsStore.hideRemoteHistoryEntry(entry.entryId)
        }
    }
    val playlists: StateFlow<List<UserPlaylist>> = combine(
        playlistStore.playlists,
        openSubsonicCollectionsStore.playlists
    ) { localPlaylists, remotePlaylists ->
        localPlaylists + remotePlaylists
    }.stateIn(viewModelScope, SharingStarted.Eagerly, playlistStore.playlists.value)
    private val _libraryCacheLoaded = MutableStateFlow(false)
    val libraryCacheLoaded: StateFlow<Boolean> = _libraryCacheLoaded.asStateFlow()
    private val _ratingRevision = MutableStateFlow(0)
    val ratingRevision: StateFlow<Int> = _ratingRevision.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private var scanJob: Job? = null
    private var cachedLibraryLoadJob: Job? = null
    private var searchSnapshotPrewarmJob: Job? = null
    private var autoScanRequested = false
    private val metadataCategoryItemsCache = ConcurrentHashMap<String, MetadataCategoryItemsCacheEntry>()

    init {
        viewModelScope.launchNameSplitConfigObservers(
            settingsManager = settingsManager,
            onNameSplitConfigChanged = { metadataCategoryItemsCache.clear() },
            onAlbumIdentityConfigChanged = { repository.rebuildAlbumAggregation() }
        )
        // Start restoring the persisted library before the first composition. Previously the
        // activity waited for a post-frame LaunchedEffect, so every cold start rendered empty
        // collections once and then visibly rebuilt them from cache.
        loadCachedLibrary()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun scanMusic(fullRescan: Boolean = false, deepRescan: Boolean? = null) {
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            awaitCachedLibraryRestoreBeforeScanning()
            val source = settingsManager.librarySource.first()
            if (source != SettingsManager.LIBRARY_SOURCE_LOCAL) {
                // Remote library: the refresh button re-fetches the whole remote library.
                loadRemoteLibrarySource(source, forceRefresh = true)
                return@launch
            }
            val effectiveDeepRescan = deepRescan ?: (fullRescan || settingsManager.fullTagSearchEnabled.first())
            scanFromCurrentSettings(fullRescan = fullRescan, deepRescan = effectiveDeepRescan)
        }
    }

    fun setLibrarySource(source: String) {
        val requestedSource = SettingsManager.normalizeLibrarySource(source)
        viewModelScope.launch {
            settingsManager.setLibrarySource(requestedSource)
            val activeSource = settingsManager.librarySource.first()
            scanJob?.cancel()
            // The prior source's cache restore must not finish after this source switch and put
            // an obsolete library back into the repository while the new source is scanning.
            cachedLibraryLoadJob?.cancel()
            cachedLibraryLoadJob = null
            _libraryCacheLoaded.value = false
            scanJob = viewModelScope.launch {
                repository.clearInMemoryLibrary()
                if (activeSource == SettingsManager.LIBRARY_SOURCE_LOCAL) {
                    repository.loadCachedLibrary()
                    scanFromCurrentSettings(fullRescan = false, deepRescan = false)
                } else {
                    loadRemoteLibrarySource(activeSource, forceRefresh = false)
                }
                _libraryCacheLoaded.value = true
            }
        }
    }

    private suspend fun loadRemoteLibrarySource(source: String, forceRefresh: Boolean) {
        val ownerJob = currentCoroutineContext()[Job]
        repository.startScanning()
        val summary = try {
            repository.loadRemoteLibrary(source, forceRefresh = forceRefresh)
        } finally {
            repository.finishScanning()
            if (scanJob === ownerJob) scanJob = null
        }
        openSubsonicCollectionsStore.refreshForLibrarySource(source)
        repository.emitScanSummary(summary)
        _libraryCacheLoaded.value = true
        preloadLibrarySearchSnapshot()
    }

    fun fullRescanMusic() {
        scanMusic(fullRescan = true, deepRescan = true)
    }

    fun scanMusicForFolders(folders: List<String>, fullRescan: Boolean = false, deepRescan: Boolean = fullRescan) {
        if (scanJob?.isActive == true || isScanning.value) return
        val includeFolders = folders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (includeFolders.isEmpty()) return
        scanJob = viewModelScope.launch {
            awaitCachedLibraryRestoreBeforeScanning()
            scanWithIncludeFolders(
                includeFolders = includeFolders,
                preferExplicitFolders = true,
                fullRescan = fullRescan,
                deepRescan = deepRescan
            )
        }
    }

    /**
     * Refreshes (re-scans) the songs within the given [folders] and **merges** the results into
     * the current library. Unlike [scanMusicForFolders], this does NOT replace the entire library —
     * it only re-scans the specified folders for metadata/cover updates and adds any newly-found
     * songs in those folders, leaving all other library entries untouched.
     *
     * Use this for the "文件夹歌单" (folder playlist) refresh action.
     */
    fun refreshFolderPlaylistFolders(folders: List<String>) {
        if (scanJob?.isActive == true || isScanning.value) return
        val normalizedFolders = folders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedFolders.isEmpty()) return
        scanJob = viewModelScope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            awaitCachedLibraryRestoreBeforeScanning()
            repository.startScanning()
            val summary = try {
                val minDuration = settingsManager.minDurationSec.first() * 1000L
                repository.refreshFolders(
                    folders = normalizedFolders,
                    minDurationMs = minDuration,
                    deepMetadata = true
                )
            } finally {
                repository.finishScanning()
                if (scanJob === ownerJob) scanJob = null
            }
            repository.emitScanSummary(summary)
            preloadLibrarySearchSnapshot()
            withContext(Dispatchers.IO) {
                prewarmLibraryAnalysisCache(getApplication(), songs.value, this@MainViewModel)
            }
        }
    }

    fun scanMusicIfAutoEnabled() {
        if (autoScanRequested) return
        autoScanRequested = true
        if (scanJob?.isActive == true || isScanning.value) return
        scanJob = viewModelScope.launch {
            awaitCachedLibraryRestoreBeforeScanning()
            val source = settingsManager.librarySource.first()
            if (source != SettingsManager.LIBRARY_SOURCE_LOCAL) {
                // Remote libraries are already restored during startup via loadCachedLibrary().
                // Do not route them through the scan flow again, otherwise every cold start /
                // activity recreation shows a fake "scan finished" toast for cached data.
                return@launch
            }
            if (!settingsManager.autoScan.first()) return@launch
            scanFromCurrentSettings(fullRescan = false, deepRescan = false)
        }
    }

    /**
     * A cache restore and a full scan both replace the entire library state. Let the lightweight
     * restore finish first so an old on-disk snapshot cannot win a race after a user-initiated
     * scan has already discovered moved files and rebuilt their metadata.
     */
    private suspend fun awaitCachedLibraryRestoreBeforeScanning() {
        cachedLibraryLoadJob?.takeIf { it.isActive }?.join()
    }

    /** Wait until the persisted library snapshot is ready for the very first app frame. */
    suspend fun awaitInitialLibraryRestore() {
        cachedLibraryLoadJob?.join()
    }

    private suspend fun scanFromCurrentSettings(fullRescan: Boolean = false, deepRescan: Boolean = fullRescan) {
        val includeFolders = settingsManager.scanIncludeFolders.first().toFolderFilterList()
        scanWithIncludeFolders(
            includeFolders = includeFolders,
            fullRescan = fullRescan,
            deepRescan = deepRescan
        )
    }

    private suspend fun scanWithIncludeFolders(
        includeFolders: List<String>,
        preferExplicitFolders: Boolean = false,
        fullRescan: Boolean = false,
        deepRescan: Boolean = fullRescan
    ) {
        val ownerJob = currentCoroutineContext()[Job]
        repository.startScanning()
        val completedSummary = try {
            val minDuration = settingsManager.minDurationSec.first() * 1000L
            val excludeFolders = settingsManager.scanExcludeFolders.first().toFolderFilterList()
            val useAndroidMediaLibrary = settingsManager.useAndroidMediaLibrary.first()
            val fullTagSearchEnabled = settingsManager.fullTagSearchEnabled.first()
            // The media-store source and deep-tag indexing are independent preferences.  In
            // particular, disabling full tag search must not silently turn the media-store
            // source back on, otherwise the UI cannot honour an explicit "off" choice.
            val effectiveUseAndroidMediaLibrary = useAndroidMediaLibrary
            // A long-press full scan is intentionally stronger than the normal full-tag-search
            // preference: it re-reads tags and also checks configured folders that MediaStore may
            // not have indexed yet.
            val effectiveDeepRescan = fullRescan || (deepRescan && fullTagSearchEnabled)
            val scanIncludeFolders = when {
                preferExplicitFolders -> includeFolders
                effectiveUseAndroidMediaLibrary -> emptyList()
                else -> includeFolders.ifEmpty { listOf("__ella_no_custom_folder__") }
            }
            val filesystemFallbackFolders = when {
                preferExplicitFolders -> includeFolders
                fullRescan && effectiveUseAndroidMediaLibrary -> includeFolders
                else -> scanIncludeFolders
            }
            var summary = repository.scanMusic(
                minDuration,
                scanIncludeFolders,
                excludeFolders,
                fullRescan = fullRescan,
                deepRescan = effectiveDeepRescan,
                deepMetadataEnabled = fullRescan || fullTagSearchEnabled,
                filesystemFallbackFolders = filesystemFallbackFolders
            )
            if (!preferExplicitFolders && summary.total == 0 && includeFolders.isNotEmpty() && (fullRescan || useAndroidMediaLibrary)) {
                summary = repository.scanMusic(
                    minDuration,
                    includeFolders,
                    excludeFolders,
                    fullRescan = fullRescan,
                    deepRescan = effectiveDeepRescan,
                    deepMetadataEnabled = fullRescan || fullTagSearchEnabled
                )
            }
            val usbFolderUris = settingsManager.usbFolderUris.first()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (usbFolderUris.isNotEmpty()) {
                val uris = usbFolderUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                repository.scanUsbFolders(
                    usbUris = uris,
                    minDurationMs = minDuration,
                    deepMetadata = effectiveDeepRescan
                )
            }
            summary
        } finally {
            repository.finishScanning()
            // Release the scan gate as soon as the repository scan is done. Search/cache
            // prewarming below is intentionally best-effort and must not block a new scan.
            if (scanJob === ownerJob) scanJob = null
        }
        repository.emitScanSummary(completedSummary)
        preloadLibrarySearchSnapshot()
        withContext(Dispatchers.IO) {
            prewarmLibraryAnalysisCache(getApplication(), songs.value, this@MainViewModel)
        }
    }

    fun loadCachedLibrary() {
        if (_libraryCacheLoaded.value || cachedLibraryLoadJob?.isActive == true) return
        cachedLibraryLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val source = settingsManager.librarySource.first()
            if (source != SettingsManager.LIBRARY_SOURCE_LOCAL) {
                // Load the cached remote library instantly on startup (network refresh happens via
                // scanMusicIfAutoEnabled / the refresh button).
                repository.loadRemoteLibrary(source, forceRefresh = false)
            } else {
                repository.loadCachedLibrary()
            }
            openSubsonicCollectionsStore.refreshForLibrarySource(source)
            _libraryCacheLoaded.value = true
            preloadLibrarySearchSnapshot()
        }
    }

    fun preloadLibrarySearchSnapshot() {
        val currentSongs = songs.value
        if (currentSongs.isEmpty()) return
        searchSnapshotPrewarmJob?.cancel()
        searchSnapshotPrewarmJob = viewModelScope.launch(Dispatchers.IO) {
            // Prewarm a cheap base snapshot immediately so the first search can hit cached
            // normalized song fields. Deeper tag fields are enriched in a second pass.
            repository.preloadLibrarySearchSnapshot(currentSongs)
            delay(1200)
            repository.preloadSongRatings(currentSongs)
            if (settingsManager.fullTagSearchEnabled.first()) {
                repository.preloadSongTagInfos(currentSongs)
                repository.preloadLibrarySearchSnapshot(currentSongs, refreshExisting = true)
            }
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return repository.getSongsForAlbum(albumId)
    }

    fun getArtists(includeAlbumArtists: Boolean = false): List<Artist> {
        return buildArtists(
            songs = songs.value,
            albums = albums.value,
            includeAlbumArtists = includeAlbumArtists
        )
    }

    fun getSongsForArtist(artistName: String, includeAlbumArtist: Boolean = false): List<Song> {
        return filterSongsForArtist(
            songs = songs.value,
            artistName = artistName,
            includeAlbumArtist = includeAlbumArtist
        )
    }

    fun getAlbumsForArtist(artistName: String): List<Album> {
        return getParticipatedAlbumsForArtist(artistName)
    }

    fun getParticipatedAlbumsForArtist(artistName: String): List<Album> {
        val artistSongs = getSongsForArtist(artistName)
        val artistAlbumIds = artistSongs.map { it.albumIdentityId() }.toSet()
        return albums.value
            .filter { it.id in artistAlbumIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun getReleaseAlbumsForArtist(artistName: String): List<Album> {
        return albums.value
            .filter { it.albumArtist.isNotBlank() && it.albumArtist.matchesArtistName(artistName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun hasAlbumArtistTags(): Boolean {
        return songs.value.any { it.albumArtist.isNotBlank() } || albums.value.any { it.albumArtist.isNotBlank() }
    }

    fun getMetadataCategoryItems(type: String): List<MetadataCategoryItem> {
        return getMetadataCategoryItems(songs.value, type)
    }

    fun getMetadataCategoryItems(
        songs: List<Song>,
        type: String
    ): List<MetadataCategoryItem> {
        val cacheKey = "$type:${System.identityHashCode(songs)}"
        metadataCategoryItemsCache[cacheKey]?.let { return it.items }
        val items = buildMetadataCategoryItems(songs, type)
        metadataCategoryItemsCache.entries.removeIf { (key, _) -> key.startsWith("$type:") && key != cacheKey }
        metadataCategoryItemsCache[cacheKey] = MetadataCategoryItemsCacheEntry(items)
        return items
    }

    fun getMetadataCategoryCount(type: String): Int {
        return countMetadataCategories(songs.value, type)
    }

    fun getMetadataCategoryCounts(types: Collection<String>): Map<String, Int> {
        return countMetadataCategories(songs.value, types)
    }

    fun getSongsForMetadataCategory(type: String, name: String): List<Song> {
        return filterSongsForMetadataCategory(songs.value, type, name)
    }

    fun getSongsForMetadataCategories(type: String, names: Collection<String>): List<Song> {
        return filterSongsForMetadataCategories(songs.value, type, names)
    }

    fun hasMetadataCategory(type: String, name: String): Boolean {
        return containsMetadataCategory(songs.value, type, name)
    }

    suspend fun getNeteaseArtistUrlForArtist(artistName: String): String? =
        neteaseLinkResolver.artistUrlForArtist(artistName)

    suspend fun getNeteaseAlbumUrlForAlbum(albumId: Long): String? =
        neteaseLinkResolver.albumUrlForAlbum(albumId)

    fun getAlbumArtUri(albumId: Long) = repository.getAlbumArtUri(albumId)

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 128, CoverUsage.ListThumbnail)

    fun getCoverArtBitmap(song: Song, maxSize: Int) = repository.getCoverArtBitmap(song, maxSize, CoverUsage.ListThumbnail)

    fun getAlbumCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 512, CoverUsage.AlbumGrid)

    fun getLargeCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1200, CoverUsage.Player)

    fun getOriginalCoverModel(song: Song): Any? = repository.getOriginalCoverModel(song)

    fun getMetadataEditorCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1600, CoverUsage.Player)

    fun getReplayGain(song: Song): Float? {
        return repository.getReplayGain(song)
    }

    fun getAudioInfo(song: Song): AudioInfo {
        return repository.getAudioInfo(song)
    }

    fun getSongTagInfo(song: Song): SongTagInfo {
        return repository.getSongTagInfo(song)
    }

    fun getArtistCoverUri(artistName: String, folderLocation: String): Uri? {
        return artistCoverRepository.getArtistCoverUri(artistName, folderLocation)
    }

    fun getArtistCoverAsset(artistName: String, folderLocation: String): ArtistCoverAsset? {
        return artistCoverRepository.getArtistCoverAsset(artistName, folderLocation)
    }

    fun getArtistCoverAssets(artistName: String, folderLocation: String): List<ArtistCoverAsset> {
        return artistCoverRepository.getArtistCoverAssets(artistName, folderLocation)
    }

    suspend fun getFiveStarSongs(): List<Song> = withContext(Dispatchers.IO) {
        songs.value.filter { repository.getSongRating(it) >= 5 }
    }

    fun getSongRating(song: Song): Int = repository.getSongRating(song)

    suspend fun writeSongRating(song: Song, rating: Int): Result<Song?> {
        val result = repository.writeSongRating(song, rating)
        if (result.isSuccess) {
            _ratingRevision.value += 1
        }
        return result
    }

    suspend fun writeSongCustomTag(song: Song, key: String, value: String): Result<Song?> =
        repository.writeSongCustomTag(song, key, value)

    suspend fun writeSongMetadata(song: Song, tags: AudioTagInfo): Result<Song?> {
        val result = repository.writeSongMetadata(song, tags)
        if (result.isSuccess && tags.rating != null) {
            _ratingRevision.value += 1
        }
        return result
    }

    suspend fun writeSongEmbeddedCover(song: Song, cover: AudioCoverInfo?): Result<Song?> =
        repository.writeSongEmbeddedCover(song, cover)

    fun getFullAudioTagInfo(song: Song): AudioTagInfo? =
        repository.getFullAudioTagInfo(song)

    suspend fun interpretSongWithOpenAi(song: Song): String =
        aiCoordinator.interpretSong(song)

    suspend fun recommendPlaylistWithOpenAi(maxItems: Int = 30): AiPlaylistRecommendationResult =
        aiCoordinator.recommendPlaylist(
            librarySongs = songs.value,
            playbackStats = playbackStats.value,
            playbackHistory = playbackHistory.value,
            maxItems = maxItems
        )

    suspend fun chatWithOpenAiLibraryAssistant(
        message: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        maxPlayableItems: Int = 30
    ): AiLibraryChatResult =
        aiCoordinator.chatWithLibrary(
            librarySongs = songs.value,
            playbackStats = playbackStats.value,
            playbackHistory = playbackHistory.value,
            message = message,
            conversationHistory = conversationHistory,
            maxPlayableItems = maxPlayableItems
        )

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    suspend fun prefetchWebDavMetadataHeaders(songs: List<Song>, maxItems: Int = 80) {
        repository.prefetchWebDavMetadataHeaders(songs, maxItems)
    }

    suspend fun resolveSongForPlayback(song: Song): Song =
        repository.resolveSongForPlayback(song)

    suspend fun songMatchesSearchSnapshot(song: Song, query: String): Boolean =
        repository.songMatchesSearchSnapshot(song, query, includeFullTags = settingsManager.fullTagSearchEnabled.first())

    suspend fun filterSongsBySearchSnapshot(songs: List<Song>, query: String): List<Song> =
        repository.filterSongsBySearchSnapshot(songs, query, includeFullTags = settingsManager.fullTagSearchEnabled.first())

    fun clearLibrarySnapshotCache() {
        viewModelScope.launch {
            repository.clearLibrarySnapshotCache()
        }
    }

    fun refreshSongAfterExternalEdit(song: Song, onUpdated: (Song?) -> Unit = {}) {
        viewModelScope.launch {
            onUpdated(repository.refreshSongAfterExternalEdit(song))
        }
    }

    fun playlistSongs(playlist: UserPlaylist): List<Song> {
        return playlistCoordinator.playlistSongs(playlist)
    }

    fun createPlaylist(name: String, onCreated: (UserPlaylist?) -> Unit = {}) {
        playlistCoordinator.createPlaylist(name, onCreated)
    }

    fun renamePlaylist(id: String, newName: String, onRenamed: (Boolean) -> Unit = {}) {
        if (playlists.value.firstOrNull { it.id == id }?.remoteWritable == true) {
            viewModelScope.launch { onRenamed(openSubsonicCollectionsStore.renamePlaylist(id, newName)) }
            return
        }
        playlistCoordinator.renamePlaylist(id, newName, onRenamed)
    }

    fun deletePlaylist(id: String) {
        if (playlists.value.firstOrNull { it.id == id }?.remoteWritable == true) {
            viewModelScope.launch { openSubsonicCollectionsStore.deletePlaylist(id) }
            return
        }
        playlistCoordinator.deletePlaylist(id)
    }

    fun deletePlaylists(ids: Set<String>) {
        playlistCoordinator.deletePlaylists(ids)
    }

    fun removeSongFromPlaylist(playlistId: String, songKey: String) {
        if (playlists.value.firstOrNull { it.id == playlistId }?.remoteWritable == true) {
            viewModelScope.launch { openSubsonicCollectionsStore.removeSongs(playlistId, setOf(songKey)) }
            return
        }
        playlistCoordinator.removeSongFromPlaylist(playlistId, songKey)
    }

    fun removeSongsFromPlaylist(playlistId: String, songKeys: Set<String>) {
        if (playlists.value.firstOrNull { it.id == playlistId }?.remoteWritable == true) {
            viewModelScope.launch { openSubsonicCollectionsStore.removeSongs(playlistId, songKeys) }
            return
        }
        playlistCoordinator.removeSongsFromPlaylist(playlistId, songKeys)
    }

    fun addSongsToPlaylist(playlistId: String, songs: Collection<Song>, appendToEnd: Boolean = false) {
        if (playlists.value.firstOrNull { it.id == playlistId }?.remoteWritable == true) {
            viewModelScope.launch { openSubsonicCollectionsStore.addSongs(playlistId, songs) }
            return
        }
        playlistCoordinator.addSongsToPlaylist(playlistId, songs, appendToEnd)
    }

    fun reorderPlaylistSongs(playlistId: String, orderedKeys: List<String>) {
        if (playlists.value.firstOrNull { it.id == playlistId }?.remoteWritable == true) {
            viewModelScope.launch { openSubsonicCollectionsStore.reorderSongs(playlistId, orderedKeys) }
            return
        }
        playlistCoordinator.reorderPlaylistSongs(playlistId, orderedKeys)
    }

    fun reorderPlaylists(orderedIds: List<String>) {
        playlistCoordinator.reorderPlaylists(orderedIds)
    }

    fun importLocalPlaylist(uri: Uri, onResult: (Result<PlaylistImportResult>) -> Unit) {
        playlistCoordinator.importLocalPlaylist(uri, onResult)
    }

    fun importLocalPlaylists(
        uris: List<Uri>,
        mode: PlaylistImportMode = PlaylistImportMode.MergeKeepExisting,
        onResult: (Result<PlaylistBatchImportResult>) -> Unit
    ) {
        playlistCoordinator.importLocalPlaylists(uris, mode, onResult)
    }

    fun scanLocalPlaylistFiles(
        onResult: (Result<PlaylistBatchImportResult>) -> Unit = {}
    ) {
        playlistCoordinator.scanLocalPlaylistFiles(onResult)
    }

    fun exportLocalPlaylist(
        playlist: UserPlaylist,
        uri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistExportResult>) -> Unit
    ) {
        playlistCoordinator.exportLocalPlaylist(playlist, uri, format, onResult)
    }

    fun exportLocalPlaylists(
        playlists: List<UserPlaylist>,
        treeUri: Uri,
        format: PlaylistExportFormat = PlaylistExportFormat.PlainText,
        onResult: (Result<PlaylistBatchExportResult>) -> Unit
    ) {
        playlistCoordinator.exportLocalPlaylists(playlists, treeUri, format, onResult)
    }

    fun deleteSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteSongs(songs) }
        }
    }

    suspend fun deleteSongsResult(songs: Collection<Song>): Result<Int> {
        if (songs.isEmpty()) return Result.success(0)
        return runCatching { repository.deleteSongs(songs) }
    }

    fun removeSongsFromLibrary(songs: Collection<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.removeSongsFromLibrary(songs)
        }
    }

}

private data class MetadataCategoryItemsCacheEntry(
    val items: List<MetadataCategoryItem>
)
