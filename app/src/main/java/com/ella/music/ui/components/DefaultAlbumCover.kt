package com.ella.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ella.music.R

/**
 * No-artwork placeholder. The default drawable has its own rounded tile; [fullBleed] uses the
 * square-edged variant with Crop so edge-to-edge player covers show no empty corners.
 */
@Composable
fun DefaultAlbumCover(modifier: Modifier = Modifier, fullBleed: Boolean = false) {
    Image(
        painter = painterResource(if (fullBleed) R.drawable.ic_default_song_cover_square else R.drawable.ic_default_song_cover),
        contentDescription = null,
        contentScale = if (fullBleed) ContentScale.Crop else ContentScale.FillBounds,
        modifier = modifier
    )
}
