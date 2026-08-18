package com.ella.music.plugin.source

import android.content.Context
import android.net.Uri
import com.ella.music.plugin.model.PluginManifest
import com.ella.music.plugin.runtime.HostApiRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

class CustomPluginStore(
    private val context: Context,
    private val json: Json = pluginJson
) {
    private val rootDir: File = File(context.filesDir, "lyrico_plugins")

    suspend fun loadPlugins(): List<LyricoPluginSource> = withContext(Dispatchers.IO) {
        rootDir.listFiles { file -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { dir -> runCatching { loadPlugin(dir) }.getOrNull() }
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
                    targetDir.deleteRecursively()
                    copyDirectory(pluginDir, targetDir)
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
        validateManifest(manifest, dir)
        return LyricoPluginSource(
            manifest = manifest,
            assetDir = dir.absolutePath,
            script = buildScript(dir, manifest),
            cacheRootDir = File(context.cacheDir, "lyrico_plugin_cache")
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
            append(File(pluginDir, manifest.entry).readText())
            append("\n//# sourceURL=${manifest.entry}\n")
        }
    }

    private fun unzip(uri: Uri, targetDir: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open plugin zip" }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(targetDir, entry.name).canonicalFile
                    require(target.path.startsWith(targetDir.canonicalPath + File.separator)) { "Invalid zip entry" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> zip.copyTo(output) }
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
        validateManifest(manifest, pluginDir)
        return manifest
    }

    private fun validateManifest(manifest: PluginManifest, pluginDir: File) {
        require(HostApiRegistry.supportsPluginApiVersion(manifest.apiVersion)) {
            "Unsupported plugin apiVersion: ${manifest.apiVersion}"
        }
        require(HostApiRegistry.supportsHostApiVersion(manifest.minHostApiVersion)) {
            "Unsupported minHostApiVersion: ${manifest.minHostApiVersion}"
        }
        require(manifest.entry.isNotBlank()) { "Missing plugin entry" }
        require(File(pluginDir, manifest.entry).isFile) { "Missing plugin entry file" }
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

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private fun String.safeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "plugin" }

    private data class IncludedScript(val path: String, val content: String)
}
