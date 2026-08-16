package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitArtistNames
import com.ella.music.player.EllaRenderersFactory
import com.ella.music.ui.components.SafeCoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal enum class DynamicCoverKind {
    Video,
    AnimatedImage
}

internal enum class PlayerVideoRole {
    DynamicCover,
    MusicVideo
}

internal data class DynamicCoverSource(
    val uri: Uri,
    val failureKey: String,
    val kind: DynamicCoverKind = DynamicCoverKind.Video,
    val aspectRatio: Float? = null,
    val preferLandscapeBackground: Boolean = false,
    val playbackOwnerKey: String = "",
    val role: PlayerVideoRole = PlayerVideoRole.DynamicCover
)

internal fun Song.dynamicCoverResolutionKey(): String =
    listOf(
        playlistIdentityKey(),
        path,
        title,
        artist,
        album,
        dateModified,
        fileSize
    ).joinToString("|")

@Composable
internal fun DynamicCoverVideo(
    source: DynamicCoverSource,
    isPlaying: Boolean,
    playAudio: Boolean = false,
    syncPositionMs: Long? = null,
    syncDurationMs: Long? = null,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 14f,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    if (source.kind == DynamicCoverKind.AnimatedImage) {
        SafeCoverImage(
            model = source.uri,
            contentDescription = null,
            modifier = modifier,
            contentScale = if (resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            },
            sizePx = 1200,
            showDefaultPlaceholder = false
        )
        return
    }

    val context = LocalContext.current
    val followsAudioClock = source.role == PlayerVideoRole.MusicVideo
    val playbackMemoryKey = remember(source.failureKey, source.playbackOwnerKey) {
        DynamicCoverPlaybackMemory.activate(
            ownerKey = source.playbackOwnerKey.ifBlank { source.failureKey },
            sourceKey = source.failureKey
        )
    }
    val initialPositionMs = remember(playbackMemoryKey) {
        DynamicCoverPlaybackMemory.restore(playbackMemoryKey)
    }

    val exoPlayer = remember(playbackMemoryKey, playAudio) {
        val trackSelector = DefaultTrackSelector(context).apply {
            if (!playAudio) {
                parameters = buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
        }
        val renderersFactory = EllaRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
            repeatMode = if (followsAudioClock) Media3Player.REPEAT_MODE_OFF else Media3Player.REPEAT_MODE_ALL
            // Video artwork and synced playback-page MVs have no selected audio track. This is
            // stronger than volume=0 and prevents their audio from entering screen recordings.
            volume = if (playAudio) 1f else 0f
            setMediaItem(context.buildMusicVideoMediaItem(source.uri))
            prepare()
            if (initialPositionMs > 0L) seekTo(initialPositionMs)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Media3Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    "PlayerVideo",
                    "Playback failed (${error.errorCodeName}) for ${source.uri}",
                    error
                )
                onPlaybackError()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                MusicVideoPlaybackBridge.publish(source, exoPlayer)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Media3Player.PositionInfo,
                newPosition: Media3Player.PositionInfo,
                reason: Int
            ) {
                MusicVideoPlaybackBridge.publish(source, exoPlayer)
            }
        }

        exoPlayer.addListener(listener)
        MusicVideoPlaybackBridge.attach(source, exoPlayer)

        onDispose {
            DynamicCoverPlaybackMemory.save(playbackMemoryKey, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            MusicVideoPlaybackBridge.detach(source, exoPlayer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, source) {
        if (source.role != PlayerVideoRole.MusicVideo) return@LaunchedEffect
        while (isActive) {
            MusicVideoPlaybackBridge.publish(source, exoPlayer)
            delay(100L)
        }
    }

    val latestSyncPositionMs by rememberUpdatedState(syncPositionMs)
    val latestSyncDurationMs by rememberUpdatedState(syncDurationMs)
    LaunchedEffect(exoPlayer, followsAudioClock, syncPositionMs, syncDurationMs, isPlaying) {
        if (!followsAudioClock || latestSyncPositionMs == null) return@LaunchedEffect
        val audioLimit = latestSyncDurationMs?.coerceAtLeast(0L) ?: Long.MAX_VALUE
        val videoDuration = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val unclampedTarget = latestSyncPositionMs!!.coerceAtLeast(0L)
        val target = if (unclampedTarget >= videoDuration) {
            // Seeking exactly to duration can clear the video surface on some decoders.
            // Keep the final decoded frame visible after a short MV has ended.
            (videoDuration - 1L).coerceAtLeast(0L)
        } else {
            unclampedTarget.coerceAtMost(audioLimit)
        }
        // Let ExoPlayer advance smoothly between occasional audio-clock corrections. Re-seeking
        // on every Compose position tick flushes the decoder and makes the MV look choppy.
        if (kotlin.math.abs(exoPlayer.currentPosition - target) > MUSIC_VIDEO_RESYNC_TOLERANCE_MS ||
            (!isPlaying && exoPlayer.currentPosition != target)
        ) {
            exoPlayer.seekTo(target)
        }
        exoPlayer.playWhenReady = isPlaying && unclampedTarget < videoDuration && unclampedTarget < audioLimit
    }

    DisposableEffect(isPlaying, playAudio, followsAudioClock, exoPlayer) {
        exoPlayer.volume = if (playAudio) 1f else 0f
        if (!followsAudioClock) exoPlayer.playWhenReady = isPlaying
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = false
                this.resizeMode = resizeMode
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(Color.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
                player = exoPlayer
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radiusPx = view.resources.displayMetrics.density * cornerRadiusDp
                        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                    }
                }
                hideController()
            }
        },
        update = { view ->
            view.useController = false
            view.controllerAutoShow = false
            view.controllerHideOnTouch = false
            view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
            view.player = exoPlayer
            view.resizeMode = resizeMode
            view.setKeepContentOnPlayerReset(true)
            view.setShutterBackgroundColor(Color.TRANSPARENT)
            view.clipToOutline = true
            view.hideController()
            exoPlayer.volume = if (playAudio) 1f else 0f
            if (!followsAudioClock) exoPlayer.playWhenReady = isPlaying
        }
    )
}

