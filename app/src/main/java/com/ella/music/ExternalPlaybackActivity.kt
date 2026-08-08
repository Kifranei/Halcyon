package com.ella.music

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.data.AppLogStore
import com.ella.music.data.ExternalUriResolver
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.model.Song
import com.ella.music.player.PlaybackService
import com.ella.music.player.toMediaItemExtras
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExternalPlaybackActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var handlingIntent = false
    private val externalUriResolver by lazy { ExternalUriResolver(this) }
    private val audioTagRepository by lazy { AudioTagRepository(LyricoAudioTagReaderWriter()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleExternalPlaybackIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalPlaybackIntent(intent)
    }

    override fun onDestroy() {
        releaseController()
        super.onDestroy()
    }

    private fun handleExternalPlaybackIntent(intent: Intent?) {
        if (handlingIntent) return
        val uri = intent?.resolveAudioUri()
        if (uri == null) {
            finish()
            return
        }
        handlingIntent = true

        lifecycleScope.launch {
            val mediaItem = runCatching {
                withContext(Dispatchers.IO) {
                    val song = uri.toExternalSong(intent.type.orEmpty())
                    val resolved = externalUriResolver.resolveForPlayback(
                        uri = uri,
                        grantFlags = intent.flags,
                        preferredName = song.fileName
                    )
                    val metadataPath = resolved.playbackUri.localMetadataPath()
                    val tagInfo = audioTagRepository.readTagsBlocking(metadataPath)
                    val playbackSong = song
                        .withExternalTagInfo(tagInfo)
                        .copy(
                            id = stableExternalId(resolved.playbackUri),
                            path = metadataPath,
                            fileSize = if (resolved.copiedToCache) {
                                File(metadataPath).length()
                            } else {
                                song.fileSize
                            }
                        )
                    playbackSong.toExternalMediaItem(resolved.playbackUri)
                }
            }.getOrElse { error ->
                AppLogStore.error(this@ExternalPlaybackActivity, "ExternalPlayback", "Failed to resolve external uri=$uri", error)
                handlingIntent = false
                finish()
                return@launch
            }
            playExternalMediaItem(uri, mediaItem)
        }
    }

    private fun playExternalMediaItem(originalUri: Uri, mediaItem: MediaItem) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        Futures.addCallback(
            future,
            object : FutureCallback<MediaController> {
                override fun onSuccess(controller: MediaController?) {
                    if (controller == null || controllerFuture !== future) return
                    runCatching {
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                    }.onFailure { error ->
                        AppLogStore.error(this@ExternalPlaybackActivity, "ExternalPlayback", "Failed to play external uri=$originalUri", error)
                    }
                    handlingIntent = false
                    finish()
                }

                override fun onFailure(t: Throwable) {
                    if (controllerFuture !== future) return
                    AppLogStore.error(this@ExternalPlaybackActivity, "ExternalPlayback", "Failed to connect media controller", t)
                    handlingIntent = false
                    finish()
                }
            },
            mainExecutor
        )

        lifecycleScope.launch {
            delay(5_000L)
            if (!isFinishing) {
                AppLogStore.warn(this@ExternalPlaybackActivity, "ExternalPlayback", "Timed out while opening external uri=$originalUri")
                handlingIntent = false
                finish()
            }
        }
    }

    private fun releaseController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun Intent.resolveAudioUri(): Uri? {
        data?.let { return it }
        streamUriExtra()?.let { return it }
        streamUriListExtra().firstOrNull()?.let { return it }
        clipData?.firstUri()?.let { return it }
        listOf("uri", "contentUri", "content_uri", "contentUrl", "contenturl", "path", "filePath", "file_path")
            .asSequence()
            .mapNotNull { key -> getStringExtra(key)?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()
            ?.let { raw ->
                return if (raw.startsWith("/") || raw.matches(Regex("^[A-Za-z]:[\\\\/].*"))) {
                    Uri.fromFile(File(raw))
                } else {
                    raw.toUri()
                }
            }
        return null
    }

    private fun Intent.streamUriExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun Intent.streamUriListExtra(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun ClipData.firstUri(): Uri? =
        (0 until itemCount).asSequence()
            .mapNotNull { index -> getItemAt(index)?.uri }
            .firstOrNull()

    private fun Uri.toExternalSong(intentMimeType: String): Song {
        val queried = queryAudioMetadata(this)
        val retrieved = retrieveAudioMetadata(this)
        val displayName = queried.displayName
            ?: lastPathSegment?.substringAfterLast('/')
            ?: toString().substringAfterLast('/')
        val title = queried.title
            ?: retrieved.title
            ?: displayName.substringBeforeLast('.', displayName)
        val artist = queried.artist ?: retrieved.artist ?: "Unknown"
        val album = queried.album ?: retrieved.album ?: "Unknown"

        return Song(
            id = queried.id ?: stableExternalId(this),
            title = title.ifBlank { displayName.ifBlank { toString() } },
            artist = artist.ifBlank { "Unknown" },
            album = album.ifBlank { "Unknown" },
            albumId = queried.albumId ?: 0L,
            duration = queried.duration ?: retrieved.duration ?: 0L,
            path = toString(),
            fileName = displayName,
            fileSize = queried.size ?: 0L,
            mimeType = queried.mimeType ?: intentMimeType,
            dateAdded = queried.dateAdded ?: 0L,
            dateModified = queried.dateModified ?: 0L,
            trackNumber = queried.trackNumber ?: retrieved.trackNumber ?: 0,
            albumArtist = retrieved.albumArtist.orEmpty(),
            genre = retrieved.genre.orEmpty(),
            year = queried.year ?: retrieved.year.orEmpty(),
            composer = queried.composer ?: retrieved.composer.orEmpty(),
            arranger = retrieved.arranger.orEmpty()
        )
    }

    private fun Song.toExternalMediaItem(uri: Uri): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMimeType(mimeType.takeIf { it.isNotBlank() })
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setAlbumArtist(albumArtist.takeIf { it.isNotBlank() })
                    .setDisplayTitle(title)
                    .setSubtitle(artist)
                    .setTrackNumber(trackNumber.takeIf { it > 0 })
                    .setDiscNumber(discNumber.takeIf { it > 0 })
                    .setExtras(toMediaItemExtras())
                    .apply {
                        duration.takeIf { it > 0L }?.let(::setDurationMs)
                    }
                    .build()
            )
            .build()

    private fun Uri.localMetadataPath(): String =
        if (scheme.equals("file", ignoreCase = true)) path.orEmpty() else toString()

    private fun Song.withExternalTagInfo(tagInfo: AudioTagInfo?): Song {
        if (tagInfo == null) return this
        return copy(
            title = tagInfo.title.usableTag() ?: title,
            artist = tagInfo.artist.usableTag() ?: artist,
            album = tagInfo.album.usableTag() ?: album,
            albumArtist = tagInfo.albumArtist.usableTag() ?: albumArtist,
            genre = tagInfo.genre.usableTag() ?: genre,
            year = tagInfo.year.usableTag() ?: year,
            composer = tagInfo.composer.usableTag() ?: composer,
            arranger = tagInfo.arranger.usableTag() ?: arranger,
            lyricist = tagInfo.lyricist.usableTag() ?: lyricist,
            trackNumber = tagInfo.trackNumber ?: trackNumber,
            discNumber = tagInfo.discNumber ?: discNumber
        )
    }

    private fun String?.usableTag(): String? =
        LibraryNormalizer.cleanedTagText(this).takeIf { it.isNotBlank() }

    private fun queryAudioMetadata(uri: Uri): ExternalAudioMetadata {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.COMPOSER
        )
        val audio = runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ExternalAudioMetadata(
                    id = cursor.longOrNull(MediaStore.Audio.Media._ID),
                    title = cursor.tagStringOrNull(MediaStore.Audio.Media.TITLE),
                    artist = cursor.tagStringOrNull(MediaStore.Audio.Media.ARTIST),
                    album = cursor.tagStringOrNull(MediaStore.Audio.Media.ALBUM),
                    albumId = cursor.longOrNull(MediaStore.Audio.Media.ALBUM_ID),
                    duration = cursor.longOrNull(MediaStore.Audio.Media.DURATION),
                    displayName = cursor.stringOrNull(MediaStore.Audio.Media.DISPLAY_NAME),
                    size = cursor.longOrNull(MediaStore.Audio.Media.SIZE),
                    mimeType = cursor.stringOrNull(MediaStore.Audio.Media.MIME_TYPE),
                    dateAdded = cursor.longOrNull(MediaStore.Audio.Media.DATE_ADDED),
                    dateModified = cursor.longOrNull(MediaStore.Audio.Media.DATE_MODIFIED),
                    trackNumber = cursor.intOrNull(MediaStore.Audio.Media.TRACK),
                    year = cursor.tagStringOrNull(MediaStore.Audio.Media.YEAR),
                    composer = cursor.tagStringOrNull(MediaStore.Audio.Media.COMPOSER)
                )
            }
        }.getOrNull()

        if (audio != null) return audio

        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ExternalAudioMetadata()
                ExternalAudioMetadata(
                    displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME),
                    size = cursor.longOrNull(OpenableColumns.SIZE)
                )
            }
        }.getOrNull() ?: ExternalAudioMetadata()
    }

    private fun retrieveAudioMetadata(uri: Uri): RetrievedAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(this, uri)
            RetrievedAudioMetadata(
                title = retriever.extract(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extract(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extract(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extract(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = retriever.extract(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = retriever.extract(MediaMetadataRetriever.METADATA_KEY_YEAR),
                composer = retriever.extract(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                arranger = null,
                duration = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                trackNumber = retriever.extract(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull()
            )
        }.getOrElse {
            RetrievedAudioMetadata()
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.extract(keyCode: Int): String? =
        LibraryNormalizer.cleanedTagText(extractMetadata(keyCode)).takeIf { it.isNotBlank() }

    private fun stableExternalId(uri: Uri): Long =
        (uri.toString().hashCode().toLong() and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getString(index)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.tagStringOrNull(columnName: String): String? =
        LibraryNormalizer.cleanedTagText(stringOrNull(columnName)).takeIf { it.isNotBlank() }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getLong(index)
    }

    private fun Cursor.intOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) return null
        return getInt(index)
    }

    private data class ExternalAudioMetadata(
        val id: Long? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumId: Long? = null,
        val duration: Long? = null,
        val displayName: String? = null,
        val size: Long? = null,
        val mimeType: String? = null,
        val dateAdded: Long? = null,
        val dateModified: Long? = null,
        val trackNumber: Int? = null,
        val year: String? = null,
        val composer: String? = null
    )

    private data class RetrievedAudioMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val composer: String? = null,
        val arranger: String? = null,
        val duration: Long? = null,
        val trackNumber: Int? = null
    )
}
