package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

enum class ArtworkUsage {
    ListThumbnail,
    ArtistImage,
    MiniPlayer
}

data class SongArtworkState(
    val model: Any?,
    val showDefaultCover: Boolean
)

private val artworkResolutionGeneration = MutableStateFlow(0L)

@Composable
fun rememberSongArtworkState(
    song: Song?,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> Bitmap?)?,
    usage: ArtworkUsage,
    showDefaultWhenMissing: Boolean = true
): SongArtworkState {
    val coverUrl = song?.coverUrl?.takeIf { it.isNotBlank() }
    val preferEmbedded = song?.prefersEmbeddedArtwork() == true
    val cacheKey = remember(
        song?.id,
        song?.path,
        song?.dateModified,
        song?.fileSize,
        coverUrl,
        albumArtUri,
        usage
    ) {
        song?.let { current ->
            listOf(
                usage.name,
                current.id.toString(),
                current.path,
                current.dateModified.toString(),
                current.fileSize.toString(),
                coverUrl.orEmpty(),
                albumArtUri?.toString().orEmpty()
            ).joinToString("|")
        }
    }
    val shouldTryEmbedded = song != null &&
        coverUrl == null &&
        loadCoverArt != null &&
        when (usage) {
            ArtworkUsage.ListThumbnail -> true
            // Artist/album detail headers need the song's embedded cover even when MediaStore
            // exposes an album-art URI.  Several providers return no artwork for mp3/ogg there.
            ArtworkUsage.ArtistImage -> true
            ArtworkUsage.MiniPlayer -> albumArtUri == null || preferEmbedded
        }
    val cachedModel = remember(cacheKey) {
        cacheKey?.let(ArtworkModelMemoryCache::get)
    }
    val resolutionGeneration by artworkResolutionGeneration.collectAsState()
    val initialModel = cachedModel ?: when {
        usage == ArtworkUsage.ListThumbnail && shouldTryEmbedded -> coverUrl
        else -> coverUrl ?: albumArtUri
    }

    val state by produceState(
        initialValue = SongArtworkState(
            model = initialModel,
            showDefaultCover = showDefaultWhenMissing && initialModel == null && !shouldTryEmbedded
        ),
        song?.id,
        song?.path,
        song?.dateModified,
        song?.fileSize,
        coverUrl,
        albumArtUri,
        loadCoverArt,
        usage,
        shouldTryEmbedded,
        resolutionGeneration
    ) {
        val currentSong = song
        value = if (currentSong == null) {
            SongArtworkState(null, showDefaultWhenMissing)
        } else if (!shouldTryEmbedded) {
            SongArtworkState(initialModel, showDefaultWhenMissing && initialModel == null)
        } else {
            val embeddedCover = withContext(Dispatchers.IO) {
                runCatching {
                    CoverLoadLimiter.run { loadCoverArt.invoke(currentSong) }
                }.getOrNull()
            }
            val resolved = coverUrl ?: when {
                usage == ArtworkUsage.ListThumbnail -> embeddedCover
                usage == ArtworkUsage.ArtistImage -> embeddedCover ?: albumArtUri
                preferEmbedded -> embeddedCover ?: albumArtUri
                else -> albumArtUri ?: embeddedCover
            }
            if (resolved != null && cacheKey != null) {
                ArtworkModelMemoryCache.put(cacheKey, resolved)
            }
            SongArtworkState(
                model = resolved,
                showDefaultCover = showDefaultWhenMissing && resolved == null
            )
        }
    }
    return state
}

fun Song.prefersEmbeddedArtwork(): Boolean =
    fileName.substringAfterLast('.', path.substringAfterLast('.'))
        .lowercase() in embeddedArtworkExtensions

private val embeddedArtworkExtensions = setOf(
    "m4a",
    "mp4",
    "alac",
    "flac",
    "wav",
    "wave",
    "aif",
    "aiff"
)

private object ArtworkModelMemoryCache {
    // Larger cache so that browsing a long playback-history list (which loads many covers via
    // produceState) does not evict the artwork models resolved for the main library grid, which
    // would make every library cell briefly fall back to DefaultAlbumCover on return.
    private val cache = LruCache<String, Any>(256)

    @Synchronized
    fun get(key: String): Any? = cache.get(key)

    @Synchronized
    fun put(key: String, model: Any) {
        cache.put(key, model)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}

internal fun clearArtworkModelMemoryCache() {
    ArtworkModelMemoryCache.clear()
    artworkResolutionGeneration.value += 1L
}
