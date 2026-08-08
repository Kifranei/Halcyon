package com.ella.music.ui.artist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ArtistCoverAsset
import com.ella.music.data.ArtistCoverKind
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.DynamicCoverVideo
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class ArtistTab(@param:StringRes val labelRes: Int) {
    Songs(R.string.artist_tab_songs),
    ParticipatedAlbums(R.string.artist_tab_participated_albums),
    ReleaseAlbums(R.string.artist_tab_release_albums),
    MusicVideos(R.string.artist_tab_music_videos)
}

@Composable
internal fun ArtistJumpActions(
    hasComposerCategory: Boolean,
    hasArrangerCategory: Boolean,
    hasLyricistCategory: Boolean,
    hasNeteaseArtist: Boolean,
    onComposerClick: () -> Unit,
    onArrangerClick: () -> Unit,
    onLyricistClick: () -> Unit,
    onNeteaseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasComposerCategory) {
            ArtistJumpChip(stringResource(R.string.artist_composer_page), onComposerClick)
        }
        if (hasArrangerCategory) {
            ArtistJumpChip(stringResource(R.string.artist_arranger_page), onArrangerClick)
        }
        if (hasLyricistCategory) {
            ArtistJumpChip(stringResource(R.string.artist_lyricist_page), onLyricistClick)
        }
        if (hasNeteaseArtist) {
            ArtistJumpChip(stringResource(R.string.artist_netease_artist_page), onNeteaseClick)
        }
    }
}

@Composable
private fun ArtistJumpChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

internal fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
internal fun ArtistTabRow(
    tabs: List<ArtistTab>,
    selectedTab: ArtistTab,
    onTabSelected: (ArtistTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Text(
                text = stringResource(tab.labelRes),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
internal fun ArtistHeader(
    artistName: String,
    fallbackCoverModel: Any?,
    customCoverAssets: List<ArtistCoverAsset>,
    dynamicCoverEnabled: Boolean,
    carousel: Boolean,
    songCount: Int,
    albumCount: Int,
    onPlayAll: () -> Unit
) {
    val headerTextColor = Color.White
    val headerSubTextColor = Color.White.copy(alpha = 0.78f)
    val pageBackground = ellaPageBackground()
    val dynamicCoverSource = remember(customCoverAssets, dynamicCoverEnabled) {
        if (!dynamicCoverEnabled) {
            null
        } else {
            customCoverAssets
                .firstOrNull { it.kind == ArtistCoverKind.Video }
                ?.let { asset ->
                    DynamicCoverSource(
                        uri = asset.uri,
                        failureKey = "artist-video:${asset.uri}"
                    )
                }
        }
    }
    val imageUris = remember(customCoverAssets) {
        customCoverAssets.filter { it.kind == ArtistCoverKind.Image }.map { it.uri }
    }
    var videoFailed by remember(dynamicCoverSource?.failureKey) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(468.dp)
    ) {
        if (dynamicCoverSource != null && !videoFailed) {
            DynamicCoverVideo(
                source = dynamicCoverSource,
                isPlaying = true,
                onPlaybackError = { videoFailed = true },
                modifier = Modifier.fillMaxSize(),
                cornerRadiusDp = 0f,
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
        } else if (imageUris.isNotEmpty()) {
            ArtistHeaderImageCover(
                imageUris = imageUris,
                carousel = carousel,
                modifier = Modifier.fillMaxSize()
            )
        } else if (fallbackCoverModel != null) {
            SafeCoverImage(
                model = fallbackCoverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000,
                loadOriginal = true
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.12f),
                            0.42f to Color.Black.copy(alpha = 0.28f),
                            0.72f to Color.Black.copy(alpha = 0.58f),
                            0.88f to pageBackground.copy(alpha = 0.82f),
                            1.00f to pageBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = artistName.ifBlank { stringResource(R.string.player_unknown_artist) },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = headerTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.artist_album_song_summary, albumCount, songCount),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = headerSubTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AppleStylePlayButton(
                text = stringResource(R.string.play_all),
                onClick = onPlayAll,
                modifier = Modifier
                    .padding(top = 12.dp)
            )
        }
    }
}

/**
 * 艺术家头部封面：单图直接显示；多图时按设置在「轮播」（定时淡入淡出切换）与
 * 「随机」（每次进入随机取一张）之间选择。
 */
@Composable
private fun ArtistHeaderImageCover(
    imageUris: List<Uri>,
    carousel: Boolean,
    modifier: Modifier = Modifier
) {
    if (imageUris.size <= 1) {
        SafeCoverImage(
            model = imageUris.firstOrNull(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            sizePx = 3000,
            loadOriginal = true
        )
        return
    }

    if (!carousel) {
        val randomUri = remember(imageUris) { imageUris.random() }
        SafeCoverImage(
            model = randomUri,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            sizePx = 3000,
            loadOriginal = true
        )
        return
    }

    var index by remember(imageUris) { mutableStateOf(0) }
    LaunchedEffect(imageUris) {
        while (true) {
            kotlinx.coroutines.delay(ARTIST_COVER_CAROUSEL_INTERVAL_MS)
            index = (index + 1) % imageUris.size
        }
    }
    Crossfade(
        targetState = index,
        animationSpec = tween(durationMillis = 900),
        modifier = modifier,
        label = "artist-cover-carousel"
    ) { current ->
        SafeCoverImage(
            model = imageUris[current.coerceIn(imageUris.indices)],
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            sizePx = 3000,
            loadOriginal = true
        )
    }
}

private const val ARTIST_COVER_CAROUSEL_INTERVAL_MS = 5000L

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun ArtistAlbumRow(
    album: Album,
    duration: Long,
    albumArtUri: Uri?,
    representativeSong: Song?,
    loadCoverArt: ((Song) -> Bitmap?)?,
    contextArtistName: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coverState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val coverModel = coverState.model
    val summary = buildList {
        add(context.getString(R.string.artist_album_song_summary_detail, album.songCount))
        add(duration.formatArtistDetailDuration())
        if (album.year.isNotBlank()) add(album.year)
        album.contextualAlbumArtist(contextArtistName)?.let(::add)
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 256,
                    showDefaultPlaceholder = false
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = if (selectionMode && selected) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun Long.formatArtistDetailDuration(): String {
    return formatPlaybackDuration()
}

private fun Album.contextualAlbumArtist(contextArtistName: String?): String? {
    val currentName = contextArtistName?.trim().orEmpty()
    val albumArtist = albumArtist.trim()
    return albumArtist.takeIf {
        it.isNotBlank() && (currentName.isBlank() || !it.equals(currentName, ignoreCase = true))
    }
}
