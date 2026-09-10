package com.ella.music.data.scanner

import android.content.Context
import android.util.Log
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

sealed class TwoStageScanEvent {
    data class Started(val totalEstimated: Int) : TwoStageScanEvent()
    data class QuickProgress(val scanned: Int, val total: Int, val message: String = "读取媒体库") : TwoStageScanEvent()
    data class QuickCompleted(val songs: List<Song>, val timeMs: Long) : TwoStageScanEvent()
    data class EnrichProgress(
        val processed: Int,
        val total: Int,
        val percent: Int,
        val cacheHits: Int,
        val enrichedCount: Int,
        val message: String = "补全音频信息"
    ) : TwoStageScanEvent()
    data class EnrichBatchCompleted(
        val enrichedBatch: List<Song>,
        val processed: Int,
        val total: Int,
        val cacheHits: Int,
        val enrichedCount: Int
    ) : TwoStageScanEvent()
    data class FullyCompleted(
        val allSongs: List<Song>,
        val totalTimeMs: Long,
        val cacheHits: Int,
        val enrichedCount: Int
    ) : TwoStageScanEvent()
    data class Error(val message: String) : TwoStageScanEvent()
}

internal object TwoStageScanCoordinator {

    private const val TAG = "TwoStageScan"
    const val DEFAULT_ENRICH_BATCH_SIZE = 64

    fun scan(
        context: Context,
        scanner: MusicScanner,
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        filesystemFallbackFolders: List<String> = includeFolders,
        filterVideoFiles: Boolean = true,
        refreshMediaStore: Boolean = false,
        deepMetadataEnabled: Boolean = true,
        forceClearCache: Boolean = false,
        previousSongs: List<Song> = emptyList(),
        workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
        batchSize: Int = DEFAULT_ENRICH_BATCH_SIZE,
        enrichSong: suspend (Song) -> Song
    ): Flow<TwoStageScanEvent> = flow {
        val startTime = System.currentTimeMillis()
        val appContext = context.applicationContext
        val cache = PersistentMetadataCache.load(appContext)
        if (forceClearCache) {
            cache.clear()
        }

        emit(TwoStageScanEvent.Started(0))
        emit(TwoStageScanEvent.QuickProgress(0, 0, "正在读取媒体库文件"))

        // --- STAGE 1: Quick Scan ---
        val items = try {
            scanner.enumerateAudioFiles(
                includeFolders = includeFolders,
                excludeFolders = excludeFolders,
                filesystemFallbackFolders = filesystemFallbackFolders,
                filterVideoFiles = filterVideoFiles,
                refreshMediaStore = refreshMediaStore
            )
        } catch (e: Exception) {
            Log.w(TAG, "enumerateAudioFiles failed", e)
            emit(TwoStageScanEvent.Error(e.message ?: "Failed to enumerate audio files"))
            return@flow
        }

        val totalItems = items.size
        emit(TwoStageScanEvent.QuickProgress(totalItems, totalItems, "媒体库文件读取完成"))

        val quickSongs = ArrayList<Song>(totalItems)
        val seenPaths = HashSet<String>(totalItems)
        val previousByPath = if (forceClearCache) emptyMap() else previousSongs.associateBy { it.path.trim().lowercase() }
        var cacheHits = 0

        for (item in items) {
            coroutineContext.ensureActive()
            if (!item.path.isAllowedByFolderFilters(includeFolders, excludeFolders)) continue
            val normalizedPath = item.path.trim().lowercase()
            if (normalizedPath.isBlank() || !seenPaths.add(normalizedPath)) continue

            val shallow = item.toShallowSong(minDurationMs)
                ?: if (item.duration in 1 until minDurationMs) null
                else Song(
                    id = item.id,
                    title = LibraryNormalizer.cleanedTagText(item.title).ifBlank {
                        item.fileName.substringBeforeLast('.').ifBlank { item.path.substringAfterLast('/') }
                    },
                    artist = LibraryNormalizer.cleanedArtistText(item.artist).ifBlank { "Unknown Artist" },
                    album = LibraryNormalizer.cleanedAlbumText(item.album).ifBlank { "Unknown Album" },
                    albumId = item.albumId,
                    duration = item.duration.coerceAtLeast(0L),
                    path = item.path,
                    fileName = item.fileName,
                    fileSize = item.fileSize.coerceAtLeast(0L),
                    mimeType = item.mimeType,
                    dateAdded = item.dateAdded,
                    dateModified = item.dateModified,
                    trackNumber = item.trackNumber,
                    discNumber = item.discNumber
                )

            if (shallow != null) {
                // 1. Check persistent metadata cache
                val cached = cache.get(shallow)
                if (cached != null) {
                    quickSongs.add(cached)
                    cacheHits++
                } else {
                    // 2. Check previousSongs migration
                    val prev = previousByPath[normalizedPath]
                    if (prev != null &&
                        prev.fileSize == shallow.fileSize &&
                        prev.dateModified == shallow.dateModified &&
                        !prev.needsMetadataPlaceholderRefresh()
                    ) {
                        val migrated = prev.copy(
                            id = shallow.id,
                            albumId = shallow.albumId,
                            fileName = shallow.fileName.ifBlank { prev.fileName },
                            mimeType = shallow.mimeType.ifBlank { prev.mimeType },
                            dateAdded = if (shallow.dateAdded > 0L) shallow.dateAdded else prev.dateAdded
                        )
                        cache.put(migrated)
                        quickSongs.add(migrated)
                        cacheHits++
                    } else {
                        quickSongs.add(shallow)
                    }
                }
            }
        }

        val quickTimeMs = System.currentTimeMillis() - startTime
        Log.i(TAG, "Quick scan completed: found=${quickSongs.size} cacheHits=$cacheHits in ${quickTimeMs}ms")
        emit(TwoStageScanEvent.QuickCompleted(quickSongs, quickTimeMs))

        // If deep metadata is disabled or no songs found, finish right away
        if (!deepMetadataEnabled || quickSongs.isEmpty()) {
            emit(TwoStageScanEvent.FullyCompleted(quickSongs, System.currentTimeMillis() - startTime, cacheHits, 0))
            return@flow
        }

        // --- STAGE 2: Background Multi-threaded Tag Enrichment ---
        val songsToEnrich = quickSongs.filter { song ->
            !cache.contains(song) || song.needsMetadataPlaceholderRefresh()
        }

        if (songsToEnrich.isEmpty()) {
            Log.i(TAG, "All songs satisfied from cache or complete, no enrichment needed")
            emit(TwoStageScanEvent.FullyCompleted(quickSongs, System.currentTimeMillis() - startTime, cacheHits, 0))
            return@flow
        }

        val totalToEnrich = songsToEnrich.size
        var processed = 0
        var enrichedCount = 0
        var dirtyCacheCount = 0
        var lastCacheSaveMs = System.currentTimeMillis()
        val semaphore = Semaphore(workerCount)
        val finalMap = quickSongs.associateBy { it.path }.toMutableMap()

        Log.i(TAG, "Starting enrichment: totalToEnrich=$totalToEnrich workers=$workerCount batchSize=$batchSize")

        for (chunk in songsToEnrich.chunked(batchSize)) {
            coroutineContext.ensureActive()
            val chunkResults = coroutineScope {
                chunk.map { song ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            val enriched = runCatching { enrichSong(song) }.getOrDefault(song)
                            cache.put(enriched)
                            enriched
                        }
                    }
                }.awaitAll()
            }

