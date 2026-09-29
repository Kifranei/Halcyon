package com.ella.music.data.musicfree

import android.content.Context
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.model.Song
import com.ella.music.data.readUtf8Bounded
import com.ella.music.data.requireHttpsUrl
import com.ella.music.data.requireHttpsRequests
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URI
import java.util.concurrent.TimeUnit

data class MusicFreeOnlineSong(
    val song: Song,
    val pluginName: String,
    val rawJson: String,
    val coverUrl: String = ""
)

data class MusicFreeImportResult(
    val plugins: List<MusicFreePluginConfig>,
    val skippedCount: Int = 0
)

class MusicFreePluginService(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("MusicFreeNetwork"))
        .build()

    private val importClient = client.newBuilder().requireHttpsRequests().build()
    private val importSlots = Semaphore(4)

    suspend fun importPlugins(url: String): MusicFreeImportResult = withContext(Dispatchers.IO) {
        val sourceUrl = url.trim()
        val body = fetchText(sourceUrl)
        parsePluginHub(body, sourceUrl)?.let { entries ->
            val imported = coroutineScope {
                entries.map { entry ->
                    async(Dispatchers.IO) {
                        importSlots.withPermit { runCatching {
                            val script = fetchText(entry.url)
                            val (detectedName, normalizedScript) = importPluginScript(
                                script = script,
                                fallbackName = entry.name,
                                allowRuntimeInspect = context != null
                            )
                            MusicFreePluginConfig(
                                id = "musicfree_${entry.url.hashCode()}",
                                url = entry.url,
                                name = detectedName,
                                script = normalizedScript
                            )
                        } }
                    }
                }.awaitAll()
            }
            val plugins = imported.mapNotNull { it.getOrNull() }
            val skipped = imported.count { it.isFailure }
            if (plugins.isEmpty()) error("插件仓库里没有可导入的 MusicFree 音乐插件")
            return@withContext MusicFreeImportResult(plugins = plugins, skippedCount = skipped)
        }
        val (name, script) = importPluginScript(body, allowRuntimeInspect = context != null)
        MusicFreeImportResult(
            plugins = listOf(
                MusicFreePluginConfig(
                    id = "musicfree_${sourceUrl.hashCode()}",
                    url = sourceUrl,
                    name = name,
                    script = script
                )
            )
        )
    }

    @Deprecated("Use importPlugins so repository links and single scripts are handled consistently.")
    suspend fun importPlugin(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim().requireHttpsUrl("MusicFree source"))
            .header("User-Agent", USER_AGENT)
            .header("Cache-Control", "no-cache")
            .build()
        importClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            importPluginScript(response.body?.byteStream()?.use { it.readUtf8Bounded(9_000_000L) }.orEmpty(), allowRuntimeInspect = context != null)
        }
    }

    fun importPluginScript(
        script: String,
        fallbackName: String = "",
        allowRuntimeInspect: Boolean = true
    ): Pair<String, String> {
        if (script.length !in 50..9_000_000) error("插件脚本内容异常")
        val normalizedScript = normalizeModuleScript(script)
        val staticName = extractPlatform(normalizedScript)
            .ifBlank { extractPluginMetadataName(script) }
            .ifBlank { fallbackName.trim() }
        val staticSupportsMusic = normalizedScript.hasStaticMusicCapability()
        val runtimeInfo = if (allowRuntimeInspect) {
            inspectPluginIfNeeded(normalizedScript)
        } else {
            null
        }
        val name = runtimeInfo?.optString("platform").orEmpty()
            .ifBlank { staticName }
            .ifBlank { runtimeInfo?.optString("name").orEmpty() }
        if (name.isBlank()) error("没有识别到 MusicFree 插件 platform、@name 或仓库名称")
        val supportsMusic = if (runtimeInfo == null) staticSupportsMusic else
            runtimeInfo.optBoolean("hasSearch") || runtimeInfo.optBoolean("hasMediaSource") ||
                runtimeInfo.optBoolean("hasImportMusicItem")
        if (!supportsMusic) error("插件未声明搜索或播放能力")
        return name to normalizedScript
    }

    private fun String.hasStaticMusicCapability(): Boolean {
        return "search" in this ||
            "getMediaSource" in this ||
            "importMusicItem" in this
    }

    private fun inspectPluginIfNeeded(script: String): JSONObject? {
        val appContext = context ?: return null
        return MusicFreePluginRuntime(appContext, client).use { runtime ->
            runtime.inspectPlugin(script)
        }
    }

    private fun normalizeModuleScript(script: String): String {
        return script
            .replace(Regex("""^\s*export\s+default\s+""", RegexOption.MULTILINE), "module.exports = ")
            .replace(Regex("""^\s*export\s+const\s+(\w+)\s*=""", RegexOption.MULTILINE), "const $1 =")
            .replace(Regex("""^\s*export\s+\{[^}]+\}\s*;?""", RegexOption.MULTILINE), "")
            .removeParcelDemoInvocations()
    }

    private fun String.removeParcelDemoInvocations(): String {
        return replace(
            Regex(
                pattern = """(?s)\n\s*\(0,\s*${'$'}[A-Za-z0-9]+${'$'}export${'$'}[A-Za-z0-9]+\)\([^;]*?\)\.then\(\(res\)\s*=>\s*\{.*?\n\s*\}\);\s*""",
            ),
            "\n"
        )
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url.requireHttpsUrl("MusicFree source"))
            .header("User-Agent", USER_AGENT)
            .header("Cache-Control", "no-cache")
            .build()
        importClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            return response.body?.byteStream()?.use { it.readUtf8Bounded(9_000_000L) }.orEmpty()
        }
    }

    private fun parsePluginHub(content: String, baseUrl: String): List<PluginHubEntry>? {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val array = runCatching {
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("plugins") ?: return null
        }.getOrNull() ?: return null
        val base = runCatching { URI(baseUrl) }.getOrNull()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@List null
            val rawUrl = item.optString("url").ifBlank { item.optString("srcUrl") }
            if (rawUrl.isBlank()) return@List null
            val resolvedUrl = runCatching { base?.resolve(rawUrl)?.toString() ?: rawUrl }.getOrDefault(rawUrl)
            PluginHubEntry(
                name = item.optString("name").ifBlank { item.optString("platform") },
                url = resolvedUrl
            )
        }.filterNotNull()
    }

    suspend fun search(
        keyword: String,
        plugin: MusicFreePluginConfig?,
        page: Int = 1
    ): List<MusicFreeOnlineSong> = withContext(Dispatchers.IO) {
        if (plugin == null) error("请先选择一个 MusicFree 插件")
        if (context == null) error("MusicFree 运行环境未初始化")
        val normalizedKeyword = keyword.trim()
        val safePage = page.coerceAtLeast(1)
        val rawItems = runCatching {
            MusicFreePluginRuntime(context, client).use { runtime ->
                runtime.search(plugin.script, normalizedKeyword, safePage)
            }
        }.getOrElse { error ->
            if (error.message?.contains("lists", ignoreCase = true) == true && plugin.looksLikeKugouPlugin()) {
                searchKugouFallback(normalizedKeyword, safePage)
            } else {
                throw error
            }
        }
        val items = rawItems.toJsonObjects()
        val durationBySongMid = fetchQqDurationsIfNeeded(plugin.name, items)
        items.mapNotNull { item ->
            item.toOnlineSong(plugin.name)?.let { onlineSong ->
                enrichMissingDuration(item, onlineSong, durationBySongMid)
            }
        }
    }

    suspend fun resolvePlayableSong(item: MusicFreeOnlineSong, plugin: MusicFreePluginConfig?): Song = withContext(Dispatchers.IO) {
        if (plugin == null) error("请先选择一个 MusicFree 插件")
        if (item.song.path.startsWith("http://") || item.song.path.startsWith("https://")) return@withContext item.song
        if (context == null) error("MusicFree 运行环境未初始化")
        val mediaSource = MusicFreePluginRuntime(context, client).use { runtime ->
            runtime.getMediaSource(plugin.script, item.rawJson, preferredQuality(item.rawJson, com.ella.music.data.SettingsManager.getInstance(requireNotNull(context)).onlinePlaybackQuality.first()))
        }
        val lyricPayload = runCatching {
            MusicFreePluginRuntime(context, client).use { runtime ->
                runtime.getLyric(plugin.script, item.rawJson)
            }
        }.getOrNull()
        val url = mediaSource.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: error("插件没有返回播放地址")
        val ext = url.substringBefore('?')
            .substringAfterLast('.', "")
            .takeIf { it.length in 2..5 }
            ?: "mp3"
        item.song.copy(
            path = url,
            fileName = "${item.song.title}.$ext",
            mimeType = mimeTypeFromExtension(ext),
            onlineLyrics = lyricPayload?.extractLyrics().orEmpty().ifBlank { item.song.onlineLyrics },
            onlineLyricTranslation = lyricPayload?.extractLyricTranslation().orEmpty().ifBlank { item.song.onlineLyricTranslation }
        )
    }

    private fun extractPlatform(script: String): String {
        val patterns = listOf(
            Regex("""platform\s*:\s*['"]([^'"]+)['"]"""),
            Regex("""platform\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""['"]platform['"]\s*:\s*['"]([^'"]+)['"]""")
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(script)?.groupValues?.getOrNull(1)
        }.orEmpty().trim()
    }

    private fun extractPluginMetadataName(script: String): String {
        val commentName = Regex("""(?m)^\s*\*\s*@name\s+(.+?)\s*$""")
            .find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!commentName.isNullOrBlank()) return commentName

        val objectName = Regex("""(?m)\bname\s*:\s*['"]([^'"]+)['"]""")
            .find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return objectName.orEmpty()
    }

    private fun JSONObject.toOnlineSong(pluginName: String): MusicFreeOnlineSong? {
        val title = optFirstString("title", "name", "songname", "songName").trim()
        if (title.isBlank()) return null
        val artist = optArtist().ifBlank { "未知歌手" }.trim()
        val album = optFirstString("album", "albumName", "albumname").ifBlank { "在线音乐" }.trim()
        val platform = optString("platform").ifBlank { pluginName }
        val onlineId = optFirstString("id", "songmid", "mid", "hash", "rid", "album_audio_id")
            .ifBlank { "$platform:$title:$artist" }
        val artwork = optFirstString("artwork", "cover", "coverImg", "coverUrl", "pic", "picUrl", "albumArt")
        val url = optFirstString("url", "playUrl", "musicUrl")
        val lyrics = extractLyrics()
        val lyricTranslation = extractLyricTranslation()
        val durationMs = parseDurationMs()
        val id = "musicfree_${platform}_$onlineId".hashCode().toLong()
        return MusicFreeOnlineSong(
            song = Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                albumId = 0L,
                duration = durationMs,
                path = url,
                fileName = "$title-$artist.mp3",
                mimeType = "audio/mpeg",
                coverUrl = artwork,
                onlineSource = "musicfree:$platform",
                onlineId = onlineId,
                onlineLyrics = lyrics,
                onlineLyricTranslation = lyricTranslation
            ),
            pluginName = platform,
            rawJson = toString(),
            coverUrl = artwork
        )
    }

    private fun JSONObject.optFirstString(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            opt(key)?.takeUnless { it == JSONObject.NULL }?.let { value ->
                when (value) {
                    is String -> value
                    is Number -> value.toString()
                    else -> null
                }?.takeIf { it.isNotBlank() }
            }
        }.orEmpty()
    }

    private fun JSONObject.optArtist(): String {
        optFirstString("artist", "singer", "author", "creator").takeIf { it.isNotBlank() }?.let { return it }
        val artistArray = optJSONArray("artists") ?: optJSONArray("singers") ?: optJSONArray("ar")
        if (artistArray != null) {
            val names = List(artistArray.length()) { index ->
                val value = artistArray.opt(index)
                when (value) {
                    is JSONObject -> value.optFirstString("name", "title", "singerName")
                    is String -> value
                    else -> ""
                }
            }.filter { it.isNotBlank() }
            if (names.isNotEmpty()) return names.joinToString(", ")
        }
        optJSONObject("artist")?.optFirstString("name", "title")?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun JSONObject.parseDurationMs(): Long {
        val raw = optFirstString("duration", "durationMs", "interval", "time", "dt", "playTime")
        if (raw.isBlank()) return 0L
        if (":" in raw) {
            val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
            if (parts.isNotEmpty()) {
                val seconds = parts.fold(0L) { acc, part -> acc * 60L + part }
                return seconds * 1000L
            }
        }
        val number = raw.toDoubleOrNull() ?: return 0L
        return when {
            number <= 0.0 -> 0L
            raw.contains(".") && number < 86_400.0 -> (number * 1000L).toLong()
            number in 1.0..86_400.0 -> number.toLong() * 1000L
            else -> number.toLong()
        }
    }

    private fun enrichMissingDuration(
        item: JSONObject,
        onlineSong: MusicFreeOnlineSong,
        durationBySongMid: Map<String, Long>
    ): MusicFreeOnlineSong {
        if (onlineSong.song.duration > 0L) return onlineSong
        val songMid = item.optFirstString("songmid", "mid")
        val durationMs = durationBySongMid[songMid].orZero()
        if (durationMs <= 0L) return onlineSong
        return onlineSong.copy(
            song = onlineSong.song.copy(duration = durationMs)
        )
    }

    private fun fetchQqDurationsIfNeeded(pluginName: String, items: List<JSONObject>): Map<String, Long> {
        if (!pluginName.contains("QQ", ignoreCase = true)) return emptyMap()
        val songMids = items.mapNotNull { item ->
            item.optFirstString("songmid", "mid").takeIf { it.isNotBlank() }
        }.distinct()
        if (songMids.isEmpty()) return emptyMap()
        val request = Request.Builder()
            .url("https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?songmid=${songMids.joinToString(",")}&format=json")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap()
                val json = JSONObject(response.body?.string().orEmpty())
                val data = json.optJSONArray("data") ?: return@use emptyMap()
                List(data.length()) { index -> data.optJSONObject(index) }
                    .filterNotNull()
                    .mapNotNull { song ->
                        val mid = song.optString("mid").ifBlank { song.optString("songmid") }
                        val seconds = song.optLong("interval", 0L)
                        if (mid.isBlank() || seconds <= 0L) null else mid to seconds * 1000L
                    }
                    .toMap()
            }
        }.getOrDefault(emptyMap())
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun JSONObject.extractLyrics(): String {
        return optString("rawLrc")
            .ifBlank { optString("rawLrcTxt") }
            .ifBlank { optString("lrc") }
            .ifBlank { optString("lyric") }
            .ifBlank { optString("lyrics") }
            .ifBlank { optString("plainLyric") }
            .ifBlank {
                optJSONObject("lyric")?.extractLyrics().orEmpty()
            }
            .trim()
    }

    private fun JSONObject.extractLyricTranslation(): String {
        return optString("translation")
            .ifBlank { optString("transLyric") }
            .ifBlank { optString("translatedLyric") }
            .ifBlank { optString("translate") }
            .ifBlank { optString("rawTranslation") }
            .ifBlank {
                optJSONObject("lyric")?.extractLyricTranslation().orEmpty()
            }
            .trim()
    }

    private fun preferredQuality(rawJson: String, selected: String): String {
        if (selected.isBlank() || selected == "auto") return bestQuality(rawJson)
        val item = runCatching { JSONObject(rawJson) }.getOrNull() ?: return bestQuality(rawJson)
        val values = buildList {
            item.optJSONArray("qualities")?.let { array -> for (i in 0 until array.length()) add(array.optString(i)) }
            item.optJSONArray("qualitys")?.let { array -> for (i in 0 until array.length()) add(array.optString(i)) }
            item.optJSONObject("qualities")?.keys()?.forEach { add(it) }
        }
        return com.ella.music.data.OnlinePlaybackQuality.musicFreeCandidates(selected)
            .firstNotNullOfOrNull { wanted -> values.firstOrNull { it.equals(wanted, ignoreCase = true) } }
            ?: bestQuality(rawJson)
    }

    private fun bestQuality(rawJson: String): String {
        val item = runCatching { JSONObject(rawJson) }.getOrNull() ?: return "standard"
        val qualities = item.optJSONObject("qualities")
        if (qualities != null) {
            return when {
                qualities.has("super") -> "super"
                qualities.has("high") -> "high"
                qualities.has("standard") -> "standard"
                qualities.has("low") -> "low"
                else -> "standard"
            }
        }
        val qualityArray = item.optJSONArray("qualities") ?: item.optJSONArray("qualitys") ?: item.optJSONArray("types")
        if (qualityArray != null) {
            val values = List(qualityArray.length()) { index ->
                val value = qualityArray.opt(index)
                when (value) {
                    is JSONObject -> value.optFirstString("type", "quality", "name")
                    else -> value?.toString().orEmpty()
                }
            }
            val preference = listOf("super", "lossless", "flac", "high", "320k", "standard", "128k", "low")
            preference.firstOrNull { preferred -> values.any { it.equals(preferred, ignoreCase = true) } }?.let { return it }
        }
        item.optFirstString("quality", "type").takeIf { it.isNotBlank() }?.let { return it }
        return "standard"
    }

    private fun mimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun JSONArray.toJsonObjects(): List<JSONObject> {
        return List(length()) { index -> optJSONObject(index) }.filterNotNull()
    }

    private fun MusicFreePluginConfig.looksLikeKugouPlugin(): Boolean {
        return name.contains("酷狗", ignoreCase = true) ||
            name.contains("kugou", ignoreCase = true) ||
            url.contains("kg", ignoreCase = true) ||
            script.contains("kugou", ignoreCase = true) ||
            script.contains("mobilecdn.kugou.com", ignoreCase = true)
    }

    private fun searchKugouFallback(keyword: String, page: Int): JSONArray {
        if (keyword.isBlank()) return JSONArray()
        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=$encoded&page=$page&pagesize=20")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("酷狗搜索失败: HTTP ${response.code}")
            val info = JSONObject(response.body?.string().orEmpty())
                .optJSONObject("data")
                ?.optJSONArray("info")
                ?: JSONArray()
            return JSONArray().apply {
                for (index in 0 until info.length()) {
                    val item = info.optJSONObject(index) ?: continue
                    val hash = item.optFirstString("hash", "320hash", "sqhash")
                    val cover = item.optJSONObject("trans_param")
                        ?.optFirstString("union_cover")
                        ?.replace("{size}", "400")
                        .orEmpty()
                    put(
                        JSONObject()
                            .put("id", hash)
                            .put("hash", hash)
                            .put("title", item.optFirstString("songname", "songname_original", "filename"))
                            .put("artist", item.optFirstString("singername", "author_name"))
                            .put("album", item.optFirstString("album_name"))
                            .put("album_id", item.optFirstString("album_id"))
                            .put("album_audio_id", item.optFirstString("album_audio_id", "audio_id"))
                            .put("duration", item.optLong("duration", 0L))
                            .put("artwork", cover)
                            .put("coverImg", cover)
                            .put("320hash", item.optFirstString("320hash"))
                            .put("sqhash", item.optFirstString("sqhash"))
                            .put("origin_hash", hash)
                    )
                }
            }
        }
    }

    private data class PluginHubEntry(
        val name: String,
        val url: String
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0"
    }
}
