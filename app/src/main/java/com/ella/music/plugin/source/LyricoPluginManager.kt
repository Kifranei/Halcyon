package com.ella.music.plugin.source

import android.content.Context
import android.net.Uri
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LyricoPluginManager(
    context: Context,
    private val settingsManager: SettingsManager = SettingsManager.getInstance(context)
) {
    private val appContext = context.applicationContext
    private val customStore = CustomPluginStore(appContext)
    private val configStore = PluginConfigStore(appContext)

    suspend fun availableSources(): List<LyricoPluginSource> = customStore.loadPlugins()

    suspend fun importPluginZip(uri: Uri) = customStore.importPluginZip(uri)

    suspend fun deletePlugin(id: String): Boolean {
        val deleted = customStore.deletePlugin(id)
        if (deleted) configStore.deleteConfig(id)
        return deleted
    }

    fun pluginConfig(source: LyricoPluginSource): Map<String, String> =
        configStore.loadConfig(source.manifest)

    fun setPluginConfigValue(pluginId: String, key: String, value: String) {
        configStore.setValue(pluginId, key, value)
    }

    suspend fun enabledSources(): List<LyricoPluginSource> {
        val sources = availableSources()
        val enabledIds = settingsManager.lyricoPluginEnabledIds.first()
        return sources.filter { it.manifest.id in enabledIds }
    }

    suspend fun searchSongs(keyword: String, pageSizePerSource: Int = 8): List<PluginSearchHit> = withContext(Dispatchers.IO) {
        // Query every enabled source in parallel so total search time is bounded by the slowest
        // responsive source, not the sum of all of them; a single slow/dead source (capped by the
        // plugin HTTP client's call timeout) can no longer stall the whole search.
        coroutineScope {
            enabledSources().map { source ->
                async {
                    ScriptSearchSource(source, configStore.loadConfig(source.manifest)).use { runtime ->
                        runtime.searchSongs(keyword, pageSize = pageSizePerSource).map { result ->
                            PluginSearchHit(source.manifest.id, source.manifest.name, result)
                        }
                    }
                }
            }.awaitAll().flatten()
        }
    }

    suspend fun searchCovers(keyword: String, pageSizePerSource: Int = 5): List<PluginSearchHit> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                enabledSources().map { source ->
                    async {
                        ScriptSearchSource(source, configStore.loadConfig(source.manifest)).use { runtime ->
                            runtime.searchCovers(keyword, pageSize = pageSizePerSource).map { result ->
                                PluginSearchHit(source.manifest.id, source.manifest.name, result)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
        }

    suspend fun searchCovers(
        hit: PluginSearchHit,
        page: Int = 1,
        pageSize: Int = 5
    ): List<PluginSearchHit> = withContext(Dispatchers.IO) {
        val source = availableSources().firstOrNull { it.manifest.id == hit.sourceId }
            ?: return@withContext emptyList()
        ScriptSearchSource(source, configStore.loadConfig(source.manifest)).use { runtime ->
            runtime.searchCovers(hit.song, page = page, pageSize = pageSize).map { result ->
                PluginSearchHit(source.manifest.id, source.manifest.name, result)
            }
        }
    }

    suspend fun getLyrics(hit: PluginSearchHit): PluginLyricsResult? = withContext(Dispatchers.IO) {
        val source = availableSources().firstOrNull { it.manifest.id == hit.sourceId } ?: return@withContext null
        ScriptSearchSource(source, configStore.loadConfig(source.manifest)).use { runtime -> runtime.getLyrics(hit.song) }
    }

    companion object {
        fun normalizeEnabledIds(raw: String?): Set<String> =
            raw.orEmpty()
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
    }
}
