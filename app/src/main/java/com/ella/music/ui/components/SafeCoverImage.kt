package com.ella.music.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * Provided true on player surfaces: while the next track's artwork is still decoding the
 * previous track's painter stays visible instead of dropping to the default cover.
 */
val LocalRetainCoverArtwork = compositionLocalOf { false }

@Composable
fun SafeCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int = 1200,
    loadOriginal: Boolean = false,
    showDefaultPlaceholder: Boolean = true,
    artworkIdentity: String? = null
) {
    val context = LocalContext.current
    val lane = LocalLibraryMorphLane.current
    val artworkKey = artworkIdentity?.let { song -> lane?.let { LibraryArtworkKey(it.id, song) } }
    val retainArtwork = LocalRetainCoverArtwork.current
    var artworkReady by remember(model, sizePx, loadOriginal) {
        mutableStateOf(model is Bitmap && !model.isRecycled)
    }
    var retainedPainter by remember { mutableStateOf<Painter?>(null) }
    if (lane != null && artworkKey != null) {
        SideEffect { lane.scene.artworkReady[artworkKey] = artworkReady }
        DisposableEffect(lane.scene, artworkKey) {
            onDispose { lane.scene.artworkReady.remove(artworkKey) }
        }
    }
    val request = remember(context, model, sizePx, loadOriginal) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (loadOriginal) {
                    size(Size.ORIGINAL)
                } else {
                    size(sizePx)
                }
            }
            .build()
    }

    Box(modifier = modifier) {
        if (showDefaultPlaceholder && model == null) {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        } else if (retainArtwork && !artworkReady) {
            retainedPainter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }
        if (model is Bitmap && !model.isRecycled && artworkKey != null) {
            // Resolved library bitmaps already exist. Re-enqueueing them through Coil on
            // every layout change introduces a blank painter frame, even on a cache hit.
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (model != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onSuccess = { state ->
                    artworkReady = true
                    retainedPainter = state.painter
                }
            )
        }
    }
}
