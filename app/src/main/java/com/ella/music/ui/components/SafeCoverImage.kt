package com.ella.music.ui.components

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
    showDefaultPlaceholder: Boolean = true
) {
    val context = LocalContext.current
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
        if (showDefaultPlaceholder) {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
        if (model != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}