private const val MUSIC_VIDEO_RESYNC_TOLERANCE_MS = 750L

internal fun Song.dynamicCoverSource(
    context: Context,
    includeExternalFiles: Boolean = true,
    customRootPaths: List<String> = emptyList()
): DynamicCoverSource? {
    val resolvedSource = if (includeExternalFiles) {
        dynamicCoverVideoFile(
            context = context,
            customRootPaths = customRootPaths,
            includeExternalFiles = includeExternalFiles
        )?.let { file ->
            file.toDynamicCoverSource(
                context = context,
                role = PlayerVideoRole.DynamicCover
            )
        } ?: dynamicCoverDocumentSource(
            context = context,
            customRootPaths = customRootPaths
        ) ?: legacyEmbeddedAnimatedImageSource(context)
            ?: embeddedDynamicVideoSource(context)
    } else {
        legacyEmbeddedAnimatedImageSource(context) ?: embeddedDynamicVideoSource(context)
    }
    return resolvedSource?.copy(playbackOwnerKey = dynamicCoverResolutionKey())
}

internal fun Song.musicVideoSource(
    context: Context,
    customRootPaths: List<String> = emptyList(),
    musicVideoCustomFolders: List<String> = emptyList()
): DynamicCoverSource? {
    // Dedicated MV folders win: files there are named for MVs directly ("Artist - Title.mp4",
    // no _MV suffix required) so they are the least ambiguous user intent.
    val resolvedSource = musicVideoCustomFolderSource(
        context = context,
        musicVideoCustomFolders = musicVideoCustomFolders
    ) ?: musicVideoLocalFile(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = true
    )?.toDynamicCoverSource(
        context = context,
        role = PlayerVideoRole.MusicVideo
    ) ?: musicVideoDocumentSource(
        context = context,
        customRootPaths = customRootPaths
    )
    return resolvedSource?.copy(playbackOwnerKey = dynamicCoverResolutionKey())
}

private fun Song.embeddedDynamicVideoSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    if (!hasPlayableEmbeddedVideoTrack(context, mediaUri)) return null
    return DynamicCoverSource(
        uri = mediaUri,
        failureKey = "embedded-video:$path:${dateModified}:${fileSize}",
        aspectRatio = context.readDynamicCoverAspectRatio(mediaUri)
    )
}

private fun Song.legacyEmbeddedAnimatedImageSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    val picture = runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (mediaUri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(context, mediaUri)
            } else {
                retriever.setDataSource(mediaUri.path.orEmpty())
            }
            retriever.embeddedPicture
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

    val format = picture.legacyAnimatedPictureFormat() ?: return null
    val cacheFile = File(context.cacheDir, "dynamic_covers/${path.hashCode()}_${dateModified}_${fileSize}.${format.extension}")
    return runCatching {
        cacheFile.parentFile?.mkdirs()
        if (!cacheFile.exists() || cacheFile.length() != picture.size.toLong()) {
            cacheFile.writeBytes(picture)
        }
        DynamicCoverSource(
            uri = Uri.fromFile(cacheFile),
            failureKey = "embedded-image:${cacheFile.absolutePath}:${cacheFile.length()}",
            kind = DynamicCoverKind.AnimatedImage
        )
    }.getOrNull()
}

