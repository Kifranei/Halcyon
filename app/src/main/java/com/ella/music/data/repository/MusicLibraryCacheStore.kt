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
import org.json.JSONObject

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

    fun remoteLibraryCacheFile(source: String): File =
        File(context.filesDir, "remote_library_$source.json")

    fun saveLibraryCacheTo(file: File, songs: List<Song>, albums: List<Album>) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("songs", songsToLibraryCacheJsonArray(songs))
                .put("albums", albumsToLibraryCacheJsonArray(albums))
            writeLibraryCacheAtomically(file, root.toString())
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
            val root = JSONObject()
                .put("version", 1)
                .put("songs", songsToLibraryCacheJsonArray(songs))
                .put("albums", albumsToLibraryCacheJsonArray(albums))
            writeLibraryCacheAtomically(targetFile, root.toString())
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
