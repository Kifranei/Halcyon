package com.ella.music.data.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.looksLikeNeteaseKeyValue
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.rw.AudioTagReader as LyricoReader
import com.lonx.audiotag.rw.AudioTagWriter as LyricoWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class AudioTagInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val arranger: String? = null,
    val lyricist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val copyright: String? = null,
    val neteaseKey: String? = null,
    val rating: Int? = null,
    val replayGainTrackGain: String? = null,
    val replayGainTrackPeak: String? = null,
    val replayGainAlbumGain: String? = null,
    val replayGainAlbumPeak: String? = null,
    val replayGainReferenceLoudness: String? = null,
    val customTags: Map<String, List<String>> = emptyMap()
)

data class AudioCoverInfo(
    val bytes: ByteArray,
    val mimeType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioCoverInfo) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (mimeType?.hashCode() ?: 0)
}

data class AudioQualityInfo(
    val mimeType: String = "",
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0
)

interface AudioTagReader {
    suspend fun readTags(path: String): AudioTagInfo?
    suspend fun readEmbeddedCover(path: String): AudioCoverInfo?
    suspend fun readEmbeddedLyrics(path: String): String?
}

interface AudioTagWriter {
    suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit>
    suspend fun writeTags(pfd: ParcelFileDescriptor, tags: AudioTagInfo): Result<Unit> =
        Result.failure(UnsupportedOperationException("Writing tags through a file descriptor is not supported"))
    suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit>
    suspend fun writeEmbeddedCover(pfd: ParcelFileDescriptor, cover: AudioCoverInfo): Result<Unit> =
        Result.failure(UnsupportedOperationException("Writing cover through a file descriptor is not supported"))
    suspend fun removeEmbeddedCover(path: String): Result<Unit>
    suspend fun removeEmbeddedCover(pfd: ParcelFileDescriptor): Result<Unit> =
        Result.failure(UnsupportedOperationException("Removing cover through a file descriptor is not supported"))
}

