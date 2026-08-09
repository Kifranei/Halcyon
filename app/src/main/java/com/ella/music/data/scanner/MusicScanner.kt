package com.ella.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.metadata.WavMetadataReader
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.looksLikeNeteaseKeyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class MusicScanner(private val context: Context) {
    private val audioTagReader = LyricoAudioTagReaderWriter()

    companion object {
        private const val TAG = "MusicScanner"

        private val DEFAULT_EXCLUDE_FOLDERS = listOf(
            "/storage/emulated/0/Music/Recordings"
        )
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "opus", "aac", "m4a", "mp4",
            "wav", "wave", "wma", "aiff", "aif", "ape", "alac"
        )
    }

    suspend fun enumerateAudioFiles(
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList()
    ): List<MediaStoreAudioItem> = withContext(Dispatchers.IO) {
        val items = queryMediaStoreAudioItems(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders,
            verifyFileSnapshot = false
        )
        val fallbackItems = filesystemFallbackAudioItems(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders,
            existingPaths = items.map { it.path }.toSet()
        )
        val (merged, stats) = mergeMediaStoreAndFilesystemItems(items, fallbackItems)
        Log.i(
            TAG,
            "enumerateAudioFiles mediaStore=${stats.mediaStoreItemCount} filesystemFallback=${stats.filesystemFallbackItemCount} merged=${stats.mergedItemCount}"
        )
        merged
    }

    suspend fun scanAudioItem(
        item: MediaStoreAudioItem,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false
    ): Song? = withContext(Dispatchers.IO) {
        var title = item.title
        var artist = item.artist
        var album = item.album
        var albumArtist = ""
        var genre = ""
        var year = ""
        var composer = ""
        var arranger = ""
        var lyricist = ""
        var duration = item.duration
        var trackNumber = item.trackNumber
        var discNumber = item.discNumber
        val file = File(item.path)
        if (!file.exists()) return@withContext null

        val shouldDeepRead = deepMetadata ||
            isMissingTag(title, file.name) ||
            isMissingArtistTag(artist) ||
            isMissingAlbumTag(album) ||
            duration <= 0

        val tagInfo = if (shouldDeepRead) readTagsBlocking(item.path) else null

        if (tagInfo != null) {
            if (deepMetadata) {
                title = tagInfo.title.orEmpty()
                artist = tagInfo.artist.orEmpty()
                album = tagInfo.album.orEmpty()
            } else {
                if (isMissingTag(title, file.name)) title = tagInfo.title.orEmpty()
                if (isMissingArtistTag(artist)) artist = tagInfo.artist.orEmpty()
                if (isMissingAlbumTag(album)) album = tagInfo.album.orEmpty()
            }
            albumArtist = tagInfo.albumArtist.orEmpty()
            genre = tagInfo.genre.orEmpty()
            year = tagInfo.year.orEmpty().normalizeReleaseDate()
            composer = tagInfo.composer.orEmpty()
            arranger = firstNonBlank(
                tagInfo.arranger,
                tagInfo.customTagValue("ARRANGER", "ARRANGED BY", "ARRANGEDBY", "ARRANGEMENT", "ARRANGE")
            ).orEmpty()
            lyricist = firstNonBlank(
                tagInfo.lyricist,
                tagInfo.customTagValue("TEXT"),
                tagInfo.customTagValue("WRITER")
            ).orEmpty()
            trackNumber = trackNumber.takeIf { it > 0 } ?: tagInfo.trackNumber ?: firstNonBlank(
                tagInfo.customTagValue("TRACKNUMBER"),
                tagInfo.customTagValue("TRACK"),
                tagInfo.customTagValue("TRCK")
            ).orEmpty().normalizedTrackNumberFromTag()
            discNumber = discNumber.takeIf { it > 0 } ?: firstNonBlank(
                tagInfo.discNumber?.toString(),
                tagInfo.customTagValue("DISC"),
                tagInfo.customTagValue("TPOS")
            ).orEmpty().normalizedDiscNumberFromTag()
        }

        // WAV files always try WavMetadataReader — MediaStore/Lyrico may not read LIST/INFO chunks
        if (file.extension.lowercase() in setOf("wav", "wave")) {
            WavMetadataReader.read(file)?.let { wavInfo ->
                if (duration <= 0) duration = wavInfo.durationMs
                if (isMissingTag(title, file.name)) title = wavInfo.title.orEmpty()
                if (isMissingArtistTag(artist)) artist = wavInfo.artist.orEmpty()
                if (isMissingAlbumTag(album)) album = wavInfo.album.orEmpty()
                if (albumArtist.isBlank()) albumArtist = wavInfo.albumArtist.orEmpty()
                if (genre.isBlank()) genre = wavInfo.genre.orEmpty()
                if (year.isBlank()) year = wavInfo.year.orEmpty().normalizeReleaseDate()
                if (composer.isBlank()) composer = wavInfo.composer.orEmpty()
                if (arranger.isBlank()) arranger = wavInfo.arranger.orEmpty()
                if (lyricist.isBlank()) lyricist = wavInfo.lyricist.orEmpty()
                trackNumber = trackNumber.takeIf { it > 0 } ?: wavInfo.trackNumber ?: 0
                discNumber = discNumber.takeIf { it > 0 } ?: wavInfo.discNumber ?: 0
            }
        } else if (shouldDeepRead || deepMetadata) {
            WavMetadataReader.read(file)?.let { wavInfo ->
                if (isMissingTag(title, file.name)) title = wavInfo.title.orEmpty()
                if (isMissingArtistTag(artist)) artist = wavInfo.artist.orEmpty()
                if (isMissingAlbumTag(album)) album = wavInfo.album.orEmpty()
                if (albumArtist.isBlank()) albumArtist = wavInfo.albumArtist.orEmpty()
                if (genre.isBlank()) genre = wavInfo.genre.orEmpty()
                if (year.isBlank()) year = wavInfo.year.orEmpty().normalizeReleaseDate()
                if (composer.isBlank()) composer = wavInfo.composer.orEmpty()
                if (arranger.isBlank()) arranger = wavInfo.arranger.orEmpty()
                if (lyricist.isBlank()) lyricist = wavInfo.lyricist.orEmpty()
                trackNumber = trackNumber.takeIf { it > 0 } ?: wavInfo.trackNumber ?: 0
                discNumber = discNumber.takeIf { it > 0 } ?: wavInfo.discNumber ?: 0
            }
        }

        if (shouldDeepRead && (isMissingTag(title, file.name) || isMissingArtistTag(artist) || isMissingAlbumTag(album) || duration <= 0)) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(item.path)
                if (isMissingTag(title, file.name)) title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                if (isMissingArtistTag(artist)) artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                if (isMissingAlbumTag(album)) album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                if (duration <= 0) duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Metadata extraction failed for ${item.path}", e)
            }
        }

        if (isMissingTag(title, file.name)) title = item.fileName.substringBeforeLast('.')
        if (isMissingArtistTag(artist)) artist = "Unknown Artist"
        if (isMissingAlbumTag(album)) album = "Unknown Album"

        if (duration <= 0 || duration < minDurationMs) return@withContext null

        Song(
            id = item.id,
            title = title,
            artist = artist,
            album = album,
            albumId = item.albumId,
            duration = duration,
            path = item.path,
            fileName = item.fileName,
            fileSize = item.fileSize,
            mimeType = item.mimeType,
            dateAdded = item.dateAdded,
            dateModified = item.dateModified,
            trackNumber = trackNumber,
            discNumber = discNumber,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            composer = composer,
            arranger = arranger,
            lyricist = lyricist
        )
    }

    suspend fun scanAllSongs(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        deepMetadata: Boolean = false,
        filesystemFallbackFolders: List<String> = includeFolders,
        onProgress: ((Int) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val mediaStoreItems = queryMediaStoreAudioItems(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders,
            verifyFileSnapshot = deepMetadata
        )
        mediaStoreItems.forEachIndexed { index, item ->
            val song = runCatching {
                if (deepMetadata) {
                    scanAudioItem(item, minDurationMs = minDurationMs, deepMetadata = true)
                } else {
                    item.toShallowSong(minDurationMs)
                        ?: scanAudioItem(item, minDurationMs = minDurationMs, deepMetadata = false)
                }
            }.onFailure { error ->
                Log.w(TAG, "scanAllSongs item failed for ${item.path}", error)
            }.getOrNull()
            if (song != null) songs += song
            onProgress?.invoke(index + 1)
        }
        val mediaStoreSongCount = songs.size
        val fallbackItems = filesystemFallbackAudioItems(
            includeFolders = filesystemFallbackFolders,
            excludeFolders = excludeFolders,
            existingPaths = songs.map { it.path }.toSet()
        )
        fallbackItems.forEach { item ->
            runCatching {
                scanAudioItem(item, minDurationMs = minDurationMs, deepMetadata = deepMetadata)
            }.onFailure { error ->
                Log.w(TAG, "scanAllSongs fallback item failed for ${item.path}", error)
            }.getOrNull()?.let { song ->
                songs.add(song)
                onProgress?.invoke(songs.size)
            }
        }
        Log.i(
            TAG,
            "scanAllSongs mediaStore=$mediaStoreSongCount filesystemFallback=${songs.size - mediaStoreSongCount} total=${songs.size} deepMetadata=$deepMetadata fallbackFolders=${filesystemFallbackFolders.size}"
        )
        songs
    }

    private fun queryMediaStoreAudioItems(
        includeFolders: List<String>,
        excludeFolders: List<String>,
        verifyFileSnapshot: Boolean
    ): List<MediaStoreAudioItem> {
        val items = mutableListOf<MediaStoreAudioItem>()
        val normalizedIncludeFolders = includeFolders.mapNotNull { it.normalizedFolderPath() }
        val normalizedExcludeFolders = (DEFAULT_EXCLUDE_FOLDERS + excludeFolders).mapNotNull { it.normalizedFolderPath() }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK
        )
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol).orEmpty()
                if (path.isEmpty()) continue
                if (!path.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)) continue

                val file = if (verifyFileSnapshot) File(path) else null
                if (file != null && !file.exists()) continue

                val rawTrackNumber = cursor.getInt(trackCol)
                val mediaStoreSize = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val mediaStoreModified = cursor.getLong(dateModifiedCol).takeIf { it > 0L }?.times(1000L) ?: 0L

                items += MediaStoreAudioItem(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = cursor.getLong(albumIdCol),
                    duration = cursor.getLong(durationCol),
                    path = path,
                    fileName = cursor.getString(nameCol).orEmpty(),
                    fileSize = file?.length()?.takeIf { it > 0L } ?: mediaStoreSize,
                    mimeType = cursor.getString(mimeCol).orEmpty(),
                    dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                    dateModified = file?.lastModified()?.takeIf { it > 0L } ?: mediaStoreModified,
                    trackNumber = rawTrackNumber.normalizedTrackNumber(),
                    discNumber = rawTrackNumber.normalizedDiscNumber()
                )
            }
        }
        return items
    }

    internal fun filesystemFallbackAudioItems(
        includeFolders: List<String>,
        excludeFolders: List<String>,
        existingPaths: Set<String> = emptySet()
    ): List<MediaStoreAudioItem> {
        if (includeFolders.isEmpty()) return emptyList()
        val normalizedIncludeFolders = includeFolders.mapNotNull { it.normalizedFolderPath() }
        if (normalizedIncludeFolders.isEmpty()) return emptyList()
        val normalizedExcludeFolders = (DEFAULT_EXCLUDE_FOLDERS + excludeFolders).mapNotNull { it.normalizedFolderPath() }
        val existingKeys = existingPaths.mapTo(HashSet()) { it.normalizedAudioPathKey() }
        val fallback = mutableListOf<MediaStoreAudioItem>()
        includeFolders
            .asSequence()
            .filterNot { it == "__ella_no_custom_folder__" }
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .distinctBy { it.absolutePath.normalizedAudioPathKey() }
            .forEach { root ->
                runCatching {
                    root.walkTopDown()
                        .onEnter { dir ->
                            dir.absolutePath.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)
                        }
                        .filter { file ->
                            file.isFile &&
                                file.extension.lowercase() in AUDIO_EXTENSIONS &&
                                file.absolutePath.isAllowedByFolderFilters(normalizedIncludeFolders, normalizedExcludeFolders)
                        }
                        .forEach { file ->
                            val path = file.absolutePath
                            val key = path.normalizedAudioPathKey()
                            if (key.isBlank() || key in existingKeys) return@forEach
                            existingKeys += key
                            fallback += file.toFallbackAudioItem()
                        }
                }.onFailure { error ->
                    Log.w(TAG, "Filesystem fallback scan failed for ${root.absolutePath}", error)
                }
            }
        return fallback
    }

    /**
     * Scan audio files from a SAF document tree URI (e.g. USB drive).
     * Returns a list of songs found recursively under the given URI.
     */
    suspend fun scanUsbFolder(
        treeUri: Uri,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, documentId
            )
            scanDocumentTreeRecursive(
                context, treeUri, childrenUri, songs, minDurationMs, deepMetadata, onProgress
            )
        } catch (e: Exception) {
            Log.w(TAG, "USB folder scan failed for $treeUri", e)
        }
        songs
    }

    private fun scanDocumentTreeRecursive(
        context: Context,
        rootTreeUri: Uri,
        childrenUri: Uri,
        songs: MutableList<Song>,
        minDurationMs: Long,
        deepMetadata: Boolean,
        onProgress: ((Int) -> Unit)?
    ) {
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.DocumentsContract.Document.COLUMN_SIZE,
            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val docIdCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                val modifiedCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(docIdCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val lastModified = cursor.getLong(modifiedCol) * 1000L

                    if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subChildrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                            rootTreeUri, docId
                        )
                        scanDocumentTreeRecursive(
                            context, rootTreeUri, subChildrenUri, songs, minDurationMs, deepMetadata, onProgress
                        )
                        continue
                    }

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in AUDIO_EXTENSIONS) continue

                    val songUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                    var title = name.substringBeforeLast('.')
                    var artist = ""
                    var album = ""
                    var albumArtist = ""
                    var genre = ""
                    var year = ""
                    var composer = ""
                    var arranger = ""
                    var lyricist = ""
                    var duration = 0L
                    var trackNumber = 0
                    var discNumber = 0
                    var albumId = 0L

                    if (deepMetadata || title.isBlank()) {
                        try {
                            context.contentResolver.openFileDescriptor(songUri, "r")?.use { pfd ->
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                    val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                    val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    if (!metaTitle.isNullOrBlank()) title = metaTitle
                                    if (!metaArtist.isNullOrBlank()) artist = metaArtist
                                    if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                                    if (metaDuration != null) duration = metaDuration.toLongOrNull() ?: 0L
                                } finally {
                                    retriever.release()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Metadata extraction failed for USB file $name", e)
                        }
                    }

                    title = title.cleanTagText().ifBlank { name.substringBeforeLast('.') }
                    artist = LibraryNormalizer.cleanedArtistText(artist).ifBlank { "Unknown Artist" }
                    album = LibraryNormalizer.cleanedAlbumText(album).ifBlank { "Unknown Album" }

                    if (duration > 0 && duration >= minDurationMs) {
                        val stableId = kotlin.math.abs(songUri.hashCode().toLong()).takeIf { it != 0L } ?: 1L
                        songs.add(
                            Song(
                                id = stableId,
                                title = title,
                                artist = artist,
                                album = album,
                                albumId = albumId,
                                duration = duration,
                                path = songUri.toString(),
                                fileName = name,
                                fileSize = size,
                                mimeType = mimeType.substringBefore(';').trim().lowercase(),
                                dateAdded = System.currentTimeMillis(),
                                dateModified = lastModified,
                                trackNumber = trackNumber,
                                discNumber = discNumber,
                                albumArtist = albumArtist,
                                genre = genre,
                                year = year,
                                composer = composer,
                                arranger = arranger,
                                lyricist = lyricist
                            )
                        )
                        onProgress?.invoke(songs.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning SAF document tree", e)
        }
    }

    /**
     * Check if a SAF URI is still accessible (USB drive connected).
     */
    fun isUsbUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun scanAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Album>()
        val collection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.FIRST_YEAR
        )
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Audio.Albums.ALBUM} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                albums.add(Album(
                    cursor.getLong(0),
                    LibraryNormalizer.cleanedAlbumText(cursor.getString(1)).ifBlank { "Unknown Album" },
                    LibraryNormalizer.cleanedArtistText(cursor.getString(2)).ifBlank { "Unknown Artist" },
                    cursor.getInt(3),
                    cursor.getInt(4).takeIf { it > 0 }?.toString() ?: ""
                ))
            }
        }
        albums
    }

    fun extractEmbeddedLyrics(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null

        val audioFileLyrics = readTagsBlocking(path)
            ?.lyrics
            ?.takeIf { it.isUsableSynchronizedLyrics() }
        if (!audioFileLyrics.isNullOrBlank()) {
            Log.d(TAG, "Found embedded lyrics (${audioFileLyrics.length} chars) for ${file.name}")
            return audioFileLyrics
        }

        return runCatching {
            MediaMetadataRetriever().useCompat { retriever ->
                retriever.setDataSource(path)
                val lyrics = retriever.extractMetadata(1000)
                if (!lyrics.isNullOrBlank()) {
                    Log.d(TAG, "Found retriever lyrics (${lyrics.length} chars) for ${file.name}")
                    lyrics
                } else null
            }
        }.onFailure {
            Log.w(TAG, "Retriever lyrics extraction failed for $path", it)
        }.getOrNull()
    }

    fun extractCoverArt(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null

        val audioFileArt = readEmbeddedCoverBlocking(path)
        if (audioFileArt != null) return audioFileArt

        return runCatching {
            MediaMetadataRetriever().useCompat { retriever ->
                retriever.setDataSource(path)
                retriever.embeddedPicture
            }
        }.onFailure {
            Log.w(TAG, "Retriever cover art extraction failed for $path", it)
        }.getOrNull()
    }

    fun extractReplayGain(path: String, mode: Int = SettingsManager.REPLAY_GAIN_AUTO): Float? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            readTagsBlocking(path)
                ?.let { tagInfo ->
                    tagInfo.replayGainForMode(mode)
                }
                ?.let { return it }
            null
        } catch (e: Exception) {
            Log.w(TAG, "ReplayGain extraction failed for $path", e)
            null
        }
    }

    fun extractSongTagInfo(path: String): SongTagInfo {
        val file = File(path)
        if (!file.exists() || !file.isFile) return SongTagInfo()

        val tagInfo = readTagsBlocking(path) ?: AudioTagInfo()

        return SongTagInfo(
            title = tagInfo.title.orEmpty().cleanTagText(),
            artist = tagInfo.artist.orEmpty().cleanTagText(),
            album = LibraryNormalizer.cleanedAlbumText(tagInfo.album),
            albumArtist = LibraryNormalizer.cleanedArtistText(tagInfo.albumArtist),
            genre = tagInfo.genre.orEmpty().cleanTagText(),
            year = tagInfo.year.orEmpty().cleanTagText(),
            composer = tagInfo.composer.orEmpty().cleanTagText(),
            arranger = firstNonBlank(
                tagInfo.arranger,
                tagInfo.customTagValue("ARRANGER", "ARRANGED BY", "ARRANGEDBY", "ARRANGEMENT", "ARRANGE")
            ).orEmpty().cleanTagText(),
            lyricist = firstNonBlank(
                tagInfo.lyricist,
                tagInfo.customTagValue("TEXT"),
                tagInfo.customTagValue("WRITER")
            ).orEmpty().cleanTagText(),
            track = tagInfo.trackNumber?.toString().orEmpty().cleanTagText(),
            comment = tagInfo.comment.orEmpty().cleanTagText(),
            copyright = tagInfo.copyright.orEmpty().cleanTagText(),
            neteaseKey = tagInfo.neteaseKey.orEmpty()
                .takeIf { it.looksLikeNeteaseKeyValue() }
                .orEmpty()
                .ifBlank { tagInfo.comment.orEmpty().extractPrefixedNeteaseCommentKey() }
                .cleanTagText(),
            rating = ratingStarsFromTagValues(tagInfo.rating?.toString()),
            lyrics = tagInfo.lyrics.orEmpty().cleanTagText(),
            customTagText = tagInfo.customTags.flattenForSearch(),
            customTags = tagInfo.customTags
        )
    }

    fun getAlbumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    private fun readTagsBlocking(path: String): AudioTagInfo? =
        runBlocking(Dispatchers.IO) {
            runCatching { audioTagReader.readTags(path) }
                .onFailure { Log.d(TAG, "lyrico-audiotag tag read failed for $path", it) }
                .getOrNull()
        }

    private fun readEmbeddedCoverBlocking(path: String): ByteArray? =
        runBlocking(Dispatchers.IO) {
            runCatching { audioTagReader.readEmbeddedCover(path)?.bytes }
                .onFailure { Log.d(TAG, "lyrico-audiotag artwork unavailable for $path", it) }
                .getOrNull()
        }
}
