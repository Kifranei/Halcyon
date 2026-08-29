package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumCard(
    album: Album,
    albumArtUri: Uri? = null,
    representativeSong: Song? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    summary: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayName = remember(album.name, context) {
        album.name.takeUnless(LibraryNormalizer::isGeneratedUnknownAlbumPlaceholder)
            ?: context.getString(R.string.player_unknown_album)
    }
    val displayArtist = remember(album.albumArtist) {
        album.albumArtist.takeIf(LibraryNormalizer::isUsableArtistText)
    }
    val resolvedSummary = summary ?: buildList {
        displayArtist?.let(::add)
        add(context.getString(R.string.song_count, album.songCount))
    }.joinToString(" · ")
    val coverState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.LibraryGrid,
        showDefaultWhenMissing = false
    )
    val coverModel: Any? = coverState.model

    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = displayName,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 384,
                    showDefaultPlaceholder = false
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.matchParentSize())
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.34f)
                            else Color.Black.copy(alpha = 0.18f)
                        )
                )
                SelectionCheck(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    unselectedColor = Color.White.copy(alpha = 0.28f),
                    checkColor = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayName,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isPinned) {
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = MiuixIcons.Regular.Pin,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = resolvedSummary,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