class AudioTagRepository(
    private val primary: AudioTagReader,
    private val writer: AudioTagWriter? = primary as? AudioTagWriter
) {
    private val coverDataCache = object : LruCache<String, AudioCoverInfo>(8 * 1024) {
        override fun sizeOf(key: String, value: AudioCoverInfo): Int = value.bytes.size / 1024
    }
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val lyricsCache = LruCache<String, String>(64)
    private val tagsCache = LruCache<String, AudioTagInfo>(64)
    private val qualityCache = LruCache<String, AudioQualityInfo>(64)

    suspend fun readTags(path: String): AudioTagInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        tagsCache.get(key)?.let { return@withContext it }
        var tags = readWithFallback("tags", path) { it.readTags(path) }?.takeIf { it.hasUsefulTagData() }
        // For WAV files, always try to supplement missing fields from WavMetadataReader
        // because LyricoAudioTagReader may return non-null but empty-tag AudioTagInfo
        if (isWavFile(path)) {
            val wavTags = WavMetadataReader.read(path)?.toAudioTagInfo()
            if (wavTags != null) {
                tags = if (tags != null) {
                    tags.mergeMissingFieldsFrom(wavTags)
                } else {
                    wavTags.takeIf { it.hasUsefulTagData() }
                }
            }
        }
        if (tags == null) {
            tags = WavMetadataReader.read(path)?.toAudioTagInfo()?.takeIf { it.hasUsefulTagData() }
        }
        tags?.also { tagsCache.put(key, it) }
    }

    suspend fun readEmbeddedCover(path: String): AudioCoverInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        coverDataCache.get(key)?.let { return@withContext it }
        val cover = readWithFallback("cover", path) { it.readEmbeddedCover(path) }?.takeIf { it.bytes.isNotEmpty() }
        cover?.also { coverDataCache.put(key, it) }
    }

    suspend fun readEmbeddedLyrics(path: String): String? = withContext(Dispatchers.IO) {
        val key = cacheKey(path) ?: return@withContext null
        lyricsCache.get(key)?.let { return@withContext it }
        val lyrics = readWithFallback("lyrics", path) { it.readEmbeddedLyrics(path) }?.takeIf { it.isNotBlank() }
        lyrics?.also { lyricsCache.put(key, it) }
    }

    suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit> =
        writer?.writeTags(path, tags)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun writeTags(pfd: ParcelFileDescriptor, tags: AudioTagInfo): Result<Unit> =
        writer?.writeTags(pfd, tags) ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit> =
        writer?.writeEmbeddedCover(path, cover)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun writeEmbeddedCover(pfd: ParcelFileDescriptor, pathForCache: String, cover: AudioCoverInfo): Result<Unit> =
        writer?.writeEmbeddedCover(pfd, cover)?.onSuccess { clear(pathForCache) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun removeEmbeddedCover(path: String): Result<Unit> =
        writer?.removeEmbeddedCover(path)?.onSuccess { clear(path) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    suspend fun removeEmbeddedCover(pfd: ParcelFileDescriptor, pathForCache: String): Result<Unit> =
        writer?.removeEmbeddedCover(pfd)?.onSuccess { clear(pathForCache) } ?: Result.failure(UnsupportedOperationException("No audio tag writer"))

    fun readTagsBlocking(path: String): AudioTagInfo? = runBlocking(Dispatchers.IO) { readTags(path) }

    fun readEmbeddedCoverDataBlocking(path: String): ByteArray? =
        runBlocking(Dispatchers.IO) { readEmbeddedCover(path)?.bytes }

    fun readEmbeddedCoverBitmapBlocking(path: String, maxSize: Int = 512): Bitmap? {
        val key = cacheKey(path)?.let { "$it:${maxSize.coerceIn(64, 3000)}" } ?: return null
        coverBitmapCache.get(key)?.let { return it }
        val data = readEmbeddedCoverDataBlocking(path) ?: return null
        return decodeCoverBitmap(data, maxSize)?.also { coverBitmapCache.put(key, it) }
    }

    fun readEmbeddedLyricsBlocking(path: String): String? = runBlocking(Dispatchers.IO) { readEmbeddedLyrics(path) }

    fun readQualityInfoBlocking(path: String): AudioQualityInfo? {
        val key = cacheKey(path) ?: return null
        qualityCache.get(key)?.let { return it }
        val wavMetadata = WavMetadataReader.read(path)
        val quality = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                var audioFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioFormat = format
                        break
                    }
                }
                val format = audioFormat
                    ?: return@runCatching wavMetadata?.takeIf { it.hasQuality }?.toAudioQualityInfo()
                AudioQualityInfo(
                    mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty().ifBlank {
                        wavMetadata?.takeIf { it.hasQuality }?.let { "audio/wav" }.orEmpty()
                    },
                    bitRate = format.getIntOrZero(MediaFormat.KEY_BIT_RATE).takeIf { it > 0 }
                        ?: wavMetadata?.bitRate ?: 0,
                    sampleRate = format.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 }
                        ?: wavMetadata?.sampleRate ?: 0,
                    bitDepth = format.getIntOrZero("bits-per-sample").takeIf { it > 0 }
                        ?: wavMetadata?.bitDepth ?: 0,
                    channels = format.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 }
                        ?: wavMetadata?.channels ?: 0
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w(TAG, "Failed to read quality info for $path", it)
            wavMetadata?.takeIf { metadata -> metadata.hasQuality }?.toAudioQualityInfo()
        }
        return quality?.also { qualityCache.put(key, it) }
    }

    fun clear(path: String) {
        val key = cacheKey(path) ?: return
        coverDataCache.remove(key)
        lyricsCache.remove(key)
        tagsCache.remove(key)
        qualityCache.remove(key)
        for (bitmapKey in coverBitmapCache.snapshot().keys) {
            if (bitmapKey.startsWith("$key:")) coverBitmapCache.remove(bitmapKey)
        }
    }

    fun clearCache() {
        coverDataCache.evictAll()
        coverBitmapCache.evictAll()
        lyricsCache.evictAll()
        tagsCache.evictAll()
        qualityCache.evictAll()
    }

    private suspend fun <T> readWithFallback(
        label: String,
        path: String,
        block: suspend (AudioTagReader) -> T?
    ): T? {
        return runCatching { block(primary) }
            .getOrElse { error ->
                // A memory-pressure failure is recoverable.  Do not turn it into a normal
                // "no metadata" result, otherwise artwork callers cache a false missing cover.
                if (error is OutOfMemoryError) throw error
                Log.d(TAG, "lyrico-audiotag $label failed for $path", error)
                null
            }
    }

    private fun cacheKey(path: String): String? {
        if (path.isBlank() || path.isHttpAudioSource()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }

    private fun decodeCoverBitmap(data: ByteArray, maxSize: Int): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
                sampleSize *= 2
            }
            BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        }.getOrNull()
    }

    private fun AudioTagInfo.hasUsefulTagData(): Boolean =
        listOf(title, artist, album, albumArtist, composer, arranger, lyricist, genre, year, comment, lyrics, copyright, neteaseKey)
            .any { !it.isNullOrBlank() } ||
            trackNumber != null ||
            discNumber != null ||
            rating != null ||
            customTags.isNotEmpty()

    private fun AudioTagInfo.mergeMissingFieldsFrom(other: AudioTagInfo): AudioTagInfo =
        copy(
            title = title.takeIf { !it.isNullOrBlank() } ?: other.title,
            artist = artist.takeIf { !it.isNullOrBlank() } ?: other.artist,
            album = album.takeIf { !it.isNullOrBlank() } ?: other.album,
            albumArtist = albumArtist.takeIf { !it.isNullOrBlank() } ?: other.albumArtist,
            composer = composer.takeIf { !it.isNullOrBlank() } ?: other.composer,
            arranger = arranger.takeIf { !it.isNullOrBlank() } ?: other.arranger,
            lyricist = lyricist.takeIf { !it.isNullOrBlank() } ?: other.lyricist,
            genre = genre.takeIf { !it.isNullOrBlank() } ?: other.genre,
            year = year.takeIf { !it.isNullOrBlank() } ?: other.year,
            trackNumber = trackNumber ?: other.trackNumber,
            discNumber = discNumber ?: other.discNumber
        )

    private fun isWavFile(path: String): Boolean =
        path.lowercase().let { it.endsWith(".wav") || it.endsWith(".wave") }

    private fun WavMetadata.toAudioTagInfo(): AudioTagInfo =
        AudioTagInfo(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            composer = composer,
            arranger = arranger,
            lyricist = lyricist,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber
        )

    private fun WavMetadata.toAudioQualityInfo(): AudioQualityInfo =
        AudioQualityInfo(
            mimeType = "audio/wav",
            bitRate = bitRate,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels
        )

    private fun MediaFormat?.getIntOrZero(key: String): Int =
        runCatching { if (this != null && containsKey(key)) getInteger(key) else 0 }.getOrDefault(0)

    companion object {
        private const val TAG = "AudioTagRepository"
    }
}

