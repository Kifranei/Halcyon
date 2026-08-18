package com.ella.music.plugin.source

import android.util.Log
import com.ella.music.plugin.model.PluginCapability
import com.ella.music.plugin.model.defaultValueString
import com.ella.music.plugin.runtime.PluginJsRuntime
import com.ella.music.plugin.runtime.QuickJsHostApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class ScriptSearchSource(
    private val source: LyricoPluginSource,
    configOverrides: Map<String, String> = emptyMap()
) : AutoCloseable {
    val id: String = source.manifest.id
    val name: String = source.manifest.name
    private val capabilities = source.manifest.capabilities.ifEmpty { setOf(PluginCapability.SEARCH_SONGS) }
    private val config = source.manifest.configFields
        .associate { it.key to it.defaultValueString() }
        .plus(configOverrides)
    private val parser = PluginJsonParser(pluginJson)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, "Halcyon-LyricoPlugin-$id", 4L * 1024L * 1024L)
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val runtimeDelegate = lazy {
        PluginJsRuntime.ensureLoaded()
        PluginJsRuntime(
            hostApi = QuickJsHostApi(
                pluginId = id,
                cacheRootDir = source.cacheRootDir
            )
        ).also { it.eval(source.script, source.manifest.entry) }
    }
    private val runtime: PluginJsRuntime by runtimeDelegate

    suspend fun searchSongs(keyword: String, page: Int = 1, pageSize: Int = 20): List<PluginSongSearchResult> =
        withContext(dispatcher) {
            if (PluginCapability.SEARCH_SONGS !in capabilities) return@withContext emptyList()
            runCatching {
                val request = PluginSearchSongsRequest(keyword = keyword, page = page, pageSize = pageSize, config = config)
                parser.parseSongResults(
                    rawJson = runtime.call("searchSongs", pluginJson.encodeToString(request)),
                    pluginId = id,
                    pluginName = name
                )
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "searchSongs failed for $id", throwable)
                emptyList()
            }
        }

    suspend fun getLyrics(song: PluginSongSearchResult): PluginLyricsResult? =
        getLyricsCandidates(song).firstOrNull()?.lyrics

    suspend fun getLyricsCandidates(
        song: PluginSongSearchResult,
        page: Int = 1,
        pageSize: Int = 20
    ): List<PluginLyricsCandidateResult> = withContext(dispatcher) {
        if (PluginCapability.GET_LYRICS !in capabilities) return@withContext emptyList()
        runCatching {
            val request = PluginGetLyricsRequest(
                song = song.toPluginSongRequest(),
                page = page,
                pageSize = pageSize,
                config = config
            )
            val raw = runtime.call("getLyrics", pluginJson.encodeToString(request))
            parser.parseLyricsCandidates(
                rawJson = raw,
                pluginId = id,
                pluginName = name,
                fallbackSong = song,
                enforceApi4Contract = source.manifest.apiVersion >= 4
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "getLyrics failed for $id", throwable)
            emptyList()
        }
    }

    suspend fun searchCovers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 5
    ): List<PluginSongSearchResult> = searchCoversInternal(
        keyword = keyword,
        song = null,
        page = page,
        pageSize = pageSize
    )

    suspend fun searchCovers(
        song: PluginSongSearchResult,
        page: Int = 1,
        pageSize: Int = 5
    ): List<PluginSongSearchResult> = searchCoversInternal(
        keyword = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
        song = song,
        page = page,
        pageSize = pageSize
    )

    private suspend fun searchCoversInternal(
        keyword: String,
        song: PluginSongSearchResult?,
        page: Int,
        pageSize: Int
    ): List<PluginSongSearchResult> = withContext(dispatcher) {
        if (PluginCapability.SEARCH_COVERS !in capabilities) return@withContext emptyList()
        runCatching {
            val request = PluginSearchCoversRequest(
                keyword = keyword,
                song = song?.toPluginSongRequest(),
                page = page,
                pageSize = pageSize,
                config = config
            )
            parser.parseCoverResults(
                rawJson = runtime.call("searchCovers", pluginJson.encodeToString(request)),
                pluginId = id,
                pluginName = name,
                enforceApi4Contract = source.manifest.apiVersion >= 4
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "searchCovers failed for $id", throwable)
            emptyList()
        }
    }

    override fun close() {
        runCatching {
            if (runtimeDelegate.isInitialized()) {
                executor.submit { runtime.close() }.get(3, TimeUnit.SECONDS)
            }
        }
        dispatcher.close()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "LyricoPlugin"
    }
}

data class PluginSearchHit(
    val sourceId: String,
    val sourceName: String,
    val song: PluginSongSearchResult
)
