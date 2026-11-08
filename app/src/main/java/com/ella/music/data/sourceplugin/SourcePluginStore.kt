package com.ella.music.data.sourceplugin

import android.content.Context
import android.net.Uri
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.readBytesBounded
import com.ella.music.data.readUtf8Bounded
import com.ella.music.data.requireHttpsRequests
import com.ella.music.data.requireHttpsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class SourcePluginImportResult(
    val plugins: List<SourcePluginConfig>,
    val skippedCount: Int = 0
)

private data class SourcePluginHubEntry(val name: String, val url: String)

/**
 * Registry of installed account-source plugins. Scripts live on disk under filesDir (they can be
 * multi-MB bundles); only metadata sits in SharedPreferences. Everything here is user-supplied —
 * the app ships no source plugins and no platform logic.
 */
class SourcePluginStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scriptsDir = File(context.filesDir, "source_plugins")
    private val prefs = context.getSharedPreferences("source_plugins", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("SourcePlugin"))
        .build()
    private val httpsClient = client.newBuilder().requireHttpsRequests().build()
    private val importSlots = Semaphore(4)

    private val mutablePlugins = MutableStateFlow(loadConfigs())
    val plugins = mutablePlugins.asStateFlow()

    fun plugin(pluginId: String): SourcePluginConfig? = mutablePlugins.value.firstOrNull { it.id == pluginId }

    suspend fun scriptOf(pluginId: String): String = withContext(Dispatchers.IO) {
        File(scriptsDir, "$pluginId.js").takeIf { it.isFile }?.readText().orEmpty()
    }

    /** file read for callers that already hold the script (avoids re-reading per call). */
    fun scriptFile(pluginId: String): File = File(scriptsDir, "$pluginId.js")

    suspend fun setEnabled(pluginId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        update(pluginId) { it.copy(enabled = enabled) }
    }

    private fun update(pluginId: String, transform: (SourcePluginConfig) -> SourcePluginConfig) {
        val next = mutablePlugins.value.map { if (it.id == pluginId) transform(it) else it }
        persist(next)
    }

    suspend fun deletePlugin(pluginId: String): Unit = withContext(Dispatchers.IO) {
        persist(mutablePlugins.value.filterNot { it.id == pluginId })
        File(scriptsDir, "$pluginId.js").delete()
        File(appContext.filesDir, "source_plugin_collections_$pluginId.json").delete()
        File(appContext.filesDir, "source_plugin_items_$pluginId.json").delete()
        SourcePluginAccountStore.getInstance(appContext).clear(pluginId)
    }

    suspend fun importFromUrl(url: String): SourcePluginImportResult = withContext(Dispatchers.IO) {
        val sourceUrl = url.trim()
        val bodyBytes = fetchBytes(sourceUrl)
        if (isZipBytes(bodyBytes)) {
            return@withContext installAll(extractZipScripts(bodyBytes), url = sourceUrl)
        }
        val body = String(bodyBytes, StandardCharsets.UTF_8)
        parsePluginHub(body, sourceUrl)?.let { entries ->
            val imported = coroutineScope {
                entries.map { entry ->
                    async(Dispatchers.IO) {
                        importSlots.withPermit {
                            runCatching {
                                val script = fetchText(entry.url)
                                install(script, url = entry.url, fallbackName = entry.name)
                            }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            val plugins = imported.filterNotNull()
            if (plugins.isEmpty()) error("插件仓库里没有可导入的音源插件")
            return@withContext SourcePluginImportResult(plugins, skippedCount = imported.size - plugins.size)
        }
        SourcePluginImportResult(listOf(install(body, url = sourceUrl, fallbackName = "")))
    }

    suspend fun importFromFile(uri: Uri): SourcePluginImportResult = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(MAX_SCRIPT_BYTES) }
            ?: error("无法读取插件文件")
        if (isZipBytes(bytes)) {
            return@withContext installAll(extractZipScripts(bytes), url = "")
        }
        SourcePluginImportResult(listOf(install(String(bytes, StandardCharsets.UTF_8), url = "", fallbackName = "")))
    }

    /** Validates the script through the real runtime, writes it to disk, registers metadata. */
    suspend fun install(script: String, url: String, fallbackName: String): SourcePluginConfig = withContext(Dispatchers.IO) {
        if (script.length !in 50..MAX_SCRIPT_BYTES) error("插件脚本内容异常")
        val normalized = normalizeModuleScript(script)
        val manifest = inspectManifest(normalized)
        val id = SourcePluginConfig.sanitizeId(manifest.optString("id").ifBlank { manifest.optString("platform") })
        if (id.isBlank()) error("插件未声明 platform / id")
        val name = manifest.optString("platform").ifBlank { fallbackName }.ifBlank { id }
        val account = manifest.optJSONObject("account")
        val methods = account?.optJSONArray("methods")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            .orEmpty().ifEmpty { listOf("cookie") }
        val capabilities = manifest.optJSONObject("capabilities")?.let { caps ->
            caps.keys().asSequence().filter { caps.optBoolean(it) }.toList()
        }.orEmpty()
        val declared = manifest.optJSONArray("functions")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        if (declared.intersect(CONTRACT_METHODS).isEmpty()) {
            error("插件未实现任何音源接口（login/getCollections/getMediaSource/search 等）")
        }
        // Capabilities = explicit flags + implied by implemented contract methods.
        val implied = declared.mapNotNull { methodCapability(it) }
        val config = SourcePluginConfig(
            id = id,
            name = name,
            version = manifest.optString("version"),
            author = manifest.optString("author"),
            url = url,
            enabled = true,
            importedAt = System.currentTimeMillis(),
            methods = methods,
            capabilities = (capabilities + implied).distinct()
        )
        scriptsDir.mkdirs()
        File(scriptsDir, "$id.js").writeText(normalized)
        val next = mutablePlugins.value.filterNot { it.id == id } + config
        persist(next)
        config
    }

    private fun inspectManifest(script: String): JSONObject =
        SourcePluginRuntime(appContext, client, "source_plugin_kv_probe").use { runtime ->
            runtime.inspect(script)
        }

    private fun methodCapability(method: String): String? = when (method) {
        "getCollections" -> "collections"
        "search" -> "search"
        "getLyric" -> "lyric"
        "getRecentPlays" -> "recentPlays"
        "setFavorite" -> "setFavorite"
        "getComments" -> "comments"
        "getMvUrl", "getMvCover" -> "mv"
        "getDailySongs", "getDailyPlaylists" -> "daily"
        "getRadio" -> "radio"
        "getMediaSource" -> "mediaSource"
        else -> null
    }

    private fun normalizeModuleScript(script: String): String {
        return script
            .replace(Regex("""^\s*export\s+default\s+""", RegexOption.MULTILINE), "module.exports = ")
            .replace(Regex("""^\s*export\s+const\s+(\w+)\s*=""", RegexOption.MULTILINE), "const $1 =")
            .replace(Regex("""^\s*export\s+\{[^}]+\}\s*;?""", RegexOption.MULTILINE), "")
    }

    private fun fetchText(url: String): String =
        String(fetchBytes(url), StandardCharsets.UTF_8)

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url.requireHttpsUrl("Source plugin"))
            .header("User-Agent", "Halcyon-SourcePlugin/1.0")
            .header("Cache-Control", "no-cache")
            .build()
        httpsClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("导入失败: HTTP ${response.code}")
            return response.body?.byteStream()?.use { it.readBytesBounded(MAX_SCRIPT_BYTES) }
                ?: ByteArray(0)
        }
    }

    private fun isZipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** Collects .js entries from a plugin zip package; every entry is independently validated by install(). */
    private fun extractZipScripts(bytes: ByteArray): List<String> {
        val scripts = mutableListOf<String>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && scripts.size < MAX_ZIP_PLUGIN_COUNT) {
                    if (!entry.isDirectory && entry.name.endsWith(".js", ignoreCase = true)) {
                        val text = String(zip.readBytesBounded(MAX_SCRIPT_BYTES), StandardCharsets.UTF_8)
                        if (text.length >= 50) scripts += text
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return scripts
    }

    private suspend fun installAll(scripts: List<String>, url: String): SourcePluginImportResult {
        if (scripts.isEmpty()) error("压缩包里没有可导入的音源插件")
        val imported = coroutineScope {
            scripts.map { script ->
                async(Dispatchers.IO) {
                    importSlots.withPermit {
                        runCatching { install(script, url = url, fallbackName = "") }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        val plugins = imported.filterNotNull()
        if (plugins.isEmpty()) error("压缩包里没有可导入的音源插件")
        return SourcePluginImportResult(plugins, skippedCount = scripts.size - plugins.size)
    }

    private fun parsePluginHub(content: String, baseUrl: String): List<SourcePluginHubEntry>? {
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
            SourcePluginHubEntry(
                name = item.optString("name").ifBlank { item.optString("platform") },
                url = resolvedUrl
            )
        }.filterNotNull()
    }

    private fun loadConfigs(): List<SourcePluginConfig> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PLUGINS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(SourcePluginConfig::fromJson)
        }.filter { File(scriptsDir, "${it.id}.js").isFile }
    }.getOrDefault(emptyList())

    private fun persist(configs: List<SourcePluginConfig>) {
        val array = JSONArray()
        configs.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PLUGINS, array.toString()).apply()
        mutablePlugins.value = configs
    }

    companion object {
        const val KEY_PLUGINS = "plugins"
        const val MAX_SCRIPT_BYTES = 9_000_000L
        const val MAX_ZIP_PLUGIN_COUNT = 32
        val CONTRACT_METHODS = setOf(
            "login", "getQrCode", "checkQrCode", "getCollections", "getDailySongs",
            "getDailyPlaylists", "getRadio", "search", "getMediaSource", "getLyric",
            "getRecentPlays", "setFavorite", "getComments", "getCommentFloor",
            "likeComment", "postComment", "getMvUrl", "getMvCover", "getSongDetail"
        )
        @Volatile private var instance: SourcePluginStore? = null
        fun getInstance(context: Context): SourcePluginStore = instance ?: synchronized(this) {
            instance ?: SourcePluginStore(context.applicationContext).also { instance = it }
        }
    }
}