class LyricoAudioTagReaderWriter : AudioTagReader, AudioTagWriter {
    override suspend fun readTags(path: String): AudioTagInfo? = withPfd(path, ParcelFileDescriptor.MODE_READ_ONLY) { pfd ->
        val data = LyricoReader.read(pfd, readPictures = false)
        val raw = data.rawProperties.orEmpty().mapValues { (_, values) -> values.toList() }
        val resolvedComment = data.comment.cleanTagValue()
            ?: raw.firstTagValue(
                "COMMENT",
                "Comment",
                "DESCRIPTION",
                "Description",
                "DESC",
                "desc",
                "COMM",
                "©cmt",
                "\u00a9cmt",
                "\\u00a9cmt"
            )
        val resolvedNeteaseKey = raw.bestNeteaseKey(resolvedComment)
        val resolvedArranger = raw.firstTagValue(
            "ARRANGER",
            "ARRANGED BY",
            "ARRANGEDBY",
            "ARRANGEMENT",
            "ARRANGE"
        )
        AudioTagInfo(
            title = data.title,
            artist = data.artist,
            album = data.album,
            albumArtist = data.albumArtist,
            composer = data.composer,
            arranger = resolvedArranger,
            lyricist = data.lyricist,
            genre = data.genre,
            year = data.date,
            trackNumber = data.trackNumber?.substringBefore('/')?.toIntOrNull(),
            discNumber = data.discNumber,
            comment = resolvedComment,
            lyrics = raw.bestTtmlLyrics() ?: data.lyrics?.ifBlank { null } ?: raw.bestLyrics(),
            copyright = data.copyright,
            neteaseKey = resolvedNeteaseKey,
            rating = data.rating,
            replayGainTrackGain = data.replayGainTrackGain,
            replayGainTrackPeak = data.replayGainTrackPeak,
            replayGainAlbumGain = data.replayGainAlbumGain,
            replayGainAlbumPeak = data.replayGainAlbumPeak,
            replayGainReferenceLoudness = data.replayGainReferenceLoudness,
            customTags = raw
        )
    }