private data class LegacyAnimatedPictureFormat(val extension: String)

private fun ByteArray.legacyAnimatedPictureFormat(): LegacyAnimatedPictureFormat? {
    return when {
        startsWithAscii("GIF8") -> LegacyAnimatedPictureFormat("gif")
        else -> null
    }
}

private fun ByteArray.startsWithBytes(vararg bytes: Int): Boolean =
    size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }

private fun ByteArray.startsWithAscii(prefix: String): Boolean =
    size >= prefix.length && prefix.indices.all { this[it].toInt().toChar() == prefix[it] }

private inline fun <T> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> T): T {
    try {
        return block(this)
    } finally {
        release()
    }
}

private fun Song.dynamicCoverMediaUri(): Uri? {
    val trimmedPath = path.trim()
    if (trimmedPath.isBlank() || trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) return null
    return if (trimmedPath.startsWith("content://", ignoreCase = true)) {
        Uri.parse(trimmedPath)
    } else {
        File(trimmedPath)
            .takeIf { it.exists() && it.isFile && it.length() > 0L }
            ?.let(Uri::fromFile)
    }
}

private fun Song.hasPlayableEmbeddedVideoTrack(context: Context, uri: Uri): Boolean {
    return runCatching {
        val extractor = MediaExtractor()
        try {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(uri.path.orEmpty())
            }
            (0 until extractor.trackCount).any { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
                mime.startsWith("video/") &&
                    mime != "video/mjpeg" &&
                    !mime.startsWith("image/")
            }
        } finally {
            extractor.release()
        }
    }.getOrElse { error ->
        Log.d("PlayerScreen", "Embedded dynamic cover video unavailable for ${title.ifBlank { fileName }}", error)
        false
    }
}

private fun Song.dynamicCoverVideoFile(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean
): File? {
    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }

    val songFolder = songFile?.parentFile

    val albumName = album.ifBlank {
        songFolder?.name.orEmpty()
    }.ifBlank {
        "Unknown"
    }

    val albumKey = albumName.toSafeDynamicCoverName()

    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()

    val artistSongName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val artistSongCompactName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val songNameCandidates = listOf(
        songFile?.nameWithoutExtension.orEmpty(),
        title,
        artistSongCompactName,
        artistSongName
    )
        .filter { it.isNotBlank() }
        .distinct()
    val safeSongNameCandidates = songNameCandidates
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()
    val songCandidates = (songNameCandidates + safeSongNameCandidates).distinct()
    val selectedSongCandidates = playerVideoNameCandidates(songCandidates, musicVideoOnly = false)
    val albumNameCandidates = listOf(
        albumName,
        albumKey,
        listOf(artist, albumName).filter { it.isNotBlank() }.joinToString(" - "),
        artistAlbumKey
    )
        .filter { it.isNotBlank() }
        .distinct()

    val folderCandidates = songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.let { folder ->
            songCandidates.map { File(folder, "$it.mp4") } + listOf(
                File(folder, "cover.mp4"),
                File(folder, "${folder.name}.mp4"),
                File(folder, "$albumName.mp4"),
                File(folder, "$albumKey.mp4"),
                File(folder, "$artistAlbumKey.mp4")
            )
        }
        .orEmpty()

    val roots = dynamicCoverRootDirectories(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = includeExternalFiles
    )

    val libraryCandidates = roots.flatMap { root ->
        buildList {
            add(File(root, "cover.mp4"))
            addAll(selectedSongCandidates.map { name ->
                File(root, "$name.mp4")
            })
            addAll(albumNameCandidates.map { name -> File(root, "$name.mp4") })
            listOf("Song", "song").forEach { songDir ->
                addAll(selectedSongCandidates.map { name ->
                    File(root, "$songDir/$name.mp4")
                })
            }
            listOf("Album", "album").forEach { albumDir ->
                addAll(albumNameCandidates.map { name -> File(root, "$albumDir/$name.mp4") })
            }
        }
    }

    val candidates = folderCandidates + libraryCandidates

    candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }?.let { return it }

    val fuzzySongTokens = selectedSongCandidates
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzyAlbumTokens = albumNameCandidates
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzySearchDirs = buildList {
        songFolder?.takeIf { it.exists() && it.isDirectory }?.let(::add)
        roots.forEach { root ->
            root.takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Album").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "album").takeIf { it.exists() && it.isDirectory }?.let(::add)
        }
    }.distinctBy { it.absolutePath.lowercase() }

    return fuzzySearchDirs.firstNotNullOfOrNull { dir ->
        dir.listFiles { file ->
            file.isFile &&
                file.extension.equals("mp4", ignoreCase = true) &&
                file.length() > 0L &&
                file.nameWithoutExtension.toDynamicCoverMatchToken().let { token ->
                    token in fuzzySongTokens || token in fuzzyAlbumTokens
                }
        }?.firstOrNull()
    }
}