            for (enriched in chunkResults) {
                finalMap[enriched.path] = enriched
                processed++
                enrichedCount++
                dirtyCacheCount++
            }

            if (dirtyCacheCount >= 256 && System.currentTimeMillis() - lastCacheSaveMs >= 10_000L) {
                cache.save()
                dirtyCacheCount = 0
                lastCacheSaveMs = System.currentTimeMillis()
            }

            emit(
                TwoStageScanEvent.EnrichBatchCompleted(
                    enrichedBatch = chunkResults,
                    processed = processed,
                    total = totalToEnrich,
                    cacheHits = cacheHits,
                    enrichedCount = enrichedCount
                )
            )

            val pct = ((processed.toFloat() / totalToEnrich) * 100f).roundToInt().coerceIn(0, 100)
            emit(
                TwoStageScanEvent.EnrichProgress(
                    processed = processed,
                    total = totalToEnrich,
                    percent = pct,
                    cacheHits = cacheHits,
                    enrichedCount = enrichedCount,
                    message = "正在补全音频标签: $processed / $totalToEnrich"
                )
            )
        }

        cache.save()
        val allFinalSongs = quickSongs.map { finalMap[it.path] ?: it }
        val totalTimeMs = System.currentTimeMillis() - startTime
        Log.i(TAG, "TwoStageScan completed: total=${allFinalSongs.size} cacheHits=$cacheHits enriched=$enrichedCount time=${totalTimeMs}ms")
        emit(
            TwoStageScanEvent.FullyCompleted(
                allSongs = allFinalSongs,
                totalTimeMs = totalTimeMs,
                cacheHits = cacheHits,
                enrichedCount = enrichedCount
            )
        )
    }.flowOn(Dispatchers.IO)

    internal fun Song.needsMetadataPlaceholderRefresh(): Boolean =
        LibraryNormalizer.isGeneratedUnknownArtistPlaceholder(artist) ||
            LibraryNormalizer.isGeneratedUnknownAlbumPlaceholder(album) ||
            (album.isNotBlank() && album.looksLikeLastFolderName(path))

    internal fun String.looksLikeLastFolderName(filePath: String): Boolean {
        val folder = filePath.substringBeforeLast('/', "").substringAfterLast('/')
        return folder.isNotBlank() && equals(folder, ignoreCase = true)
    }
}
