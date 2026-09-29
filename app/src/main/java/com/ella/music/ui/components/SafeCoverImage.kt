package com.ella.music.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
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
    var artworkReady by remember(model, sizePx, loadOriginal) {
        mutableStateOf(model is Bitmap && !model.isRecycled)
    }
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
                onSuccess = { artworkReady = true }
            )
        }
    }
}
