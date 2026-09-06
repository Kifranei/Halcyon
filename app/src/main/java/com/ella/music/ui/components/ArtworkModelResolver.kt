package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.isMediaStoreAlbumArtworkUri
import com.ella.music.data.model.Song
import com.ella.music.data.repository.audioExtension
import com.ella.music.data.repository.embeddedArtworkFileExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

enum class ArtworkUsage {
    ListThumbnail,
    LibraryGrid,
    ArtistImage,
    MiniPlayer
}

/**
 * Library layouts are different presentations of the same song artwork.  Keep them in one
 * cache family so a list -> two-column -> grid transition can reuse the already-resolved model
 * instead of briefly falling back to a placeholder while the new presentation resolves it again.
 */
private val ArtworkUsage.cacheFamily: String
    get() = when (this) {
        ArtworkUsage.ListThumbnail,
        ArtworkUsage.LibraryGrid -> "library"
        ArtworkUsage.ArtistImage -> "artist"
        ArtworkUsage.MiniPlayer -> "mini-player"
    }

data class SongArtworkState(
    val model: Any?,
    val showDefaultCover: Boolean
)

internal val artworkResolutionGeneration = MutableStateFlow(0L)

@Composable
fun rememberSongArtworkState(
    song: Song?,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> Bitmap?)?,
    usage: ArtworkUsage,
    showDefaultWhenMissing: Boolean = true
): SongArtworkState {
    val coverUrl = song?.coverUrl?.takeIf {
        it.isNotBlank() && !it.isMediaStoreAlbumArtworkUri()
    }
    val cacheFamily = usage.cacheFamily
    val cacheKey = remember(
        song?.id,
        song?.path,
        song?.title,
        song?.artist,
        song?.album,
        song?.albumArtist,
        song?.dateModified,
        song?.fileSize,
        coverUrl,
        albumArtUri,
        cacheFamily
    ) {
        song?.let { current ->
            listOf(
                cacheFamily,
                "v2",
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
            // Library grid cards are much larger than list thumbnails and must resolve through
            // their own high-resolution loader/cache entry instead of upscaling the 128 px model.
            ArtworkUsage.LibraryGrid -> true
            // Artist/album detail headers need the song's embedded cover even when MediaStore
            // exposes an album-art URI.  Several providers return no artwork for mp3/ogg there.
            ArtworkUsage.ArtistImage -> true
            // The mini-player is song-specific. MediaStore's album URI is only a fallback;
            // otherwise two tracks from one album with different embedded covers look identical.
            ArtworkUsage.MiniPlayer -> true
        }
    val cachedModel = remember(cacheKey) {
        cacheKey?.let(ArtworkModelMemoryCache::get)
    }
    val resolutionGeneration by artworkResolutionGeneration.collectAsState()
    // Song-specific surfaces must not flash a different track's album-level artwork while the
    // embedded picture is being extracted.  Use the shared album URI only after that lookup has
    // completed and confirmed that this song has no readable cover of its own.
    val initialModel = cachedModel ?: when {
        shouldTryEmbedded -> coverUrl
        else -> coverUrl ?: albumArtUri
    }

    val state by produceState(
        initialValue = SongArtworkState(
            model = initialModel,
            showDefaultCover = showDefaultWhenMissing && initialModel == null && !shouldTryEmbedded
        ),
        song?.id,
        song?.path,
        song?.title,
        song?.artist,
        song?.album,
        song?.albumArtist,
        song?.dateModified,
        song?.fileSize,
        coverUrl,
        albumArtUri,
        cacheFamily,
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
            val resolved: Any? = coverUrl ?: embeddedCover ?: albumArtUri
            // Bitmap models are cached too: keeping them alive lets a layout switch (list /
            // two-column / grid) display instantly instead of flashing placeholders and
            // re-running the async cover load.
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
    audioExtension() in embeddedArtworkFileExtensions

private object ArtworkModelMemoryCache {
    // Budgeted by bytes so cached Bitmap models (covers resolved from embedded artwork) cannot
    // balloon while still keeping recent artwork alive across layout switches and long lists,
    // so library cells do not fall back to DefaultAlbumCover on return.
    private val cache = object : LruCache<String, Any>(32 * 1024) {
        override fun sizeOf(key: String, value: Any): Int = when (value) {
            is Bitmap -> value.byteCount / 1024
            else -> 1
        }
    }

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