/**
 * Finds an MV on the filesystem (song's own folder plus the dynamic-cover library roots).
 * Files here still require the `_MV` / `-MV` suffix so they can coexist with ambient covers.
 * All supported MV containers are accepted; ambient dynamic covers remain mp4-only.
 *
 * Candidate names are searched in strict tiers so a same-titled song by another artist can no
 * longer steal the match: the song's own file name first (unambiguous), then artist+title
 * combinations, and only if those find nothing the bare title (legacy naming).
 */
private fun Song.musicVideoLocalFile(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean
): File? {
    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }
    val songFolder = songFile?.parentFile?.takeIf { it.exists() && it.isDirectory }

    val roots = dynamicCoverRootDirectories(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = includeExternalFiles
    )
    val searchDirs = buildList {
        songFolder?.let(::add)
        roots.forEach { root ->
            root.takeIf { it.exists() && it.isDirectory }?.let(::add)
            listOf("Song", "song", "Album", "album").forEach { child ->
                File(root, child).takeIf { it.exists() && it.isDirectory }?.let(::add)
            }
        }
    }.distinctBy { it.absolutePath.lowercase() }
    if (searchDirs.isEmpty()) return null

    val tiers = buildMusicVideoBaseNameTiers(
        fileBaseName = songFile?.nameWithoutExtension.orEmpty(),
        title = title,
        artist = artist
    )
    tiers.forEach { tier ->
        val mvNames = buildLandscapeMusicVideoNameCandidates(tier)
        val exactFileNames = musicVideoFileNameCandidates(mvNames)
        searchDirs.firstNotNullOfOrNull { dir ->
            exactFileNames.firstNotNullOfOrNull { name ->
                File(dir, name).takeIf { it.exists() && it.isFile && it.length() > 0L }
            }
        }?.let { return it }

        val fuzzyTokens = mvNames.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
        searchDirs.firstNotNullOfOrNull { dir ->
            dir.listFiles { file ->
                file.isFile &&
                    isSupportedMusicVideoExtension(file.extension) &&
                    file.length() > 0L &&
                    file.nameWithoutExtension.toDynamicCoverMatchToken() in fuzzyTokens
            }?.firstOrNull()
        }?.let { return it }
    }
    return null
}

internal fun dynamicCoverRootDirectories(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean = true
): List<File> {
    val customRoots = customRootPaths
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.startsWith("content://", ignoreCase = true) }
        .map(::File)

    val publicMovieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val defaultRoots = listOf(
        File(publicMovieDir, "Halcyon/DynamicCovers"),
        File(publicMovieDir, "Ella/DynamicCovers")
    )
    val appRoots = if (includeExternalFiles) {
        listOf(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "DynamicCovers"
            )
        )
    } else {
        emptyList()
    }

    return (customRoots + defaultRoots + appRoots)
        .map { it.absoluteFile }
        .distinctBy { it.path.lowercase() }
}

private fun Song.dynamicCoverDocumentSource(
    context: Context,
    customRootPaths: List<String>
): DynamicCoverSource? {
    val albumName = album.ifBlank { "Unknown" }
    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()
    val artistSongName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val artistSongCompactName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val songNameCandidates = listOf(
        File(path).nameWithoutExtension,
        title,
        artistSongCompactName,
        artistSongName
    )
        .filter { it.isNotBlank() }
        .distinct()
    val safeSongNameCandidates = songNameCandidates
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()
    val albumNameCandidates = listOf(
        albumName,
        albumName.toSafeDynamicCoverName(),
        listOf(artist, albumName).filter { it.isNotBlank() }.joinToString(" - "),
        artistAlbumKey
    )
        .filter { it.isNotBlank() }
        .distinct()
    val songCandidates = (songNameCandidates + safeSongNameCandidates).distinct()
    val selectedSongCandidates = playerVideoNameCandidates(songCandidates, musicVideoOnly = false)
    val fuzzySongTokens = selectedSongCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzyAlbumTokens = albumNameCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }

    return customRootPaths
        .asSequence()
        .map(String::trim)
        .filter { it.startsWith("content://", ignoreCase = true) }
        .mapNotNull { rawUri ->
            val root = runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(rawUri))
            }.getOrNull() ?: return@mapNotNull null

            val searchRoots = listOfNotNull(
                root,
                root.findChildDirectoryIgnoreCase("Song"),
                root.findChildDirectoryIgnoreCase("Album")
            )

            searchRoots.firstNotNullOfOrNull { directory ->
                val exactNames = buildList {
                    add("cover.mp4")
                    addAll(selectedSongCandidates.map { "$it.mp4" })
                    addAll(albumNameCandidates.map { "$it.mp4" })
                }
                exactNames.firstNotNullOfOrNull { name ->
                    directory.findChildFileIgnoreCase(name)?.toDynamicCoverSource(
                        context,
                        rawUri,
                        PlayerVideoRole.DynamicCover
                    )
                } ?: directory.listFiles().firstOrNull { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        file.name.orEmpty().substringAfterLast('.', "").equals("mp4", ignoreCase = true) &&
                        file.name.orEmpty().substringBeforeLast('.').toDynamicCoverMatchToken().let { token ->
                            token in fuzzySongTokens || token in fuzzyAlbumTokens
                        }
                }?.toDynamicCoverSource(
                    context,
                    rawUri,
                    PlayerVideoRole.DynamicCover
                )
            }
        }
        .firstOrNull()
}

