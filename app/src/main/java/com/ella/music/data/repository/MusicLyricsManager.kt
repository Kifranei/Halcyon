package com.ella.music.data.repository

import android.content.Context
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.parser.EllaLyricsParser
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal class MusicLyricsManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val audioTagRepository: AudioTagRepository,
    private val httpClient: OkHttpClient,
    private val remoteAudioCacheDir: File,
    private val remoteMetadataHeaderCacheDir: File
) {
    private val lyricsCache = ConcurrentHashMap<String, List<LyricLine>>()
    private val lyricFormatAvailabilityCache = ConcurrentHashMap<String, MusicRepository.LyricFormatAvailability>()
    private val remoteSidecarLyricsDir = File(remoteMetadataHeaderCacheDir, "sidecar_lyrics")

    suspend fun getLyrics(
        song: Song,
        sourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        val sourcePriority = settingsManager.lyricSourcePriority.first()
        val ignoreHeaderTags = settingsManager.ignoreLyricHeaderTags.first()
        val cacheKey = "${song.metadataCacheKey()}:lyrics:$safeMode:$sourcePriority:$ignoreHeaderTags"
        lyricsCache[cacheKey]?.let { cached ->
            // A WebDAV song can be requested before its cancellable metadata window arrives. Do
            // not let that transient empty result suppress the later retry after hydration.
            if (cached.isNotEmpty() || !song.isWebDavRemoteSong() || song.hasWebDavMetadataCache()) {
                return@withContext cached
            }
            lyricsCache.remove(cacheKey)
        }

        if (safeMode == SettingsManager.LYRIC_SOURCE_AUTO) {
            fetchOnlineLyrics(song, ignoreHeaderTags)?.let { onlineLyrics ->
                lyricsCache[cacheKey] = onlineLyrics
                return@withContext onlineLyrics
            }
        }

        val effectivePath = song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
        for (sourceId in orderedLyricSourceIds(sourcePriority, safeMode)) {
            loadLyricsBySourceId(song, effectivePath, sourceId, ignoreHeaderTags)?.let { lyrics ->
                lyricsCache[cacheKey] = lyrics
                return@withContext lyrics
            }
        }

        if (!song.isWebDavRemoteSong() || song.hasWebDavMetadataCache()) {
            lyricsCache[cacheKey] = emptyList()
        }
        emptyList()
    }

    suspend fun reloadLyrics(song: Song, sourceMode: Int): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        val metadataPrefix = "${song.metadataCachePrefix()}:"
        lyricsCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        lyricFormatAvailabilityCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        getLyrics(song, safeMode)
    }

    suspend fun getLyricFormatAvailability(song: Song): MusicRepository.LyricFormatAvailability = withContext(Dispatchers.IO) {
        val cacheKey = "${song.metadataCacheKey()}:availability"
        lyricFormatAvailabilityCache[cacheKey]?.let { cached ->
            // Do not retain a temporary "no lyrics" result for a WebDAV song while its
            // cancellable metadata header is still being hydrated.
            if (cached.hasAnyFormat() || !song.isWebDavRemoteSong() || song.hasWebDavMetadataCache()) {
                return@withContext cached
            }
            lyricFormatAvailabilityCache.remove(cacheKey)
        }
        val effectivePath = song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
        val ignoreHeaderTags = settingsManager.ignoreLyricHeaderTags.first()
        val ttml = loadExternalLyricsByFormat(song, effectivePath, preferTtml = true)
            ?: loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = true, ignoreHeaderTags = ignoreHeaderTags)
        val plain = loadExternalLyricsByFormat(song, effectivePath, preferTtml = false)
            ?: loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = false, ignoreHeaderTags = ignoreHeaderTags)
        MusicRepository.LyricFormatAvailability(hasTtml = !ttml.isNullOrEmpty(), hasPlain = !plain.isNullOrEmpty())
            .also { availability ->
                if (!song.isWebDavRemoteSong() || availability.hasAnyFormat() || song.hasWebDavMetadataCache()) {
                    lyricFormatAvailabilityCache[cacheKey] = availability
                }
            }
    }

    suspend fun reloadLyricsByFormat(song: Song, preferTtml: Boolean): List<LyricLine> = withContext(Dispatchers.IO) {
        val sourcePriority = settingsManager.lyricSourcePriority.first()
        val ignoreHeaderTags = settingsManager.ignoreLyricHeaderTags.first()
        val cacheKey = "${song.metadataCacheKey()}:format:$preferTtml:$sourcePriority:$ignoreHeaderTags"
        lyricsCache.remove(cacheKey)
        lyricFormatAvailabilityCache.removeKeysMatching { it.startsWith("${song.metadataCachePrefix()}:") }
        val effectivePath = song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
        var lyrics: List<LyricLine>? = null
        for (sourceId in orderedLyricSourceIds(sourcePriority, SettingsManager.LYRIC_SOURCE_AUTO).filter { id ->
                if (preferTtml) {
                    id == SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML || id == SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML
                } else {
                    id == SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN || id == SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN
                }
            }) {
            if (lyrics == null) {
                lyrics = loadLyricsBySourceId(song, effectivePath, sourceId, ignoreHeaderTags)
            }
        }
        val resolvedLyrics = lyrics ?: emptyList()
        if (!song.isWebDavRemoteSong() || resolvedLyrics.isNotEmpty() || song.hasWebDavMetadataCache()) {
            lyricsCache[cacheKey] = resolvedLyrics
        }
        resolvedLyrics
    }

    fun clearCache() {
        lyricsCache.clear()
        lyricFormatAvailabilityCache.clear()
    }

    fun clearMetadataCache(song: Song) {
        val metadataPrefix = "${song.metadataCachePrefix()}:"
        lyricsCache.removeKeysMatching { it.startsWith(metadataPrefix) || it.startsWith("${song.id}:") }
        lyricFormatAvailabilityCache.removeKeysMatching { it.startsWith(metadataPrefix) || it.startsWith("${song.id}:") }
    }

    private fun orderedLyricSourceIds(priority: String, sourceMode: Int): List<String> {
        val ordered = SettingsManager.normalizeLyricSourcePriority(priority)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return when (sourceMode) {
            SettingsManager.LYRIC_SOURCE_EXTERNAL -> ordered.filter {
                it == SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML || it == SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN
            }
            SettingsManager.LYRIC_SOURCE_EMBEDDED -> ordered.filter {
                it == SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML || it == SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN
            }
            else -> ordered
        }
    }

    private suspend fun loadLyricsBySourceId(
        song: Song, effectivePath: String, sourceId: String, ignoreHeaderTags: Boolean
    ): List<LyricLine>? {
        return when (sourceId) {
            SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML ->
                loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = true, ignoreHeaderTags = ignoreHeaderTags)
            SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN ->
                loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = false, ignoreHeaderTags = ignoreHeaderTags)
            SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML ->
                loadExternalLyricsByFormat(song, effectivePath, preferTtml = true, ignoreHeaderTags = ignoreHeaderTags)
            SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN ->
                loadExternalLyricsByFormat(song, effectivePath, preferTtml = false, ignoreHeaderTags = ignoreHeaderTags)
            else -> null
        }
    }

    private suspend fun loadExternalLyricsByFormat(song: Song, effectivePath: String, preferTtml: Boolean, ignoreHeaderTags: Boolean = false): List<LyricLine>? {
        val content = findExternalLyricContentByFormat(effectivePath, preferTtml)
            ?: findWebDavExternalLyricContent(song, preferTtml)
            ?: return null
        val parsed = LrcParser.parse(content, ignoreHeaderTags)
        val lyrics = parsed.lyrics.takeIf { it.isNotEmpty() } ?: return null
        return lyrics.takeIf { lines -> lines.any { it.isTtml } == preferTtml }
    }

    private suspend fun findWebDavExternalLyricContent(song: Song, preferTtml: Boolean): String? =
        withContext(Dispatchers.IO) {
            if (!song.isWebDavRemoteSong()) return@withContext null
            val config = loadWebDavConfig(settingsManager) ?: return@withContext null
            val parentUrl = runCatching {
                val uri = URI(song.path)
                val parentPath = uri.path.orEmpty().substringBeforeLast('/', missingDelimiterValue = "")
                URI(uri.scheme, uri.userInfo, uri.host, uri.port, "$parentPath/", null, null).toString()
            }.getOrNull() ?: return@withContext null
            val extensions = if (preferTtml) listOf("ttml") else listOf("lrc", "elrc")
            val songName = song.fileName.substringBeforeLast('.').ifBlank {
                URI(song.path).path.orEmpty().substringAfterLast('/').substringBeforeLast('.')
            }
            val sidecar = runCatching {
                WebDavClient.list(config, parentUrl, includeNonAudioFiles = true)
                    .asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in extensions }
                    .sortedWith(compareBy<WebDavItem> { extensions.indexOf(it.name.substringAfterLast('.', "").lowercase()) }.thenBy { it.name })
                    .firstOrNull {
                        val sidecarStem = it.name.substringBeforeLast('.')
                        sidecarStem.equals(songName, ignoreCase = true) ||
                            sidecarStem.startsWith(songName, ignoreCase = true) ||
                            songName.startsWith(sidecarStem, ignoreCase = true)
                    }
            }.getOrNull() ?: return@withContext null
            val extension = sidecar.name.substringAfterLast('.', "txt").lowercase()
            val cacheFile = File(remoteSidecarLyricsDir, "${song.path.sha256()}_${sidecar.url.sha256()}.$extension")
            if (!cacheFile.exists() || cacheFile.length() <= 0L) {
                runCatching { WebDavClient.downloadToFile(sidecar.url, config, cacheFile) }.getOrNull() ?: return@withContext null
            }
            readTextIfExists(cacheFile.absolutePath)
        }

    private fun loadEmbeddedLyricsByFormat(
        song: Song, effectivePath: String, preferTtml: Boolean, ignoreHeaderTags: Boolean
    ): List<LyricLine>? {
        val embedded = audioTagRepository.readTagsBlocking(effectivePath)
            ?.embeddedLyricsContent(preferTtml = preferTtml) ?: return null
        val parsed = parseEmbeddedLyrics(song, embedded, ignoreHeaderTags) ?: return null
        return parsed.takeIf { lines -> lines.any { it.isTtml } == preferTtml }
    }

    private fun parseEmbeddedLyrics(song: Song, embedded: String, ignoreHeaderTags: Boolean): List<LyricLine>? {
        val parsed = LrcParser.parse(embedded, ignoreHeaderTags)
        if (parsed.lyrics.isNotEmpty()) {
            return parsed.lyrics
        }
        val result = mutableListOf<LyricLine>()
        var timeOffset = 0L
        embedded.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && (!ignoreHeaderTags || !EllaLyricsParser.isIgnorableRawLyricLine(trimmed))) {
                result.add(LyricLine(timeMs = timeOffset, text = trimmed, words = emptyList()))
                timeOffset += 3000L
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchOnlineLyrics(song: Song, ignoreHeaderTags: Boolean): List<LyricLine>? {
        song.onlineLyrics.takeIf(String::isNotBlank)?.let { raw ->
            parseRemoteLyrics(raw, ignoreHeaderTags)?.let { return it }
        }
        if (
            song.onlineSource == RemoteMusicProvider.Navidrome.id ||
            song.onlineSource == RemoteMusicProvider.OpenSubsonic.id
        ) {
            val config = if (song.onlineSource == RemoteMusicProvider.OpenSubsonic.id) {
                settingsManager.openSubsonicConfig.first()
            } else {
                settingsManager.navidromeConfig.first()
            }
            if (config.isConfigured && song.onlineId.isNotBlank()) {
                runCatching { NavidromeService(context).getServerLyrics(config, song) }
                    .getOrNull()
                    ?.let { raw -> parseRemoteLyrics(raw, ignoreHeaderTags) }
                    ?.let { return it }
            }
        }
        if (song.onlineId.isBlank() && song.onlineSource.isBlank()) return null
        val script = settingsManager.lxSourceScript.first()
        if (song.onlineSource.isNotBlank()) {
            val onlineSong = com.ella.music.data.lx.LxOnlineSong(
                song = song,
                source = song.onlineSource,
                songmid = song.onlineId,
                quality = "128k",
                coverUrl = song.coverUrl
            )
            runCatching {
                com.ella.music.data.lx.LxOnlineService(context).fetchLyrics(onlineSong, script)
            }.getOrNull()
                ?.let { raw -> parseRemoteLyrics(raw, ignoreHeaderTags) }
                ?.let { return it }
        }
        if (song.onlineSource != "kw" || song.onlineId.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${song.onlineId}")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Halcyon/1.0")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val list = root.optJSONObject("data")?.optJSONArray("lrclist") ?: return@use null
                val rawLines = List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    val timeMs = ((item.optString("time").toDoubleOrNull() ?: 0.0) * 1000).toLong()
                    LyricLine(timeMs = timeMs, text = item.optString("lineLyric").trim())
                }.filter { it.text.isNotBlank() }
                rawLines.takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to fetch online lyrics for ${song.title}", it)
            null
        }
    }

    private fun parseRemoteLyrics(raw: String, ignoreHeaderTags: Boolean): List<LyricLine>? {
        val timed = LrcParser.parse(raw, ignoreHeaderTags).lyrics
        if (timed.isNotEmpty()) return timed
        return raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { ignoreHeaderTags && EllaLyricsParser.isIgnorableRawLyricLine(it) }
            .mapIndexed { index, text -> LyricLine(timeMs = index * 3000L, text = text, words = emptyList()) }
            .toList()
            .takeIf(List<LyricLine>::isNotEmpty)
    }

    private fun Song.hasWebDavMetadataCache(): Boolean =
        hasUsableWebDavMetadataCache(remoteAudioCacheDir, remoteMetadataHeaderCacheDir)

    private fun MusicRepository.LyricFormatAvailability.hasAnyFormat(): Boolean = hasTtml || hasPlain
}
