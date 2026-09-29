package com.ella.music.data.scanner

import android.content.Context
import android.util.Log
import com.ella.music.data.model.Song
import com.ella.music.data.repository.writeLibraryCacheAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class PersistentMetadataCache internal constructor(
    private val cacheFile: File,
    private val records: ConcurrentHashMap<String, CachedMetadata>
) {
    @Volatile private var dirty: Boolean = false

    fun get(song: Song): Song? {
        val key = cacheKey(song.path, song.fileSize, song.dateModified)
        val record = records[key] ?: return null
        return record.applyTo(song)
    }

    /**
     * @param folderAlbumConfirmed true when [Song.album] equals the parent folder name but was
     * confirmed from the file's own tags, so later scans must not keep re-enriching it (#675).
     */
    fun put(song: Song, folderAlbumConfirmed: Boolean = false) {
        if (song.path.isBlank() || song.fileSize <= 0L || song.dateModified <= 0L) return
        val key = cacheKey(song.path, song.fileSize, song.dateModified)
        val record = CachedMetadata.from(song, folderAlbumConfirmed)
        if (records[key] != record) {
            records[key] = record
            dirty = true
        }
    }

    fun contains(song: Song): Boolean {
        val key = cacheKey(song.path, song.fileSize, song.dateModified)
        return records.containsKey(key)
    }

    fun isFolderAlbumConfirmed(song: Song): Boolean {
        val key = cacheKey(song.path, song.fileSize, song.dateModified)
        return records[key]?.folderAlbumConfirmed == true
    }

    fun size(): Int = records.size

    fun clear() {
        records.clear()
        dirty = true
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        if (!dirty) return@withContext
        val t0 = System.currentTimeMillis()
        val snapshot = records.values.toList()
        runCatching {
            writeLibraryCacheAtomically(cacheFile) { writer ->
                writer.write("{\"version\":$CACHE_VERSION,\"items\":[")
                snapshot.forEachIndexed { index, record ->
                    if (index > 0) writer.write(",")
                    writer.write(record.toJson().toString())
                }
                writer.write("]}")
            }
            dirty = false
            Log.d(TAG, "save: items=${snapshot.size} time=${System.currentTimeMillis() - t0}ms bytes=${cacheFile.length()}")
        }.onFailure { error ->
            Log.w(TAG, "Failed to save persistent metadata cache", error)
        }
    }

    companion object {
        private const val TAG = "MetadataCache"
        private const val CACHE_VERSION = 1
        private const val FILE_NAME = "music_metadata_cache.json"

        fun cacheKey(path: String, fileSize: Long, dateModified: Long): String =
            "${path.trim().lowercase()}|$fileSize|$dateModified"

        fun load(context: Context): PersistentMetadataCache {
            val file = File(context.filesDir, FILE_NAME)
            val map = ConcurrentHashMap<String, CachedMetadata>()
            if (file.exists()) {
                val t0 = System.currentTimeMillis()
                runCatching {
                    val root = JSONObject(file.readText(Charsets.UTF_8))
                    val items = root.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val obj = items.optJSONObject(i) ?: continue
                        val item = CachedMetadata.fromJson(obj) ?: continue
                        map[cacheKey(item.path, item.fileSize, item.dateModified)] = item
                    }
                    Log.d(TAG, "load: items=${map.size} time=${System.currentTimeMillis() - t0}ms")
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load persistent metadata cache, starting empty", error)
                }
            }
            return PersistentMetadataCache(file, map)
        }
    }

    internal data class CachedMetadata(
        val path: String,
        val fileSize: Long,
        val dateModified: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val year: String,
        val composer: String,
        val arranger: String,
        val lyricist: String,
        val trackNumber: Int,
        val discNumber: Int,
        val folderAlbumConfirmed: Boolean = false
    ) {
        fun applyTo(shallow: Song): Song = shallow.copy(
            title = title.ifBlank { shallow.title },
            artist = artist.ifBlank { shallow.artist },
            album = album.ifBlank { shallow.album },
            albumArtist = albumArtist.ifBlank { shallow.albumArtist },
            genre = genre.ifBlank { shallow.genre },
            year = year.ifBlank { shallow.year },
            composer = composer.ifBlank { shallow.composer },
            arranger = arranger.ifBlank { shallow.arranger },
            lyricist = lyricist.ifBlank { shallow.lyricist },
            trackNumber = if (trackNumber > 0) trackNumber else shallow.trackNumber,
            discNumber = if (discNumber > 0) discNumber else shallow.discNumber
        )

        fun toJson(): JSONObject = JSONObject()
            .put("path", path)
            .put("fileSize", fileSize)
            .put("dateModified", dateModified)
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("albumArtist", albumArtist)
            .put("genre", genre)
            .put("year", year)
            .put("composer", composer)
            .put("arranger", arranger)
            .put("lyricist", lyricist)
            .put("trackNumber", trackNumber)
            .put("discNumber", discNumber)
            .apply { if (folderAlbumConfirmed) put("folderAlbumConfirmed", true) }

        companion object {
            fun from(song: Song, folderAlbumConfirmed: Boolean = false): CachedMetadata = CachedMetadata(
                path = song.path,
                fileSize = song.fileSize,
                dateModified = song.dateModified,
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumArtist = song.albumArtist,
                genre = song.genre,
                year = song.year,
                composer = song.composer,
                arranger = song.arranger,
                lyricist = song.lyricist,
                trackNumber = song.trackNumber,
                discNumber = song.discNumber,
                folderAlbumConfirmed = folderAlbumConfirmed
            )

            fun fromJson(obj: JSONObject): CachedMetadata? {
                val path = obj.optString("path", "").trim()
                if (path.isBlank()) return null
                val fileSize = obj.optLong("fileSize", 0L)
                val dateModified = obj.optLong("dateModified", 0L)
                return CachedMetadata(
                    path = path,
                    fileSize = fileSize,
                    dateModified = dateModified,
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    album = obj.optString("album", ""),
                    albumArtist = obj.optString("albumArtist", ""),
                    genre = obj.optString("genre", ""),
                    year = obj.optString("year", ""),
                    composer = obj.optString("composer", ""),
                    arranger = obj.optString("arranger", ""),
                    lyricist = obj.optString("lyricist", ""),
                    trackNumber = obj.optInt("trackNumber", 0),
                    discNumber = obj.optInt("discNumber", 0),
                    folderAlbumConfirmed = obj.optBoolean("folderAlbumConfirmed", false)
                )
            }
        }
    }
}