/**
 * MV lookup inside the SAF dynamic-cover folders (legacy shared location). Files must carry the
 * `_MV` / `-MV` suffix. MP4, MKV, WebM and MOV use the same candidate priority
 * (artist+title tiers first, bare title as fallback).
 */
private fun Song.musicVideoDocumentSource(
    context: Context,
    customRootPaths: List<String>
): DynamicCoverSource? {
    val listings = customRootPaths
        .map(String::trim)
        .filter { it.startsWith("content://", ignoreCase = true) }
        .mapNotNull { rawUri ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) }
                .getOrNull()
                ?.let { rawUri to it }
        }
        .flatMap { (rawUri, root) ->
            listOfNotNull(
                root,
                root.findChildDirectoryIgnoreCase("Song"),
                root.findChildDirectoryIgnoreCase("Album")
            ).map { directory -> rawUri to directory }
        }
        // List each SAF directory once; DocumentFile.listFiles() is an IPC round-trip per call.
        .map { (rawUri, directory) -> rawUri to directory.listFiles().toList() }
    if (listings.isEmpty()) return null

    val tiers = buildMusicVideoBaseNameTiers(
        fileBaseName = File(path).nameWithoutExtension,
        title = title,
        artist = artist
    )
    tiers.forEach { tier ->
        val mvNames = buildLandscapeMusicVideoNameCandidates(tier)
        val exactFileNames = musicVideoFileNameCandidates(mvNames)
        listings.firstNotNullOfOrNull { (rawUri, files) ->
            exactFileNames.firstNotNullOfOrNull { name ->
                files.firstOrNull { file ->
                    file.isFile && file.length() > 0L && file.name.equals(name, ignoreCase = true)
                }
            }?.toDynamicCoverSource(context, rawUri, PlayerVideoRole.MusicVideo)
        }?.let { return it }

        val fuzzyTokens = mvNames.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
        listings.firstNotNullOfOrNull { (rawUri, files) ->
            files.firstOrNull { file ->
                file.isFile &&
                    file.length() > 0L &&
                    isSupportedMusicVideoExtension(
                        file.name.orEmpty().substringAfterLast('.', "")
                    ) &&
                    file.name.orEmpty().substringBeforeLast('.').toDynamicCoverMatchToken() in fuzzyTokens
            }?.toDynamicCoverSource(context, rawUri, PlayerVideoRole.MusicVideo)
        }?.let { return it }
    }
    return null
}

/**
 * MV lookup inside the dedicated "MV folders" setting. Unlike the shared dynamic-cover folders
 * these hold nothing but MVs, so the `_MV` suffix is optional ("Artist - Title.mp4" works as-is,
 * suffixed names are still accepted) and the container list is relaxed to mp4/mkv/webm/mov.
 * Both SAF tree URIs (from the folder picker) and plain filesystem paths are supported.
 */
