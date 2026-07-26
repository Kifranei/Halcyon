package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.SettingsManager
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.isMediaStoreContentAudioSource
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.metadata.WavMetadataReader
import com.ella.music.data.scanner.MediaStoreAudioItem
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.scanner.toShallowSong
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class CoverUsage {
    ListThumbnail,
    AlbumGrid,
    Player,
    Notification,
    ShareCard
}

data class MusicScanSummary(
    val total: Int,
    val added: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val fullRescan: Boolean = false
)

class MusicRepository(private val context: Context) {
    companion object {
        private const val WEBDAV_EAGER_METADATA_LIMIT = 300

        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }

        /** Releases repository caches without creating the repository just to trim memory. */
        fun clearMemoryCachesIfInitialized() {
            instance?.clearCache()
        }
    }

    data class LyricFormatAvailability(
        val hasTtml: Boolean = false,
        val hasPlain: Boolean = false
    ) {
        val hasBoth: Boolean get() = hasTtml && hasPlain
    }


    private val scanner = MusicScanner(context)
    private val audioTagRepository = AudioTagRepository(
        primary = LyricoAudioTagReaderWriter()
    )
    private val settingsManager = SettingsManager.getInstance(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("MusicRepoNetwork"))
        .build()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    private val albumAggregationGeneration = AtomicLong(0L)

    private val scanProgressState = LibraryScanProgressState()
    val isScanning: StateFlow<Boolean> = scanProgressState.isScanning
    val scanProgress: StateFlow<Int> = scanProgressState.scanProgress

    private val _scanSummaryEvents = MutableSharedFlow<MusicScanSummary>(extraBufferCapacity = 1)
    val scanSummaryEvents: SharedFlow<MusicScanSummary> = _scanSummaryEvents.asSharedFlow()

    fun startScanning() {
        scanProgressState.start()
    }

    fun emitScanSummary(summary: MusicScanSummary) {
        _scanSummaryEvents.tryEmit(summary)
    }

    fun finishScanning() {
        scanProgressState.finish()
    }

    fun clearInMemoryLibrary() {
        _songs.value = emptyList()
        _albums.value = emptyList()
    }

    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")
    private val remoteMetadataHeaderCacheDir = File(context.cacheDir, "webdav_metadata_headers")
    private val lyricsManager = MusicLyricsManager(context, settingsManager, audioTagRepository, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
    private val coverArtManager = MusicCoverArtManager(context, audioTagRepository, settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
    private val snapshotManager: MusicSnapshotManager = MusicSnapshotManager(
        File(context.filesDir, "library_search_snapshot.json"),
        File(context.filesDir, "library_rating_snapshot.json")
    ) { song -> searchCoordinator.buildSongSearchSnapshotText(song, includeCachedTagInfo = true) }
    private val searchCoordinator: MusicLibrarySearchCoordinator =
        MusicLibrarySearchCoordinator(snapshotManager) { song -> getCachedSongTagInfo(song) }

    private val tagInfoCache = ConcurrentHashMap<String, SongTagInfo>()
    private val audioInfoProvider = MusicAudioInfoProvider(scanner, audioTagRepository) { song ->
        song.effectiveLocalPathForMetadata()
    }
    private val tagWriter = MusicTagWriter(
        context,
        audioTagRepository,
        metadataPathResolver = { song -> song.effectiveLocalPathForMetadata() },
        clearMetadataCache = { song -> clearMetadataCache(song) }
    )
    private val libraryCacheStore = MusicLibraryCacheStore(context, settingsManager)

    suspend fun scanMusic(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        fullRescan: Boolean = false,
        deepRescan: Boolean = fullRescan,
        deepMetadataEnabled: Boolean = true,
        filesystemFallbackFolders: List<String> = includeFolders
    ): MusicScanSummary {
        val mode = if (includeFolders.isEmpty()) "media_library" else "custom_folders"
        val previousSongs = libraryCacheStore.readLocalScanBaselineSongs().ifEmpty { _songs.value }
        AppLogStore.info(
            context,
            "MusicScanner",
            "Start scan mode=$mode minDuration=${minDurationMs}ms include=${includeFolders.size} fallbackFolders=${filesystemFallbackFolders.size} exclude=${excludeFolders.size} fullRescan=$fullRescan deepRescan=$deepRescan deepMetadataEnabled=$deepMetadataEnabled",
            AppLogType.LIBRARY
        )
        // A complete scan must always rebuild tags, even when the normal-scan full-tag option is
        // off. Otherwise a long press only re-enumerates MediaStore and can retain stale metadata.
        val effectiveDeepRescan = fullRescan || (deepRescan && deepMetadataEnabled)
        if (effectiveDeepRescan) {
            clearScanMetadataCaches()
            if (fullRescan) snapshotManager.clearLibraryCache()
        }
        val scanResult = if (fullRescan || effectiveDeepRescan) {
            val scannedSongs = scanner.scanAllSongs(
                minDurationMs = minDurationMs,
                includeFolders = includeFolders,
                excludeFolders = excludeFolders,
                deepMetadata = effectiveDeepRescan,
                onProgress = { count -> scanProgressState.update(count) },
                filesystemFallbackFolders = filesystemFallbackFolders
            )
            LibraryScanResult(
                songs = scannedSongs,
                summary = buildFullScanSummary(previousSongs, scannedSongs, fullRescan = true)
            )
        } else {
            synchronizeLibrary(
                minDurationMs = minDurationMs,
                includeFolders = includeFolders,
                excludeFolders = excludeFolders,
                previousSummarySongs = previousSongs,
                deepMetadataEnabled = deepMetadataEnabled
            )
        }
        val scannedSongs = scanResult.songs
        // A transient MediaStore/provider failure must not overwrite both on-disk snapshots with
        // an empty library during an ordinary refresh. A deliberate full scan still owns the
        // result, including an intentionally empty library.
        val preservePreviousLibrary = !fullRescan && scannedSongs.isEmpty() && previousSongs.isNotEmpty()
        val resolvedSongs = if (preservePreviousLibrary) previousSongs else scannedSongs
        if (preservePreviousLibrary) {
            AppLogStore.warn(
                context,
                "MusicScanner",
                "Ignoring unexpected empty incremental scan and preserving ${previousSongs.size} cached songs",
                type = AppLogType.LIBRARY
            )
        }
        val resolvedSummary = if (preservePreviousLibrary) {
            MusicScanSummary(total = resolvedSongs.size, failed = scanResult.summary.failed)
        } else {
            scanResult.summary.copy(total = resolvedSongs.size)
        }
        val clearedRatingSnapshots = snapshotManager.clearMissingFileSnapshots(resolvedSongs.map { it.path }.toSet())
        val albums = resolvedSongs.toAlbums()
        _songs.value = resolvedSongs
        _albums.value = albums
        libraryCacheStore.saveLibraryCache(resolvedSongs, albums)
        libraryCacheStore.saveLocalScanBaseline(resolvedSongs, albums)
        AppLogStore.info(
            context,
            "MusicScanner",
            "Scan finished mode=$mode songs=${resolvedSongs.size} albums=${_albums.value.size} added=${resolvedSummary.added} removed=${resolvedSummary.deleted} updated=${resolvedSummary.updated} ratingSnapshotsCleared=$clearedRatingSnapshots preservedPrevious=$preservePreviousLibrary",
            AppLogType.LIBRARY
        )
        return resolvedSummary
    }

    /**
     * Scan USB folders via SAF and merge the results into the current library.
     */
    suspend fun scanUsbFolders(
        usbUris: List<android.net.Uri>,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false
    ): MusicScanSummary {
        if (usbUris.isEmpty()) return MusicScanSummary(total = _songs.value.size)
        val existingSongs = _songs.value
        val existingPaths = existingSongs.map { it.path }.toSet()
        val usbSongs = mutableListOf<Song>()
        for (uri in usbUris) {
            val accessible = scanner.isUsbUriAccessible(uri)
            if (!accessible) {
                AppLogStore.info(
                    context,
                    "MusicScanner",
                    "USB URI not accessible, skipping: $uri",
                    AppLogType.LIBRARY
                )
                continue
            }
            val found = scanner.scanUsbFolder(
                treeUri = uri,
                minDurationMs = minDurationMs,
                deepMetadata = deepMetadata
            ) { count -> scanProgressState.update(count) }
            usbSongs.addAll(found.filter { it.path !in existingPaths })
        }
        if (usbSongs.isNotEmpty()) {
            val merged = existingSongs + usbSongs
            _songs.value = merged
            _albums.value = merged.toAlbums()
            libraryCacheStore.saveLibraryCache(merged, _albums.value)
            AppLogStore.info(
                context,
                "MusicScanner",
                "USB scan finished: ${usbSongs.size} new songs from ${usbUris.size} folders, total=${merged.size}",
                AppLogType.LIBRARY
            )
            return MusicScanSummary(total = merged.size, added = usbSongs.size)
        }
        return MusicScanSummary(total = _songs.value.size)
    }

    /**
     * Refreshes (re-scans) the songs within the given [folders] and **merges** the results into
     * the current library, rather than replacing the entire library. This is used by the
     * "文件夹歌单" (folder playlist) refresh action: only songs under the playlist's folders are
     * re-scanned for metadata/cover updates, and any newly-discovered songs in those folders are
     * added. Songs elsewhere in the library are left untouched.
     *
     * @return a summary of the refresh (added/updated counts).
     */
    suspend fun refreshFolders(
        folders: List<String>,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = true
    ): MusicScanSummary = withContext(Dispatchers.IO) {
        val normalizedFolders = folders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedFolders.isEmpty()) return@withContext MusicScanSummary(total = _songs.value.size)

        val previousSummarySongs = libraryCacheStore.readLocalScanBaselineSongs()
        val existingSongs = _songs.value
        val existingByPath = existingSongs.associateBy { it.path }
        val existingPaths = existingByPath.keys

        // Scan only the specified folders.
        val scannedSongs = scanner.scanAllSongs(
            minDurationMs = minDurationMs,
            includeFolders = normalizedFolders,
            excludeFolders = emptyList(),
            deepMetadata = deepMetadata
        ) { count -> scanProgressState.update(count) }

        val scannedByPath = scannedSongs.associateBy { it.path }

        // Build the merged library: keep all existing songs, but replace/update those whose path
        // falls within the scanned folders, and add any brand-new songs found.
        val merged = existingSongs.mapTo(ArrayList(existingSongs.size)) { existing ->
            val scanned = scannedByPath[existing.path]
            if (scanned != null) {
                scanned
            } else {
                existing
            }
        }
        // Add songs that are in the scanned folders but not already in the library.
        val newPathSongs = scannedSongs.filter { it.path !in existingPaths }
        if (newPathSongs.isNotEmpty()) {
            merged.addAll(newPathSongs)
        }

        val clearedRatingSnapshots = snapshotManager.clearMissingFileSnapshots(merged.map { it.path }.toSet())
        val normalizedFolderFilters = normalizedFolders.mapNotNull { it.normalizedLocalFolderPath() }
        val previousFolderSongs = previousSummarySongs.filter { song ->
            song.path.isAllowedByLocalFolderFilters(normalizedFolderFilters, excludeFolders = emptyList())
        }
        val currentFolderSongs = merged.filter { song ->
            song.path.isAllowedByLocalFolderFilters(normalizedFolderFilters, excludeFolders = emptyList())
        }
        val summary = buildLibraryDeltaSummary(previousFolderSongs, currentFolderSongs).copy(total = merged.size)
        val albums = merged.toAlbums()
        _songs.value = merged
        _albums.value = albums
        libraryCacheStore.saveLibraryCache(merged, albums)
        libraryCacheStore.saveLocalScanBaseline(merged, albums)
        AppLogStore.info(
            context,
            "MusicScanner",
            "Folder refresh finished: folders=${normalizedFolders.size} scanned=${scannedSongs.size} added=${summary.added} updated=${summary.updated} total=${merged.size}",
            AppLogType.LIBRARY
        )
        summary
    }

    private suspend fun synchronizeLibrary(
        minDurationMs: Long,
        includeFolders: List<String>,
        excludeFolders: List<String>,
        previousSummarySongs: List<Song>,
        deepMetadataEnabled: Boolean = true
    ): LibraryScanResult = withContext(Dispatchers.IO) {
        val cachedSongs = _songs.value.takeIf { it.isNotEmpty() } ?: libraryCacheStore.readCachedSongs()
        val cachedBySyncKey = cachedSongs.associateBy { it.librarySyncKey() }
        val cachedByPath = cachedSongs.associateBy { it.path }
        val currentItems = scanner.enumerateAudioFiles(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders
        )
        val currentKeys = currentItems.map { it.librarySyncKey() }.toSet()
        val currentPaths = currentItems.map { it.path }.toSet()
        val mergedSongs = ArrayList<Song>(currentItems.size)
        var reusedCount = 0
        var failedCount = 0

        currentItems.forEachIndexed { index, item ->
            val cached = cachedBySyncKey[item.librarySyncKey()] ?: cachedByPath[item.path]
            val mediaStoreSaysTooShort = item.duration > 0L && item.duration < minDurationMs
            if (mediaStoreSaysTooShort) {
                scanProgressState.update(index + 1)
                return@forEachIndexed
            }

            val currentInfo = item.toLibrarySyncInfo()
            val cachedInfo = cached?.toLibrarySyncInfo()
            val needsUpdate = cachedInfo == null ||
                cachedInfo.key != currentInfo.key ||
                cachedInfo.path != currentInfo.path ||
                cachedInfo.fileSize != currentInfo.fileSize ||
                cachedInfo.dateModified != currentInfo.dateModified ||
                (deepMetadataEnabled && cached.needsMetadataPlaceholderRefresh())

            if (needsUpdate) {
                val scanned = runCatching {
                    buildIncrementalLibrarySong(
                        item = item,
                        minDurationMs = minDurationMs,
                        deepMetadataEnabled = deepMetadataEnabled
                    )
                }.onFailure { error ->
                    failedCount++
                    AppLogStore.warn(
                        context,
                        "MusicScanner",
                        "Incremental item failed path=${item.path}: ${error.message ?: error.javaClass.name}",
                        type = AppLogType.LIBRARY
                    )
                }.getOrNull()

                if (scanned != null) {
                    cached?.let(::clearMetadataCache)
                    clearMetadataCache(scanned)
                    mergedSongs += scanned
                } else if (cached != null) {
                    mergedSongs += cached
                }
            } else {
                val reused = cached.copy(
                    albumId = item.albumId,
                    fileName = item.fileName.ifBlank { cached.fileName },
                    mimeType = item.mimeType.ifBlank { cached.mimeType },
                    dateAdded = item.dateAdded.takeIf { it > 0L } ?: cached.dateAdded,
                    trackNumber = item.trackNumber.takeIf { it > 0 } ?: cached.trackNumber,
                    discNumber = item.discNumber.takeIf { it > 0 } ?: cached.discNumber
                )
                if (reused.duration >= minDurationMs) {
                    mergedSongs += reused
                    reusedCount++
                }
            }
            scanProgressState.update(index + 1)
        }

        // MediaStore can lag behind filesystem changes.  A fast scan must not throw away songs
        // discovered by a previous full scan merely because the provider has not indexed them
        // yet; retain those entries until their local file actually disappears.
        val retainedFromFilesystem = cachedSongs.filter { song ->
            song.librarySyncKey() !in currentKeys &&
                song.path !in currentPaths &&
                song.hasExistingLocalFile()
        }
        retainedFromFilesystem.forEach { retained ->
            if (mergedSongs.none { it.path == retained.path }) mergedSongs += retained
        }
        val deletedSongs = cachedSongs.filter { song ->
            song.librarySyncKey() !in currentKeys &&
                song.path !in currentPaths &&
                song !in retainedFromFilesystem
        }
        deletedSongs.forEach(::clearMetadataCache)
        val summary = buildLibraryDeltaSummary(previousSummarySongs, mergedSongs)
            .copy(total = mergedSongs.size, failed = failedCount)

        AppLogStore.info(
            context,
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=${summary.added} updated=${summary.updated} reused=$reusedCount retained=${retainedFromFilesystem.size} deleted=${summary.deleted} failed=$failedCount",
            AppLogType.LIBRARY
        )
        Log.d(
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=${summary.added} updated=${summary.updated} reused=$reusedCount retained=${retainedFromFilesystem.size} deleted=${summary.deleted} failed=$failedCount"
        )
        LibraryScanResult(
            songs = mergedSongs,
            summary = summary
        )
    }

    private fun buildFullScanSummary(
        previousSongs: List<Song>,
        scannedSongs: List<Song>,
        fullRescan: Boolean
    ): MusicScanSummary = buildLibraryDeltaSummary(
        previousSongs = previousSongs,
        currentSongs = scannedSongs,
        fullRescan = fullRescan
    )

    private fun buildLibraryDeltaSummary(
        previousSongs: List<Song>,
        currentSongs: List<Song>,
        fullRescan: Boolean = false
    ): MusicScanSummary {
        val previousByKey = previousSongs.associateBy { it.scanSummaryKey() }
        val currentByKey = currentSongs.associateBy { it.scanSummaryKey() }
        val added = currentByKey.keys.count { it !in previousByKey }
        val deleted = previousByKey.keys.count { it !in currentByKey }
        val updated = currentByKey.count { (key, song) ->
            val previous = previousByKey[key]
            previous != null && previous.toScanSummaryInfo() != song.toScanSummaryInfo()
        }
        return MusicScanSummary(
            total = currentSongs.size,
            added = added,
            updated = updated,
            deleted = deleted,
            failed = 0,
            fullRescan = fullRescan
        )
    }

    private data class LibraryScanResult(
        val songs: List<Song>,
        val summary: MusicScanSummary
    )

    private suspend fun buildIncrementalLibrarySong(
        item: MediaStoreAudioItem,
        minDurationMs: Long,
        deepMetadataEnabled: Boolean = true
    ): Song? {
        item.toShallowSong(minDurationMs)?.let { shallow ->
            return if (deepMetadataEnabled) shallow.withRepositoryTags() else shallow.withFinalLibraryFallbacks()
        }
        return scanner.scanAudioItem(
            item = item,
            minDurationMs = minDurationMs,
            deepMetadata = false
        )?.let { scanned ->
            if (deepMetadataEnabled) scanned.withRepositoryTags() else scanned.withFinalLibraryFallbacks()
        }
    }

    suspend fun refreshSongAfterExternalEdit(song: Song): Song? = withContext(Dispatchers.IO) {
        if (song.path.isHttpAudioSource()) return@withContext null

        clearMetadataCache(song)
        scanEditedFile(song)
        delay(350)

        val updated = (querySystemSong(song) ?: song.withCurrentFileSnapshot())
            .withRepositoryTags()
            .withCurrentFileSnapshot()
        clearMetadataCache(updated)

        val currentSongs = _songs.value
        if (currentSongs.isNotEmpty()) {
            val nextSongs = currentSongs.map { existing ->
                if (existing.id == song.id || existing.path == song.path) updated else existing
            }
            _songs.value = nextSongs
            _albums.value = nextSongs.toAlbums()
            libraryCacheStore.saveLibraryCache(nextSongs, _albums.value)
        }
        updated
    }

    suspend fun loadCachedLibrary() = withContext(Dispatchers.IO) {
        fun applyCachedSongs(songs: List<Song>) {
            val albums = songs.toAlbums()
            _songs.value = songs
            _albums.value = albums
            if (!hasLibraryCache(libraryCacheStore.localScanBaselineFile)) {
                libraryCacheStore.saveLocalScanBaseline(songs, albums)
            }
        }

        val primarySongs = if (hasLibraryCache(libraryCacheStore.libraryCacheFile)) {
            runCatching { readLibraryCacheSongs(libraryCacheStore.libraryCacheFile) }
                .onFailure { Log.w("MusicRepo", "Failed to load music library cache", it) }
                .getOrNull()
        } else {
            null
        }
        if (!primarySongs.isNullOrEmpty()) {
            applyCachedSongs(primarySongs)
            return@withContext
        }

        // If a service-process crash lands between the primary and baseline writes, the primary
        // cache can be a valid-but-empty snapshot while the baseline still has the last complete
        // library. Prefer that non-empty recovery snapshot, then heal the primary cache.
        val baselineSongs = if (hasLibraryCache(libraryCacheStore.localScanBaselineFile)) {
            runCatching { readLibraryCacheSongs(libraryCacheStore.localScanBaselineFile) }
                .onFailure { Log.w("MusicRepo", "Failed to restore local scan baseline", it) }
                .getOrNull()
        } else {
            null
        }
        if (!baselineSongs.isNullOrEmpty()) {
            val albums = baselineSongs.toAlbums()
            _songs.value = baselineSongs
            _albums.value = albums
            libraryCacheStore.saveLibraryCacheTo(libraryCacheStore.libraryCacheFile, baselineSongs, albums)
        } else if (primarySongs != null) {
            applyCachedSongs(primarySongs)
        } else if (baselineSongs != null) {
            applyCachedSongs(baselineSongs)
            libraryCacheStore.saveLibraryCacheTo(libraryCacheStore.libraryCacheFile, baselineSongs, baselineSongs.toAlbums())
        } else {
            clearInMemoryLibrary()
        }
    }

    /**
     * Load the whole library from a remote source (Navidrome / Emby / WebDAV) into [_songs] /
     * [_albums] so all the local library views (artist / album / genre / year …) work against
     * streamed songs.
     *
     * With [forceRefresh] = false a cached snapshot is loaded instantly (and only fetched over the
     * network when there is no cache); the library refresh button passes true to re-fetch. The
     * remote library is cached per-source so switching back is instant and works offline.
     */
    suspend fun loadRemoteLibrary(source: String, forceRefresh: Boolean): MusicScanSummary = withContext(Dispatchers.IO) {
        val cacheFile = libraryCacheStore.remoteLibraryCacheFile(source)
        val previousSongs = _songs.value.takeIf { it.isNotEmpty() }
            ?: runCatching { readLibraryCacheSongs(cacheFile) }.getOrDefault(emptyList())

        fun applyCache(): Boolean {
            if (!hasLibraryCache(cacheFile)) return false
            val cached = runCatching { readLibraryCacheSongs(cacheFile) }.getOrDefault(emptyList())
            if (cached.isEmpty()) return false
            _songs.value = cached
            _albums.value = cached.toAlbums()
            return true
        }

        if (!forceRefresh && applyCache()) {
            return@withContext MusicScanSummary(total = _songs.value.size)
        }

        val remoteSongs = runCatching {
            when (source) {
                SettingsManager.LIBRARY_SOURCE_NAVIDROME -> {
                    val config = settingsManager.navidromeConfig.first()
                    if (!config.isConfigured) return@runCatching null
                    NavidromeService(context).listSongs(config).map { it.song }
                }
                SettingsManager.LIBRARY_SOURCE_OPENSUBSONIC -> {
                    val config = settingsManager.openSubsonicConfig.first()
                    if (!config.isConfigured) return@runCatching null
                    NavidromeService(context).listSongs(config).map { it.song }
                }
                SettingsManager.LIBRARY_SOURCE_EMBY -> {
                    val config = settingsManager.embyConfig.first()
                    if (!config.isConfigured) return@runCatching null
                    EmbyService(context).listSongs(config).map { it.song }
                }
                SettingsManager.LIBRARY_SOURCE_WEBDAV -> {
                    val config = loadWebDavConfig(settingsManager) ?: return@runCatching null
                    listWebDavLibrarySongs(config, forceRefresh = forceRefresh)
                }
                else -> return@withContext MusicScanSummary(total = _songs.value.size)
            }
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to load remote library ($source)", error)
            if (!applyCache()) clearInMemoryLibrary()
            return@withContext MusicScanSummary(total = _songs.value.size)
        } ?: run {
            // Not configured yet — fall back to any cache, otherwise leave the library empty.
            if (!applyCache()) clearInMemoryLibrary()
            return@withContext MusicScanSummary(total = _songs.value.size)
        }

        _songs.value = remoteSongs
        _albums.value = remoteSongs.toAlbums()
        libraryCacheStore.saveLibraryCacheTo(cacheFile, remoteSongs, _albums.value)
        buildLibraryDeltaSummary(previousSongs, remoteSongs)
    }

    private fun listWebDavLibrarySongs(config: WebDavConfig, forceRefresh: Boolean): List<Song> {
        if (forceRefresh) WebDavClient.clearListCache()
        val root = config.url.trim()
        val pending = ArrayDeque<String>().apply { add(root) }
        val visited = LinkedHashSet<String>()
        val songs = ArrayList<Song>()

        while (pending.isNotEmpty()) {
            val currentUrl = pending.removeFirst()
            val visitKey = WebDavClient.normalizeFileUrl(currentUrl).trimEnd('/')
            if (!visited.add(visitKey)) continue
            val items = WebDavClient.list(config, currentUrl, forceRefresh = forceRefresh)
            items.forEach { item ->
                if (item.isDirectory) {
                    pending.add(item.url)
                } else if (WebDavClient.isAudioFile(item.name)) {
                    songs += item.toWebDavLibrarySong()
                }
            }
        }
        return if (songs.size <= WEBDAV_EAGER_METADATA_LIMIT) {
            songs.map { it.withRepositoryTags(allowFullDownload = false) }
        } else {
            songs.map { song ->
                if (song.webDavHeaderCacheFile(remoteMetadataHeaderCacheDir).let { it.exists() && it.length() > 0L }) {
                    song.withRepositoryTags(allowFullDownload = false)
                } else {
                    song.withFinalLibraryFallbacks()
                }
            }
        }
    }

    private fun com.ella.music.data.webdav.WebDavItem.toWebDavLibrarySong(): Song {
        val playbackUrl = WebDavClient.normalizeFileUrl(url)
        val fileName = name.ifBlank { playbackUrl.substringBefore('?').substringBefore('#').substringAfterLast('/') }
        val title = fileName.substringBeforeLast('.', fileName).ifBlank { fileName }
        val parentAlbum = runCatching {
            java.net.URI(playbackUrl).path
                .trimEnd('/')
                .substringBeforeLast('/', "")
                .substringAfterLast('/')
                .ifBlank { "WebDAV" }
        }.getOrDefault("WebDAV")
        return Song(
            id = stableRemoteSongId("webdav:$playbackUrl"),
            title = title,
            artist = "",
            album = parentAlbum,
            albumId = 0L,
            duration = 0L,
            path = playbackUrl,
            fileName = fileName,
            fileSize = size,
            mimeType = mimeType.substringBefore(';').trim().lowercase(),
            dateAdded = System.currentTimeMillis(),
            dateModified = 0L
        )
    }

    private fun stableRemoteSongId(key: String): Long {
        val value = key.hashCode().toLong()
        return if (value == Long.MIN_VALUE) 1L else kotlin.math.abs(value).takeIf { it != 0L } ?: 1L
    }

    suspend fun getLyrics(
        song: Song,
        sourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO
    ): List<LyricLine> = lyricsManager.getLyrics(song, sourceMode)

    suspend fun reloadLyrics(song: Song, sourceMode: Int): List<LyricLine> = lyricsManager.reloadLyrics(song, sourceMode)

    suspend fun getLyricFormatAvailability(song: Song): LyricFormatAvailability = lyricsManager.getLyricFormatAvailability(song)

    suspend fun reloadLyricsByFormat(song: Song, preferTtml: Boolean): List<LyricLine> = lyricsManager.reloadLyricsByFormat(song, preferTtml)

    fun getReplayGain(song: Song, mode: Int = SettingsManager.REPLAY_GAIN_AUTO): Float? =
        audioInfoProvider.getReplayGain(song, mode)

    fun getAudioInfo(song: Song): AudioInfo = audioInfoProvider.getAudioInfo(song)

    fun getSongTagInfo(song: Song): SongTagInfo {
        val cacheKey = song.metadataCacheKey()
        tagInfoCache[cacheKey]?.let { return it }
        val info = runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo() ?: SongTagInfo()
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read tag info for ${song.path}", it)
            SongTagInfo()
        }
        tagInfoCache[cacheKey] = info
        return info
    }

    fun getCachedSongTagInfo(song: Song): SongTagInfo? =
        tagInfoCache[song.metadataCacheKey()]

    private fun resolveSongRatingFromTags(song: Song): Int =
        runCatching { getSongTagInfo(song).rating.coerceIn(0, 5) }
            .getOrElse {
                Log.w("MusicRepo", "Failed to resolve rating for ${song.path}", it)
                0
            }

    suspend fun songMatchesSearchSnapshot(
        song: Song,
        query: String,
        includeFullTags: Boolean = true
    ): Boolean = searchCoordinator.songMatchesSearchSnapshot(song, query, includeFullTags)

    suspend fun filterSongsBySearchSnapshot(
        songs: List<Song>,
        query: String,
        includeFullTags: Boolean = true
    ): List<Song> = searchCoordinator.filterSongsBySearchSnapshot(songs, query, includeFullTags)

    suspend fun getSongSearchText(song: Song): String =
        snapshotManager.getSongSearchText(song)

    suspend fun preloadLibrarySearchSnapshot(
        songs: List<Song>,
        refreshExisting: Boolean = false
    ) = snapshotManager.preloadSearchSnapshot(songs, refreshExisting)

    suspend fun preloadSongTagInfos(songs: List<Song>) = withContext(Dispatchers.IO) {
        songs.forEach(::getSongTagInfo)
    }

    suspend fun clearLibrarySnapshotCache() = withContext(Dispatchers.IO) {
        snapshotManager.clearLibraryCache()
    }

    fun getSongRating(song: Song): Int {
        snapshotManager.getFreshSongRating(song)?.let { return it }
        val resolved = resolveSongRatingFromTags(song)
        snapshotManager.updateRatingSnapshot(song, resolved, trustedLocalWrite = false)
        return resolved
    }

    suspend fun preloadSongRatings(songs: List<Song>) = withContext(Dispatchers.IO) {
        snapshotManager.preloadSongRatings(songs, ::resolveSongRatingFromTags)
        snapshotManager.saveAll()
    }

    suspend fun writeSongRating(song: Song, rating: Int): Result<Song?> = withContext(Dispatchers.IO) {
        val safeRating = rating.coerceIn(0, 5)
        val result = try {
            tagWriter.writeSongTags(
                song,
                AudioTagInfo(rating = safeRating)
            )
        } catch (e: SecurityException) {
            val sender = tagWriter.createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        tagWriter.writePermissionRequestIfNeeded(result, song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            snapshotManager.updateRatingSnapshot(immediate, safeRating)
            val refreshed = refreshSongAfterExternalEdit(immediate) ?: immediate
            snapshotManager.updateRatingSnapshot(refreshed, safeRating)
            snapshotManager.saveAll()
            refreshed
        }
    }

    suspend fun writeSongCustomTag(song: Song, key: String, value: String): Result<Song?> = withContext(Dispatchers.IO) {
        val tagKey = key.trim()
        if (tagKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag name is blank"))
        }
        val result = try {
            tagWriter.writeSongTags(
                song,
                AudioTagInfo(customTags = mapOf(tagKey to listOf(value)))
            )
        } catch (e: SecurityException) {
            val sender = tagWriter.createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        tagWriter.writePermissionRequestIfNeeded(result, song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    suspend fun writeSongMetadata(song: Song, tags: AudioTagInfo): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            tagWriter.writeSongTags(song, tags)
        } catch (e: SecurityException) {
            val sender = tagWriter.createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        tagWriter.writePermissionRequestIfNeeded(result, song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            val refreshed = refreshSongAfterExternalEdit(immediate) ?: immediate
            tags.rating?.let { rating ->
                snapshotManager.updateRatingSnapshot(refreshed, rating.coerceIn(0, 5))
                snapshotManager.saveAll()
            }
            refreshed
        }
    }

    suspend fun writeSongEmbeddedCover(song: Song, cover: AudioCoverInfo?): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            tagWriter.writeSongCover(song, cover)
        } catch (e: SecurityException) {
            val sender = tagWriter.createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        tagWriter.writePermissionRequestIfNeeded(result, song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    private suspend fun updateSongAfterLocalTagWrite(song: Song): Song = withContext(Dispatchers.IO) {
        clearMetadataCache(song)
        val updated = song.withCurrentFileSnapshot()
            .withRepositoryTags()
            .withCurrentFileSnapshot()
        val currentSongs = _songs.value
        if (currentSongs.isNotEmpty()) {
            val nextSongs = currentSongs.map { existing ->
                if (existing.id == song.id || existing.path == song.path) updated else existing
            }
            _songs.value = nextSongs
            _albums.value = nextSongs.toAlbums()
            libraryCacheStore.saveLibraryCache(nextSongs, _albums.value)
        }
        updated
    }

    private fun Song.withCurrentFileSnapshot(): Song {
        if (path.isHttpAudioSource()) return this
        val file = File(path)
        if (!file.exists()) return copy(dateModified = System.currentTimeMillis())
        return copy(
            fileSize = file.length().takeIf { it > 0L } ?: fileSize,
            dateModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    fun getFullAudioTagInfo(song: Song): AudioTagInfo? {
        return runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())
        }.getOrNull()
    }

    fun getCoverArt(song: Song): ByteArray? = coverArtManager.getCoverArt(song)

    fun getOriginalCoverModel(song: Song): Any? = coverArtManager.getOriginalCoverModel(song)

    fun getCoverArtBitmap(
        song: Song,
        maxSize: Int = 512,
        usage: CoverUsage = CoverUsage.ListThumbnail
    ): Bitmap? = coverArtManager.getCoverArtBitmap(song, maxSize, usage)

    fun getAlbumArtUri(albumId: Long): Uri? = coverArtManager.getAlbumArtUri(albumId)

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _songs.value
            .filter { it.albumIdentityId() == albumId }
            .sortedWith(
                compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                    .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
    }

    suspend fun deleteSongs(songs: Collection<Song>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        val deletedSongs = mutableListOf<Song>()
        val mediaStoreUrisNeedingPermission = mutableListOf<Uri>()

        songs.forEach { song ->
            if (tryDeleteSongDirect(song)) {
                deleted++
                deletedSongs += song
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                song.mediaStoreDeleteUriOrNull()?.let { mediaStoreUrisNeedingPermission += it }
            }
        }

        if (deletedSongs.isNotEmpty()) {
            removeDeletedSongsFromState(deletedSongs)
        }

        if (mediaStoreUrisNeedingPermission.isNotEmpty()) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUrisNeedingPermission.distinct())
            throw WritePermissionRequiredException(request.intentSender)
        }

        deleted
    }

    private fun tryDeleteSongDirect(song: Song): Boolean {
        if (song.onlineSource.isNotBlank()) return false
        val path = song.path.trim()
        if (path.isContentAudioSource()) {
            val uri = Uri.parse(path)
            val documentDeleted = runCatching {
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }.getOrDefault(false)
            if (documentDeleted) return true
            return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }

        val fileDeleted = runCatching {
            val file = File(path)
            file.exists() && file.delete()
        }.getOrDefault(false)
        if (fileDeleted) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && song.id > 0L) {
            return runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }

        return false
    }

    private fun Song.mediaStoreDeleteUriOrNull(): Uri? {
        if (onlineSource.isNotBlank() || id <= 0L) return null
        if (path.isContentAudioSource() && !path.isMediaStoreContentAudioSource()) {
            return null
        }
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    private suspend fun removeDeletedSongsFromState(deletedSongs: Collection<Song>) {
        val deletedKeys = deletedSongs.map { it.deleteIdentityKey() }.toSet()
        val deletedIds = deletedSongs.map { it.id }.filter { it > 0L }.toSet()
        _songs.value = _songs.value.filterNot { song ->
            (song.id > 0L && song.id in deletedIds) || song.deleteIdentityKey() in deletedKeys
        }
        _albums.value = _songs.value.toAlbums()
        libraryCacheStore.saveLibraryCache(_songs.value, _albums.value)
    }

    private fun Song.deleteIdentityKey(): String = "$id|$path"

    suspend fun removeSongsFromLibrary(songs: Collection<Song>): Unit = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        removeDeletedSongsFromState(songs)
        Unit
    }

    /** Rebuilds album identities after a grouping rule changes without scanning media again. */
    suspend fun rebuildAlbumAggregation() {
        val aggregationGeneration = albumAggregationGeneration.incrementAndGet()
        val currentSongs = _songs.value
        val rebuiltAlbums = withContext(Dispatchers.Default) {
            currentSongs.toAlbums()
        }
        // Settings flows emit immediately at startup. If their aggregation began while the
        // in-memory list was still empty, do not let that stale result overwrite the cache that
        // finished restoring in the meantime. The generation check also prevents an older
        // grouping-rule calculation from winning a later settings update for the same song list.
        if (albumAggregationGeneration.get() == aggregationGeneration && _songs.value === currentSongs) {
            _albums.value = rebuiltAlbums
        }
    }

    fun clearCache() {
        lyricsManager.clearCache()
        coverArtManager.clearCache()
        snapshotManager.clearCache()
        audioTagRepository.clearCache()
        audioInfoProvider.clearCache()
        tagInfoCache.clear()
    }

    private fun clearScanMetadataCaches() {
        lyricsManager.clearCache()
        coverArtManager.clearCache()
        audioTagRepository.clearCache()
        audioInfoProvider.clearCache()
        tagInfoCache.clear()
    }

    fun clearMetadataCache(song: Song) {
        lyricsManager.clearMetadataCache(song)
        coverArtManager.clearMetadataCache(song)
        snapshotManager.clearMetadataCache(song)
        val metadataPrefix = "${song.metadataCachePrefix()}:"
        audioInfoProvider.clearMetadataCache(metadataPrefix)
        tagInfoCache.removeKeysMatching { it.startsWith(metadataPrefix) || it.startsWith("${song.id}:") }
        audioTagRepository.clear(song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir))
        if (song.isWebDavRemoteSong()) {
            song.webDavHeaderCacheFile(remoteMetadataHeaderCacheDir).delete()
            song.webDavFullCacheFile(remoteAudioCacheDir).delete()
        }
    }

    fun clearRemoteMetadataCache() {
        clearCache()
        runCatching {
            if (remoteAudioCacheDir.exists()) {
                remoteAudioCacheDir.deleteRecursively()
            }
            if (remoteMetadataHeaderCacheDir.exists()) {
                remoteMetadataHeaderCacheDir.deleteRecursively()
            }
        }.onFailure {
            Log.w("MusicRepo", "Failed to clear online metadata cache", it)
        }
    }

    suspend fun resolveSongForPlayback(song: Song): Song = withContext(Dispatchers.IO) {
        runCatching {
            song.withRepositoryTags(allowFullDownload = song.isWebDavRemoteSong() && song.isLikelyWavAudio())
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to resolve playback song for ${song.path}", error)
            song
        }
    }

    suspend fun prefetchWebDavMetadataHeaders(songs: List<Song>, maxItems: Int = 80) = supervisorScope {
        val targets = songs
            .asSequence()
            .filter { it.isWebDavRemoteSong() }
            .distinctBy { it.path }
            .take(maxItems.coerceIn(1, 100))
            .toList()
        if (targets.isEmpty()) return@supervisorScope
        val config = loadWebDavConfig(settingsManager) ?: return@supervisorScope
        val semaphore = Semaphore(3)
        targets.forEach { song ->
            launch(Dispatchers.IO) {
                runCatching {
                    semaphore.withPermit {
                        val headerFile = song.webDavHeaderCacheFile(remoteMetadataHeaderCacheDir)
                        if (headerFile.exists() && headerFile.length() > 0L) {
                            Log.d("MusicRepo", "WebDAV header prefetch hit cache url=${song.path.webDavSafeLogUrl()}")
                            return@withPermit
                        }
                        Log.d("MusicRepo", "WebDAV header prefetch start url=${song.path.webDavSafeLogUrl()}")
                        val cached = downloadWebDavMetadataHeader(song, config, remoteMetadataHeaderCacheDir)
                        if (cached != null) {
                            Log.d("MusicRepo", "WebDAV header prefetch success url=${song.path.webDavSafeLogUrl()} bytes=${headerFile.length()}")
                        } else {
                            Log.d("MusicRepo", "WebDAV header prefetch skipped url=${song.path.webDavSafeLogUrl()}")
                        }
                    }
                }.onFailure { error ->
                    AppLogStore.warn(
                        context,
                        "MusicRepoWebDav",
                        "WebDAV header prefetch failed url=${song.path.webDavSafeLogUrl()}",
                        error,
                        AppLogType.NETWORK
                    )
                }
            }
        }
    }

    private fun Song.effectiveLocalPathForMetadata(allowFullDownload: Boolean = false): String {
        if (path.isContentAudioSource()) return path
        if (!isWebDavRemoteSong()) return path
        val fullCache = webDavFullCacheFile(remoteAudioCacheDir)
        if (fullCache.exists() && fullCache.length() > 0L) return fullCache.absolutePath
        val headerCache = webDavHeaderCacheFile(remoteMetadataHeaderCacheDir)
        if (headerCache.exists() && headerCache.length() > 0L) return headerCache.absolutePath
        val config = runBlocking(Dispatchers.IO) { loadWebDavConfig(settingsManager) } ?: return path
        downloadWebDavMetadataHeader(this, config, remoteMetadataHeaderCacheDir)?.let { return it.absolutePath }
        if (!allowFullDownload) return path
        return runCatching {
            WebDavClient.downloadToFile(path, config, fullCache).absolutePath
        }.getOrElse {
            Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
            path
        }
    }

    private fun List<Song>.toAlbums(): List<Album> {
        return LibraryAlbumAggregator.toAlbums(this)
    }

    private fun Song.withRepositoryTags(allowFullDownload: Boolean = false): Song {
        val metadataPath = effectiveLocalPathForMetadata(allowFullDownload)
        val tagInfo = runCatching {
            audioTagRepository.readTagsBlocking(metadataPath)
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to refresh library tags for $path", error)
            null
        }
        val wavMetadata = runCatching { WavMetadataReader.read(metadataPath) }
            .getOrNull()

        val mergedArtist = tagInfo?.artist.takeIf { it.isUsableArtistText() }
            ?: wavMetadata?.artist.takeIf { it.isUsableArtistText() }
            ?: artist.takeIf { it.isUsableArtistText() }
            ?: "Unknown Artist"
        val mergedAlbum = tagInfo?.album.takeIf { it.isUsableAlbumText() }
            ?: wavMetadata?.album.takeIf { it.isUsableAlbumText() }
            ?: album.takeIf { it.isUsableAlbumText() }
            ?: "Unknown Album"
        val mergedAlbumArtist = tagInfo?.albumArtist.takeIf { it.isUsableArtistText() }
            ?: wavMetadata?.albumArtist.takeIf { it.isUsableArtistText() }
            ?: albumArtist.takeIf { it.isUsableArtistText() }
            ?: ""

        return copy(
            title = tagInfo?.title.takeIf { it.isUsableTagText() }
                ?: wavMetadata?.title.takeIf { it.isUsableTagText() }
                ?: title.takeIf { it.isUsableTagText() }
                ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = mergedArtist,
            album = mergedAlbum,
            albumArtist = mergedAlbumArtist,
            genre = tagInfo?.genre.takeIf { it.isUsableTagText() } ?: wavMetadata?.genre.takeIf { it.isUsableTagText() } ?: genre,
            year = tagInfo?.year.takeIf { it.isUsableTagText() } ?: wavMetadata?.year.takeIf { it.isUsableTagText() } ?: year,
            composer = tagInfo?.composer.takeIf { it.isUsableTagText() } ?: wavMetadata?.composer.takeIf { it.isUsableTagText() } ?: composer,
            arranger = tagInfo?.arranger.takeIf { it.isUsableTagText() } ?: wavMetadata?.arranger.takeIf { it.isUsableTagText() } ?: arranger,
            lyricist = tagInfo?.lyricist.takeIf { it.isUsableTagText() } ?: wavMetadata?.lyricist.takeIf { it.isUsableTagText() } ?: lyricist,
            trackNumber = tagInfo?.trackNumber ?: wavMetadata?.trackNumber ?: trackNumber,
            discNumber = tagInfo?.discNumber ?: wavMetadata?.discNumber ?: discNumber
        ).withFinalLibraryFallbacks()
    }

    private fun Song.withFinalLibraryFallbacks(): Song {
        val fallbackArtist = artist.takeIf { it.isUsableArtistText() } ?: "Unknown Artist"
        val fallbackAlbum = album.takeIf { it.isUsableAlbumText() }
            ?: "Unknown Album"
        return copy(
            title = title.takeIf { it.isUsableTagText() } ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = fallbackArtist,
            album = fallbackAlbum,
            albumArtist = albumArtist.takeIf { it.isUsableArtistText() }.orEmpty()
        )
    }

    private fun Song.needsMetadataPlaceholderRefresh(): Boolean =
        LibraryNormalizer.isGeneratedUnknownArtistPlaceholder(artist) ||
            LibraryNormalizer.isGeneratedUnknownAlbumPlaceholder(album) ||
            (album.isUsableAlbumText() && album.looksLikeLastFolderName(path))

    private suspend fun scanEditedFile(song: Song) = suspendCoroutine<Unit> { continuation ->
        val path = song.path.takeIf { it.isNotBlank() }
        if (path == null || path.isContentAudioSource()) {
            continuation.resume(Unit)
            return@suspendCoroutine
        }
        val mimeTypes = song.mimeType.takeIf { it.isNotBlank() }?.let { arrayOf(it) }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            mimeTypes,
        ) { _, _ ->
            continuation.resume(Unit)
        }
    }

    private fun querySystemSong(song: Song): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK
        )
        val uri = if (song.id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selection = if (song.id > 0L) null else "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = if (song.id > 0L) null else arrayOf(song.path)

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val tagInfo = runCatching {
                audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo()
            }.getOrNull() ?: SongTagInfo()
            val wavInfo = runCatching { WavMetadataReader.read(song.effectiveLocalPathForMetadata()) }
                .getOrNull()
            val path = cursor.getString(6).orEmpty().ifBlank { song.path }
            val file = File(path)
            val fileSize = file.length().takeIf { file.exists() && it > 0L }
                ?: cursor.getLong(8)
            val dateModified = file.lastModified().takeIf { file.exists() && it > 0L }
                ?: (cursor.getLong(11) * 1000L).takeIf { it > 0L }
                ?: song.dateModified
            Song(
                id = cursor.getLong(0),
                title = tagInfo.title.usableTagText().ifBlank {
                    wavInfo?.title.usableTagText().ifBlank {
                        cursor.getString(1)?.usableTagText().orEmpty().ifBlank { song.title }
                    }
                },
                artist = tagInfo.artist.usableArtistText().ifBlank {
                    wavInfo?.artist.usableArtistText().ifBlank {
                        cursor.getString(2)?.usableArtistText().orEmpty().ifBlank { song.artist }
                    }
                },
                album = tagInfo.album.usableAlbumText().ifBlank {
                    wavInfo?.album.usableAlbumText().ifBlank {
                        cursor.getString(3)?.usableAlbumText().orEmpty().ifBlank { song.album }
                    }
                },
                albumId = cursor.getLong(4),
                duration = cursor.getLong(5).takeIf { it > 0L } ?: song.duration,
                path = path,
                fileName = cursor.getString(7).orEmpty().ifBlank { song.fileName },
                fileSize = fileSize,
                mimeType = cursor.getString(9).orEmpty().ifBlank { song.mimeType },
                dateAdded = cursor.getLong(10) * 1000L,
                dateModified = dateModified,
                trackNumber = tagInfo.track.takeIf { it.isNotBlank() }?.toIntOrNull()
                    ?: wavInfo?.trackNumber
                    ?: cursor.getInt(12).let { if (it > 1000) it % 1000 else it },
                discNumber = wavInfo?.discNumber
                    ?: cursor.getInt(12).let { if (it >= 1000) it / 1000 else song.discNumber },
                albumArtist = tagInfo.albumArtist.usableArtistText().ifBlank {
                    wavInfo?.albumArtist.usableArtistText().ifBlank { song.albumArtist }
                },
                genre = tagInfo.genre.ifBlank { wavInfo?.genre.orEmpty().ifBlank { song.genre } },
                year = tagInfo.year.ifBlank { wavInfo?.year.orEmpty().ifBlank { song.year } },
                composer = tagInfo.composer.ifBlank { wavInfo?.composer.orEmpty().ifBlank { song.composer } },
                arranger = tagInfo.arranger.ifBlank { wavInfo?.arranger.orEmpty().ifBlank { song.arranger } },
                lyricist = tagInfo.lyricist.ifBlank { wavInfo?.lyricist.orEmpty().ifBlank { song.lyricist } },
                coverUrl = song.coverUrl,
                onlineSource = song.onlineSource,
                onlineId = song.onlineId,
                onlineLyrics = song.onlineLyrics,
                onlineLyricTranslation = song.onlineLyricTranslation
            ).withFinalLibraryFallbacks()
        }
    }

    private fun Song.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun MediaStoreAudioItem.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun Song.hasExistingLocalFile(): Boolean {
        if (path.isBlank() || path.isContentAudioSource() || path.isHttpAudioSource()) return false
        return runCatching { File(path).isFile }.getOrDefault(false)
    }

    private fun Song.scanSummaryKey(): String = path.ifBlank { librarySyncKey() }

    private fun Song.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun MediaStoreAudioItem.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun Song.toScanSummaryInfo(): ScanSummaryInfo =
        ScanSummaryInfo(
            key = scanSummaryKey(),
            fileSize = fileSize,
            dateModified = dateModified,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber
        )

    private data class LibrarySyncInfo(
        val key: String,
        val path: String,
        val fileSize: Long,
        val dateModified: Long
    )

    private data class ScanSummaryInfo(
        val key: String,
        val fileSize: Long,
        val dateModified: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val year: String,
        val trackNumber: Int,
        val discNumber: Int
    )
}
