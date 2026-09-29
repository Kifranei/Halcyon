package com.ella.music.plugin.source

import android.content.Context
import android.net.Uri
import com.ella.music.plugin.i18n.PluginLocales
import com.ella.music.plugin.i18n.PluginStrings
import com.ella.music.plugin.model.PluginManifest
import com.ella.music.plugin.runtime.HostApiRegistry
import com.ella.music.data.copyToBoundedOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

class CustomPluginStore(
    private val context: Context,
    private val json: Json = pluginJson
) {
    private val rootDir: File = File(context.filesDir, "lyrico_plugins")
    private val configStore = PluginConfigStore(context.applicationContext)

    suspend fun loadPlugins(): List<LyricoPluginSource> = withContext(Dispatchers.IO) {
        val bundled = loadBundledPlugins()
        val imported = rootDir.listFiles { file -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { dir -> runCatching { loadPlugin(dir) }.getOrNull() }
        // First-party sources cannot be replaced by an imported plugin with the same id.
        (bundled + imported).distinctBy { it.manifest.id }
    }

    suspend fun deletePlugin(id: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(rootDir, id.safeFileName())
        dir.isDirectory && dir.deleteRecursively()
    }

    suspend fun importPluginZip(uri: Uri): List<PluginManifest> = withContext(Dispatchers.IO) {
        rootDir.mkdirs()
        val tempDir = File(context.cacheDir, "lyrico_plugin_import_${System.currentTimeMillis()}")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        try {
            unzip(uri, tempDir)
            val imported = findPluginRoots(tempDir)
                .map { pluginDir ->
                    val manifest = readAndValidateManifest(pluginDir)
                    val targetDir = File(rootDir, manifest.id.safeFileName())
                    val stagedDir = File(rootDir, ".${manifest.id.safeFileName()}.staging-${UUID.randomUUID()}")
                    try {
                        val oldFingerprint = targetDir.takeIf(File::isDirectory)
                            ?.let { runCatching { directoryFingerprint(it) }.getOrNull() }
                        copyDirectory(pluginDir, stagedDir)
                        val newFingerprint = directoryFingerprint(stagedDir)
                        val pluginCodeChanged = oldFingerprint == null || !oldFingerprint.contentEquals(newFingerprint)
                        val backupDir = targetDir.takeIf(File::exists)?.let {
                            File(rootDir, ".${manifest.id.safeFileName()}.previous-${UUID.randomUUID()}")
                        }
                        try {
                            if (backupDir != null) require(targetDir.renameTo(backupDir)) { "Unable to stage current plugin" }
                            require(stagedDir.renameTo(targetDir)) { "Unable to install plugin" }
                            if (pluginCodeChanged) configStore.deleteConfig(manifest.id)
                            backupDir?.deleteRecursively()
                        } catch (error: Throwable) {
                            if (backupDir?.exists() == true) {
                                targetDir.deleteRecursively()
                                backupDir.renameTo(targetDir)
                            }
                            throw error
                        }
                    } finally {
                        stagedDir.deleteRecursively()
                    }
                    manifest
                }
            require(imported.isNotEmpty()) { "Plugin manifest.json not found" }
            imported
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun loadPlugin(dir: File): LyricoPluginSource {
        val manifest = json.decodeFromString<PluginManifest>(File(dir, "manifest.json").readText())
        val strings = runCatching { PluginStrings.load(dir, manifest) }.getOrNull()
        validateManifest(manifest, dir, strings)
        val localizedManifest = strings?.snapshot(PluginLocales.preferences.value)?.localize(manifest) ?: manifest
        return LyricoPluginSource(
            manifest = localizedManifest,
            assetDir = dir.absolutePath,
            script = buildScript(dir, manifest),
            cacheRootDir = File(context.cacheDir, "lyrico_plugin_cache"),
            strings = strings
        )
    }

    private fun loadBundledPlugins(): List<LyricoPluginSource> =
        context.assets.list(BUNDLED_PLUGIN_ROOT)
            .orEmpty()
            .sorted()
            .mapNotNull { directoryName ->
                runCatching { loadBundledPlugin(directoryName) }.getOrNull()
            }

    private fun loadBundledPlugin(directoryName: String): LyricoPluginSource {
        val pluginRoot = "$BUNDLED_PLUGIN_ROOT/$directoryName"
        val manifest = json.decodeFromString<PluginManifest>(readAssetText("$pluginRoot/manifest.json"))
        val strings = runCatching { PluginStrings.loadFromAssets(context.assets, pluginRoot, manifest) }.getOrNull()
        validateManifestBasics(manifest)
        require(assetExists("$pluginRoot/${manifest.entry}")) { "Missing plugin entry file" }
        val localizedManifest = strings?.snapshot(PluginLocales.preferences.value)?.localize(manifest) ?: manifest
        val includeSources = manifest.includeDirs
            .flatMap { includeDir -> assetFilesUnder("$pluginRoot/$includeDir") }
            .filter { it.endsWith(".js", ignoreCase = true) }
            .map { path ->
                IncludedScript(
                    path = path.removePrefix("$pluginRoot/"),
                    content = readAssetText(path)
                )
            }
            .sortedBy { it.path }
        return LyricoPluginSource(
            manifest = localizedManifest,
            assetDir = "asset://$pluginRoot",
            script = composeScript(
                manifest = manifest,
                includeSources = includeSources,
                entryContent = readAssetText("$pluginRoot/${manifest.entry}")
            ),
            cacheRootDir = File(context.cacheDir, "lyrico_plugin_cache"),
            bundled = true,
            strings = strings
        )
    }

    private fun buildScript(pluginDir: File, manifest: PluginManifest): String {
        val includeSources = manifest.includeDirs
            .flatMap { includeDir ->
                File(pluginDir, includeDir).walkTopDown()
                    .filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
                    .map { file ->
                        IncludedScript(
                            path = file.relativeTo(pluginDir).invariantPath(),
                            content = file.readText()
                        )
                    }
                    .toList()
            }
            .sortedBy { it.path }
        return composeScript(
            manifest = manifest,
            includeSources = includeSources,
            entryContent = File(pluginDir, manifest.entry).readText()
        )
    }

    private fun composeScript(
        manifest: PluginManifest,
        includeSources: List<IncludedScript>,
        entryContent: String
    ): String {
        val includePathSetJson = json.encodeToString(includeSources.map { it.path }.toSet())
        return buildString {
            append(
                """
                (function() {
                  var __lyricoDeclaredIncludes = $includePathSetJson;
                  var __lyricoDeclaredIncludeMap = Object.create(null);
                  __lyricoDeclaredIncludes.forEach(function(path) {
                    __lyricoDeclaredIncludeMap[path] = true;
                  });
                  globalThis.include = function(path) {
                    path = String(path || "");
                    if (!Object.prototype.hasOwnProperty.call(__lyricoDeclaredIncludeMap, path)) {
                      throw new Error("Include path is not declared in includeDirs: " + path);
                    }
                  };
                })();
                """.trimIndent()
            )
            includeSources.forEach { source ->
                append("\n;\n// ===== Platform include: ${source.path} =====\n")
                append(source.content)
                append("\n//# sourceURL=${source.path}\n")
            }
            append("\n;\n// ===== Platform entry: ${manifest.entry} =====\n")
            append(entryContent)
            append("\n//# sourceURL=${manifest.entry}\n")
        }
    }

    private fun readAssetText(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun assetExists(path: String): Boolean =
        runCatching { context.assets.open(path).close() }.isSuccess

    private fun assetFilesUnder(path: String): List<String> {
        val children = context.assets.list(path).orEmpty()
        return if (children.isEmpty()) {
            listOf(path)
        } else {
            children.flatMap { child -> assetFilesUnder("$path/$child") }
        }
    }

    private fun unzip(uri: Uri, targetDir: File) {
        var entryCount = 0
        var totalBytes = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open plugin zip" }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_PLUGIN_ZIP_ENTRIES) { "Plugin archive contains too many entries" }
                    val target = File(targetDir, entry.name).canonicalFile
                    require(target.path.startsWith(targetDir.canonicalPath + File.separator)) { "Invalid zip entry" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        val bytes = target.outputStream().use { output ->
                            zip.copyToBoundedOrThrow(output, MAX_PLUGIN_ENTRY_BYTES)
                        }
                        totalBytes += bytes
                        require(totalBytes <= MAX_PLUGIN_ARCHIVE_BYTES) { "Plugin archive expands beyond the size limit" }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun findPluginRoots(tempDir: File): List<File> {
        return tempDir.walkTopDown()
            .filter { file -> file.isFile && file.name.equals("manifest.json", ignoreCase = true) }
            .mapNotNull { it.parentFile }
            .distinctBy { it.canonicalPath }
            .toList()
    }

    private fun readAndValidateManifest(pluginDir: File): PluginManifest {
        val manifest = json.decodeFromString<PluginManifest>(File(pluginDir, "manifest.json").readText())
        val strings = runCatching { PluginStrings.load(pluginDir, manifest) }.getOrNull()
        validateManifest(manifest, pluginDir, strings)
        return strings?.snapshot(PluginLocales.preferences.value)?.localize(manifest) ?: manifest
    }

    private fun validateManifest(manifest: PluginManifest, pluginDir: File, strings: PluginStrings? = null) {
        validateManifestBasics(manifest)
        require(File(pluginDir, manifest.entry).isFile) { "Missing plugin entry file" }
        if (manifest.i18n != null && strings == null) {
            PluginStrings.load(pluginDir, manifest)
        }
    }

    private fun validateManifestBasics(manifest: PluginManifest) {
        require(HostApiRegistry.supportsPluginApiVersion(manifest.apiVersion)) {
            "Unsupported plugin apiVersion: ${manifest.apiVersion}"
        }
        require(HostApiRegistry.supportsHostApiVersion(manifest.minHostApiVersion)) {
            "Unsupported minHostApiVersion: ${manifest.minHostApiVersion}"
        }
        require(manifest.entry.isNotBlank()) { "Missing plugin entry" }
    }

    private fun copyDirectory(from: File, to: File) {
        from.walkTopDown().forEach { source ->
            val target = File(to, source.relativeTo(from).path)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun directoryFingerprint(dir: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val files = dir.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(dir).invariantPath() }.toList()
        require(files.size <= MAX_PLUGIN_ZIP_ENTRIES) { "Plugin contains too many files" }
        var totalBytes = 0L
        files.forEach { file ->
            val size = file.length()
            require(size <= MAX_PLUGIN_ENTRY_BYTES) { "Plugin file exceeds the size limit" }
            totalBytes += size
            require(totalBytes <= MAX_PLUGIN_ARCHIVE_BYTES) { "Plugin exceeds the size limit" }
            digest.update(file.relativeTo(dir).invariantPath().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest()
    }

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private fun String.safeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "plugin" }

    private data class IncludedScript(val path: String, val content: String)

    private companion object {
        const val BUNDLED_PLUGIN_ROOT = "lyrico_plugins"
        const val MAX_PLUGIN_ZIP_ENTRIES = 512
        const val MAX_PLUGIN_ENTRY_BYTES = 16L * 1024L * 1024L
        const val MAX_PLUGIN_ARCHIVE_BYTES = 64L * 1024L * 1024L
    }
}