internal fun Song.musicVideoCustomFolderSource(
    context: Context,
    musicVideoCustomFolders: List<String>
): DynamicCoverSource? {
    val cleaned = musicVideoCustomFolders.map(String::trim).filter(String::isNotBlank)
    if (cleaned.isEmpty()) return null
    val folderIndex = MusicVideoCustomFolderIndexCache.get(context, cleaned)
    if (folderIndex.files.isEmpty() && folderIndex.documents.isEmpty()) return null

    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }
    val tiers = buildMusicVideoBaseNameTiers(
        fileBaseName = songFile?.nameWithoutExtension.orEmpty(),
        title = title,
        artist = artist
    )
    tiers.forEach { tier ->
        val names = musicVideoFolderFileNameCandidates(tier)
        val exactFileNames = musicVideoFileNameCandidates(names)
        exactFileNames.firstNotNullOfOrNull { name ->
            folderIndex.files.firstOrNull { file -> file.name.equals(name, ignoreCase = true) }
        }?.let { return it.toDynamicCoverSource(context, role = PlayerVideoRole.MusicVideo) }
        folderIndex.documents.firstNotNullOfOrNull { (rawUri, files) ->
            exactFileNames.firstNotNullOfOrNull { name ->
                files.firstOrNull { file ->
                    file.name.equals(name, ignoreCase = true)
                }
            }?.toDynamicCoverSource(context, rawUri, PlayerVideoRole.MusicVideo)
        }?.let { return it }

        val fuzzyTokens = names.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
        folderIndex.files.firstOrNull { file ->
            file.nameWithoutExtension.toDynamicCoverMatchToken() in fuzzyTokens
        }?.let { return it.toDynamicCoverSource(context, role = PlayerVideoRole.MusicVideo) }
        folderIndex.documents.firstNotNullOfOrNull { (rawUri, files) ->
            files.firstOrNull { file ->
                val name = file.name.orEmpty()
                name.substringBeforeLast('.').toDynamicCoverMatchToken() in fuzzyTokens
            }?.toDynamicCoverSource(context, rawUri, PlayerVideoRole.MusicVideo)
        }?.let { return it }
    }
    return null
}

private data class MusicVideoCustomFolderIndex(
    val createdAt: Long,
    val files: List<File>,
    val documents: List<Pair<String, List<DocumentFile>>>
)

private object MusicVideoCustomFolderIndexCache {
    private const val CACHE_TTL_MS = 15_000L
    private val entries = ConcurrentHashMap<String, MusicVideoCustomFolderIndex>()

    fun get(context: Context, folders: List<String>): MusicVideoCustomFolderIndex {
        val key = folders.joinToString("\u0000")
        val now = android.os.SystemClock.elapsedRealtime()
        entries[key]?.takeIf { now - it.createdAt <= CACHE_TTL_MS }?.let { return it }

        val files = folders.asSequence()
            .filterNot { it.startsWith("content://", ignoreCase = true) }
            .map(::File)
            .filter { it.exists() && it.isDirectory }
            .distinctBy { it.absolutePath.lowercase() }
            .flatMap { root -> root.walkTopDown().maxDepth(3).asSequence() }
            .filter { file ->
                file.isFile && file.length() > 0L && isSupportedMusicVideoExtension(file.extension)
            }
            .distinctBy { it.absolutePath.lowercase() }
            .toList()
        val documents = folders.asSequence()
            .filter { it.startsWith("content://", ignoreCase = true) }
            .mapNotNull { rawUri ->
                runCatching { DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) }
                    .getOrNull()
                    ?.let { root -> rawUri to root.collectMusicVideoDocuments(maxDepth = 3) }
            }
            .toList()
        return MusicVideoCustomFolderIndex(now, files, documents).also { index ->
            entries.clear()
            entries[key] = index
        }
    }
}

private fun DocumentFile.collectMusicVideoDocuments(maxDepth: Int): List<DocumentFile> {
    val result = ArrayList<DocumentFile>()
    fun visit(folder: DocumentFile, depth: Int) {
        folder.listFiles().forEach { child ->
            when {
                child.isDirectory && depth < maxDepth -> visit(child, depth + 1)
                child.isFile && child.length() > 0L &&
                    isSupportedMusicVideoExtension(child.name.orEmpty().substringAfterLast('.', "")) -> result += child
            }
        }
    }
    visit(this, 0)
    return result
}

/** Keeps an ambient video's loop position while Compose swaps player pages. */
internal object DynamicCoverPlaybackMemory {
    private val positions = ConcurrentHashMap<String, Long>()
    private var activeOwnerKey: String? = null

    @Synchronized
    fun activate(ownerKey: String, sourceKey: String): String {
        if (activeOwnerKey != ownerKey) {
            positions.clear()
            activeOwnerKey = ownerKey
        }
        return "$ownerKey|$sourceKey"
    }

    fun restore(key: String): Long = positions[key]?.coerceAtLeast(0L) ?: 0L

    fun save(key: String, positionMs: Long) {
        if (positionMs > 0L) positions[key] = positionMs
    }

