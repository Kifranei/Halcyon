package com.ella.music.data.repository

import android.content.Context
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * On-disk persistence for the music library snapshots (primary cache, local scan baseline and
 * per-source remote library caches). Logic moved verbatim from [MusicRepository]; this class only
 * reads/writes disk — applying results to the in-memory StateFlows stays in the repository.
 */
internal class MusicLibraryCacheStore(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    val libraryCacheFile = File(context.filesDir, "music_library_cache.json")
    val localScanBaselineFile = File(context.filesDir, "music_local_scan_baseline.json")

    suspend fun remoteLibraryCacheFile(source: String): File {
        if (source == SettingsManager.LIBRARY_SOURCE_WEBDAV || source == SettingsManager.LIBRARY_SOURCE_SMB) {
            val identity = if (source == SettingsManager.LIBRARY_SOURCE_WEBDAV) listOf(
                settingsManager.webDavUrl.first().trim().trimEnd('/'), settingsManager.webDavUsername.first(),
                settingsManager.webDavCustomHeaders.first().toString()
            ) else listOf(settingsManager.smbUrl.first().trim().trimEnd('/'), settingsManager.smbUsername.first(),
                settingsManager.smbDomain.first(), settingsManager.smbProtocol.first())
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity.joinToString("\u0000").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(24)
            return File(context.filesDir, "remote_library_${source}_$key.json")
        }
        if (source in setOf(SettingsManager.LIBRARY_SOURCE_FTP, SettingsManager.LIBRARY_SOURCE_SFTP)) {
            val config = settingsManager.transferLibraryConfig(source).first()
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest("${config.url}\u0000${config.username}\u0000${config.hostKey}".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(24)
            return File(context.filesDir, "remote_library_${source}_$key.json")
        }
        val pluginId = com.ella.music.data.sourceplugin.sourcePluginKeyOf(source)
        if (pluginId.isNotBlank()) {
            val userId = com.ella.music.data.sourceplugin.SourcePluginRegistry
                .accountStore(context).account(pluginId)?.userId.orEmpty()
            return File(context.filesDir, "remote_library_plugin_${pluginId}_$userId.json")
        }
        return File(context.filesDir, if (source == SettingsManager.LIBRARY_SOURCE_NETEASE)
            "remote_library_netease_${com.ella.music.data.netease.NeteaseAccountStore.getInstance(context).account.value.userId}.json"
        else "remote_library_$source.json")
    }

    fun saveLibraryCacheTo(file: File, songs: List<Song>, albums: List<Album>) {
        runCatching {
            writeLibrarySnapshot(file, songs, albums)
        }.onFailure {
            Log.w("MusicRepo", "Failed to save library cache snapshot", it)
        }
    }

    suspend fun saveLibraryCache(songs: List<Song>, albums: List<Album>) = withContext(Dispatchers.IO) {
        // Persist to the source-appropriate cache: keep the local cache intact while a remote
        // library is active so mutations (e.g. remove-from-library) don't clobber the local cache
        // that restores when switching back to Local.
        val source = settingsManager.librarySource.first()
        val targetFile = if (source == SettingsManager.LIBRARY_SOURCE_LOCAL) {
            libraryCacheFile
        } else {
            remoteLibraryCacheFile(source)
        }
        runCatching {
            writeLibrarySnapshot(targetFile, songs, albums)
        }.onFailure {
            Log.w("MusicRepo", "Failed to save music library cache", it)
        }
    }

    fun readCachedSongs(): List<Song> {
        if (!hasLibraryCache(libraryCacheFile)) return emptyList()
        return runCatching {
            readLibraryCacheSongs(libraryCacheFile)
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read music library cache for sync", it)
            emptyList()
        }
    }

    fun readLocalScanBaselineSongs(): List<Song> {
        val baselineFile = if (hasLibraryCache(localScanBaselineFile)) localScanBaselineFile else libraryCacheFile
        if (!hasLibraryCache(baselineFile)) return emptyList()
        return runCatching {
            readLibraryCacheSongs(baselineFile)
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read local scan baseline", it)
            emptyList()
        }
    }

    fun saveLocalScanBaseline(songs: List<Song>, albums: List<Album>) {
        saveLibraryCacheTo(localScanBaselineFile, songs, albums)
    }
}
