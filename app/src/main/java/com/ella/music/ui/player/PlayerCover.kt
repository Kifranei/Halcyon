package com.ella.music.ui.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player as Media3Player
import coil3.compose.AsyncImage
import coil3.size.Size
import com.ella.music.data.model.Song
import com.ella.music.ui.components.DefaultAlbumCover
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun FullBleedCover(
    song: Song?,
    embeddedCover: Bitmap?,
    coverModel: Any? = null,
    cornerRadius: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val resolvedCoverModel = coverModel ?: resolveCoverPreviewModel(song, embeddedCover)
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (resolvedCoverModel != null) {
            PlayerCoverImage(
                model = resolvedCoverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                sizePx = 768,
                loadOriginal = true,
                cornerRadius = cornerRadius
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SmallCover(song: Song?, embeddedCover: Bitmap?, modifier: Modifier = Modifier) {
    AlbumArtView(
        song = song,
        embeddedCover = embeddedCover,
        cornerRadius = 12.dp,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

@Composable
internal fun PlayerCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int = 1200,
    loadOriginal: Boolean = false,
    cornerRadius: Dp = 20.dp
) {
    val context = LocalContext.current
    var sourceAspectRatio by remember(model) {
        mutableStateOf(
            (model as? Bitmap)
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?.let { it.width.toFloat() / it.height.toFloat() }
        )
    }
    val request = remember(context, model, sizePx, loadOriginal) {
        coil3.request.ImageRequest.Builder(context)
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
    if (model != null) {
        val roundedContentModifier = if (cornerRadius > 0.dp && contentScale == ContentScale.Fit) {
            Modifier.drawWithContent {
                val aspectRatio = sourceAspectRatio
                val contentBounds = if (aspectRatio != null && aspectRatio > 0f) {
                    val containerAspectRatio = size.width / size.height.coerceAtLeast(1f)
                    if (aspectRatio >= containerAspectRatio) {
                        val contentHeight = size.width / aspectRatio
                        val top = (size.height - contentHeight) / 2f
                        floatArrayOf(0f, top, size.width, top + contentHeight)
                    } else {
                        val contentWidth = size.height * aspectRatio
                        val left = (size.width - contentWidth) / 2f
                        floatArrayOf(left, 0f, left + contentWidth, size.height)
                    }
                } else {
                    floatArrayOf(0f, 0f, size.width, size.height)
                }
                val radius = cornerRadius.toPx().coerceAtMost(
                    minOf(
                        (contentBounds[2] - contentBounds[0]) / 2f,
                        (contentBounds[3] - contentBounds[1]) / 2f
                    )
                )
                val clipPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = contentBounds[0],
                            top = contentBounds[1],
                            right = contentBounds[2],
                            bottom = contentBounds[3],
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    )
                }
                clipPath(clipPath) { this@drawWithContent.drawContent() }
            }
        } else if (cornerRadius > 0.dp) {
            Modifier.clip(RoundedCornerShape(cornerRadius))
        } else {
            Modifier
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier.then(roundedContentModifier),
            contentScale = contentScale,
            onSuccess = { state ->
                val image = state.result.image
                if (image.width > 0 && image.height > 0) {
                    sourceAspectRatio = image.width.toFloat() / image.height.toFloat()
                }
            }
        )
    }
}

@Composable
internal fun AlbumArtView(
    song: Song?,
    embeddedCover: Bitmap?,
    coverModel: Any? = null,
    cornerRadius: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Fit,
    loadOriginal: Boolean = true,
    showHiResLogo: Boolean = false,
    hiResLogoUri: String = "",
    modifier: Modifier = Modifier
) {
    val resolvedCoverModel = coverModel ?: resolveCoverPreviewModel(song, embeddedCover)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (resolvedCoverModel != null) {
            PlayerCoverImage(
                model = resolvedCoverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                sizePx = 768,
                loadOriginal = loadOriginal,
                cornerRadius = cornerRadius
            )
        } else {
            DefaultAlbumCover(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
            )
        }
        if (showHiResLogo) {
            HiResLogoBadge(
                logoUri = hiResLogoUri,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            )
        }
    }
}

internal fun resolveCoverPreviewModel(song: Song?, embeddedCover: Bitmap?): Any? {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else {
        null
    }
    return embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri
}

@Composable
internal fun HiResLogoBadge(
    logoUri: String,
    modifier: Modifier = Modifier
) {
    if (logoUri.isNotBlank()) {
        AsyncImage(
            model = Uri.parse(logoUri),
            contentDescription = null,
            modifier = modifier
                .size(34.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hi-Res",
            color = Color(0xFFFFD45A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = "AUDIO",
            color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
            fontSize = 5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