    /**
     * Explicitly clears all remembered playback positions.
     *
     * Called when the active song changes (next/previous) to guarantee MV/dynamic-cover
     * videos restart from the beginning instead of resuming a stale position. This is a
     * safety net alongside [activate] — the activate-based clear can be defeated by Compose
     * lifecycle timing (onDispose save racing ahead of activate clear).
     */
    @Synchronized
    fun clearAll() {
        positions.clear()
        activeOwnerKey = null
    }
}

private fun DocumentFile.findChildDirectoryIgnoreCase(name: String): DocumentFile? =
    listFiles().firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }

private fun DocumentFile.findChildFileIgnoreCase(name: String): DocumentFile? =
    listFiles().firstOrNull { it.isFile && it.length() > 0L && it.name.equals(name, ignoreCase = true) }

private fun File.toDynamicCoverSource(
    context: Context,
    role: PlayerVideoRole
): DynamicCoverSource {
    val uri = Uri.fromFile(this)
    return DynamicCoverSource(
        uri = uri,
        failureKey = "file:${absolutePath}:${lastModified()}:${length()}",
        aspectRatio = context.readDynamicCoverAspectRatio(uri),
        preferLandscapeBackground = role == PlayerVideoRole.MusicVideo,
        role = role
    )
}

private fun DocumentFile.toDynamicCoverSource(
    context: Context,
    rootUri: String,
    role: PlayerVideoRole
): DynamicCoverSource =
    DynamicCoverSource(
        uri = uri,
        failureKey = "tree:$rootUri:${uri}:${length()}",
        aspectRatio = context.readDynamicCoverAspectRatio(uri),
        preferLandscapeBackground = role == PlayerVideoRole.MusicVideo,
        role = role
    )

private fun Context.readDynamicCoverAspectRatio(uri: Uri): Float? =
    runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (uri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(this, uri)
            } else {
                retriever.setDataSource(uri.path.orEmpty())
            }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return@useCompat null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return@useCompat null
            if (width <= 0 || height <= 0) return@useCompat null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val rotated = rotation == 90 || rotation == 270
            val displayWidth = if (rotated) height else width
            val displayHeight = if (rotated) width else height
            displayWidth.toFloat() / displayHeight.toFloat()
        }
    }.getOrNull()