    override suspend fun readEmbeddedCover(path: String): AudioCoverInfo? =
        withPfd(path, ParcelFileDescriptor.MODE_READ_ONLY) { pfd ->
            val data = LyricoReader.read(pfd, readPictures = true)
            val picture = data.pictures.firstOrNull { it.pictureType.equals("Front Cover", ignoreCase = true) }
                ?: data.pictures.firstOrNull()
            picture?.let { AudioCoverInfo(bytes = it.data, mimeType = it.mimeType) }
        }

    override suspend fun readEmbeddedLyrics(path: String): String? =
        readTags(path)?.lyrics?.takeIf { it.isNotBlank() }

    override suspend fun writeTags(path: String, tags: AudioTagInfo): Result<Unit> {
        return try {
            val ok = withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
                writeTags(pfd, tags).getOrThrow()
                true
            } ?: false
            check(ok) { "lyrico-audiotag writeTags returned false" }
            Result.success(Unit)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTags(pfd: ParcelFileDescriptor, tags: AudioTagInfo): Result<Unit> {
        return try {
            val updates = tags.toWritableMap()
            if (updates.isEmpty()) return Result.success(Unit)
            val ok = LyricoWriter.writeTags(pfd, updates, preserveOldTags = true)
            check(ok) { "lyrico-audiotag writeTags returned false" }
            Result.success(Unit)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeEmbeddedCover(path: String, cover: AudioCoverInfo): Result<Unit> = runCatching {
        withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
            writeEmbeddedCover(pfd, cover).getOrThrow()
        } ?: error("Unable to open audio file for cover writing")
    }

    override suspend fun writeEmbeddedCover(pfd: ParcelFileDescriptor, cover: AudioCoverInfo): Result<Unit> = runCatching {
        val picture = AudioPicture(
            data = cover.bytes,
            mimeType = cover.mimeType ?: "image/jpeg",
            description = "",
            pictureType = "Front Cover"
        )
        val ok = LyricoWriter.writePictures(pfd, listOf(picture))
        check(ok) { "lyrico-audiotag writeEmbeddedCover returned false" }
    }

    override suspend fun removeEmbeddedCover(path: String): Result<Unit> = runCatching {
        withPfd(path, ParcelFileDescriptor.MODE_READ_WRITE) { pfd ->
            removeEmbeddedCover(pfd).getOrThrow()
        } ?: error("Unable to open audio file for cover removal")
    }

    override suspend fun removeEmbeddedCover(pfd: ParcelFileDescriptor): Result<Unit> = runCatching {
        val ok = LyricoWriter.writePictures(pfd, emptyList())
        check(ok) { "lyrico-audiotag removeEmbeddedCover returned false" }
    }

    private suspend fun <T> withPfd(path: String, mode: Int, block: suspend (ParcelFileDescriptor) -> T): T? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || !file.isFile) return@withContext null
            ParcelFileDescriptor.open(file, mode).use { pfd -> block(pfd) }
        }

    private fun AudioTagInfo.toWritableMap(): Map<String, String> = buildMap {
        title?.let { put("TITLE", it) }
        artist?.let { put("ARTIST", it) }
        album?.let { put("ALBUM", it) }
        albumArtist?.let { put("ALBUMARTIST", it) }
        composer?.let { put("COMPOSER", it) }
        arranger?.let { put("ARRANGER", it) }
        lyricist?.let { put("LYRICIST", it) }
        genre?.let { put("GENRE", it) }
        year?.let { put("DATE", it) }
        trackNumber?.let { put("TRACKNUMBER", it.toString()) }
        discNumber?.let { put("DISCNUMBER", it.toString()) }
        comment?.let { put("COMMENT", it) }
        lyrics?.let { put("LYRICS", it) }
        rating?.let { put("RATING", it.toStandardTagRatingValue().toString()) }
        customTags.forEach { (key, values) ->
            if (key.isNotBlank() && values.isNotEmpty()) put(key, values.joinToString("; "))
        }
    }
}

private fun Int.toStandardTagRatingValue(): Int {
    val safe = coerceIn(0, 5)
    return if (safe <= 0) 0 else safe * 20
}

private fun Map<String, List<String>>.bestTtmlLyrics(): String? =
    firstTagValue(
        "TTML LYRICS",
        "TTML LYRIC",
        "TTMLLYRICS",
        "TTMLLYRIC",
        "TTML"
    )

private fun Map<String, List<String>>.bestLyrics(): String? =
    firstTagValue(
        "SYNCEDLYRICS",
        "UNSYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "LYRICS",
        "USLT",
        "SYLT",
        "©lyr",
        "\u00a9lyr",
        "LYRIC",
        "LYR",
        // iTunes / M4A extended lyric tags
        "----:com.apple.iTunes:Lyrics",
        "ITUNESLYRICS"
    )

private fun Map<String, List<String>>.bestNeteaseKey(comment: String?): String? {
    firstTagValue(
        "163KEY",
        "163 KEY",
        "NETEASEKEY",
        "NETEASE_KEY",
        "NETEASE_CLOUD_MUSIC_KEY",
        "CLOUDMUSICKEY",
        "CLOUDMUSIC_KEY",
        "MUSIC163KEY",
        "MUSIC_163_KEY"
    )?.extractNeteaseKeyCandidate()?.let { return it }

    sequenceOf(
        comment,
        firstTagValue("COMMENT", "DESCRIPTION", "DESC", "COMM", "©cmt", "\u00a9cmt", "\\u00a9cmt")
    ).forEach { value ->
        value?.extractNeteaseKeyCandidate()?.let { return it }
    }

    return null
}

private fun Map<String, List<String>>.firstTagValue(vararg keys: String): String? {
    keys.forEach { requested ->
        val value = entries.firstOrNull { (key, _) -> key.equals(requested, ignoreCase = true) }
            ?.value
            ?.firstOrNull { it.isNotBlank() }
            .cleanTagValue()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun String?.cleanTagValue(): String? =
    this?.trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')?.takeIf { it.isNotBlank() }

private fun String.extractNeteaseKeyCandidate(): String? {
    val text = cleanTagValue() ?: return null
    val patterns = listOf(
        Regex(
            """(?i)(163\s*key|163key|netease\s*key|neteasecloudmusic|cloudmusic\s*key|music163key|网易云(?:音乐)?)[\s:=：]+(.+)"""
        ),
        Regex("""(?i)(music\.163\.com[^\s]+)""")
    )
    for (pattern in patterns) {
        val value = pattern.find(text)
            ?.groupValues
            ?.lastOrNull()
            ?.cleanTagValue()
        if (!value.isNullOrBlank()) return value
    }
    return if (text.looksLikeNeteaseKeyValue()) text else null
}