internal fun Context.readMusicVideoDurationMs(uri: Uri): Long =
    runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (uri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(this, uri)
            } else {
                retriever.setDataSource(uri.path.orEmpty())
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

/**
 * Builds a useful MV thumbnail instead of accepting a common all-black opening frame. The frame
 * remains uncropped here; the detail row supplies its required 16:9 presentation box.
 */
internal fun Context.readMusicVideoPreviewFrame(uri: Uri): Bitmap? =
    runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (uri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(this, uri)
            } else {
                retriever.setDataSource(uri.path.orEmpty())
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val candidateTimestampsMs = listOf(0L, 1_000L, 3_000L, 6_000L, 10_000L)
                .filter { durationMs <= 0L || it <= durationMs }
                .distinct()
            var selected: Bitmap? = null
            for (timestampMs in candidateTimestampsMs) {
                if (selected != null) break
                val frame = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    if (frame.isVisiblyLit()) selected = frame else frame.recycle()
                }
            }
            selected ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()

private fun Bitmap.isVisiblyLit(): Boolean {
    val stepX = (width / 20).coerceAtLeast(1)
    val stepY = (height / 12).coerceAtLeast(1)
    var total = 0L
    var count = 0
    for (x in 0 until width step stepX) {
        for (y in 0 until height step stepY) {
            val color = getPixel(x, y)
            total += (android.graphics.Color.red(color) * 30L +
                android.graphics.Color.green(color) * 59L +
                android.graphics.Color.blue(color) * 11L) / 100L
            count++
        }
    }
    return count > 0 && total / count > 14L
}

private fun String.toSafeDynamicCoverName(): String {
    return trim()
        .replace("""[\\/:*?"<>|]""".toRegex(), "_")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Unknown" }
}

private val LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX =
    Regex("""(?:[\s_\-–—]+mv)$""", RegexOption.IGNORE_CASE)

internal fun buildLandscapeMusicVideoNameCandidates(baseNames: Collection<String>): List<String> =
    baseNames
        .map(String::trim)
        .filter { it.isNotBlank() }
        .flatMap { name -> listOf("${name}_MV", "${name}-MV") }
        .distinct()

internal fun playerVideoNameCandidates(
    songCandidates: Collection<String>,
    musicVideoOnly: Boolean
): List<String> = if (musicVideoOnly) {
    buildLandscapeMusicVideoNameCandidates(songCandidates)
} else {
    songCandidates.map(String::trim).filter(String::isNotBlank).distinct()
}

/** Containers accepted by every MV lookup path. Ambient dynamic covers remain mp4-only. */
internal val MUSIC_VIDEO_FOLDER_EXTENSIONS = listOf("mp4", "mkv", "webm", "mov")

internal fun musicVideoFileNameCandidates(baseNames: Collection<String>): List<String> =
    baseNames
        .map(String::trim)
        .filter(String::isNotBlank)
        .flatMap { name ->
            MUSIC_VIDEO_FOLDER_EXTENSIONS.map { extension -> "$name.$extension" }
        }
        .distinct()

internal fun isSupportedMusicVideoExtension(extension: String): Boolean =
    MUSIC_VIDEO_FOLDER_EXTENSIONS.any { supported ->
        extension.equals(supported, ignoreCase = true)
    }

/**
 * Artist strings that may appear in an MV file name: the full (possibly multi-artist) tag value
 * plus the first split artist, so "ArtistA&ArtistB - Title.mp4" and "ArtistA - Title.mp4" both
 * match a song tagged "ArtistA&ArtistB". Splitting follows the user's artist-separator settings.
 */
internal fun musicVideoArtistNameVariants(artist: String): List<String> {
    val full = artist.trim()
    if (full.isBlank()) return emptyList()
    val first = splitArtistNames(artist).firstOrNull()?.trim().orEmpty()
    return listOf(full, first).filter(String::isNotBlank).distinct()
}

/** "Artist - Title" / "Artist-Title" / "Title - Artist" combinations for MV file names. */
internal fun buildArtistTitleMusicVideoBaseNames(artist: String, title: String): List<String> {
    val trimmedTitle = title.trim()
    if (trimmedTitle.isBlank()) return emptyList()
    return musicVideoArtistNameVariants(artist)
        .flatMap { name ->
            listOf("$name - $trimmedTitle", "$name-$trimmedTitle", "$trimmedTitle - $name")
        }
        .distinct()
}

/**
 * Priority tiers of MV base names (no `_MV` suffix, no extension). Earlier tiers must be fully
 * exhausted (exact and fuzzy) before a later tier is tried:
 *  1. the song's own file name — unambiguous, keeps "songfile_MV.mp4" working,
 *  2. artist+title combinations — disambiguates same-titled songs by different artists,
 *  3. bare title — legacy fallback for users who already named files by title only.
 * With a blank artist tier 2 is empty, so behaviour degrades to the previous title matching.
 * Each name is doubled with its filesystem-safe form; names already used by an earlier tier are
 * dropped from later ones.
 */
internal fun buildMusicVideoBaseNameTiers(
    fileBaseName: String,
    title: String,
    artist: String
): List<List<String>> {
    fun expand(names: List<String>): List<String> = names
        .map(String::trim)
        .filter(String::isNotBlank)
        .flatMap { listOf(it, it.toSafeDynamicCoverName()) }
        .filter(String::isNotBlank)
        .distinct()

    val seen = mutableSetOf<String>()
    return listOf(
        expand(listOf(fileBaseName)),
        expand(buildArtistTitleMusicVideoBaseNames(artist, title)),
        expand(listOf(title))
    )
        .map { tier -> tier.filter(seen::add) }
        .filter { it.isNotEmpty() }
}

/**
 * File names (without extension) accepted inside a dedicated MV folder: the plain base names
 * ("Artist - Title") plus the suffixed forms ("Artist - Title_MV") for users who reuse files.
 */
internal fun musicVideoFolderFileNameCandidates(baseNames: Collection<String>): List<String> {
    val trimmed = baseNames.map(String::trim).filter(String::isNotBlank).distinct()
    return (trimmed + buildLandscapeMusicVideoNameCandidates(trimmed)).distinct()
}

internal fun isLandscapeMusicVideoFileName(
    nameWithoutExtension: String,
    songCandidates: Collection<String>
): Boolean {
    if (!nameWithoutExtension.hasLandscapeMusicVideoSuffix()) return false
    val baseToken = nameWithoutExtension.removeLandscapeMusicVideoSuffix().toDynamicCoverMatchToken()
    val songTokens = songCandidates.mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    return baseToken.isNotBlank() && baseToken in songTokens
}

private fun String.hasLandscapeMusicVideoSuffix(): Boolean =
    LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX.containsMatchIn(trim())

private fun String.removeLandscapeMusicVideoSuffix(): String =
    trim().replace(LANDSCAPE_MUSIC_VIDEO_SUFFIX_REGEX, "")

private fun String.toDynamicCoverMatchToken(): String =
    lowercase()
        .replace(Regex("""[\s_\-–—]+"""), "")
        .replace(Regex("""[\\/:*?"<>|.,，。'’`~!！()\[\]{}]+"""), "")
